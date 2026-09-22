package kr.overbreak.client.fx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kr.overbreak.classes.gunslinger.TrailPursuit;
import kr.overbreak.client.ClientClock;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HomingPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 궤적 추격 (궤적의 깃털 궁극기) — 클라이언트 쪽 연출.
 *
 *   유도 탄 곡선 : 서버가 보낸 출발점 · 방향 · 대상으로 서버와 같은 규칙(초당 60칸 · 초당 720° 선회 · 블록에 소멸 · 0.4초)으로
 *                 탄을 날리며 지나간 길을 굵고 밝은 하늘색 곡선으로 그림 — 각 점은 0.25초 뒤 사라짐. 명중 자리에 작은 섬광.
 *                 명중 판정은 서버가 합니다 (여기는 보이기만). 모든 사람에게 보임
 *   비행 바람    : 궁극기로 나는 사람마다 낮은 바람 소리 (볼륨 0.3) — 끝나면 멈춤
 *   HUD (본인)   : 조준점 둘레 유도 범위 원 (12°), 원 안에 잡히는 적에게 작은 표식 (월드), 화면 가장자리 하늘빛
 */
public final class HomingView {
	/** 곡선의 점이 남는 시간 (1/20초 단위 · 0.25초). */
	private static final float FADE = 5.0F;

	private record Point(Vec3 at, float born) {}

	private static final class Shot {
		final int caster;
		Vec3 pos;
		Vec3 dir;
		final int target;
		float age;
		boolean done;
		final List<Point> path = new ArrayList<>();

		Shot(int caster, Vec3 pos, Vec3 dir, int target) {
			this.caster = caster;
			this.pos = pos;
			this.dir = dir;
			this.target = target;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static final Map<Integer, SoundInstance> WIND = new HashMap<>();
	private static float now;

	private HomingView() {}

	public static void receive(HomingPayload msg) {
		Shot s = new Shot(msg.casterId(), msg.start(), msg.dir().normalize(), msg.targetId());
		s.path.add(new Point(msg.start(), now));
		SHOTS.add(s);
	}

	// ── 틱 ───────────────────────────────────────────────

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			SHOTS.clear();
			WIND.clear();
			return;
		}
		now = (float) ClientClock.now();
		if (!mc.isPaused()) {
			double step = Ticks.speed(TrailPursuit.BULLET_SPEED / 20.0);
			double maxTurn = Math.toRadians(Ticks.speed(TrailPursuit.TURN_RATE / 20.0));
			for (Shot s : SHOTS) {
				if (!s.done) {
					fly(mc, s, step, maxTurn);
				}
			}
		}
		SHOTS.removeIf(s -> s.done && (s.path.isEmpty() || now - s.path.get(s.path.size() - 1).born > FADE));
		for (Shot s : SHOTS) {
			s.path.removeIf(p -> now - p.born > FADE);
		}
		wind(mc);
	}

	/** 서버와 같은 규칙으로 한 틱. */
	private static void fly(Minecraft mc, Shot s, double step, double maxTurn) {
		s.age += (float) Ticks.step();
		if (s.age > TrailPursuit.LIFE) {
			s.done = true;
			return;
		}
		Entity t = s.target >= 0 ? mc.level.getEntity(s.target) : null;
		if (t instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) {
			s.dir = TrailPursuit.turn(s.dir, le.position().add(0, le.getBbHeight() * 0.6, 0).subtract(s.pos), maxTurn);
		}
		Vec3 next = s.pos.add(s.dir.scale(step));
		Entity caster = mc.level.getEntity(s.caster);
		HitResult wall = mc.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				caster != null ? caster : mc.player));
		Vec3 end = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
		Vec3 hit = firstHit(mc, s, end);
		if (hit != null) {
			s.path.add(new Point(hit, now));
			flash(mc, hit);
			s.done = true;
			return;
		}
		s.path.add(new Point(end, now));
		if (wall.getType() != HitResult.Type.MISS) {
			s.done = true;
			return;
		}
		s.pos = next;
	}

	private static @Nullable Vec3 firstHit(Minecraft mc, Shot s, Vec3 to) {
		Entity caster = mc.level.getEntity(s.caster);
		AABB sweep = new AABB(s.pos, to).inflate(0.5);
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, sweep)) {
			if (e == caster || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || (caster != null && caster.isAlliedTo(e))) {
				continue;
			}
			var h = e.getBoundingBox().inflate(0.2).clip(s.pos, to);
			if (h.isPresent() && s.pos.distanceToSqr(h.get()) < bestD) {
				bestD = s.pos.distanceToSqr(h.get());
				best = h.get();
			}
		}
		return best;
	}

	private static void flash(Minecraft mc, Vec3 at) {
		mc.level.addParticle(Fx.dust(Fx.rgb(0.6, 0.9, 1.0), 1.2F), at.x, at.y, at.z, 0, 0, 0);
		for (int i = 0; i < 4; i++) {
			mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z,
					(mc.level.getRandom().nextDouble() - 0.5) * 0.12, (mc.level.getRandom().nextDouble() - 0.5) * 0.12,
					(mc.level.getRandom().nextDouble() - 0.5) * 0.12);
		}
	}

	/** 궁극기로 나는 사람마다 바람 소리 하나. */
	private static void wind(Minecraft mc) {
		for (AbstractClientPlayer p : mc.level.players()) {
			boolean flying = SkillAnims.find(p.getId(), SkillAnimPayload.GS_PURSUIT) != null;
			if (flying && !WIND.containsKey(p.getId())) {
				Wind w = new Wind(p);
				WIND.put(p.getId(), w);
				mc.getSoundManager().play(w);
			}
		}
		WIND.values().removeIf(s -> ((Wind) s).isStopped());
	}

	/** 낮은 바람 소리 루프 — 궁극기 비행이 끝나면 멈춤. */
	private static final class Wind extends AbstractTickableSoundInstance {
		private final AbstractClientPlayer player;

		Wind(AbstractClientPlayer player) {
			super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.player = player;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.3F;
			this.pitch = 0.7F;
			this.x = player.getX();
			this.y = player.getY();
			this.z = player.getZ();
		}

		@Override
		public void tick() {
			if (player.isRemoved() || SkillAnims.find(player.getId(), SkillAnimPayload.GS_PURSUIT) == null) {
				stop();
				return;
			}
			x = player.getX();
			y = player.getY();
			z = player.getZ();
		}
	}

	// ── 그리기 ───────────────────────────────────────────

	public static void submit(PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized() || mc.level == null) {
			return;
		}
		LivingEntity mark = ownTarget(mc);
		if (SHOTS.isEmpty() && mark == null) {
			return;
		}
		Vec3 cam = camera.position();
		float t = now + ClientClock.partial(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		List<Shot> snapshot = List.copyOf(SHOTS);
		collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(BulletTrails.TEXTURE, true), (p, buffer) -> {
			for (Shot s : snapshot) {
				draw(p, buffer, s, cam, t);
			}
			if (mark != null) {
				marker(p, buffer, mark, cam, t);
			}
		});
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Shot s, Vec3 cam, float t) {
		List<Point> path = List.copyOf(s.path);
		for (int i = 1; i < path.size(); i++) {
			Point a = path.get(i - 1);
			Point b = path.get(i);
			float k = 1.0F - Mth.clamp((t - b.born) / FADE, 0.0F, 1.0F);
			if (k <= 0.0F) {
				continue;
			}
			BulletTrails.quad(pose, buffer, a.at, b.at, cam, 0.09F, 0.0F, ARGB.color(Math.round(170 * k), 0x5CCBFF), 0.3F);
			BulletTrails.quad(pose, buffer, a.at, b.at, cam, 0.035F, 0.0F, ARGB.color(Math.round(240 * k), 0xE6F8FF), 0.3F);
		}
	}

	/** 유도 대상 표식 — 가슴 높이에 카메라를 보는 작은 마름모. */
	private static void marker(PoseStack.Pose pose, VertexConsumer buffer, LivingEntity e, Vec3 cam, float t) {
		Vec3 c = e.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)).add(0, e.getBbHeight() + 0.35, 0);
		float pulse = 0.75F + 0.25F * Mth.sin(t * 0.8F);
		double r = 0.18 * pulse;
		int color = ARGB.color(230, 0x7FD4FF);
		BulletTrails.quad(pose, buffer, c.add(0, r, 0), c.add(0, -r, 0), cam, (float) r * 0.45F, 0.0F, color, 0.0F);
		BulletTrails.quad(pose, buffer, c.add(0, r * 1.4, 0), c.add(0, r * 0.9, 0), cam, 0.02F, 0.0F, color, 0.0F);
	}

	/** 지금 내 조준 12° 안에 잡히는 적 (궁극기 비행 중일 때만) — 서버와 같은 고르기. */
	private static @Nullable LivingEntity ownTarget(Minecraft mc) {
		if (mc.player == null || SkillAnims.find(mc.player.getId(), SkillAnimPayload.GS_PURSUIT) == null) {
			return null;
		}
		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getLookAngle();
		double cos = Math.cos(Math.toRadians(TrailPursuit.CONE));
		LivingEntity best = null;
		double bestDot = cos;
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(TrailPursuit.RANGE))) {
			if (e == mc.player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || mc.player.isAlliedTo(e)) {
				continue;
			}
			Vec3 chest = e.position().add(0, e.getBbHeight() * 0.6, 0);
			Vec3 to = chest.subtract(eye);
			double dist = to.length();
			if (dist > TrailPursuit.RANGE || dist < 1.0E-3) {
				continue;
			}
			double dot = look.dot(to.scale(1.0 / dist));
			if (dot < bestDot) {
				continue;
			}
			if (mc.level.clip(new ClipContext(eye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) {
				continue;
			}
			best = e;
			bestDot = dot;
		}
		return best;
	}

	// ── HUD (본인) ───────────────────────────────────────

	public static void hud(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || SkillAnims.find(mc.player.getId(), SkillAnimPayload.GS_PURSUIT) == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		// 화면 가장자리 하늘빛 (약하게)
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
		// 유도 범위 원 (12°) — 지금 시야각 기준 화면 반지름
		double fov = mc.options.fov().get() * mc.player.getFieldOfViewModifier(mc.options.getCameraType().isFirstPerson(), 1.0F);
		double r = Math.tan(Math.toRadians(TrailPursuit.CONE)) / Math.tan(Math.toRadians(fov / 2.0)) * (h / 2.0);
		boolean locked = ownTarget(mc) != null;
		// 잡힌 적이 있으면 밝게 이어진 원, 없으면 옅은 점선
		int color = locked ? 0xF0B4EEFF : 0xB07FD4FF;
		int n = 72;
		int cx = w / 2;
		int cy = h / 2;
		for (int i = 0; i < n; i++) {
			if (!locked && i % 3 == 2) {
				continue;
			}
			double a = i * Math.PI * 2.0 / n;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
	}

	/** 시험용: 지금 그리고 있는 탄 수 · 이 시전자의 날고 있는 탄. */
	public static int count() {
		return SHOTS.size();
	}

	public static int flyingOf(int caster) {
		int n = 0;
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
			Shot s = it.next();
			if (s.caster == caster) {
				n++;
			}
		}
		return n;
	}
}
