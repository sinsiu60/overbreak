package kr.overbreak.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 런처 설정 — 어느 저장소에서 모드를 받아올지.
 *
 * 값은 아래 차례로 정해집니다 — 뒤에 오는 것이 이깁니다:
 * 아래 박힌 기본값 → jar 안에 구워 넣은 {@code /launcher.properties} → jar 옆에 둔 {@code launcher.properties}.
 *
 * 옆에 두는 파일은 이렇게 씁니다:
 *
 * <pre>
 * repo=내계정/내저장소
 * # 또는 주소를 통째로
 * manifest=https://example.com/update.json
 * </pre>
 */
public final class Config {
	/** GitHub 저장소 (owner/repo) — 저장소를 만든 뒤 여기나 launcher.properties 에서 바꿉니다. */
	public static final String DEFAULT_REPO = "sinsiu60/overbreak";

	public final String repo;
	public final String manifestUrl;

	private Config(String repo, String manifestUrl) {
		this.repo = repo;
		this.manifestUrl = manifestUrl;
	}

	public static Config load() {
		Properties p = new Properties();
		// 빌드할 때 구워 넣은 기본값 (jar 안) — 옆에 둔 파일이 있으면 그쪽으로 덮어씁니다
		try (InputStream in = Config.class.getResourceAsStream("/launcher.properties")) {
			if (in != null) {
				p.load(in);
			}
		} catch (IOException ignored) {
			// 없거나 깨져 있으면 아래 기본값으로 갑니다
		}
		for (Path candidate : new Path[] {besideJar("launcher.properties"), Path.of("launcher.properties")}) {
			if (candidate != null && Files.isRegularFile(candidate)) {
				try (InputStream in = Files.newInputStream(candidate)) {
					p.load(in);
					break;
				} catch (IOException ignored) {
					// 설정이 깨져 있으면 기본값으로 갑니다
				}
			}
		}
		String repo = p.getProperty("repo", DEFAULT_REPO).trim();
		// 최신 릴리스의 update.json — 태그를 몰라도 늘 최신을 가리키는 주소
		String url = p.getProperty("manifest", "https://github.com/" + repo + "/releases/latest/download/update.json").trim();
		return new Config(repo, url);
	}

	/** 실행 중인 jar 가 있는 폴더의 파일. 알 수 없으면 null. */
	public static Path besideJar(String name) {
		try {
			Path jar = Path.of(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
			return dir == null ? null : dir.resolve(name);
		} catch (Exception e) {
			return null;
		}
	}

	public String releasesPage() {
		return "https://github.com/" + repo + "/releases";
	}
}
