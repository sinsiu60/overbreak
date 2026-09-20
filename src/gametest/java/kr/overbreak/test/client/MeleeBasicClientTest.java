package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.combat.MeleeCleave;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 근접 직업 1인칭 평타 궤적 — 워리어 · 햄머나이트 · 셰이드 (0.2c).
 *
 * 셋 다 공용 표를 쓰던 것을 무기 무게에 맞는 직업별 표로 나눴습니다. 정방향 · 역방향을 구간별로 찍습니다.
 * 이름: 직업_방향_경과틱.
 */
public final class MeleeBasicClientTest implements FabricClientGameTest {
	private static final String[] CLASSES = {"warrior", "hammer_knight", "shade"};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			ctx.getInput().lookAt(0.0F, 4.0F);
			sp.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(Ticks.of(20));

			for (String id : CLASSES) {
				PvpClass c = Classes.byId(id);
				onServer(sp, p -> Classes.give(p, c));
				// HUD 로 직업 id 가 넘어가야 직업별 표가 골라집니다
				ctx.waitTicks(Ticks.of(10));
				ctx.runOnClient(mc -> {
					if (!id.equals(kr.overbreak.client.hud.HudState.classId())) {
						throw new AssertionError("HUD 직업 id 가 아직 " + kr.overbreak.client.hud.HudState.classId());
					}
				});

				// 정방향 — 치켜듦 · 임팩트 · 되돌아옴
				onServer(sp, p -> MeleeCleave.swing(p, c));
				shots(ctx, id + "_fwd", 1, 3, 5);
				ready(sp);
				// 이어서 휘두르면 역방향
				onServer(sp, p -> MeleeCleave.swing(p, c));
				shots(ctx, id + "_back", 1, 3, 5);
				ready(sp);
				ctx.waitTicks(Ticks.of(20));
			}
			onServer(sp, Classes::clear);
		}
	}

	/** 지정한 경과 틱마다 한 장씩. */
	private static void shots(ClientGameTestContext ctx, String label, int... at) {
		int now = 0;
		for (int t : at) {
			ctx.waitTicks(Ticks.of(t - now));
			now = t;
			ctx.takeScreenshot(label + "_" + t);
		}
	}

	/** 공격속도 잠금만 풀고 연속 횟수(정방향 · 역방향 번갈이)는 그대로 둡니다. */
	private static void ready(TestSingleplayerContext sp) {
		onServer(sp, p -> Attachments.profile(p).atkCd = 0);
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}
}
