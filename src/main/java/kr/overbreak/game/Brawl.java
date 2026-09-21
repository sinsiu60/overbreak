package kr.overbreak.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.MenuPayload;
import kr.overbreak.net.ScorePayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * 대난투 — 한 전장에 최대 5명, 사람 수 x {@link #KILLS_PER_PLAYER} 킬을 먼저 내면 승리.
 *
 *   목표 킬: 사람 한 명당 10킬 (3명 30킬 · 4명 40킬 · 5명 50킬). 중간에 들어오면 그만큼 올라갑니다
 *   모집: 3명이 모이면 10초 카운트다운이 돌고, 그 사이에 들어온 사람도 5명까지 함께 들어갑니다
 *   중도 난입: 경기 중에 골라도 자리가 남아 있으면 바로 들어갑니다. 꽉 차 있으면 관전
 *   쓰러지면 실제로 죽지 않고 3초 뒤 빈 시작 자리에서 다시 일어납니다. 쓰러뜨린 사람이 1킬
 *     쓰러져 있는 동안 규격 선택 창이 떠서 <b>다음 판에 쓸 규격을 바꿀 수 있습니다</b>
 *   전장 밖으로 나가면 가까운 시작 자리로 되돌립니다
 *   끝나면 6초 동안 결과를 보여 준 뒤 모두 메인 화면으로
 */
public final class Brawl {
	/** 사람 한 명당 목표 킬 — 3명이면 30킬, 5명이면 50킬. */
	public static final int KILLS_PER_PLAYER = 10;
	public static final int MIN_PLAYERS = 3;
	public static final int MAX_PLAYERS = 5;
	/** 모인 뒤 시작까지 (1/20초 단위 = 10초). */
	static final int LOBBY_TIME = 200;
	/** 쓰러진 뒤 다시 일어나기까지 (1/20초 단위 = 3초) — 그동안 규격을 바꿀 수 있습니다. */
	static final int RESPAWN = 60;
	static final int END = 120;

	public enum Phase { LOBBY, FIGHT, END }

	private final List<ServerPlayer> players = new ArrayList<>();
	private final List<ServerPlayer> watchers = new ArrayList<>();
	private final Map<ServerPlayer, Integer> kills = new IdentityHashMap<>();
	private final Map<ServerPlayer, String> classes = new IdentityHashMap<>();
	private final Map<ServerPlayer, Integer> down = new IdentityHashMap<>();
	Phase phase = Phase.LOBBY;
	int t;
	boolean finished;
	/** 이기는 데 필요한 킬 — 사람이 늘면 함께 올라갑니다 (줄어도 내려가지 않음). */
	private int goal = MIN_PLAYERS * KILLS_PER_PLAYER;
	private @Nullable ServerPlayer winner;

	Brawl() {}

	// ── 들어오기 · 나가기 ─────────────────────────────────────

	/** 참가 — 모집 중이든 경기 중이든 자리가 남아 있으면 들어갑니다 (중도 난입). 꽉 찼으면 false. */
	boolean join(ServerPlayer p, String classId) {
		if (phase == Phase.END || players.size() >= MAX_PLAYERS || players.contains(p)) {
			return false;
		}
		boolean running = phase == Phase.FIGHT;
		players.add(p);
		kills.put(p, 0);
		classes.put(p, classId);
		goal = Math.max(goal, players.size() * KILLS_PER_PLAYER);
		Session s = Session.of(p);
		s.place = Session.Place.BRAWL;
		s.brawl = this;
		s.spectating = false;
		p.setGameMode(GameType.ADVENTURE);
		MenuPayload.send(p, MenuPayload.CLOSED, true, "");
		(running ? spawnFor(p) : Places.BRAWL_SPAWNS[(players.size() - 1) % Places.BRAWL_SPAWNS.length]).teleport(p);
		give(p);
		if (!running) {
			Duel.freeze(p, true);
		}
		Hud.title(p, Hud.bold("대난투", ChatFormatting.GOLD),
				Hud.text("먼저 " + goal + "킬  ·  " + (running ? "지금 바로 싸웁니다" : "곧 시작합니다"), ChatFormatting.GRAY), 5, 40, 10);
		Opening.play(p);
		announce(Component.empty().append(Hud.bold(p.getName().getString(), ChatFormatting.WHITE))
				.append(Hud.text((running ? " 난입" : " 참가") + "  (" + players.size() + "/" + MAX_PLAYERS + ")  ·  목표 "
						+ goal + "킬", ChatFormatting.GRAY)));
		return true;
	}

	/** 이기는 데 필요한 킬. */
	public int goal() {
		return goal;
	}

	/** 경기 중에 고른 사람 — 관전으로 들어옵니다. */
	void watch(ServerPlayer p) {
		if (!watchers.contains(p)) {
			watchers.add(p);
		}
		Session s = Session.of(p);
		s.place = Session.Place.BRAWL;
		s.brawl = this;
		s.spectating = true;
		Classes.clear(p);
		p.setGameMode(GameType.SPECTATOR);
		Places.MENU_VIEW.teleport(p);
		MenuPayload.send(p, MenuPayload.CLOSED, true, "");
		Hud.title(p, Hud.bold("대난투 관전", ChatFormatting.AQUA),
				Hud.text("자리가 가득 찼습니다  ·  ESC 메뉴 → 메인 화면으로", ChatFormatting.GRAY), 5, 60, 10);
	}

	/** 나감 (접속 종료 · 메인 화면 · 기권). */
	void leave(ServerPlayer p) {
		Session s = Session.of(p);
		if (s.brawl == this) {
			s.brawl = null;
			s.spectating = false;
		}
		watchers.remove(p);
		if (players.remove(p)) {
			MenuPayload.send(p, MenuPayload.CLOSED, true, "");
			kills.remove(p);
			classes.remove(p);
			down.remove(p);
			Duel.freeze(p, false);
			announce(Component.empty().append(Hud.bold(p.getName().getString(), ChatFormatting.WHITE))
					.append(Hud.text(" 퇴장", ChatFormatting.GRAY)));
			if (phase == Phase.FIGHT && players.size() <= 1) {
				end(players.isEmpty() ? null : players.getFirst());
			}
		}
		ScoreBoard.clear(p);
	}

	public int size() {
		return players.size();
	}

	/** 막판 — 누군가 이기기까지 {@link MatchMusic#BRAWL_LEFT} 킬 이하로 남음 (경기가 끝나면 아님). */
	boolean climax() {
		if (phase != Phase.FIGHT) {
			return false;
		}
		int best = 0;
		for (int k : kills.values()) {
			best = Math.max(best, k);
		}
		return goal - best <= MatchMusic.BRAWL_LEFT;
	}

	public Phase phase() {
		return phase;
	}

	public int kills(ServerPlayer p) {
		return kills.getOrDefault(p, 0);
	}

	/** 같은 경기에 있는 사람끼리만 서로 때립니다. */
	boolean fighting(ServerPlayer p) {
		return phase == Phase.FIGHT && players.contains(p) && !down.containsKey(p);
	}

	boolean allowInput(ServerPlayer p) {
		return fighting(p);
	}

	// ── 매 틱 (1/20초) ───────────────────────────────────────

	void tick() {
		players.removeIf(p -> {
			if (!p.hasDisconnected()) {
				return false;
			}
			kills.remove(p);
			classes.remove(p);
			down.remove(p);
			return true;
		});
		watchers.removeIf(ServerPlayer::hasDisconnected);
		t++;
		switch (phase) {
			case LOBBY -> {
				int left = (LOBBY_TIME - t + 19) / 20;
				// 사람이 모자라면 카운트다운을 멈추고 t 를 0 으로 되돌립니다 — 그동안은 숫자를 띄우지 않습니다
				// 마지막 5초만 숫자를 띄웁니다 (10초 내내 세면 시끄럽습니다)
				if (players.size() >= MIN_PLAYERS && t % 20 == 1 && left > 0 && left <= 5) {
					for (ServerPlayer p : players) {
						Hud.title(p, Hud.bold(String.valueOf(left), ChatFormatting.WHITE),
								Hud.text(players.size() + "명  ·  먼저 " + goal + "킬", ChatFormatting.GRAY), 0, 20, 4);
						Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1.0F, 1.0F);
					}
				}
				if (players.size() < MIN_PLAYERS) {
					// 기다리는 동안 인원이 빠지면 모집으로 되돌아갑니다
					t = 0;
				} else if (t >= LOBBY_TIME) {
					start();
				}
			}
			case FIGHT -> {
				for (ServerPlayer p : List.copyOf(players)) {
					Integer wait = down.get(p);
					if (wait != null) {
						if (wait <= 1) {
							down.remove(p);
							respawn(p);
						} else {
							down.put(p, wait - 1);
						}
						continue;
					}
					if (!Places.inside(Places.DUEL_ARENA, p)) {
						spawnFor(p).teleport(p);
					}
				}
			}
			case END -> {
				if (t >= END) {
					finish();
				}
			}
		}
		scoreboard();
	}

	private void start() {
		phase = Phase.FIGHT;
		t = 0;
		for (ServerPlayer p : players) {
			Duel.freeze(p, false);
			Hud.title(p, Hud.bold("싸워라!", ChatFormatting.RED), Hud.text("먼저 " + goal + "킬", ChatFormatting.GRAY), 0, 20, 4);
			Fx.sound(p, SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 1.0F, 0.6F);
		}
	}

	/** 쓰러짐 — 죽음은 막고 쓰러뜨린 사람에게 1킬. */
	void onDown(ServerPlayer victim, @Nullable ServerPlayer killer) {
		victim.setHealth(victim.getMaxHealth());
		if (phase != Phase.FIGHT || !players.contains(victim) || down.containsKey(victim)) {
			return;
		}
		Effects.cancelOwnedBy(victim);
		CrowdControl.clearAll(victim);
		down.put(victim, RESPAWN);
		victim.setGameMode(GameType.SPECTATOR);
		Hud.title(victim, Hud.bold("쓰러졌다", ChatFormatting.RED),
				Hud.text("3초 뒤 다시 시작  ·  규격을 바꿀 수 있습니다", ChatFormatting.GRAY), 0, 50, 8);
		Fx.sound(victim, SoundEvents.WITHER_HURT, SoundSource.MASTER, 0.6F, 0.8F);
		// 쓰러져 있는 동안 규격 선택 창 — 고르면 다시 일어날 때 그 규격으로
		MenuPayload.send(victim, MenuPayload.RESPAWN, true, classes.getOrDefault(victim, ""));
		if (killer != null && killer != victim && players.contains(killer)) {
			int n = kills.merge(killer, 1, Integer::sum);
			Fx.sound(killer, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0F, 1.6F);
			PlayerProfile prof = Attachments.profile(killer);
			prof.msgT = 30;
			Hud.actionbar(killer, Component.empty().append(Hud.bold("처치!  ", ChatFormatting.GOLD))
					.append(Hud.text(victim.getName().getString(), ChatFormatting.WHITE))
					.append(Hud.text("   " + n + " / " + goal, ChatFormatting.GRAY)));
			if (n >= goal) {
				end(killer);
			}
		}
	}

	private void respawn(ServerPlayer p) {
		if (p.hasDisconnected()) {
			return;
		}
		MenuPayload.send(p, MenuPayload.CLOSED, true, "");
		p.setGameMode(GameType.ADVENTURE);
		spawnFor(p).teleport(p);
		give(p);
		PvpClass c = Classes.byId(classes.get(p));
		Hud.title(p, Component.empty(), Component.empty().append(Hud.text("다시 싸웁니다   ", ChatFormatting.GRAY))
				.append(c == null ? Component.empty() : c.displayName()), 0, 15, 5);
	}

	/**
	 * 쓰러져 있는 동안 고른 규격 — 다시 일어날 때부터 적용됩니다.
	 * @return 바꿨으면 true
	 */
	boolean pickClass(ServerPlayer p, String classId) {
		if (!players.contains(p) || Classes.byId(classId) == null) {
			return false;
		}
		classes.put(p, classId);
		if (!down.containsKey(p)) {
			// 서 있는 동안에는 다음에 쓰러졌다 일어날 때 적용됩니다
			return true;
		}
		PvpClass c = Classes.byId(classId);
		Attachments.profile(p).msgT = 30;
		Hud.actionbar(p, Component.empty().append(Hud.text("다음 규격  ", ChatFormatting.GRAY))
				.append(c == null ? Component.empty() : c.displayName()));
		return true;
	}

	/** 다른 사람에게서 가장 먼 시작 자리. */
	private Places.Spot spawnFor(ServerPlayer p) {
		Places.Spot best = Places.BRAWL_SPAWNS[0];
		double bestScore = -1.0;
		for (Places.Spot spot : Places.BRAWL_SPAWNS) {
			double nearest = Double.MAX_VALUE;
			for (ServerPlayer o : players) {
				if (o != p && !down.containsKey(o)) {
					nearest = Math.min(nearest, o.position().distanceToSqr(spot.pos()));
				}
			}
			if (nearest > bestScore) {
				bestScore = nearest;
				best = spot;
			}
		}
		return best;
	}

	/** 직업을 새로 받되 궁극기 게이지는 이어 줍니다. */
	private void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		int raw = prof.ultRaw;
		Effects.cancelOwnedBy(p);
		PvpClass c = Classes.byId(classes.get(p));
		Classes.announce = false;
		try {
			Classes.give(p, c);
		} finally {
			Classes.announce = true;
		}
		prof.ultRaw = raw;
		UltGauge.recalc(prof);
		CrowdControl.clearAll(p);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
	}

	private void end(@Nullable ServerPlayer champion) {
		phase = Phase.END;
		t = 0;
		winner = champion;
		for (ServerPlayer p : players) {
			Duel.freeze(p, true);
			boolean won = p == champion;
			Hud.title(p, won ? Hud.bold("승리", ChatFormatting.GOLD) : Hud.bold("패배", ChatFormatting.DARK_RED),
					Hud.text(champion == null ? "경기 종료" : champion.getName().getString() + " 승리", ChatFormatting.GRAY), 5, 90, 20);
			Fx.sound(p, won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, won ? 1.0F : 0.7F);
		}
		for (ServerPlayer p : watchers) {
			Hud.title(p, Hud.bold("경기 종료", ChatFormatting.GOLD),
					Hud.text(champion == null ? "" : champion.getName().getString() + " 승리", ChatFormatting.GRAY), 5, 90, 20);
		}
	}

	private void finish() {
		finished = true;
		for (ServerPlayer p : List.copyOf(players)) {
			Duel.freeze(p, false);
			Session s = Session.of(p);
			s.brawl = null;
			if (!p.hasDisconnected()) {
				Game.toMenu(p);
			}
		}
		for (ServerPlayer p : List.copyOf(watchers)) {
			Session.of(p).brawl = null;
			Session.of(p).spectating = false;
			if (!p.hasDisconnected()) {
				Game.toMenu(p);
			}
		}
		players.clear();
		watchers.clear();
	}

	/** 기권 · 접속 종료로 사람이 빠질 때 부릅니다. */
	void forfeit(ServerPlayer p) {
		leave(p);
	}

	// ── 점수판 · 안내 ────────────────────────────────────────

	private void scoreboard() {
		List<ServerPlayer> order = new ArrayList<>(players);
		order.sort(Comparator.comparingInt((ServerPlayer p) -> kills.getOrDefault(p, 0)).reversed()
				.thenComparing(p -> p.getName().getString()));
		List<String> names = new ArrayList<>(order.size());
		List<Integer> scores = new ArrayList<>(order.size());
		for (ServerPlayer p : order) {
			names.add(p.getName().getString());
			scores.add(kills.getOrDefault(p, 0));
		}
		String note = switch (phase) {
			case LOBBY -> players.size() < MIN_PLAYERS
					? MIN_PLAYERS + "명이 모이면 시작합니다  (" + players.size() + "/" + MAX_PLAYERS + ")"
					: (LOBBY_TIME - t + 19) / 20 + "초 뒤 시작";
			case FIGHT -> players.size() < MAX_PLAYERS ? "난입 가능  (" + players.size() + "/" + MAX_PLAYERS + ")" : "";
			case END -> winner == null ? "경기 종료" : winner.getName().getString() + " 승리";
		};
		List<ServerPlayer> all = new ArrayList<>(order);
		all.addAll(watchers);
		for (ServerPlayer p : all) {
			if (p.hasDisconnected()) {
				continue;
			}
			if (ScorePayload.canSend(p)) {
				ScoreBoard.send(p, new ScorePayload(ScorePayload.BRAWL, names, scores, order.indexOf(p), goal, note));
			} else if (phase == Phase.FIGHT && t % 20 == 0 && Attachments.profile(p).msgT <= 0) {
				Hud.actionbar(p, Component.empty().append(Hud.bold("대난투  ", ChatFormatting.GOLD))
						.append(Hud.text(kills.getOrDefault(p, 0) + " / " + goal + " 킬", ChatFormatting.WHITE)));
			}
		}
	}

	private void announce(Component line) {
		for (ServerPlayer p : players) {
			p.sendSystemMessage(Component.empty().append(Hud.text("[대난투] ", ChatFormatting.GOLD)).append(line));
		}
	}
}
