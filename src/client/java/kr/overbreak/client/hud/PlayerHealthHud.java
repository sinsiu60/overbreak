package kr.overbreak.client.hud;

import kr.overbreak.client.input.InputMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치 방식 내 체력 — 화면 왼쪽 아래.
 *
 *   큰 숫자: 현재 체력 (추가체력이 있으면 옆에 파란 +N) / 최대 체력
 *   아래 막대: 체력 25 당 한 칸, 기울어진 칸. 체력 흰색 · 추가체력 파란색 · 방금 잃은 체력은 붉은 잔상
 *   체력 30% 이하: 숫자와 칸이 빨갛게 맥동 / 피해를 받은 순간: 막대 테두리가 빨갛게 번쩍
 *
 * 조작 모드(직업 있음)일 때만 그리고, 바닐라 하트 · 허기 · 방어구 막대는 숨깁니다 (SkillHud).
 */
public final class PlayerHealthHud {
	private static final int MARGIN = 12;
	private static final float HP_PER_SEGMENT = 25.0F;
	private static final int SEG_W = 9;
	private static final int SEG_GAP = 2;
	private static final int SEG_H = 8;
	private static final int MAX_BAR_W = 170;
	private static final float SKEW = -0.35F;
	private static final float LOW = 0.3F;

	/** 체력 막대 왼쪽 위 (튜토리얼 화살표가 가리킬 곳). */
	public static int[] barAnchor(int w, int h) {
		return new int[] {MARGIN + 4 + 40, h - MARGIN - SEG_H - 10};
	}

	private static final int HEALTH = 0xFFFFFFFF;
	private static final int LOW_HEALTH = 0xFFFF5A5A;
	private static final int EXTRA = 0xFF3FA2FF;
	private static final int CHIP = 0xC0FF6B6B;
	private static final int EMPTY = 0x90282A30;

	private static float lag = -1.0F;
	private static float lastHealth = -1.0F;
	private static float ticks;
	private static float hurtAt = -1000;

	private PlayerHealthHud() {}

	public static void tick(Minecraft mc) {
		ticks = (float) kr.overbreak.client.ClientClock.now();
		Player p = mc.player;
		if (p == null) {
			lag = -1.0F;
			lastHealth = -1.0F;
			return;
		}
		float health = p.getHealth();
		if (lastHealth >= 0.0F && health < lastHealth - 0.01F) {
			hurtAt = ticks;
		}
		lastHealth = health;
		if (lag < health) {
			lag = health;
		} else {
			lag = Math.max(health, lag - Math.max(1.5F, (lag - health) * 0.15F));
		}
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (!InputMode.active() || p == null) {
			return;
		}
		Font font = mc.font;
		float t = ticks + kr.overbreak.client.ClientClock.partial(dt.getGameTimeDeltaPartialTick(false));
		float health = Math.max(0.0F, p.getHealth());
		float max = Math.max(1.0F, p.getMaxHealth());
		float extra = Math.max(0.0F, p.getAbsorptionAmount());
		float chip = Math.max(health, Math.min(lag < 0 ? health : lag, max));
		boolean low = health / max <= LOW;
		int healthColor = low ? pulse(LOW_HEALTH, t) : HEALTH;

		// ── 막대 ──
		float total = max + extra;
		int n = Math.max(1, Mth.ceil(total / HP_PER_SEGMENT));
		float unit = HP_PER_SEGMENT;
		float segW = SEG_W;
		if (n * (SEG_W + SEG_GAP) - SEG_GAP > MAX_BAR_W) {
			segW = (MAX_BAR_W - (n - 1) * SEG_GAP) / (float) n;
			if (segW < 3.0F) {
				n = (MAX_BAR_W + SEG_GAP) / (3 + SEG_GAP);
				unit = total / n;
				segW = 3.0F;
			}
		}
		int barW = Math.round(n * (segW + SEG_GAP) - SEG_GAP);
		int x = MARGIN + 4;
		int barY = g.guiHeight() - MARGIN - SEG_H;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, barY);
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		float hurt = 1.0F - Mth.clamp((ticks - hurtAt) / 6.0F, 0.0F, 1.0F);
		g.fill(-2, -2, barW + 2, SEG_H + 2, 0xA0000000);
		if (hurt > 0.0F) {
			g.outline(-2, -2, barW + 4, SEG_H + 4, alpha(0xFF3030, hurt));
		}
		for (int i = 0; i < n; i++) {
			int x0 = Math.round(i * (segW + SEG_GAP));
			int x1 = Math.max(x0 + 1, Math.round(i * (segW + SEG_GAP) + segW));
			float from = i * unit;
			float to = from + unit;
			g.fill(x0, 0, x1, SEG_H, EMPTY);
			span(g, x0, x1, from, to, 0.0F, health, healthColor);
			span(g, x0, x1, from, to, health, chip, CHIP);
			span(g, x0, x1, from, to, max, max + extra, EXTRA);
			// 칸 윗부분 하이라이트
			g.fill(x0, 0, x1, 1, 0x30FFFFFF);
		}
		pose.popMatrix();

		// ── 숫자 ──
		int textY = barY - 22;
		String current = String.valueOf(Mth.ceil(health));
		pose.pushMatrix();
		pose.translate(x, textY);
		pose.scale(2.0F, 2.0F);
		g.text(font, Component.literal(current).withStyle(ChatFormatting.BOLD), 0, 0, healthColor);
		pose.popMatrix();
		int after = x + font.width(Component.literal(current).withStyle(ChatFormatting.BOLD)) * 2 + 4;
		if (extra > 0.0F) {
			String plus = "+" + Mth.ceil(extra);
			g.text(font, Component.literal(plus).withStyle(ChatFormatting.BOLD), after, textY + 1, EXTRA);
			after += font.width(Component.literal(plus).withStyle(ChatFormatting.BOLD)) + 4;
		}
		g.text(font, "/ " + Mth.ceil(max), after, textY + 8, 0xFFA8ADB8);
	}

	/** 칸 [from, to) 중 체력 구간 [a, b) 에 해당하는 부분을 칠합니다. */
	private static void span(GuiGraphicsExtractor g, int x0, int x1, float from, float to, float a, float b, int color) {
		float lo = Math.max(from, a);
		float hi = Math.min(to, b);
		if (hi <= lo) {
			return;
		}
		int w = x1 - x0;
		int l = x0 + Math.round((lo - from) / (to - from) * w);
		int r = x0 + Math.round((hi - from) / (to - from) * w);
		if (r > l) {
			g.fill(l, 0, r, SEG_H, color);
		}
	}

	private static int alpha(int rgb, float a) {
		return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static int pulse(int argb, float t) {
		float k = 0.75F + 0.25F * Mth.sin(t * 0.35F);
		int r = Math.round(((argb >> 16) & 255) * k);
		int gr = Math.round(((argb >> 8) & 255) * k);
		int b = Math.round((argb & 255) * k);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}
}
