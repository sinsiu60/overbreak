package kr.overbreak.game;

import kr.overbreak.net.ScorePayload;
import net.minecraft.server.level.ServerPlayer;

/** 점수판 보내기 — 바뀐 때만 보냅니다 (매 틱 같은 값을 흘리지 않게). */
final class ScoreBoard {
	private ScoreBoard() {}

	static void send(ServerPlayer p, ScorePayload score) {
		Session s = Session.of(p);
		if (!score.equals(s.lastScore)) {
			s.lastScore = score;
			ScorePayload.send(p, score);
		}
	}

	static void clear(ServerPlayer p) {
		send(p, ScorePayload.NONE);
	}
}
