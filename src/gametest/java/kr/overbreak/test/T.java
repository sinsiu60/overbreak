package kr.overbreak.test;

import kr.overbreak.core.tick.Ticks;

/**
 * 시험의 시간 — 시험 코드의 숫자는 1/20초 단위로 적고, 틱 세계(runAfterDelay · 틱 필드 비교 · 직접 돌리는 틱 수)에 넣을 때 여기서 바꿉니다.
 * 서버는 1초 60틱 고정이라 T.of(20) = 60틱. {@code @GameTest(maxTicks)} 는 상수여야 해서 60틱 수로 적습니다.
 */
final class T {
	private T() {}

	/** 시간 → 틱 수. */
	static int of(int time) {
		return Ticks.of(time);
	}

	/** 틱 수 → 시간 (반올림). */
	static long time(long ticks) {
		return Math.round(ticks * Ticks.step());
	}
}
