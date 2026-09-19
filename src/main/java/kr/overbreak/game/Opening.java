package kr.overbreak.game;

import kr.overbreak.net.TutorialCuePayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 경기 투입 연출 — 전장에 들어갈 때 튜토리얼 오프닝과 같은 「가상세계 부팅」 화면을 짧게 보여 줍니다.
 *
 * 튜토리얼판과 다른 점: 시점을 1인칭으로 바꾸지 않고 (바로 싸워야 하므로) 문구도 전장용입니다.
 * 화면 효과일 뿐이라 게임은 그대로 돌아갑니다 — 카운트다운 동안 지나갑니다.
 */
public final class Opening {
	/** 길이 (1/20초 단위 = 2초). */
	public static final int LENGTH = 40;
	/** 이 글이 붙어 오면 클라이언트가 전장판으로 그립니다. */
	public static final String MATCH = "match";

	private Opening() {}

	public static void play(ServerPlayer p) {
		TutorialCuePayload.send(p, TutorialCuePayload.BOOT, LENGTH, MATCH);
	}

	public static void play(Iterable<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			play(p);
		}
	}
}
