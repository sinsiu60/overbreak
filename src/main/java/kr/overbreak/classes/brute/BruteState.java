package kr.overbreak.classes.brute;

import org.jspecify.annotations.Nullable;

/** 투귀 한 명의 진행 상태. */
final class BruteState {
	/** 투기 스택 (0 ~ {@link Fervor#MAX}). */
	int fervor;
	/** 스택이 풀리기까지 남은 틱. */
	int fervorT;
	/** 지금 걸어 둔 이동속도 보정에 쓰인 스택 (바뀔 때만 속성을 다시 겁니다). */
	int fervorApplied = -1;
	/** 무쌍(궁극기) 남은 틱 — 도는 동안 투기가 최대로 고정됩니다. */
	int rampageT;
	/** 진행 중인 강타 (없으면 null). */
	@Nullable HeavyBlow blow;
	/** 진행 중인 돌개바람. */
	@Nullable Whirl whirl;
	/** 진행 중인 전열 재정비. */
	@Nullable Regroup regroup;
}
