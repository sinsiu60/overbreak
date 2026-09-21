package kr.overbreak.client.mixin;

import kr.overbreak.client.camera.ViewTransition;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3인칭 → 1인칭 전환 0.3초 동안은 1인칭 손 · 총을 그리지 않습니다 — 카메라가 아직 몸 뒤에서 들어오는 중 ({@link ViewTransition}). */
@Mixin(ItemInHandRenderer.class)
public abstract class HandTransitionMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void overbreak$hideDuringTransition(CallbackInfo ci) {
		if (ViewTransition.active()) {
			ci.cancel();
		}
	}
}
