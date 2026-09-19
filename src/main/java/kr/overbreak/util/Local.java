package kr.overbreak.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 로컬 좌표 — 데이터팩 {@code execute anchored eyes positioned ^x ^y ^z} 와 똑같이 계산합니다.
 * 바닐라 LocalCoordinates 의 식을 그대로 옮겼습니다.
 *
 * 주의: ^x 는 <b>왼쪽이 양수</b>입니다. 오른손 총구는 음수(-0.28)입니다.
 */
public final class Local {
	private Local() {}

	/** 기준점에서 (yRot, xRot) 회전 기준 왼쪽 left · 위 up · 앞 forward 만큼 옮긴 지점. */
	public static Vec3 offset(Vec3 base, float yRot, float xRot, double left, double up, double forward) {
		double f2 = Math.cos(Math.toRadians(yRot + 90.0F));
		double f3 = Math.sin(Math.toRadians(yRot + 90.0F));
		double f4 = Math.cos(Math.toRadians(-xRot));
		double f5 = Math.sin(Math.toRadians(-xRot));
		double f6 = Math.cos(Math.toRadians(-xRot + 90.0F));
		double f7 = Math.sin(Math.toRadians(-xRot + 90.0F));
		Vec3 fwd = new Vec3(f2 * f4, f5, f3 * f4);
		Vec3 upV = new Vec3(f2 * f6, f7, f3 * f6);
		Vec3 leftV = fwd.cross(upV).scale(-1.0);
		return base.add(fwd.scale(forward)).add(upV.scale(up)).add(leftV.scale(left));
	}

	/** anchored eyes + 시전자 회전 (피치 포함). */
	public static Vec3 fromEyes(Entity e, double left, double up, double forward) {
		return offset(e.getEyePosition(), e.getYRot(), e.getXRot(), left, up, forward);
	}

	/** 발밑 + 수평 회전만 (피치 0). 바닥 원·수평 돌진용. */
	public static Vec3 flat(Vec3 base, float yRot, double left, double up, double forward) {
		return offset(base, yRot, 0.0F, left, up, forward);
	}

	/** 방향 벡터 → {yaw, pitch} (tp ... facing 과 같은 값). */
	public static float[] yawPitch(Vec3 dir) {
		double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
		float pitch = (float) (-Math.toDegrees(Math.atan2(dir.y, horiz)));
		return new float[] {yaw, pitch};
	}
}
