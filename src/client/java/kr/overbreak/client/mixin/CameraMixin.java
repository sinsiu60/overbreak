package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kr.overbreak.client.camera.TpsCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 어깨 너머 3인칭 — 바닐라가 카메라를 뒤로 빼기 직전에 오른쪽으로 옮기고, 뒤로 빼는 거리를 3/4 로 줄입니다.
 *
 * 오른쪽 이동도 벽을 레이캐스트해서 벽 앞에서 멈춥니다. 뒤로 빼는 거리는 바닐라 getMaxZoom 이
 * 옮겨진 위치에서 다시 벽을 검사하므로 카메라가 블록 안으로 들어가지 않습니다.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private @Nullable Level level;
	@Shadow private @Nullable Entity entity;
	@Shadow private Vec3 position;
	@Shadow @Final private Vector3f left;

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float overbreak$shoulder(Camera self, float cameraDist, Operation<Float> original) {
		if (!TpsCamera.active(Minecraft.getInstance().options.getCameraType()) || this.level == null || this.entity == null) {
			return original.call(self, cameraDist);
		}
		// 파멸의 일격은 1인칭에서 3인칭으로 벌어졌다가 다시 붙습니다 (0.2a)
		float zoom = kr.overbreak.client.camera.DoomCamera.zoom();
		float side = TpsCamera.SIDE * cameraDist / 4.0F * zoom;
		Vec3 right = new Vec3(-this.left.x(), -this.left.y(), -this.left.z());
		Vec3 from = this.position;
		Vec3 to = from.add(right.scale(side + TpsCamera.WALL_MARGIN));
		HitResult hit = this.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
		float allowed = side;
		if (hit.getType() != HitResult.Type.MISS) {
			allowed = Math.min(side, Math.max(0.0F, (float) hit.getLocation().distanceTo(from) - TpsCamera.WALL_MARGIN));
		}
		this.move(0.0F, 0.0F, allowed);
		return original.call(self, cameraDist * TpsCamera.DISTANCE_RATIO * zoom);
	}
}
