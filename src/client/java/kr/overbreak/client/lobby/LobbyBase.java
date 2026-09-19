package kr.overbreak.client.lobby;

import java.util.ArrayList;
import java.util.List;

import kr.overbreak.net.MenuActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * 메인 화면 창들의 공통 — 기울어진 버튼 · 큰 글자 · 클릭 처리 · 서버로 보내기.
 *
 * 버튼은 그릴 때 자리를 적어 두고 클릭에서 그 자리를 봅니다 (화면 크기가 바뀌어도 따로 다시 배치할 필요 없음).
 * 뒤로는 전장이 보이도록 화면 전체를 덮지 않습니다. 게임은 멈추지 않고, ESC 로 닫히지 않습니다 (뒤로 가기만).
 */
abstract class LobbyBase extends Screen {
	static final int ACCENT = 0xFFE8363C;
	static final int PANEL = 0xD0101218;
	static final int TEXT = 0xFFFFFFFF;
	static final int MUTED = 0xFF9AA0AC;
	static final int DIM = 0xFF5C616C;
	static final float SKEW = -0.22F;
	static final int PAD = 16;

	record Hit(String name, int x, int y, int w, int h, Runnable action) {}

	/** 마지막으로 그린 화면의 누를 수 있는 자리 (시험이 이름으로 찾아 실제로 클릭). */
	static volatile List<Hit> lastHits = List.of();

	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> drawing = new ArrayList<>();

	LobbyBase(Component title) {
		super(title);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** ESC — 뒤로 가기가 있는 창만. */
	void back() {}

	/** Enter — 확인이 있는 창만. */
	void confirm() {}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			back();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// 전장이 비치도록 흐림 · 메뉴 무늬 없이 아주 옅게만
		g.fillGradient(0, 0, width, height, 0x50000000, 0x70000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		drawing.clear();
		super.extractRenderState(g, mouseX, mouseY, a);
		draw(g, mouseX, mouseY, a);
		hits.clear();
		hits.addAll(drawing);
		lastHits = List.copyOf(drawing);
	}

	abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a);

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (event.x() >= h.x && event.x() < h.x + h.w && event.y() >= h.y && event.y() < h.y + h.h) {
					click();
					h.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** 이 자리를 누르면 action (나중에 등록한 것이 위). */
	void clickable(int x, int y, int w, int h, String name, Runnable action) {
		drawing.add(new Hit(name, x, y, w, h, action));
	}

	static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	static void send(String action, String arg, String mode) {
		if (ClientPlayNetworking.canSend(MenuActionPayload.TYPE)) {
			ClientPlayNetworking.send(new MenuActionPayload(action, arg, mode));
		}
	}

	// ── 그리기 ──────────────────────────────────────────────

	/** 기울어진 사각형 (가운데 기준으로 옆으로 밀림). */
	static void skewed(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w / 2.0F, y + h / 2.0F);
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.fill(-w / 2, -h / 2, w - w / 2, h - h / 2, color);
		pose.popMatrix();
	}

	/**
	 * 기울어진 판 (x, y, w, h) 안의 일부분 (판 기준 px, py 에서 pw x ph) — 판과 같은 기울기로 붙어서 가장자리 띠가 어긋나지 않음.
	 */
	static void skewedPart(GuiGraphicsExtractor g, int x, int y, int w, int h, int px, int py, int pw, int ph, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w / 2.0F, y + h / 2.0F);
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		int left = -w / 2;
		int top = -h / 2;
		g.fill(left + px, top + py, left + px + pw, top + py + ph, color);
		pose.popMatrix();
	}

	static void skewedOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w / 2.0F, y + h / 2.0F);
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.outline(-w / 2, -h / 2, w, h, color);
		pose.popMatrix();
	}

	/** 글자를 정수 배율로 (한글은 정수 배율이어야 또렷함). */
	static void big(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int scale, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		g.text(font, text, 0, 0, color);
		pose.popMatrix();
	}

	static Component bold(String s) {
		return Component.literal(s).withStyle(ChatFormatting.BOLD);
	}

	/**
	 * 기울어진 버튼. primary 는 강조색으로 채움. 누를 수 없으면 흐리게 그리고 클릭 자리도 등록하지 않습니다.
	 */
	void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean primary,
				boolean enabled, Runnable action) {
		boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
		int fill = !enabled ? 0x80202228 : primary ? (hover ? 0xFFFF5157 : ACCENT) : (hover ? 0xE0343844 : 0xC0181A20);
		skewed(g, x, y, w, h, fill);
		if (hover && !primary) {
			skewedOutline(g, x, y, w, h, 0xC0FFFFFF);
		}
		Component text = bold(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, enabled ? TEXT : DIM);
		if (enabled) {
			clickable(x, y, w, h, label, action);
		}
	}

	/** 화면 위 제목 띠: 제목(2배) + 설명. 다음 y 를 돌려줌. */
	int header(GuiGraphicsExtractor g, String title, String subtitle) {
		g.fill(0, 0, width, PAD + 34, 0xB0000000);
		g.fill(PAD, PAD + 20, PAD + 22, PAD + 22, ACCENT);
		big(g, font, bold(title), PAD, PAD, 2, TEXT);
		g.text(font, subtitle, PAD + 30, PAD + 17, MUTED);
		return PAD + 34;
	}
}
