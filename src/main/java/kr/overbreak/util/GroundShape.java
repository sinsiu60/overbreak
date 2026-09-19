package kr.overbreak.util;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Transformation;
import kr.overbreak.skill.Effects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 바닥 판정 표시 — 파티클 대신 반투명 평면 모델로 부채꼴 · 원 · 호를 그립니다.
 *
 * 텍스트 디스플레이의 배경 사각형(ARGB 반투명)을 바닥에 눕혀 조각조각 이어 붙입니다.
 *   채움: 반지름 방향 띠를 나눠 사다리꼴에 가까운 사각형들로 덮음
 *   테두리: 바깥 호 + (부채꼴이면) 양쪽 변
 * 서버 엔티티라 모드가 없는 클라이언트에도 똑같이 보입니다.
 *
 * 조각 하나하나가 같은 중심 위치에 있고, 모양은 변환(transformation)으로만 잡습니다.
 * 그래서 도형을 돌리거나(setYaw) 반지름을 바꿔도(setRadius) 엔티티를 옮기지 않고 변환만 바꿉니다.
 */
public final class GroundShape {
	private static final float Y_OFFSET = 0.03F;
	private static final double EDGE = 0.09;
	private static final Quaternionf NO_ROT = new Quaternionf();

	private enum Kind { FILL, EDGE, CUSTOM }

	private static final class Part {
		final Display.TextDisplay display;
		final Kind kind;
		final double theta;
		final double rc;
		final double w;
		final double h;
		final boolean scaleThickness;
		final int layer;
		final double order;
		final int customColor;
		boolean shown = true;

		Part(Display.TextDisplay display, Kind kind, double theta, double rc, double w, double h, boolean scaleThickness,
			 int layer, double order, int customColor) {
			this.display = display;
			this.kind = kind;
			this.theta = theta;
			this.rc = rc;
			this.w = w;
			this.h = h;
			this.scaleThickness = scaleThickness;
			this.layer = layer;
			this.order = order;
			this.customColor = customColor;
		}
	}

	private final ServerLevel level;
	private final List<Part> parts = new ArrayList<>();
	private final double baseRadius;
	private Vec3 center;
	private float yaw;
	private double radiusScale = 1.0;
	private int fill;
	private int edge;
	private float alpha = 1.0F;
	private boolean discarded;

	private GroundShape(ServerLevel level, Vec3 center, float yaw, double baseRadius, int fill, int edge) {
		this.level = level;
		this.center = center;
		this.yaw = yaw;
		this.baseRadius = Math.max(0.01, baseRadius);
		this.fill = fill;
		this.edge = edge;
	}

	// ── 만들기 ──────────────────────────────────────────────

	/**
	 * 부채꼴 (arcDeg 360 이면 원판). yaw 는 가운데 방향 (마인크래프트 yaw).
	 * fill · edge 는 ARGB — 알파 0 이면 그 부분은 만들지 않습니다.
	 */
	public static GroundShape sector(ServerLevel level, Vec3 center, float yaw, double arcDeg, double radius, int fill, int edge) {
		GroundShape s = new GroundShape(level, center, yaw, radius, fill, edge);
		double arc = Math.toRadians(Math.min(arcDeg, 360.0));
		boolean full = arcDeg >= 359.9;
		if ((fill >>> 24) != 0) {
			s.addFill(arc, radius);
		}
		if ((edge >>> 24) != 0) {
			s.addArc(arc, radius, EDGE, Kind.EDGE, 0);
			if (!full) {
				s.addSide(-arc / 2.0, radius);
				s.addSide(arc / 2.0, radius);
			}
		}
		return s;
	}

	/** 테두리만 있는 원. */
	public static GroundShape ring(ServerLevel level, Vec3 center, double radius, int edge) {
		return sector(level, center, 0.0F, 360.0, radius, 0, edge);
	}

	/** 두께 있는 호 한 줄 (파면 등). */
	public static GroundShape arc(ServerLevel level, Vec3 center, float yaw, double arcDeg, double radius, int color, double thickness) {
		GroundShape s = new GroundShape(level, center, yaw, radius, 0, color);
		s.addArc(Math.toRadians(Math.min(arcDeg, 360.0)), radius, thickness, Kind.EDGE, 0);
		return s;
	}

	/** 중심에서 뻗는 금 (각도마다 조금씩 흔들림). 반지름을 바꿔도 따라 늘어납니다. */
	public GroundShape crack(double thetaDeg, double r0, double r1, double thickness, int color, int seed) {
		double len = 0.35;
		int n = Math.max(1, (int) Math.ceil((r1 - r0) / len));
		double step = (r1 - r0) / n;
		for (int i = 0; i < n; i++) {
			double jitter = Math.toRadians(((seed * 31 + i * 17) % 13) - 5.0);
			double rc = r0 + (i + 0.5) * step;
			add(Kind.CUSTOM, Math.toRadians(thetaDeg) + jitter / Math.max(1.0, rc), rc, thickness, step + 0.06, true, 2, 0.0, color);
		}
		return this;
	}

	private void addFill(double arc, double radius) {
		double bw = radius <= 4.0 ? 0.5 : radius <= 10.0 ? 1.0 : 2.0;
		for (double r0 = 0.0; r0 < radius - 1.0E-6; r0 += bw) {
			double r1 = Math.min(radius, r0 + bw);
			int k = Math.max((int) Math.ceil(arc * r1 / (bw * 1.2)), (int) Math.ceil(arc / (Math.PI / 3.0)));
			k = Math.max(1, k);
			double dt = arc / k;
			double w = 2.0 * r1 * Math.sin(dt / 2.0) + 0.02;
			for (int j = 0; j < k; j++) {
				double theta = -arc / 2.0 + (j + 0.5) * dt;
				add(Kind.FILL, theta, (r0 + r1) / 2.0, w, (r1 - r0) + 0.01, true, 0, (j + 0.5) / k, 0);
			}
		}
	}

	private void addArc(double arc, double radius, double thickness, Kind kind, int layer) {
		int k = Math.max(3, (int) Math.ceil(arc * radius / 0.35));
		double dt = arc / k;
		double w = 2.0 * radius * Math.sin(dt / 2.0) + 0.03;
		for (int j = 0; j < k; j++) {
			double theta = -arc / 2.0 + (j + 0.5) * dt;
			add(kind, theta, radius - thickness / 2.0, w, thickness, false, layer + 1, (j + 0.5) / k, 0);
		}
	}

	private void addSide(double theta, double radius) {
		add(Kind.EDGE, theta, radius / 2.0, EDGE, radius, true, 1, theta < 0 ? 0.0 : 1.0, 0);
	}

	private void add(Kind kind, double theta, double rc, double w, double h, boolean scaleThickness, int layer, double order, int custom) {
		Display.TextDisplay d = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
		if (d == null) {
			return;
		}
		Part p = new Part(d, kind, theta, rc, w, h, scaleThickness, layer, order, custom);
		d.setText(Component.literal(" "));
		d.setBackgroundColor(colorOf(p));
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.snapTo(center.x, center.y + Y_OFFSET, center.z, 0.0F, 0.0F);
		d.setTransformation(transform(p));
		d.setPosRotInterpolationDuration(1);
		level.addFreshEntity(d);
		parts.add(p);
	}

	// ── 바꾸기 ──────────────────────────────────────────────

	public void setYaw(float newYaw) {
		if (Math.abs(newYaw - yaw) < 0.5F) {
			return;
		}
		yaw = newYaw;
		parts.forEach(this::update);
	}

	public void moveTo(Vec3 newCenter) {
		if (newCenter.distanceToSqr(center) < 1.0E-4) {
			return;
		}
		center = newCenter;
		for (Part p : parts) {
			p.display.snapTo(center.x, center.y + Y_OFFSET, center.z, 0.0F, 0.0F);
		}
	}

	/** 0 → 1: 각도 순서대로 조각이 드러납니다 (금 조각은 항상 보임). */
	public void reveal(double progress) {
		for (Part p : parts) {
			boolean show = p.kind == Kind.CUSTOM || p.order <= progress + 1.0E-6;
			if (show != p.shown) {
				p.shown = show;
				update(p);
			}
		}
	}

	/** 반지름만 바꿉니다 (호 · 원은 두께 유지). */
	public void setRadius(double radius) {
		double scale = Math.max(0.01, radius) / baseRadius;
		if (Math.abs(scale - radiusScale) < 1.0E-3) {
			return;
		}
		radiusScale = scale;
		parts.forEach(this::update);
	}

	public void setColors(int newFill, int newEdge) {
		if (newFill == fill && newEdge == edge) {
			return;
		}
		fill = newFill;
		edge = newEdge;
		refreshColors();
	}

	/** 전체 투명도 배율 (사라지는 연출). */
	public void setAlpha(float a) {
		float clamped = Math.max(0.0F, Math.min(1.0F, a));
		if (Math.abs(clamped - alpha) < 0.01F) {
			return;
		}
		alpha = clamped;
		refreshColors();
	}

	public void discard() {
		if (discarded) {
			return;
		}
		discarded = true;
		parts.forEach(p -> p.display.discard());
		parts.clear();
	}

	public boolean isDiscarded() {
		return discarded;
	}

	/** 잠깐 보였다가 사라지는 도형 (평타 부채꼴 · 발동 순간 등). */
	public static void flash(ServerLevel level, Vec3 center, float yaw, double arcDeg, double radius, int fill, int edge, int ticks) {
		fadeOut(sector(level, center, yaw, arcDeg, radius, fill, edge), ticks, null);
	}

	/** 이미 있는 도형을 ticks 동안 흐리게 한 뒤 치웁니다. */
	public static void fadeOut(GroundShape shape, int time, @Nullable Object owner) {
		int ticks = kr.overbreak.core.tick.Ticks.of(time);
		Effects.add(new Effects.Active() {
			private int t;

			@Override
			public boolean tick() {
				t++;
				shape.setAlpha(1.0F - t / (float) ticks);
				if (t >= ticks) {
					shape.discard();
					return false;
				}
				return true;
			}

			@Override
			public void cancel() {
				shape.discard();
			}

			@Override
			public Object owner() {
				return owner;
			}
		});
	}

	// ── 내부 ────────────────────────────────────────────────

	private void refreshColors() {
		for (Part p : parts) {
			p.display.setBackgroundColor(colorOf(p));
		}
	}

	private int colorOf(Part p) {
		int base = switch (p.kind) {
			case FILL -> fill;
			case EDGE -> edge;
			case CUSTOM -> p.customColor;
		};
		int a = Math.round(((base >>> 24) & 255) * alpha);
		return (a << 24) | (base & 0xFFFFFF);
	}

	private void update(Part p) {
		p.display.setTransformationInterpolationDuration(1);
		p.display.setTransformationInterpolationDelay(0);
		p.display.setTransformation(transform(p));
	}

	/**
	 * 텍스트 " " 의 배경 사각형은 렌더러 기준 x -0.075~0.05 · y 0~0.25 입니다 (폭 0.125 · 높이 0.25).
	 * 크기 (8w, 4h) 로 늘리면 w x h 사각형이 되고, 중심은 (-0.1w, 0.5h) 에 옵니다.
	 * X 축 -90도로 눕혀(앞면이 위) Y 축으로 돌린 뒤, 사각형 중심이 원하는 지점에 오도록 옮깁니다.
	 */
	private Transformation transform(Part p) {
		double rc = p.rc * radiusScale;
		double w = p.w * radiusScale;
		double h = p.scaleThickness ? p.h * radiusScale : p.h;
		double a = Math.toRadians(yaw) + p.theta;
		Quaternionf rot = new Quaternionf().rotationY((float) (Math.PI - a)).rotateX((float) (-Math.PI / 2.0));
		Vector3f mid = rot.transform(new Vector3f((float) (-0.1 * w), (float) (0.5 * h), 0.0F));
		float x = (float) (-Math.sin(a) * rc) - mid.x;
		float y = p.layer * 0.004F - mid.y;
		float z = (float) (Math.cos(a) * rc) - mid.z;
		boolean visible = p.shown && !discarded;
		return new Transformation(new Vector3f(x, y, z), rot,
				visible ? new Vector3f((float) (8.0 * w), (float) (4.0 * h), 1.0F) : new Vector3f(0.0F, 0.0F, 0.0F), NO_ROT);
	}
}
