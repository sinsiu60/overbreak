package kr.overbreak.client.input;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.hud.HudState;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 공중 제어 (전 직업, 0.2f) — 공중에서도 이동키로 가속 · 선회할 수 있게 바닐라 공중 이동을 대신합니다.
 *
 *   이동키(WASD) 기준 · 시선의 수평 방향이 앞 — 조준과 이동이 따로라 적을 겨눈 채 좌우로 돌 수 있습니다
 *   궤적의 깃털: 최고 초당 6칸 · 가속 초당 24칸² (약 0.25초) · 90° 넘게 꺾는 입력은 초당 36칸² · 손을 떼면 초당 4칸² 로 서서히 감속
 *   다른 직업  : 최고 초당 4.5칸 · 가속 초당 8칸² · 꺾기 초당 12칸² · 감속 초당 3칸² (방향만 조금 트는 정도)
 *   최고 속도를 넘은 상태(스킬 추진 직후)에서는 같은 쪽 입력은 가속 없이 감속만, 다른 쪽 입력은 방향을 틀며 최고 속도까지 끌어내림
 *   수직 속도는 건드리지 않습니다 (중력 · 활공 그대로) — 궤적 추격 비행만 점프 키로 오르고 떼면 천천히 가라앉음
 *
 *   꺼지는 때: 땅 · 물 · 용암 · 겉날개 · 탈것 · 크리에이티브 비행 / 기절 · 에어본 · 스킬 추진 중(서버 표시) /
 *              서버가 속도를 실어 준 직후(스킬 추진) / 넉백을 받은 뒤 0.3초
 *
 * 플레이어 위치는 클라이언트가 정하므로 클라이언트 이동 계산에서 적용합니다 (LivingEntityAirControlMixin).
 * 바닐라 공중 가속(getFlyingSpeed)과 수평 공기 저항은 이 동안 쓰지 않아 둘이 겹치지 않습니다.
 */
public final class AirControl {
	/** 제어 값 — 초당 칸 · 초당 칸². */
	public record Params(double maxSpeed, double accel, double reverseAccel, double drag) {}

	/** 궤적의 깃털 (체공 훈풍) — 공중 기동 직업이라 크게. */
	public static final Params NORMAL = new Params(6.0, 24.0, 36.0, 4.0);
	/** 다른 직업 — 방향만 조금 틀 수 있을 만큼 (가속 3분의 1, 약 0.55초에 최고 속도). */
	public static final Params OTHER = new Params(4.5, 8.0, 12.0, 3.0);
	/** 궤적의 깃털 궁극기 「궤적 추격」 비행. */
	public static final Params FLIGHT = new Params(10.0, 40.0, 60.0, 6.0);
	/** 비행 중 점프 키 상승 · 손을 뗐을 때 가라앉는 속도 (초당 칸). */
	public static final double ASCEND = 5.0;
	public static final double SINK = 1.0;
	/** 넉백 뒤 제어가 꺼지는 시간 (1/20초 단위 · 0.3초). */
	public static final double KNOCK_LOCK = 6.0;

	/** 이번 이동 계산에서 제어 중인가 · 이 틱에 정한 수평 속도 (틱당). */
	private static boolean controlling;
	private static double vx;
	private static double vz;
	/** 서버가 마지막으로 속도를 실어 준 틱 · 넉백 잠금이 풀리는 시각. */
	private static int pushedTick = -100;
	private static double lockUntil;

	private AirControl() {}

	// ── 서버 신호 ────────────────────────────────────────

	/** 서버가 내 속도를 정해 보냄 (스킬 추진 · 넉백) — 그 틱과 다음 틱은 서버 속도를 그대로 둡니다. */
	public static void pushed(LocalPlayer p) {
		pushedTick = p.tickCount;
	}

	/** 넉백을 받음 — 0.3초 동안 제어하지 않음 (넉백을 곧바로 상쇄하지 못하게). */
	public static void knocked() {
		lockUntil = kr.overbreak.client.ClientClock.now() + KNOCK_LOCK;
	}

	// ── 판정 ─────────────────────────────────────────────

	public static boolean flight(LocalPlayer p) {
		return SkillAnims.find(p.getId(), SkillAnimPayload.GS_PURSUIT) != null;
	}

	/** 이번 틱에 공중 제어를 할 것인가. */
	public static boolean applies(LocalPlayer p) {
		if (!InputMode.active() || p.onGround() || p.getAbilities().flying || p.isInWater() || p.isInLava()
				|| p.isFallFlying() || p.isPassenger() || p.isSpectator()) {
			return false;
		}
		// 새 월드 · 새 플레이어면 틱 수와 시계가 처음부터 다시 셉니다 — 지난 월드의 기록은 버림
		if (p.tickCount < pushedTick) {
			pushedTick = -100;
		}
		double now = kr.overbreak.client.ClientClock.now();
		if (lockUntil - now > KNOCK_LOCK) {
			lockUntil = 0.0;
		}
		if (HudState.noAirControl() || p.tickCount - pushedTick <= 1) {
			return false;
		}
		return now >= lockUntil;
	}

	// ── 이동 계산 (travelInAir 앞 · 뒤) ──────────────────

	/** 이동 계산 앞 — 입력으로 새 수평 속도를 정해 싣습니다. */
	public static void before(LocalPlayer p) {
		controlling = applies(p);
		if (!controlling) {
			return;
		}
		Params prm = flight(p) ? FLIGHT
				: kr.overbreak.classes.gunslinger.Gunslinger.ID.equals(HudState.classId()) ? NORMAL : OTHER;
		double tickPerSec = 20.0 * Ticks.k();
		double dt = 1.0 / tickPerSec;
		Vec3 d = p.getDeltaMovement();
		// 틱당 → 초당
		double sx = d.x * tickPerSec;
		double sz = d.z * tickPerSec;
		double[] w = input(p);
		double[] out = step(sx, sz, w[0], w[1], dt, prm);
		vx = out[0] / tickPerSec;
		vz = out[1] / tickPerSec;
		p.setDeltaMovement(vx, d.y, vz);
	}

	/** 이동 계산 뒤 — 바닐라 수평 공기 저항을 되돌리고 (벽에 막힌 축은 0 그대로), 비행이면 수직 속도. */
	public static void after(LocalPlayer p) {
		Vec3 d = p.getDeltaMovement();
		double x = d.x;
		double z = d.z;
		if (controlling) {
			x = Math.abs(d.x) < 1.0E-9 ? 0.0 : vx;
			z = Math.abs(d.z) < 1.0E-9 ? 0.0 : vz;
		}
		double y = d.y;
		if (flight(p) && !HudState.noAirControl()) {
			double tickPerSec = 20.0 * Ticks.k();
			boolean jump = Minecraft.getInstance().options.keyJump.isDown() && Minecraft.getInstance().gui.screen() == null;
			if (jump) {
				y = ASCEND / tickPerSec;
			} else if (!p.onGround()) {
				y = -SINK / tickPerSec;
			}
		}
		p.setDeltaMovement(x, y, z);
		controlling = false;
	}

	/** 바닐라 공중 가속을 끄는가 (getFlyingSpeed → 0). */
	public static boolean controlling() {
		return controlling;
	}

	/** 이동키 방향 (수평 단위 벡터, 입력 없으면 0) — 시선의 수평 방향이 앞. */
	private static double[] input(LocalPlayer p) {
		double strafe = p.xxa;
		double forward = p.zza;
		double len = Math.sqrt(strafe * strafe + forward * forward);
		if (len < 1.0E-4) {
			return new double[] {0.0, 0.0};
		}
		strafe /= len;
		forward /= len;
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		return new double[] {strafe * cos - forward * sin, forward * cos + strafe * sin};
	}

	/**
	 * 한 틱의 수평 속도 (초당 칸) — 시험에서 직접 부릅니다.
	 * @param wx wz 입력 방향 (단위 벡터, 없으면 0)
	 */
	public static double[] step(double sx, double sz, double wx, double wz, double dt, Params prm) {
		double speed = Math.sqrt(sx * sx + sz * sz);
		boolean input = wx * wx + wz * wz > 1.0E-6;
		if (!input) {
			// 관성 — 서서히 감속
			double next = Math.max(0.0, speed - prm.drag() * dt);
			return speed < 1.0E-6 ? new double[] {0.0, 0.0} : new double[] {sx / speed * next, sz / speed * next};
		}
		double along = speed < 1.0E-6 ? 1.0 : (sx * wx + sz * wz) / speed;
		if (speed > prm.maxSpeed()) {
			if (along > 0.7) {
				// 같은 쪽 — 가속 없이 감속만
				double next = Math.max(prm.maxSpeed(), speed - prm.drag() * dt);
				return new double[] {sx / speed * next, sz / speed * next};
			}
			// 다른 쪽 — 방향을 틀되 속력은 최고 속도까지 끌어내림
			double nx = sx + wx * prm.reverseAccel() * dt;
			double nz = sz + wz * prm.reverseAccel() * dt;
			double cap = Math.max(prm.maxSpeed(), speed - prm.reverseAccel() * dt);
			return clamp(nx, nz, cap);
		}
		double a = along < 0.0 ? prm.reverseAccel() : prm.accel();
		return clamp(sx + wx * a * dt, sz + wz * a * dt, prm.maxSpeed());
	}

	private static double[] clamp(double x, double z, double max) {
		double s = Math.sqrt(x * x + z * z);
		if (s > max) {
			return new double[] {x / s * max, z / s * max};
		}
		return new double[] {x, z};
	}
}
