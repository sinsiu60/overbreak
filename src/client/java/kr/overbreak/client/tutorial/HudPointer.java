package kr.overbreak.client.tutorial;

import kr.overbreak.client.ClientClock;
import kr.overbreak.client.hud.PlayerHealthHud;
import kr.overbreak.client.hud.SkillHud;
import kr.overbreak.net.TutorialCuePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 튜토리얼 화살표 — 지금 써야 할 HUD 칸(스킬 · 궁극기 · 무기 · 체력) 위에서 통통 튀며 가리킵니다.
 * 가리키는 칸 둘레에는 숨 쉬듯 밝아지는 테두리를, 화살표 위에는 짧은 글(키 이름 등)을 붙입니다.
 */
public final class HudPointer {
	private static final int CYAN = 0x7FE0FF;

	private static int target = -1;
	private static String label = "";
	private static double since;

	private HudPointer() {}

	public static void point(int which, String text) {
		if (target != which) {
			since = ClientClock.now();
		}
		target = which;
		label = text;
	}

	public static void clear() {
		target = -1;
		label = "";
	}

	/** 시험용. */
	public static int target() {
		return target;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (target < 0 || mc.player == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		double now = ClientClock.at(partial);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int[] at = anchor(target, w, h);
		if (at == null) {
			return;
		}
		// 나타날 때 살짝 커짐
		float in = (float) Mth.clamp((now - since) / 4.0, 0.0, 1.0);
		float bob = Mth.sin((float) now * 0.9F) * 3.0F;
		int cx = at[0];
		int cy = Math.round(at[1] - 10 + bob);

		// 가리키는 칸 둘레 (스킬 칸일 때만 네모)
		if (target <= TutorialCuePayload.P_SKILL3) {
			int pulse = Math.round(90 + 90 * (0.5F + 0.5F * Mth.sin((float) now * 0.9F)));
			g.outline(cx - 16, at[1] - 1, 32, 28, argb(pulse, CYAN));
		}

		// 아래를 가리키는 삼각형 (한 줄씩 쌓아 그림)
		int size = Math.round(9 * (0.6F + 0.4F * in));
		for (int i = 0; i < size; i++) {
			int half = size - i;
			int y = cy - size + i;
			g.fill(cx - half, y, cx + half, y + 1, argb(255, 0xFFFFFF));
		}
		// 삼각형 뒤 빛
		for (int i = 0; i < size + 3; i++) {
			int half = size + 3 - i;
			int y = cy - size - 2 + i;
			g.fill(cx - half, y, cx + half, y + 1, argb(70, CYAN));
		}

		// 글
		if (!label.isEmpty()) {
			Font font = mc.font;
			Component text = Component.literal(label);
			int tw = font.width(text);
			int tx = Mth.clamp(cx - tw / 2, 4, w - tw - 4);
			int ty = cy - size - 14;
			g.fill(tx - 4, ty - 3, tx + tw + 4, ty + 10, 0xC0080C14);
			g.fill(tx - 4, ty - 3, tx + tw + 4, ty - 2, argb(220, CYAN));
			g.text(font, text, tx, ty, 0xFFFFFFFF);
		}
	}

	private static int[] anchor(int which, int w, int h) {
		return switch (which) {
			case TutorialCuePayload.P_SKILL1, TutorialCuePayload.P_SKILL2, TutorialCuePayload.P_SKILL3 ->
					SkillHud.slotAnchor(which, w, h);
			case TutorialCuePayload.P_ULT -> SkillHud.ultAnchor(w, h);
			case TutorialCuePayload.P_WEAPON -> SkillHud.weaponAnchor(w, h);
			case TutorialCuePayload.P_HEALTH -> PlayerHealthHud.barAnchor(w, h);
			case TutorialCuePayload.P_CENTER -> new int[] {w / 2, h / 2 + 30};
			default -> null;
		};
	}

	private static int argb(int a, int rgb) {
		return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
