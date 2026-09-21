package kr.overbreak.test.client;

import kr.overbreak.core.tick.Ticks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** 시야각 — 달리기 · 둔화 · 가속으로 이동 속도가 바뀌어도 시야각 배율은 1 그대로. */
public final class FovClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(10));
			check(ctx, "기본");
			sp.getServer().runCommand("effect give @a minecraft:slowness 30 3");
			ctx.waitTicks(Ticks.of(5));
			check(ctx, "둔화");
			sp.getServer().runCommand("effect clear @a");
			sp.getServer().runCommand("effect give @a minecraft:speed 30 4");
			ctx.waitTicks(Ticks.of(5));
			check(ctx, "가속");
			ctx.getInput().holdKey(o -> o.keySprint);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(Ticks.of(10));
			check(ctx, "가속 + 달리기");
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.getInput().releaseKey(o -> o.keySprint);
		}
	}

	private static void check(ClientGameTestContext ctx, String what) {
		float fov = ctx.computeOnClient(mc -> mc.player.getFieldOfViewModifier(true, 1.0F));
		System.out.println("[FovClientTest] " + what + " 시야각 배율 " + fov);
		if (Math.abs(fov - 1.0F) > 1.0E-4F) {
			throw new AssertionError(what + " 시야각 배율 " + fov + " (1 이어야 함)");
		}
	}
}
