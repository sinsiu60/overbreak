package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * OVERBREAK 조작 모드에서 좌클릭 — 바닐라 공격 · 블록 부수기 · 팔 휘두르기를 하지 않고 서버에 좌클릭 신호만 보냅니다.
 * 기본 공격이 실제로 나갈지(공격속도 · 정신집중 · 기절)는 서버가 정하고, 나갈 때만 기본 공격 애니메이션이 옵니다.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
	/** 좌클릭을 누른 순간. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void overbreak$leftClick(CallbackInfoReturnable<Boolean> cir) {
		if (InputMode.active()) {
			InputMode.click();
			cir.setReturnValue(false);
		}
	}

	/** 좌클릭을 누르고 있는 동안 매 틱 — 공격속도만큼 연속 공격. */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void overbreak$leftHold(boolean down, CallbackInfo ci) {
		if (InputMode.active()) {
			if (down) {
				InputMode.hold();
			}
			ci.cancel();
		}
	}
}
