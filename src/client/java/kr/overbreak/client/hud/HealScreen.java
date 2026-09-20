package kr.overbreak.client.hud;

import kr.overbreak.client.input.InputMode;
import kr.overbreak.net.HealPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 회복 표시 (본인 화면만) — 스킬로 체력을 되찾는 동안 화면 가장자리가 초록으로 번집니다.
 *
 *   워리어 살육 · 피의 갈망 흡혈은 짧게 한 번 번쩍이고,
 *   투귀 전열 재정비처럼 계속 회복하는 동안에는 그 길이만큼 은은히 맥동합니다.
 *
 * 피격 표시(붉은 가장자리)와 겹쳐도 서로를 가리지 않게 얇게 깝니다.
 */
public final class HealScreen {
	private static final int BANDS = 12;
	/** 가장자리 색 (회복 초록). */
	private static final int EDGE = 0x4FD07A;
	private static final float IN = 3.0F;
	private static final float OUT = 7.0F;
	private static final float BASE_ALPHA = 135.0F;
	private static final float BURST_ALPHA = 115.0F;
	/** 들어온 직후 번쩍이는 길이 (틱). */
	private static final float BURST = 6.0F;

	/** 남은 표시 시간 (틱). */
	private static float left;
	private static float startedAt = -1000;
	private static float fade;

	private HealScreen() {}

	public static void receive(HealPayload msg) {
		if (left <= 0.0F) {
			startedAt = HudState.ticks;
		}
		left = Math.max(left, msg.ticks());
	}

	/** 지금 화면에 얼마나 들어와 있나 (0~1) — 시험용. */
	public static float fade() {
		return fade;
	}

	/** 남은 표시 시간 (틱) — 시험용. */
	public static float left() {
		return left;
	}

	public static void clear() {
		left = 0.0F;
		fade = 0.0F;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			clear();
			return;
		}
		if (left > 0.0F) {
			left--;
		}
		boolean on = left > 0.0F && InputMode.active();
		fade = Mth.clamp(fade + (on ? 1.0F / IN : -1.0F / OUT), 0.0F, 1.0F);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || fade <= 0.002F) {
			return;
		}
		float t = HudState.ticks + kr.overbreak.client.ClientClock.partial(dt.getGameTimeDeltaPartialTick(false));
		float burst = 1.0F - Mth.clamp((t - startedAt) / BURST, 0.0F, 1.0F);
		float pulse = 0.84F + 0.16F * Mth.sin(t * 0.45F);
		float alpha = BASE_ALPHA * fade * pulse + BURST_ALPHA * burst * fade;

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
	}

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | rgb;
	}
}
