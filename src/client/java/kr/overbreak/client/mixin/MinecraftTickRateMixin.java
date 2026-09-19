package kr.overbreak.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 클라이언트도 서버 틱레이트대로 돎 — 바닐라는 max(50ms, 서버 1틱) 이라 서버를 60틱으로 올려도 클라이언트는 1초 20틱에 머뭅니다.
 * 60틱이면 플레이어 이동 · 입력 · 위치 전송이 1초 60번이 되어 서버 판정(되감기)도 17ms 단위가 됩니다.
 * 서버가 멈춤 · 한 틱씩 진행 중이면 바닐라대로.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickRateMixin {
	@Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
	private void overbreak$followServerTickRate(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.level == null) {
			return;
		}
		TickRateManager manager = mc.level.tickRateManager();
		if (manager.runsNormally()) {
			cir.setReturnValue(manager.millisecondsPerTick());
		}
	}
}
