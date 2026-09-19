package kr.overbreak.core.tick;

import kr.overbreak.Overbreak;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 틱레이트를 올려도 바닐라 이동이 빨라지지 않게 하는 속성 보정 (1초에 움직이는 거리 · 점프 높이 · 낙하 속도를 20틱과 같게).
 *
 * 바닐라 이동은 틱마다 "속도에 가속을 더하고 → 마찰을 곱하는" 계산이라, 틱이 k배(60틱이면 3배)가 되면 모든 것이 k배 빨라집니다.
 * 26.2 의 속성으로 1초 기준 식을 맞춥니다 (공중 가속만 PlayerFlyingSpeedMixin):
 *
 *   공기 저항   air_drag_modifier  : 틱당 감속 0.91 → 0.91^(1/k)            (1 - f) 에 곱하는 값 = (1 - 0.91^(1/k)) / 0.09
 *   땅 마찰     friction_modifier  : 땅(0.6) × 공기(0.91) 이 1초에 줄어드는 비율을 맞춤
 *   걷기 속도   movement_speed     : 마찰이 바뀌면 바닐라가 가속을 0.216/마찰³ 으로 줄이므로, 최고 속도가 1초 기준 같도록 다시 맞춤
 *   중력 · 점프  gravity · jump_strength : 바닐라 틱 계산을 돌려 점프 최고 높이 · 체공 시간 · 1초 낙하 거리가 같아지는 값 (대략 1/k² · 1/k)
 *
 * 모두 ADD_MULTIPLIED_TOTAL 수정자라 직업 이동속도 · 둔화 같은 다른 수정자 위에 곱해집니다. 기준 블록은 보통 땅(마찰 0.6) —
 * 얼음 · 슬라임처럼 미끄러운 블록은 정확히 맞지 않습니다 (전장 · 훈련장 바닥은 모래 · 네더 사마귀 블록).
 */
public final class MovementScale {
	private static final Identifier ID = Overbreak.id("tick_rate_movement");
	private static final double GROUND = 0.6;
	private static final double AIR = 0.91;
	private static final double VERTICAL = 0.98;

	/** 계산된 보정값 — {speed, gravity, jump, friction, airDrag} (배율, 1 = 그대로). */
	public record Factors(double speed, double gravity, double jump, double friction, double airDrag, double airAccel) {
		public static final Factors NONE = new Factors(1, 1, 1, 1, 1, 1);
	}

	private MovementScale() {}

	public static Factors factors(double k) {
		if (Math.abs(k - 1.0) < 1.0E-6) {
			return Factors.NONE;
		}
		// 공기 저항: 수평 0.91 · 수직 0.98 을 한 값으로 — 둘의 가운데
		double airH = (1.0 - Math.pow(AIR, 1.0 / k)) / (1.0 - AIR);
		double airV = (1.0 - Math.pow(VERTICAL, 1.0 / k)) / (1.0 - VERTICAL);
		double airDrag = (airH + airV) / 2.0;
		double airF = 1.0 - (1.0 - AIR) * airDrag;
		// 땅: 틱당 (땅 × 공기) 이 1초에 줄어드는 비율을 20틱과 같게
		double combined = Math.pow(GROUND * AIR, 1.0 / k);
		double ground = combined / airF;
		double friction = (1.0 - ground) / (1.0 - GROUND);
		// 걷기 최고 속도 (틱당 이동) = 가속 / (1 - f) — 바닐라는 가속을 더하고 이동한 뒤에 마찰을 곱함
		// 가속 = speed × 0.216 / 땅³ (땅 > 0.6 일 때, 아니면 speed 그대로)
		double vanillaTop = 1.0 / (1.0 - GROUND * AIR);
		double accelScale = 0.216 / (ground * ground * ground);
		double newTop = accelScale / (1.0 - combined);
		double speed = vanillaTop / (k * newTop);
		// 공중 가속 (바닐라 0.02 고정값, 속성 없음 → PlayerFlyingSpeedMixin): 공중 최고 속도가 1초 기준 같도록
		double air = (1.0 / (1.0 - AIR)) / (k / (1.0 - airF));
		double vDrag = 1.0 - (1.0 - VERTICAL) * airDrag;
		double[] jg = fitJump(k, vDrag);
		return new Factors(speed, jg[1], jg[0], friction, airDrag, air);
	}

	private static final java.util.Map<Long, double[]> JUMP_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * 점프 속도 · 중력 배율 — 연속 근사(1/k, 1/k²)는 틱 단위 계산과 어긋나 60틱에서 점프가 12% 낮아집니다 (1.25칸 → 1.10칸, 한 칸 블록이 겨우 넘어감).
	 * 바닐라와 같은 틱 계산을 돌려 점프 최고 높이 · 체공 시간 · 1초 낙하 거리가 20틱과 같아지는 값을 찾습니다 (틱레이트마다 한 번, 저장).
	 */
	private static double[] fitJump(double k, double vDrag) {
		return JUMP_CACHE.computeIfAbsent(Math.round(k * 1000.0), key -> {
			double[] target = jumpSim(1.0, 1.0, 1.0, VERTICAL);
			double bestJ = 1.0 / k;
			double bestG = 1.0 / (k * k);
			double best = Double.MAX_VALUE;
			double jStep = 0.2 / k / 40.0;
			double gStep = 0.2 / (k * k) / 40.0;
			for (int round = 0; round < 3; round++) {
				double cj = bestJ;
				double cg = bestG;
				for (int i = -40; i <= 40; i++) {
					for (int j = -40; j <= 40; j++) {
						double jf = cj + i * jStep;
						double gf = cg + j * gStep;
						if (jf <= 0.0 || gf <= 0.0) {
							continue;
						}
						double[] r = jumpSim(k, jf, gf, vDrag);
						double e = Math.abs(r[0] - target[0]) / target[0] + Math.abs(r[1] - target[1]) / target[1]
								+ Math.abs(r[2] - target[2]) / target[2];
						if (e < best) {
							best = e;
							bestJ = jf;
							bestG = gf;
						}
					}
				}
				jStep /= 10.0;
				gStep /= 10.0;
			}
			return new double[] {bestJ, bestG};
		});
	}

	/** 바닐라 수직 이동 (이동한 뒤 중력 빼고 저항 곱함) — {점프 최고 높이, 체공 초, 1초 낙하 거리}. */
	private static double[] jumpSim(double k, double jumpFactor, double gravityFactor, double drag) {
		double g = 0.08 * gravityFactor;
		double vy = 0.42 * jumpFactor;
		double y = 0.0;
		double top = 0.0;
		int n = 0;
		while (n < 100000) {
			y += vy;
			n++;
			top = Math.max(top, y);
			vy = (vy - g) * drag;
			if (y <= 0.0) {
				break;
			}
		}
		double fy = 0.0;
		double fv = 0.0;
		int ticks = (int) Math.round(20.0 * k);
		for (int i = 0; i < ticks; i++) {
			fy += fv;
			fv = (fv - g) * drag;
		}
		return new double[] {top, n / (20.0 * k), -fy};
	}

	/**
	 * 지금 틱레이트에 맞춰 수정자를 걸거나 뗍니다.
	 * 플레이어 포함 — 클라이언트도 서버 틱레이트대로 돌기 때문입니다 (client MinecraftTickRateMixin; 바닐라 클라이언트는 1초 20틱에 머묾).
	 */
	public static void apply(LivingEntity e) {
		Factors f = factors(TickRateConfig.scale());
		set(e, Attributes.MOVEMENT_SPEED, f.speed);
		set(e, Attributes.GRAVITY, f.gravity);
		set(e, Attributes.JUMP_STRENGTH, f.jump);
		set(e, Attributes.FRICTION_MODIFIER, f.friction);
		set(e, Attributes.AIR_DRAG_MODIFIER, f.airDrag);
	}

	private static void set(LivingEntity e, Holder<Attribute> attr, double factor) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(ID);
		if (Math.abs(factor - 1.0) > 1.0E-9) {
			inst.addTransientModifier(new AttributeModifier(ID, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** 걸린 이동속도 보정 배율 (시야각 계산에서 되돌릴 때). */
	public static double speedFactor(LivingEntity e) {
		AttributeInstance inst = e.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeModifier m = inst == null ? null : inst.getModifier(ID);
		return m == null ? 1.0 : 1.0 + m.amount();
	}
}
