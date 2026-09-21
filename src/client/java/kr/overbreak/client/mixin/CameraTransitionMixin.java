package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kr.overbreak.client.camera.ViewTransition;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 3인칭 → 1인칭 전환 0.3초 — 전환 동안은 카메라를 몸에서 뗀 채(3인칭처럼) 거리만 0 으로 당깁니다 ({@link ViewTransition}).
 */
@Mixin(Camera.class)
public abstract class CameraTransitionMixin {
	/** 1인칭이어도 전환 중이면 떼어 둔 것으로 (몸이 보이고 뒤로 물러난 자리에서 시작). */
	@ModifyExpressionValue(method = "alignWithEntity",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z", ordinal = 0))
	private boolean overbreak$easeIn(boolean firstPerson) {
		return firstPerson && !ViewTransition.active();
	}

	/** 뒤로 물러나는 거리를 전환 진행만큼 줄임. */
	@ModifyArg(method = "alignWithEntity",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float overbreak$pullIn(float distance) {
		return ViewTransition.active() ? distance * ViewTransition.distanceScale() : distance;
	}
}
