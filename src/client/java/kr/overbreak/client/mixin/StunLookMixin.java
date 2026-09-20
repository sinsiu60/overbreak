package kr.overbreak.client.mixin;

import kr.overbreak.client.hud.HudState;
import kr.overbreak.client.input.InputMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 화면이 움직이지 않아야 할 때 마우스 회전을 막습니다 (0.2a).
 *
 *   기절 중 · 파멸의 일격으로 조준하는 중 (그때는 시야가 착탄점을 따라갑니다).
 *
 * 기절은 "아무것도 못 하는" 제어기인데 시야만은 자유로우면 맞은 느낌이 흐려집니다.
 * 기절 동안 마우스로 도는 것을 막아, 맞은 자리에 그대로 굳어 있게 합니다.
 * 서버가 보낸 기절 깃발만 보므로, 기절이 풀리는 순간 곧바로 다시 돌릴 수 있습니다.
 */
@Mixin(Entity.class)
public abstract class StunLookMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void overbreak$stunLocksView(double yaw, double pitch, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		if (InputMode.active() && (HudState.stunned() || kr.overbreak.client.camera.DoomCamera.holding())) {
			ci.cancel();
		}
	}
}
