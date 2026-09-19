package kr.overbreak.client.lobby;

import java.util.List;

import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.game.Brawl;
import kr.overbreak.game.Duel;
import kr.overbreak.game.TeamMatch;
import kr.overbreak.net.MenuActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * 게임 종류 — 고른 규격을 들고 어떤 경기를 할지. 1대1 매치 · 팀 격전 (2대2 · 3대3) · 대난투.
 */
public final class ModeSelectScreen extends LobbyBase {
	/** 카드 한 장 — 제목 · 설명 줄 · 누르면 할 일. */
	private record Mode(String name, int color, Item icon, String action, List<String> lines, Runnable go) {}

	private final PvpClass pvpClass;
	private final List<Mode> modes;

	public ModeSelectScreen(PvpClass pvpClass) {
		super(Component.literal("게임 종류"));
		this.pvpClass = pvpClass;
		this.modes = List.of(
				new Mode("1대1 매치", ACCENT, Items.IRON_SWORD, "대기열", List.of(
						"한 명씩 맞붙기",
						Duel.WIN + "선승제",
						"상대가 모이면 시작"), this::queueDuel),
				new Mode("팀 격전", 0xFF3FA2FF, Items.SHIELD, "2대2 · 3대3", List.of(
						"두 편으로 나뉘어 겨루기",
						"전멸시키면 1점",
						"먼저 " + TeamMatch.WIN + "점이면 승리",
						"편은 직접 고릅니다"), this::teamSizes),
				new Mode("대난투", 0xFFFFC94A, Items.NETHERITE_AXE, "참가", List.of(
						"최대 " + Brawl.MAX_PLAYERS + "명 난투",
						"1명당 " + Brawl.KILLS_PER_PLAYER + "킬이 목표",
						Brawl.MIN_PLAYERS + "명 모이면 10초 뒤",
						"경기 중에도 난입"), this::queueBrawl));
	}

	@Override
	void back() {
		minecraft.gui.setScreen(new ClassSelectScreen(ClassSelectScreen.Purpose.MATCH));
	}

	@Override
	void confirm() {
		queueDuel();
	}

	private void queueDuel() {
		send(MenuActionPayload.QUEUE, pvpClass.id(), MenuActionPayload.MODE_DUEL);
	}

	private void teamSizes() {
		minecraft.gui.setScreen(new TeamSizeScreen(pvpClass));
	}

	private void queueBrawl() {
		send(MenuActionPayload.QUEUE, pvpClass.id(), MenuActionPayload.MODE_BRAWL);
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int top = header(g, "게임 종류", "어떤 경기를 할지 고르세요");

		// 고른 규격 (누르면 다시 고르기)
		ClassInfo info = pvpClass.info();
		String chip = "규격  " + info.name();
		int chipW = font.width(chip) + 16;
		int chipX = width - PAD - chipW;
		boolean chipHover = over(mouseX, mouseY, chipX, PAD + 4, chipW, 16);
		skewed(g, chipX, PAD + 4, chipW, 16, chipHover ? 0xE0343844 : 0xC0181A20);
		skewedPart(g, chipX, PAD + 4, chipW, 16, 0, 14, chipW, 2, 0xFF000000 | info.color());
		g.text(font, chip, chipX + 8, PAD + 8, TEXT);
		clickable(chipX, PAD + 4, chipW, 16, "규격", this::back);

		// 경기 카드 세 장
		int n = modes.size();
		int gap = 12;
		int cardW = Math.max(110, Math.min(210, (width - 2 * PAD - (n - 1) * gap) / n));
		int cardH = Math.max(104, Math.min(150, height - top - 70));
		int total = n * cardW + (n - 1) * gap;
		int x0 = (width - total) / 2;
		int cy = top + Math.max(12, (height - top - 40 - cardH) / 2);
		for (int i = 0; i < n; i++) {
			card(g, mouseX, mouseY, modes.get(i), x0 + i * (cardW + gap), cy, cardW, cardH);
		}

		button(g, mouseX, mouseY, PAD, height - PAD - 20, 80, 20, "뒤로", false, true, this::back);
	}

	private void card(GuiGraphicsExtractor g, int mouseX, int mouseY, Mode m, int x, int y, int w, int h) {
		boolean hover = over(mouseX, mouseY, x, y, w, h);
		skewed(g, x, y, w, h, hover ? shade(m.color(), 0x60) : 0xD0141820);
		skewedPart(g, x, y, w, h, 0, 0, w, 3, m.color());
		if (hover) {
			skewedOutline(g, x, y, w, h, 0xE0FFFFFF);
		}
		// 아이콘은 제목 위, 카드 오른쪽 위 구석에
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w - 34, y + 7);
		pose.scale(1.4F, 1.4F);
		g.item(new ItemStack(m.icon()), 0, 0);
		pose.popMatrix();

		int tx = x + 12;
		big(g, font, bold(m.name()), tx, y + 30, 2, TEXT);
		int ly = y + 54;
		int room = w - 20;
		for (int i = 0; i < m.lines().size(); i++) {
			String line = m.lines().get(i);
			if (font.width(line) > room) {
				line = font.plainSubstrByWidth(line, room - font.width("…")) + "…";
			}
			g.text(font, line, tx, ly, i == 0 ? 0xFFC8CCD4 : i == m.lines().size() - 1 ? DIM : MUTED);
			ly += 12;
		}
		Component go = bold(m.action());
		g.text(font, go, x + w - 12 - font.width(go), y + h - 15, hover ? TEXT : m.color());
		clickable(x, y, w, h, m.name(), m.go());
	}

	/** 카드 색을 어둡게 깔아 둔 배경 (직업 색이 은은하게 비치게). */
	private static int shade(int argb, int alpha) {
		int r = ((argb >> 16) & 255) / 5;
		int gr = ((argb >> 8) & 255) / 5;
		int b = (argb & 255) / 5;
		return (Math.min(255, alpha + 0x90) << 24) | (r << 16) | (gr << 8) | b;
	}
}
