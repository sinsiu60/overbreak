package kr.overbreak.util;

import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 남의 시야를 돌리는 것 — 스킬이 "저쪽을 보게" 만들어야 할 때 (워리어 피의 사슬).
 *
 * 한 번에 홱 돌리면 멀미가 나므로 틱마다 조금씩 돌립니다.
 * 플레이어는 회전 패킷을 따로 보내 줘야 화면이 실제로 돕니다.
 */
public final class Look {
	private Look() {}

	/**
	 * 한 틱 분량만큼 {@code point} 쪽으로 시야를 돌립니다.
	 *
	 * @param rate 한 틱에 남은 각도의 몇 %를 좁힐지 (0~1). 0.25 면 네 틱쯤에 거의 맞춰집니다
	 */
	public static void toward(LivingEntity e, Vec3 point, float rate) {
		Vec3 d = point.subtract(e.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		if (d.lengthSqr() < 1.0E-6) {
			return;
		}
		float wantYaw = (float) (Mth.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
		float wantPitch = (float) (-(Mth.atan2(d.y, flat) * 180.0 / Math.PI));
		float yaw = e.getYRot() + Mth.wrapDegrees(wantYaw - e.getYRot()) * rate;
		float pitch = e.getXRot() + Mth.wrapDegrees(wantPitch - e.getXRot()) * rate;
		set(e, yaw, Mth.clamp(pitch, -90.0F, 90.0F));
	}

	/** 시야를 그 각도로 둡니다 (플레이어면 화면도 함께 돕니다). */
	public static void set(LivingEntity e, float yaw, float pitch) {
		e.setYRot(yaw);
		e.setXRot(pitch);
		e.setYHeadRot(yaw);
		e.yRotO = yaw;
		e.xRotO = pitch;
		if (e instanceof ServerPlayer p) {
			p.connection.send(new ClientboundPlayerRotationPacket(yaw, false, pitch, false));
		}
	}
}
