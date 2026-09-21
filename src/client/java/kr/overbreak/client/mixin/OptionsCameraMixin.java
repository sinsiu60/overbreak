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
		if (type == CameraType.THIRD_PERSON_FRONT && InputMode.active()) {
			type = CameraType.FIRST_PERSON;
		}
		// 스킬이 3인칭을 잡고 있는 동안(돌진 난사)은 F5 로도 1인칭이 되지 않습니다 (0.2e) — 풀 때는 잡기를 먼저 놓아서 여기 걸리지 않음
		if (type == CameraType.FIRST_PERSON && kr.overbreak.client.camera.ViewLock.held()) {
			type = CameraType.THIRD_PERSON_BACK;
		}
		// 3인칭 → 1인칭이면 0.3초 동안 카메라를 당겨 들어옴 (0.2e)
		kr.overbreak.client.camera.ViewTransition.changed(((Options) (Object) this).getCameraType(), type);
		return type;
	}
}
