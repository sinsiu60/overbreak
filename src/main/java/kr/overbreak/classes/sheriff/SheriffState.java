package kr.overbreak.classes.sheriff;

import org.jspecify.annotations.Nullable;

/** 보안관 진행 상태 — 데이터팩 pvp.sh_* 점수들. */
public final class SheriffState {
	/** 리볼버 탄창 (6발). */
	public int ammo = Peacekeeper.MAG;
	/** 재장전 남은 틱 (0 = 재장전 중 아님). */
	public int reloadT;
	/** 다음 발까지 (틱, 데이터팩 sh_cd). */
	public int shotCd;
	/** 구르기 피해 감소 남은 틱 (데이터팩 sh_dr). */
	public int guardT;

	@Nullable FanHammer fan;
	@Nullable CombatRoll roll;
	@Nullable Deadeye deadeye;

	public boolean inUlt() {
		return deadeye != null;
	}
}
