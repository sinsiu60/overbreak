package kr.overbreak.game;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.MenuPayload;
import kr.overbreak.net.ScorePayload;
import kr.overbreak.net.TeamDraftPayload;
import kr.overbreak.net.TeamPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * 팀 격전 — 2대2 · 3대3. 한 팀이 전멸하면 상대 팀이 1점, 먼저 {@link #WIN}점을 내면 승리 (제거전).
 *
 *   <b>편 짜기(DRAFT)</b>: 인원이 모이면 먼저 편 짜기 화면이 뜹니다. 플레이어가 직접 청팀 · 홍팀을 고르고,
 *     {@link #DRAFT_TIME} 안에 안 고른 사람은 빈자리에 자동으로 들어갑니다. 양쪽이 다 차면 바로 시작
 *   라운드 시작: 체력 · 쿨타임 · 진행 중인 스킬 · CC 초기화 (궁극기 게이지는 이어짐), 3초 카운트다운 (움직일 수 없음)
 *   쓰러지면 실제로 죽지 않고 그 라운드 동안 관전 — 한 팀이 모두 쓰러지면 상대 팀 1점, 2.5초 뒤 다음 라운드
 *   같은 팀끼리는 피해 · 군중 제어 · 총알 판정이 아예 없습니다 ({@link kr.overbreak.util.Targets#allies})
 *   테두리: 내 화면에서 우리 편은 파랑, 상대는 빨강 ({@link TeamPayload} — 보는 사람마다 다릅니다)
 *   경기 중 나가면 그 자리는 비고, 한 팀이 모두 나가면 상대 팀 승리
 */
public final class TeamMatch {
	/** 이기는 데 필요한 점수. */
	public static final int WIN = 3;
	/** 고를 수 있는 한 팀 인원. */
	public static final int[] SIZES = {2, 3};
	/** 편 짜기에 주는 시간 (1/20초 단위 = 15초). */
	public static final int DRAFT_TIME = 300;
	static final int COUNTDOWN = 60;
	static final int ROUND_END = 50;
	static final int END = 120;

	public enum Phase { DRAFT, COUNTDOWN, FIGHT, ROUND_END, END }

	/** 한 팀 인원 (2 또는 3). */
	private final int size;
	/** 0 = 청팀, 1 = 홍팀. */
	private final List<List<ServerPlayer>> teams = List.of(new ArrayList<>(), new ArrayList<>());
	/** 아직 편을 고르지 않은 사람. */
	private final List<ServerPlayer> waiting = new ArrayList<>();
	private final Map<ServerPlayer, String> classes = new IdentityHashMap<>();
	private final Map<ServerPlayer, Boolean> downed = new IdentityHashMap<>();
	private final int[] score = new int[2];
	Phase phase = Phase.DRAFT;
	int round;
	int t;
	boolean finished;
	private int winner = -1;

	public TeamMatch(List<ServerPlayer> queued, int size) {
		this.size = Math.max(2, Math.min(3, size));
		for (int i = 0; i < queued.size() && i < this.size * 2; i++) {
			ServerPlayer p = queued.get(i);
			waiting.add(p);
			classes.put(p, Session.of(p).queuedClass);
		}
	}

	public int size() {
		return size;
	}

	public int players() {
		return size * 2;
	}

	// ── 편 짜기 ─────────────────────────────────────────────

	/** 모두를 편 짜기 화면으로. */
	void beginDraft() {
		for (ServerPlayer p : everyone()) {
			Session s = Session.of(p);
			s.place = Session.Place.TEAM;
			s.team = this;
			Effects.cancelOwnedBy(p);
			Classes.clear(p);
			CrowdControl.clearAll(p);
			p.setGameMode(GameType.SPECTATOR);
			Places.MENU_VIEW.teleport(p);
			MenuPayload.send(p, MenuPayload.TEAM_DRAFT, true, size + "대" + size);
		}
		sendDraft();
	}

	/** 플레이어가 고른 편으로 옮깁니다. 자리가 없으면 false. */
	public boolean pickTeam(ServerPlayer p, int team) {
		if (phase != Phase.DRAFT || team < 0 || team > 1 || !classes.containsKey(p)) {
			return false;
		}
		if (teams.get(team).contains(p)) {
			return true;
		}
		if (teams.get(team).size() >= size) {
			Attachments.profile(p).msgT = 20;
			Hud.actionbar(p, Hud.text(name(team).getString() + " 자리가 찼습니다", ChatFormatting.GRAY));
			return false;
		}
		waiting.remove(p);
		teams.get(1 - team).remove(p);
		teams.get(team).add(p);
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7F, team == 0 ? 1.4F : 0.9F);
		sendDraft();
		return true;
	}

	/** 남은 사람을 빈자리에 채웁니다 (시간이 다 됐을 때). */
	private void fillTeams() {
		for (ServerPlayer p : List.copyOf(waiting)) {
			int team = teams.get(0).size() <= teams.get(1).size() ? 0 : 1;
			if (teams.get(team).size() >= size) {
				team = 1 - team;
			}
			if (teams.get(team).size() < size) {
				teams.get(team).add(p);
			}
			waiting.remove(p);
		}
	}

	private void sendDraft() {
		List<String> blue = names(teams.get(0));
		List<String> red = names(teams.get(1));
		List<String> idle = names(waiting);
		int left = Math.max(0, (DRAFT_TIME - t + 19) / 20);
		for (ServerPlayer p : everyone()) {
			TeamDraftPayload.send(p, new TeamDraftPayload(blue, red, idle, team(p), left, size));
		}
	}

	private static List<String> names(List<ServerPlayer> list) {
		List<String> out = new ArrayList<>(list.size());
		for (ServerPlayer p : list) {
			out.add(p.getName().getString());
		}
		return out;
	}

	// ── 시작 ────────────────────────────────────────────────

	public void begin() {
		fillTeams();
		for (ServerPlayer p : all()) {
			Session s = Session.of(p);
			s.place = Session.Place.TEAM;
			s.team = this;
			p.setGameMode(GameType.ADVENTURE);
			MenuPayload.send(p, MenuPayload.CLOSED, true, "");
			TeamDraftPayload.send(p, TeamDraftPayload.NONE);
			Opening.play(p);
			Hud.title(p, Hud.bold(size + "대" + size + " 팀 격전", ChatFormatting.GOLD),
					Component.empty().append(name(team(p))).append(Hud.text("  ·  먼저 " + WIN + "점", ChatFormatting.GRAY)), 10, 50, 10);
			p.sendSystemMessage(Component.empty().append(Hud.text("[팀 격전] ", ChatFormatting.GOLD))
					.append(Hud.text("우리 팀: ", ChatFormatting.GRAY)).append(roster(team(p))));
		}
		startRound();
	}

	// ── 편 ──────────────────────────────────────────────────

	/** 0 = 청팀, 1 = 홍팀, -1 = 아직 안 고름 · 이 경기 사람이 아님. */
	public int team(ServerPlayer p) {
		return teams.get(0).contains(p) ? 0 : teams.get(1).contains(p) ? 1 : -1;
	}

	public boolean sameTeam(ServerPlayer a, ServerPlayer b) {
		int ta = team(a);
		return ta >= 0 && ta == team(b);
	}

	private List<ServerPlayer> all() {
		List<ServerPlayer> list = new ArrayList<>(teams.get(0));
		list.addAll(teams.get(1));
		return list;
	}

	/** 편을 고른 사람 + 아직 안 고른 사람. */
	private List<ServerPlayer> everyone() {
		List<ServerPlayer> list = all();
		list.addAll(waiting);
		return list;
	}

	private static Component name(int team) {
		return team == 0 ? Hud.bold("청팀", ChatFormatting.BLUE) : Hud.bold("홍팀", ChatFormatting.RED);
	}

	private Component roster(int team) {
		MutableComponent out = Component.empty();
		List<ServerPlayer> list = teams.get(Math.max(0, team));
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				out.append(Hud.text(" · ", ChatFormatting.DARK_GRAY));
			}
			out.append(Hud.text(list.get(i).getName().getString(), ChatFormatting.WHITE));
		}
		return out;
	}

	public int score(int team) {
		return score[Math.max(0, Math.min(1, team))];
	}

	/** 매치 포인트 — 어느 팀이든 한 점만 더 내면 이김 (경기가 끝나면 아님). */
	boolean climax() {
		return phase != Phase.END && phase != Phase.DRAFT && (score[0] >= WIN - 1 || score[1] >= WIN - 1);
	}

	public Phase phase() {
		return phase;
	}

	/** 지금 싸울 수 있는가 (살아 있고 전투 단계). */
	public boolean fighting(ServerPlayer p) {
		return phase == Phase.FIGHT && team(p) >= 0 && !Boolean.TRUE.equals(downed.get(p));
	}

	boolean allowInput(ServerPlayer p) {
		return fighting(p);
	}

	boolean allowDamage(ServerPlayer p) {
		return fighting(p);
	}

	private int alive(int team) {
		int n = 0;
		for (ServerPlayer p : teams.get(team)) {
			if (!Boolean.TRUE.equals(downed.get(p)) && !p.hasDisconnected()) {
				n++;
			}
		}
		return n;
	}

	// ── 라운드 ──────────────────────────────────────────────

	private void startRound() {
		round++;
		downed.clear();
		for (int team = 0; team < 2; team++) {
			Places.Spot[] spots = team == 0 ? Places.TEAM_BLUE : Places.TEAM_RED;
			List<ServerPlayer> list = teams.get(team);
			for (int i = 0; i < list.size(); i++) {
				reset(list.get(i), spots[i % spots.length]);
			}
		}
		phase = Phase.COUNTDOWN;
		t = 0;
		sendTeams();
	}

	/** 라운드 시작 상태 — 직업을 다시 받되 궁극기 게이지는 이어 줌. */
	private void reset(ServerPlayer p, Places.Spot spot) {
		PlayerProfile prof = Attachments.profile(p);
		int raw = prof.ultRaw;
		Effects.cancelOwnedBy(p);
		Classes.announce = false;
		try {
			Classes.give(p, Classes.byId(classes.get(p)));
		} finally {
			Classes.announce = true;
		}
		prof.ultRaw = raw;
		UltGauge.recalc(prof);
		CrowdControl.clearAll(p);
		p.setGameMode(GameType.ADVENTURE);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		spot.teleport(p);
		Duel.freeze(p, true);
	}

	public void tick() {
		t++;
		switch (phase) {
			case DRAFT -> {
				boolean full = teams.get(0).size() >= size && teams.get(1).size() >= size;
				if (t % 20 == 0 || full) {
					sendDraft();
				}
				if (full || t >= DRAFT_TIME) {
					begin();
				}
			}
			case COUNTDOWN -> {
				if (t == 1 || t == 21 || t == 41) {
					int n = 3 - (t - 1) / 20;
					both(Hud.bold(String.valueOf(n), ChatFormatting.WHITE), roundText(), SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F);
				}
				if (t >= COUNTDOWN) {
					phase = Phase.FIGHT;
					t = 0;
					for (ServerPlayer p : all()) {
						Duel.freeze(p, false);
					}
					both(Hud.bold("싸워라!", ChatFormatting.RED), roundText(), SoundEvents.RAID_HORN.value(), 0.6F);
				}
			}
			case FIGHT -> {
				for (ServerPlayer p : all()) {
					if (!Boolean.TRUE.equals(downed.get(p)) && !Places.inside(Places.DUEL_ARENA, p)) {
						home(p);
					}
				}
			}
			case ROUND_END -> {
				if (t >= ROUND_END) {
					if (score[0] >= WIN || score[1] >= WIN) {
						end(score[0] >= WIN ? 0 : 1);
					} else {
						startRound();
					}
				}
			}
			case END -> {
				if (t >= END) {
					finish();
				}
			}
		}
		if (phase != Phase.DRAFT) {
			scoreboard();
		}
	}

	private void home(ServerPlayer p) {
		int team = team(p);
		Places.Spot[] spots = team == 1 ? Places.TEAM_RED : Places.TEAM_BLUE;
		int i = Math.max(0, teams.get(Math.max(0, team)).indexOf(p));
		spots[i % spots.length].teleport(p);
	}

	/** 쓰러짐 — 죽음은 막고 그 라운드 동안 관전. 한 팀이 모두 쓰러지면 상대 팀 1점. */
	public void onDown(ServerPlayer victim) {
		victim.setHealth(victim.getMaxHealth());
		int team = team(victim);
		if (phase != Phase.FIGHT || team < 0 || Boolean.TRUE.equals(downed.get(victim))) {
			return;
		}
		downed.put(victim, true);
		Effects.cancelOwnedBy(victim);
		CrowdControl.clearAll(victim);
		victim.setGameMode(GameType.SPECTATOR);
		Hud.title(victim, Hud.bold("쓰러졌다", ChatFormatting.RED),
				Hud.text("다음 라운드까지 관전", ChatFormatting.GRAY), 0, 40, 10);
		Fx.sound(victim, SoundEvents.WITHER_HURT, SoundSource.MASTER, 0.6F, 0.8F);
		int other = 1 - team;
		for (ServerPlayer p : all()) {
			if (p != victim) {
				PlayerProfile prof = Attachments.profile(p);
				prof.msgT = 30;
				Hud.actionbar(p, Component.empty()
						.append(Hud.text(victim.getName().getString(), ChatFormatting.WHITE))
						.append(Hud.text(" 쓰러짐   ", ChatFormatting.GRAY))
						.append(Hud.bold(alive(0) + " : " + alive(1), ChatFormatting.GOLD)));
			}
		}
		if (alive(team) <= 0) {
			roundWon(other);
		}
	}

	private void roundWon(int team) {
		score[team]++;
		phase = Phase.ROUND_END;
		t = 0;
		for (ServerPlayer p : all()) {
			Effects.cancelOwnedBy(p);
			CrowdControl.clearAll(p);
			Duel.freeze(p, true);
			boolean won = team(p) == team;
			Hud.title(p, won ? Hud.bold("라운드 획득", ChatFormatting.AQUA) : Hud.bold("라운드 패배", ChatFormatting.RED),
					roundText(), 0, 40, 10);
			Fx.sound(p, won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, won ? 1.4F : 0.8F);
		}
	}

	private Component roundText() {
		return Component.empty().append(Hud.text("라운드 " + round + "   ", ChatFormatting.GRAY))
				.append(Hud.bold(score[0] + " : " + score[1], ChatFormatting.WHITE));
	}

	private void both(Component title, Component sub, net.minecraft.sounds.SoundEvent sound, float pitch) {
		for (ServerPlayer p : all()) {
			Hud.title(p, title, sub, 0, 20, 4);
			Fx.sound(p, sound, SoundSource.MASTER, 1.0F, pitch);
		}
	}

	// ── 끝 ──────────────────────────────────────────────────

	private void end(int team) {
		phase = Phase.END;
		t = 0;
		winner = team;
		for (ServerPlayer p : all()) {
			Duel.freeze(p, true);
			boolean won = team(p) == team;
			Hud.title(p, won ? Hud.bold("승리", ChatFormatting.GOLD) : Hud.bold("패배", ChatFormatting.DARK_RED), roundText(), 5, 90, 20);
			Fx.sound(p, won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, won ? 1.0F : 0.7F);
		}
	}

	private void finish() {
		finished = true;
		for (ServerPlayer p : everyone()) {
			Duel.freeze(p, false);
			Session s = Session.of(p);
			if (s.team == this) {
				s.team = null;
				if (!p.hasDisconnected()) {
					Game.toMenu(p);
				}
			}
		}
		teams.get(0).clear();
		teams.get(1).clear();
		waiting.clear();
	}

	/** 기권 (나감 · 메인 화면) — 그 자리는 빕니다. 한 팀이 모두 빠지면 상대 팀 승리. */
	void forfeit(ServerPlayer leaver) {
		int team = team(leaver);
		Session.of(leaver).team = null;
		Duel.freeze(leaver, false);
		boolean known = classes.remove(leaver) != null;
		waiting.remove(leaver);
		TeamPayload.send(leaver, TeamPayload.NONE);
		TeamDraftPayload.send(leaver, TeamDraftPayload.NONE);
		ScoreBoard.clear(leaver);
		if (!known) {
			return;
		}
		if (team >= 0) {
			teams.get(team).remove(leaver);
		}
		downed.remove(leaver);
		Attachments.profile(leaver).msgT = 0;
		for (ServerPlayer p : everyone()) {
			p.sendSystemMessage(Component.empty().append(Hud.text("[팀 격전] ", ChatFormatting.GOLD))
					.append(Hud.text(leaver.getName().getString() + " 기권", ChatFormatting.GRAY)));
		}
		if (phase == Phase.DRAFT) {
			// 편 짜기 도중에 빠지면 남은 사람들만 그대로 이어 갑니다 (아무도 없으면 Game 이 정리)
			sendDraft();
			return;
		}
		sendTeams();
		if (phase == Phase.END) {
			return;
		}
		if (team >= 0 && teams.get(team).isEmpty()) {
			end(1 - team);
		} else if (phase == Phase.FIGHT && team >= 0 && alive(team) <= 0) {
			roundWon(1 - team);
		}
	}

	/** 남은 사람이 아무도 없는가 (Game 이 경기를 접을지 판단). */
	boolean empty() {
		return everyone().isEmpty();
	}

	// ── 화면에 보내는 것 ─────────────────────────────────────

	/** 테두리 색은 보는 사람마다 다릅니다 — 각자에게 "우리 편 · 상대 편" 목록을 보냅니다. */
	private void sendTeams() {
		for (int team = 0; team < 2; team++) {
			List<Integer> mine = ids(teams.get(team));
			List<Integer> theirs = ids(teams.get(1 - team));
			for (ServerPlayer p : teams.get(team)) {
				List<Integer> allies = new ArrayList<>(mine);
				allies.remove(Integer.valueOf(p.getId()));
				TeamPayload.send(p, new TeamPayload(allies, theirs));
			}
		}
	}

	private static List<Integer> ids(List<ServerPlayer> list) {
		List<Integer> out = new ArrayList<>(list.size());
		for (ServerPlayer p : list) {
			out.add(p.getId());
		}
		return out;
	}

	private void scoreboard() {
		String note = switch (phase) {
			case DRAFT -> "편 짜는 중";
			case COUNTDOWN -> "라운드 " + round + "  ·  곧 시작";
			case FIGHT, ROUND_END -> "라운드 " + round + "   ·   생존 " + alive(0) + " : " + alive(1);
			case END -> winner < 0 ? "경기 종료" : (winner == 0 ? "청팀" : "홍팀") + " 승리";
		};
		for (ServerPlayer p : all()) {
			if (p.hasDisconnected()) {
				continue;
			}
			int team = Math.max(0, team(p));
			List<String> names = List.of("우리 팀", "상대 팀");
			List<Integer> scores = List.of(score[team], score[1 - team]);
			if (ScorePayload.canSend(p)) {
				ScoreBoard.send(p, new ScorePayload(ScorePayload.TEAM, names, scores, 0, WIN,
						team == 0 ? note : flip(note)));
			} else if (phase == Phase.FIGHT && t % 10 == 0 && Attachments.profile(p).msgT <= 0) {
				Hud.actionbar(p, Component.empty().append(Hud.bold("우리 " + score[team], ChatFormatting.BLUE))
						.append(Hud.text("  :  ", ChatFormatting.DARK_GRAY))
						.append(Hud.bold(String.valueOf(score[1 - team]), ChatFormatting.RED))
						.append(Hud.text("   (먼저 " + WIN + "점)", ChatFormatting.GRAY)));
			}
		}
	}

	/** 홍팀 화면에서는 생존 수를 "우리 : 상대" 로 뒤집어 보여 줍니다. */
	private String flip(String note) {
		int at = note.indexOf("생존 ");
		if (at < 0) {
			return note;
		}
		return note.substring(0, at) + "생존 " + alive(1) + " : " + alive(0);
	}

	/** 시험용. */
	public int aliveCount(int team) {
		return alive(team);
	}

	/** 시험용. */
	public @Nullable ServerPlayer memberOf(int team, int index) {
		List<ServerPlayer> list = teams.get(Math.max(0, Math.min(1, team)));
		return index < list.size() ? list.get(index) : null;
	}
}
