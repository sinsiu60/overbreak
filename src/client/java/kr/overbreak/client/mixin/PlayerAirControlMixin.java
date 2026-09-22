package kr.overbreak.client.mixin;

import kr.overbreak.client.input.AirControl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 공중 제어가 도는 동안 바닐라 공중 가속을 끕니다 — 둘이 겹쳐 두 배로 가속되지 않게 ({@link AirControl}). */
@Mixin(Player.class)
public abstract class PlayerAirControlMixin {
	@Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
	private void overbreak$noVanillaAirAccel(CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof LocalPlayer && AirControl.controlling()) {
			cir.setReturnValue(0.0F);
		}
	}
}
