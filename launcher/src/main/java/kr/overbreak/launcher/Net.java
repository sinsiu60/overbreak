package kr.overbreak.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.BiConsumer;

/** 내려받기 — 글 하나 읽기, 파일 받기(진행률 · sha256 확인). */
public final class Net {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(20))
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();
	private static final String AGENT = "OVERBREAK-Launcher";

	private Net() {}

	public static String text(String url) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", AGENT)
				.header("Accept", "application/json, text/plain, */*")
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() / 100 != 2) {
			throw new IOException(url + " → HTTP " + res.statusCode());
		}
		return res.body();
	}

	/**
	 * 파일로 받습니다. 받는 동안 진행률(받은 바이트, 전체 바이트)을 알려 줍니다.
	 * 전체 크기를 모르면 전체는 -1 입니다.
	 */
	public static void download(String url, Path target, BiConsumer<Long, Long> progress) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", AGENT)
				.timeout(Duration.ofMinutes(10))
				.GET()
				.build();
		HttpResponse<InputStream> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() / 100 != 2) {
			res.body().close();
			throw new IOException(url + " → HTTP " + res.statusCode());
		}
		long total = res.headers().firstValueAsLong("content-length").orElse(-1L);
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		Files.createDirectories(target.getParent());
		try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
			byte[] buf = new byte[1 << 16];
			long got = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				got += n;
				if (progress != null) {
					progress.accept(got, total);
				}
			}
		}
		Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	public static String sha256(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[1 << 16];
			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
			return HexFormat.of().formatHex(md.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	/** 있는 파일이 기대하는 sha256 과 같은가 (기대값이 비어 있으면 있기만 하면 통과). */
	public static boolean matches(Path file, String sha256) {
		if (!Files.isRegularFile(file)) {
			return false;
		}
		if (sha256 == null || sha256.isBlank()) {
			return true;
		}
		try {
			return sha256(file).equalsIgnoreCase(sha256);
		} catch (IOException e) {
			return false;
		}
	}
}
