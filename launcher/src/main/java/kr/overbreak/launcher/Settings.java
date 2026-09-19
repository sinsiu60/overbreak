package kr.overbreak.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 런처가 기억해 두는 것 — 사람이 직접 골라 준 마인크래프트 런처 경로 같은 것.
 *
 * 프로그램 폴더가 읽기 전용일 수 있어 사용자 폴더에 둡니다: {@code ~/.overbreak-launcher.properties}
 */
public final class Settings {
	/** 마인크래프트 런처 실행 파일 경로. */
	public static final String MC_LAUNCHER = "mcLauncher";

	private static final Path FILE =
			Path.of(System.getProperty("user.home", "."), ".overbreak-launcher.properties");

	private Settings() {}

	public static String get(String key) {
		Properties p = load();
		String v = p.getProperty(key);
		return v == null || v.isBlank() ? null : v.trim();
	}

	public static void put(String key, String value) {
		Properties p = load();
		p.setProperty(key, value);
		try (OutputStream out = Files.newOutputStream(FILE)) {
			p.store(out, "OVERBREAK 런처가 기억해 둔 것");
		} catch (IOException ignored) {
			// 못 적어도 이번 판은 그대로 돌아갑니다
		}
	}

	private static Properties load() {
		Properties p = new Properties();
		if (Files.isRegularFile(FILE)) {
			try (InputStream in = Files.newInputStream(FILE)) {
				p.load(in);
			} catch (IOException ignored) {
				// 깨져 있으면 빈 것으로 봅니다
			}
		}
		return p;
	}
}
