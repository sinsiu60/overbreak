package kr.overbreak.client.lobby;

import kr.overbreak.net.MenuActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * 메인 화면 — 왼쪽에 제목과 메뉴, 오른쪽은 전장 풍경.
 *
 *   플레이   처음이면 튜토리얼, 마쳤으면 규격 선택 → 게임 종류 (지금은 1대1 매치)
 *   훈련장   규격 선택 → 훈련장 방
 *   패치노트 버전별 변경점
 *   설정     바닐라 설정 창
 *   관리자 모드 (게임마스터만) 전장 · 훈련장을 나가 맵을 자유롭게 돌아다님
 *   나가기   서버 접속 끊기
 */
public final class LobbyScreen extends LobbyBase {
	/** 시험용: 강제로 가리킬 메뉴 (-1 = 마우스). */
	public static int debugHover = -1;

	public LobbyScreen() {
		super(Component.literal("OVERBREAK"));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// 왼쪽만 어둡게 — 오른쪽으로 갈수록 옅어져 전장이 보임
		int panel = Math.max(200, (int) (width * 0.42F));
		// 1칸 폭 줄로 — 띠 경계가 보이지 않게
		for (int i = 0; i < panel; i++) {
			int x0 = i;
			int x1 = i + 1;
			float t = i / (float) panel;
			int alpha = (int) (0xE0 * (1.0F - t * t));
			g.fill(x0, 0, x1, height, alpha << 24 | 0x08090D);
		}
		g.fillGradient(0, height - 60, width, height, 0x00000000, 0x90000000);
	}

	@Override
	void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = PAD + 8;
		int titleY = Math.max(PAD, (int) (height * 0.14F));
		big(g, font, bold("OVERBREAK"), x, titleY, 3, TEXT);
		g.fill(x, titleY + 30, x + 40, titleY + 32, ACCENT);
		g.text(font, "PVP 아레나", x + 46, titleY + 27, MUTED);

		java.util.List<String> labelList = new java.util.ArrayList<>(java.util.List.of("플레이", "훈련장", "패치노트", "설정"));
		java.util.List<Runnable> actionList = new java.util.ArrayList<>(java.util.List.of(this::play, this::training, this::patchNotes, this::options));
		if (LobbyClient.admin()) {
			labelList.add("관리자 모드");
			actionList.add(this::free);
		}
		labelList.add("나가기");
		actionList.add(this::quit);
		String[] labels = labelList.toArray(new String[0]);
		Runnable[] actions = actionList.toArray(new Runnable[0]);
		int itemH = Math.max(22, Math.min(30, (height - titleY - 70) / labels.length - 4));
		int y = titleY + 52;
		int w = 170;
		for (int i = 0; i < labels.length; i++) {
			boolean hover = debugHover >= 0 ? debugHover == i : over(mouseX, mouseY, x - 8, y, w, itemH);
			if (hover) {
				skewed(g, x - 8, y, w, itemH, 0x30FFFFFF);
				skewedPart(g, x - 8, y, w, itemH, 0, 0, 3, itemH, ACCENT);
			}
			int tx = x + (hover ? 6 : 0);
			int ty = y + (itemH - 16) / 2;
			big(g, font, bold(labels[i]), tx, ty, 2, hover ? TEXT : 0xFFD8DBE2);
			if (i == 0 && !LobbyClient.tutorialDone()) {
				String tag = "튜토리얼부터";
				int tagX = tx + font.width(bold(labels[i])) * 2 + 8;
				g.fill(tagX, ty + 3, tagX + font.width(tag) + 6, ty + 14, ACCENT);
				g.text(font, tag, tagX + 3, ty + 5, TEXT);
			}
			clickable(x - 8, y, w, itemH, labels[i], actions[i]);
			y += itemH + 4;
		}

		String name = minecraft.player != null ? minecraft.player.getGameProfile().name() : "";
		g.text(font, Component.literal(name), x, height - PAD - 8, MUTED);
	}

	private void play() {
		if (!LobbyClient.tutorialDone()) {
			send(MenuActionPayload.TUTORIAL, "", "");
			return;
		}
		minecraft.gui.setScreen(new ClassSelectScreen(ClassSelectScreen.Purpose.MATCH));
	}

	private void training() {
		minecraft.gui.setScreen(new ClassSelectScreen(ClassSelectScreen.Purpose.TRAINING));
	}

	/** 관리자 — 전장 · 훈련장을 나가 맵을 자유롭게 돌아다닙니다. */
	private void free() {
		send(MenuActionPayload.FREE, "", "");
	}

	private void patchNotes() {
		minecraft.gui.setScreen(new PatchNotesScreen());
	}

	private void options() {
		minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, true));
	}

	private void quit() {
		minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
	}
}
