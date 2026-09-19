package kr.overbreak.skill;

/**
 * 스킬 HUD 추가 표시.
 *
 * @param ammo      남은 탄 (-1 = 탄창 없음)
 * @param ammoMax   탄창 크기
 * @param meter     조준점 아래 게이지 0~100 (-1 = 없음)
 * @param meterKind 1 = 충전 (파랑), 2 = 방어량 (회색, 가득 차면 금색), 3 = 남은 지속시간 (금색), 4 = 재장전 (흰색)
 * @param stacks    조준점 아래 칸에 채울 스택 (발키리 미사일까지 남은 명중)
 * @param stacksMax 칸 수 (0 = 표시 안 함)
 * @param flags     화면 연출 깃발 ({@link #FLAG_HASTE} 등)
 */
public record HudExtra(int ammo, int ammoMax, int meter, int meterKind, int stacks, int stacksMax, int flags) {
	public static final HudExtra NONE = new HudExtra(-1, 0, -1, 0, 0, 0, 0);
	public static final int METER_RELOAD = 4;
	/** 공격속도가 올라간 상태 — 화면 가장자리가 노랗게 빛납니다. */
	public static final int FLAG_HASTE = 1;

	public HudExtra(int ammo, int ammoMax, int meter, int meterKind) {
		this(ammo, ammoMax, meter, meterKind, 0, 0, 0);
	}

	public HudExtra(int ammo, int ammoMax, int meter, int meterKind, int stacks, int stacksMax) {
		this(ammo, ammoMax, meter, meterKind, stacks, stacksMax, 0);
	}

	public static final int METER_CHARGE = 1;
	public static final int METER_GUARD = 2;
	/** 남은 지속시간 (금색, 줄어듦) — 탄막 포격. */
	public static final int METER_DURATION = 3;
}
