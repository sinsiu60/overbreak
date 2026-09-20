package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.client.hud.HealScreen;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HealPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * 회복 화면 (0.2a) — 스킬로 체력을 되찾을 때 화면 가장자리가 초록으로 번지는지.
 *
 * 워리어 흡혈 · 투귀 강화 포션이 쓰는 길입니다. 서버에서 실제로 살육을 적중시켜
 * 흡혈까지 가는 대신, 서버가 보내는 그 신호를 그대로 넣어 화면까지 닿는지 봅니다.
 */
public final class HealScreenClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			onServer(sp, p -> Classes.give(p, Classes.byId(Warrior.ID)));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(10));

			ctx.runOnClient(mc -> HealScreen.clear());
			ctx.waitTicks(Ticks.of(2));
			check(ctx.computeOnClient(mc -> HealScreen.fade()) < 0.01F, "회복 중이 아니면 화면에 아무것도 없음");
			ctx.takeScreenshot("heal_none");

			// 흡혈 한 번 — 서버가 보내는 것과 같은 신호
			ctx.runOnClient(mc -> HealScreen.receive(new HealPayload(Ticks.of(HealPayload.PULSE))));
			ctx.waitTicks(Ticks.of(3));
			check(ctx.computeOnClient(mc -> HealScreen.fade()) > 0.5F,
					"흡혈 한 번이면 초록이 들어옴 (실측 " + ctx.computeOnClient(mc -> HealScreen.fade()) + ")");
			ctx.takeScreenshot("heal_pulse");

			// 투귀 강화 포션처럼 1초 내내
			ctx.runOnClient(mc -> HealScreen.receive(new HealPayload(Ticks.of(20))));
			ctx.waitTicks(Ticks.of(8));
			check(ctx.computeOnClient(mc -> HealScreen.fade()) > 0.9F, "계속 회복하는 동안은 계속 켜져 있음");
			ctx.takeScreenshot("heal_sustained");

			// 끝나면 빠집니다
			ctx.runOnClient(mc -> HealScreen.clear());
			ctx.waitTicks(Ticks.of(10));
			check(ctx.computeOnClient(mc -> HealScreen.fade()) < 0.01F, "끝나면 빠짐");
			ctx.takeScreenshot("heal_gone");
			onServer(sp, Classes::clear);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}
}
