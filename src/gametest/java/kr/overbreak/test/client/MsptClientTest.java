package kr.overbreak.test.client;

import java.util.Locale;

import kr.overbreak.core.tick.TickRate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * 60틱 서버 부하 — 봇 0 · 10 · 20 명이 스킬을 계속 쓰는 동안 평균 틱 처리 시간(MSPT)을 [mspt] 로그로 남깁니다.
 * 한 플레이 서버(클라이언트와 같은 컴퓨터)라 전용 서버보다 불리한 값입니다. 10명에서 16.7ms 를 넘으면 실패.
 */
public final class MsptClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(300);
			double base = sample(ctx, sp, 0);
			double ten = sample(ctx, sp, 10);
			double twenty = sample(ctx, sp, 20);
			sp.getServer().runCommand("overbreak bots clear");
			System.out.println(String.format(Locale.ROOT, "[mspt] 60틱 예산 16.67ms  봇0 %.2fms  봇10 %.2fms  봇20 %.2fms", base, ten, twenty));
			if (ten > 1000.0 / 60.0) {
				throw new AssertionError(String.format(Locale.ROOT, "봇 10명 MSPT %.2fms > 16.67ms", ten));
			}
		}
	}

	/** 봇을 세우고 5초 안정 뒤 5초 동안 1초마다 잰 평균. */
	private static double sample(ClientGameTestContext ctx, TestSingleplayerContext sp, int bots) {
		if (bots > 0) {
			sp.getServer().runCommand("execute positioned 0 ~ 0 run overbreak bots " + bots);
		} else {
			sp.getServer().runCommand("overbreak bots clear");
		}
		ctx.waitTicks(300);
		double sum = 0.0;
		for (int i = 0; i < 5; i++) {
			ctx.waitTicks(60);
			sum += sp.getServer().computeOnServer(TickRate::mspt);
		}
		double avg = sum / 5.0;
		System.out.println(String.format(Locale.ROOT, "[mspt] 봇 %d명: %.2fms", bots, avg));
		return avg;
	}
}
