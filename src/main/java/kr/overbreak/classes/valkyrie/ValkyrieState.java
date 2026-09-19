package kr.overbreak.classes.valkyrie;

import org.jspecify.annotations.Nullable;

/** 발키리 진행 상태 — 데이터팩 pvp.vk_* · mis_* · oh_* · br_* 점수들. */
public final class ValkyrieState {
	// ── 연사 ──
	/** 이번 틱에 좌클릭이 들어왔는가 (직업 틱에서 읽고 지웁니다). */
	public boolean trigger;
	/** 연달아 붙잡고 쏜 틱 수 — 탄퍼짐 (데이터팩 vk_spr). 손을 떼면 0. */
	public int holdT;
	/** 다음 발까지 (틱). */
	public int fireCd;
	/** 공중 가속 중 3 · 3 · 2 순서 (데이터팩 vk_seq). */
	public int seq;
	/** 탄창 (50발). */
	public int ammo = Rifle.MAG;
	/** 재장전 남은 틱 (0 = 재장전 중 아님). */
	public int reloadT;

	// ── 미사일 ──
	public int missileN;
	/** 마지막 명중 뒤 남은 시간 (틱). 0 이 되면 세던 것이 풀립니다. */
	public int missileT;

	// ── 차원 도약 ──
	/** 차원 도약으로 뜬 상태 (착지하면 끝). */
	public boolean floating;
	/** 이륙 직후 착지로 보지 않는 틱. */
	public int floatGrace;
	/** 지금 연사 1.5배 (뜬 상태 + 땅에 안 닿음). */
	public boolean boosted;

	@Nullable Overheat overheat;
	@Nullable Barrage barrage;

	public boolean inUlt() {
		return barrage != null;
	}
}
