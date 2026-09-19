package kr.overbreak.core.tick;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * 설정 파일의 시간 값 — 초 단위 문자열만 씁니다 ("7.0s", "1.5s", "300ms"). 틱은 설정에 드러내지 않습니다.
 */
public record Duration(double seconds) {
	public static final Codec<Duration> CODEC = Codec.STRING.comapFlatMap(Duration::parse, Duration::toString);

	public static DataResult<Duration> parse(String text) {
		String s = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
		try {
			if (s.endsWith("ms")) {
				return ok(Double.parseDouble(s.substring(0, s.length() - 2).trim()) / 1000.0, text);
			}
			if (s.endsWith("s")) {
				return ok(Double.parseDouble(s.substring(0, s.length() - 1).trim()), text);
			}
		} catch (NumberFormatException e) {
			return DataResult.error(() -> "시간 값을 읽을 수 없음: \"" + text + "\" (예: \"7.0s\", \"300ms\")");
		}
		return DataResult.error(() -> "시간 값에 단위가 없음: \"" + text + "\" (s 또는 ms)");
	}

	private static DataResult<Duration> ok(double seconds, String text) {
		if (seconds < 0.0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
			return DataResult.error(() -> "시간 값은 0 이상이어야 함: \"" + text + "\"");
		}
		return DataResult.success(new Duration(seconds));
	}

	public static Duration ofSeconds(double seconds) {
		return new Duration(seconds);
	}

	/** 지금 틱레이트의 틱 수. */
	public int toTicks() {
		return TickRateConfig.ticks(seconds);
	}

	@Override
	public String toString() {
		return seconds < 1.0 && seconds > 0.0 ? Math.round(seconds * 1000.0) + "ms" : seconds + "s";
	}
}
