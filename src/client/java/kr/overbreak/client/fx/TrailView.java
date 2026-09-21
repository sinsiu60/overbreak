package kr.overbreak.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kr.overbreak.classes.gunslinger.TrailRelease;
import kr.overbreak.client.ClientClock;
import kr.overbreak.net.TrailPayload;
import kr.overbreak.net.TrailPhasePayload;
import kr.overbreak.util.Fx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 궤적 해방 (궤적의 깃털 궁극기) — 공중에 남은 궤적을 그리는 자리. 서버가 보낸 궤적 · 단계만 그립니다 (판정은 서버).
 *
 *   궤적     : 파티클이 아니라 선 도형 (25줄 × 5명을 5초 동안 파티클로 두면 프레임이 무너짐). 벽 · 지형에 가려짐
 *              일반 궤적 두께 0.08칸 하늘색 · 치명 궤적 0.14칸 밝은 하늘색 + 흰 속줄. 적 팀 궤적도 색은 같음
 *              생기는 순간 총구에서 끝점까지 0.05초에 그어지고, 선을 따라 작은 빛이 천천히 흘러감 (선당 1~2개, 도형)
 *   예고     : 0.5초 동안 흰색으로 세 번 번쩍 (점점 빠르게) · 미세하게 떨림 · 점점 높아지는 차징음 (궤적 한가운데서, 가까울수록 크게)
 *   폭발     : 선을 따라 하늘색 파편 · 깃털 (치명 궤적은 두 배 + 흰 섬광), 궤적이 엇갈리는 매듭마다 작은 빛 폭발,
 *              유리가 한꺼번에 깨지는 소리 + 낮은 폭발음 — 궤적 수와 상관없이 한 번
 *   소멸(사망): 폭발 없이 아래로 흘러내리며 흩어짐 (0.3초) · 힘 빠지는 하강음
 *   HUD(본인): 궤적 수 · 치명 궤적 수 (12 / 25 · 치명 7), 발동 1초 뒤부터 "Q — 해방", 화면 가장자리 하늘빛
 */
public final class TrailView {
	/** 그어지는 시간 · 폭발 뒤 사라지는 시간 · 소멸 시간 (1/20초 단위). */
	private static final float DRAW = 1.0F;
	private static final float BURST = 3.0F;
	private static final float VANISH = 6.0F;

	private record Line(int index, Vec3 start, Vec3 end, boolean crit, float born) {}

	private static final class Cast {
		final List<Line> lines = new ArrayList<>();
		int phase = TrailPhasePayload.COLLECT;
		float castAt;
		float phaseAt;
		/** 예고 차징음을 몇 번 울렸는가. */
		int chimes;
	}

	private static final Map<Integer, Cast> CASTS = new HashMap<>();
	private static float now;

	private TrailView() {}

	// ── 수신 ─────────────────────────────────────────────

	public static void receive(TrailPayload msg) {
		Cast c = live(msg.casterId(), msg.age());
		for (Line l : c.lines) {
			if (l.index == msg.index()) {
				return;
			}
		}
		c.lines.add(new Line(msg.index(), msg.start(), msg.end(), msg.crit(), now - msg.age()));
	}

	public static void phase(TrailPhasePayload msg) {
		switch (msg.phase()) {
			case TrailPhasePayload.COLLECT -> {
				Cast c = live(msg.casterId(), msg.elapsed());
				c.castAt = now - msg.elapsed();
			}
			case TrailPhasePayload.TELEGRAPH -> {
				Cast c = live(msg.casterId(), 0);
				c.phase = TrailPhasePayload.TELEGRAPH;
				c.phaseAt = now - msg.elapsed();
			}
			case TrailPhasePayload.DETONATE -> {
				Cast c = CASTS.get(msg.casterId());
				if (c != null && c.phase < TrailPhasePayload.DETONATE) {
					c.phase = TrailPhasePayload.DETONATE;
					c.phaseAt = now;
					burst(c);
				}
			}
			case TrailPhasePayload.VANISH -> {
				Cast c = CASTS.get(msg.casterId());
				if (c != null && c.phase < TrailPhasePayload.DETONATE) {
					c.phase = TrailPhasePayload.VANISH;
					c.phaseAt = now;
					if (!c.lines.isEmpty()) {
						sound(SoundEvents.BEACON_DEACTIVATE, center(c), 1.0F, 0.6F);
					}
				}
			}
			default -> {
			}
		}
	}

	/** 진행 중인 시전 (끝난 것이면 새로). */
	private static Cast live(int id, int age) {
		Cast c = CASTS.get(id);
		if (c == null || c.phase >= TrailPhasePayload.DETONATE) {
			c = new Cast();
			c.castAt = now - age;
			CASTS.put(id, c);
		}
		return c;
	}

	// ── 틱 ───────────────────────────────────────────────

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			CASTS.clear();
			return;
		}
		now = (float) ClientClock.now();
		CASTS.values().removeIf(c -> c.phase == TrailPhasePayload.DETONATE && now - c.phaseAt > BURST
				|| c.phase == TrailPhasePayload.VANISH && now - c.phaseAt > VANISH);
		// 예고 차징음 — 0.5초 동안 다섯 번, 점점 높게
		for (Cast c : CASTS.values()) {
			if (c.phase != TrailPhasePayload.TELEGRAPH || c.lines.isEmpty()) {
				continue;
			}
			float x = (now - c.phaseAt) / TrailRelease.TELEGRAPH;
			while (c.chimes < 5 && x >= c.chimes * 0.2F) {
				float pitch = 1.0F + c.chimes * 0.25F;
				sound(SoundEvents.NOTE_BLOCK_CHIME.value(), center(c), 1.6F, pitch);
				sound(SoundEvents.AMETHYST_BLOCK_CHIME, center(c), 1.0F, pitch);
				c.chimes++;
			}
		}
	}

	// ── 폭발 ─────────────────────────────────────────────

	private static void burst(Cast c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || c.lines.isEmpty()) {
			return;
		}
		RandomSource r = mc.level.getRandom();
		ParticleOptions sky = Fx.dust(Fx.rgb(0.55, 0.85, 1.0), 1.1F);
		ParticleOptions feather = new ItemParticleOption(ParticleTypes.ITEM, Items.FEATHER);
		for (Line l : c.lines) {
			Vec3 d = l.end.subtract(l.start);
			int n = Mth.clamp((int) (d.length() * 1.5), 3, 20) * (l.crit ? 2 : 1);
			for (int i = 0; i < n; i++) {
				Vec3 at = l.start.add(d.scale(r.nextDouble()));
				double vx = (r.nextDouble() - 0.5) * 0.25;
				double vy = (r.nextDouble() - 0.3) * 0.25;
				double vz = (r.nextDouble() - 0.5) * 0.25;
				mc.level.addParticle(i % 4 == 0 ? feather : sky, at.x, at.y, at.z, vx, vy, vz);
				if (l.crit && i % 3 == 0) {
					mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, vx * 0.6, vy * 0.6, vz * 0.6);
				}
			}
		}
		// 궤적이 엇갈리는 매듭 — 작은 빛 폭발
		int knots = 0;
		for (int i = 0; i < c.lines.size() && knots < 40; i++) {
			for (int j = i + 1; j < c.lines.size() && knots < 40; j++) {
				Vec3 k = knot(c.lines.get(i), c.lines.get(j));
				if (k != null) {
					knots++;
					mc.level.addParticle(ParticleTypes.FIREWORK, k.x, k.y, k.z, 0, 0, 0);
					for (int n = 0; n < 5; n++) {
						mc.level.addParticle(ParticleTypes.END_ROD, k.x, k.y, k.z,
								(r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2);
					}
				}
			}
		}
		Vec3 m = center(c);
		sound(SoundEvents.GLASS_BREAK, m, 2.0F, 1.1F);
		sound(SoundEvents.GENERIC_EXPLODE.value(), m, 1.2F, 0.6F);
	}

	/** 두 궤적이 0.4칸 안으로 스치는 자리 (없으면 null). */
	private static Vec3 knot(Line a, Line b) {
		Vec3 d1 = a.end.subtract(a.start);
		Vec3 d2 = b.end.subtract(b.start);
		Vec3 r0 = a.start.subtract(b.start);
		double aa = d1.dot(d1);
		double ee = d2.dot(d2);
		double f = d2.dot(r0);
		double c = d1.dot(r0);
		double bb = d1.dot(d2);
		double denom = aa * ee - bb * bb;
		double s = denom > 1.0E-6 ? Mth.clamp((bb * f - c * ee) / denom, 0.0, 1.0) : 0.0;
		double t = Mth.clamp((bb * s + f) / ee, 0.0, 1.0);
		s = Mth.clamp((bb * t - c) / aa, 0.0, 1.0);
		Vec3 p1 = a.start.add(d1.scale(s));
		Vec3 p2 = b.start.add(d2.scale(t));
		// 같은 총구에서 연달아 나간 줄끼리 시작점이 겹치는 것은 매듭으로 치지 않음
		if (s < 0.05 && t < 0.05) {
			return null;
		}
		return p1.distanceTo(p2) < 0.4 ? p1.add(p2).scale(0.5) : null;
	}

	private static Vec3 center(Cast c) {
		Vec3 sum = Vec3.ZERO;
		for (Line l : c.lines) {
			sum = sum.add(l.start.add(l.end).scale(0.5));
		}
		return c.lines.isEmpty() ? sum : sum.scale(1.0 / c.lines.size());
	}

	private static void sound(SoundEvent e, Vec3 at, float volume, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		mc.getSoundManager().play(new SimpleSoundInstance(e, SoundSource.PLAYERS, volume, pitch, mc.level.getRandom(), at.x, at.y, at.z));
	}

	// ── 그리기 ───────────────────────────────────────────

	/** 월드 렌더 제출 — 포즈 원점이 카메라 위치. */
	public static void submit(PoseStack pose, SubmitNodeCollector collector) {
		if (CASTS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 cam = camera.position();
		float t = now + ClientClock.partial(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		List<Cast> snapshot = List.copyOf(CASTS.values());
		collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(BulletTrails.TEXTURE, true), (p, buffer) -> {
			for (Cast c : snapshot) {
				for (Line l : c.lines) {
					draw(p, buffer, c, l, cam, t);
				}
			}
		});
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Cast c, Line l, Vec3 cam, float t) {
		float age = t - l.born;
		if (age < 0.0F) {
			return;
		}
		Vec3 a = l.start;
		Vec3 b = l.start.lerp(l.end, Mth.clamp(age / DRAW, 0.0F, 1.0F));
		float alpha = 1.0F;
		float white = 0.0F;
		float widen = 1.0F;
		if (c.phase == TrailPhasePayload.TELEGRAPH) {
			float x = Mth.clamp((t - c.phaseAt) / TrailRelease.TELEGRAPH, 0.0F, 1.0F);
			// 세 번 번쩍 — 점점 빠르게 (0.1 · 0.5 · 0.8 무렵)
			white = Math.max(flash(x, 0.12F, 0.12F), Math.max(flash(x, 0.52F, 0.09F), flash(x, 0.82F, 0.07F)));
			// 미세한 떨림
			float j = 0.035F * x;
			Vec3 shake = new Vec3(Mth.sin(t * 7.3F + l.index * 1.7F), Mth.sin(t * 9.1F + l.index * 2.3F), Mth.sin(t * 8.2F + l.index * 0.9F)).scale(j);
			a = a.add(shake);
			b = b.add(shake);
		} else if (c.phase == TrailPhasePayload.DETONATE) {
			float x = Mth.clamp((t - c.phaseAt) / BURST, 0.0F, 1.0F);
			white = 1.0F;
			widen = 1.0F + x;
			alpha = 1.0F - x;
		} else if (c.phase == TrailPhasePayload.VANISH) {
			float x = Mth.clamp((t - c.phaseAt) / VANISH, 0.0F, 1.0F);
			// 아래로 흘러내리며 흩어짐 — 끝으로 갈수록 더 처짐
			a = a.subtract(0, 1.4 * x * x, 0);
			b = b.subtract(0, 2.0 * x * x, 0);
			alpha = 1.0F - x;
		}
		if (alpha <= 0.01F) {
			return;
		}
		int glowRgb = mix(l.crit ? 0x7FD4FF : 0x4FB8FF, 0xFFFFFF, white);
		int coreRgb = mix(l.crit ? 0xF4FCFF : 0x9FDFFF, 0xFFFFFF, white);
		float glowW = (l.crit ? 0.14F : 0.08F) * widen;
		float coreW = (l.crit ? 0.05F : 0.025F) * widen;
		BulletTrails.quad(pose, buffer, a, b, cam, glowW, 0.0F, ARGB.color(Math.round(255 * alpha * (l.crit ? 0.75F : 0.5F)), glowRgb), 0.3F);
		BulletTrails.quad(pose, buffer, a, b, cam, coreW, 0.0F, ARGB.color(Math.round(255 * alpha * 0.95F), coreRgb), 0.3F);
		// 선을 따라 천천히 흐르는 빛 (치명 궤적은 둘)
		if (age >= DRAW && c.phase <= TrailPhasePayload.TELEGRAPH) {
			for (int k = 0; k < (l.crit ? 2 : 1); k++) {
				float f = ((age * 0.05F + l.index * 0.37F + k * 0.5F) % 1.0F);
				Vec3 d = b.subtract(a);
				double len = d.length();
				if (len < 0.5) {
					break;
				}
				double seg = 0.45 / len;
				Vec3 s0 = a.add(d.scale(f));
				Vec3 s1 = a.add(d.scale(Math.min(1.0, f + seg)));
				BulletTrails.quad(pose, buffer, s0, s1, cam, coreW * 2.4F, 0.0F, ARGB.color(Math.round(230 * alpha), 0xFFFFFF), 0.0F);
			}
		}
	}

	private static float flash(float x, float at, float width) {
		return Mth.clamp(1.0F - Math.abs(x - at) / width, 0.0F, 1.0F);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	// ── HUD (본인) ───────────────────────────────────────

	public static void hud(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Cast c = CASTS.get(mc.player.getId());
		if (c == null || c.phase > TrailPhasePayload.TELEGRAPH) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		// 1인칭: 화면 가장자리에 하늘빛 비네트 (약하게)
		if (mc.options.getCameraType().isFirstPerson()) {
			for (int i = 0; i < 6; i++) {
				int a = ARGB.color(Math.max(0, 0x2A - i * 7), 0x7FD4FF);
				int band = 4 + i * 4;
				g.fill(0, i * 4, w, band, a);
				g.fill(0, h - band, w, h - i * 4, a);
				g.fill(i * 4, band, band, h - band, a);
				g.fill(w - band, band, w - i * 4, h - band, a);
			}
		}
		int crit = 0;
		for (Line l : c.lines) {
			if (l.crit) {
				crit++;
			}
		}
		String text = c.lines.size() + " / " + TrailRelease.MAX_TRAILS + (crit > 0 ? "  ·  치명 " + crit : "");
		int y = h / 2 + 30;
		g.centeredText(mc.font, Component.literal(text), w / 2, y, 0xFF9FE2FF);
		if (c.phase == TrailPhasePayload.COLLECT && now - c.castAt >= TrailRelease.EARLY) {
			float pulse = 0.6F + 0.4F * Mth.sin(now * 0.4F);
			g.centeredText(mc.font, Component.literal("Q — 해방"), w / 2, y + 11, ARGB.color(Math.round(255 * pulse), 0xFFFFFF));
		}
	}

	/** 시험용: 이 시전자의 궤적 수 · 단계. */
	public static int count(int casterId) {
		Cast c = CASTS.get(casterId);
		return c == null ? 0 : c.lines.size();
	}

	public static int phaseOf(int casterId) {
		Cast c = CASTS.get(casterId);
		return c == null ? -1 : c.phase;
	}
}
