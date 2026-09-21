package kr.overbreak.game;

import kr.overbreak.Overbreak;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * 1대1 매치 — 서버 맵의 전장에서 먼저 3라운드를 따면 승리.
 *
 *   라운드 시작: 체력 · 쿨타임 · 진행 중인 스킬 · CC 초기화 (궁극기 게이지는 이어짐), 양 끝에서 마주 보고 3초 카운트다운 (움직일 수 없음)
 *   한 명이 쓰러지면 (실제로 죽지 않음) 상대가 1점 · 2.5초 뒤 다음 라운드
 *   경기 중 나가거나 메인 화면으로 가면 기권 → 상대 승리. 전장 밖으로 떨어지면 자기 시작 자리로
 *   끝나면 6초 동안 승패 표시 후 둘 다 메인 화면으로
 */
public final class Duel {
	public static final int WIN = 3;
	static final int COUNTDOWN = 60;
	static final int ROUND_END = 50;
	static final int END = 120;
	private static final Identifier FREEZE = Overbreak.id("duel_freeze");

	public enum Phase { COUNTDOWN, FIGHT, ROUND_END, END }

	final ServerPlayer a;
	final ServerPlayer b;
	private final String classA;
	private final String classB;
	int scoreA;
	int scoreB;
	int round;
	Phase phase = Phase.COUNTDOWN;
	int t;
	boolean finished;

	Duel(ServerPlayer a, String classA, ServerPlayer b, String classB) {
		this.a = a;
		this.b = b;
		this.classA = classA;
		this.classB = classB;
	}

	void begin() {
		for (ServerPlayer p : new ServerPlayer[] {a, b}) {
			Session s = Session.of(p);
			s.place = Session.Place.DUEL;
			s.duel = this;
			p.setGameMode(GameType.ADVENTURE);
			MenuPayload.send(p, MenuPayload.CLOSED, true, "");
			Opening.play(p);
			ServerPlayer o = other(p);
			PvpClass oc = Classes.byId(o == a ? classA : classB);
			Hud.title(p, Hud.bold("1대1 매치", ChatFormatting.GOLD),
					Component.empty().append(Hud.text("상대 ", ChatFormatting.GRAY)).append(Hud.text(o.getName().getString(), ChatFormatting.WHITE))
							.append(Hud.text("  ·  ", ChatFormatting.DARK_GRAY)).append(oc == null ? Component.empty() : oc.displayName()), 10, 50, 10);
		}
		Classes.give(a, Classes.byId(classA));
		Classes.give(b, Classes.byId(classB));
		startRound();
	}

	public ServerPlayer other(ServerPlayer p) {
		return p == a ? b : a;
	}

	public int score(ServerPlayer p) {
		return p == a ? scoreA : scoreB;
	}

	/** 매치 포인트 — 어느 쪽이든 한 점만 더 내면 이김 (경기가 끝나면 아님). */
	boolean climax() {
		return phase != Phase.END && (scoreA >= WIN - 1 || scoreB >= WIN - 1);
	}

	public Phase phase() {
		return phase;
	}

	private void startRound() {
		round++;
		reset(a, classA, Places.DUEL_A);
		reset(b, classB, Places.DUEL_B);
		phase = Phase.COUNTDOWN;
		t = 0;
	}

	/** 라운드 시작 상태 — 직업을 다시 받되 궁극기 게이지는 이어 줌. */
	private void reset(ServerPlayer p, String cls, Places.Spot spot) {
		PlayerProfile prof = Attachments.profile(p);
		int raw = prof.ultRaw;
		Effects.cancelOwnedBy(p);
		Classes.announce = false;
		try {
			Classes.give(p, Classes.byId(cls));
		} finally {
			Classes.announce = true;
		}
		prof.ultRaw = raw;
		UltGauge.recalc(prof);
		CrowdControl.clearAll(p);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		spot.teleport(p);
		freeze(p, true);
	}

	static void freeze(ServerPlayer p, boolean on) {
		if (on) {
			CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			CrowdControl.mod(p, Attributes.JUMP_STRENGTH, FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, FREEZE);
			CrowdControl.unmod(p, Attributes.JUMP_STRENGTH, FREEZE);
		}
	}

	void tick() {
		t++;
		scoreboard();
		switch (phase) {
			case COUNTDOWN -> {
				if (t == 1 || t == 21 || t == 41) {
					int n = 3 - (t - 1) / 20;
					both(Hud.bold(String.valueOf(n), ChatFormatting.WHITE), roundText(), SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F);
				}
				if (t >= COUNTDOWN) {
					freeze(a, false);
					freeze(b, false);
					phase = Phase.FIGHT;
					t = 0;
					both(Hud.bold("싸워라!", ChatFormatting.RED), roundText(), SoundEvents.RAID_HORN.value(), 0.6F);
				}
			}
			case FIGHT -> {
				keepInside(a, Places.DUEL_A);
				keepInside(b, Places.DUEL_B);
			}
			case ROUND_END -> {
				if (t >= ROUND_END) {
					if (scoreA >= WIN || scoreB >= WIN) {
						end(scoreA >= WIN ? a : b);
					} else {
						startRound();
					}
				}
			}
			case END -> {
				if (t >= END) {
					finished = true;
					for (ServerPlayer p : new ServerPlayer[] {a, b}) {
						freeze(p, false);
						Session s = Session.of(p);
						if (s.duel == this) {
							s.duel = null;
							if (!p.hasDisconnected()) {
								Game.toMenu(p);
							}
						}
					}
				}
			}
		}
	}

	private Component roundText() {
		return Component.empty().append(Hud.text("라운드 " + round + "   ", ChatFormatting.GRAY))
				.append(Hud.bold(scoreA + " : " + scoreB, ChatFormatting.WHITE));
	}

	private void both(Component title, Component sub, net.minecraft.sounds.SoundEvent sound, float pitch) {
		for (ServerPlayer p : new ServerPlayer[] {a, b}) {
			Hud.title(p, title, sub, 0, 20, 4);
			Fx.sound(p, sound, SoundSource.MASTER, 1.0F, pitch);
		}
	}

	/** 화면 위쪽 점수판 (오버워치 방식) — 모드가 없는 클라이언트에게만 액션바로 대신 알려 줍니다. */
	private void scoreboard() {
		java.util.List<String> names = java.util.List.of(a.getName().getString(), b.getName().getString());
		java.util.List<Integer> scores = java.util.List.of(scoreA, scoreB);
		String note = phase == Phase.END ? "경기 종료" : "라운드 " + round;
		for (ServerPlayer p : new ServerPlayer[] {a, b}) {
			if (p.hasDisconnected()) {
				continue;
			}
			if (ScorePayload.canSend(p)) {
				ScoreBoard.send(p, new ScorePayload(ScorePayload.DUEL, names, scores, p == a ? 0 : 1, WIN, note));
			} else if (phase == Phase.FIGHT && t % 10 == 0) {
				scoreBar(p);
			}
		}
	}

	private void scoreBar(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (prof.msgT >= 5) {
			return;
		}
		Hud.actionbar(p, Component.empty().append(Hud.text("나 ", ChatFormatting.GRAY)).append(Hud.bold(String.valueOf(score(p)), ChatFormatting.AQUA))
				.append(Hud.text("  :  ", ChatFormatting.DARK_GRAY)).append(Hud.bold(String.valueOf(score(other(p))), ChatFormatting.RED))
				.append(Hud.text(" " + other(p).getName().getString() + "   (먼저 " + WIN + "점)", ChatFormatting.GRAY)));
	}

	private static void keepInside(ServerPlayer p, Places.Spot home) {
		if (!Places.inside(Places.DUEL_ARENA, p)) {
			home.teleport(p);
		}
	}

	boolean allowDamage() {
		return phase == Phase.FIGHT;
	}

	boolean allowInput() {
		return phase == Phase.FIGHT;
	}

	/** 쓰러짐 — 죽음은 막고 상대에게 점수. */
	void onDown(ServerPlayer victim) {
		victim.setHealth(victim.getMaxHealth());
		if (phase != Phase.FIGHT) {
			return;
		}
		ServerPlayer killer = other(victim);
		if (killer == a) {
			scoreA++;
		} else {
			scoreB++;
		}
		Effects.cancelOwnedBy(victim);
		Effects.cancelOwnedBy(killer);
		CrowdControl.clearAll(victim);
		freeze(a, true);
		freeze(b, true);
		phase = Phase.ROUND_END;
		t = 0;
		Hud.title(killer, Hud.bold("라운드 획득", ChatFormatting.AQUA), roundText(), 0, 40, 10);
		Hud.title(victim, Hud.bold("쓰러졌다", ChatFormatting.RED), roundText(), 0, 40, 10);
		Fx.sound(killer, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0F, 1.4F);
		Fx.sound(victim, SoundEvents.WITHER_HURT, SoundSource.MASTER, 0.6F, 0.8F);
	}

	/** 기권 (나감 · 메인 화면) — 상대 승리. */
	void forfeit(ServerPlayer leaver) {
		Session.of(leaver).duel = null;
		freeze(leaver, false);
		if (phase != Phase.END) {
			end(other(leaver));
		}
	}

	private void end(ServerPlayer winner) {
		phase = Phase.END;
		t = 0;
		ServerPlayer loser = other(winner);
		freeze(a, true);
		freeze(b, true);
		Hud.title(winner, Hud.bold("승리", ChatFormatting.GOLD), roundText(), 5, 90, 20);
		Hud.title(loser, Hud.bold("패배", ChatFormatting.DARK_RED), roundText(), 5, 90, 20);
		Fx.sound(winner, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
		Fx.sound(loser, SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, 0.7F);
	}
}
