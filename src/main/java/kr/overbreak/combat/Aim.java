package kr.overbreak.combat;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.AimPayload;
import kr.overbreak.util.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 조준 방향 — 스킬 · 평타가 "어느 쪽을 향하는가" 는 모두 여기서 읽습니다.
 *
 * 어깨 너머 시점에서는 카메라가 캐릭터 오른쪽에 있어 캐릭터 시선과 화면 조준점이 어긋납니다.
 * 클라이언트가 보낸 조준점이 있으면 "눈 → 조준점" 방향을 쓰고, 없거나 오래됐거나(1인칭 등)
 * 시선에서 45도 넘게 벗어나면(조작 방지) 캐릭터 시선을 씁니다.
 *
 * 가까운 조준점(아래를 보면 발 앞 바닥)은 카메라가 옆에 있어 "눈 → 조준점" 이 크게 옆으로 틀어집니다.
 * 수평 거리 3칸 이하는 시선 그대로, 10칸부터 조준점 그대로, 그 사이는 섞습니다.
 *
 * 어느 쪽을 쓰나
 *   {@link #direction} (조준점 보정): 투사체 · 히트스캔 · 멀리 조준하는 스킬 — 연사 · 전술 로켓 · 철권포 · 피의 사슬 · 파멸의 일격 조준 · 차원 도약
 *   {@link #facing} (보정 없음, 캐릭터 정면): 근접 · 부채꼴 · 돌진 — 근접 평타 · 지면 분쇄 · 중력 파쇄 · 돌진 충격 · 로켓 펀치 · 지진 강타 · 과열 분사
 */
public final class Aim {
	/** 조준점이 이 틱 수보다 오래되면 버립니다. */
	private static final int STALE_TICKS = 5;
	/** 시선과 조준 방향의 최대 차이 (cos 45°). */
	private static final double MAX_DEVIATION_COS = Math.cos(Math.toRadians(45.0));
	private static final double MAX_DISTANCE = 128.0;
	private static final double NEAR = 3.0;
	private static final double FAR = 10.0;

	private Aim() {}

	public static void receive(ServerPlayer p, AimPayload aim) {
		if (!Double.isFinite(aim.x()) || !Double.isFinite(aim.y()) || !Double.isFinite(aim.z())) {
			return;
		}
		Vec3 point = new Vec3(aim.x(), aim.y(), aim.z());
		if (point.distanceToSqr(p.getEyePosition()) > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}
		PlayerProfile prof = Attachments.profile(p);
		prof.aimPoint = point;
		prof.aimTick = kr.overbreak.core.tick.GameClock.now();
	}

	/** 조준 방향 (단위 벡터). */
	public static Vec3 direction(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		PlayerProfile prof = Attachments.profile(p);
		if (prof.aimPoint == null || kr.overbreak.core.tick.GameClock.now() - prof.aimTick > STALE_TICKS) {
			return look;
		}
		Vec3 raw = prof.aimPoint.subtract(p.getEyePosition());
		if (raw.lengthSqr() < 1.0E-4) {
			return look;
		}
		Vec3 d = raw.normalize();
		if (d.dot(look) < MAX_DEVIATION_COS) {
			return look;
		}
		double flat = Math.sqrt(raw.x * raw.x + raw.z * raw.z);
		double w = Math.max(0.0, Math.min(1.0, (flat - NEAR) / (FAR - NEAR)));
		if (w >= 1.0) {
			return d;
		}
		Vec3 blended = look.scale(1.0 - w).add(d.scale(w));
		return blended.lengthSqr() < 1.0E-6 ? look : blended.normalize();
	}

	/** 캐릭터 정면 (단위 벡터) — 조준점 보정 없음. 근접 · 부채꼴 · 돌진용. */
	public static Vec3 facing(ServerPlayer p) {
		return p.getLookAngle();
	}

	/** 캐릭터 정면의 {yaw, pitch} — 조준점 보정 없음. */
	public static float[] facingYawPitch(ServerPlayer p) {
		return new float[] {p.getYRot(), p.getXRot()};
	}

	/** 조준 방향의 {yaw, pitch} — 데이터팩 "시전자 회전" 자리에 씁니다. */
	public static float[] yawPitch(ServerPlayer p) {
		return Local.yawPitch(direction(p));
	}
}
