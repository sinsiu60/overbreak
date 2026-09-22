package kr.overbreak.client.hud.crosshair;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 커스텀 조준점 설정 — 오버워치 조준점 설정처럼 모양 · 색 · 두께 · 길이 · 간격 · 불투명도 · 테두리 · 가운데 점을 고릅니다.
 * 이 컴퓨터에만 저장됩니다 (config/overbreak-crosshair.json). 크기 값은 모두 실제 화면 픽셀 기준 — GUI 배율과 무관.
 */
public final class CrosshairConfig {
	private static final Logger LOG = LoggerFactory.getLogger("overbreak/crosshair");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 모양. */
	public enum Type {
		CROSS("십자"), CIRCLE("원"), CIRCLE_CROSS("원 + 십자"), DOT("점"), VANILLA("마인크래프트 기본");

		public final String label;

		Type(String label) {
			this.label = label;
		}
	}

	/** 미리 정해 둔 색 — 오버워치 기본 색 몇 가지 + 하늘색(궤적의 깃털). */
	public static final int[] PRESETS = {0xFFFFFF, 0x00FF00, 0xFF3030, 0xFFFF00, 0x00FFFF, 0x7FD4FF, 0xFF40FF, 0xFFA030};
	public static final String[] PRESET_NAMES = {"흰색", "초록", "빨강", "노랑", "청록", "하늘", "분홍", "주황"};

	public Type type = Type.CROSS;
	/** 색 (0xRRGGBB). */
	public int color = 0xFFFFFF;
	/** 선 두께 (픽셀 1~10) · 길이 (0~40) · 가운데 간격 (0~40). */
	public int thickness = 2;
	public int length = 8;
	public int gap = 4;
	/** 불투명도 · 테두리 불투명도 (0~100). */
	public int opacity = 100;
	public int outline = 50;
	/** 가운데 점 크기 (0~10, 0 이면 없음) · 점 불투명도 (0~100). */
	public int dotSize = 0;
	public int dotOpacity = 100;
	/** 원 반지름 (픽셀 2~40). */
	public int radius = 10;

	private static CrosshairConfig current;

	public static CrosshairConfig get() {
		if (current == null) {
			current = load();
		}
		return current;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("overbreak-crosshair.json");
	}

	private static CrosshairConfig load() {
		Path f = file();
		if (Files.exists(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				CrosshairConfig c = GSON.fromJson(r, CrosshairConfig.class);
				if (c != null) {
					c.clamp();
					return c;
				}
			} catch (Exception e) {
				LOG.warn("조준점 설정을 읽지 못해 기본값을 씁니다: {}", e.toString());
			}
		}
		return new CrosshairConfig();
	}

	public void save() {
		clamp();
		try {
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			LOG.warn("조준점 설정을 저장하지 못했습니다: {}", e.toString());
		}
	}

	/** 기본값으로. */
	public void reset() {
		CrosshairConfig d = new CrosshairConfig();
		type = d.type;
		color = d.color;
		thickness = d.thickness;
		length = d.length;
		gap = d.gap;
		opacity = d.opacity;
		outline = d.outline;
		dotSize = d.dotSize;
		dotOpacity = d.dotOpacity;
		radius = d.radius;
	}

	void clamp() {
		if (type == null) {
			type = Type.CROSS;
		}
		color &= 0xFFFFFF;
		thickness = Mth.clamp(thickness, 1, 10);
		length = Mth.clamp(length, 0, 40);
		gap = Mth.clamp(gap, 0, 40);
		opacity = Mth.clamp(opacity, 0, 100);
		outline = Mth.clamp(outline, 0, 100);
		dotSize = Mth.clamp(dotSize, 0, 10);
		dotOpacity = Mth.clamp(dotOpacity, 0, 100);
		radius = Mth.clamp(radius, 2, 40);
	}
}
