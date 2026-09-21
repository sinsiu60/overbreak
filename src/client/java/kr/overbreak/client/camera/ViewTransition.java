package kr.overbreak.client.camera;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

/**
 * 3인칭 → 1인칭 전환을 0.3초 동안 부드럽게 (0.2e).
 *
 * 시점이 3인칭(뒤 · 앞)에서 1인칭으로 바뀌는 순간부터 0.3초 동안 카메라를 떼어 둔 채(3인칭처럼)
 * 뒤로 물러나 있던 거리를 0 까지 당겨 머리 속으로 들어옵니다 (CameraTransitionMixin).
 * 화면이 멈춰도 자연스럽도록 게임 시간이 아니라 실제 시간으로 잽니다.
 */
public final class ViewTransition {
	/** 전환 시간 (초). */
	public static final double SECONDS = 0.3;

	/** 전환을 시작한 시각 (나노초 · 0 = 전환 중 아님). */
	private static long startedAt;

	private ViewTransition() {}

	/** 시점이 바뀌는 순간 (Options.setCameraType). */
	public static void changed(CameraType from, CameraType to) {
		if (!from.isFirstPerson() && to.isFirstPerson()) {
			startedAt = System.nanoTime();
		} else if (!to.isFirstPerson()) {
			startedAt = 0L;
		}
	}

	/** 0 → 1 진행도 (전환 중이 아니면 1). */
	private static double progress() {
		if (startedAt == 0L) {
			return 1.0;
		}
		double x = (System.nanoTime() - startedAt) / 1.0E9 / SECONDS;
		if (x >= 1.0) {
			startedAt = 0L;
			return 1.0;
		}
		return x;
	}

	/** 지금 전환 중인가 — 이 동안 카메라는 몸에서 떨어져 있습니다. */
	public static boolean active() {
		return progress() < 1.0;
	}

	/** 3인칭 거리에 곱할 배율 (1 → 0, 부드럽게 들어옴). */
	public static float distanceScale() {
		double x = progress();
		double e = x < 0.5 ? 4.0 * x * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 3) / 2.0;
		return (float) Mth.clamp(1.0 - e, 0.0, 1.0);
	}
}
