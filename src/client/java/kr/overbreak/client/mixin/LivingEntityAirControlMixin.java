package kr.overbreak.client.mixin;

import kr.overbreak.client.input.AirControl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 공중 제어 (전 직업) — 내 플레이어의 공중 이동 계산 앞뒤에 끼어듭니다 ({@link AirControl}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAirControlMixin {
	@Inject(method = "travelInAir", at = @At("HEAD"))
	private void overbreak$airControlBefore(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer p) {
			AirControl.before(p);
		}
	}

	@Inject(method = "travelInAir", at = @At("TAIL"))
	private void overbreak$airControlAfter(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer p) {
			AirControl.after(p);
		}
	}
}
