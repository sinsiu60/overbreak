package kr.overbreak.client.mixin;

import kr.overbreak.core.tick.Ticks;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 파티클은 1/20초마다만 갱신 — 바닐라 파티클의 수명 · 속도 · 중력은 틱 단위라 60틱이면 3배 빨리 움직이고 사라집니다.
 * (서버가 뿌리는 연출 파티클이 60틱에서도 20틱과 같은 모습 · 시간으로 보이게)
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Unique
	private double overbreak$acc;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void overbreak$vanillaCadence(CallbackInfo ci) {
		overbreak$acc += Ticks.step();
		if (overbreak$acc < 1.0 - 1.0E-9) {
			ci.cancel();
			return;
		}
		overbreak$acc -= 1.0;
	}
}
