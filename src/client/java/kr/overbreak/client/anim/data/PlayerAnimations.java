package kr.overbreak.client.anim.data;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonParser;
import kr.overbreak.Overbreak;
import kr.overbreak.net.SkillAnimPayload;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blockbench 애니메이션 파일 모음.
 *
 *   1. 모드에 들어 있는 기본값: assets/overbreak/player_animations/*.animation.json
 *   2. 게임 폴더의 overbreak/animations/*.json — 같은 이름이 있으면 이쪽이 이깁니다.
 *      게임 중 1초마다 확인해서, 파일을 저장하면 바로 다시 읽고 채팅으로 알려 줍니다 (오류도 채팅으로).
 *
 * 스킬 번호 → 애니메이션 이름은 {@link #name(int)}. 이름이 있는 파일이 없으면 코드에 적힌 동작을 씁니다.
 */
public final class PlayerAnimations {
	private static final Logger LOG = LoggerFactory.getLogger("overbreak/animations");
	private static final String BUNDLED = "assets/" + Overbreak.MOD_ID + "/player_animations";
	public static final String FOLDER = Overbreak.MOD_ID + "/animations";

	private static Map<String, PlayerAnimation> all = Map.of();
	private static Map<String, Long> stamps = Map.of();
	private static final List<Component> MESSAGES = new ArrayList<>();
	private static boolean loaded;
	private static int timer;

	private PlayerAnimations() {}

	/** 두 손 총을 들고 있는 동안 늘 까는 기본 자세. */
	public static final String VALKYRIE_READY = "valkyrie.ready";

	/** 스킬 애니메이션 번호 → 파일 안의 이름 ("animation.overbreak." 뒤). */
	public static @Nullable String name(int anim) {
		return switch (anim) {
			case SkillAnimPayload.SLAY -> "warrior.slay";
			case SkillAnimPayload.FURY -> "warrior.fury";
			case SkillAnimPayload.CHAIN -> "warrior.chain";
			case SkillAnimPayload.ULT -> "warrior.ult";
			case SkillAnimPayload.BASIC -> "warrior.basic";
			case SkillAnimPayload.BASIC_BACK -> "warrior.basic_back";
			case SkillAnimPayload.HK_SMASH -> "hammerknight.smash";
			case SkillAnimPayload.HK_SLAM -> "hammerknight.slam";
			case SkillAnimPayload.HK_CHARGE -> "hammerknight.charge";
			case SkillAnimPayload.HK_ULT -> "hammerknight.ult";
			case SkillAnimPayload.IF_SHOT -> "ironfist.shot";
			case SkillAnimPayload.IF_CHARGE -> "ironfist.charge";
			case SkillAnimPayload.IF_PUNCH -> "ironfist.punch";
			case SkillAnimPayload.IF_BLOCK -> "ironfist.block";
			case SkillAnimPayload.IF_SLAM_AIR -> "ironfist.slam_air";
			case SkillAnimPayload.IF_SLAM_HIT -> "ironfist.slam_hit";
			case SkillAnimPayload.IF_ULT_RISE -> "ironfist.ult_rise";
			case SkillAnimPayload.IF_ULT_DROP -> "ironfist.ult_drop";
			case SkillAnimPayload.VK_SHOT -> "valkyrie.shot";
			case SkillAnimPayload.VK_ROCKET -> "valkyrie.rocket";
			case SkillAnimPayload.VK_FLOAT -> "valkyrie.float";
			case SkillAnimPayload.VK_OVERHEAT -> "valkyrie.overheat";
			case SkillAnimPayload.VK_BARRAGE -> "valkyrie.barrage";
			case SkillAnimPayload.VK_RELOAD -> "valkyrie.reload";
			case SkillAnimPayload.SH_SHOT -> "sheriff.shot";
			case SkillAnimPayload.SH_FAN -> "sheriff.fan";
			case SkillAnimPayload.SH_RELOAD -> "sheriff.reload";
			case SkillAnimPayload.SH_ROLL -> "sheriff.roll";
			case SkillAnimPayload.SH_FLASH -> "sheriff.flash";
			case SkillAnimPayload.SH_DEADEYE -> "sheriff.deadeye";
			case SkillAnimPayload.SH_DEADEYE_FIRE -> "sheriff.deadeye_fire";
			case SkillAnimPayload.SD_REND -> "shade.rend";
			case SkillAnimPayload.SD_EVADE -> "shade.evade";
			case SkillAnimPayload.SD_KUNAI -> "shade.kunai";
			case SkillAnimPayload.SD_STRIKE -> "shade.strike";
			case SkillAnimPayload.SD_STEP -> "shade.step";
			case SkillAnimPayload.TH_CAST -> "thunder.cast";
			case SkillAnimPayload.TH_DASH -> "thunder.dash";
			case SkillAnimPayload.TH_FIELD -> "thunder.field";
			case SkillAnimPayload.TH_SMITE -> "thunder.smite";
			case SkillAnimPayload.TH_ULT -> "thunder.ult";
			case SkillAnimPayload.BR_BASIC -> "brute.basic";
			case SkillAnimPayload.BR_BASIC_BACK -> "brute.basic_back";
			case SkillAnimPayload.BR_BLOW -> "brute.blow";
			case SkillAnimPayload.BR_WHIRL -> "brute.whirl";
			case SkillAnimPayload.BR_REGROUP -> "brute.regroup";
			case SkillAnimPayload.BR_ULT -> "brute.ult";
			case SkillAnimPayload.GS_SHOT -> "gunslinger.shot";
			case SkillAnimPayload.GS_SHOT_L -> "gunslinger.shot_left";
			case SkillAnimPayload.GS_RELOAD -> "gunslinger.reload";
			case SkillAnimPayload.GS_BOOST -> "gunslinger.boost";
			case SkillAnimPayload.GS_ANCHOR -> "gunslinger.anchor";
			case SkillAnimPayload.GS_RELEASE_TWIRL -> "gunslinger.release_twirl";
			case SkillAnimPayload.GS_RELEASE_SNAP -> "gunslinger.release_snap";
			case SkillAnimPayload.GS_GLIDE -> "gunslinger.glide";
			default -> null;
		};
	}

	public static @Nullable PlayerAnimation get(String name) {
		if (!loaded) {
			reload(false);
		}
		return all.get(name);
	}

	public static @Nullable PlayerAnimation forSkill(int anim) {
		String n = name(anim);
		return n == null ? null : get(n);
	}

	/** 읽혀 있는 애니메이션 이름들 (시험 · 확인용). */
	public static java.util.Set<String> names() {
		if (!loaded) {
			reload(false);
		}
		return all.keySet();
	}

	public static Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve(FOLDER);
	}

	/** 클라이언트 틱: 1초마다 폴더가 바뀌었는지 확인 · 쌓인 알림을 채팅으로. */
	public static void tick(Minecraft mc) {
		if (++timer >= 20) {
			timer = 0;
			if (!scan().equals(stamps)) {
				reload(true);
			}
		}
		if (mc.player != null && !MESSAGES.isEmpty()) {
			MESSAGES.forEach(mc.gui.hud.getChat()::addClientSystemMessage);
			MESSAGES.clear();
		}
	}

	/** 기본값 + 폴더를 처음부터 다시 읽습니다. */
	public static synchronized void reload(boolean announce) {
		loaded = true;
		Map<String, PlayerAnimation> next = new HashMap<>();
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Overbreak.MOD_ID);
		Optional<Path> bundled = mod.flatMap(c -> c.findPath(BUNDLED));
		if (bundled.isPresent()) {
			readDir(bundled.get(), next, false);
		}
		Map<String, Long> now = scan();
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, Long> e : now.entrySet()) {
			if (!e.getValue().equals(stamps.get(e.getKey()))) {
				changed.add(e.getKey());
			}
		}
		int before = next.size();
		int fromFolder = readDir(folder(), next, true);
		stamps = now;
		all = next;
		LOG.info("플레이어 애니메이션 {}개 (모드 기본 {}, 폴더 {})", next.size(), before, fromFolder);
		if (announce && !changed.isEmpty()) {
			MESSAGES.add(Component.literal("[OVERBREAK] 애니메이션 다시 읽음: " + String.join(", ", changed) + " (전체 " + next.size() + "개)")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	/** @return 읽은 애니메이션 수 */
	private static int readDir(Path dir, Map<String, PlayerAnimation> into, boolean folder) {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		int count = 0;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					Map<String, PlayerAnimation> anims = PlayerAnimation.parseFile(JsonParser.parseReader(r).getAsJsonObject());
					into.putAll(anims);
					count += anims.size();
				} catch (RuntimeException | IOException ex) {
					String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
					LOG.warn("애니메이션 파일 오류 {}: {}", file, msg);
					if (folder) {
						MESSAGES.add(Component.literal("[OVERBREAK] 애니메이션 파일 오류 (" + file.getFileName() + "): " + msg)
								.withStyle(ChatFormatting.RED));
					}
				}
			}
		} catch (IOException ex) {
			LOG.warn("애니메이션 폴더를 읽지 못했습니다: {}", dir, ex);
		}
		return count;
	}

	/** 폴더 안 파일 → 수정 시각 + 크기. */
	private static Map<String, Long> scan() {
		Path dir = folder();
		if (!Files.isDirectory(dir)) {
			return Map.of();
		}
		Map<String, Long> out = new TreeMap<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
				out.put(f.getFileName().toString(), Files.getLastModifiedTime(f).toMillis() * 31 + Files.size(f));
			}
		} catch (IOException ignored) {
			// 저장 도중이면 다음 확인 때 다시
		}
		return out;
	}
}
