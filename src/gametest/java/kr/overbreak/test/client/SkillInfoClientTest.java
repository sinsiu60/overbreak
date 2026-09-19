package kr.overbreak.test.client;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.client.hud.SkillInfoScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** F8 스킬 설명 화면 — 열기 · 카드 가리키기(세부 수치) · 닫기. */
public final class SkillInfoClientTest implements FabricClientGameTest {
	private static final int F8 = 297;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runOnServer(server -> Classes.give(server.getPlayerList().getPlayers().getFirst(), Classes.byId(Warrior.ID)));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

			ctx.getInput().pressKey(F8);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			if (!SkillInfoScreen.open) {
				throw new AssertionError("F8 로 스킬 설명 화면이 열려야 함");
			}
			ctx.takeScreenshot("skillinfo");

			for (int i : new int[] {0, 2, 4, 5}) {
				final int card = i;
				ctx.runOnClient(mc -> SkillInfoScreen.debugHover = card);
				ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
				ctx.takeScreenshot("skillinfo_hover_" + card);
			}
			ctx.runOnClient(mc -> SkillInfoScreen.debugHover = -1);

			ctx.getInput().pressKey(F8);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			if (SkillInfoScreen.open) {
				throw new AssertionError("F8 을 다시 누르면 닫혀야 함");
			}

			// 새 직업(투귀) — 스킬 HUD 아이콘 · 투기 스택 칸 · 설명 화면
			sp.getServer().runOnServer(server -> Classes.give(server.getPlayerList().getPlayers().getFirst(),
					Classes.byId(kr.overbreak.classes.brute.Brute.ID)));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(8));
			ctx.takeScreenshot("brute_hud");
			ctx.getInput().pressKey(F8);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			ctx.takeScreenshot("skillinfo_brute");
			ctx.runOnClient(mc -> SkillInfoScreen.debugHover = 5);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
			ctx.takeScreenshot("skillinfo_brute_ult");
			ctx.runOnClient(mc -> SkillInfoScreen.debugHover = -1);
			ctx.getInput().pressKey(F8);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(4));
		}
	}
}
