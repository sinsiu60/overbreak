package kr.overbreak.classes.warrior;

/**
 * 피의 사슬 준비 동작의 공통 박자 — 서버(갈고리 모델)와 클라이언트(팔 애니메이션)가 같은 각도로 돕니다.
 *
 *   준비 14틱(0.7초) 동안 사슬을 돌림. 각도 = 30t + 1.5t² 도 (점점 빨라져 약 두 바퀴)
 *   t 는 서버 기준 준비 틱 (시전한 틱의 다음 틱이 1). 클라이언트는 패킷 도착 경과 + 1 을 넣습니다.
 */
public final class ChainSpin {
	public static final int WINDUP_TICKS = 8;

	private ChainSpin() {}

	public static double angleDeg(double t) {
		return 30.0 * t + 1.5 * t * t;
	}
}
