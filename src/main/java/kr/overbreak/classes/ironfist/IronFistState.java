package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import org.jspecify.annotations.Nullable;

/** 파쇄권 진행 상태 — 데이터팩 pvp.if_* 점수들. */
public final class IronFistState {
	// ── 철권포 ──
	public int ammo = HandCannon.MAG;
	/** 다음 한 발이 찰 때까지 (틱). 쏠 때마다 0.7초로 되돌아갑니다. */
	public int reloadT = kr.overbreak.core.tick.Ticks.of(HandCannon.RELOAD);
	/** 다음 발사까지 (1/20 초 단위, 0 이하면 발사 가능). 한 발 = 20, 틱당 3 씩 줄어듦. */
	public int fireCd;

	// ── 최선의 방어는 (흡수 체력, 10배 기준) ──
	public int absorb;
	/** 마지막으로 얻은 뒤 감쇠가 시작될 때까지 (틱). */
	public int absorbHold;
	/** 흡수 체력 감소 누적 (시간 단위) — 1 이 찰 때마다 1 감소 = 초당 20. */
	double absorbDecay;

	/** 땅에 닿지 않은 연속 틱 (지진 강타 체공 보정). */
	public int air;

	/** 로켓 펀치 강화 남은 시간 (틱, 데이터팩 if_emp). 0 이면 없음. */
	public int empowerT;

	@Nullable RocketPunch punch;
	@Nullable PowerBlock block;
	@Nullable SeismicSlam slam;
	@Nullable MeteorStrike doom;

	public boolean charging() {
		return punch != null && punch.charging();
	}

	public boolean blocking() {
		return block != null;
	}

	public boolean flying() {
		return slam != null;
	}

	public boolean inUlt() {
		return doom != null;
	}
}
