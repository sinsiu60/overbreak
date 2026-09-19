package kr.overbreak.core.tick;

/**
 * 틱레이트 단일 진입점 — 로직 코드는 틱 수를 직접 쓰지 않고 초 · 초당 값으로 정의해 여기서 바꿉니다.
 *
 *   ticks(0.7)       0.7초 → 지금 틱레이트의 틱 수 (20틱이면 14, 60틱이면 42)
 *   perTick(22.0)    초당 22칸 → 틱당 칸 (20틱이면 1.1, 60틱이면 0.367)
 *   seconds(42)      42틱 → 초 (HUD 표시)
 *
 * OVERBREAK 는 1초 60틱 고정입니다 ({@link #DEFAULT}). 클라이언트도 같은 값 — 바닐라가 서버 틱레이트를 보내 주면 {@link #set} 이 갱신합니다.
 */
public final class TickRateConfig {
	/** 시간 단위 기준 (1/20초) — 직업 수치가 이 단위로 적혀 있어 틱으로 바꿀 때만 씁니다. 20틱으로 돌리지 않습니다. */
	public static final int VANILLA = 20;
	public static final int DEFAULT = 60;

	private static volatile int rate = DEFAULT;

	private TickRateConfig() {}

	public static int tickRate() {
		return rate;
	}

	static void set(int ticksPerSecond) {
		rate = Math.max(1, ticksPerSecond);
	}

	/** 초 → 틱 (반올림, 0초가 아니면 최소 1틱). */
	public static int ticks(double seconds) {
		if (seconds <= 0.0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(seconds * rate));
	}

	/** 초당 값 → 틱당 값. */
	public static double perTick(double perSecond) {
		return perSecond / rate;
	}

	/** 틱 → 초. */
	public static double seconds(int ticks) {
		return ticks / (double) rate;
	}

	/** 바닐라 20틱 기준 배수 (60틱이면 3). 이동 보정에만 씁니다. */
	public static double scale() {
		return rate / (double) VANILLA;
	}
}
