package kr.overbreak.classes.gunslinger;

import org.jspecify.annotations.Nullable;

/** 건슬링어 진행 상태 — 탄창 · 재장전 · 체공 훈풍 · 진행 중인 스킬. */
public final class GunslingerState {
	/** 쌍권총 탄창 (18발). */
	public int ammo = DualPistols.MAG;
	/** 재장전 남은 틱 (0 = 재장전 중 아님). */
	public int reloadT;
	/** 다음 발까지 남은 틱. */
	public int shotCd;
	/** 다음 발이 나갈 총구 — false 오른손, true 왼손 (번갈아). */
	public boolean leftMuzzle;

	/** 활공 남은 틱 (체공 훈풍). 땅에 닿으면 다시 가득 찹니다. */
	public int glideT = AeroDrift.GLIDE_TICKS_INIT;
	/** 지금 활공 중인가 (웅크리기 + 공중). */
	public boolean gliding;
	/** 발이 땅에 닿아 있었는가 (직전 틱) — 착지 순간을 잡습니다. */
	public boolean wasGround = true;

	public @Nullable AeroAcrobatics acrobatics;
	public @Nullable AerialBombardment bombardment;

	/** 무적 프레임 중인가 (곡예 난사). */
	public boolean iframes() {
		return acrobatics != null && acrobatics.spinning();
	}

	public boolean inUlt() {
		return bombardment != null;
	}
}
