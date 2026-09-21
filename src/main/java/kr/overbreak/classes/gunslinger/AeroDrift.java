package kr.overbreak.classes.gunslinger;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * [패시브] 체공 훈풍 — 건슬링어를 공중에 붙잡아 두는 두 가지 이득.
 *
 *   활공   : 떨어지기 시작한 뒤 점프 키를 누르고 있으면 떨어지는 속도가 확 줄어듭니다 (최대 2초).
 *            올라가는 동안(그냥 점프 · 반동 도약)에는 켜지지 않습니다 — 점프만 해도 패시브가 켜지던 것 (0.2d).
 *            땅에 닿으면 다시 가득 찹니다. 느린 낙하로 걸어 클라이언트가 그대로 예측합니다 (끊김 없음).
 *   공중 명중: 땅에서 1.5칸 이상 떠서 맞힌 총알은 무조건 치명타 150%, 그리고 맞힐 때마다
 *            반동 도약 · 사선 앵커의 남은 쿨타임이 0.5초씩 깎입니다.
 *
 * 낙하 피해는 늘 받지 않습니다 ({@link Gunslinger} 의 ALLOW_DAMAGE).
 */
public final class AeroDrift {
	/** 활공 지속 (시간 단위 · 2초). */
	public static final int GLIDE = 40;
	/** 상태 초기값 (틱). */
	public static final int GLIDE_TICKS_INIT = 40 * 3;
	/** 반동 도약 · 사선 앵커를 쓸 때마다 늘어나는 활공 시간 (시간 단위 · 1초). */
	public static final int EXTEND = 20;

	/** 공중 명중 치명타 배율 (%). */
	public static final int CRIT_PERCENT = 150;
	/** 공중 명중 한 번에 깎이는 쿨타임 (시간 단위 · 0.5초). */
	public static final int REFUND = 10;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private AeroDrift() {}

	/** 공중 치명타가 붙는 높이 (칸) — 발밑으로 이만큼이 비어 있어야 합니다. */
	public static final double CRIT_HEIGHT = 1.5;

	/**
	 * 공중 치명타가 붙는가 — 땅에서 {@link #CRIT_HEIGHT} 칸 이상 떠 있을 때 (0.2d).
	 * 예전에는 발만 떼도(제자리 점프 한 번) 치명타였습니다. 발 가운데에서 바로 아래로 재서,
	 * 그 사이에 단단한 블록이 없으면 떠 있는 것으로 봅니다 (물 위는 받침이 없으니 떠 있는 것).
	 */
	public static boolean airborne(ServerPlayer p) {
		if (p.onGround()) {
			return false;
		}
		net.minecraft.world.phys.Vec3 feet = p.position();
		return p.level().clip(new net.minecraft.world.level.ClipContext(feet, feet.subtract(0, CRIT_HEIGHT, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p))
				.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** 공중에서 한 발 맞혔을 때 — 반동 도약 · 사선 앵커 쿨타임 환급. */
	public static void onAirHit(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		int back = Ticks.of(REFUND);
		refund(prof, Gunslinger.BOOST, back);
		refund(prof, Gunslinger.ANCHOR, back);
		Fx.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 1.9F);
	}

	private static void refund(PlayerProfile prof, String key, int ticks) {
		int left = prof.cooldown(key);
		if (left > 0) {
			prof.setCooldownTicks(key, Math.max(0, left - ticks));
		}
	}

	/**
	 * 이동기를 썼다 — 활공 시간 1초 연장 (2초 위로도 쌓임).
	 * 땅에서 쏘아 올라가도 날아가지 않게, 땅에 있는 동안에는 2초 아래로만 채우고 깎지 않습니다.
	 * 쌓인 시간은 다음에 착지하는 순간 2초로 돌아갑니다.
	 */
	static void extend(GunslingerState st) {
		st.glideT += Ticks.of(EXTEND);
	}

	/** 직업 틱 — 활공 충전 · 유지 · 해제. */
	static void tick(ServerPlayer p, GunslingerState st) {
		boolean ground = p.onGround();
		// 떨어지기 시작했는가 — 서버의 플레이어 속도는 클라이언트 이동을 따라오지 않아 높이 변화로 봅니다
		double y = p.getY();
		if (ground) {
			st.descending = false;
		} else if (!Double.isNaN(st.lastY) && y < st.lastY - 1.0E-3) {
			st.descending = true;
		}
		st.lastY = y;
		if (ground && !st.wasGround) {
			// 착지: 활공을 2초로 되돌립니다 (쌓였던 연장분은 여기서 사라짐)
			st.glideT = Ticks.of(GLIDE);
		}
		st.wasGround = ground;
		if (ground) {
			// 땅에서는 2초까지 채우기만 — 땅에서 쓴 이동기의 연장분을 지우지 않습니다
			st.glideT = Math.max(st.glideT, Ticks.of(GLIDE));
			stop(p, st);
			return;
		}
		// 궁극기로 공중에 붙잡혀 있는 동안에는 활공이 끼어들지 않습니다
		// 웅크리기는 돌진 난사라, 활공은 점프 키로 — 다만 떨어지기 시작한 뒤부터만
		// (누르자마자 켜지면 그냥 점프만 해도 패시브가 켜졌습니다)
		boolean want = Attachments.profile(p).jumpDown && st.descending && st.glideT > 0;
		if (!want) {
			stop(p, st);
			return;
		}
		st.glideT--;
		st.gliding = true;
		// 재장전 동안에는 활공 동작을 띄우지 않습니다 — 공중 재장전(1.25초)을 통째로 덧어써 끊어먹었습니다.
		// 느린 낙하는 그대로 먹히고, 재장전이 끝나면 그때 동작이 들어옵니다.
		// 돌진 난사 동안도 마찬가지 — 난사 몸 동작을 덮어썼습니다.
		boolean showAnim = st.reloadT <= 0 && st.scatter == null;
		if (showAnim && !st.glideAnim) {
			st.glideAnim = true;
			SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_GLIDE, -1);
			Fx.sound(p, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.7F, 1.5F);
		} else if (!showAnim && st.glideAnim) {
			st.glideAnim = false;
			SkillAnimPayload.stop(p, SkillAnimPayload.GS_GLIDE);
		}
		// 느린 낙하는 1초마다 다시 걸어 두면 충분합니다 (끊기면 바로 다시 떨어짐)
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Ticks.of(6), 0, false, false));
		p.resetFallDistance();
		if (Ticks.ambient()) {
			ServerLevel level = p.level();
			Fx.particle(level, Fx.dust(SKY, 0.8F), p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.45, 0.35, 0.45, 0);
			Fx.particle(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.05, 0.3, 0.01);
		}
	}

	static void stop(ServerPlayer p, GunslingerState st) {
		if (!st.gliding) {
			return;
		}
		st.gliding = false;
		p.removeEffect(MobEffects.SLOW_FALLING);
		if (st.glideAnim) {
			st.glideAnim = false;
			SkillAnimPayload.stop(p, SkillAnimPayload.GS_GLIDE);
		}
	}
}
