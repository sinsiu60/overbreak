package kr.overbreak.client.hud;

import java.util.ArrayList;
import java.util.List;

import kr.overbreak.client.input.InputMode;
import kr.overbreak.net.HurtPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치식 피격 피드백 (본인 화면) — 바닐라의 화면 기울기 대신.
 *
 *   맞은 방향에 붉은 호(弧)가 조준점 둘레에 잠깐 떠오릅니다. 세게 맞을수록 두껍고 오래 남습니다
 *   같은 방향에서 연달아 맞으면 호 하나가 진해질 뿐, 화면이 덜컹거리지 않습니다
 *   화면 가장자리는 최근에 받은 피해만큼 붉게 물들고, 체력이 낮으면 계속 옅게 깔립니다
 *   방향을 모르는 피해(낙하 등)는 사방이 함께 번쩍입니다
 */
public final class DamageFeedback {
	/** 표시 하나가 남아 있는 시간 (1/20초 단위). */
	private static final float MARK_LIFE = 22.0F;
	/** 이 시간 안에 같은 방향에서 또 맞으면 새로 그리지 않고 진하게만 합니다. */
	private static final float MERGE_TIME = 6.0F;
	private static final float MERGE_ANGLE = 25.0F;
	private static final int MAX_MARKS = 6;
	private static final int RED = 0xFF3A38;
	private static final int DEEP = 0xB01010;
	/** 이 피해에서 표시가 가장 두꺼워집니다. */
	private static final float FULL_DAMAGE = 60.0F;
	/** 체력이 이 비율 아래면 가장자리가 계속 붉게 깔립니다. */
	private static final float LOW = 0.35F;

	private static final class Mark {
		float angle;
		float weight;
		float at;
		boolean directed;
	}

	private static final List<Mark> MARKS = new ArrayList<>();
	private static float ticks;

	private DamageFeedback() {}

	public static void receive(HurtPayload msg) {
		float w = Mth.clamp(msg.amount() / FULL_DAMAGE, 0.12F, 1.0F);
		for (Mark m : MARKS) {
			boolean same = m.directed == msg.directed()
					&& (!msg.directed() || Math.abs(Mth.degreesDifference(m.angle, msg.angle())) < MERGE_ANGLE);
			if (same && ticks - m.at < MERGE_TIME) {
				// 연사에 맞는 동안에는 표시가 늘어나지 않고 진해지기만 합니다
				m.weight = Math.min(1.0F, m.weight + w * 0.6F);
				m.at = ticks;
				m.angle = msg.directed() ? msg.angle() : m.angle;
				return;
			}
		}
		Mark m = new Mark();
		m.angle = msg.angle();
		m.weight = w;
		m.at = ticks;
		m.directed = msg.directed();
		MARKS.add(m);
		while (MARKS.size() > MAX_MARKS) {
			MARKS.removeFirst();
		}
	}

	public static void tick(Minecraft mc) {
		ticks = (float) kr.overbreak.client.ClientClock.now();
		if (mc.level == null) {
			MARKS.clear();
			return;
		}
		MARKS.removeIf(m -> ticks - m.at > MARK_LIFE);
	}

	/** 시험용: 지금 떠 있는 표시 수. */
	public static int markCount() {
		return MARKS.size();
	}

	public static void clear() {
		MARKS.clear();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || !InputMode.active()) {
			return;
		}
		float t = ticks + kr.overbreak.client.ClientClock.partial(dt.getGameTimeDeltaPartialTick(false));
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = h / 2;

		// ── 가장자리 붉은 기운 ──
		float recent = 0.0F;
		for (Mark m : MARKS) {
			recent = Math.max(recent, m.weight * fade(m, t));
		}
		float low = Mth.clamp(1.0F - p.getHealth() / Math.max(1.0F, p.getMaxHealth()) / LOW, 0.0F, 1.0F);
		float edge = Math.max(recent * 0.45F, low * (0.3F + 0.1F * Mth.sin(t * 0.3F)));
		if (edge > 0.01F) {
			vignette(g, w, h, edge);
		}

		// ── 방향 표시 ──
		float radius = Math.min(w, h) * 0.055F + 14.0F;
		for (Mark m : MARKS) {
			float k = fade(m, t);
			if (k <= 0.01F) {
				continue;
			}
			float alpha = Mth.clamp(k * (0.45F + 0.55F * m.weight), 0.0F, 1.0F);
			float thickness = 2.0F + 2.6F * m.weight;
			// 처음 뜰 때 바깥에서 안으로 들어옵니다
			float pop = 1.0F - Mth.clamp((t - m.at) / 4.0F, 0.0F, 1.0F);
			float r = radius + 7.0F * pop;
			if (m.directed) {
				float bearing = Mth.wrapDegrees(m.angle - p.getYRot());
				arc(g, cx, cy, r, bearing, 13.0F + 7.0F * m.weight, thickness, alpha);
			} else {
				for (float bearing = 0.0F; bearing < 360.0F; bearing += 90.0F) {
					arc(g, cx, cy, r, bearing, 12.0F, thickness, alpha * 0.8F);
				}
			}
		}
	}

	private static float fade(Mark m, float t) {
		float age = (t - m.at) / MARK_LIFE;
		if (age >= 1.0F) {
			return 0.0F;
		}
		// 처음 두 틱은 가득, 그 뒤로 부드럽게 빠짐
		float k = 1.0F - Mth.clamp((age - 0.1F) / 0.9F, 0.0F, 1.0F);
		return k * k;
	}

	/** 조준점 둘레의 호 한 조각 — 작은 막대를 각도마다 돌려 그립니다. */
	private static void arc(GuiGraphicsExtractor g, int cx, int cy, float radius, float bearing, float halfSpan,
							float thickness, float alpha) {
		int steps = 11;
		float span = halfSpan * 2.0F;
		// 조각이 딱 붙도록 폭을 호 길이에 맞춥니다
		int segW = Mth.ceil((float) (Math.toRadians(span) * radius) / (steps - 1)) + 1;
		int half = Math.max(1, Mth.ceil(thickness / 2.0F));
		Matrix3x2fStack pose = g.pose();
		for (int i = 0; i < steps; i++) {
			float a = bearing - halfSpan + span * i / (steps - 1);
			// 가운데가 가장 진하고 끝으로 갈수록 옅어짐
			float edge = 1.0F - Math.abs(i - (steps - 1) / 2.0F) / ((steps - 1) / 2.0F);
			int color = argb(Math.round(255 * alpha * (0.35F + 0.65F * edge)), i % 2 == 0 ? RED : DEEP);
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.rotate((float) Math.toRadians(a));
			g.fill(-segW / 2, Math.round(-radius) - half, segW - segW / 2, Math.round(-radius) + half, color);
			pose.popMatrix();
		}
	}

	private static void vignette(GuiGraphicsExtractor g, int w, int h, float k) {
		int bands = 12;
		int depth = Math.min(w, h) / 4;
		for (int i = 0; i < bands; i++) {
			float s = 1.0F - i / (float) bands;
			int color = argb(Math.round(120.0F * s * s * k), DEEP);
			int a = i * depth / bands;
			int b = (i + 1) * depth / bands;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}
	}

	private static int argb(int alpha, int rgb) {
		return (Mth.clamp(alpha, 0, 255) << 24) | rgb;
	}
}
