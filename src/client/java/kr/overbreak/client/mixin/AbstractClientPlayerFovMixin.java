package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kr.overbreak.classes.Classes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 시야각: 늘 달리기 속도(Classes.SPRINT_BASE, +30%) 는 시야를 넓히지 않습니다 — 바닐라는 이동속도만큼 시야가 넓어져
 * 그대로 두면 계속 달리는 것처럼 화면이 벌어져 보입니다. 둔화 · 가속 같은 다른 속도 변화는 그대로 반영됩니다.
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
				* kr.overbreak.client.tutorial.BootSequence.fovScale();
		if (kr.overbreak.client.hud.DeadeyeHud.active()) {
			scale *= 0.9F;
		}
		if (scale != 1.0F) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}

	@ModifyExpressionValue(method = "getFieldOfViewModifier",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double overbreak$ignoreSprintBase(double speed) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		// 틱레이트 이동 보정(속도를 줄여 1초 거리를 맞춤)은 시야를 좁히지 않게 되돌림
		speed /= kr.overbreak.core.tick.MovementScale.speedFactor(self);
		AttributeInstance inst = self.getAttribute(Attributes.MOVEMENT_SPEED);
		if (inst != null && inst.getModifier(Classes.SPRINT_BASE) != null) {
			return speed / (1.0 + Classes.SPRINT_BASE_RATIO);
		}
		return speed;
	}
}
