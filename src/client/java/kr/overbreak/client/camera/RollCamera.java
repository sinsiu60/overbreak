package kr.overbreak.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 전술 구르기 1인칭 화면 (오버워치 캐서디 구르기) — 구르는 동안 시야가 낮아지고, 구르는 쪽으로 기울며 살짝 앞으로 숙였다가
 * 부드럽게 돌아옵니다. 월드 화면에만 걸고 손 · 총은 화면에 붙어 있습니다 (GameRendererBobMixin).
 */
public final class RollCamera {
	/** 구르기 + 일어서는 시간 (틱). */
	private static final float TOTAL = 9.0F;
	private static final float DIP = 0.55F;
	private static final float TILT = 16.0F;
	private static final float PITCH = 9.0F;

	private RollCamera() {}

	public static void apply(PoseStack pose, float partial) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		SkillAnims.Play play = SkillAnims.find(p.getId(), SkillAnimPayload.SH_ROLL);
		if (play == null) {
			return;
		}
		float e = play.elapsed(partial);
		if (e >= TOTAL) {
			return;
		}
		// 빠르게 내려갔다가 천천히 올라옴
		float x = Mth.clamp(e / TOTAL, 0.0F, 1.0F);
		float amount = x < 0.3F ? Mth.sin(x / 0.3F * Mth.HALF_PI) : Mth.cos((x - 0.3F) / 0.7F * Mth.HALF_PI);
		amount *= amount;
		// 구르는 방향: 화면 오른쪽이면 +1, 왼쪽이면 -1, 앞뒤면 0
		Vec3 v = p.getDeltaMovement();
		double yaw = Math.toRadians(p.getYRot());
		double right = -v.x * Math.cos(yaw) - v.z * Math.sin(yaw);
		double forward = -v.x * Math.sin(yaw) + v.z * Math.cos(yaw);
		double len = Math.sqrt(right * right + forward * forward);
		float side = len < 1.0E-3 ? 0.0F : (float) (right / len);
		float ahead = len < 1.0E-3 ? 1.0F : (float) (forward / len);
		pose.translate(0.0F, -DIP * amount, 0.0F);
		pose.mulPose(Axis.ZP.rotationDegrees(-side * TILT * amount));
		pose.mulPose(Axis.XP.rotationDegrees(ahead * PITCH * amount));
	}
}
