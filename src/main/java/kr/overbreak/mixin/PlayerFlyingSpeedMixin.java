package kr.overbreak.mixin;

import kr.overbreak.core.tick.MovementScale;
import kr.overbreak.core.tick.TickRateConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 공중 가속 보정 — 바닐라 플레이어의 공중 · 비행 가속(0.02 등)은 속성이 아닌 고정값이라 틱레이트를 올리면 공중에서 k배 빨라집니다.
 * {@link MovementScale} 의 나머지 보정과 같은 기준으로 줄입니다. 서버 · 클라이언트 모두 (플레이어 이동은 클라이언트가 계산).
 */
@Mixin(Player.class)
public abstract class PlayerFlyingSpeedMixin {
	@Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
	private void overbreak$tickRateAirAccel(CallbackInfoReturnable<Float> cir) {
		double k = TickRateConfig.scale();
		if (Math.abs(k - 1.0) > 1.0E-6) {
			cir.setReturnValue((float) (cir.getReturnValue() * MovementScale.factors(k).airAccel()));
		}
	}
}
