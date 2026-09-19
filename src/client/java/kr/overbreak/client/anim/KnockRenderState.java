package kr.overbreak.client.anim;

/** 생명체 렌더 상태에 붙이는 넘어뜨림 진행도 (LivingEntityRenderStateMixin 이 구현). 0 = 서 있음, 1 = 완전히 누움. */
public interface KnockRenderState {
	void overbreak$setKnock(float progress);

	float overbreak$knock();

	/** 전술 구르기: 회전각(라디안) · 회전축(월드 수평, 이동 방향 기준). 0 이면 없음. */
	void overbreak$setRoll(float angle, float axisX, float axisZ);

	float overbreak$rollAngle();

	float overbreak$rollAxisX();

	float overbreak$rollAxisZ();
}
