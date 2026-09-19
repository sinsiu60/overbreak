package kr.overbreak.test.client;

import kr.overbreak.client.tutorial.CoreDialogue;
import kr.overbreak.client.tutorial.HudPointer;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.game.Game;
import kr.overbreak.game.Tutorial;
import kr.overbreak.net.TutorialCuePayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * 튜토리얼 연출 — 부팅 화면(시야가 열림) · 「코어」 대사창(글자가 한 자씩) · HUD 화살표.
 * 화면은 build/run/clientGameTest/screenshots 에 남습니다.
 */
public final class TutorialClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			// 시험 월드에는 훈련장이 없으니 그 자리에 방을 파 둡니다 (화면을 보기 위한 것)
			sp.getServer().runCommand("fill 96 -62 -34 128 -40 2 air");
			sp.getServer().runCommand("fill 96 -63 -34 128 -63 2 smooth_stone");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(10));

			onServer(sp, p -> {
				Game.enabled = true;
				// 접속 흐름을 타야 서버가 이 플레이어를 튜토리얼로 돌립니다
				Game.join(p);
				Game.startTutorial(p);
			});

			// ── 부팅 연출: 위아래 검은 띠가 물러나며 시야가 열림 ──
			for (int i = 0; i < 4; i++) {
				ctx.waitTicks(Ticks.of(12));
				ctx.takeScreenshot("tutorial_boot_" + i);
			}
			check(sp.getServer().computeOnServer(s -> Tutorial.stage(player(s)) == 0), "부팅 단계");

			// ── 에너지 장벽: 실제로 막히고 눈에는 푸른 장막 ──
			check(sp.getServer().computeOnServer(s -> s.overworld()
					.getBlockState(new net.minecraft.core.BlockPos(107, -55, -15))
					.is(net.minecraft.world.level.block.Blocks.BARRIER)), "에너지 장벽이 실제로 막고 있음 (높이 12)");
			ctx.takeScreenshot("tutorial_barrier");

			// ── 대사창: 글자가 찍히는 중 · 다 찍힌 뒤 ──
			waitFor(ctx, "대사창", () -> ctx.computeOnClient(mc -> CoreDialogue.visible()));
			ctx.takeScreenshot("tutorial_dialogue_typing");
			int typing = ctx.computeOnClient(mc -> CoreDialogue.typed());
			ctx.waitTicks(Ticks.of(14));
			int later = ctx.computeOnClient(mc -> CoreDialogue.typed());
			check(later > typing, "글자가 한 자씩 늘어남 (" + typing + " → " + later + ")");
			ctx.takeScreenshot("tutorial_dialogue");

			// ── 실제 조작으로 초반 단계를 통과 ──
			waitForStage(ctx, sp, 1, "이동 단계");
			ctx.takeScreenshot("tutorial_barrier_lit");
			// 시작 칸은 장벽으로 막혀 있으니 앞뒤로 오가며 (실제 플레이와 같게)
			for (int i = 0; i < 4; i++) {
				ctx.getInput().holdKeyFor(opts -> opts.keyUp, Ticks.of(12));
				ctx.getInput().holdKeyFor(opts -> opts.keyDown, Ticks.of(12));
			}
			waitForStage(ctx, sp, 2, "도약 단계");
			for (int i = 0; i < 4; i++) {
				ctx.getInput().holdKeyFor(opts -> opts.keyJump, Ticks.of(2));
				ctx.waitTicks(Ticks.of(10));
			}
			waitForStage(ctx, sp, 3, "웅크리기 단계");
			// 대사가 끝난 뒤부터 1초를 세므로 넉넉히 누르고 있습니다
			ctx.getInput().holdKeyFor(opts -> opts.keyShift, Ticks.of(140));
			waitForStage(ctx, sp, 4, "장벽 단계");
			ctx.getInput().holdKeyFor(opts -> opts.keyUp, Ticks.of(60));
			waitForStage(ctx, sp, 5, "규격 수신 단계");
			ctx.takeScreenshot("tutorial_played_to_class");
			check(sp.getServer().computeOnServer(s -> s.overworld()
					.getBlockState(new net.minecraft.core.BlockPos(107, -55, -15)).isAir()), "장벽이 열리면 막힘도 사라짐");

			// ── 규격 수신: 체력 화살표 ──
			onServer(sp, p -> Tutorial.debugStage(p, 5));
			waitFor(ctx, "체력 화살표", () -> ctx.computeOnClient(mc -> HudPointer.target()) == TutorialCuePayload.P_HEALTH);
			ctx.waitTicks(Ticks.of(20));
			ctx.takeScreenshot("tutorial_health_pointer");

			// ── 평타: 무기 칸 화살표 ──
			onServer(sp, p -> Tutorial.debugStage(p, 6));
			waitFor(ctx, "무기 화살표", () -> ctx.computeOnClient(mc -> HudPointer.target()) == TutorialCuePayload.P_WEAPON);
			ctx.waitTicks(Ticks.of(20));
			ctx.takeScreenshot("tutorial_weapon_pointer");

			// ── 스킬: 우클릭 칸 화살표 ──
			onServer(sp, p -> Tutorial.debugStage(p, 8));
			waitFor(ctx, "스킬 화살표", () -> ctx.computeOnClient(mc -> HudPointer.target()) == TutorialCuePayload.P_SKILL1);
			ctx.waitTicks(Ticks.of(25));
			ctx.takeScreenshot("tutorial_skill_pointer");

			// ── 궁극기 칸 화살표 ──
			onServer(sp, p -> Tutorial.debugStage(p, 11));
			waitFor(ctx, "궁극기 화살표", () -> ctx.computeOnClient(mc -> HudPointer.target()) == TutorialCuePayload.P_ULT);
			ctx.waitTicks(Ticks.of(25));
			ctx.takeScreenshot("tutorial_ult_pointer");

			// ── 끝내면 화살표 · 대사창이 사라짐 ──
			onServer(sp, p -> {
				Tutorial.skip(p);
				Game.toMenu(p);
			});
			waitFor(ctx, "화살표 치움", () -> ctx.computeOnClient(mc -> HudPointer.target()) < 0);
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

	/** 그 단계가 될 때까지 (대사가 다 끝나야 넘어갑니다 — 넉넉히 기다림). */
	private static void waitForStage(ClientGameTestContext ctx, TestSingleplayerContext sp, int stage, String what) {
		for (int i = 0; i < Ticks.of(600); i++) {
			if (sp.getServer().computeOnServer(s -> Tutorial.stage(player(s))) >= stage) {
				return;
			}
			ctx.waitTick();
		}
		throw new AssertionError("기다려도 안 됨: " + what + " (지금 "
				+ sp.getServer().computeOnServer(s -> Tutorial.stage(player(s))) + ")");
	}

	private static void waitFor(ClientGameTestContext ctx, String what, java.util.function.BooleanSupplier ok) {
		for (int i = 0; i < Ticks.of(150); i++) {
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
