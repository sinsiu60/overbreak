package kr.overbreak.core.tick;

/**
 * 게임 시간 단위 ↔ 틱 변환 — 2단계 (서버 · 클라이언트 모두 틱레이트대로 도는 구조).
 *
 * 직업 · 애니메이션 수치는 역사적으로 <b>1/20초 단위</b>(바닐라 20틱의 1틱 = 50ms)로 적혀 있습니다 ("쿨타임 160" = 8초).
 * 이 숫자들은 "시간" 으로 그대로 두고, 틱 세계에 들어가는 순간 여기서 지금 틱레이트의 값으로 바꿉니다:
 *
 *   of(160)          시간 160 (8초) → 틱 수 (20틱 160, 60틱 480)
 *   speed(0.67)      시간 1 당 0.67칸 이동 → 틱당 칸 (60틱이면 0.223)
 *   damping(0.9)     시간 1 마다 0.9 배로 줄어드는 값 → 틱마다 곱할 배율 (0.9^(1/3))
 *   every(t, 5)      틱 카운터 t 가 시간 5 마다 한 번 참
 *   toTime(ticks)    틱 수 → 시간 단위 (클라이언트에 보내는 남은 시간 등)
 *
 * 초로 쓰고 싶을 때는 {@link TickRateConfig#ticks(double)} — 둘은 같은 계산입니다 (of(n) = ticks(n / 20)).
 */
public final class Ticks {
	private Ticks() {}

	/** 틱 하나가 몇 시간 단위인가 (20틱 1.0, 60틱 0.333). */
	public static double step() {
		return TickRateConfig.VANILLA / (double) TickRateConfig.tickRate();
	}

	/** 틱레이트 배수 (60틱이면 3). */
	public static double k() {
		return TickRateConfig.tickRate() / (double) TickRateConfig.VANILLA;
	}

	public static int of(int time) {
		if (time <= 0) {
			return time;
		}
		return Math.max(1, (int) Math.round(time * k()));
	}

	public static int of(double time) {
		if (time <= 0.0) {
			return 0;
		}
		return Math.max(1, (int) Math.round(time * k()));
	}

	/** 틱 수 → 연속 시간 (반올림 없음, 20틱 t, 60틱 t/3). */
	public static double time(long ticks) {
		return ticks * step();
	}

	/** 한 번 뿌리는 연출이 아니라 매 틱 뿌리는 파티클 — 시간 단위 경계에서만 참 ({@link GameClock#unit()}). */
	public static boolean ambient() {
		return GameClock.unit();
	}

	/** 틱 수 → 시간 단위 (반올림). */
	public static int toTime(int ticks) {
		if (ticks <= 0) {
			return ticks;
		}
		return Math.max(1, (int) Math.round(ticks * step()));
	}

	public static double speed(double perTime) {
		return perTime * step();
	}

	public static float speed(float perTime) {
		return (float) (perTime * step());
	}

	public static double damping(double perTime) {
		return Math.pow(perTime, step());
	}

	/** 틱 카운터 t 기준으로 시간 period 마다 한 번 (60틱이면 3배 틱마다). */
	public static boolean every(long t, int period) {
		int p = of(period);
		return p <= 1 || t % p == 0;
	}
}
