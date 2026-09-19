package kr.overbreak.game;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.input.InputRouter;
import kr.overbreak.net.MenuActionPayload;
import kr.overbreak.net.MenuPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Hud;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * 게임 흐름 — 접속하면 메인 화면, 거기서 튜토리얼 · 훈련장 · 1대1 매치로.
 *
 *   메인 화면 (MENU)   관전 모드로 전장 위에 떠 있고, 모드 클라이언트는 메인 화면 창을 띄움 (net/MenuPayload)
 *                      모드가 없는 클라이언트는 로비에 서서 채팅 버튼(/lobby …)으로 고름
 *   플레이             튜토리얼을 마치지 않았으면 튜토리얼부터. 마쳤으면 규격(직업) 선택 → 게임 종류 선택 → 매칭 대기
 *   훈련장             규격 선택 → 훈련장 방 (더미). 언제든 메인 화면으로
 *   1대1 매치 (QUEUE → DUEL)  두 명이 모이면 전장에서 3선승제
 *
 * 접속을 거친 플레이어(Session.managed)에게만 제한이 걸립니다 — 시험용 가짜 플레이어는 예전처럼 자유롭습니다.
 */
public final class Game {
	/**
	 * 접속하면 메인 화면으로 보내는가. 시험 실행(build.gradle 의 -Doverbreak.lobby=false)에서는 꺼 두어 직업 시험이 메뉴에 갇히지 않게 합니다.
	 * 메인 화면 시험은 켜고 {@link #join} 을 직접 부릅니다.
	 */
	public static boolean enabled = !"false".equals(System.getProperty("overbreak.lobby"));

	private static final List<ServerPlayer> QUEUE = new ArrayList<>();
	/** 팀 격전 대기열 — [0] 2대2 (4명) · [1] 3대3 (6명). 인원이 차면 편 짜기부터 시작합니다. */
	private static final List<List<ServerPlayer>> TEAM_QUEUES = List.of(new ArrayList<>(), new ArrayList<>());
	private static @Nullable Duel match;
	/** 지금 돌고 있는 대난투 (모집 중일 수도 있음). */
	private static @Nullable Brawl brawl;
	private static @Nullable TeamMatch teamMatch;

	private Game() {}

	public static void init() {
		Session.init();
		Tutorial.init();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			if (enabled) {
				join(handler.player);
			}
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> leave(handler.player)));
		ServerTickEvents.END_SERVER_TICK.register(Game::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Game::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(Game::allowDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			Training.onDamaged(target);
			Tutorial.onDamaged(target, source);
			if (target instanceof ServerPlayer hit) {
				PvpClass c = kr.overbreak.core.Attachments.profile(hit).pvpClass;
				if (c != null) {
					c.onDamaged(hit, taken);
				}
			}
		});
		InputRouter.gate = Game::allowInput;
		// 팀전에서는 아군이 스킬 · 총알 판정에 아예 잡히지 않습니다
		kr.overbreak.util.Targets.allies = Game::sameTeam;
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> {
			dispatcher.register(Commands.literal("lobby")
					.executes(c -> run(c.getSource(), p -> onAction(p, MenuActionPayload.LOBBY, "", "")))
					.then(Commands.literal("tutorial").executes(c -> run(c.getSource(), p -> onAction(p, MenuActionPayload.TUTORIAL, "", ""))))
					.then(Commands.literal("cancel").executes(c -> run(c.getSource(), p -> onAction(p, MenuActionPayload.CANCEL, "", ""))))
					.then(Commands.literal("training").then(classArg().executes(c ->
							run(c.getSource(), p -> onAction(p, MenuActionPayload.TRAINING, StringArgumentType.getString(c, "class"), "")))))
					.then(Commands.literal("duel").then(classArg().executes(c ->
							run(c.getSource(), p -> onAction(p, MenuActionPayload.QUEUE, StringArgumentType.getString(c, "class"), MenuActionPayload.MODE_DUEL)))))
					.then(Commands.literal("brawl").then(classArg().executes(c ->
							run(c.getSource(), p -> onAction(p, MenuActionPayload.QUEUE, StringArgumentType.getString(c, "class"), MenuActionPayload.MODE_BRAWL)))))
					.then(Commands.literal("team").then(classArg().executes(c ->
							run(c.getSource(), p -> onAction(p, MenuActionPayload.QUEUE, StringArgumentType.getString(c, "class"), MenuActionPayload.MODE_TEAM3)))))
					.then(Commands.literal("team2").then(classArg().executes(c ->
							run(c.getSource(), p -> onAction(p, MenuActionPayload.QUEUE, StringArgumentType.getString(c, "class"), MenuActionPayload.MODE_TEAM2))))));
			dispatcher.register(Commands.literal("ob").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.literal("tutorial")
							.then(Commands.literal("skip").executes(c -> run(c.getSource(), p -> {
								Tutorial.skip(p);
								toMenu(p);
							})))
							.then(Commands.literal("reset").executes(c -> run(c.getSource(), p -> {
								Session.setTutorialDone(p, false);
								toMenu(p);
							}))))
					.then(Commands.literal("free").executes(c -> run(c.getSource(), Game::toFree))));
		});
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> classArg() {
		return Commands.argument("class", StringArgumentType.word()).suggests((c, b) -> {
			Classes.all().forEach(pc -> b.suggest(pc.id()));
			return b.buildFuture();
		});
	}

	private static int run(CommandSourceStack src, java.util.function.Consumer<ServerPlayer> action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		action.accept(src.getPlayerOrException());
		return 1;
	}

	// ── 접속 · 메인 화면 ─────────────────────────────────────

	/** 접속 — 무엇을 하던 중이었든 메인 화면에서 시작. */
	public static void join(ServerPlayer p) {
		Session s = Session.of(p);
		s.managed = true;
		s.joinT = 0;
		toMenu(p);
	}

	/** 지금 하던 것을 정리하고 메인 화면으로. */
	public static void toMenu(ServerPlayer p) {
		leave(p);
		Session s = Session.of(p);
		s.managed = true;
		s.place = Session.Place.MENU;
		s.pendingRespawn = false;
		s.menuSent = false;
		s.helpSent = false;
		Effects.cancelOwnedBy(p);
		Classes.clear(p);
		CrowdControl.clearAll(p);
		Duel.freeze(p, false);
		ScoreBoard.clear(p);
		kr.overbreak.net.TeamPayload.send(p, kr.overbreak.net.TeamPayload.NONE);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		// 훈련장 · 경기에서 띄운 화면 가운데 제목이 메인 화면 위에 남지 않게
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
		if (MenuPayload.canSend(p)) {
			p.setGameMode(GameType.SPECTATOR);
			Places.MENU_VIEW.teleport(p);
			sendMenu(p);
		} else {
			p.setGameMode(GameType.ADVENTURE);
			Places.LOBBY.teleport(p);
		}
	}

	private static void sendMenu(ServerPlayer p) {
		Session s = Session.of(p);
		if (!MenuPayload.canSend(p)) {
			return;
		}
		s.menuSent = true;
		int screen = s.place == Session.Place.QUEUE ? MenuPayload.QUEUE : MenuPayload.MAIN;
		// "이름|아래에 붙는 글" — 대기 화면이 세로줄에서 잘라 씁니다
		int queuedSize = teamSize(s.queuedMode);
		String detail = s.place != Session.Place.QUEUE ? ""
				: queuedSize > 0
						? queuedSize + "대" + queuedSize + " 팀 격전|" + TEAM_QUEUES.get(queuedSize - 2).size()
								+ " / " + (queuedSize * 2) + "명 모임"
						: "1대1 매치";
		MenuPayload.send(p, screen, Session.tutorialDone(p), detail);
	}

	/** 하던 것에서 빠짐 (대기 취소 · 튜토리얼 중단 · 경기 기권). 장소는 바꾸지 않습니다. */
	static void leave(ServerPlayer p) {
		Session s = Session.of(p);
		QUEUE.remove(p);
		TEAM_QUEUES.forEach(q -> q.remove(p));
		if (s.tutorial != null) {
			Tutorial.stop(p);
		}
		if (s.duel != null) {
			s.duel.forfeit(p);
		}
		if (s.brawl != null) {
			s.brawl.forfeit(p);
		}
		if (s.team != null) {
			s.team.forfeit(p);
		}
	}

	/**
	 * 관리자 자유 이동 — 전장 · 훈련장의 제한을 벗고 맵을 마음대로 돌아다닙니다 (크리에이티브).
	 * 게임마스터 권한이 있어야 하고, ESC 메뉴의 「메인 화면으로」 로 돌아옵니다.
	 */
	public static void toFree(ServerPlayer p) {
		if (!MenuPayload.admin(p)) {
			p.sendSystemMessage(Hud.text("[OVERBREAK] 관리자 모드는 게임마스터 권한이 필요합니다.", ChatFormatting.RED));
			return;
		}
		leave(p);
		Session s = Session.of(p);
		s.managed = true;
		s.place = Session.Place.FREE;
		s.pendingRespawn = false;
		s.menuSent = false;
		Effects.cancelOwnedBy(p);
		Classes.clear(p);
		CrowdControl.clearAll(p);
		Duel.freeze(p, false);
		ScoreBoard.clear(p);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
		MenuPayload.send(p, MenuPayload.CLOSED, Session.tutorialDone(p), "");
		p.setGameMode(GameType.CREATIVE);
		Hud.title(p, Hud.bold("관리자 모드", ChatFormatting.LIGHT_PURPLE),
				Hud.text("맵을 자유롭게 돌아다닙니다  ·  ESC 메뉴 → 메인 화면으로", ChatFormatting.GRAY), 5, 60, 10);
		p.sendSystemMessage(Component.empty().append(Hud.text("[OVERBREAK] ", ChatFormatting.GOLD))
				.append(Hud.text("관리자 모드 — /lobby 로 메인 화면으로 돌아갑니다.", ChatFormatting.GRAY)));
	}

	// ── 메인 화면에서 누른 것 ─────────────────────────────────

	public static void onAction(ServerPlayer p, String action, String arg, String mode) {
		Session s = Session.of(p);
		s.managed = true;
		switch (action) {
			case MenuActionPayload.LOBBY -> toMenu(p);
			case MenuActionPayload.CANCEL -> {
				if (s.place == Session.Place.QUEUE) {
					QUEUE.remove(p);
					TEAM_QUEUES.forEach(q -> q.remove(p));
					s.place = Session.Place.MENU;
					sendMenu(p);
				}
			}
			case MenuActionPayload.TUTORIAL -> {
				if (s.place == Session.Place.MENU || s.place == Session.Place.TRAINING) {
					startTutorial(p);
				}
			}
			case MenuActionPayload.TRAINING -> {
				PvpClass c = Classes.byId(arg);
				if (c != null && s.place != Session.Place.DUEL) {
					startTraining(p, c);
				}
			}
			case MenuActionPayload.FREE -> toFree(p);
			// 편 짜기 화면에서 고른 편 (0 = 청 · 1 = 홍)
			case MenuActionPayload.TEAM_PICK -> {
				if (s.team != null) {
					s.team.pickTeam(p, "1".equals(arg) ? 1 : 0);
				}
			}
			// 쓰러져 있는 동안 다음 판에 쓸 규격을 고름 (대난투)
			case MenuActionPayload.PICK -> {
				PvpClass picked = Classes.byId(arg);
				if (picked != null && s.brawl != null && s.brawl.pickClass(p, picked.id())) {
					MenuPayload.send(p, MenuPayload.CLOSED, Session.tutorialDone(p), "");
				}
			}
			case MenuActionPayload.QUEUE -> {
				PvpClass c = Classes.byId(arg);
				if (c == null || s.place == Session.Place.DUEL || s.place == Session.Place.BRAWL) {
					return;
				}
				if (!Session.tutorialDone(p)) {
					startTutorial(p);
					return;
				}
				if (MenuActionPayload.MODE_BRAWL.equals(mode)) {
					startBrawl(p, c);
					return;
				}
				int teamSize = teamSize(mode);
				if (teamSize == 0 && !MenuActionPayload.MODE_DUEL.equals(mode)) {
					return;
				}
				leave(p);
				toMenuQuiet(p);
				s.place = Session.Place.QUEUE;
				s.queuedClass = c.id();
				s.queuedMode = teamSize == 0 ? mode : teamMode(teamSize);
				(teamSize == 0 ? QUEUE : TEAM_QUEUES.get(teamSize - 2)).add(p);
				sendMenu(p);
				if (!MenuPayload.canSend(p)) {
					p.sendSystemMessage(Hud.text("[OVERBREAK] " + (teamSize == 0 ? "1대1 매치" : teamSize + "대" + teamSize + " 팀 격전")
							+ " 대기 중... (/lobby cancel 로 취소)", ChatFormatting.GRAY));
				}
			}
			default -> {
			}
		}
	}

	/** 메인 화면 자리로만 옮김 (화면은 보내지 않음). */
	private static void toMenuQuiet(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		Classes.clear(p);
		CrowdControl.clearAll(p);
		if (MenuPayload.canSend(p)) {
			p.setGameMode(GameType.SPECTATOR);
			Places.MENU_VIEW.teleport(p);
		} else {
			p.setGameMode(GameType.ADVENTURE);
			Places.LOBBY.teleport(p);
		}
	}

	public static void startTutorial(ServerPlayer p) {
		leave(p);
		MenuPayload.send(p, MenuPayload.CLOSED, Session.tutorialDone(p), "");
		Tutorial.start(p);
	}

	/** 대난투 — 모집 중이면 참가, 이미 시작했으면 관전으로 들어갑니다. */
	public static void startBrawl(ServerPlayer p, PvpClass c) {
		leave(p);
		toMenuQuiet(p);
		if (brawl == null || brawl.finished) {
			brawl = new Brawl();
		}
		if (!brawl.join(p, c.id())) {
			brawl.watch(p);
		}
	}

	public static void startTraining(ServerPlayer p, PvpClass c) {
		leave(p);
		Session s = Session.of(p);
		s.place = Session.Place.TRAINING;
		s.pendingRespawn = false;
		p.setGameMode(GameType.ADVENTURE);
		// 규격은 메인 화면에서 골랐으니 채팅 알림은 생략
		Classes.announce = false;
		try {
			Classes.give(p, c);
		} finally {
			Classes.announce = true;
		}
		Places.TRAINING_SPAWN.teleport(p);
		MenuPayload.send(p, MenuPayload.CLOSED, Session.tutorialDone(p), "");
		Training.ensure(p.level().getServer().overworld());
		Hud.title(p, Hud.bold("훈련장", ChatFormatting.GREEN), Hud.text("ESC 메뉴 → 메인 화면으로", ChatFormatting.GRAY), 5, 40, 10);
	}

	// ── 매 틱 ───────────────────────────────────────────────

	private static void tick(MinecraftServer server) {
		// 게임 흐름(카운트다운 · 대사 · 메뉴 대기)은 1/20초 단위 숫자라 시간 단위 경계에서만 돌림 — 틱레이트와 무관하게 같은 시간
		if (!Ticks.ambient()) {
			return;
		}
		ServerLevel level = server.overworld();
		boolean training = false;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Session s = Session.of(p);
			if (!s.managed) {
				continue;
			}
			s.joinT++;
			switch (s.place) {
				case MENU, QUEUE -> {
					// 접속 직후에는 채널 등록 전이라 못 보냈을 수 있음 — 등록되면 그때 보냄
					if (!s.menuSent && MenuPayload.canSend(p)) {
						p.setGameMode(GameType.SPECTATOR);
						Places.MENU_VIEW.teleport(p);
						sendMenu(p);
					}
					if (!s.menuSent && !s.helpSent && s.joinT > 40) {
						s.helpSent = true;
						help(p);
					}
				}
				case TUTORIAL -> {
					if (s.pendingRespawn) {
						s.pendingRespawn = false;
						revive(p);
						Tutorial.respawn(p);
					}
					Tutorial.tick(p, s);
				}
				case TRAINING -> {
					training = true;
					if (s.pendingRespawn || !Places.inside(Places.TRAINING_ROOM, p)) {
						if (s.pendingRespawn) {
							revive(p);
						}
						s.pendingRespawn = false;
						Places.TRAINING_SPAWN.teleport(p);
					}
				}
				case DUEL -> {
					if (s.duel == null) {
						toMenu(p);
					}
				}
				case BRAWL -> {
					if (s.brawl == null) {
						toMenu(p);
					}
				}
				case TEAM -> {
					if (s.team == null) {
						toMenu(p);
					}
				}
				case FREE -> {
					// 제한 없음 — 관리자가 알아서 돌아다닙니다
				}
			}
		}
		Training.tick(level, training);
		Tutorial.tickDummy(level);
		QUEUE.removeIf(ServerPlayer::hasDisconnected);
		TEAM_QUEUES.forEach(q -> q.removeIf(ServerPlayer::hasDisconnected));
		if (match != null) {
			match.tick();
			if (match.finished) {
				match = null;
			}
		}
		if (brawl != null) {
			brawl.tick();
			if (brawl.finished || (brawl.phase() == Brawl.Phase.LOBBY && brawl.size() == 0)) {
				brawl = null;
			}
		}
		if (match == null && QUEUE.size() >= 2) {
			ServerPlayer a = QUEUE.remove(0);
			ServerPlayer b = QUEUE.remove(0);
			match = new Duel(a, Session.of(a).queuedClass, b, Session.of(b).queuedClass);
			match.begin();
		}
		if (teamMatch != null) {
			teamMatch.tick();
			if (teamMatch.finished || teamMatch.empty()) {
				teamMatch = null;
			}
		}
		for (int i = 0; i < TEAM_QUEUES.size() && teamMatch == null; i++) {
			int teamSize = i + 2;
			List<ServerPlayer> queue = TEAM_QUEUES.get(i);
			if (queue.size() >= teamSize * 2) {
				List<ServerPlayer> picked = new ArrayList<>(queue.subList(0, teamSize * 2));
				picked.forEach(queue::remove);
				teamMatch = new TeamMatch(picked, teamSize);
				teamMatch.beginDraft();
			}
		}
		if (Ticks.every(level.getGameTime(), 20)) {
			// 몇 명 모였는지 대기 화면에 알려 줍니다
			TEAM_QUEUES.forEach(q -> q.forEach(Game::sendMenu));
		}
	}

	/** 막은 죽음에서 되살림 — 진행 중인 스킬 · CC 를 풀고 체력 가득. */
	private static void revive(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		CrowdControl.clearAll(p);
		p.setHealth(p.getMaxHealth());
		p.clearFire();
	}

	/** 모드가 없는 클라이언트용 안내 — 채팅 버튼. */
	private static void help(ServerPlayer p) {
		p.sendSystemMessage(Component.empty().append(Hud.bold("OVERBREAK", ChatFormatting.RED))
				.append(Hud.text("  메인 화면을 보려면 OVERBREAK 모드가 필요합니다.", ChatFormatting.GRAY)));
		MutableComponent line = Component.empty();
		if (!Session.tutorialDone(p)) {
			line.append(button("[튜토리얼]", "/lobby tutorial", ChatFormatting.GREEN));
		} else {
			line.append(Hud.text("1대1 매치: /lobby duel <직업>  ", ChatFormatting.GRAY));
		}
		line.append(Hud.text("  훈련장: /lobby training <직업>", ChatFormatting.GRAY));
		p.sendSystemMessage(line);
	}

	private static MutableComponent button(String text, String command, ChatFormatting color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withBold(true).withClickEvent(new ClickEvent.RunCommand(command)));
	}

	// ── 막기 ────────────────────────────────────────────────

	private static boolean allowInput(ServerPlayer p, InputRouter.Slot slot) {
		Session s = Session.of(p);
		if (!s.managed) {
			return true;
		}
		return switch (s.place) {
			case MENU, QUEUE, FREE -> false;
			case TUTORIAL -> Tutorial.allow(p, slot);
			case TRAINING -> true;
			case DUEL -> s.duel != null && s.duel.allowInput();
			case BRAWL -> s.brawl != null && s.brawl.allowInput(p);
			case TEAM -> s.team != null && s.team.allowInput(p);
		};
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer p) {
			Session s = Session.of(p);
			if (!s.managed) {
				return true;
			}
			if (s.place == Session.Place.MENU || s.place == Session.Place.QUEUE || s.place == Session.Place.FREE) {
				return false;
			}
			if (s.place == Session.Place.DUEL) {
				if (s.duel == null || !s.duel.allowDamage()) {
					return false;
				}
			}
			if (s.place == Session.Place.BRAWL) {
				if (s.brawl == null || !s.brawl.fighting(p)) {
					return false;
				}
			}
			if (s.place == Session.Place.TEAM) {
				if (s.team == null || !s.team.allowDamage(p)) {
					return false;
				}
			}
			// 다른 플레이어에게는 같은 경기의 상대에게만 맞음
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != p) {
				if (s.team != null) {
					// 같은 편은 서로 때리지 못합니다
					return Session.of(attacker).team == s.team && !s.team.sameTeam(p, attacker) && s.team.fighting(attacker);
				}
				if (s.brawl != null) {
					return Session.of(attacker).brawl == s.brawl && s.brawl.fighting(attacker);
				}
				return s.duel != null && s.duel.other(p) == attacker;
			}
		}
		return true;
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p)) {
			return true;
		}
		Session s = Session.of(p);
		if (!s.managed) {
			return true;
		}
		switch (s.place) {
			case DUEL -> {
				if (s.duel != null) {
					s.duel.onDown(p);
				}
				p.setHealth(p.getMaxHealth());
			}
			case BRAWL -> {
				if (s.brawl != null) {
					s.brawl.onDown(p, source.getEntity() instanceof ServerPlayer killer ? killer : null);
				}
				p.setHealth(p.getMaxHealth());
			}
			case TEAM -> {
				if (s.team != null) {
					s.team.onDown(p);
				}
				p.setHealth(p.getMaxHealth());
			}
			case TUTORIAL, TRAINING -> {
				p.setHealth(p.getMaxHealth());
				s.pendingRespawn = true;
			}
			default -> p.setHealth(p.getMaxHealth());
		}
		return false;
	}

	/** 시험용. */
	public static @Nullable Duel match() {
		return match;
	}

	public static int queued() {
		return QUEUE.size();
	}

	/** 팀전에서 같은 편인가 — 스킬 범위 · 총알 판정이 아군을 아예 무시하게 합니다. */
	private static boolean sameTeam(net.minecraft.world.entity.Entity caster, LivingEntity target) {
		if (!(caster instanceof ServerPlayer a) || !(target instanceof ServerPlayer b) || a == b) {
			return false;
		}
		TeamMatch m = Session.of(a).team;
		return m != null && m == Session.of(b).team && m.sameTeam(a, b);
	}

	/** 시험용. */
	public static @Nullable TeamMatch teamMatch() {
		return teamMatch;
	}

	/** 게임 종류 이름 → 한 팀 인원 (팀 격전이 아니면 0). */
	private static int teamSize(String mode) {
		if (MenuActionPayload.MODE_TEAM2.equals(mode)) {
			return 2;
		}
		// MODE_TEAM 은 예전 이름 — 3대3 으로 봅니다
		return MenuActionPayload.MODE_TEAM3.equals(mode) || MenuActionPayload.MODE_TEAM.equals(mode) ? 3 : 0;
	}

	private static String teamMode(int teamSize) {
		return teamSize == 2 ? MenuActionPayload.MODE_TEAM2 : MenuActionPayload.MODE_TEAM3;
	}

	/** 시험용: 팀 격전 대기 인원 (2 또는 3). */
	public static int teamQueued(int teamSize) {
		return TEAM_QUEUES.get(Math.max(0, Math.min(1, teamSize - 2))).size();
	}

	/** 시험용. */
	public static @Nullable Brawl brawl() {
		return brawl;
	}

	/** 시험용: 대기 · 경기를 모두 비움. */
	public static void resetForTest() {
		QUEUE.clear();
		TEAM_QUEUES.forEach(List::clear);
		match = null;
		brawl = null;
		teamMatch = null;
	}
}
