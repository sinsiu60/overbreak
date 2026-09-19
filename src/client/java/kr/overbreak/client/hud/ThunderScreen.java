package kr.overbreak.client.hud;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 뇌신 화면 효과 (본인 화면만).
 *
 *   섬전: 시작 순간 하얀 하늘색 섬광 · 달리는 동안 가장자리 전기빛 · 시야가 넓어짐 (속도감)
 *   뇌신강림: 떠 있는 동안 가장자리가 짙은 남청색으로 조여 들고 전기처럼 깜빡임 · 벼락이 떨어질 때마다 섬광
 */
public final class ThunderScreen {
	private static final int BANDS = 18;
	private static final int EDGE = 0x021C33;
	private static final int TINT = 0x2A9CFF;
	private static final int FLASH = 0xCFF4FF;
	private static final float DASH_FOV = 1.18F;
	private static final float ULT_FOV = 1.06F;

	private ThunderScreen() {}

	private static SkillAnims.Play play(int anim) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : SkillAnims.find(mc.player.getId(), anim);
	}

	/** 시작 in 틱에 들어오고 끝난 뒤 out 틱에 빠지는 세기 0~1. */
	private static float weight(int anim, float partial, float in, float out) {
		SkillAnims.Play p = play(anim);
		if (p == null) {
			return 0.0F;
		}
		float e = p.elapsed(partial);
		float w = Math.min(Mth.clamp(e / in, 0.0F, 1.0F), 1.0F - Mth.clamp((e - p.end()) / out, 0.0F, 1.0F));
		return w * w * (3.0F - 2.0F * w);
	}

	/** 막 시작한 순간 1 → ticks 동안 빠지는 섬광. */
	private static float burst(int anim, float partial, float ticks) {
		SkillAnims.Play p = play(anim);
		if (p == null) {
			return 0.0F;
		}
		float k = 1.0F - Mth.clamp(p.elapsed(partial) / ticks, 0.0F, 1.0F);
		return k * k;
	}

	public static float fovScale(float partial) {
		float dash = weight(SkillAnimPayload.TH_DASH, partial, 1.0F, 4.0F);
		float ult = weight(SkillAnimPayload.TH_ULT, partial, 8.0F, 8.0F);
		return Mth.lerp(dash, 1.0F, DASH_FOV) * Mth.lerp(ult, 1.0F, ULT_FOV);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		float dash = weight(SkillAnimPayload.TH_DASH, partial, 1.0F, 4.0F);
		float ult = weight(SkillAnimPayload.TH_ULT, partial, 8.0F, 8.0F);
		// 강림 중 벼락 (TH_CAST 가 강림 중에 오면 벼락 한 번) · 섬전 시작 섬광
		float flash = Math.max(burst(SkillAnimPayload.TH_DASH, partial, 5.0F), ult > 0.0F ? burst(SkillAnimPayload.TH_CAST, partial, 6.0F) : 0.0F);
		float edge = Math.max(dash * 0.8F, ult);
		if (edge <= 0.001F && flash <= 0.001F) {
			return;
		}
		// 전기처럼 불규칙하게 깜빡임
		float t = (float) kr.overbreak.client.ClientClock.at(partial);
		float crackle = 0.82F + 0.18F * Mth.sin(t * 2.7F) * Mth.sin(t * 1.3F + 0.7F);
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, argb(Math.round(34.0F * edge * crackle), TINT));
		int depth = Math.min(w, h) / 3;
		for (int i = 0; i < BANDS; i++) {
			float s = 1.0F - i / (float) BANDS;
			int color = argb(Math.round(190.0F * s * s * edge * crackle), EDGE);
			int a = i * depth / BANDS;
			int b = (i + 1) * depth / BANDS;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}
		if (flash > 0.001F) {
			g.fill(0, 0, w, h, argb(Math.round(120.0F * flash), FLASH));
		}
	}

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | rgb;
	}
}
