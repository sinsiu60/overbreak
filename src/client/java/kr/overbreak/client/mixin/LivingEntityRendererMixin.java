package kr.overbreak.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.KnockRenderState;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 넘어뜨림 — 대상 모델이 발을 축으로 뒤로 쓰러져 누워 있다가 일어납니다 (플레이어 · 몹 공통).
 *   0~4틱 쓰러짐 · 끝나기 6틱 전부터 일어남 (서버가 먼저 풀면 그 순간부터)
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final float FALL_TICKS = 4.0F;
	private static final float RISE_TICKS = 6.0F;
	private static final float ROLL_TICKS = 7.0F;

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void overbreak$knockState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		SkillAnims.Play play = SkillAnims.find(entity.getId(), SkillAnimPayload.KNOCKDOWN);
		float progress = 0.0F;
		if (play != null) {
			float e = play.elapsed(partialTicks);
			float fall = Mth.clamp(e / FALL_TICKS, 0.0F, 1.0F);
			fall = 1.0F - (1.0F - fall) * (1.0F - fall);
			float riseStart = Math.min(play.planned() - RISE_TICKS, play.end());
			float rise = Mth.clamp((e - riseStart) / RISE_TICKS, 0.0F, 1.0F);
			progress = fall * (1.0F - rise * rise * (3.0F - 2.0F * rise));
		}
		((KnockRenderState) state).overbreak$setKnock(progress);

		// 전술 구르기: 이동 방향으로 앞구르기 한 바퀴 (0.35초 동안, 빠르게 시작해 천천히 끝남)
		SkillAnims.Play roll = SkillAnims.find(entity.getId(), SkillAnimPayload.SH_ROLL);
		float angle = 0.0F;
		float ax = 0.0F;
		float az = 0.0F;
		if (roll != null) {
			float x = Mth.clamp(roll.elapsed(partialTicks) / ROLL_TICKS, 0.0F, 1.0F);
			angle = Mth.TWO_PI * (1.0F - (1.0F - x) * (1.0F - x));
			double dx = entity.getX() - entity.xo;
			double dz = entity.getZ() - entity.zo;
			if (dx * dx + dz * dz < 1.0E-4) {
				double yaw = Math.toRadians(entity.getYRot());
				dx = -Math.sin(yaw);
				dz = Math.cos(yaw);
			}
			double len = Math.sqrt(dx * dx + dz * dz);
			// 앞구르기 축 = 위 x 이동 방향
			ax = (float) (dz / len);
			az = (float) (-dx / len);
			if (x >= 1.0F) {
				angle = 0.0F;
			}
		}
		((KnockRenderState) state).overbreak$setRoll(angle, ax, az);
	}

	/** 뇌신 섬전: 번개가 되어 달리는 동안 모습(몸 · 든 물건)을 그리지 않음. */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true)
	private void overbreak$lightningForm(LivingEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
										 net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		if (state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar
				&& SkillAnims.playing(avatar.id, SkillAnimPayload.TH_DASH)) {
			ci.cancel();
		}
	}

	/** 그림자 가르기 잔상 — 플레이어를 그린 뒤 같은 모델을 지나간 자리에 파랗게 다시 그림. */
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("TAIL"))
	private void overbreak$afterimages(LivingEntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
									   net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		if ((Object) this instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar
				&& state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState) {
			kr.overbreak.client.fx.Afterimages.submit(avatar, avatarState, poseStack, collector);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void overbreak$knockPose(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		KnockRenderState ks = (KnockRenderState) state;
		if (ks.overbreak$rollAngle() != 0.0F) {
			// 이미 몸 방향(180 - bodyRot)으로 돌아간 좌표계라, 월드 축을 몸 기준으로 되돌려 허리 높이를 축으로 돌림
			org.joml.Vector3f axis = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-(180.0F - bodyRot)))
					.transform(new org.joml.Vector3f(ks.overbreak$rollAxisX(), 0.0F, ks.overbreak$rollAxisZ()));
			poseStack.translate(0.0F, 0.9F * entityScale, 0.0F);
			poseStack.mulPose(new org.joml.Quaternionf().rotationAxis(ks.overbreak$rollAngle(), axis));
			poseStack.translate(0.0F, -0.9F * entityScale, 0.0F);
		}
		float k = ks.overbreak$knock();
		if (k <= 0.0F) {
			return;
		}
		// 몸 두께만큼 살짝 띄워 바닥에 파묻히지 않게, 발을 축으로 뒤로 눕힘
		poseStack.translate(0.0F, 0.18F * k, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F * k));
	}
}
