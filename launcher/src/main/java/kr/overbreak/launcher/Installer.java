package kr.overbreak.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 설치 · 업데이트 — 마인크래프트 폴더를 찾고, Fabric 0.19.5 를 깔고, 모드를 최신으로 맞춥니다.
 *
 * 바닐라 세계 · 다른 모드를 건드리지 않도록 전용 게임 폴더({@code .minecraft/overbreak})를 따로 씁니다.
 * 모드는 그 폴더의 {@code mods/} 안에만 들어가고, 옛 판은 지웁니다.
 */
public final class Installer {
	/** 전용 게임 폴더 이름 (.minecraft 안). */
	public static final String GAME_DIR = "overbreak";
	/** 런처 프로필 이름 · 키. */
	public static final String PROFILE_KEY = "overbreak";
	public static final String PROFILE_NAME = "OVERBREAK";

	private final Consumer<String> log;

	public Installer(Consumer<String> log) {
		this.log = log;
	}

	// ── 마인크래프트 폴더 ────────────────────────────────────

	/** 운영체제별 기본 .minecraft 위치. 없으면 null. */
	public static Path findMinecraft() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		List<Path> tries = new ArrayList<>();
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			if (appData != null) {
				tries.add(Path.of(appData, ".minecraft"));
			}
		} else if (os.contains("mac")) {
			tries.add(Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft"));
		} else {
			tries.add(Path.of(System.getProperty("user.home"), ".minecraft"));
		}
		for (Path p : tries) {
			if (Files.isDirectory(p)) {
				return p;
			}
		}
		return tries.isEmpty() ? null : tries.getFirst();
	}

	public static Path gameDir(Path minecraft) {
		return minecraft.resolve(GAME_DIR);
	}

	public static Path modsDir(Path minecraft) {
		return gameDir(minecraft).resolve("mods");
	}

	// ── 설치본 상태 ─────────────────────────────────────────

	/** 지금 깔려 있는 모드 버전 (mods 폴더의 파일 이름에서). 없으면 null. */
	public static String installedVersion(Path minecraft) {
		Path mods = modsDir(minecraft);
		if (!Files.isDirectory(mods)) {
			return null;
		}
		try (Stream<Path> files = Files.list(mods)) {
			return files.map(p -> p.getFileName().toString())
					.filter(n -> n.startsWith("overbreak-") && n.endsWith(".jar"))
					.map(n -> n.substring("overbreak-".length(), n.length() - ".jar".length()))
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	// ── 설치 ────────────────────────────────────────────────

	/**
	 * 최신 상태로 맞춥니다 — Fabric 버전 · 런처 프로필 · 모드 두 개.
	 *
	 * @param progress 파일 하나의 진행률 (받은 바이트, 전체 바이트 — 모르면 -1)
	 */
	public void install(Path minecraft, Map<String, Object> manifest, BiConsumer<Long, Long> progress)
			throws IOException, InterruptedException {
		String mc = Json.str(manifest, "minecraft");
		String loader = Json.str(manifest, "fabricLoader");
		if (mc == null || loader == null) {
			throw new IOException("update.json 에 minecraft · fabricLoader 가 없습니다");
		}
		Files.createDirectories(modsDir(minecraft));

		String versionId = fabricVersionId(loader, mc);
		installFabric(minecraft, mc, loader, versionId);
		writeProfile(minecraft, versionId);

		syncMod(minecraft, manifest, "fabricApi", "fabric-api-", progress);
		syncMod(minecraft, manifest, "mod", "overbreak-", progress);
		log.accept("모두 최신입니다.");
	}

	public static String fabricVersionId(String loader, String mc) {
		return "fabric-loader-" + loader + "-" + mc;
	}

	/** Fabric 모드로더 버전 파일 — 이미 있으면 그대로 둡니다. */
	private void installFabric(Path minecraft, String mc, String loader, String versionId)
			throws IOException, InterruptedException {
		Path dir = minecraft.resolve("versions").resolve(versionId);
		Path json = dir.resolve(versionId + ".json");
		if (Files.isRegularFile(json) && Files.size(json) > 0) {
			log.accept("Fabric " + loader + " 이미 설치됨");
			return;
		}
		log.accept("Fabric " + loader + " (" + mc + ") 설치 중...");
		String url = "https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/profile/json";
		String body = Net.text(url);
		Files.createDirectories(dir);
		Files.writeString(json, body, StandardCharsets.UTF_8);
		log.accept("Fabric " + loader + " 설치 완료");
	}

	/** 공식 런처 프로필에 OVERBREAK 를 넣습니다 (전용 게임 폴더 · 최근 사용으로 표시). */
	@SuppressWarnings("unchecked")
	private void writeProfile(Path minecraft, String versionId) throws IOException {
		Path file = minecraft.resolve("launcher_profiles.json");
		Map<String, Object> root;
		if (Files.isRegularFile(file)) {
			root = Json.object(Files.readString(file, StandardCharsets.UTF_8));
		} else {
			root = new LinkedHashMap<>();
		}
		Object profilesRaw = root.get("profiles");
		Map<String, Object> profiles = profilesRaw instanceof Map ? (Map<String, Object>) profilesRaw : new LinkedHashMap<>();
		// 공식 런처가 쓰는 모양 (…T…Z)
		String now = Instant.now().toString();

		Object old = profiles.get(PROFILE_KEY);
		Map<String, Object> profile = old instanceof Map ? new LinkedHashMap<>((Map<String, Object>) old) : new LinkedHashMap<>();
		profile.putIfAbsent("created", now);
		profile.put("name", PROFILE_NAME);
		profile.put("type", "custom");
		profile.put("icon", "Crafting_Table");
		profile.put("lastVersionId", versionId);
		profile.put("gameDir", gameDir(minecraft).toAbsolutePath().toString());
		// 공식 런처는 보통 마지막으로 쓴 프로필을 골라 둡니다
		profile.put("lastUsed", now);
		profiles.put(PROFILE_KEY, profile);

		root.put("profiles", profiles);
		root.putIfAbsent("version", 3.0);
		Files.writeString(file, Json.write(root), StandardCharsets.UTF_8);
		log.accept("런처 프로필 「" + PROFILE_NAME + "」 준비 완료");
	}

	/**
	 * 모드 파일 하나를 최신으로 — 같은 이름표(prefix)의 옛 파일은 지우고 새로 받습니다.
	 * 이미 같은 파일(sha256)이 있으면 받지 않습니다.
	 */
	private void syncMod(Path minecraft, Map<String, Object> manifest, String key, String prefix, BiConsumer<Long, Long> progress)
			throws IOException, InterruptedException {
		String file = Json.str(manifest, key, "file");
		String url = Json.str(manifest, key, "url");
		String sha = Json.str(manifest, key, "sha256");
		if (file == null || url == null) {
			log.accept("update.json 에 " + key + " 가 없어 건너뜁니다");
			return;
		}
		Path mods = modsDir(minecraft);
		Path target = mods.resolve(file);
		if (Net.matches(target, sha)) {
			log.accept(file + " 최신");
			cleanOld(mods, prefix, file);
			return;
		}
		log.accept(file + " 내려받는 중...");
		Net.download(url, target, progress);
		if (sha != null && !sha.isBlank() && !Net.sha256(target).equalsIgnoreCase(sha)) {
			Files.deleteIfExists(target);
			throw new IOException(file + " 가 깨졌습니다 (체크섬 불일치). 잠시 뒤 다시 시도해 주세요.");
		}
		cleanOld(mods, prefix, file);
		log.accept(file + " 설치 완료");
	}

	/** 같은 이름표를 가진 옛 판 지우기. */
	private void cleanOld(Path mods, String prefix, String keep) throws IOException {
		try (Stream<Path> files = Files.list(mods)) {
			List<Path> old = files.filter(p -> {
				String n = p.getFileName().toString();
				return n.startsWith(prefix) && n.endsWith(".jar") && !n.equals(keep);
			}).toList();
			for (Path p : old) {
				Files.deleteIfExists(p);
				log.accept("옛 판 삭제: " + p.getFileName());
			}
		}
	}

	// ── 실행 ────────────────────────────────────────────────

	/**
	 * 공식 마인크래프트 런처를 띄웁니다 (로그인 · 게임 실행은 공식 런처가 합니다).
	 *
	 * @return 띄웠으면 true
	 */
	public boolean openMinecraftLauncher() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		List<String[]> tries = new ArrayList<>();
		if (os.contains("win")) {
			String pf86 = System.getenv("ProgramFiles(x86)");
			String pf = System.getenv("ProgramFiles");
			if (pf86 != null) {
				tries.add(new String[] {pf86 + "\\Minecraft Launcher\\MinecraftLauncher.exe"});
			}
			if (pf != null) {
				tries.add(new String[] {pf + "\\Minecraft Launcher\\MinecraftLauncher.exe"});
			}
			// 마이크로소프트 스토어판은 minecraft: 주소로 열립니다
			tries.add(new String[] {"cmd", "/c", "start", "", "minecraft://"});
		} else if (os.contains("mac")) {
			tries.add(new String[] {"open", "-a", "Minecraft"});
		} else {
			tries.add(new String[] {"minecraft-launcher"});
		}
		for (String[] cmd : tries) {
			if (cmd.length == 1 && cmd[0].contains("\\") && !Files.isRegularFile(Path.of(cmd[0]))) {
				continue;
			}
			try {
				new ProcessBuilder(cmd).start();
				log.accept("마인크래프트 런처를 띄웠습니다. 프로필에서 「" + PROFILE_NAME + "」 을 고르고 플레이하세요.");
				return true;
			} catch (IOException ignored) {
				// 다음 후보
			}
		}
		log.accept("마인크래프트 런처를 찾지 못했습니다. 직접 실행한 뒤 「" + PROFILE_NAME + "」 프로필을 고르세요.");
		return false;
	}

	public static void openFolder(Path dir) {
		try {
			Files.createDirectories(dir);
			java.awt.Desktop.getDesktop().open(dir.toFile());
		} catch (Exception ignored) {
			// 열 수 없으면 조용히 둡니다
		}
	}
}
