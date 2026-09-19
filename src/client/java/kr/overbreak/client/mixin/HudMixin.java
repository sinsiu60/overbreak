package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kr.overbreak.client.camera.TpsCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 바닐라는 3인칭에서 조준점을 숨깁니다. 어깨 너머 시점에서는 보이게 합니다. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean overbreak$tpsCrosshair(CameraType type, Operation<Boolean> original) {
		return original.call(type) || TpsCamera.active(type);
	}
}
