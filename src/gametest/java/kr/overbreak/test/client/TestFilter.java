package kr.overbreak.test.client;

/**
 * 클라이언트 시험 골라 돌리기 — 환경 변수 OVERBREAK_CLIENT_TEST 에 클래스 이름(쉼표로 여러 개)을 적으면 그 시험만 돌립니다.
 * 비어 있으면 전부. 예: OVERBREAK_CLIENT_TEST=ValkyrieClientTest ./gradlew runClientGameTest
 */
public final class TestFilter {
	private TestFilter() {}

	public static boolean skip(Class<?> test) {
		String only = System.getenv("OVERBREAK_CLIENT_TEST");
		if (only == null || only.isBlank()) {
			return false;
		}
		for (String name : only.split(",")) {
			if (name.trim().equals(test.getSimpleName())) {
				return false;
			}
		}
		return true;
	}
}
