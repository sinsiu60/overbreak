package kr.overbreak.client.lobby;

import kr.overbreak.net.MenuActionPayload;
import kr.overbreak.net.MenuPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 메인 화면 (클라이언트) — 서버가 보낸 상태(MenuPayload)대로 창을 띄웁니다.
 *
 *   MAIN    메인 화면 (플레이 · 훈련장 · 설정 · 나가기) → 규격 선택 → 게임 종류 → 대기
 *   QUEUE   매칭 대기 창 (취소)
 *   CLOSED  게임 중 — 창을 닫고, ESC 메뉴에 "메인 화면으로" 버튼
 *
 * 메인 화면 상태에서 창이 없어지면(다른 창이 닫힘 등) 다음 틱에 다시 띄웁니다. 메인 화면 동안은 1인칭으로 둡니다.
 */
public final class LobbyClient {
	/** 서버가 메인 화면을 쓰는가 (이번 접속에서 한 번이라도 받음). */
	private static boolean available;
	private static int screen = MenuPayload.CLOSED;
	private static boolean tutorialDone;
	private static String detail = "";
	private static boolean admin;
	private static kr.overbreak.net.TeamDraftPayload draft = kr.overbreak.net.TeamDraftPayload.NONE;
	private static long queueSince;
	private static CameraType savedCamera;
	/** 규격 선택에서 마지막으로 고른 직업. */
	static String lastClass = "";

	private LobbyClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE, (payload, context) -> receive(context.client(), payload));
		// 편 짜기 내용 (누가 어느 편인지)
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.TeamDraftPayload.TYPE, (payload, context) -> draft = payload);
		ClientTickEvents.END_CLIENT_TICK.register(LobbyClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			available = false;
			screen = MenuPayload.CLOSED;
			savedCamera = null;
		}));
		ScreenEvents.AFTER_INIT.register((client, s, w, h) -> {
			if (s instanceof PauseScreen && available && screen == MenuPayload.CLOSED && client.level != null) {
				addLobbyButton(client, s, w, h);
			}
		});
	}

	static void receive(Minecraft mc, MenuPayload p) {
		available = true;
		int before = screen;
		screen = p.screen();
		tutorialDone = p.tutorialDone();
		detail = p.detail();
		admin = p.admin();
		if (screen == MenuPayload.QUEUE && before != MenuPayload.QUEUE) {
			queueSince = System.currentTimeMillis();
		}
		if (screen == MenuPayload.CLOSED) {
			if (mc.gui.screen() instanceof LobbyBase) {
				mc.gui.setScreen(null);
			}
			if (savedCamera != null) {
				mc.options.setCameraType(savedCamera);
				savedCamera = null;
			}
			return;
		}
		if (screen == MenuPayload.TEAM_DRAFT) {
			if (!(mc.gui.screen() instanceof TeamDraftScreen)) {
				mc.gui.setScreen(new TeamDraftScreen());
			}
			return;
		}
		if (screen == MenuPayload.RESPAWN) {
			// 쓰러져 있는 동안만 뜨는 규격 변경 창 — 시점은 그대로 두고, 닫으면 다시 띄우지 않습니다
			lastClass = detail.isEmpty() ? lastClass : detail;
			mc.gui.setScreen(new ClassSelectScreen(ClassSelectScreen.Purpose.RESPAWN));
			return;
		}
		if (savedCamera == null) {
			savedCamera = mc.options.getCameraType();
		}
		mc.options.setCameraType(CameraType.FIRST_PERSON);
		mc.gui.toastManager().clear();
		mc.gui.setScreen(screen == MenuPayload.QUEUE ? new QueueScreen() : new LobbyScreen());
	}

	private static void tick(Minecraft mc) {
		if (!available || screen == MenuPayload.CLOSED || screen == MenuPayload.RESPAWN
				|| mc.level == null || mc.player == null) {
			return;
		}
		if (mc.gui.screen() == null) {
			mc.gui.setScreen(switch (screen) {
				case MenuPayload.QUEUE -> new QueueScreen();
				case MenuPayload.TEAM_DRAFT -> new TeamDraftScreen();
				default -> new LobbyScreen();
			});
		}
		if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}
	}

	private static void addLobbyButton(Minecraft mc, Screen pause, int w, int h) {
		Button b = Button.builder(Component.literal("메인 화면으로"), btn -> mc.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				LobbyBase.send(MenuActionPayload.LOBBY, "", "");
			} else {
				mc.gui.setScreen(pause);
			}
		}, Component.literal("메인 화면으로 갈까요?"), Component.literal("진행 중인 튜토리얼 · 훈련은 끝나고, 경기 중이면 기권으로 처리됩니다."))))
				.bounds(w / 2 - 102, h - 32, 204, 20)
				.build();
		Screens.getWidgets(pause).add(b);
	}

	public static boolean inLobby() {
		return available && screen != MenuPayload.CLOSED && screen != MenuPayload.RESPAWN;
	}

	static boolean tutorialDone() {
		return tutorialDone;
	}

	static String detail() {
		return detail;
	}

	/** 게임마스터 권한이 있는가 (메인 화면의 관리자 모드). */
	static boolean admin() {
		return admin;
	}

	/** 편 짜기 화면 내용 (팀 격전). */
	static kr.overbreak.net.TeamDraftPayload draft() {
		return draft;
	}

	/** 시험용: 편 짜기 내용을 직접 넣습니다. */
	public static void debugDraft(kr.overbreak.net.TeamDraftPayload payload) {
		draft = payload;
	}

	static long queueSince() {
		return queueSince;
	}

	/** 시험용: 지금 화면에서 이름이 name 인 버튼의 가운데 (GUI 좌표), 없으면 null. */
	public static double @org.jspecify.annotations.Nullable [] buttonCenter(String name) {
		for (LobbyBase.Hit h : LobbyBase.lastHits) {
			if (h.name().equals(name)) {
				return new double[] {h.x() + h.w() / 2.0, h.y() + h.h() / 2.0};
			}
		}
		return null;
	}

	/** 시험용. */
	public static int screenState() {
		return screen;
	}
}
