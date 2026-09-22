package kr.overbreak.classes.ironcleaver;

/**
 * 참철 수치 — 시간은 1/20초 단위(20 = 1초), 거리는 칸, 피해는 x100 (dealFine).
 * 한곳에 모아 두어 여기만 고치면 됩니다 (틱 수는 Ticks.of 로 바꿔 씀 — 60틱에서도 같은 초).
 */
public final class IronSpec {
	private IronSpec() {}

	// ── 스탯 · 패시브 「중량」 ─────────────────────────────
	/** 체력 260 (기본 200 + 60) · 이동속도 -5%. */
	public static final double HEALTH_BONUS = 60.0;
	public static final double MOVE_SPEED = -0.05;
	/** 동작 중 받는 넉백 50% 감소 (넉백 저항 +0.5). */
	public static final double ACTION_KB_RESIST = 0.5;
	/** 방어 파쇄 — 이 단계 이상 모아 벤 참격은 대상의 피해 감소를 절반만 받음. */
	public static final int GUARD_BREAK_STAGE = 2;
	public static final float GUARD_BREAK_FACTOR = 0.5F;

	// ── LMB 판별 · 평타 ─────────────────────────────────
	/** 이만큼 누르고 있으면 모아 베기로 (0.25초). */
	public static final double HOLD = 5.0;
	/** 마지막 동작 뒤 이만큼 입력이 없으면 1타로 (1.2초). */
	public static final int COMBO_RESET = 24;
	/** 휘두르는 동안 이동속도 ×0.8. */
	public static final double SWING_SPEED = -0.2;

	/** 평타 한 타 — 선딜 · 판정 · 후딜 (시간 단위), 피해 x100, 부채꼴 각도(도, 0 이면 직선) · 직선 폭 · 사거리. */
	public record Swing(int anim, double windup, double active, double recovery, int damage100, double arc, double width,
						double range, boolean rightToLeft) {
		public double total() {
			return windup + active + recovery;
		}
	}

	public static final Swing[] SWINGS = {
			new Swing(kr.overbreak.net.SkillAnimPayload.IC_SWING_R, 7.0, 2.0, 9.0, 4500, 150.0, 0.0, 4.0, true),
			new Swing(kr.overbreak.net.SkillAnimPayload.IC_SWING_L, 7.0, 2.0, 9.0, 4500, 150.0, 0.0, 4.0, false),
			new Swing(kr.overbreak.net.SkillAnimPayload.IC_OVERHEAD, 9.0, 2.0, 11.0, 9000, 0.0, 1.5, 4.5, false)};
	/** 3타 내려찍기는 맞으면 치명타 판정 (치명타 표시 · 소리만 — 피해는 그대로). */
	public static final int CRIT_SWING = 2;
	/** 판정 높이 — 발밑 -0.5 ~ 머리 위 +1.0. */
	public static final double SWING_LOW = -0.5;
	public static final double SWING_HIGH = 1.0;

	// ── 참 모으기 ──────────────────────────────────────
	/** 모으는 동안 이동속도 ×0.7. */
	public static final double CHARGE_SPEED = -0.3;
	/** 단계 도달 시각 (누른 순간부터) — 1단 0.6초 · 2단 1.2초 · 3단 1.8초. */
	public static final double[] STAGE_AT = {12.0, 24.0, 36.0};
	/** 3단 도달 뒤 진 참 창 (0.2초) · 자동 발동 (3단 뒤 1.0초). */
	public static final double PERFECT = 4.0;
	public static final double AUTO_AFTER = 20.0;
	/** 단계별 피해 x100 · 사거리 (1단 · 2단 · 3단 · 진 참). 부채꼴 60°. */
	public static final int[] RELEASE_DAMAGE = {5000, 8000, 14000, 13000};
	public static final double[] RELEASE_RANGE = {5.0, 5.0, 5.5, 6.0};
	public static final double RELEASE_ARC = 60.0;
	/** 판정 (0.12초) · 후딜 (1·2단 0.6초 · 3단 · 진 참 0.8초). */
	public static final double RELEASE_ACTIVE = 2.4;
	public static final double[] RELEASE_RECOVERY = {12.0, 12.0, 16.0, 16.0};
	/** 판정 높이 — 발밑 -0.5 ~ 머리 위 +1.5. */
	public static final double RELEASE_HIGH = 1.5;
	/** 서버가 본 누름~뗌 구간과 클라이언트 값 허용 오차 (0.15초). */
	public static final double HOLD_TOLERANCE = 3.0;

	// ── RMB 어깨 박치기 ────────────────────────────────
	public static final int BASH_COOLDOWN = 140;
	public static final double BASH_DISTANCE = 6.0;
	public static final int BASH_TIME = 6;
	public static final int BASH_DAMAGE = 2000;
	public static final double BASH_PUSH = 2.0;
	public static final float BASH_TAKEN = 0.5F;

	// ── SHIFT 검막 ────────────────────────────────────
	public static final int GUARD_COOLDOWN = 180;
	public static final int GUARD_TIME = 20;
	public static final double GUARD_FRONT = 120.0;
	public static final float GUARD_TAKEN = 0.2F;
	/** 막기 성공 뒤 이 안에 시작한 모아 베기는 2단(1.2초)부터. */
	public static final int GUARD_REWARD = 30;

	// ── E 대지 가르기 ─────────────────────────────────
	public static final int REND_COOLDOWN = 200;
	/** 선딜 0.25초 — 들어 올림 0.15초 + 긁기 0.1초 뒤 발사. */
	public static final double REND_WINDUP = 3.0;
	public static final double REND_DRAG = 2.0;
	public static final double REND_RECOVERY = 6.0;
	public static final double REND_RANGE = 8.0;
	/** 초당 20칸 = 시간 단위당 1칸. */
	public static final double REND_SPEED = 1.0;
	public static final double REND_WIDTH = 2.0;
	public static final double REND_HEIGHT = 2.5;
	public static final double REND_STEP = 1.0;
	public static final int REND_DAMAGE = 8500;
	public static final double REND_SLOW = 0.4;
	public static final int REND_SLOW_TIME = 30;
	public static final double REND_CAST_SPEED = -0.5;
	/** 발밑 이만큼 안에 땅이 없으면 쓸 수 없음 (공중). */
	public static final double REND_GROUND_REACH = 3.0;

	// ── Q 천참 ────────────────────────────────────────
	public static final double ULT_CHARGE = 24.0;
	public static final double ULT_LENGTH = 14.0;
	public static final double ULT_WIDTH = 4.0;
	public static final int ULT_DAMAGE = 14000;
	public static final double ULT_RECOVERY = 16.0;
	public static final double ULT_LOW = -1.0;
	public static final double ULT_HIGH = 4.0;

	// ── 색 (오오라) ───────────────────────────────────
	public static final int[] STAGE_COLOR = {0xFFFFFF, 0x3CFF6A, 0xFFD84A, 0xFF3B3B};
}
