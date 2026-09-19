package kr.overbreak.mixin;

import kr.overbreak.core.tick.TickRateConfig;
import kr.overbreak.core.tick.Ticks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 최소 속도 보정 — 바닐라는 틱마다 속도가 0.003 칸/틱보다 작으면 0 으로 자릅니다. 60틱에서는 틱당 속도가 1/3 이라
 * 느린 움직임(에어본 부양이 막 시작할 때 등)이 통째로 잘려 멈춥니다. 문턱도 틱 길이만큼 줄입니다. 서버 · 클라이언트 모두.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMinVelocityMixin {
	@ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003))
	private double overbreak$minVelocity(double value) {
		return Math.abs(TickRateConfig.scale() - 1.0) > 1.0E-6 ? value * Ticks.step() : value;
	}
}
