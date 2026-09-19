package kr.overbreak.client.hud;

import java.util.List;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치 F1 방식 스킬 설명 화면 — F8 로 열고 닫습니다.
 *
 *   왼쪽: 직업 이름 · 역할 · 소개 · 기본 능력치
 *   오른쪽: 스킬 카드 (기울어진 아이콘 칸 · 이름 · 키 · 대략적인 설명)
 *   카드에 마우스를 올리면 세부 수치 패널
 *
 * 한글은 1배보다 작게 줄이면 글자가 뭉개지므로 글자는 모두 1배 크기로 그립니다.
 * 게임은 멈추지 않습니다.
 */
public final class SkillInfoScreen extends Screen {
	private static final int PAD = 12;
	private static final int GAP = 4;
	private static final int LINE = 10;
	private static final float SKEW = -0.22F;

	private static KeyMapping key;

	/** 시험용: 열려 있는가 · 강제로 가리킬 카드 (-1 = 마우스). */
	public static boolean open;
	public static int debugHover = -1;

	private final ClassInfo info;

	private SkillInfoScreen(ClassInfo info) {
		super(Component.literal(info.name() + " 스킬 설명"));
		this.info = info;
	}

	public static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.overbreak.skill_info", 297, kr.overbreak.client.input.Keys.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				toggle(mc);
			}
		});
	}

	private static void toggle(Minecraft mc) {
		PvpClass c = Classes.byId(HudState.classId);
		ClassInfo info = c == null ? null : c.info();
		if (info == null || mc.player == null) {
			return;
		}
		mc.gui.setScreen(new SkillInfoScreen(info));
	}

	@Override
	public void added() {
		open = true;
		// 튜토리얼에서 "F8 을 열어 봐라" 단계를 넘기는 신호
		kr.overbreak.client.tutorial.TutorialClient.infoScreenOpened();
	}

	@Override
	public void removed() {
		open = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key != null && key.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xD80A0C12, 0xC0101420);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Font font = this.font;
		int accent = 0xFF000000 | info.color();

		// ── 왼쪽: 직업 정보 ──
		int leftW = Math.max(100, Math.min(160, (int) (width * 0.27F)));
		int x = PAD;
		int y = PAD;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(2.0F, 2.0F);
		g.text(font, Component.literal(info.name()).withStyle(ChatFormatting.BOLD), 0, 0, accent);
		pose.popMatrix();
		y += 21;
		g.fill(x, y, x + 24, y + 2, accent);
		y += 6;
		g.text(font, info.role(), x, y, 0xFFB8BCC6);
		y += 14;
		y = wrapped(g, font, info.blurb(), x, y, leftW, 0xFFD6D8DE, 4);
		y += 6;
		for (SkillInfo.Stat s : info.stats()) {
			if (y + LINE > height - PAD - 14) {
				break;
			}
			g.text(font, s.label(), x, y, 0xFF8A8F9A);
			g.text(font, s.value(), x + leftW - font.width(s.value()), y, 0xFFFFFFFF);
			g.fill(x, y + LINE, x + leftW, y + LINE + 1, 0x30FFFFFF);
			y += LINE + 3;
		}
		g.text(font, "F8 · ESC 닫기  |  카드에 마우스를 올리면 세부 수치", x, height - PAD - 8, 0xFF6E7380);

		// ── 오른쪽: 스킬 카드 ──
		List<SkillInfo> skills = info.skills();
		int n = skills.size();
		int cardX = PAD + leftW + 14;
		int cardW = width - PAD - cardX;
		int avail = height - 2 * PAD - 14;
		int cardH = Math.max(26, Math.min(44, (avail - (n - 1) * GAP) / Math.max(1, n)));
		int hovered = -1;
		for (int i = 0; i < n; i++) {
			int cy = PAD + i * (cardH + GAP);
			boolean over = debugHover >= 0 ? debugHover == i
					: mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cy && mouseY < cy + cardH;
			if (over) {
				hovered = i;
			}
			card(g, font, skills.get(i), cardX, cy, cardW, cardH, over, accent);
		}

		// ── 세부 수치 패널 ──
		if (hovered >= 0) {
			details(g, font, skills.get(hovered), cardX, PAD + hovered * (cardH + GAP), cardH, accent);
		}
	}

	private void card(GuiGraphicsExtractor g, Font font, SkillInfo s, int x, int y, int w, int h, boolean over, int accent) {
		int keyColor = s.ult() ? 0xFFFFC94A : accent;
		g.fill(x, y, x + w, y + h, over ? 0xC0283040 : 0x90181C26);
		g.fill(x, y, x + 2, y + h, over ? 0xFFFFFFFF : keyColor);
		if (over) {
			g.outline(x, y, w, h, 0xA0FFFFFF);
		}

		// 기울어진 아이콘 칸
		int box = h - 6;
		int bx = x + 8;
		int by = y + 3;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(bx + box / 2.0F, by + box / 2.0F);
		pose.pushMatrix();
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.fill(-box / 2, -box / 2, box / 2, box / 2, 0xA0303844);
		g.outline(-box / 2, -box / 2, box, box, s.ult() ? 0xC0FFC94A : 0x90FFFFFF);
		pose.popMatrix();
		int icon = Math.min(22, box - 4);
		Identifier sprite = s.icon();
		if (sprite != null) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -icon / 2, -icon / 2, icon, icon, s.ult() ? 0xFFFFC94A : 0xFFFFFFFF);
		} else if (s.item() != null) {
			float k = icon / 16.0F;
			pose.pushMatrix();
			pose.scale(k, k);
			g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item()))), -8, -8);
			pose.popMatrix();
		}
		pose.popMatrix();

		// 이름 · 키
		int tx = bx + box + 10;
		int lines = Math.max(1, (h - 6 - LINE) / LINE);
		int textH = LINE + Math.min(lines, font.split(Component.literal(s.summary()), Math.max(10, x + w - 8 - tx)).size()) * LINE;
		int ty = y + Math.max(3, (h - textH) / 2);
		Component name = Component.literal(s.name()).withStyle(ChatFormatting.BOLD);
		g.text(font, name, tx, ty, 0xFFFFFFFF);
		int kx = tx + font.width(name) + 6;
		int kw = font.width(s.key()) + 6;
		g.fill(kx, ty - 1, kx + kw, ty + 9, 0x70000000);
		g.outline(kx, ty - 1, kw, 10, (keyColor & 0x00FFFFFF) | 0xB0000000);
		g.text(font, s.key(), kx + 3, ty, keyColor);

		// 대략적인 설명
		wrapped(g, font, s.summary(), tx, ty + LINE + 1, x + w - 8 - tx, 0xFFBFC3CC, lines);
	}

	/** 세부 수치 — 내용에 맞춘 폭, 화면 안에서 카드 왼쪽(모자라면 카드 위)에 붙입니다. */
	private void details(GuiGraphicsExtractor g, Font font, SkillInfo s, int cardX, int cardY, int cardH, int accent) {
		g.nextStratum();
		int keyColor = s.ult() ? 0xFFFFC94A : accent;
		int rowH = 12;
		int labelW = 0;
		int valueW = 0;
		for (SkillInfo.Stat st : s.details()) {
			labelW = Math.max(labelW, font.width(st.label()));
			valueW = Math.max(valueW, font.width(st.value()));
		}
		Component name = Component.literal(s.name()).withStyle(ChatFormatting.BOLD);
		int w = Math.min(width - 2 * PAD, Math.max(font.width(name) + font.width(s.key()) + 24, labelW + valueW + 26));
		int h = 22 + s.details().size() * rowH + 3;
		int x = Math.max(PAD, Math.min(cardX - w - 6, width - PAD - w));
		int y = Math.max(PAD, Math.min(cardY + cardH / 2 - h / 2, height - PAD - h));

		g.fill(x, y, x + w, y + h, 0xF4101218);
		g.outline(x, y, w, h, 0xC0FFFFFF);
		g.fill(x, y, x + w, y + 2, keyColor);
		g.text(font, name, x + 7, y + 6, 0xFFFFFFFF);
		g.text(font, s.key(), x + w - 7 - font.width(s.key()), y + 6, keyColor);
		int ry = y + 19;
		g.fill(x + 6, ry, x + w - 6, ry + 1, 0x40FFFFFF);
		ry += 3;
		for (SkillInfo.Stat st : s.details()) {
			g.text(font, st.label(), x + 7, ry + 1, 0xFF9098A6);
			g.text(font, st.value(), x + w - 7 - font.width(st.value()), ry + 1, 0xFFFFFFFF);
			ry += rowH;
		}
	}

	/** 줄바꿈해서 그리고 다음 y 를 돌려줍니다. maxLines 를 넘으면 마지막 줄 끝을 … 로 줄입니다. */
	private static int wrapped(GuiGraphicsExtractor g, Font font, String text, int x, int y, int width, int color, int maxLines) {
		int inner = Math.max(10, width);
		List<FormattedCharSequence> lines = font.split(Component.literal(text), inner);
		int count = Math.min(maxLines, lines.size());
		for (int i = 0; i < count; i++) {
			FormattedCharSequence line = lines.get(i);
			if (i == count - 1 && lines.size() > count) {
				List<FormattedCharSequence> cut = font.split(Component.literal(text), Math.max(10, inner - font.width("…")));
				line = FormattedCharSequence.composite(cut.get(Math.min(i, cut.size() - 1)), Component.literal("…").getVisualOrderText());
			}
			g.text(font, line, x, y, color);
			y += LINE;
		}
		return y;
	}
}
