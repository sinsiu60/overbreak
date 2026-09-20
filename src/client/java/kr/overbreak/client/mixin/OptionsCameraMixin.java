package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * F5 시점 전환에서 "내 얼굴이 보이는" 정면 3인칭을 뺍니다 — 전장에서는 1인칭과 어깨 너머 3인칭 둘만 돕니다.
 *
 * 바닐라 순환은 1인칭 → 뒤에서 → 앞에서 → 1인칭 이라, 앞에서 보기가 들어오면 곧바로 1인칭으로 넘깁니다.
 * 설정값 자체는 건드리지 않고 들어오는 값만 바꿔서, 직업이 없을 때(로비 · 관전)는 바닐라 그대로입니다.
 */
@Mixin(Options.class)
public abstract class OptionsCameraMixin {
	@ModifyVariable(method = "setCameraType", at = @At("HEAD"), argsOnly = true)
	private CameraType overbreak$skipFrontView(CameraType type) {
		return type == CameraType.THIRD_PERSON_FRONT && InputMode.active() ? CameraType.FIRST_PERSON : type;
	}
}
