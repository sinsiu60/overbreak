package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.client.hud.DamageFeedback;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.game.Opening;
import kr.overbreak.net.HurtPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * 피격 피드백 (오버워치식 방향 표시) · 경기 투입 연출 — 화면을 찍어 둡니다.
 *
 *   맞은 방향에 붉은 호가 뜨는지, 연달아 맞아도 표시가 하나로 뭉치는지
 *   전장에 들어갈 때 도는 부팅 화면
 */
public final class FeedbackClientTest implements FabricClientGameTest {
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

			// 정면 · 왼쪽 뒤 · 오른쪽에서 맞음
			ctx.runOnClient(mc -> {
				DamageFeedback.clear();
				float yaw = mc.player == null ? 0.0F : mc.player.getYRot();
				DamageFeedback.receive(new HurtPayload(yaw, 40.0F, true));
				DamageFeedback.receive(new HurtPayload(yaw + 135.0F, 25.0F, true));
				DamageFeedback.receive(new HurtPayload(yaw - 90.0F, 60.0F, true));
			});
			ctx.waitTicks(Ticks.of(2));
			check(ctx.computeOnClient(mc -> DamageFeedback.markCount() == 3), "방향마다 표시 하나씩");
			ctx.takeScreenshot("hurt_directions");

			// 같은 방향으로 연달아 맞으면 표시가 늘지 않고 진해지기만 합니다 (발키리 연사)
			ctx.runOnClient(mc -> {
				float yaw = mc.player == null ? 0.0F : mc.player.getYRot();
				for (int i = 0; i < 8; i++) {
					DamageFeedback.receive(new HurtPayload(yaw, 7.0F, true));
				}
			});
			ctx.waitTicks(Ticks.of(2));
			check(ctx.computeOnClient(mc -> DamageFeedback.markCount() == 3), "연사에 맞아도 표시는 그대로");
			ctx.takeScreenshot("hurt_burst");

			// 방향을 모르는 피해 (낙하 등)
			ctx.runOnClient(mc -> {
				DamageFeedback.clear();
				DamageFeedback.receive(new HurtPayload(0.0F, 45.0F, false));
			});
			ctx.waitTicks(Ticks.of(2));
			ctx.takeScreenshot("hurt_all_around");

			// 경기 투입 연출
			ctx.runOnClient(mc -> DamageFeedback.clear());
			onServer(sp, Opening::play);
			ctx.waitTicks(Ticks.of(8));
			ctx.takeScreenshot("match_opening");
			ctx.waitTicks(Ticks.of(20));
			ctx.takeScreenshot("match_opening_late");
			onServer(sp, Classes::clear);
		}
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}
