package kr.overbreak.client.mixin;

import kr.overbreak.client.anim.ThirdPersonAnim;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 플레이어 렌더 상태에 스킬 애니메이션 시간 · 몸 회전 · 손 아이템 크기를 채웁니다. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void overbreak$skillAnim(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ThirdPersonAnim.extract(entity.getId(), state, partialTicks);
		// 걸음 박자: 늘 달리기 속도라 바닐라 다리 흔들기가 너무 빨라 1인칭 무기 흔들림과 같은 비율로 늦춤
		state.walkAnimationPos *= kr.overbreak.client.camera.WeaponBob.CADENCE;
		// 재장전 중 탄창이 빠진 구간: 받치는 손(주 손 반대)에 탄창 모델
		net.minecraft.client.renderer.item.ItemStackRenderState magazine = ((kr.overbreak.client.anim.AnimRenderState) state).overbreak$magazine();
		if (kr.overbreak.client.anim.ReloadAnim.magazineOut(entity.getId(), partialTicks)) {
			boolean leftSupport = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
			net.minecraft.client.Minecraft.getInstance().getItemModelResolver().updateForLiving(magazine, kr.overbreak.client.anim.ReloadAnim.magazineStack(),
					leftSupport ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
		} else {
			magazine.clear();
		}
	}
}
