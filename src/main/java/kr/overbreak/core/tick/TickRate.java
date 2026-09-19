package kr.overbreak.core.tick;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * 서버 틱레이트 강제 — OVERBREAK 는 1초 60틱 고정입니다 (설정 · 명령으로 바꿀 수 없음).
 *
 *   config/overbreak/server.json : { "lockGameRules": true }
 *
 *   서버가 켜지면 60틱으로 설정 → 바닐라가 모든 클라이언트에 알려 줌 (ClientboundTickingStatePacket, 접속할 때도)
 *   /tick rate 등으로 바뀌면 다음 틱에 되돌림 (/tick sprint · freeze · step 중에는 건드리지 않음)
 *   바닐라 이동이 빨라지지 않게 모든 생명체에 {@link MovementScale} 보정
 *   lockGameRules: 20틱이 아니면 틱 기반 바닐라 타이머를 멈춤 — 낮밤 · 날씨 · 몹 스폰 · 무작위 틱(작물 · 불) · 자연 회복
 *
 *   /overbreak tickrate       지금 틱레이트 보기
 *   /overbreak mspt           평균 틱 처리 시간 (1틱 예산과 비교)
 *   /overbreak bots <n>|clear 부하 시험용 봇 (LoadBots)
 */
public final class TickRate {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int target = TickRateConfig.DEFAULT;
	private static boolean lockGameRules = true;

	private TickRate() {}

	public static void init() {
		load();
		TickRateConfig.set(target);
		ServerLifecycleEvents.SERVER_STARTED.register(TickRate::enforce);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerTickRateManager m = server.tickRateManager();
			if (Math.round(m.tickrate()) != target && !m.isSprinting() && !m.isFrozen()) {
				Overbreak.LOGGER.warn("틱레이트가 {} 로 바뀌어 {} 로 되돌립니다", m.tickrate(), target);
				m.setTickRate(target);
			}
			LoadBots.tick(server);
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(target, source, base, taken, blocked) -> GameClock.hurt(target));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity e) {
				MovementScale.apply(e);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> MovementScale.apply(newPlayer));
		ServerPlayerEvents.JOIN.register(MovementScale::apply);
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(
				Commands.literal("overbreak").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("tickrate")
								.executes(c -> {
									c.getSource().sendSuccess(() -> Component.literal("[OVERBREAK] 목표 틱레이트 " + target
											+ " · 지금 " + c.getSource().getServer().tickRateManager().tickrate()), false);
									return target;
								}))
						.then(Commands.literal("mspt").executes(c -> {
							MinecraftServer server = c.getSource().getServer();
							c.getSource().sendSuccess(() -> Component.literal(msptReport(server)), false);
							return (int) Math.round(mspt(server));
						}))
						.then(Commands.literal("bots")
								.then(Commands.literal("clear").executes(c -> {
									LoadBots.clear();
									c.getSource().sendSuccess(() -> Component.literal("[OVERBREAK] 봇을 모두 치웠습니다"), false);
									return 1;
								}))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 32)).executes(c -> {
									int n = IntegerArgumentType.getInteger(c, "count");
									LoadBots.spawn(c.getSource().getLevel(), c.getSource().getPosition(), n);
									c.getSource().sendSuccess(() -> Component.literal("[OVERBREAK] 봇 " + n + "명 · 표적 " + n + "개 — /overbreak mspt 로 확인"), false);
									return n;
								})))));
	}

	/** 평균 틱 처리 시간 (ms). */
	public static double mspt(MinecraftServer server) {
		return server.getAverageTickTimeNanos() / 1_000_000.0;
	}

	public static String msptReport(MinecraftServer server) {
		double ms = mspt(server);
		double budget = 1000.0 / target;
		return String.format(java.util.Locale.ROOT, "[OVERBREAK] MSPT %.2fms / 예산 %.2fms (%d틱) · %s · 접속 %d · 봇 %d",
				ms, budget, target, ms <= budget ? "여유" : "초과", server.getPlayerList().getPlayerCount(), LoadBots.count());
	}

	private static void enforce(MinecraftServer server) {
		server.tickRateManager().setTickRate(target);
		if (lockGameRules && target != TickRateConfig.VANILLA) {
			GameRules rules = server.getGameRules();
			rules.set(GameRules.ADVANCE_TIME, false, server);
			rules.set(GameRules.ADVANCE_WEATHER, false, server);
			rules.set(GameRules.SPAWN_MOBS, false, server);
			rules.set(GameRules.SPAWN_MONSTERS, false, server);
			rules.set(GameRules.RANDOM_TICK_SPEED, 0, server);
			rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof LivingEntity le) {
					MovementScale.apply(le);
				}
			}
		}
		Overbreak.LOGGER.info("틱레이트 {} (이동 보정 {})", target, MovementScale.factors(TickRateConfig.scale()));
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(Overbreak.MOD_ID).resolve("server.json");
	}

	private static void load() {
		Path f = file();
		try {
			if (Files.exists(f)) {
				try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
					JsonObject o = GSON.fromJson(r, JsonObject.class);
					if (o != null && o.has("lockGameRules")) {
						lockGameRules = o.get("lockGameRules").getAsBoolean();
					}
				}
			} else {
				save();
			}
		} catch (IOException | RuntimeException e) {
			Overbreak.LOGGER.error("config/overbreak/server.json 을 읽지 못해 기본값을 씁니다", e);
		}
	}

	private static void save() {
		JsonObject o = new JsonObject();
		o.addProperty("lockGameRules", lockGameRules);
		try {
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
		} catch (IOException e) {
			Overbreak.LOGGER.error("config/overbreak/server.json 을 저장하지 못했습니다", e);
		}
	}
}
