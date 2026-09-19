package kr.overbreak.client.mixin;

import kr.overbreak.client.anim.ThirdPersonAnim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 바닐라 자세가 다 정해진 뒤 스킬 애니메이션 자세를 덮어씁니다. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void overbreak$skillPose(AvatarRenderState state, CallbackInfo ci) {
		ThirdPersonAnim.pose((HumanoidModel<?>) (Object) this, state);
	}
}
