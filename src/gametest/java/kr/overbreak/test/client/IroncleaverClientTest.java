package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.ironcleaver.Ironcleaver;
import kr.overbreak.client.fx.IronFx;
import kr.overbreak.client.hud.HudLayouts;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 참철 — 진짜 LMB 입력으로 평타 · 모으기를 쓰고 1인칭 · 3인칭을 구간별로 찍습니다. 이름: ic_시점_동작_경과.
 *
 *   역경직: 평타가 앞의 표적을 맞히면 서버가 알린 순간 1인칭 칼이 멈추는지 (IronFx.frozen) 확인
 *   모으기: 단계 색 오오라 (초록 · 노랑 · 빨강 · 진 참 흰빛) · 게이지
 *   대지 가르기 칼날이 땅을 따라 달리는지 · 천참 예고
 */
public final class IroncleaverClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ctx.runOnClient(mc -> {
			if (HudLayouts.get(Ironcleaver.ID) == null) {
				throw new AssertionError("HudLayouts 에 ironcleaver 가 없음");
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			onServer(sp, p -> Classes.give(p, Classes.byId(Ironcleaver.ID)));
			ctx.getInput().lookAt(0.0F, 8.0F);
			sp.getConnection().waitForChunksRender();
			// 앞 3칸 표적 (맞아도 죽지 않게)
			sp.getServer().runCommand("execute as @p at @s run summon minecraft:husk ~ ~ ~3 {NoAI:1b,Invulnerable:1b,Silent:1b}");
			ctx.waitTicks(Ticks.of(20));

			for (CameraType view : new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
				String tag = view == CameraType.FIRST_PERSON ? "fp" : "tp";
				ctx.runOnClient(mc -> mc.options.setCameraType(view));
				ctx.waitTicks(Ticks.of(6));
				ctx.takeScreenshot("ic_" + tag + "_idle");

				// 1타 — 짧게 누름. 판정 순간 앞 표적을 맞히면 역경직
				ctx.getInput().holdKey(o -> o.keyAttack);
				ctx.waitTicks(1);
				// 누른 즉시(서버 확인 전) 본인 화면에서 1타가 시작됐는가
				ctx.runOnClient(mc -> {
					if (mc.player == null || kr.overbreak.client.anim.SkillAnims.find(mc.player.getId(),
							kr.overbreak.net.SkillAnimPayload.IC_SWING_R) == null) {
						throw new AssertionError("LMB 를 누른 즉시 1타 휘두르기가 시작되지 않음");
					}
				});
				ctx.getInput().releaseKey(o -> o.keyAttack);
				boolean[] froze = {false};
				int[] frozeAt = {-1};
				int now = 0;
				int[] shots = {4, 7, 12, 17};
				int s = 0;
				for (int t = 1; t <= Ticks.of(22); t++) {
					ctx.waitTicks(1);
					int unit = (int) Math.floor(t * Ticks.step());
					ctx.runOnClient(mc -> {
						if (IronFx.frozen(1.0F) && !froze[0]) {
							froze[0] = true;
						}
					});
					if (froze[0] && frozeAt[0] < 0) {
						frozeAt[0] = unit;
						ctx.takeScreenshot("ic_" + tag + "_swing1_hitstop");
					}
					if (s < shots.length && unit >= shots[s] && t % 3 == 0) {
						ctx.takeScreenshot("ic_" + tag + "_swing1_" + shots[s]);
						s++;
					}
				}
				if (!froze[0]) {
					throw new AssertionError(tag + ": 평타가 표적을 맞혔는데 역경직이 걸리지 않음");
				}
				System.out.println("[IroncleaverClientTest] " + tag + " 역경직 시작 " + frozeAt[0] + " (1/20초)");
				// 2타 · 3타
				tap(ctx);
				ctx.waitTicks(Ticks.of(9));
				ctx.takeScreenshot("ic_" + tag + "_swing2_9");
				ctx.waitTicks(Ticks.of(10));
				tap(ctx);
				ctx.waitTicks(Ticks.of(8));
				ctx.takeScreenshot("ic_" + tag + "_swing3_8");
				ctx.waitTicks(Ticks.of(3));
				ctx.takeScreenshot("ic_" + tag + "_swing3_11");
				ctx.waitTicks(Ticks.of(30));

				// 모으기 — 누른 채로 단계별
				ctx.getInput().holdKey(o -> o.keyAttack);
				ctx.waitTicks(Ticks.of(8));
				ctx.takeScreenshot("ic_" + tag + "_charge_8");
				ctx.waitTicks(Ticks.of(8));
				ctx.takeScreenshot("ic_" + tag + "_charge_16_stage1");
				ctx.waitTicks(Ticks.of(12));
				ctx.takeScreenshot("ic_" + tag + "_charge_28_stage2");
				ctx.waitTicks(Ticks.of(9));
				ctx.takeScreenshot("ic_" + tag + "_charge_37_perfect");
				ctx.runOnClient(mc -> {
					if (mc.player == null || IronFx.stage(mc.player.getId()) < 3) {
						throw new AssertionError("1.85초 모았는데 3단이 아님");
					}
				});
				ctx.getInput().releaseKey(o -> o.keyAttack);
				ctx.waitTicks(Ticks.of(2));
				ctx.takeScreenshot("ic_" + tag + "_release_2");
				ctx.waitTicks(Ticks.of(4));
				ctx.takeScreenshot("ic_" + tag + "_release_6");
				ctx.waitTicks(Ticks.of(30));

				// 검막
				onServer(sp, p -> Classes.byId(Ironcleaver.ID).secondary(p));
				ctx.waitTicks(Ticks.of(6));
				ctx.takeScreenshot("ic_" + tag + "_guard");
				ctx.waitTicks(Ticks.of(20));

				// 어깨 박치기
				onServer(sp, p -> Classes.byId(Ironcleaver.ID).primary(p));
				ctx.waitTicks(Ticks.of(3));
				ctx.takeScreenshot("ic_" + tag + "_bash");
				ctx.waitTicks(Ticks.of(20));
				// 박치기로 밀려난 표적 대신 새로
				sp.getServer().runCommand("execute as @p at @s run tp @e[type=minecraft:husk] ~ ~ ~3");
				reset(sp);

				// 대지 가르기 — 땅을 달리는 칼날
				onServer(sp, p -> Classes.byId(Ironcleaver.ID).tertiary(p));
				ctx.waitTicks(Ticks.of(8));
				ctx.takeScreenshot("ic_" + tag + "_rend_8");
				ctx.waitTicks(Ticks.of(3));
				ctx.takeScreenshot("ic_" + tag + "_rend_11");
				ctx.waitTicks(Ticks.of(5));
				ctx.takeScreenshot("ic_" + tag + "_rend_16");
				ctx.runOnClient(mc -> {
					if (IronFx.wavePos() == null && IronFx.active() == 0) {
						throw new AssertionError("대지 가르기 칼날이 없음");
					}
				});
				ctx.waitTicks(Ticks.of(17));

				// 천참 — 예고 · 내려침
				onServer(sp, p -> {
					UltGauge.fill(p);
					Classes.byId(Ironcleaver.ID).ult(p);
				});
				ctx.waitTicks(Ticks.of(12));
				ctx.takeScreenshot("ic_" + tag + "_ult_12");
				ctx.waitTicks(Ticks.of(13));
				ctx.takeScreenshot("ic_" + tag + "_ult_25");
				ctx.waitTicks(Ticks.of(30));
				reset(sp);
			}
			onServer(sp, Classes::clear);
		}
	}

	private static void tap(ClientGameTestContext ctx) {
		ctx.getInput().holdKey(o -> o.keyAttack);
		ctx.waitTicks(1);
		ctx.getInput().releaseKey(o -> o.keyAttack);
	}

	private static void reset(TestSingleplayerContext sp) {
		onServer(sp, p -> Attachments.profile(p).cooldowns.clear());
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			action.accept(p);
		});
	}
}
