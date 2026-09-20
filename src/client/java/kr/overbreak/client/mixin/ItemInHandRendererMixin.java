package kr.overbreak.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.overbreak.client.anim.FirstPersonAnim;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1인칭 손.
 *
 *   오른손(무기): 스킬 애니메이션 중에는 바닐라가 쌓은 손 변환(휘두르기 · 교체 흔들림)을 버리고
 *     {@link FirstPersonAnim} 의 자세로 바꿔서 그립니다. submitArmWithItem 은 시작에서 pushPose 하고
 *     끝에서 popPose 하므로, 아이템을 그리기 직전에 pop → push 하면 손 변환이 없는 기본 상태로 돌아갑니다.
 *   왼손(빈손): 바닐라는 빈 왼손을 그리지 않습니다. 피의 사슬처럼 왼손을 쓰는 동작 중에만 직접 그립니다.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static final String RENDER_ITEM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
								 float inverseArmHeight, float attackValue, HumanoidArm arm) {
		throw new AssertionError();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM))
	private void overbreak$skillPose(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
									 ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
									 SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && FirstPersonAnim.active()) {
			poseStack.popPose();
			poseStack.pushPose();
			FirstPersonAnim.apply(poseStack, player.getMainArm(), frameInterp);
		} else if (hand == InteractionHand.OFF_HAND && FirstPersonAnim.offhandItemActive()) {
			poseStack.popPose();
			poseStack.pushPose();
			FirstPersonAnim.applyOffhandItem(poseStack, player.getMainArm().getOpposite(), frameInterp);
		}
	}

	@Shadow
	@org.spongepowered.asm.mixin.Final
	private net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher;

	@Shadow
	public abstract void renderItem(net.minecraft.world.entity.LivingEntity mob, ItemStack itemStack, net.minecraft.world.item.ItemDisplayContext type,
									PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords);

	/**
	 * 두 손 무기 (발키리 연사 포탑): 총을 그린 바로 뒤에 팔을 그립니다.
	 *   주 손: 총 자세 기준으로 손잡이를 쥠
	 *   받치는 손: 화면 왼쪽 아래에서 뻗어 총 몸통 밑을 받침. 재장전 중에는 탄창을 빼고 끼우고 장전 손잡이를 당김
	 *   (받치는 손 · 탄창은 총 포즈를 기억한 뒤 손 기준점이 없는 기본 포즈로 돌아가 계산 — 끝에서 바닐라가 pop 합니다)
	 */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.AFTER))
	private void overbreak$gunHands(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
									ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
									SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || player.isInvisible() || !FirstPersonAnim.twoHandedGun(itemStack)) {
			return;
		}
		HumanoidArm main = player.getMainArm();
		int invert = main == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.pushPose();
		FirstPersonAnim.gunGrip(poseStack, invert, false);
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, main);
		poseStack.popPose();

		org.joml.Matrix4f gun = new org.joml.Matrix4f(poseStack.last().pose());
		poseStack.popPose();
		poseStack.pushPose();
		org.joml.Matrix4f rel = new org.joml.Matrix4f(poseStack.last().pose()).invert().mul(gun);
		FirstPersonAnim.GunHands hands = FirstPersonAnim.gunHands(rel, invert, player.getId(), frameInterp);
		if (hands.magazine() != null) {
			poseStack.pushPose();
			poseStack.mulPose(hands.magazine());
			this.renderItem(player, kr.overbreak.client.anim.ReloadAnim.magazineStack(),
					main == HumanoidArm.RIGHT ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
							: net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
					poseStack, submitNodeCollector, lightCoords);
			poseStack.popPose();
		}
		// 쏠 때마다 총구 화염 (총에 붙어 다님)
		kr.overbreak.client.fx.BulletTrails.firstPersonFlash(poseStack, submitNodeCollector, rel, invert, player.getId(), frameInterp);
		poseStack.pushPose();
		poseStack.mulPose(hands.arm());
		net.minecraft.client.renderer.entity.player.AvatarRenderer<AbstractClientPlayer> renderer = this.entityRenderDispatcher.getPlayerRenderer(player);
		net.minecraft.resources.Identifier skin = player.getSkin().body().texturePath();
		if (main == HumanoidArm.RIGHT) {
			renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin,
					player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE));
		} else {
			renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin,
					player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE));
		}
		poseStack.popPose();
	}

	/**
	 * 보안관 리볼버 (한 손 총): 총을 그린 바로 뒤에
	 *   오른손 손잡이 · 총구 화염 · 재장전 중 젖힌 탄창 · 동작 중에만 들어오는 왼손(난사 손날 · 스피드로더 · 섬광 수류탄)을 그립니다.
	 *   (왼손 · 탄창은 총 포즈를 기억한 뒤 손 기준점이 없는 기본 포즈로 돌아가 계산 — 끝에서 바닐라가 pop 합니다)
	 */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.AFTER))
	private void overbreak$revolverHands(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
										 ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
										 SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || player.isInvisible() || !kr.overbreak.client.anim.SheriffAnim.revolver(itemStack)) {
			return;
		}
		HumanoidArm main = player.getMainArm();
		int invert = main == HumanoidArm.RIGHT ? 1 : -1;
		org.joml.Matrix4f gun = new org.joml.Matrix4f(poseStack.last().pose());
		poseStack.popPose();
		poseStack.pushPose();
		org.joml.Matrix4f rel = new org.joml.Matrix4f(poseStack.last().pose()).invert().mul(gun);
		// 주 손 팔: 손끝은 지금 총 손잡이를 따라가고 팔 방향은 평소 자세 그대로 (총이 돌아도 팔 끝이 화면에 안 들어옴)
		// 손끝을 축으로 총만 돌리는 회전(firstperson_item_spin)은 빼고 따라감 — 총 돌리기 중 팔은 조금만 움직임
		org.joml.Matrix4f spin = kr.overbreak.client.anim.FirstPersonAnim.active() ? kr.overbreak.client.anim.FirstPersonAnim.itemSpin(invert, frameInterp) : null;
		org.joml.Matrix4f relArm = spin == null ? rel : new org.joml.Matrix4f(rel).mul(spin.invert());
		poseStack.pushPose();
		poseStack.mulPose(kr.overbreak.client.anim.RevolverHands.armBase(relArm, invert));
		kr.overbreak.client.anim.RevolverHands.gripArm(poseStack, invert);
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, main);
		poseStack.popPose();
		kr.overbreak.client.fx.BulletTrails.firstPersonFlash(poseStack, submitNodeCollector, rel, invert, player.getId(), frameInterp,
				kr.overbreak.client.anim.RevolverHands.muzzle());
		kr.overbreak.client.anim.RevolverHands.Pose hands = kr.overbreak.client.anim.RevolverHands.compute(rel, invert, player.getId(), frameInterp);
		net.minecraft.world.item.ItemDisplayContext mainCtx = main == HumanoidArm.RIGHT
				? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		net.minecraft.world.item.ItemDisplayContext offCtx = main == HumanoidArm.RIGHT
				? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
		if (hands.cylinder() != null) {
			poseStack.pushPose();
			poseStack.mulPose(hands.cylinder());
			this.renderItem(player, kr.overbreak.client.anim.SheriffAnim.cylinderStack(), mainCtx, poseStack, submitNodeCollector, lightCoords);
			poseStack.popPose();
		}
		if (hands.leftItem() != null && hands.leftStack() != null) {
			poseStack.pushPose();
			poseStack.mulPose(hands.leftItem());
			this.renderItem(player, hands.leftStack(), offCtx, poseStack, submitNodeCollector, lightCoords);
			poseStack.popPose();
		}
		if (hands.leftArm() != null) {
			poseStack.pushPose();
			poseStack.mulPose(hands.leftArm());
			net.minecraft.client.renderer.entity.player.AvatarRenderer<AbstractClientPlayer> renderer = this.entityRenderDispatcher.getPlayerRenderer(player);
			net.minecraft.resources.Identifier skin = player.getSkin().body().texturePath();
			if (main == HumanoidArm.RIGHT) {
				renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin,
						player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE));
			} else {
				renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin,
						player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE));
			}
			poseStack.popPose();
		}
	}

	/**
	 * 건슬링어 쌍권총 — 양손에 한 자루씩이라 주 손 · 왼손 차례에 각각 그 손의 팔을 그리고,
	 * 공중 재장전 동안에는 하늘로 튕겨 올린 탄창 두 개도 따로 그립니다.
	 * 탄창은 손을 떠난 물건이라 총 자세가 아니라 카메라 공간 기준입니다 (pop → push 로 되돌림).
	 */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = RENDER_ITEM, shift = At.Shift.AFTER))
	private void overbreak$gunslingerHands(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
										  ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
										  SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (!kr.overbreak.client.anim.GunslingerAnim.pistols(itemStack)) {
			return;
		}
		boolean off = hand == InteractionHand.OFF_HAND;
		HumanoidArm main = player.getMainArm();
		// 양손에 한 자루씩 — 이 훅은 손마다 한 번씩 불리므로 그 손의 팔을 그립니다
		HumanoidArm arm = off ? main.getOpposite() : main;
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		if (!player.isInvisible()) {
			// 바닐라는 1인칭에서 아이템만 그리므로 총을 쥐는 팔을 직접 올립니다.
			poseStack.pushPose();
			FirstPersonAnim.gunGrip(poseStack, invert, false);
			this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, arm);
			poseStack.popPose();
		}
		// 탄창 두 개는 한 번만 (주 손 차례에)
		org.joml.Matrix4f[] mags = off ? new org.joml.Matrix4f[0]
				: kr.overbreak.client.anim.GunslingerAnim.flyingMags(player.getId(), invert, frameInterp);
		if (mags.length == 0) {
			return;
		}
		poseStack.popPose();
		poseStack.pushPose();
		net.minecraft.world.item.ItemDisplayContext ctx = arm == HumanoidArm.RIGHT
				? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
				: net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		for (org.joml.Matrix4f m : mags) {
			poseStack.pushPose();
			poseStack.mulPose(m);
			this.renderItem(player, kr.overbreak.client.anim.GunslingerAnim.magazineStack(), ctx, poseStack, submitNodeCollector, lightCoords);
			poseStack.popPose();
		}
	}

	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void overbreak$offhandArm(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
									  LocalPlayer player, int lightCoords, CallbackInfo ci) {
		if (!player.getOffhandItem().isEmpty() || player.isInvisible()) {
			return;
		}
		HumanoidArm arm = player.getMainArm().getOpposite();
		poseStack.pushPose();
		if (FirstPersonAnim.applyOffhandArm(poseStack, arm, frameInterp)) {
			if (FirstPersonAnim.kunaiInHand(frameInterp)) {
				// 그림자 표창: 손끝에 쥔 표창 (칼끝이 앞 · 조금 위)
				int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
				org.joml.Vector3f tip = FirstPersonAnim.handTip(invert);
				poseStack.pushPose();
				poseStack.translate(tip.x, tip.y, tip.z);
				poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(25.0F));
				poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-invert * 20.0F));
				this.renderItem(player, kr.overbreak.client.anim.ShadeAnim.kunaiStack(),
						arm == HumanoidArm.RIGHT ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
								: net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack, submitNodeCollector, lightCoords);
				poseStack.popPose();
			}
			this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, arm);
		}
		poseStack.popPose();
	}
}
