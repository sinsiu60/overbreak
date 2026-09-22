package kr.overbreak.client.anim;

/** 플레이어 렌더 상태에 붙이는 스킬 애니메이션 값 (AvatarRenderStateMixin 이 구현). */
public interface AnimRenderState {
	void overbreak$set(int anim, float time, float end, float weight);

	void overbreak$setItemScale(float scale);

	/** 상체 회전 (라디안) — 살육처럼 상체만 도는 동작. 다리는 걷기 방향 그대로. */
	void overbreak$setSpin(float spin);

	int overbreak$anim();

	float overbreak$time();

	float overbreak$end();

	float overbreak$weight();

	float overbreak$itemScale();

	float overbreak$spin();

	/** 돌진 난사 3인칭 자세 · 가중치 (재생 중이 아니면 null · 0). */
	void overbreak$setScatter(kr.overbreak.client.anim.scatter.ScatterBody.@org.jspecify.annotations.Nullable Pose pose, float weight);

	kr.overbreak.client.anim.scatter.ScatterBody.@org.jspecify.annotations.Nullable Pose overbreak$scatter();

	float overbreak$scatterWeight();

	/** 참철 칼날 오오라 색 (ARGB, 0 = 없음). */
	void overbreak$setAura(int argb);

	int overbreak$aura();

	/** 참철 칼날 오오라 모델 (비어 있으면 안 그림). */
	net.minecraft.client.renderer.item.ItemStackRenderState overbreak$auraItem();

	/** 재장전 중 받치는 손에 든 탄창 (비어 있으면 안 그림). */
	net.minecraft.client.renderer.item.ItemStackRenderState overbreak$magazine();
}
