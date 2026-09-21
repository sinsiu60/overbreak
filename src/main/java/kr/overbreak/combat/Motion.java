package kr.overbreak.combat;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 이동 신호 — 플러그인 PvpInput 의 pumpLaunch / pumpKnock / pumpDash 대응.
 * 데이터팩은 점수로 부탁하고 플러그인이 다음 틱에 실어 줬지만,
 * 여기서는 스킬 코드가 직접 부르므로 한 틱 지연이 없습니다.
 */
public final class Motion {
	/** 땅에서 출발하는 도약에 보태는 최소 상승분. 없으면 첫 틱에 마찰로 다 깎입니다. */
	public static final double LAUNCH_MIN_LIFT = 0.42;
	/** 밀어낼 때 살짝 띄우는 정도. */
	public static final double KNOCK_LIFT = 0.28;

	private Motion() {}

	/** 시선 방향(피치 포함)으로 한 번 날립니다. 이후는 중력에 맡깁니다. */
	public static void launch(LivingEntity e, double power) {
		if (power <= 0) {
			return;
		}
		Vec3 v = e.getLookAngle().normalize().scale(power);
		if (e.onGround() && v.y < LAUNCH_MIN_LIFT) {
			v = new Vec3(v.x, LAUNCH_MIN_LIFT, v.z);
		}
		push(e, v);
	}

	/** 주어진 방향(크기 무관)으로 한 번 날립니다. 땅을 딛고 있으면 최소 상승분을 보탭니다. */
	public static void launch(LivingEntity e, Vec3 dir, double power) {
		if (power <= 0 || dir.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 v = dir.normalize().scale(power);
		if (e.onGround() && v.y < LAUNCH_MIN_LIFT) {
			v = new Vec3(v.x, LAUNCH_MIN_LIFT, v.z);
		}
		push(e, v);
	}

	/** 수평 방향(dx, dz, 크기 무관)으로 세기만큼 밀어냅니다. */
	public static void knock(LivingEntity e, double dx, double dz, double power) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4 || power <= 0) {
			return;
		}
		double k = power / len;
		push(e, new Vec3(dx * k, KNOCK_LIFT, dz * k));
	}

	/**
	 * 남은 틱 동안 매 틱 속도를 다시 싣는 돌진.
	 * 마찰에 깎이지 않아 거리가 서고, 이동은 바닐라 물리라 벽·계단이 공짜로 따라옵니다.
	 *
	 * @param hold true 면 세로 속도를 0 으로 붙잡아 공중에서 써도 떨어지지 않습니다
	 */
	public static void dash(LivingEntity e, double dx, double dz, double power, int ticks, boolean hold) {
		Combatant c = Attachments.combatant(e);
		c.dashX = dx;
		c.dashZ = dz;
		c.dashPower = power;
		c.dashT = kr.overbreak.core.tick.Ticks.of(ticks);
		c.dashHold = hold;
		c.dashY = 0;
	}

	/**
	 * 바라보는 방향(위아래 포함) 돌진 — 방향 벡터 길이와 상관없이 {@code power} 칸/시간 단위로 곧게 나갑니다.
	 * 중력은 받지 않습니다 (세로 속도도 매 틱 다시 실음).
	 */
	public static void dash(LivingEntity e, Vec3 dir, double power, int ticks) {
		dash(e, dir.x, dir.z, power, ticks, true);
		Attachments.combatant(e).dashY = dir.y;
	}

	/** 제동 — 세로 속도까지 0 (위로 돌진한 뒤 계속 솟구치지 않게). */
	public static void stop(LivingEntity e) {
		brake(e);
		pushRaw(e, Vec3.ZERO);
	}

	/**
	 * 제동. 속도 주기를 멈추기만 하면 남은 수평 속도로 미끄러집니다
	 * (고도 유지 중이었다면 마찰도 안 먹어 훨씬 멀리 갑니다).
	 */
	public static void brake(LivingEntity e) {
		Combatant c = Attachments.combatant(e);
		c.dashT = 0;
		c.dashHold = false;
		Vec3 v = e.getDeltaMovement();
		pushRaw(e, new Vec3(0, v.y, 0));
	}

	/** 매 틱 돌진 유지. GameLoop 가 부릅니다. */
	public static void tick(LivingEntity e, Combatant c) {
		if (c.dashT <= 0) {
			return;
		}
		c.dashT--;
		double dy = c.dashHold ? c.dashY : 0;
		double len = Math.sqrt(c.dashX * c.dashX + dy * dy + c.dashZ * c.dashZ);
		if (c.dashPower <= 0 || len < 1.0E-4) {
			brake(e);
			return;
		}
		double k = c.dashPower / len;
		double vy = c.dashHold ? dy * k * kr.overbreak.core.tick.Ticks.step() : e.getDeltaMovement().y;
		double step = kr.overbreak.core.tick.Ticks.step();
		pushRaw(e, new Vec3(c.dashX * k * step, vy, c.dashZ * k * step));
	}

	/** 시선의 수평 방향 (피치 제거). 앞으로 나가는 이동기는 반드시 이것을 씁니다. */
	public static Vec3 flatLook(LivingEntity e) {
		double yaw = Math.toRadians(e.getYRot());
		return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	/** 시간 단위(1/20초당 칸) 속도를 틱당으로 바꿔 실음 — 마찰 · 중력은 MovementScale 이 1초 기준으로 맞춰 둬서 같은 거리 · 궤적. */
	private static void push(LivingEntity e, Vec3 perTime) {
		double step = kr.overbreak.core.tick.Ticks.step();
		// 위로 튀는 속도는 점프와 같은 배율 — 중력 보정이 점프 최고 높이 기준으로 맞춰져 있음 (MovementScale)
		double up = kr.overbreak.core.tick.MovementScale.factors(kr.overbreak.core.tick.Ticks.k()).jump();
		pushRaw(e, new Vec3(perTime.x * step, perTime.y * up, perTime.z * step));
	}

	/** 이미 틱당인 속도 그대로. */
	private static void pushRaw(LivingEntity e, Vec3 v) {
		e.setDeltaMovement(v);
		// 플레이어는 이 표시가 있어야 서버가 속도 패킷을 보냅니다.
		e.hurtMarked = true;
	}
}
