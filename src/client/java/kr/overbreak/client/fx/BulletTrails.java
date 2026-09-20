package kr.overbreak.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kr.overbreak.Overbreak;
import kr.overbreak.net.TracerPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 총알 궤적 · 총구 화염 — 서버가 알려 준 줄(net/TracerPayload)을 화면을 향한 납작한 2D 빛줄기 이미지로 그립니다.
 *
 *   모양: 텍스처 한 장 (textures/effect/bullet_trail.png) — 가운데가 밝고 가장자리 · 꼬리가 옅음. 줄을 따라 늘이고,
 *         줄을 축으로 카메라 쪽으로 돌려 어느 시점에서 봐도 평평한 이미지로 보입니다
 *   굵기: 카메라에서 멀수록 조금씩 굵혀 화면에서는 거의 같은 두께로 보이게
 *   발키리: 굵고 밝은 탄두가 초당 50칸으로 날아가고, 지나간 자리에 궤적 줄이 0.5초 남음
 *   파쇄권: 가는 산탄 줄이 순식간에 뻗었다가 꼬리부터 사라짐 (약 0.2초)
 *   시작점: 월드에 고정 — 본인 1인칭이면 쏜 순간 화면에 그려진 총구 자리를 월드 좌표로 바꿔 둡니다 (화면을 돌려도 따라오지 않음)
 *   총구 화염(발키리, 더해지는 빛 eyes 렌더 — 손 렌더 단계에서도 밝게): 쏠 때마다 0.1초 — 별 모양 불꽃 + 앞으로 뻗는 불줄기 (+ 남이 볼 때 연기 한 줌).
 *     1인칭 본인은 손에 든 총구에 붙여 그리고(ItemInHandRendererMixin → {@link #firstPersonFlash}), 나머지는 월드 총구 자리에 그림
 */
public final class BulletTrails {
	private static final Identifier TEXTURE = Overbreak.id("textures/effect/bullet_trail.png");
	private static final Identifier FLASH = Overbreak.id("textures/effect/muzzle_flash.png");
	/** 번개 빛줄기 — 폭 방향으로만 가운데가 밝은 텍스처 (길이 방향은 고름). */
	private static final Identifier LIGHTNING = Overbreak.id("textures/effect/lightning.png");
	private static final int LIFE = 5;
	/** 번개 (뇌신) — 지그재그 줄이 번쩍이며 사라지는 시간 (틱). */
	private static final int LIGHTNING_LIFE = 6;
	private static final int LIGHTNING_BIG_LIFE = 9;
	/** 발키리 탄두 속도 (칸/틱). */
	private static final double VK_SPEED = 2.5;
	/** 발키리 탄두 길이 (칸). */
	private static final double VK_BULLET = 1.4;
	/** 탄두가 닿은 뒤 궤적이 남는 시간 (틱). */
	private static final float VK_LINGER = 10.0F;
	/** 총구 화염 시간 (틱). */
	private static final float FLASH_TICKS = 2.0F;
	/** 1인칭 연사 포탑 총구 끝 (손 기준, 블록 — 주 손이 오른손일 때). */
	private static final Vector3f FP_MUZZLE = new Vector3f(-0.08F, 0.125F, -0.76F);

	private record Trail(int owner, Vec3 from, Vec3 to, int style, float born, boolean ownView) {
		int life() {
			if (style == TracerPayload.LIGHTNING) {
				return LIGHTNING_LIFE;
			}
			if (style == TracerPayload.LIGHTNING_BIG) {
				return LIGHTNING_BIG_LIFE;
			}
			return style == TracerPayload.VALKYRIE ? (int) Math.ceil(from.distanceTo(to) / VK_SPEED + VK_LINGER) + 1 : LIFE;
		}
	}

	private record Shot(float tick, float spin) {}

	private static final List<Trail> TRAILS = new ArrayList<>();
	private static final Map<Integer, Shot> SHOTS = new HashMap<>();
	private static float ticks;

	private BulletTrails() {}

	public static void receive(TracerPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 from = new Vec3(msg.fx(), msg.fy(), msg.fz());
		Vec3 to = new Vec3(msg.tx(), msg.ty(), msg.tz());
		boolean ownView = false;
		Camera camera = mc.gameRenderer.mainCamera();
		boolean lightning = msg.style() == TracerPayload.LIGHTNING || msg.style() == TracerPayload.LIGHTNING_BIG;
		// 번개는 창끝에서 쏜 것(눈 가까이서 시작)만 화면 속 창끝으로 옮김 — 섬전 · 벼락 · 감전 줄은 월드 자리 그대로
		boolean fromHand = !lightning || (mc.player != null && from.distanceTo(mc.player.getEyePosition()) < 1.5);
		if (fromHand && mc.player != null && msg.ownerId() == mc.player.getId() && mc.options.getCameraType().isFirstPerson() && camera.isInitialized()) {
			// 쏜 순간의 화면 속 총구 자리를 월드 좌표로 고정
			from = viewMuzzle(camera.position(), vec(camera.forwardVector()), vec(camera.upVector()), vec(camera.leftVector()), msg.style());
			ownView = true;
		}
		TRAILS.add(new Trail(msg.ownerId(), from, to, msg.style(), ticks, ownView));
		if (gunStyle(msg.style())) {
			SHOTS.put(msg.ownerId(), new Shot(ticks, (float) (Math.random() * 360.0)));
			// 연기: 남이 볼 때만 (본인 1인칭은 카메라 바로 앞이라 크고 검은 덩어리로 보임)
			if (mc.level != null && !ownView) {
				Vec3 dir = to.subtract(from).normalize();
				Vec3 at = from.add(dir.scale(0.25));
				mc.level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, dir.x * 0.03, dir.y * 0.03 + 0.01, dir.z * 0.03);
			}
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			SHOTS.clear();
			return;
		}
		ticks = (float) kr.overbreak.client.ClientClock.now();
		TRAILS.removeIf(t -> ticks - t.born > t.life());
		SHOTS.values().removeIf(s -> ticks - s.tick > FLASH_TICKS + 1);
	}

	/** 월드 렌더 제출 단계 — 포즈 원점이 카메라 위치입니다. */
	public static void submit(PoseStack pose, SubmitNodeCollector collector) {
		if (TRAILS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Vec3 cam = camera.position();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		int self = mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
		float now = ticks;
		float partialTime = kr.overbreak.client.ClientClock.partial(partial);
		List<Trail> snapshot = List.copyOf(TRAILS);
		collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(TEXTURE, true), (p, buffer) -> {
			for (Trail t : snapshot) {
				float age = now - t.born + partialTime;
				if (lightningStyle(t.style)) {
					continue;
				}
				if (gunStyle(t.style)) {
					if (t.style == TracerPayload.GUNSLINGER) {
						// 굴적의 깃털은 하늘색 권총입니다
						drawValkyrie(p, buffer, t.from, t.to, cam, age, 0x7FD4FF, 0x4FB8FF, 0xEAF9FF);
					} else {
						drawValkyrie(p, buffer, t.from, t.to, cam, age);
					}
				} else {
					drawPellet(p, buffer, t.from, t.to, cam, age);
				}
			}
		});
		// 번개: 더해지는 빛(eyes) — 어두운 곳 · 하늘을 배경으로도 환하게
		collector.submitCustomGeometry(pose, RenderTypes.eyes(LIGHTNING), (p, buffer) -> {
			for (Trail t : snapshot) {
				if (lightningStyle(t.style)) {
					drawLightning(p, buffer, t, cam, now - t.born + partialTime);
				}
			}
		});
		// 월드 총구 화염: 본인 1인칭은 손 렌더에서 총에 붙여 그리므로 제외
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLASH), (p, buffer) -> {
			for (Trail t : snapshot) {
				float age = now - t.born + partialTime;
				if (!gunStyle(t.style) || age >= FLASH_TICKS || (firstPerson && t.owner == self)) {
					continue;
				}
				Shot shot = SHOTS.get(t.owner);
				Vec3 dir = t.to.subtract(t.from).normalize();
				Vec3 center = t.from.add(dir.scale(0.2)).subtract(cam);
				flash(p, buffer, toF(center), toF(center.scale(-1.0)), toF(dir), 0.35F, age, shot == null ? 0.0F : shot.spin);
			}
		});
	}

	/**
	 * 1인칭 본인 총구 화염 — 손 렌더 기본 포즈(카메라 원점) 위에 그림.
	 * @param rel 기본 포즈 → 총 포즈 (ItemInHandRendererMixin 이 계산)
	 */
	public static void firstPersonFlash(PoseStack pose, SubmitNodeCollector collector, Matrix4f rel, int invert, int ownerId, float partial) {
		firstPersonFlash(pose, collector, rel, invert, ownerId, partial, FP_MUZZLE);
	}

	/** @param muzzleHand 손 기준 총구 끝 (주 손이 오른손일 때) — 총마다 다름 */
	public static void firstPersonFlash(PoseStack pose, SubmitNodeCollector collector, Matrix4f rel, int invert, int ownerId, float partial,
			Vector3f muzzleHand) {
		Shot shot = SHOTS.get(ownerId);
		if (shot == null) {
			return;
		}
		float age = ticks - shot.tick + kr.overbreak.client.ClientClock.partial(partial);
		if (age >= FLASH_TICKS || age < 0.0F) {
			return;
		}
		Vector3f muzzle = rel.transformPosition(new Vector3f(invert * muzzleHand.x, muzzleHand.y, muzzleHand.z));
		Vector3f forward = rel.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
		Vector3f center = new Vector3f(forward).mul(0.06F).add(muzzle);
		Vector3f toCamera = new Vector3f(center).negate();
		float spin = shot.spin;
		collector.submitCustomGeometry(pose, RenderTypes.eyes(FLASH),
				(p, buffer) -> flash(p, buffer, center, toCamera, forward, 0.16F, age, spin));
	}

	/** 본인 1인칭 화면 속 총구 자리 (카메라 기준). 발키리 = 오른쪽 아래 총구, 파쇄권 = 왼쪽 아래 철권포. */
	private static Vec3 viewMuzzle(Vec3 cam, Vec3 forward, Vec3 up, Vec3 left, int style) {
		if (style == TracerPayload.VALKYRIE) {
			return cam.add(forward.scale(0.95)).add(left.scale(-0.24)).add(up.scale(-0.2));
		}
		if (style == TracerPayload.SHERIFF) {
			return cam.add(forward.scale(0.9)).add(left.scale(-0.22)).add(up.scale(-0.17));
		}
		if (style == TracerPayload.GUNSLINGER) {
			// 쌀권총은 좌우로 번갈아 나가지만, 화면 속 불꽃은 가운데 조금 아래에서 다 받습니다
			return cam.add(forward.scale(0.9)).add(up.scale(-0.16));
		}
		if (style == TracerPayload.LIGHTNING || style == TracerPayload.LIGHTNING_BIG) {
			// 뇌신의 창 끝 (오른손, 위로 세운 창의 날)
			return cam.add(forward.scale(0.9)).add(left.scale(-0.3)).add(up.scale(0.05));
		}
		return cam.add(forward.scale(0.8)).add(left.scale(0.3)).add(up.scale(-0.26));
	}

	/**
	 * 총구 화염 한 번: 카메라를 보는 별 모양 불꽃(주황 + 흰 속불) + 총구 방향으로 뻗는 불줄기.
	 * 첫 틱에 가장 크고 밝다가 FLASH_TICKS 동안 줄며 사라짐.
	 */
	private static void flash(PoseStack.Pose pose, VertexConsumer buffer, Vector3f center, Vector3f toCamera, Vector3f forward,
			float size, float age, float spinDeg) {
		float k = 1.0F - Mth.clamp(age / FLASH_TICKS, 0.0F, 1.0F);
		if (k <= 0.0F) {
			return;
		}
		Vector3f n = new Vector3f(toCamera).normalize();
		Vector3f a = new Vector3f(n).cross(0.0F, 1.0F, 0.0F);
		if (a.lengthSquared() < 1.0E-4F) {
			a = new Vector3f(n).cross(1.0F, 0.0F, 0.0F);
		}
		a.normalize();
		Vector3f b = new Vector3f(n).cross(a).normalize();
		float spin = spinDeg * Mth.DEG_TO_RAD;
		Vector3f r = new Vector3f(a).mul(Mth.cos(spin)).add(new Vector3f(b).mul(Mth.sin(spin)));
		Vector3f u = new Vector3f(n).cross(r).normalize();
		float s = size * (0.7F + 0.5F * k);
		int outer = ARGB.color(Math.round(230.0F * k), 0xFFA530);
		int core = ARGB.color(Math.round(255.0F * k), 0xFFFBE8);
		billboard(pose, buffer, center, r, u, s, outer);
		billboard(pose, buffer, center, r, u, s * 0.45F, core);
		// 불줄기: 총구 방향으로 늘인 같은 텍스처 (화면을 향하게 옆으로 폄)
		Vector3f side = new Vector3f(forward).cross(n);
		if (side.lengthSquared() > 1.0E-5F) {
			side.normalize();
			Vector3f back = new Vector3f(forward).mul(-s * 0.3F).add(center);
			Vector3f front = new Vector3f(forward).mul(s * 2.6F).add(center);
			streak(pose, buffer, back, front, side, s * 0.55F, outer);
			streak(pose, buffer, back, new Vector3f(forward).mul(s * 1.6F).add(center), side, s * 0.25F, core);
		}
	}

	private static void billboard(PoseStack.Pose pose, VertexConsumer buffer, Vector3f c, Vector3f r, Vector3f u, float s, int color) {
		Vector3f p1 = new Vector3f(c).add(new Vector3f(r).mul(-s)).add(new Vector3f(u).mul(-s));
		Vector3f p2 = new Vector3f(c).add(new Vector3f(r).mul(s)).add(new Vector3f(u).mul(-s));
		Vector3f p3 = new Vector3f(c).add(new Vector3f(r).mul(s)).add(new Vector3f(u).mul(s));
		Vector3f p4 = new Vector3f(c).add(new Vector3f(r).mul(-s)).add(new Vector3f(u).mul(s));
		both(pose, buffer, p1, p2, p3, p4, color);
	}

	private static void streak(PoseStack.Pose pose, VertexConsumer buffer, Vector3f from, Vector3f to, Vector3f side, float w, int color) {
		Vector3f p1 = new Vector3f(from).add(new Vector3f(side).mul(-w));
		Vector3f p2 = new Vector3f(to).add(new Vector3f(side).mul(-w));
		Vector3f p3 = new Vector3f(to).add(new Vector3f(side).mul(w));
		Vector3f p4 = new Vector3f(from).add(new Vector3f(side).mul(w));
		both(pose, buffer, p1, p2, p3, p4, color);
	}

	/** 사각형 앞뒤 두 번 (면 버리기와 상관없이 보이게). uv 는 텍스처 한 장 전체. */
	private static void both(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4, int color) {
		vertex(pose, buffer, p1, color, 0.0F, 1.0F);
		vertex(pose, buffer, p2, color, 1.0F, 1.0F);
		vertex(pose, buffer, p3, color, 1.0F, 0.0F);
		vertex(pose, buffer, p4, color, 0.0F, 0.0F);
		vertex(pose, buffer, p4, color, 0.0F, 0.0F);
		vertex(pose, buffer, p3, color, 1.0F, 0.0F);
		vertex(pose, buffer, p2, color, 1.0F, 1.0F);
		vertex(pose, buffer, p1, color, 0.0F, 1.0F);
	}

	/** 발키리: 궤적 줄(총구 → 탄두, 옅은 노랑) + 탄두(굵고 밝은 짧은 빛). 탄두가 끝에 닿으면 궤적만 서서히 사라짐. */
	private static void drawValkyrie(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 cam, float age) {
		drawValkyrie(pose, buffer, from, to, cam, age, 0xFFC23A, 0xFFB020, 0xFFF8E0);
	}

	/** 꼬리 · 탄두 겉불 · 탄두 속불 색을 직접 정해 그립니다 (직업마다 다릅니다). */
	private static void drawValkyrie(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 cam, float age,
			int trailRgb, int glowRgb, int coreRgb) {
		double dist = from.distanceTo(to);
		if (dist < 1.0E-3) {
			return;
		}
		double travel = dist / VK_SPEED;
		// 쏜 틱에 이미 한 틱만큼 날아간 자리에서 시작 (패킷 지연 보정)
		double head = Math.min(dist, (age + 1.0) * VK_SPEED);
		double f = head / dist;
		float linger = (float) Math.max(0.0, age + 1.0 - travel);
		float trailAlpha = 0.8F * (1.0F - Mth.clamp(linger / VK_LINGER, 0.0F, 1.0F));
		if (trailAlpha > 0.0F) {
			quad(pose, buffer, from, from.lerp(to, f), cam, 0.03F, 0.006F, ARGB.color(Math.round(trailAlpha * 255.0F), trailRgb), 0.3F);
		}
		// 탄두: 끝에 닿은 뒤 1틱 동안 꺼짐
		float bulletAlpha = 1.0F - Mth.clamp(linger, 0.0F, 1.0F);
		if (bulletAlpha > 0.0F) {
			double tail = Math.max(0.0, head - VK_BULLET) / dist;
			Vec3 a = from.lerp(to, tail);
			Vec3 b = from.lerp(to, f);
			int alpha = Math.round(bulletAlpha * 255.0F);
			quad(pose, buffer, a, b, cam, 0.09F, 0.01F, ARGB.color(Math.round(alpha * 0.8F), glowRgb), 0.0F);
			quad(pose, buffer, a, b, cam, 0.038F, 0.0045F, ARGB.color(alpha, coreRgb), 0.0F);
		}
	}

	/**
	 * 번개 (뇌신): 시작점 → 끝점을 잘게 나눠 옆으로 어긋난 지그재그 줄. 2틱마다 모양이 다시 뒤틀리며 번쩍이고,
	 * 넓은 하늘색 빛 + 가는 흰 속줄기 두 겹으로 그림. 굵은 벼락은 곁가지 두 개가 더 뻗음.
	 */
	private static void drawLightning(PoseStack.Pose pose, VertexConsumer buffer, Trail t, Vec3 cam, float age) {
		boolean big = t.style == TracerPayload.LIGHTNING_BIG;
		float life = big ? LIGHTNING_BIG_LIFE : LIGHTNING_LIFE;
		float k = 1.0F - Mth.clamp(age / life, 0.0F, 1.0F);
		if (k <= 0.0F) {
			return;
		}
		// 번쩍임: 짝수 틱은 밝게, 홀수 틱은 조금 어둡게
		float flicker = ((int) age) % 2 == 0 ? 1.0F : 0.55F;
		float alpha = k * flicker;
		long seed = (long) (t.born * 20.0F) * 31L + (long) (t.from.x * 97.0) * 17L + (long) (t.from.z * 89.0) + ((int) age / 2) * 7919L;
		java.util.Random rnd = new java.util.Random(seed);
		double jag = big ? 0.9 : 0.28;
		List<Vec3> points = zigzag(t.from, t.to, big ? 1.4 : 0.55, jag, rnd);
		int glow = ARGB.color(Math.round(alpha * 255.0F), ARGB.scaleRGB(0x3AAEFF, alpha));
		int core = ARGB.color(Math.round(alpha * 255.0F), ARGB.scaleRGB(0xE8FBFF, alpha));
		// 굵기 = 기본 + 카메라 거리 비례 — 기본을 작게 두어 1인칭 창끝(카메라 코앞)에서 쐐기처럼 커지지 않게
		float glowW = big ? 0.45F : 0.03F;
		float glowGrow = big ? 0.006F : 0.014F;
		float coreW = big ? 0.13F : 0.01F;
		float coreGrow = big ? 0.003F : 0.005F;
		for (int i = 0; i + 1 < points.size(); i++) {
			quad(pose, buffer, points.get(i), points.get(i + 1), cam, glowW, glowGrow, glow, 1.0F);
			quad(pose, buffer, points.get(i), points.get(i + 1), cam, coreW, coreGrow, core, 1.0F);
		}
		if (big && points.size() > 4) {
			for (int b = 0; b < 2; b++) {
				Vec3 root = points.get(1 + rnd.nextInt(points.size() - 3));
				Vec3 dir = t.to.subtract(t.from).normalize();
				Vec3 end = root.add(dir.scale(2.5 + rnd.nextDouble() * 2.0))
						.add((rnd.nextDouble() - 0.5) * 4.0, 0.0, (rnd.nextDouble() - 0.5) * 4.0);
				List<Vec3> branch = zigzag(root, end, 0.8, 0.4, rnd);
				for (int i = 0; i + 1 < branch.size(); i++) {
					quad(pose, buffer, branch.get(i), branch.get(i + 1), cam, 0.22F, 0.003F, glow, 1.0F);
					quad(pose, buffer, branch.get(i), branch.get(i + 1), cam, 0.06F, 0.002F, core, 1.0F);
				}
			}
		}
	}

	/** 두 점 사이를 step 칸 간격으로 나누고 가운데 점들을 줄에 수직으로 ±jag 만큼 흔든 꺾은선. */
	private static List<Vec3> zigzag(Vec3 from, Vec3 to, double step, double jag, java.util.Random rnd) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		List<Vec3> out = new ArrayList<>();
		out.add(from);
		if (len < 1.0E-3) {
			out.add(to);
			return out;
		}
		Vec3 dir = d.scale(1.0 / len);
		Vec3 a = Math.abs(dir.y) < 0.9 ? dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 b = dir.cross(a).normalize();
		int n = Math.max(2, (int) Math.ceil(len / step));
		for (int i = 1; i < n; i++) {
			double f = i / (double) n;
			// 양 끝으로 갈수록 덜 흔들어 시작 · 끝점에 정확히 닿게
			double amp = jag * Math.sin(f * Math.PI);
			out.add(from.add(d.scale(f)).add(a.scale((rnd.nextDouble() - 0.5) * 2.0 * amp)).add(b.scale((rnd.nextDouble() - 0.5) * 2.0 * amp)));
		}
		out.add(to);
		return out;
	}

	/** 파쇄권 산탄: 쏜 틱에 끝의 2/3 까지, 다음 틱에 끝까지 닿고, 꼬리가 뒤따라 줄어들며 사라짐. */
	private static void drawPellet(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 cam, float age) {
		float head = Mth.clamp((age + 1.0F) / 1.5F, 0.0F, 1.0F);
		float tail = Mth.clamp((age - 0.5F) / 2.5F, 0.0F, 1.0F);
		if (tail >= head) {
			return;
		}
		float fade = 1.0F - Mth.clamp((age - 1.5F) / 3.0F, 0.0F, 1.0F);
		quad(pose, buffer, from.lerp(to, tail), from.lerp(to, head), cam, 0.008F, 0.0028F, ARGB.color(Math.round(fade * 255.0F), 0xE8F0FF), 0.0F);
	}

	/** 월드 두 점 사이 빛줄기 한 장. 굵기 = base + grow × 카메라 거리. u 는 꼬리(uStart) → 앞(1). */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 fromWorld, Vec3 toWorld, Vec3 cam,
			float base, float grow, int color, float uStart) {
		Vec3 a = fromWorld.subtract(cam);
		Vec3 b = toWorld.subtract(cam);
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		// 줄을 축으로 카메라를 향하도록: 줄 방향 x (줄 가운데 → 카메라)
		Vec3 side = dir.cross(a.add(b).scale(-0.5));
		if (side.lengthSqr() < 1.0E-10) {
			return;
		}
		side = side.normalize();
		double wa = base + grow * a.length();
		double wb = base + grow * b.length();
		Vec3 a1 = a.add(side.scale(wa));
		Vec3 a2 = a.subtract(side.scale(wa));
		Vec3 b1 = b.add(side.scale(wb));
		Vec3 b2 = b.subtract(side.scale(wb));
		// 앞뒤 두 번 (면 버리기와 상관없이 보이게)
		vertex(pose, buffer, a1, color, uStart, 0.0F);
		vertex(pose, buffer, a2, color, uStart, 1.0F);
		vertex(pose, buffer, b2, color, 1.0F, 1.0F);
		vertex(pose, buffer, b1, color, 1.0F, 0.0F);
		vertex(pose, buffer, b1, color, 1.0F, 0.0F);
		vertex(pose, buffer, b2, color, 1.0F, 1.0F);
		vertex(pose, buffer, a2, color, uStart, 1.0F);
		vertex(pose, buffer, a1, color, uStart, 0.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color, float u, float v) {
		buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f at, int color, float u, float v) {
		buffer.addVertex(pose, at.x, at.y, at.z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static Vec3 vec(Vector3fc v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static boolean lightningStyle(int style) {
		return style == TracerPayload.LIGHTNING || style == TracerPayload.LIGHTNING_BIG;
	}

	/** 탄두 궤적 · 총구 화염을 쓰는 총 (발키리 연사 포탑 · 보안관 리볼버). */
	private static boolean gunStyle(int style) {
		return style == TracerPayload.VALKYRIE || style == TracerPayload.SHERIFF || style == TracerPayload.GUNSLINGER;
	}

	private static Vector3f toF(Vec3 v) {
		return new Vector3f((float) v.x, (float) v.y, (float) v.z);
	}
}
