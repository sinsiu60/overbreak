package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.brute.Brute;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;

/**
 * 인벤토리 키(E) — 전장에서는 창이 열리지 않고 액티브3 이 나갑니다 (0.2).
 *
 *   직업이 있을 때  : 창이 안 열리고 스킬 쿨이 돎
 *   직업이 없을 때  : 로비 · 관리자 자유 이동처럼 평소대로 열림
 */
public final class InventoryKeyClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();

			// ── 직업이 없을 때는 그대로 열립니다 ──────────────
			openInventory(ctx);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "직업이 없으면 인벤토리가 열림");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(Ticks.of(2));

			// ── 직업이 있으면 창 대신 스킬 ────────────────────
			onServer(sp, p -> Classes.give(p, Classes.byId(Brute.ID)));
			ctx.waitTicks(Ticks.of(10));
			String regroup = Brute.skillKeys().get(2);
			check(cooldown(sp, regroup) == 0, "아직 액티브3 을 쓰지 않음");

			openInventory(ctx);
			check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "전장에서는 인벤토리가 열리지 않음");
			ctx.waitTicks(Ticks.of(10));
			check(cooldown(sp, regroup) > 0, "대신 액티브3 이 나감 (쿨타임이 돎)");
			ctx.takeScreenshot("inventory_key_is_skill");

			onServer(sp, Classes::clear);
			ctx.waitTicks(Ticks.of(5));

			// ── 직업을 놓으면 다시 열립니다 ───────────────────
			openInventory(ctx);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "직업을 놓으면 다시 열림");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	/** 인벤토리 키를 누른 것과 같은 일 — 바닐라는 이 창을 띄웁니다. */
	private static void openInventory(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			if (mc.player != null) {
				mc.gui.setScreen(new InventoryScreen(mc.player));
			}
		});
		ctx.waitTicks(Ticks.of(2));
	}

	private static int cooldown(TestSingleplayerContext sp, String key) {
		return sp.getServer().computeOnServer(server ->
				Attachments.profile(server.getPlayerList().getPlayers().getFirst()).cooldown(key));
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
