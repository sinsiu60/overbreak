package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kr.overbreak.client.camera.AimTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 블록 · 엔티티 선택(바닐라 hitResult) — 어깨 너머 시점에서는 "눈 → 화면 조준점" 방향으로 고릅니다.
 * 출발점(눈)과 사거리는 바닐라 그대로라 서버 사거리 검사와 어긋나지 않습니다.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAimMixin {
	private static final String PICK = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;";

	@WrapOperation(method = PICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"))
	private static Vec3 overbreak$aimDirection(Entity camera, float partial, Operation<Vec3> original) {
		Vec3 dir = aimDirection(camera, partial);
		return dir != null ? dir : original.call(camera, partial);
	}

	@WrapOperation(method = PICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
	private static HitResult overbreak$aimBlock(Entity camera, double range, float partial, boolean liquids, Operation<HitResult> original) {
		Vec3 dir = aimDirection(camera, partial);
		if (dir == null) {
			return original.call(camera, range, partial, liquids);
		}
		Vec3 from = camera.getEyePosition(partial);
		Vec3 to = from.add(dir.scale(range));
		return camera.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
				liquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, camera));
	}

	private static @Nullable Vec3 aimDirection(Entity camera, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (camera != mc.player) {
			return null;
		}
		Vec3 point = AimTracker.compute(mc, partial);
		if (point == null) {
			return null;
		}
		Vec3 d = point.subtract(camera.getEyePosition(partial));
		return d.lengthSqr() < 1.0E-4 ? null : d.normalize();
	}
}
