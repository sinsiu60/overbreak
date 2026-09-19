package kr.overbreak.client.camera;

import kr.overbreak.net.AimPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * 조준점 — 어깨 너머 시점에서 화면 한가운데 조준선이 닿는 곳.
 *
 * 카메라에서 앞으로 레이캐스트하되, 캐릭터보다 카메라 쪽에 있는 것(캐릭터 뒤의 블록 · 엔티티)은 무시하려고
 * 조준선 위에서 캐릭터 눈과 가장 가까운 지점부터 셉니다.
 *
 * 쓰는 곳
 *   - 매 틱 서버로 보내 스킬 · 평타 방향으로 씀 (서버 combat/Aim)
 *   - 블록 · 엔티티 선택(바닐라 hitResult)도 눈 → 조준점 방향으로 바꿈 (LocalPlayerAimMixin)
 */
public final class AimTracker {
	public static final double RANGE = 64.0;

	private AimTracker() {}

	/** 매 클라이언트 틱 시작 (입력 처리 전): 어깨 너머 시점이면 조준점을 서버로 보냅니다. */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null || !TpsCamera.active(mc.options.getCameraType())) {
			return;
		}
		Vec3 point = compute(mc, 1.0F);
		if (point != null && ClientPlayNetworking.canSend(AimPayload.TYPE)) {
			ClientPlayNetworking.send(new AimPayload(point.x, point.y, point.z));
		}
	}

	/** 어깨 너머 시점일 때 조준점. 아니면 null. */
	public static @Nullable Vec3 compute(Minecraft mc, float partial) {
		if (mc.player == null || mc.level == null || !TpsCamera.active(mc.options.getCameraType())) {
			return null;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return null;
		}
		Vec3 camPos = camera.position();
		Vector3fc f = camera.forwardVector();
		Vec3 forward = new Vec3(f.x(), f.y(), f.z());
		Vec3 eye = mc.player.getEyePosition(partial);

		double skip = Math.max(0.0, eye.subtract(camPos).dot(forward));
		Vec3 from = camPos.add(forward.scale(skip));
		Vec3 to = from.add(forward.scale(RANGE));
		HitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

		EntityHitResult entity = ProjectileUtil.getEntityHitResult(mc.player, from, end, new AABB(from, end).inflate(1.0),
				e -> e != mc.player && !e.isSpectator() && e.isPickable(), from.distanceToSqr(end));
		return entity != null ? entity.getLocation() : end;
	}
}
