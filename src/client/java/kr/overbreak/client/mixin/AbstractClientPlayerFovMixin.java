package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 시야각: 이동 속도는 시야를 넓히거나 좁히지 않습니다 (달리기 · 둔화 · 가속 모두) — 스킬 연출의 줌만 반영됩니다.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerFovMixin {
	/** 황야의 무법자 조준 · 잔영 회피 중에는 시야가 좁아지고, 그림자 걸음 도착 순간 잠깐 넓어집니다 (본인 화면만). */
	@org.spongepowered.asm.mixin.injection.Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void overbreak$deadeyeZoom(boolean firstPerson, float effectScale,
									  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
		if ((Object) this != net.minecraft.client.Minecraft.getInstance().player) {
			return;
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float scale = kr.overbreak.client.hud.ShadeScreen.fovScale(partial) * kr.overbreak.client.hud.ThunderScreen.fovScale(partial)
				* kr.overbreak.client.tutorial.BootSequence.fovScale()
				* kr.overbreak.client.camera.ReloadKick.fovScale(partial)
				* kr.overbreak.client.camera.ScatterCam.fovScale(partial);
		if (kr.overbreak.client.hud.DeadeyeHud.active()) {
			scale *= 0.9F;
		}
		if (scale != 1.0F) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}

	/** 이동 속도 비율을 늘 1 로 — 달리기 · 둔화 · 가속 어떤 속도 변화도 시야각을 바꾸지 않습니다. */
	@ModifyExpressionValue(method = "getFieldOfViewModifier",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double overbreak$ignoreSpeed(double speed) {
		return ((AbstractClientPlayer) (Object) this).getAbilities().getWalkingSpeed();
	}
}
