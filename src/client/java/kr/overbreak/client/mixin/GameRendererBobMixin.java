package kr.overbreak.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import kr.overbreak.client.camera.WeaponBob;
import kr.overbreak.client.input.InputMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 걸음 흔들림 — 직업이 있는 동안(조작 모드):
 *   화면(카메라)은 걸어도 흔들리지 않고 (바닐라 화면 흔들림 끔 — 설정값은 건드리지 않음)
 *   1인칭 손 · 무기만 발걸음마다 툭툭 위아래로 ({@link WeaponBob}, 오버워치 1인칭처럼). 설정의 화면 흔들림을 꺼 둬도 무기는 움직입니다.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void overbreak$noViewBob(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (InputMode.active()) {
			ci.cancel();
		}
	}

	/**
	 * 피격 기울기 — 연사에 맞으면 화면이 계속 덜컹거려 조준이 불가능해집니다.
	 * 대신 오버워치식 방향 표시로 알려 줍니다 ({@link kr.overbreak.client.hud.DamageFeedback}).
	 */
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void overbreak$noHurtTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		if (InputMode.active()) {
			ci.cancel();
		}
	}

	/** 전술 구르기 1인칭 화면 기울기 — 월드 화면 행렬에만 (손 · 총은 화면에 붙어 있음). */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			shift = At.Shift.AFTER))
	private void overbreak$rollCamera(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci, @Local PoseStack bobStack) {
		if (InputMode.active()) {
			kr.overbreak.client.camera.RollCamera.apply(bobStack, deltaTracker.getGameTimeDeltaPartialTick(false));
		}
	}

	@Inject(method = "renderItemInHand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
			shift = At.Shift.AFTER))
	private void overbreak$weaponBob(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci,
									 @Local PoseStack poseStack) {
		if (InputMode.active()) {
			WeaponBob.apply(poseStack, cameraState.entityRenderState.backwardsInterpolatedWalkDistance, cameraState.entityRenderState.bob);
		}
	}
}
