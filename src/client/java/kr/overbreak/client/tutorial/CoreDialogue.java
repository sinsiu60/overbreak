package kr.overbreak.client.tutorial;

import java.util.ArrayList;
import java.util.List;

import kr.overbreak.Overbreak;
import kr.overbreak.client.ClientClock;
import kr.overbreak.net.DialoguePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * 화면 위쪽 대사창 — 「코어」 초상화와 함께 글자가 한 자씩 촤라락 찍힙니다.
 *
 *   서버가 한 줄씩 보냄 (DialoguePayload) · 다 찍으면 ▼ 가 깜빡이고, 다음 줄이 오면 바로 갈아 끼움
 *   찍는 동안 초상화의 눈이 밝아지고 음성 그릴이 출렁임 · 글자마다 짧은 신호음
 *   마침표 · 쉼표에서는 잠깐 쉬어 갑니다
 */
public final class CoreDialogue {
	private static final Identifier PORTRAIT = Overbreak.id("tutorial/core");
	/** 1초에 찍는 글자 수. */
	private static final double CHARS_PER_SECOND = 32.0;
	private static final int CYAN = 0xFF7FE0FF;
	private static final int RED = 0xFFE8363C;
	private static final int PANEL = 0xE8080C14;

	private static String speaker = "";
	private static String text = "";
	private static int mood;
	private static double start;
	/** 창이 처음 열린 시각 (줄이 바뀔 때는 다시 미끄러져 들어오지 않게). */
	private static double shownSince;
	private static double closeAt = -1.0;
	private static double hold;
	/** 글자마다 "이 시간이 지나면 보인다" (시간 단위). */
	private static double[] charAt = new double[0];
	private static int blipped;
	/** 창이 열리고 닫히는 정도 0~1. */
	private static float open;

	private CoreDialogue() {}

	public static void receive(DialoguePayload p) {
		if (p.speaker().isEmpty() || p.text().isEmpty()) {
			close();
			return;
		}
		if (text.isEmpty() || closeAt >= 0) {
			shownSince = ClientClock.now();
		}
		speaker = p.speaker();
		text = p.text();
		mood = p.mood();
		hold = Math.max(0, p.hold());
		start = ClientClock.now();
		closeAt = -1.0;
		blipped = 0;
		charAt = new double[text.length()];
		double t = 0.0;
		double step = 20.0 / CHARS_PER_SECOND;
		for (int i = 0; i < text.length(); i++) {
			charAt[i] = t;
			char c = text.charAt(i);
			t += step;
			if (c == '.' || c == '!' || c == '?') {
				t += 5.0;
			} else if (c == ',' || c == '-') {
				t += 2.5;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.9F, 0.25F));
	}

	public static void close() {
		if (!text.isEmpty() && closeAt < 0) {
			closeAt = ClientClock.now();
		}
	}

	/** 끊길 때 흔적을 남기지 않게 바로 지움. */
	public static void clear() {
		text = "";
		speaker = "";
		closeAt = -1.0;
		open = 0.0F;
	}

	public static boolean visible() {
		return !text.isEmpty();
	}

	/** 시험용: 지금 몇 글자까지 찍혔나. */
	public static int typed() {
		return typedAt(ClientClock.now() - start);
	}

	private static int typedAt(double elapsed) {
		int n = 0;
		while (n < charAt.length && charAt[n] <= elapsed) {
			n++;
		}
		return n;
	}

	public static void tick(Minecraft mc) {
		if (text.isEmpty()) {
			return;
		}
		// 대사창이 떠 있는 동안에는 알림 토스트가 가리지 않게
		mc.gui.toastManager().clear();
		double elapsed = ClientClock.now() - start;
		int n = typedAt(elapsed);
		// 글자 세 개마다 신호음 (말소리처럼)
		if (n > blipped && closeAt < 0) {
			if (n / 3 > blipped / 3) {
				float pitch = mood == DialoguePayload.MOOD_SHARP ? 1.35F : 1.75F;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),
						pitch + (n % 5) * 0.03F, 0.22F));
			}
			blipped = n;
		}
		// 다 찍고 hold 가 지나면 스스로 닫힘
		if (closeAt < 0 && hold > 0 && n >= charAt.length && elapsed > lastCharTime() + hold) {
			close();
		}
		if (closeAt >= 0 && ClientClock.now() - closeAt > 6.0) {
			clear();
		}
	}

	private static double lastCharTime() {
		return charAt.length == 0 ? 0.0 : charAt[charAt.length - 1];
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (text.isEmpty() || mc.player == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		double now = ClientClock.at(partial);
		double elapsed = now - start;
		// 열고 닫히는 정도 (0.25초)
		float in = (float) Mth.clamp((now - shownSince) / 5.0, 0.0, 1.0);
		float out = closeAt < 0 ? 0.0F : (float) Mth.clamp((now - closeAt) / 5.0, 0.0, 1.0);
		open = in * (1.0F - out);
		if (open <= 0.001F) {
			return;
		}

		Font font = mc.font;
		int gw = g.guiWidth();
		int scale = gw >= 600 ? 2 : 1;
		int boxW = Math.min(gw - 24, scale == 2 ? 620 : 400);
		int portrait = scale == 2 ? 72 : 48;
		int boxH = portrait + 16;
		int x = (gw - boxW) / 2;
		// 위에서 미끄러져 내려옴
		float ease = 1.0F - (1.0F - open) * (1.0F - open);
		int y = Math.round(6 - (1.0F - ease) * (boxH + 10));

		// 판
		g.fill(x, y, x + boxW, y + boxH, PANEL);
		g.fill(x, y, x + boxW, y + 1, mood == DialoguePayload.MOOD_SHARP ? RED : CYAN);
		g.fill(x, y + boxH - 1, x + boxW, y + boxH, 0x60FFFFFF & (mood == DialoguePayload.MOOD_SHARP ? RED : CYAN));
		corner(g, x, y, boxW, boxH, mood == DialoguePayload.MOOD_SHARP ? RED : CYAN);

		// 초상화
		int px = x + 8;
		int py = y + 8;
		int typedN = typedAt(elapsed);
		boolean talking = typedN < charAt.length && closeAt < 0;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, PORTRAIT, px, py, portrait, portrait);
		// 눈빛 · 주사선 (말할 때 더 밝게)
		float pulse = 0.55F + 0.45F * Mth.sin((float) now * (talking ? 1.2F : 0.35F));
		int eyeY = py + Math.round(portrait * 0.40F);
		int eyeH = Math.max(1, portrait / 12);
		g.fill(px + portrait / 6, eyeY, px + portrait - portrait / 6, eyeY + eyeH,
				alpha(mood == DialoguePayload.MOOD_SHARP ? 0xE8363C : 0x9FF0FF, (talking ? 0.5F : 0.22F) * pulse));
		// 음성 그릴 출렁임
		if (talking) {
			int gy = py + Math.round(portrait * 0.66F);
			int gw0 = portrait / 2;
			for (int i = 0; i < 4; i++) {
				float k = 0.5F + 0.5F * Mth.sin((float) now * 2.4F + i * 1.7F);
				int barW = Math.round(gw0 * (0.35F + 0.65F * k));
				int bx = px + portrait / 2 - barW / 2;
				int by = gy + i * Math.max(2, portrait / 24);
				g.fill(bx, by, bx + barW, by + 1, alpha(0x9FF0FF, 0.45F));
			}
		}
		// 주사선 무늬
		for (int sy = py; sy < py + portrait; sy += 3) {
			g.fill(px, sy, px + portrait, sy + 1, 0x18000000);
		}
		g.outline(px, py, portrait, portrait, alpha(mood == DialoguePayload.MOOD_SHARP ? 0xE8363C : 0x7FE0FF, 0.75F));

        // 이름
		int tx = px + portrait + 10;
		int ty = y + 8;
		Component name = Component.literal("「" + speaker + "」");
		g.text(font, name, tx, ty, mood == DialoguePayload.MOOD_SHARP ? RED : CYAN);
		g.fill(tx, ty + 10, x + boxW - 8, ty + 11, 0x30FFFFFF);

		// 글자 (찍힌 데까지)
		int textW = (x + boxW - 8) - tx;
		List<String> lines = wrap(font, text, textW / scale);
		int shown = typedN;
		int lineY = ty + 15;
		Matrix3x2fStack pose = g.pose();
		for (String line : lines) {
			if (shown <= 0) {
				break;
			}
			String part = shown >= line.length() ? line : line.substring(0, shown);
			shown -= line.length();
			pose.pushMatrix();
			pose.translate(tx, lineY);
			pose.scale(scale, scale);
			g.text(font, part, 0, 0, 0xFFEDEFF3);
			pose.popMatrix();
			lineY += 10 * scale;
		}

		// 다 찍었으면 ▼
		if (typedN >= charAt.length && closeAt < 0 && Math.floorMod((int) (now / 5), 2) == 0) {
			g.text(font, "▼", x + boxW - 14, y + boxH - 12, CYAN);
		}
	}

	/** 네 귀퉁이의 짧은 선 (터미널 느낌). */
	private static void corner(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		int len = 6;
		g.fill(x, y, x + len, y + 1, color);
		g.fill(x, y, x + 1, y + len, color);
		g.fill(x + w - len, y, x + w, y + 1, color);
		g.fill(x + w - 1, y, x + w, y + len, color);
		g.fill(x, y + h - 1, x + len, y + h, color);
		g.fill(x, y + h - len, x + 1, y + h, color);
		g.fill(x + w - len, y + h - 1, x + w, y + h, color);
		g.fill(x + w - 1, y + h - len, x + w, y + h, color);
	}

	/** 폭에 맞춰 줄바꿈 (한글은 띄어쓰기가 드물어 글자 단위로도 자릅니다). */
	private static List<String> wrap(Font font, String s, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		int lastSpace = -1;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			line.append(c);
			if (c == ' ') {
				lastSpace = line.length() - 1;
			}
			if (font.width(line.toString()) > maxW) {
				if (lastSpace > 0 && line.length() - lastSpace < 12) {
					String head = line.substring(0, lastSpace + 1);
					String rest = line.substring(lastSpace + 1);
					out.add(head);
					line = new StringBuilder(rest);
				} else {
					out.add(line.substring(0, line.length() - 1));
					line = new StringBuilder(String.valueOf(c));
				}
				lastSpace = -1;
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	private static int alpha(int rgb, float a) {
		return (Mth.clamp((int) (a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
