package kr.overbreak.test.client;

import kr.overbreak.client.lobby.ClassSelectScreen;
import kr.overbreak.client.lobby.LobbyClient;
import kr.overbreak.client.lobby.LobbyScreen;
import kr.overbreak.client.lobby.ModeSelectScreen;
import kr.overbreak.client.lobby.QueueScreen;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.game.Game;
import kr.overbreak.game.Session;
import kr.overbreak.net.MenuPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

/**
 * 메인 화면 — 실제 마우스 클릭으로 흐름을 따라갑니다.
 *
 *   접속 → 메인 화면 (관전 모드) → 플레이(처음) = 튜토리얼 → ESC 메뉴의 "메인 화면으로"
 *   → 훈련장 → 규격 선택 → 훈련장 입장 → 다시 메인 → 플레이 → 규격 → 게임 종류 → 1대1 대기 → 취소
 */
public final class LobbyClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(10));
			// 시험 실행은 메인 화면이 꺼져 있음 — 켜고 접속을 다시 흉내
			onServer(sp, p -> {
				Game.enabled = true;
				Session.setTutorialDone(p, false);
				Game.join(p);
			});
			waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));
			check(sp.getServer().computeOnServer(s -> player(s).gameMode() == GameType.SPECTATOR), "메인 화면은 관전 모드");
			ctx.takeScreenshot("lobby_main_first");

			// 처음 플레이 → 튜토리얼
			click(ctx, "플레이");
			waitFor(ctx, "튜토리얼 시작 (화면 닫힘)", () -> LobbyClient.screenState() == MenuPayload.CLOSED
					&& sp.getServer().computeOnServer(s -> Session.of(player(s)).place == Session.Place.TUTORIAL));
			check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "튜토리얼에서는 창이 없음");
			ctx.waitTicks(Ticks.of(20));
			ctx.takeScreenshot("lobby_tutorial");

			// ESC 메뉴에 "메인 화면으로"
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(Ticks.of(3));
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof PauseScreen p
					&& Screens.getWidgets(p).stream().anyMatch(w -> w.getMessage().getString().equals("메인 화면으로"))), "ESC 메뉴에 메인 화면으로 버튼");
			ctx.takeScreenshot("lobby_pause_button");
			onServer(sp, p -> {
				Session.setTutorialDone(p, true);
				Game.toMenu(p);
			});
			waitFor(ctx, "메인 화면으로 돌아옴", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));
			ctx.takeScreenshot("lobby_main");

			// 패치노트
			click(ctx, "패치노트");
			waitFor(ctx, "패치노트 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof kr.overbreak.client.lobby.PatchNotesScreen));
			ctx.waitTicks(Ticks.of(4));
			ctx.takeScreenshot("lobby_patch_notes");
			click(ctx, "뒤로");
			waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));

			// 훈련장: 규격 선택 → 입장
			click(ctx, "훈련장");
			waitFor(ctx, "규격 선택 (훈련장)", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClassSelectScreen));
			click(ctx, "sheriff");
			ctx.takeScreenshot("lobby_class_select_training");
			click(ctx, "훈련장 입장");
			waitFor(ctx, "훈련장 입장", () -> sp.getServer().computeOnServer(s -> Session.of(player(s)).place == Session.Place.TRAINING));
			check(sp.getServer().computeOnServer(s -> Attachments.profile(player(s)).pvpClass != null
					&& "sheriff".equals(Attachments.profile(player(s)).pvpClass.id())), "고른 규격(보안관)으로 훈련장");
			waitFor(ctx, "훈련장에서 창 닫힘", () -> ctx.computeOnClient(mc -> mc.gui.screen() == null));
			ctx.waitTicks(Ticks.of(10));
			ctx.takeScreenshot("lobby_training");

			// 다시 메인 → 플레이 → 규격 → 게임 종류 → 대기
			onServer(sp, Game::toMenu);
			waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));
			click(ctx, "플레이");
			waitFor(ctx, "규격 선택 (경기)", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClassSelectScreen));
			click(ctx, "warrior");
			ctx.takeScreenshot("lobby_class_select");
			click(ctx, "선택");
			waitFor(ctx, "게임 종류", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModeSelectScreen));
			ctx.takeScreenshot("lobby_mode_select");
			click(ctx, "1대1 매치");
			waitFor(ctx, "대기열", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof QueueScreen) && Game.queued() == 1);
			ctx.waitTicks(Ticks.of(50));
			ctx.takeScreenshot("lobby_queue");
			click(ctx, "취소");
			waitFor(ctx, "대기 취소 → 메인", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen) && Game.queued() == 0);

			// 팀 격전: 2대2 · 3대3 중에 고르고, 혼자면 인원이 모일 때까지 대기
			click(ctx, "플레이");
			waitFor(ctx, "규격 선택 (팀전)", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClassSelectScreen));
			click(ctx, "shade");
			click(ctx, "선택");
			waitFor(ctx, "게임 종류", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModeSelectScreen));
			ctx.takeScreenshot("lobby_mode_select_three");
			click(ctx, "팀 격전");
			waitFor(ctx, "팀 격전 인원 고르기",
					() -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof kr.overbreak.client.lobby.TeamSizeScreen));
			ctx.takeScreenshot("lobby_team_size");
			click(ctx, "3대3 매치");
			waitFor(ctx, "팀전 대기열", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof QueueScreen)
					&& Game.teamQueued(3) == 1);
			ctx.waitTicks(Ticks.of(40));
			ctx.takeScreenshot("lobby_queue_team");
			click(ctx, "취소");
			waitFor(ctx, "대기 취소 → 메인", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen) && Game.teamQueued(3) == 0);

			// 편 짜기 화면 모양 (실제로는 인원이 다 모여야 뜹니다)
			ctx.runOnClient(mc -> {
				kr.overbreak.client.lobby.LobbyClient.debugDraft(new kr.overbreak.net.TeamDraftPayload(
						java.util.List.of("Player0", "아군1"), java.util.List.of("적1"), java.util.List.of("고민중"), 0, 9, 3));
				mc.gui.setScreen(new kr.overbreak.client.lobby.TeamDraftScreen());
			});
			ctx.waitTicks(Ticks.of(4));
			ctx.takeScreenshot("lobby_team_draft");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));

			// 대난투: 혼자 고르면 모집 상태로 들어갑니다 (3명이 모여야 시작)
			click(ctx, "플레이");
			waitFor(ctx, "규격 선택 (대난투)", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ClassSelectScreen));
			click(ctx, "thunder");
			ctx.waitTicks(Ticks.of(6));
			ctx.takeScreenshot("lobby_class_select_pick");
			click(ctx, "선택");
			waitFor(ctx, "게임 종류", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModeSelectScreen));
			click(ctx, "대난투");
			waitFor(ctx, "대난투 모집", () -> sp.getServer().computeOnServer(s -> Session.of(player(s)).place == Session.Place.BRAWL)
					&& Game.brawl() != null && Game.brawl().size() == 1);
			ctx.waitTicks(Ticks.of(20));
			check(ctx.computeOnClient(mc -> kr.overbreak.client.hud.ScoreHud.state().kind() == kr.overbreak.net.ScorePayload.BRAWL),
					"대난투 점수판이 화면 위에 뜸");
			ctx.takeScreenshot("brawl_score_hud");
			// 1대1 점수판 모양 확인 (두 명을 붙이는 대신 화면에 직접 넣어 봅니다)
			ctx.runOnClient(mc -> kr.overbreak.client.hud.ScoreHud.receive(new kr.overbreak.net.ScorePayload(
					kr.overbreak.net.ScorePayload.DUEL, java.util.List.of("Player0", "상대"), java.util.List.of(2, 1), 0, 3, "라운드 4")));
			ctx.waitTicks(Ticks.of(4));
			ctx.takeScreenshot("duel_score_hud");
			onServer(sp, Game::toMenu);
			waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));

			// 관리자 모드 (게임마스터 권한이 있을 때만 메뉴에 보입니다)
			if (ctx.computeOnClient(mc -> LobbyClient.buttonCenter("관리자 모드") != null)) {
				click(ctx, "관리자 모드");
				waitFor(ctx, "관리자 자유 이동", () -> sp.getServer().computeOnServer(s -> Session.of(player(s)).place == Session.Place.FREE
						&& player(s).gameMode() == GameType.CREATIVE));
				ctx.waitTicks(Ticks.of(10));
				ctx.takeScreenshot("admin_free");
				onServer(sp, Game::toMenu);
				waitFor(ctx, "메인 화면", () -> ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen));
			}

			// 메인 화면에서 ESC 로는 닫히지 않음
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(Ticks.of(3));
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof LobbyScreen), "메인 화면은 ESC 로 닫히지 않음");

			onServer(sp, p -> {
				Game.enabled = false;
				Game.resetForTest();
			});
		}
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void onServer(TestSingleplayerContext sp, java.util.function.Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(s -> action.accept(player(s)));
	}

	/** 이름으로 찾은 버튼 가운데를 실제 마우스로 클릭. */
	private static void click(ClientGameTestContext ctx, String name) {
		ctx.waitTicks(Ticks.of(2));
		double[] at = ctx.computeOnClient(mc -> {
			double[] c = LobbyClient.buttonCenter(name);
			if (c == null) {
				return null;
			}
			var w = mc.getWindow();
			return new double[] {c[0] * w.getScreenWidth() / w.getGuiScaledWidth(), c[1] * w.getScreenHeight() / w.getGuiScaledHeight()};
		});
		if (at == null) {
			throw new AssertionError("버튼을 찾지 못함: " + name);
		}
		ctx.getInput().setCursorPos(at[0], at[1]);
		ctx.waitTick();
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(Ticks.of(2));
	}

	private static void waitFor(ClientGameTestContext ctx, String what, java.util.function.BooleanSupplier ok) {
		for (int i = 0; i < Ticks.of(100); i++) {
			if (ok.getAsBoolean()) {
				return;
			}
			ctx.waitTick();
		}
		throw new AssertionError("기다려도 안 됨: " + what);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
