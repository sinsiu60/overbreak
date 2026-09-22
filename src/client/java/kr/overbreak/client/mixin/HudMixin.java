package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kr.overbreak.client.camera.TpsCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 바닐라는 3인칭에서 조준점을 숨깁니다. 어깨 너머 시점에서는 보이게 합니다. */
@Mixin(Hud.class)
public abstract class HudMixin {
	/**
	 * 커스텀 조준점 (0.2f) — 설정에서 「마인크래프트 기본」 이 아니면 바닐라 조준점 대신 설정대로 그립니다
	 * (client/hud/crosshair). 보이는 조건은 바닐라와 같음: 1인칭 · 어깨 너머 3인칭.
	 */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
	private void overbreak$customCrosshair(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt, CallbackInfo ci) {
		kr.overbreak.client.hud.crosshair.CrosshairConfig c = kr.overbreak.client.hud.crosshair.CrosshairConfig.get();
		if (c.type == kr.overbreak.client.hud.crosshair.CrosshairConfig.Type.VANILLA) {
			return;
		}
		ci.cancel();
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		CameraType type = mc.options.getCameraType();
		if (!type.isFirstPerson() && !TpsCamera.active(type)) {
			return;
		}
		kr.overbreak.client.hud.crosshair.CrosshairRenderer.draw(g, g.guiWidth() / 2.0F, g.guiHeight() / 2.0F, c);
	}

	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean overbreak$tpsCrosshair(CameraType type, Operation<Boolean> original) {
		return original.call(type) || TpsCamera.active(type);
	}
}
