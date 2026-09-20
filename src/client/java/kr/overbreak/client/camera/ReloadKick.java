package kr.overbreak.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.GunslingerAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * 공중 재장전 "탁" 순간의 화면 펀치 (건슬링어).
 *
 *   탄창이 맞물리는 프레임 18 에 화면이 살짝 숙였다가 곧바로 들리고, 시야각도 같은 박자로 좁아졌다 넓어집니다.
 *   {@link MeleePunch} 와 같이 실제 시야각(xRot)은 건드리지 않고 화면 행렬만 돌리므로 조준점은 그대로입니다.
 */
public final class ReloadKick {
	/** 숙임 (도). */
	private static final float PITCH = 1.1F;
	/** 시야각이 좁아지는 정도 (비율). */
	private static final float FOV = 0.045F;
	/** 펀치가 도는 시간 (1/20초 단위). */
	private static final float LENGTH = 5.0F;

	private ReloadKick() {}

	/**
	 * 프레임 18 을 0 으로 본 펀치 세기 (+ = 아래로 숙임 · 시야 좁아짐).
	 * 한 번 아래로 갔다가 위로 올라오고 사그라듭니다.
	 */
	private static float phase(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0.0F;
		}
		float e = GunslingerAnim.reloadTime(mc.player.getId(), partial);
		if (e < GunslingerAnim.CATCH) {
			return 0.0F;
		}
		float k = e - GunslingerAnim.CATCH;
		if (k > LENGTH) {
			return 0.0F;
		}
		return Mth.sin(k * Mth.PI / 2.0F) * (float) Math.exp(-k * 0.45);
	}

	/** 월드 화면 행렬에만 얹습니다 (손 · 총은 화면에 붙어 있음). */
	public static void apply(PoseStack pose, float partial) {
		float f = phase(partial);
		if (f != 0.0F) {
			pose.mulPose(Axis.XP.rotationDegrees(PITCH * f));
		}
	}

	/** 시야각 배율 (1 이면 그대로). */
	public static float fovScale(float partial) {
		float f = phase(partial);
		return f == 0.0F ? 1.0F : 1.0F - FOV * f;
	}
}
