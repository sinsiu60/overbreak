package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 달리기 없음 — 직업이 있는 동안(조작 모드) 달리기 키 · 앞으로 두 번 · 자동 달리기 모두 시작하지 않고, 달리던 중이면 멈춥니다.
 * 대신 기본 속도가 달리기 속도입니다 (서버 Classes.SPRINT_BASE).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {
	@Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
	private void overbreak$noSprint(CallbackInfoReturnable<Boolean> cir) {
		if (InputMode.active()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
	private void overbreak$stopSprint(CallbackInfoReturnable<Boolean> cir) {
		if (InputMode.active()) {
			cir.setReturnValue(true);
		}
	}
}
