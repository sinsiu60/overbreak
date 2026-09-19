package kr.overbreak.client.lobby;

import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.game.TeamMatch;
import kr.overbreak.net.MenuActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * 팀 격전 — 2대2 · 3대3 중에 고릅니다. 고르면 그 인원이 모일 때까지 대기열에 들어가고,
 * 인원이 차면 편 짜기 화면에서 직접 청팀 · 홍팀을 정합니다.
 */
public final class TeamSizeScreen extends LobbyBase {
	private static final int BLUE = 0xFF3FA2FF;

	private final PvpClass pvpClass;

	public TeamSizeScreen(PvpClass pvpClass) {
		super(Component.literal("팀 격전"));
		this.pvpClass = pvpClass;
	}

	@Override
	void back() {
		minecraft.gui.setScreen(new ModeSelectScreen(pvpClass));
	}

	@Override
	void confirm() {
		queue(3);
	}

	private void queue(int size) {
		send(MenuActionPayload.QUEUE, pvpClass.id(),
				size == 2 ? MenuActionPayload.MODE_TEAM2 : MenuActionPayload.MODE_TEAM3);
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int top = header(g, "팀 격전", "몇 대 몇으로 붙을지 고르세요");

		ClassInfo info = pvpClass.info();
		String chip = "규격  " + info.name();
		int chipW = font.width(chip) + 16;
		int chipX = width - PAD - chipW;
		skewed(g, chipX, PAD + 4, chipW, 16, 0xC0181A20);
		skewedPart(g, chipX, PAD + 4, chipW, 16, 0, 14, chipW, 2, 0xFF000000 | info.color());
		g.text(font, chip, chipX + 8, PAD + 8, TEXT);

		int n = TeamMatch.SIZES.length;
		int gap = 16;
		int cardW = Math.max(120, Math.min(220, (width - 2 * PAD - (n - 1) * gap) / n));
		int cardH = Math.max(104, Math.min(150, height - top - 70));
		int total = n * cardW + (n - 1) * gap;
		int x0 = (width - total) / 2;
		int cy = top + Math.max(12, (height - top - 40 - cardH) / 2);
		for (int i = 0; i < n; i++) {
			card(g, mouseX, mouseY, TeamMatch.SIZES[i], x0 + i * (cardW + gap), cy, cardW, cardH);
		}

		button(g, mouseX, mouseY, PAD, height - PAD - 20, 80, 20, "뒤로", false, true, this::back);
	}

	private void card(GuiGraphicsExtractor g, int mouseX, int mouseY, int size, int x, int y, int w, int h) {
		String name = size + "대" + size + " 매치";
		boolean hover = over(mouseX, mouseY, x, y, w, h);
		skewed(g, x, y, w, h, hover ? 0xF0182634 : 0xD0141820);
		skewedPart(g, x, y, w, h, 0, 0, w, 3, BLUE);
		if (hover) {
			skewedOutline(g, x, y, w, h, 0xE0FFFFFF);
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w - 34, y + 7);
		pose.scale(1.4F, 1.4F);
		g.item(new ItemStack(size == 2 ? Items.IRON_SWORD : Items.SHIELD), 0, 0);
		pose.popMatrix();

		int tx = x + 12;
		big(g, font, bold(name), tx, y + 30, 2, TEXT);
		int ly = y + 54;
		for (String line : new String[] {
				"한 팀 " + size + "명  ·  모두 " + size * 2 + "명",
				"전멸시키면 1점",
				"먼저 " + TeamMatch.WIN + "점이면 승리",
				"편은 직접 고릅니다"}) {
			String cut = font.width(line) > w - 20 ? font.plainSubstrByWidth(line, w - 20 - font.width("…")) + "…" : line;
			g.text(font, cut, tx, ly, ly == y + 54 ? 0xFFC8CCD4 : MUTED);
			ly += 12;
		}
		Component go = bold("대기열");
		g.text(font, go, x + w - 12 - font.width(go), y + h - 15, hover ? TEXT : BLUE);
		clickable(x, y, w, h, name, () -> queue(size));
	}
}
