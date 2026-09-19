package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.brute.Brute;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.combat.MeleeCleave;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/** 투귀 동작 — 3인칭 · 1인칭을 구간별로 찍습니다. 이름: 시점_동작_경과틱. */
public final class BruteAnimClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// 애니메이션 파일이 모두 읽히는지 먼저 봅니다 (뼈대 이름 · 채널 오류는 여기서 걸립니다)
		ctx.runOnClient(mc -> {
			for (String name : new String[] {
					"brute.basic", "brute.basic_back", "brute.blow", "brute.whirl", "brute.regroup", "brute.ult"}) {
				if (PlayerAnimations.get(name) == null) {
					throw new AssertionError("애니메이션 파일에 없음: " + name);
				}
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			onServer(sp, p -> Classes.give(p, brute()));
			ctx.getInput().lookAt(0.0F, 4.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(20));

			for (CameraType view : new CameraType[] {CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
				String tag = view == CameraType.FIRST_PERSON ? "fp" : "tp";
				ctx.runOnClient(mc -> mc.options.setCameraType(view));
				ctx.waitTicks(Ticks.of(4));

				// 평타 (두 방향)
				onServer(sp, p -> MeleeCleave.swing(p, brute()));
				shots(ctx, tag + "_basic", 2, 5);
				reset(sp);
				onServer(sp, p -> MeleeCleave.swing(p, brute()));
				shots(ctx, tag + "_basic_back", 2, 5);
				reset(sp);

				// 강타 — 들어올림 · 내리찍음
				onServer(sp, p -> brute().primary(p));
				shots(ctx, tag + "_blow", 5, 9);
				reset(sp);

				// 돌개바람
				onServer(sp, p -> brute().secondary(p));
				shots(ctx, tag + "_whirl", 4, 8, 14);
				reset(sp);

				// 전열 재정비
				onServer(sp, p -> brute().tertiary(p));
				shots(ctx, tag + "_regroup", 5, 13);
				reset(sp);

				// 무쌍
				onServer(sp, p -> {
					UltGauge.fill(p);
					brute().ult(p);
				});
				shots(ctx, tag + "_ult", 4, 8, 16);
				reset(sp);
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

	private static void reset(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			Attachments.profile(p).atkCd = 0;
			Attachments.combatant(p).casting = false;
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass brute() {
		return Classes.byId(Brute.ID);
	}
}
