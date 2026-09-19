package kr.overbreak.client.camera;

import net.minecraft.client.CameraType;

/**
 * 어깨 너머 3인칭 (TPS) 카메라.
 *
 * 바닐라 "뒤에서 보기" 3인칭을 이 시점으로 바꿉니다. 정면 3인칭 · 1인칭은 바닐라 그대로입니다.
 */
public final class TpsCamera {
	/** 캐릭터 뒤 거리 (바닐라 4칸 → 3칸). 엔티티 크기 · camera_distance 속성 비율은 그대로 따릅니다. */
	public static final float DISTANCE_RATIO = 3.0F / 4.0F;
	/** 오른쪽으로 옮기는 거리 (칸, 기본 거리 4 기준). */
	public static final float SIDE = 0.75F;
	/** 벽에 붙었을 때 카메라가 파고들지 않게 남기는 여유. */
	public static final float WALL_MARGIN = 0.2F;

	private TpsCamera() {}

	public static boolean active(CameraType type) {
		return type == CameraType.THIRD_PERSON_BACK;
	}
}
