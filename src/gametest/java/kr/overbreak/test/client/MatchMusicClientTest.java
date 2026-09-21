package kr.overbreak.test.client;

import kr.overbreak.client.audio.MatchMusicPlayer;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.MusicPayload;
import kr.overbreak.sound.OverbreakSounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * 경기 막판 음악 — 소리 파일이 읽히고, 켜면 울리고, 끄면 서서히 빠져 멈추는지.
 * (어느 경기에서 켜지는지는 서버 MatchMusic 이 정하고, 여기서는 받는 쪽만 봅니다.)
 */
public final class MatchMusicClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> {
				// sounds.json 에 등록돼 있고 곡 파일(스트리밍)까지 이어져 있는가
				var events = mc.getSoundManager().getSoundEvent(OverbreakSounds.MATCH_POINT.value().location());
				if (events == null) {
					throw new AssertionError("music.match_point 가 sounds.json 에 없음");
				}
				MatchMusicPlayer.receive(new MusicPayload(true));
			});
			ctx.waitTicks(Ticks.of(20));
			ctx.runOnClient(mc -> {
				if (!MatchMusicPlayer.playing()) {
					throw new AssertionError("켜라고 했는데 음악이 안 울림");
				}
				MatchMusicPlayer.receive(new MusicPayload(false));
			});
			// 3초에 걸쳐 빠진 뒤 멈춤
			ctx.waitTicks(Ticks.of(80));
			ctx.runOnClient(mc -> {
				if (MatchMusicPlayer.playing()) {
					throw new AssertionError("끄라고 했는데 음악이 계속 울림");
				}
			});
		}
	}
}
