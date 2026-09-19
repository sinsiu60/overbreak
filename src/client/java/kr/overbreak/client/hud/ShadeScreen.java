package kr.overbreak.client.hud;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 셰이드 화면 효과 (본인 화면만).
 *
 *   그림자 가르기 · 잔영 회피 · 그림자 걸음 동안: 가장자리가 어두운 파랑으로 조여 듦 (보안관 황야의 무법자와 같은 띠 방식)
 *   잔영 회피 동안: 시야가 좁아져 화면이 확대됨 (AbstractClientPlayerFovMixin)
 *   그림자 걸음 도착 · 잔영 반격 · 천검난무 한 번 (SD_STRIKE): 순간 파란 섬광 + 시야가 넓어졌다 돌아오는 튕김
 */
public final class ShadeScreen {
	private static final int BANDS = 18;
	/** 비네트 띠 색 (어두운 파랑). */
	private static final int EDGE = 0x04103A;
	/** 화면 전체에 옅게 도는 색. */
	private static final int TINT = 0x1A3C9C;
	/** 도착 섬광 색. */
	private static final int FLASH = 0x6A9CFF;
	private static final float FADE_IN = 2.0F;
	private static final float FADE_OUT = 5.0F;
	private static final float FLASH_TICKS = 7.0F;
	/** 잔영 회피 확대 (시야 배율). */
	private static final float EVADE_ZOOM = 0.82F;
	/** 도착 순간 시야가 넓어지는 정도. */
	private static final float STRIKE_KICK = 0.10F;

	private ShadeScreen() {}

	/** 동작 하나의 세기 0~1 — 시작 2틱에 들어오고 끝난 뒤 5틱에 빠짐. */
	private static float weight(int anim, float partial) {
		Minecraft mc = Minecraft.getInstance();
		SkillAnims.Play p = mc.player == null ? null : SkillAnims.find(mc.player.getId(), anim);
		if (p == null) {
			return 0.0F;
		}
		float e = p.elapsed(partial);
		float end = p.end();
		float in = Mth.clamp(e / FADE_IN, 0.0F, 1.0F);
		float out = 1.0F - Mth.clamp((e - end) / FADE_OUT, 0.0F, 1.0F);
		float w = Math.min(in, out);
		return w * w * (3.0F - 2.0F * w);
	}

	/** 도착 섬광 0~1 (막 도착한 순간 1, 7틱에 걸쳐 빠짐). */
	private static float flash(float partial) {
		Minecraft mc = Minecraft.getInstance();
		SkillAnims.Play p = mc.player == null ? null : SkillAnims.find(mc.player.getId(), SkillAnimPayload.SD_STRIKE);
		if (p == null) {
			return 0.0F;
		}
		float k = 1.0F - Mth.clamp(p.elapsed(partial) / FLASH_TICKS, 0.0F, 1.0F);
		return k * k;
	}

	private static float vignette(float partial) {
		return Math.max(Math.max(weight(SkillAnimPayload.SD_REND, partial), weight(SkillAnimPayload.SD_EVADE, partial)),
				Math.max(weight(SkillAnimPayload.SD_STEP, partial), flash(partial)));
	}

	/** 시야 배율 (1 = 그대로) — 잔영 회피 확대 · 도착 튕김. */
	public static float fovScale(float partial) {
		float zoom = Mth.lerp(weight(SkillAnimPayload.SD_EVADE, partial), 1.0F, EVADE_ZOOM);
		return zoom * (1.0F + STRIKE_KICK * flash(partial));
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		float k = vignette(partial);
		float f = flash(partial);
		if (k <= 0.001F && f <= 0.001F) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, argb(Math.round(40.0F * k), TINT));
		int depth = Math.min(w, h) / 3;
		for (int i = 0; i < BANDS; i++) {
			float s = 1.0F - i / (float) BANDS;
			int color = argb(Math.round(185.0F * s * s * k), EDGE);
			int a = i * depth / BANDS;
			int b = (i + 1) * depth / BANDS;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}
		if (f > 0.001F) {
			g.fill(0, 0, w, h, argb(Math.round(95.0F * f), FLASH));
		}
	}

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | rgb;
	}
}
