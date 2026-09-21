package kr.overbreak.mixin;

import kr.overbreak.core.Crouch;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 전장에서는 웅크리기 키가 웅크리기가 아닙니다 — {@link Crouch}.
 *
 *   자세    : Pose.CROUCHING 을 서 있는 자세로 (눈높이 · 이동 감속도 함께 사라짐)
 *   모서리 멈춤: 바닐라 isStayingOnGroundSurface() 는 isShiftKeyDown() 그대로라, 웅크리기로 쓴 돌진 난사가
 *               난간 끝에서 끊겼습니다 (maybeBackOffFromEdge). 스킬 키로 누른 것이니 난간에 붙잡지 않습니다.
 */
@Mixin(Player.class)
public abstract class PlayerCrouchMixin {
	@Inject(method = "getDesiredPose", at = @At("RETURN"), cancellable = true)
	private void overbreak$noCrouch(CallbackInfoReturnable<Pose> cir) {
		if (cir.getReturnValue() == Pose.CROUCHING && Crouch.suppressed((Player) (Object) this)) {
			cir.setReturnValue(Pose.STANDING);
		}
	}

	@Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
	private void overbreak$noEdgeStop(CallbackInfoReturnable<Boolean> cir) {
		if (Crouch.suppressed((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
