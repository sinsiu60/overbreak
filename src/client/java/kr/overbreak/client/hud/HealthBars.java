package kr.overbreak.client.hud;

import java.util.HashMap;
import java.util.Map;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 오버워치 방식 머리 위 체력바 — 화면 고정 크기의 기울어진 칸 막대가 캐릭터 머리 위를 따라다닙니다.
 *
 *   한 칸 = 체력 25 (칸이 20개를 넘으면 전체를 20칸으로 나눔)
 *   본인 체력바는 그리지 않음 (왼쪽 아래 PlayerHealthHud)
 *   색: 다른 플레이어 · 생명체 빨간색 · 추가체력(흡수 체력) 파란색은 체력 뒤에 이어 붙음
 *   방금 잃은 체력은 흰 잔상으로 남았다가 줄어듦
 *   벽에 가려지면 숨김, 40칸까지 표시 (30칸부터 흐려짐)
 */
public final class HealthBars {
	private static final double RANGE = 40.0;
	private static final double FADE_FROM = 30.0;
	private static final int WIDTH = 44;
	private static final int HEIGHT = 5;
	private static final float SKEW = -0.35F;
	private static final float HP_PER_SEGMENT = 25.0F;
	private static final int MAX_SEGMENTS = 20;

	private static final int ENEMY = 0xFFFF4545;
	private static final int EXTRA = 0xFF3FA2FF;
	private static final int CHIP = 0xC8FFFFFF;
	private static final int EMPTY = 0x80282A30;
	private static final int FRAME = 0xA0000000;

	/** 엔티티 id → 잔상 체력 (천천히 현재 체력으로 내려감). */
	private static final Map<Integer, Float> LAG = new HashMap<>();

	private HealthBars() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			LAG.clear();
			return;
		}
		LAG.entrySet().removeIf(entry -> {
			if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity e) || !e.isAlive()) {
				return true;
			}
			float health = e.getHealth();
			float lag = entry.getValue();
			entry.setValue(health >= lag ? health : Math.max(health, lag - Math.max(0.3F, (lag - health) * 0.18F)));
			return false;
		});
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (!InputMode.active() || mc.level == null || mc.player == null) {
			return;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 camPos = camera.position();
		float partial = dt.getGameTimeDeltaPartialTick(false);

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity e) || e instanceof ArmorStand || !e.isAlive() || e.isInvisible()) {
				continue;
			}
			if (e == mc.player) {
				continue;
			}
			Vec3 top = new Vec3(Mth.lerp(partial, e.xo, e.getX()),
					Mth.lerp(partial, e.yo, e.getY()) + e.getBbHeight() + 0.55,
					Mth.lerp(partial, e.zo, e.getZ()));
			double dist = top.distanceTo(camPos);
			if (dist > RANGE) {
				continue;
			}
			Vector4f clip = new Vector4f((float) (top.x - camPos.x), (float) (top.y - camPos.y), (float) (top.z - camPos.z), 1.0F)
					.mul(viewProjection);
			if (clip.w <= 0.05F) {
				continue;
			}
			float sx = (clip.x / clip.w + 1.0F) * 0.5F * g.guiWidth();
			float sy = (1.0F - clip.y / clip.w) * 0.5F * g.guiHeight();
			if (sx < -WIDTH || sx > g.guiWidth() + WIDTH || sy < -HEIGHT || sy > g.guiHeight() + HEIGHT) {
				continue;
			}
			if (mc.level.clip(new ClipContext(camPos, top, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			float fade = dist > FADE_FROM ? 1.0F - (float) ((dist - FADE_FROM) / (RANGE - FADE_FROM)) : 1.0F;
			bar(g, e, sx, sy, ENEMY, fade);
		}
	}

	private static void bar(GuiGraphicsExtractor g, LivingEntity e, float sx, float sy, int healthColor, float fade) {
		float health = Math.max(0.0F, e.getHealth());
		float max = Math.max(1.0F, e.getMaxHealth());
		float extra = Math.max(0.0F, e.getAbsorptionAmount());
		float lag = Math.max(health, LAG.computeIfAbsent(e.getId(), k -> health));
		float total = max + extra;

		float unit = HP_PER_SEGMENT;
		int n = Mth.ceil(total / unit);
		if (n > MAX_SEGMENTS) {
			n = MAX_SEGMENTS;
			unit = total / n;
		}
		n = Math.max(1, n);
		float segW = (WIDTH - (n - 1)) / (float) n;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(Math.round(sx - WIDTH / 2.0F), Math.round(sy - HEIGHT));
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.fill(-1, -1, WIDTH + 1, HEIGHT + 1, alpha(FRAME, fade));
		for (int i = 0; i < n; i++) {
			int x0 = Math.round(i * (segW + 1.0F));
			int x1 = Math.max(x0 + 1, Math.round(i * (segW + 1.0F) + segW));
			float from = i * unit;
			float to = from + unit;
			g.fill(x0, 0, x1, HEIGHT, alpha(EMPTY, fade));
			span(g, x0, x1, from, to, 0.0F, health, alpha(healthColor, fade));
			span(g, x0, x1, from, to, health, Math.min(lag, max), alpha(CHIP, fade * 0.8F));
			span(g, x0, x1, from, to, max, max + extra, alpha(EXTRA, fade));
		}
		pose.popMatrix();
	}

	/** 칸 [from, to) 중 체력 구간 [a, b) 에 해당하는 부분을 칠합니다. */
	private static void span(GuiGraphicsExtractor g, int x0, int x1, float from, float to, float a, float b, int color) {
		float lo = Math.max(from, a);
		float hi = Math.min(to, b);
		if (hi <= lo) {
			return;
		}
		int w = x1 - x0;
		int l = x0 + Math.round((lo - from) / (to - from) * w);
		int r = x0 + Math.round((hi - from) / (to - from) * w);
		if (r > l) {
			g.fill(l, 0, r, HEIGHT, color);
		}
	}

	private static int alpha(int argb, float fade) {
		int a = Math.round(((argb >>> 24) & 255) * Mth.clamp(fade, 0.0F, 1.0F));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
