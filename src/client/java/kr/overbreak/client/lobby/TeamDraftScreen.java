package kr.overbreak.client.lobby;

import java.util.List;

import kr.overbreak.net.MenuActionPayload;
import kr.overbreak.net.TeamDraftPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 편 짜기 — 팀 격전에 들어가면 먼저 뜹니다. 청팀 · 홍팀 칸을 눌러 직접 편을 고릅니다.
 *
 *   양쪽이 다 차면 바로 시작하고, 시간이 다 되면 안 고른 사람은 빈자리에 자동으로 들어갑니다.
 *   내가 고른 편은 테두리가 밝게 표시됩니다. 나가려면 ESC → 「나가기」.
 */
public final class TeamDraftScreen extends LobbyBase {
	private static final int BLUE = 0xFF3FA2FF;
	private static final int RED = 0xFFFF4B4B;

	public TeamDraftScreen() {
		super(Component.literal("편 짜기"));
	}

	private static TeamDraftPayload draft() {
		return LobbyClient.draft();
	}

	@Override
	void back() {
		// 편 짜기에서 나가면 기권 — 메인 화면으로
		send(MenuActionPayload.LOBBY, "", "");
	}

	@Override
	void confirm() {
		pick(draft().mine() < 0 ? 0 : draft().mine());
	}

	private void pick(int team) {
		send(MenuActionPayload.TEAM_PICK, String.valueOf(team), "");
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		TeamDraftPayload d = draft();
		int size = Math.max(1, d.size());
		int left = d.left();
		int top = header(g, "편 짜기", size + "대" + size + " 팀 격전  ·  들어갈 편을 고르세요");

		String timer = left > 0 ? left + "초 뒤 자동 배정" : "곧 시작합니다";
		g.text(font, timer, width - PAD - font.width(timer), PAD + 17, left <= 5 ? ACCENT : MUTED);

		int gap = 16;
		int panelW = Math.max(120, Math.min(200, (width - 2 * PAD - gap) / 2));
		int panelH = Math.max(100, Math.min(170, height - top - 76));
		int x0 = (width - (panelW * 2 + gap)) / 2;
		int py = top + Math.max(10, (height - top - 60 - panelH) / 2);
		panel(g, mouseX, mouseY, 0, "청팀", BLUE, d.blue(), size, d.mine() == 0, x0, py, panelW, panelH);
		panel(g, mouseX, mouseY, 1, "홍팀", RED, d.red(), size, d.mine() == 1, x0 + panelW + gap, py, panelW, panelH);

		// 아직 안 고른 사람
		if (!d.waiting().isEmpty()) {
			String idle = "고르는 중: " + String.join(" · ", d.waiting());
			if (font.width(idle) > width - 2 * PAD) {
				idle = font.plainSubstrByWidth(idle, width - 2 * PAD - font.width("…")) + "…";
			}
			g.text(font, idle, (width - font.width(idle)) / 2, py + panelH + 8, DIM);
		}

		button(g, mouseX, mouseY, PAD, height - PAD - 20, 80, 20, "나가기", false, true, this::back);
	}

	private void panel(GuiGraphicsExtractor g, int mouseX, int mouseY, int team, String title, int color,
					   List<String> members, int size, boolean mine, int x, int y, int w, int h) {
		boolean full = members.size() >= size;
		boolean hover = !mine && !full && over(mouseX, mouseY, x, y, w, h);
		skewed(g, x, y, w, h, mine ? 0xF0121A24 : hover ? 0xE01A2029 : 0xC00E1016);
		skewedPart(g, x, y, w, h, 0, 0, w, 3, color);
		if (mine) {
			skewedOutline(g, x, y, w, h, 0xF0FFFFFF);
		} else if (hover) {
			skewedOutline(g, x, y, w, h, 0x90FFFFFF);
		}

		int tx = x + 12;
		big(g, font, bold(title), tx, y + 12, 2, color);
		String count = members.size() + " / " + size;
		g.text(font, count, x + w - 12 - font.width(count), y + 18, full ? color : MUTED);

		int ly = y + 38;
		for (int i = 0; i < size; i++) {
			boolean taken = i < members.size();
			g.fill(tx, ly, tx + 2, ly + 9, taken ? color : 0x40FFFFFF);
			String label = taken ? members.get(i) : "빈자리";
			if (font.width(label) > w - 28) {
				label = font.plainSubstrByWidth(label, w - 28 - font.width("…")) + "…";
			}
			g.text(font, label, tx + 8, ly + 1, taken ? TEXT : DIM);
			ly += 13;
		}

		String hint = mine ? "내 편" : full ? "자리 없음" : "이 편으로";
		g.text(font, bold(hint), x + w - 12 - font.width(bold(hint)), y + h - 15, mine ? TEXT : full ? DIM : color);
		if (!mine && !full) {
			clickable(x, y, w, h, title, () -> pick(team));
		}
	}
}
