package kr.overbreak.classes.warrior;

import org.jspecify.annotations.Nullable;

/** 워리어 한 명의 진행 상태. */
final class WarriorState {
	/** 광란의 포효 자버프 남은 틱. */
	int furyT;
	/** 광란의 처형장 자버프 남은 틱 (이동속도 +20%, 살육 쿨 2배 감소). */
	int ultSelfT;
	/** 진행 중인 살육 (없으면 null). */
	@Nullable Slay slay;
	/** 날아가고 있거나 끌어오고 있는 피의 사슬. */
	@Nullable BloodChain chain;
}
