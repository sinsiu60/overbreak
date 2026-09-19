package kr.overbreak.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * 1인칭 무기 걸음 흔들림 — 화면은 가만히 두고 손 · 무기만 걷는 박자에 맞춰 흔듭니다.
 *
 *   모양: 바닐라 화면 흔들림과 같은 8자 — 두 걸음에 한 번 좌우로 흔들리고(sin), 한 걸음마다 한 번 살짝 내려감(|cos|).
 *         내려가는 쪽으로 기울고(roll) 발 닿는 순간 총구가 조금 숙여짐. 모두 부드러운 곡선이라 끊기는 느낌이 없음
 *   박자: 바닐라 걸음 거리 × {@link #CADENCE} — 늘 달리기 속도라 바닐라 박자는 너무 빨라 늦춤 (3인칭 다리 흔들기도 같은 비율)
 *   세기: 바닐라 bob (걷는 빠르기, 공중이면 0) 에 비례 — 멈추거나 떠 있으면 흔들리지 않음
 */
public final class WeaponBob {
	/** 걸음 박자 배율 — 1인칭 흔들림 · 3인칭 다리 흔들기 공용 (AvatarRendererMixin). */
	public static final float CADENCE = 0.7F;
	/** 좌우 흔들림 (블록). */
	private static final float SWAY = 0.016F;
	/** 가장 낮을 때 내려가는 양 (블록). */
	private static final float DROP = 0.022F;
	/** 좌우로 기우는 각도 (도). */
	private static final float ROLL = 1.2F;
	/** 발 닿을 때 총구가 숙여지는 각도 (도). */
	private static final float PITCH = 0.9F;
	/** 바닐라 bob 최댓값 (걷거나 뛸 때). */
	private static final float BOB_MAX = 0.1F;

	private WeaponBob() {}

	public static void apply(PoseStack pose, float walk, float bob) {
		float amount = Mth.clamp(bob / BOB_MAX, 0.0F, 1.0F);
		if (amount <= 0.001F) {
			return;
		}
		float w = walk * CADENCE * Mth.PI;
		float side = Mth.sin(w);
		float down = Math.abs(Mth.cos(w));
		pose.translate(side * SWAY * amount, -down * DROP * amount, 0.0F);
		pose.mulPose(Axis.ZP.rotationDegrees(side * ROLL * amount));
		pose.mulPose(Axis.XP.rotationDegrees(-Math.abs(Mth.cos(w - 0.2F)) * PITCH * amount));
	}
}
