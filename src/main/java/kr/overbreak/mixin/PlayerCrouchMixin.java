package kr.overbreak.mixin;

import kr.overbreak.core.Crouch;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 전장에서는 웅크리기 키를 눌러도 자세가 낮아지지 않습니다 — {@link Crouch}. */
@Mixin(Player.class)
public abstract class PlayerCrouchMixin {
	@Inject(method = "getDesiredPose", at = @At("RETURN"), cancellable = true)
	private void overbreak$noCrouch(CallbackInfoReturnable<Pose> cir) {
		if (cir.getReturnValue() == Pose.CROUCHING && Crouch.suppressed((Player) (Object) this)) {
			cir.setReturnValue(Pose.STANDING);
		}
	}
}
