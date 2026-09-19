package kr.overbreak.client.lobby;

import java.util.List;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.net.MenuActionPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * 규격 선택 (오버워치 방식) — 아래 한 줄에 초상화 카드, 위쪽에 고른 규격의 큰 초상화 · 소개 · 능력치 · 스킬.
 *
 *   고를 때마다 「픽 연출」 이 돕니다 — 큰 초상화가 옆에서 밀려 들어오며 흰 섬광이 번지고,
 *   직업 색 빛줄기가 훑고 지나가고, 고른 카드가 위로 튀어오르고, 스킬이 차례로 들어옵니다.
 *
 * 1대1 매치 · 대난투로 가는 길이면 다음은 게임 종류, 훈련장이면 바로 훈련장.
 */
public final class ClassSelectScreen extends LobbyBase {
	/** MATCH 경기에 들고 갈 규격 · TRAINING 훈련장 · RESPAWN 쓰러진 뒤 다시 살아날 때 (대난투) */
	public enum Purpose { MATCH, TRAINING, RESPAWN }

	/** 카드 · 큰 초상화 그림 (assets/overbreak/textures/gui/sprites/portrait/&lt;직업 id&gt;.png). */
	static Identifier portrait(String classId) {
		return Overbreak.id("portrait/" + classId);
	}

	private static final int CARD_W = 74;
	private static final int CARD_H = 46;
	private static final float PICK_TIME = 9.0F;

	private final Purpose purpose;
	private final List<PvpClass> classes;
	private int selected;
	private float pickAt = -1000.0F;

	/** 시험용: 강제로 고를 직업 번호 (-1 = 그대로). */
	public static int debugSelect = -1;

	public ClassSelectScreen(Purpose purpose) {
		super(Component.literal("규격 선택"));
		this.purpose = purpose;
		this.classes = Classes.all().stream().filter(c -> c.info() != null).toList();
		for (int i = 0; i < classes.size(); i++) {
			if (classes.get(i).id().equals(LobbyClient.lastClass)) {
				selected = i;
			}
		}
	}

	@Override
	void back() {
		// 다시 살아날 때 고르는 창은 닫기만 — 메인 화면으로 가지 않습니다
		minecraft.gui.setScreen(purpose == Purpose.RESPAWN ? null : new LobbyScreen());
	}

	@Override
	void confirm() {
		if (classes.isEmpty()) {
			return;
		}
		PvpClass c = classes.get(selected);
		LobbyClient.lastClass = c.id();
		switch (purpose) {
			case TRAINING -> send(MenuActionPayload.TRAINING, c.id(), "");
			case RESPAWN -> {
				send(MenuActionPayload.PICK, c.id(), "");
				minecraft.gui.setScreen(null);
			}
			default -> minecraft.gui.setScreen(new ModeSelectScreen(c));
		}
	}

	private void pick(int index) {
		if (index != selected) {
			pickAt = now();
		}
		selected = index;
	}

	private static float now() {
		return (float) kr.overbreak.client.ClientClock.now();
	}

	/** 픽 연출 진행도 0 → 1 (1 이면 연출이 끝난 상태). */
	private float pickProgress() {
		return Mth.clamp((now() - pickAt) / PICK_TIME, 0.0F, 1.0F);
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (debugSelect >= 0 && debugSelect < classes.size()) {
			if (debugSelect != selected) {
				pickAt = now();
			}
			selected = debugSelect;
		}
		int top = header(g, purpose == Purpose.RESPAWN ? "규격 변경" : "규격 선택", switch (purpose) {
			case TRAINING -> "훈련장에서 쓸 규격을 고르세요";
			case RESPAWN -> "다시 일어날 때 쓸 규격을 고르세요  ·  ESC 로 그대로 두기";
			default -> "경기에 들고 갈 규격을 고르세요";
		});
		int n = classes.size();
		int rowY = height - PAD - 24 - CARD_H;
		if (n > 0) {
			hero(g, classes.get(selected).info(), classes.get(selected).id(), top + 8, rowY - 10);
		}
		roster(g, mouseX, mouseY, rowY);

		int by = height - PAD - 20;
		button(g, mouseX, mouseY, PAD, by, 80, 20, purpose == Purpose.RESPAWN ? "닫기" : "뒤로", false, true, this::back);
		String next = switch (purpose) {
			case TRAINING -> "훈련장 입장";
			case RESPAWN -> "이 규격으로";
			default -> "선택";
		};
		button(g, mouseX, mouseY, width - PAD - 110, by, 110, 20, next, true, n > 0, this::confirm);
	}

	// ── 아래 초상화 줄 ──────────────────────────────────────

	private void roster(GuiGraphicsExtractor g, int mouseX, int mouseY, int y) {
		int n = classes.size();
		if (n == 0) {
			return;
		}
		int gap = 6;
		int cardW = Math.min(CARD_W, (width - 2 * PAD - (n - 1) * gap) / n);
		int total = n * cardW + (n - 1) * gap;
		int x0 = (width - total) / 2;
		float pick = pickProgress();
		for (int i = 0; i < n; i++) {
			PvpClass pc = classes.get(i);
			ClassInfo info = pc.info();
			int color = 0xFF000000 | info.color();
			int cx = x0 + i * (cardW + gap);
			boolean hover = over(mouseX, mouseY, cx, y - 6, cardW, CARD_H + 6);
			boolean sel = i == selected;
			// 고른 카드는 위로 튀어오르고, 픽 직후에는 조금 더 솟습니다
			int lift = sel ? 6 + Math.round(6 * (1.0F - pick)) : hover ? 3 : 0;
			int cy = y - lift;
			skewed(g, cx, cy, cardW, CARD_H, sel ? 0xF01A1F28 : 0xB00C0E13);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, portrait(pc.id()), cx + 2, cy + 2, cardW - 4, CARD_H - 12,
					sel || hover ? 0xFFFFFFFF : 0xFF8A8F9A);
			skewedPart(g, cx, cy, cardW, CARD_H, 0, CARD_H - 12, cardW, 12, sel ? 0xE0000000 : 0xC0000000);
			skewedPart(g, cx, cy, cardW, CARD_H, 0, CARD_H - 13, cardW, 2, sel ? color : (color & 0x00FFFFFF) | 0x70000000);
			if (sel) {
				skewedOutline(g, cx, cy, cardW, CARD_H, 0xE0FFFFFF);
			}
			Component name = bold(info.name());
			int nameX = cx + (cardW - font.width(name)) / 2;
			g.text(font, name, nameX, cy + CARD_H - 10, sel || hover ? TEXT : 0xFFC8CCD4);
			final int index = i;
			clickable(cx, y - 8, cardW, CARD_H + 8, pc.id(), () -> pick(index));
		}
	}

	// ── 고른 규격 ───────────────────────────────────────────

	private void hero(GuiGraphicsExtractor g, ClassInfo info, String classId, int top, int bottom) {
		int color = 0xFF000000 | info.color();
		int h = Math.max(70, bottom - top);
		g.fill(PAD, top, width - PAD, top + h, 0xA0080A0E);
		// 왼쪽에 직업 색이 옅게 깔림
		for (int i = 0; i < 120 && PAD + i < width - PAD; i++) {
			int alpha = (int) (0x40 * (1.0F - i / 120.0F));
			g.fill(PAD + i, top, PAD + i + 1, top + h, (alpha << 24) | (color & 0xFFFFFF));
		}

		float pick = pickProgress();
		float ease = 1.0F - (1.0F - pick) * (1.0F - pick) * (1.0F - pick);
		int portraitW = Math.min(112, Math.max(56, width / 7));
		int portraitH = Math.min(h - 8, portraitW * 176 / 144);
		int px = PAD + 8 + Math.round(22 * (1.0F - ease));
		int py = top + (h - portraitH) / 2;
		int fade = Math.round(255 * Math.min(1.0F, ease * 1.6F));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, portrait(classId), px, py, portraitW, portraitH, (fade << 24) | 0xFFFFFF);
		// 픽 섬광 + 빛줄기
		if (pick < 1.0F) {
			int flash = Math.round(150 * (1.0F - pick) * (1.0F - pick));
			g.fill(px, py, px + portraitW, py + portraitH, (flash << 24) | 0xFFFFFF);
			int sweep = py + Math.round((portraitH + 24) * pick) - 12;
			g.fill(px, Math.max(py, sweep), px + portraitW, Math.min(py + portraitH, sweep + 10),
					(Math.round(120 * (1.0F - pick)) << 24) | (color & 0xFFFFFF));
		}
		g.fill(px, py + portraitH, px + portraitW, py + portraitH + 2, color);

		int x = px + portraitW + 14;
		int textW = Math.max(80, (width - PAD - 10 - x) * 45 / 100);
		int y = py + 2;
		Component title = bold(info.name());
		big(g, font, title, x, y, 2, color);
		g.text(font, info.role(), x + font.width(title) * 2 + 8, y + 8, MUTED);
		y += 20;
		int statBottom = top + h - 6;
		// 능력치가 한 칸에 다 들어가는 것이 먼저 — 남는 자리에만 소개를 싣습니다
		int need = info.stats().size() * 11;
		int room = statBottom - y;
		if (room >= need + 24) {
			y = wrapped(g, font, info.blurb(), x, y, textW, 0xFFD6D8DE, room >= need + 34 ? 2 : 1);
			y += 4;
		}
		stats(g, info.stats(), x, y, textW, statBottom);

		skills(g, info, color, x + textW + 16, top + 6, top + h - 6, pick);
	}

	/** 능력치 — 세로로 다 안 들어가면 두 칸으로 나눠 싣고, 그래도 넘치면 「+N개 더」. */
	private void stats(GuiGraphicsExtractor g, List<SkillInfo.Stat> stats, int x, int y, int textW, int bottom) {
		int rows = Math.max(1, (bottom - y) / 11);
		int cols = stats.size() > rows ? 2 : 1;
		int colW = (textW - (cols - 1) * 10) / cols;
		int slots = rows * cols;
		int shown = stats.size() > slots ? Math.max(0, slots - 1) : stats.size();
		for (int i = 0; i < Math.min(stats.size(), slots); i++) {
			int cx = x + (i / rows) * (colW + 10);
			int cy = y + (i % rows) * 11;
			if (i >= shown) {
				g.text(font, "+" + (stats.size() - shown) + "개 더", cx, cy, DIM);
				break;
			}
			SkillInfo.Stat st = stats.get(i);
			g.text(font, st.label(), cx, cy, 0xFF8A8F9A);
			String value = st.value();
			int room = colW - font.width(st.label()) - 8;
			if (font.width(value) > room) {
				value = font.plainSubstrByWidth(value, Math.max(6, room - font.width("…"))) + "…";
			}
			g.text(font, value, cx + colW - font.width(value), cy, TEXT);
		}
	}

	private void skills(GuiGraphicsExtractor g, ClassInfo info, int color, int sx, int top, int bottom, float pick) {
		int sw = width - PAD - 10 - sx;
		if (sw < 60) {
			return;
		}
		g.fill(sx - 8, top + 4, sx - 7, bottom - 4, 0x30FFFFFF);
		int all = info.skills().size();
		int rowH = Math.max(11, Math.min(26, (bottom - top) / Math.max(1, all)));
		// 판을 넘어가면 들어가는 만큼만
		int rows = Math.min(all, Math.max(1, (bottom - top) / rowH));
		int ry = top + 2;
		for (int i = 0; i < rows; i++) {
			SkillInfo s = info.skills().get(i);
			// 픽 연출: 스킬이 위에서부터 차례로 들어옵니다
			float step = Mth.clamp(pick * rows - i, 0.0F, 1.0F);
			if (step <= 0.0F) {
				ry += rowH;
				continue;
			}
			int slide = Math.round(14 * (1.0F - step));
			int alpha = Math.round(255 * step);
			int keyColor = s.ult() ? 0xFFFFC94A : color;
			int icon = Math.min(16, rowH - 4);
			int iy = ry + (rowH - icon) / 2;
			if (s.icon() != null) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, s.icon(), sx + slide, iy, icon, icon,
						(alpha << 24) | (s.ult() ? 0xFFC94A : 0xFFFFFF));
			} else if (s.item() != null) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(sx + slide, iy);
				pose.scale(icon / 16.0F, icon / 16.0F);
				g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item()))), 0, 0);
				pose.popMatrix();
			}
			int tx = sx + icon + 6 + slide;
			int ty = rowH >= 22 ? ry + (rowH - 19) / 2 : ry + (rowH - 8) / 2;
			Component name = bold(s.name());
			g.text(font, name, tx, ty, (alpha << 24) | 0xFFFFFF);
			int kx = tx + font.width(name) + 5;
			g.text(font, s.key(), kx, ty, (alpha << 24) | (keyColor & 0xFFFFFF));
			if (rowH >= 22) {
				String summary = s.summary();
				int maxW = sw - icon - 6;
				if (font.width(summary) > maxW) {
					summary = font.plainSubstrByWidth(summary, maxW - font.width("…")) + "…";
				}
				g.text(font, summary, tx, ty + 10, (alpha << 24) | 0xA9AEB8);
			}
			ry += rowH;
		}
	}

	private static int wrapped(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(10, w));
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			g.text(font, lines.get(i), x, y, color);
			y += 10;
		}
		return y;
	}
}
