package kr.overbreak.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.ScatterAnim;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;

/**
 * 돌진 난사 화면 연출 — 쓴 사람 본인 화면만 (스펙 PART 6-4).
 *
 *   시야각: 기 모으기에서 살짝 좁혔다가 돌진에서 확 넓어지고, 제동 · 난사 동안 조금 넓은 채로, 끝나면 원래대로
 *   흔들림: 제동 순간 한 번 + 난사 한 발마다 작게. 화면 행렬만 흔들어 조준점은 그대로입니다
 *   카메라를 억지로 돌리거나 기울이지(roll) 않습니다 — 멀미 · 조준 빼앗기 방지
 *   설정의 "화면 흔들림" · "시야각 효과" 세기를 그대로 따릅니다 (0 이면 꺼짐)
 */
public final class ScatterCam {
	private ScatterCam() {}

	private static SkillAnims.Play play() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : SkillAnims.find(mc.player.getId(), SkillAnimPayload.GS_SCATTER);
	}

	/** 월드 화면 행렬에만 얹습니다 (손 · 총은 화면에 붙어 있음). */
	public static void apply(PoseStack pose, float partial) {
		SkillAnims.Play p = play();
		if (p == null) {
			return;
		}
		double scale = Minecraft.getInstance().options.screenEffectScale().get();
		if (scale <= 0.0) {
			return;
		}
		float[] s = ScatterAnim.shake(p.elapsed(partial), p.end());
		if (s[0] != 0.0F || s[1] != 0.0F) {
			pose.mulPose(Axis.XP.rotationDegrees((float) (s[0] * scale)));
			pose.mulPose(Axis.YP.rotationDegrees((float) (s[1] * scale)));
		}
	}

	/** 시야각 배율 (1 이면 그대로). */
	public static float fovScale(float partial) {
		SkillAnims.Play p = play();
		if (p == null) {
			return 1.0F;
		}
		double effect = Minecraft.getInstance().options.fovEffectScale().get();
		float m = ScatterAnim.fov(p.elapsed(partial), p.end(), SkillAnims.fade(p.anim));
		return (float) (1.0 + (m - 1.0) * effect);
	}
}
