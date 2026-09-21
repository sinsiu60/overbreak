package kr.overbreak.game;

import kr.overbreak.net.MusicPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 경기 막판 배경 음악 — 누가 들어야 하는지 매 틱 보고, 바뀐 사람에게만 켜기 · 끄기를 보냅니다.
 *
 *   1대1 · 팀 격전 : 어느 쪽이든 한 점만 더 내면 이기는 "매치 포인트" 부터 경기 끝까지
 *   대난투        : 누군가 이기는 킬 수까지 {@link #BRAWL_LEFT} 킬만 남았을 때부터 경기 끝까지 (관전자 포함)
 *
 * 들어오고 나가는 사람 · 기권 · 경기 끝은 따로 챙기지 않아도 여기서 저절로 맞춰집니다.
 */
public final class MatchMusic {
	/** 대난투 — 1등이 이기기까지 이만큼 남으면 음악. */
	public static final int BRAWL_LEFT = 10;

	private MatchMusic() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MatchMusic::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Session s = Session.of(p);
			boolean want = climax(s);
			if (want != s.musicOn) {
				s.musicOn = want;
				MusicPayload.send(p, want);
			}
		}
	}

	/** 이 사람이 있는 경기가 지금 막판인가. */
	static boolean climax(Session s) {
		if (s.duel != null && s.place == Session.Place.DUEL) {
			return s.duel.climax();
		}
		if (s.team != null && s.place == Session.Place.TEAM) {
			return s.team.climax();
		}
		if (s.brawl != null && s.place == Session.Place.BRAWL) {
			return s.brawl.climax();
		}
		return false;
	}
}
