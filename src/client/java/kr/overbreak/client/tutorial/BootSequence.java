package kr.overbreak.client.tutorial;

import kr.overbreak.client.ClientClock;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3x2fStack;

/**
 * 훈련장이 켜지는 연출 — 가상세계가 부팅되는 것처럼 보이게.
 *
 *   1) 새까만 화면에서 가로 틈이 열리듯 위아래 검은 띠가 물러나며 시야가 넓어짐 (시야각도 좁은 데서 제자리로)
 *   2) 그 사이 격자 · 훑고 내려가는 주사선 · 지직거리는 조각 · 부팅 문구가 한 줄씩
 *   3) 마지막에 흰 섬광과 함께 「OVERBREAK」
 *
 * 길이는 서버가 정합니다 (보통 5초). 화면 효과만이라 게임은 그대로 돌아갑니다.
 */
public final class BootSequence {
	private static final String[] TUTORIAL_LINES = {
			"OVERBREAK CORE",
			"규격 서버 연결 ....... 완료",
			"생체 신호 동기화 ..... 완료",
			"시야 보정 ............ 진행",
			"훈련장 지형 전개 ..... 완료",
	};
	/** 경기 투입판 — 전장에 들어갈 때. */
	private static final String[] MATCH_LINES = {
			"OVERBREAK CORE",
			"전장 좌표 동기화 ..... 완료",
			"규격 데이터 전개 ..... 완료",
			"생체 신호 연결 ....... 완료",
			"전장 투입 ............ 준비",
	};
	private static String[] lines = TUTORIAL_LINES;
	private static final int CYAN = 0x7FE0FF;
	private static final RandomSource RANDOM = RandomSource.create(7);

	private static @org.jspecify.annotations.Nullable CameraType savedCamera;
	private static double start = -1.0;
	private static double length;
	private static int spoken;
	private static double glitchUntil = -1.0;

	private BootSequence() {}

	/** @param timeUnits 전체 길이 (1/20초 단위) */
	public static void start(int timeUnits) {
		start(timeUnits, true);
	}

	/**
	 * @param timeUnits 전체 길이 (1/20초 단위)
	 * @param tutorial  true = 튜토리얼판 (1인칭으로 바꿔 눈을 뜨는 느낌) · false = 경기 투입판 (시점을 건드리지 않음)
	 */
	public static void start(int timeUnits, boolean tutorial) {
		start = ClientClock.now();
		length = Math.max(20, timeUnits);
		spoken = 0;
		lines = tutorial ? TUTORIAL_LINES : MATCH_LINES;
		Minecraft mc = Minecraft.getInstance();
		if (tutorial) {
			// 켜지는 동안은 1인칭 (가상세계에 눈을 뜨는 느낌)
			if (savedCamera == null) {
				savedCamera = mc.options.getCameraType();
			}
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}
		mc.gui.toastManager().clear();
	}

	/** 부팅이 끝나면 원래 시점으로. */
	private static void restoreCamera(Minecraft mc) {
		if (savedCamera != null) {
			mc.options.setCameraType(savedCamera);
			savedCamera = null;
		}
	}

	/** 짧은 지직거림 (규격 수신 등). */
	public static void glitch(int timeUnits) {
		glitchUntil = ClientClock.now() + Math.max(2, timeUnits);
	}

	public static void stop() {
		start = -1.0;
		glitchUntil = -1.0;
		restoreCamera(Minecraft.getInstance());
	}

	public static boolean running() {
		return start >= 0 && ClientClock.now() - start < length;
	}

	/** 부팅 중 시야각 배율 (좁게 시작해 제자리로) — 1인칭 시야 보정에서 곱합니다. */
	public static float fovScale() {
		if (!running()) {
			return 1.0F;
		}
		float t = (float) ((ClientClock.now() - start) / length);
		float k = Mth.clamp((t - 0.05F) / 0.55F, 0.0F, 1.0F);
		k = k * k * (3.0F - 2.0F * k);
		return Mth.lerp(k, 0.45F, 1.0F);
	}

	public static void tick(Minecraft mc) {
		if (!running()) {
			if (start >= 0) {
				start = -1.0;
				restoreCamera(mc);
			}
			return;
		}
		double t = (ClientClock.now() - start) / length;
		// 부팅 문구가 한 줄씩 뜰 때마다 신호음
		int want = lineCount(t);
		if (want > spoken) {
			spoken = want;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5F, 0.4F));
		}
	}

	private static int lineCount(double t) {
		return (int) Mth.clamp((t - 0.08) / 0.62 * lines.length + 1, 0, lines.length);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		double now = ClientClock.at(partial);
		if (glitchUntil > now) {
			glitch(g, now, 0.6F);
		}
		if (start < 0 || now - start >= length) {
			return;
		}
		float t = (float) ((now - start) / length);
		int w = g.guiWidth();
		int h = g.guiHeight();

		// ── 위아래 검은 띠: 가운데 틈에서 점점 열림 ──
		float openK = Mth.clamp((t - 0.03F) / 0.55F, 0.0F, 1.0F);
		openK = openK * openK * (3.0F - 2.0F * openK);
		int barH = Math.round(h / 2.0F * (1.0F - openK));
		if (barH > 0) {
			g.fill(0, 0, w, barH, 0xFF000000);
			g.fill(0, h - barH, w, h, 0xFF000000);
			// 띠 안쪽 가장자리의 빛
			g.fill(0, barH, w, barH + 1, argb(0xE0, CYAN));
			g.fill(0, h - barH - 1, w, h - barH, argb(0xE0, CYAN));
			g.fillGradient(0, barH + 1, w, barH + 12, argb(0x50, CYAN), 0x00000000);
			g.fillGradient(0, h - barH - 12, w, h - barH - 1, 0x00000000, argb(0x50, CYAN));
		}
		// 남은 어둠 (열린 뒤에도 잠깐 옅게)
		int dim = Math.round(120 * (1.0F - Mth.clamp((t - 0.35F) / 0.45F, 0.0F, 1.0F)));
		if (dim > 0) {
			g.fill(0, barH, w, h - barH, dim << 24);
		}

		// ── 격자 (열리는 동안만) ──
		float gridA = 1.0F - Mth.clamp((t - 0.25F) / 0.35F, 0.0F, 1.0F);
		if (gridA > 0.01F) {
			int step = Math.max(16, w / 24);
			int color = argb(Math.round(60 * gridA), CYAN);
			for (int x = 0; x < w; x += step) {
				g.fill(x, barH, x + 1, h - barH, color);
			}
			for (int y = barH; y < h - barH; y += step) {
				g.fill(0, y, w, y + 1, color);
			}
			// 가운데 십자
			g.fill(w / 2 - 12, h / 2, w / 2 + 12, h / 2 + 1, argb(Math.round(160 * gridA), CYAN));
			g.fill(w / 2, h / 2 - 12, w / 2 + 1, h / 2 + 12, argb(Math.round(160 * gridA), CYAN));
		}

		// ── 훑고 내려가는 주사선 ──
		float sweepA = 1.0F - Mth.clamp((t - 0.55F) / 0.25F, 0.0F, 1.0F);
		if (sweepA > 0.01F) {
			int sy = barH + Math.round((h - barH * 2) * ((t * 2.2F) % 1.0F));
			g.fillGradient(0, Math.max(barH, sy - 18), w, sy, 0x00000000, argb(Math.round(70 * sweepA), CYAN));
			g.fill(0, sy, w, sy + 1, argb(Math.round(150 * sweepA), 0xFFFFFF));
		}

		// ── 부팅 문구 ──
		Font font = mc.font;
		int shown = lineCount(t);
		float textA = 1.0F - Mth.clamp((t - 0.72F) / 0.18F, 0.0F, 1.0F);
		if (textA > 0.01F) {
			int tx = Math.max(12, w / 2 - 110);
			int ty = h / 2 - 34;
			for (int i = 0; i < shown; i++) {
				boolean head = i == 0;
				int color = argb(Math.round(255 * textA), head ? 0xFFFFFF : CYAN);
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(tx, ty + i * (head ? 16 : 11));
				if (head) {
					pose.scale(1.6F, 1.6F);
				}
				g.text(font, Component.literal(lines[i]), 0, 0, color);
				pose.popMatrix();
			}
			// 깜빡이는 커서
			if (shown < lines.length && Math.floorMod((int) (now / 3), 2) == 0) {
				g.fill(tx, ty + 16 + shown * 11, tx + 5, ty + 16 + shown * 11 + 8, argb(Math.round(200 * textA), CYAN));
			}
		}

		// ── 지직거림 · 마지막 섬광 ──
		if (t < 0.6F) {
			glitch(g, now, 0.35F * (1.0F - t));
		}
		float flash = 1.0F - Mth.clamp(Math.abs(t - 0.82F) / 0.08F, 0.0F, 1.0F);
		if (flash > 0.01F) {
			g.fill(0, 0, w, h, argb(Math.round(180 * flash), 0xFFFFFF));
			Component mark = Component.literal("OVERBREAK");
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(w / 2.0F - font.width(mark) * 1.5F, h / 2.0F - 12);
			pose.scale(3.0F, 3.0F);
			g.text(font, mark, 0, 0, argb(Math.round(255 * flash), 0x0A0C12));
			pose.popMatrix();
		}
	}

	/** 지직거리는 조각 + 붉고 푸른 어긋남. */
	private static void glitch(GuiGraphicsExtractor g, double now, float strength) {
		if (strength <= 0.01F) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		RANDOM.setSeed((long) (now * 3.0) * 9176L);
		int n = 3 + Math.round(6 * strength);
		for (int i = 0; i < n; i++) {
			int y = RANDOM.nextInt(Math.max(1, h));
			int bh = 1 + RANDOM.nextInt(6);
			int off = RANDOM.nextInt(19) - 9;
			int color = RANDOM.nextBoolean() ? 0xE8363C : CYAN;
			g.fill(Math.max(0, off), y, w + Math.min(0, off), y + bh, argb(Math.round(40 + 60 * strength), color));
		}
	}

	private static int argb(int a, int rgb) {
		return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
