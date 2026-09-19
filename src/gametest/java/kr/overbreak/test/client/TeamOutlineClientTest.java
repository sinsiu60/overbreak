package kr.overbreak.test.client;

import java.util.List;
import java.util.function.Consumer;

import kr.overbreak.client.hud.MatchTeams;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.TeamPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;

/**
 * 팀전 테두리 — 같은 사람이라도 보는 사람에 따라 색이 달라야 하므로 클라이언트가 칠합니다.
 * 앞에 마네킹을 하나 세우고 우리 편 → 상대 편으로 바꿔 가며 찍습니다.
 */
public final class TeamOutlineClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(10));

			int id = spawnMannequin(sp, 4.0);
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(Ticks.of(5));

			// 우리 편 — 파란 테두리
			ctx.runOnClient(mc -> MatchTeams.receive(new TeamPayload(List.of(id), List.of())));
			ctx.waitTicks(Ticks.of(6));
			check(ctx.computeOnClient(mc -> {
				var e = mc.level == null ? null : mc.level.getEntity(id);
				return e != null && MatchTeams.outline(e) == MatchTeams.ALLY;
			}), "우리 편은 파란 테두리");
			ctx.takeScreenshot("team_outline_ally");

			// 상대 편 — 빨간 테두리 (시야에 들어와 있을 때만)
			ctx.runOnClient(mc -> MatchTeams.receive(new TeamPayload(List.of(), List.of(id))));
			ctx.waitTicks(Ticks.of(6));
			check(ctx.computeOnClient(mc -> {
				var e = mc.level == null ? null : mc.level.getEntity(id);
				return e != null && MatchTeams.outline(e) == MatchTeams.FOE;
			}), "보이는 상대는 빨간 테두리");
			ctx.takeScreenshot("team_outline_foe");

			// 경기가 끝나면 테두리가 사라집니다
			ctx.runOnClient(mc -> MatchTeams.receive(TeamPayload.NONE));
			ctx.waitTicks(Ticks.of(4));
			check(ctx.computeOnClient(mc -> !MatchTeams.active()), "경기가 끝나면 테두리 없음");
			ctx.takeScreenshot("team_outline_off");
		}
	}

	private static int spawnMannequin(TestSingleplayerContext sp, double forward) {
		return sp.getServer().computeOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = p.level();
			Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
			if (m == null) {
				throw new AssertionError("마네킹을 만들지 못함");
			}
			m.snapTo(p.getX(), p.getY(), p.getZ() + forward, 180.0F, 0.0F);
			level.addFreshEntity(m);
			return m.getId();
		});
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
