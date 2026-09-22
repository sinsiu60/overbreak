package kr.overbreak.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.overbreak.client.anim.AnimRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3인칭 손 아이템.
 *   크기 — 살육 중 도끼가 커집니다 (손잡이를 중심으로).
 *   투명 — 몸이 안 보이면 든 아이템도 그리지 않습니다 (파멸의 일격 조준 중 건틀릿만 떠 보이지 않게).
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@org.spongepowered.asm.mixin.Shadow
	protected abstract void submitArmWithItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm,
											 PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords);

	/** 재장전: 받치는 손에 든 탄창 — 손 아이템과 같은 방식으로 손에 붙여 그림. */
	@Inject(method = "submit", at = @At("TAIL"))
	private void overbreak$magazine(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, ArmedEntityRenderState state,
									 float yRot, float xRot, CallbackInfo ci) {
		if (state instanceof AnimRenderState a && !a.overbreak$magazine().isEmpty()) {
			this.submitArmWithItem(state, a.overbreak$magazine(), kr.overbreak.client.anim.ReloadAnim.magazineStack(), state.mainArm.getOpposite(),
					poseStack, submitNodeCollector, lightCoords);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void overbreak$hideWhenInvisible(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm,
											 PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (state.isInvisible) {
			ci.cancel();
		}
	}

	@Inject(method = "submitArmWithItem",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void overbreak$scaleItem(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm,
									 PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (state instanceof AnimRenderState sa) {
			// 돌진 난사 — 총열 축으로 옆으로 눕혀 쏘기 · 마무리 한 바퀴
			float roll = kr.overbreak.client.anim.scatter.ScatterView.gunRoll(sa, arm);
			if (roll != 0.0F) {
				poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
			}
		}
		if (state instanceof AnimRenderState a && arm == state.mainArm) {
			float s = a.overbreak$itemScale();
			if (s != 1.0F) {
				poseStack.scale(s, s, s);
			}
		}
	}
}
