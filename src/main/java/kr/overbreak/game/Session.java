package kr.overbreak.game;

import com.mojang.serialization.Codec;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * 플레이어가 지금 게임 흐름의 어디에 있는가 (저장하지 않음) + 튜토리얼 완료 여부 (월드에 저장 · 죽어도 유지).
 *
 * managed 가 false 인 플레이어(시험용 가짜 플레이어 등 접속을 거치지 않은 몸)에게는 게임 흐름의 제한이 걸리지 않습니다.
 */
public final class Session {
	/**
	 * MENU · QUEUE 메인 화면 / TUTORIAL · TRAINING 연습 / DUEL 1대1 / BRAWL 대난투 (관전 포함) / TEAM 3대3 팀전 /
	 * FREE 관리자 자유 이동 (전장 · 훈련장 밖으로 나가 맵을 돌아다닐 수 있음)
	 */
	public enum Place { MENU, QUEUE, TUTORIAL, TRAINING, DUEL, BRAWL, TEAM, FREE }

	public static final AttachmentType<Session> TYPE = AttachmentRegistry.createDefaulted(Overbreak.id("session"), Session::new);
	public static final AttachmentType<Boolean> TUTORIAL_DONE = AttachmentRegistry.create(Overbreak.id("tutorial_done"),
			b -> b.persistent(Codec.BOOL).initializer(() -> false).copyOnDeath());

	public Place place = Place.MENU;
	/** 접속해서 게임 흐름에 들어온 플레이어. */
	public boolean managed;
	/** 메인 화면 상태를 클라이언트에 보냈는가 (접속 직후에는 채널 등록 전이라 못 보낼 수 있음). */
	boolean menuSent;
	/** 모드 없는 클라이언트에게 안내를 보냈는가. */
	boolean helpSent;
	int joinT;
	/** 죽음을 막았고 다음 틱에 되살려 제자리로 보냄. */
	boolean pendingRespawn;
	/** 대기 중인 게임에 들고 갈 직업. */
	String queuedClass = "";
	/** 대기 중인 게임 종류 (MenuActionPayload.MODE_*). */
	String queuedMode = "";
	Tutorial.@Nullable Run tutorial;
	@Nullable Duel duel;
	@Nullable Brawl brawl;
	@Nullable TeamMatch team;
	/** 대난투를 구경만 하는가 (경기 중에 들어온 사람). */
	boolean spectating;
	/** 마지막으로 보낸 점수판 — 바뀐 때만 보냅니다. */
	kr.overbreak.net.@Nullable ScorePayload lastScore;
	/** 경기 막판 음악을 켜 두었는가 (MatchMusic 이 바뀐 때만 보냄). */
	boolean musicOn;

	public static Session of(ServerPlayer p) {
		return p.getAttachedOrCreate(TYPE);
	}

	public static boolean tutorialDone(ServerPlayer p) {
		return Boolean.TRUE.equals(p.getAttachedOrCreate(TUTORIAL_DONE));
	}

	public static void setTutorialDone(ServerPlayer p, boolean done) {
		p.setAttached(TUTORIAL_DONE, done);
	}

	/** 시험용. */
	public int tutorialStage() {
		return tutorial == null ? -1 : tutorial.stage;
	}

	public boolean inDuel() {
		return duel != null;
	}

	public boolean inBrawl() {
		return brawl != null;
	}

	public boolean inTeamMatch() {
		return team != null;
	}

	/** 시험용: 0 = 청팀, 1 = 홍팀, -1 = 팀전이 아님. */
	public int teamSide(ServerPlayer p) {
		return team == null ? -1 : team.team(p);
	}

	static void init() {}
}
