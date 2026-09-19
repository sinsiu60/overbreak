package kr.overbreak.client.lobby;

import java.util.Locale;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.net.MenuActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 매칭 대기 — 위쪽 띠에 "찾는 중" · 지난 시간 · 규격, 가운데 아래에 취소. 나머지는 전장이 보이게 비워 둡니다.
 */
public final class QueueScreen extends LobbyBase {
	public QueueScreen() {
		super(Component.literal("매칭 대기"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, height - 70, width, height, 0x00000000, 0x90000000);
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int bandH = 58;
		int bandY = PAD + 6;
		int bandW = Math.min(width - 2 * PAD, 300);
		int bx = (width - bandW) / 2;
		skewed(g, bx, bandY, bandW, bandH, PANEL);
		skewedPart(g, bx, bandY, bandW, bandH, 0, 0, bandW, 2, ACCENT);

		// detail 은 "종류|아래에 붙는 글" (팀전은 몇 명 모였는지)
		String detail = LobbyClient.detail();
		int bar = detail.indexOf('|');
		String mode = (bar < 0 ? detail : detail.substring(0, bar)).isEmpty() ? "경기" : (bar < 0 ? detail : detail.substring(0, bar));
		String extra = bar < 0 ? "" : detail.substring(bar + 1);
		long ms = System.currentTimeMillis() - LobbyClient.queueSince();
		// 움직이는 점 세 개
		int dots = (int) (ms / 400 % 4);
		Component title = bold(mode + " 찾는 중" + ".".repeat(dots));
		Component widest = bold(mode + " 찾는 중...");
		big(g, font, title, (width - font.width(widest) * 2) / 2, bandY + 9, 2, TEXT);

		long s = ms / 1000;
		String time = String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
		PvpClass c = Classes.byId(LobbyClient.lastClass);
		String sub = c != null && c.info() != null ? "규격  " + c.info().name() + "   ·   " + time : time;
		if (!extra.isEmpty()) {
			sub = extra + "   ·   " + sub;
		}
		g.text(font, sub, (width - font.width(sub)) / 2, bandY + 32, MUTED);

		// 흐르는 막대
		int barW = bandW - 40;
		int barY = bandH - 10;
		skewedPart(g, bx, bandY, bandW, bandH, 20, barY, barW, 2, 0x30FFFFFF);
		int seg = barW / 4;
		int off = (int) (ms / 8 % (barW + seg)) - seg;
		int from = Math.max(0, off);
		int to = Math.min(barW, off + seg);
		if (to > from) {
			skewedPart(g, bx, bandY, bandW, bandH, 20 + from, barY, to - from, 2, ACCENT);
		}

		button(g, mouseX, mouseY, (width - 110) / 2, height - PAD - 26, 110, 20, "취소", false, true,
				() -> send(MenuActionPayload.CANCEL, "", ""));
	}
}
