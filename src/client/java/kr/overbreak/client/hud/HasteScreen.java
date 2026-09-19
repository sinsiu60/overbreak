package kr.overbreak.client.hud;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 공격속도 증가 표시 (본인 화면만) — 발키리 차원 도약처럼 공격속도가 올라가 있는 동안
 * 화면 가장자리가 노랗게 빛나고, 위쪽에 「공격속도 증가」 가 뜹니다.
 *
 *   켜진 순간 0.4초 동안 밝게 번쩍였다가 은은한 세기로 내려앉아 계속 맥동합니다.
 *   꺼지면 0.3초에 걸쳐 빠집니다.
 */
public final class HasteScreen {
	private static final int BANDS = 14;
	/** 가장자리 색 (금색). */
	private static final int EDGE = 0xFFC02A;
	private static final float IN = 8.0F;
	private static final float OUT = 6.0F;
	/** 켜진 직후 번쩍이는 길이 (틱, 1/20초 기준). */
	private static final float BURST = 8.0F;
	private static final float BASE_ALPHA = 105.0F;
	private static final float BURST_ALPHA = 90.0F;

	private static float fade;

	private HasteScreen() {}

	public static void tick(Minecraft mc) {
		boolean on = mc.player != null && InputMode.active() && HudState.haste();
		float step = on ? 1.0F / IN : -1.0F / OUT;
		fade = Mth.clamp(fade + step, 0.0F, 1.0F);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || fade <= 0.002F) {
			return;
		}
		float t = HudState.ticks + kr.overbreak.client.ClientClock.partial(dt.getGameTimeDeltaPartialTick(false));
		// 켜진 직후 번쩍 + 은은한 맥동
		float burst = 1.0F - Mth.clamp((t - HudState.hasteAt) / BURST, 0.0F, 1.0F);
		float pulse = 0.82F + 0.18F * Mth.sin(t * 0.5F);
		float k = fade * pulse;
		float alpha = BASE_ALPHA * k + BURST_ALPHA * burst * fade;

		int w = g.guiWidth();
		int h = g.guiHeight();
		int depth = Math.min(w, h) / 4;
		for (int i = 0; i < BANDS; i++) {
			float s = 1.0F - i / (float) BANDS;
			int color = argb(Math.round(alpha * s * s), EDGE);
			int a = i * depth / BANDS;
			int b = (i + 1) * depth / BANDS;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}
		String label = "공격속도 증가";
		int tw = mc.font.width(label);
		g.text(mc.font, label, (w - tw) / 2, 34, argb(Math.round(235.0F * fade), 0xFFE066));
	}

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | rgb;
	}
}
