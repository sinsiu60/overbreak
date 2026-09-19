package kr.overbreak.mixin;

import kr.overbreak.core.tick.MovementScale;
import kr.overbreak.core.tick.TickRateConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 느린 낙하 보정 — 바닐라는 느린 낙하 중 중력을 고정값 0.01 로 누르는데, 중력 속성은 {@link MovementScale} 이 틱레이트만큼 줄여 두어서
 * 60틱에서는 0.01 이 오히려 더 커져 거의 효과가 없어집니다. 고정값도 같은 배율로 줄입니다 (차원 도약 등).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySlowFallMixin {
	@ModifyConstant(method = "getEffectiveGravity", constant = @Constant(doubleValue = 0.01))
	private double overbreak$tickRateSlowFall(double value) {
		double k = TickRateConfig.scale();
		return Math.abs(k - 1.0) > 1.0E-6 ? value * MovementScale.factors(k).gravity() : value;
	}
}
