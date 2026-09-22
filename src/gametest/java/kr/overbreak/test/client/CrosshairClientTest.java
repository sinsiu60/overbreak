package kr.overbreak.test.client;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.gunslinger.Gunslinger;
import kr.overbreak.client.hud.crosshair.CrosshairConfig;
import kr.overbreak.client.hud.crosshair.CrosshairScreen;
import kr.overbreak.core.tick.Ticks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * 커스텀 조준점 — 설정 화면 · 모양별 게임 화면 · 저장 (0.2f). 스크린샷으로 눈으로 확인합니다.
 */
public final class CrosshairClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runOnServer(s -> Classes.give(s.getPlayerList().getPlayers().getFirst(), Classes.byId(Gunslinger.ID)));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(20));

			// 설정 화면
			ctx.runOnClient(CrosshairScreen::open);
			ctx.waitTicks(Ticks.of(4));
			ctx.takeScreenshot("crosshair_screen");
			ctx.runOnClient(mc -> mc.gui.screen().onClose());
			ctx.waitTicks(Ticks.of(2));

			// 모양별 게임 화면
			CrosshairConfig.Type[] types = {CrosshairConfig.Type.CROSS, CrosshairConfig.Type.CIRCLE,
					CrosshairConfig.Type.CIRCLE_CROSS, CrosshairConfig.Type.DOT};
			for (CrosshairConfig.Type t : types) {
				ctx.runOnClient(mc -> {
					CrosshairConfig c = CrosshairConfig.get();
					c.reset();
					c.type = t;
					c.color = t == CrosshairConfig.Type.DOT ? 0xFF3030 : 0x00FF00;
					c.dotSize = t == CrosshairConfig.Type.CIRCLE ? 3 : 0;
				});
				ctx.waitTicks(Ticks.of(2));
				ctx.takeScreenshot("crosshair_" + t.name().toLowerCase());
			}
			// 저장 — 다시 읽어도 같은 값
			ctx.runOnClient(mc -> {
				CrosshairConfig c = CrosshairConfig.get();
				c.reset();
				c.thickness = 3;
				c.gap = 6;
				c.save();
			});
			ctx.runOnClient(mc -> {
				try {
					String json = java.nio.file.Files.readString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
							.resolve("overbreak-crosshair.json"));
					if (!json.contains("\"thickness\": 3") || !json.contains("\"gap\": 6")) {
						throw new AssertionError("조준점 설정이 저장되지 않음: " + json);
					}
				} catch (java.io.IOException e) {
					throw new AssertionError("조준점 설정 파일을 읽지 못함", e);
				}
				CrosshairConfig.get().reset();
				CrosshairConfig.get().save();
			});
		}
	}
}
