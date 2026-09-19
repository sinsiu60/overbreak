package kr.overbreak.core;

/**
 * 모든 생명체(플레이어·더미·몹)에 붙는 전투 상태.
 *
 * 데이터팩의 점수들(pvp.slow_t, pvp.stun_t ...)을 필드 하나씩으로 옮긴 것입니다.
 * CC 는 몹에게도 걸려야 하므로 플레이어 전용 {@link PlayerProfile} 과 나눴습니다.
 * 저장하지 않습니다 — 재접속하면 CC 가 풀린 상태로 들어오는 것이 맞습니다.
 */
public final class Combatant {
	// ── 둔화 ──
	public int slowT;
	/** 감소율 x100. 중첩 비교용 (약한 둔화가 강한 둔화를 덮어쓰지 않음). */
	public int slowAmt;

	// ── 기절 ──
	public int stunT;
	public int stunMax;

	// ── 넘어뜨림 (기절의 상위 호환: 기절 효과 + 실제로 넘어짐) ──
	public int knockT;
	public int knockMax;

	/**
	 * 기절 · 넘어뜨림 한 번마다 바뀌는 번호. 풀려 있던 상태에서 새로 걸릴 때만 1 올라갑니다 (연장 · 덧씌우기는 그대로).
	 * 뇌진탕처럼 "기절 한 번에 한 번" 인 효과가 씁니다.
	 */
	public int ccId;
	/** 뇌진탕이 마지막으로 발동한 CC 번호. */
	public int concussedId = -1;

	// ── 에어본 ──
	public int airT;
	public int airMax;

	// ── 밀쳐내기 (군중 제어) ──
	public int pushT;

	// ── 이동기 봉인 (균열 · 섬광) ──
	public int sealT;

	/** CC 면역 (검의 회방 · 저지불가). 새로 걸리는 것만 막습니다. */
	public boolean ccImmune;

	/** 피해 배율 x100. 평타(속성)와 스킬(이 값) 양쪽에 같이 걸립니다. */
	public int dmgMul = 100;

	// ── 정신집중 ──
	/** 평타와 다른 스킬을 막습니다. 기절당하면 스킬 쪽이 스스로 끊습니다. */
	public boolean casting;
	/** 이동과 점프까지 막습니다 (대지 진동파 등). */
	public boolean rooted;

	// ── 돌진 (플러그인 pvp.dash_* 대응) ──
	public int dashT;
	public double dashPower;
	public double dashX;
	public double dashZ;
	/** 돌진하는 동안 세로 속도를 0 으로 붙잡습니다. */
	public boolean dashHold;

	/** 정신집중 끊기 남은 틱 — 섬광 수류탄. 기절이 아니라 채널링 스킬만 스스로 끊깁니다 (데이터팩 combat/break_channel). */
	public int breakT;

	/** 채널링을 끊을 상태인가 — 기절 · 넘어뜨림 · 에어본 · 밀쳐내기 또는 정신집중 끊기. */
	public boolean interrupted() {
		return hardCc() || breakT > 0;
	}

	public boolean hardCc() {
		return stunT > 0 || knockT > 0 || airT > 0 || pushT > 0;
	}

	public boolean anyActive() {
		return slowT > 0 || stunT > 0 || knockT > 0 || airT > 0 || pushT > 0 || sealT > 0 || dashT > 0;
	}
}
