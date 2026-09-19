package kr.overbreak.client.lobby;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 패치노트 — 버전마다 바뀐 것을 직업별로. 버프는 파란 (버프), 너프는 빨간 (너프) 꼬리표가 붙습니다.
 * 휠 · 오른쪽 막대로 넘겨 봅니다.
 */
public final class PatchNotesScreen extends LobbyBase {
	private static final int ROW = 11;
	private static final int TAG_W = 34;

	private int scroll;
	private int contentH;

	public PatchNotesScreen() {
		super(Component.literal("패치노트"));
	}

	@Override
	void back() {
		minecraft.gui.setScreen(new LobbyScreen());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		scroll = clampScroll(scroll - (int) Math.round(dy * ROW * 3));
		return true;
	}

	private int clampScroll(int v) {
		return Mth.clamp(v, 0, Math.max(0, contentH - viewH()));
	}

	private int viewH() {
		return height - (PAD + 34) - PAD - 26;
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		PatchNotes.Version newest = PatchNotes.VERSIONS.getFirst();
		int top = header(g, "패치노트", "최신 " + newest.version() + "  ·  " + newest.headline());
		int viewTop = top + 6;
		int viewBottom = height - PAD - 26;
		int x = PAD + 10;
		int w = width - 2 * PAD - 24;

		g.fill(PAD, viewTop, width - PAD, viewBottom, 0xA0080A0E);
		g.enableScissor(PAD, viewTop, width - PAD, viewBottom);
		Font font = this.font;
		int y = viewTop + 8 - scroll;
		for (PatchNotes.Version v : PatchNotes.VERSIONS) {
			// 버전 띠
			if (y + 20 > viewTop && y < viewBottom) {
				g.fill(x - 6, y - 2, width - PAD - 10, y + 18, 0x60141822);
				g.fill(x - 6, y - 2, x - 3, y + 18, ACCENT);
				big(g, font, bold(v.version()), x + 2, y, 2, TEXT);
				g.text(font, v.headline(), x + 6 + font.width(bold(v.version())) * 2, y + 8, MUTED);
			}
			y += 24;
			for (PatchNotes.Section s : v.sections()) {
				// 직업 이름 띠
				if (y + 14 > viewTop && y < viewBottom) {
					g.fill(x - 4, y - 2, x - 2, y + 10, s.color());
					g.text(font, bold(s.title()), x + 2, y, s.color());
				}
				y += 15;
				for (PatchNotes.Line line : s.lines()) {
					if (y + ROW > viewTop && y < viewBottom) {
						row(g, font, line, x + 6, y, w - 6);
					}
					y += ROW * lineCount(font, line, w - 6 - TAG_W - 8);
				}
				y += 7;
			}
			y += 10;
		}
		contentH = y + scroll - viewTop + 8;
		g.disableScissor();

		// 넘겨 보는 막대
		int max = Math.max(0, contentH - viewH());
		if (max > 0) {
			int barX = width - PAD - 5;
			int trackH = viewBottom - viewTop - 8;
			int barH = Math.max(18, trackH * viewH() / Math.max(1, contentH));
			int barY = viewTop + 4 + (trackH - barH) * scroll / max;
			g.fill(barX, viewTop + 4, barX + 3, viewBottom - 4, 0x30FFFFFF);
			g.fill(barX, barY, barX + 3, barY + barH, 0xC07FE0FF);
		}

		button(g, mouseX, mouseY, PAD, height - PAD - 20, 80, 20, "뒤로", false, true, this::back);
		String hint = "휠로 넘겨 보기";
		g.text(font, hint, width - PAD - 10 - font.width(hint), height - PAD - 14, DIM);
	}

	/** 한 줄 — 글 + 꼬리표. 길면 접습니다. */
	private void row(GuiGraphicsExtractor g, Font font, PatchNotes.Line line, int x, int y, int w) {
		int textW = w - TAG_W - 8;
		List<String> parts = wrap(font, line.text(), textW);
		for (int i = 0; i < parts.size(); i++) {
			g.text(font, parts.get(i), x, y + i * ROW, i == 0 ? 0xFFD8DBE2 : 0xFFA9AEB8);
		}
		// 꼬리표는 첫 줄 오른쪽에
		PatchNotes.Tag tag = line.tag();
		String label = "(" + tag.label + ")";
		int tx = x + w - font.width(label);
		g.text(font, label, tx, y, tag.color);
		g.fill(x - 6, y + 2, x - 4, y + 7, tag.color);
	}

	private int lineCount(Font font, PatchNotes.Line line, int w) {
		return Math.max(1, wrap(font, line.text(), w).size());
	}

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
				if (lastSpace > 0) {
					String head = line.substring(0, lastSpace);
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
}
