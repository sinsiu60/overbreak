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
	/** 지금 활공 중인가 (공중 + 점프 키). */
	public boolean gliding;
	/** 활공 동작을 보여 주고 있는가 — 재장전 중에는 띄워도 동작을 꺼 둡니다. */
	public boolean glideAnim;
	/** 발이 땅에 닿아 있었는가 (직전 틱) — 착지 순간을 잡습니다. */
	public boolean wasGround = true;
	/** 지난 틱 높이 — 떨어지기 시작했는지 잽니다. */
	public double lastY = Double.NaN;
	/** 이번 체공에서 떨어지기 시작했는가 (정점을 지났거나 난간에서 발을 떼냈음). 착지하면 다시 false. */
	public boolean descending;

	public @Nullable DashScatter scatter;
	/** 궤적 해방 (궁극기) — 수집 · 예고 동안. */
	public @Nullable TrailRelease release;

	public boolean inUlt() {
		return release != null;
	}
}
