package kr.overbreak.mixin;

import kr.overbreak.core.tick.TickRateConfig;
import kr.overbreak.core.tick.Ticks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 공중 부양 보정 (에어본) — 바닐라는 틱마다 세로 속도를 목표 0.05 x (증폭 + 1) 칸/틱 쪽으로 20% 씩 당깁니다.
 * 60틱에서는 목표 속도를 틱 길이만큼 줄이고, 당기는 비율을 1 - 0.8^(1/3) 로 바꿔 초당 같은 상승 곡선을 냅니다
 * (공기 저항은 MovementScale 의 air_drag_modifier 가 맞춤). 서버 · 클라이언트 모두.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLevitationMixin {
	@ModifyConstant(method = "travelInAir", constant = @Constant(doubleValue = 0.05))
	private double overbreak$levitationTarget(double value) {
		return scaled() ? value * Ticks.step() : value;
	}

	@ModifyConstant(method = "travelInAir", constant = @Constant(doubleValue = 0.2))
	private double overbreak$levitationRate(double value) {
		return scaled() ? 1.0 - Math.pow(1.0 - value, Ticks.step()) : value;
	}

	private static boolean scaled() {
		return Math.abs(TickRateConfig.scale() - 1.0) > 1.0E-6;
	}
}
