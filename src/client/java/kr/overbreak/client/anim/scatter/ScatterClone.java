package kr.overbreak.client.anim.scatter;

import static kr.overbreak.classes.gunslinger.DashScatter.PULSES;
import static kr.overbreak.classes.gunslinger.DashScatter.PULSE_GAP;
import static kr.overbreak.classes.gunslinger.DashScatter.RADIUS;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER_START;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.client.anim.AnimRenderState;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.anim.scatter.ScatterData.Ease;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 돌진 난사 분신 — 난사가 시작되면 본체는 사라지고, 두 가지 분신이 8칸 안을 휘젓습니다.
 *
 *   본체 모습의 분신 : 캐릭터와 같은 스킨. 1초 주기로 나타나 8칸 안의 자리들을 쉬지 않고 옮겨 다니며 서서히 투명해짐
 *                     (진짜 본체는 제자리에 숨어 있으므로 시야는 움직이지 않습니다)
 *   하늘색 공격 분신 : 본체 모습이 자리에 닿을 때마다(0.15초, 판정 펄스와 같은 박자) 그 자리에 하나씩 남아
 *                     곁의 적을 쏘고 — 한 발마다 새 자세로 바꿔 가며 — 0.15초 뒤부터 사라짐
 *
 *   자리는 반경(5칸) 안의 적 곁 — 적마다 돌아가며 붙고, 적이 없으면 본체 둘레 3~7칸 아무 데나.
 *   어느 자리든 본체에서 8칸 밖으로는 나가지 않고, 벽 · 땅속에는 서지 않습니다.
 *
 * 순수 연출입니다 — 판정은 서버의 원형 펄스(본체 기준 반경 5칸)가 그대로 맡습니다.
 */
public final class ScatterClone {
	/** 본체에서 분신이 나갈 수 있는 거리 (칸). */
	public static final double LEASH = 8.0;
	/** 첫 자리로 튀어 나가는 시간 (1/20초 단위) — 제동 끝 무렵. */
	static final float LAUNCH = 1.0F;
	/** 본체 모습이 마지막 자리에서 본체로 돌아오는 시간. */
	static final float RETURN = 1.5F;
	/** 본체가 사라지는 구간. */
	public static final float HIDE_FROM = SCATTER_START - LAUNCH;
	static final float HIDE_TO = RECOVER_START + RETURN;
	/** 본체 모습이 나타나 투명해지는 주기 (1초). */
	static final float CYCLE = 20.0F;
	/** 공격 분신이 쏘는 시간 · 사라지는 시간. */
	static final float FIRE = PULSE_GAP;
	static final float FADE = 3.0F;

	/** 한 자리 — 붙는 적(없으면 -1)과 그 둘레 어느 쪽 · 얼마나 떨어져 서는가 · 방향 흔들림. */
	private record Hop(int target, double angle, double dist, float yawJitter) {}

	private record Hops(SkillAnims.Play play, Hop[] hops) {}

	/** 그릴 분신 하나 — 발 위치 · 바라보는 yaw · 자세 · 겨누는 점 · 불투명도. */
	public record Clone(Vec3 pos, float yaw, ScatterBody.Pose pose, @Nullable Vec3 aim, float alpha) {}

	private static final Map<Integer, Hops> HOPS = new HashMap<>();

	private ScatterClone() {}

	/** 본체를 숨기는 시각인가. */
	public static boolean hidden(float e) {
		return e >= HIDE_FROM && e < HIDE_TO;
	}

	/** 지금 재생 중인 돌진 난사의 경과 시각 — 없으면 NaN. */
	public static float elapsed(int id, float partial) {
		SkillAnims.Play play = SkillAnims.find(id, SkillAnimPayload.GS_SCATTER);
		if (play == null || SkillAnims.latest(id) != play) {
			return Float.NaN;
		}
		return play.elapsed(partial);
	}

	/** k 번째 자리에 닿는 시각 (= k 번째 판정 펄스). */
	static float arrive(int k) {
		return SCATTER_START + k * PULSE_GAP;
	}

	// ── 본체 모습 (스킨) ─────────────────────────────────

	/** 본체 모습의 분신 — 자리에서 자리로 쉬지 않고 옮겨 다니며 1초 주기로 서서히 투명해짐. */
	public static @Nullable Clone phantom(Entity self, float e, Vec3 center, float partial) {
		Hop[] hops = hops(self);
		if (hops == null || !hidden(e)) {
			return null;
		}
		int seed = ScatterView.seed(self.getId());
		Vec3 from;
		Vec3 to;
		float x;
		if (e < arrive(0)) {
			from = center;
			to = spot(self, hops[0], center, partial);
			x = (e - HIDE_FROM) / LAUNCH;
		} else if (e < arrive(PULSES - 1)) {
			int k = Mth.clamp((int) Math.floor((e - SCATTER_START) / PULSE_GAP), 0, PULSES - 2);
			from = spot(self, hops[k], center, partial);
			to = spot(self, hops[k + 1], center, partial);
			x = (e - arrive(k)) / PULSE_GAP;
		} else {
			from = spot(self, hops[PULSES - 1], center, partial);
			to = center;
			x = (e - arrive(PULSES - 1)) / (HIDE_TO - arrive(PULSES - 1));
		}
		x = Mth.clamp(x, 0.0F, 1.0F);
		Vec3 pos = from.lerp(to, Ease.QUAD_IN_OUT.apply(x));
		Vec3 d = to.subtract(from);
		float yaw = d.horizontalDistanceSqr() < 1.0E-4 ? ScatterView.dashYaw(self.getId(), self.getYRot())
				: (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		// 1초 주기: 나타날 때 짙었다가 서서히 투명해짐
		float c = ((e - HIDE_FROM) % CYCLE) / CYCLE;
		float alpha = (0.05F + 0.9F * (float) Math.pow(1.0F - c, 1.6)) * Mth.clamp(((e - HIDE_FROM) % CYCLE) / 0.5F, 0.0F, 1.0F);
		return new Clone(pos, yaw, runPose(seed, e), null, alpha);
	}

	/** 내달리는 자세 — 앞으로 숙이고 총 든 두 팔은 뒤로 젖히고 다리는 크게 번갈아. */
	static ScatterBody.Pose runPose(int seed, float e) {
		float s = Mth.sin(e * Mth.PI / 1.5F);
		return new ScatterBody.Pose(0.0F, 0.0F, 28.0F, 0.0F, -1.0F,
				s * 8.0F, 0.0F, -18.0F, 1.0F,
				35.0F, 0.0F, 12.0F, 35.0F, 0.0F, -12.0F,
				-50.0F * s, 50.0F * s, 4.0F, 4.0F,
				0.0F, 0.0F);
	}

	// ── 하늘색 공격 분신 ─────────────────────────────────

	/** 지금 보이는 공격 분신들 (오래된 것부터). */
	public static List<Clone> attackers(Entity self, float e, Vec3 center, float partial) {
		List<Clone> out = new ArrayList<>();
		Hop[] hops = hops(self);
		if (hops == null) {
			return out;
		}
		for (int k = 0; k < PULSES; k++) {
			Clone c = attacker(self, hops, k, e, center, partial);
			if (c != null) {
				out.add(c);
			}
		}
		return out;
	}

	/** 그 시각에 총을 쏘는 공격 분신 (가장 최근에 남은 것) — 없으면 null. */
	public static @Nullable Clone shooter(Entity self, float t, Vec3 center, float partial) {
		Hop[] hops = hops(self);
		if (hops == null || t < arrive(0)) {
			return null;
		}
		int k = Mth.clamp((int) Math.floor((t - SCATTER_START) / PULSE_GAP), 0, PULSES - 1);
		return attacker(self, hops, k, t, center, partial);
	}

	private static @Nullable Clone attacker(Entity self, Hop[] hops, int k, float e, Vec3 center, float partial) {
		float born = arrive(k);
		float age = e - born;
		if (age < 0.0F || age >= FIRE + FADE) {
			return null;
		}
		int seed = ScatterView.seed(self.getId());
		Hop hop = hops[k];
		Vec3 pos = spot(self, hop, center, partial);
		Vec3 aim = null;
		if (hop.target >= 0) {
			Entity t = self.level().getEntity(hop.target);
			if (t != null && t.isAlive()) {
				aim = t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0);
			}
		}
		float yaw;
		float pitch = 0.0F;
		if (aim != null) {
			Vec3 d = aim.subtract(pos.add(0, 1.4, 0));
			yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		} else {
			// 적이 없으면 본체 반대쪽(바깥)을 향해 쏨
			Vec3 d = pos.subtract(center);
			yaw = d.horizontalDistanceSqr() < 1.0E-4 ? ScatterView.dashYaw(self.getId(), self.getYRot())
					: (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		}
		// 자세 번호 = 이 분신이 지금까지 쏜 발 수 (한 발마다 새 자세)
		float until = Math.min(e, born + FIRE);
		int shots = 0;
		ScatterBody.Shot last = null;
		for (ScatterBody.Shot s : ScatterBody.shotsBetween(born - 1.0E-3F, until)) {
			shots++;
			last = s;
		}
		int pick = poseIndex(seed, k, shots);
		// 자세마다 몸을 적 쪽에서 조금 틀어 섬 (옆으로 · 뒤돌아 쏘는 자세는 더 많이)
		yaw += POSES[pick].bodyTurn + hop.yawJitter;
		ScatterBody.Pose pose = POSES[pick].pose(pitch, last == null || e - last.at() > 2.0F ? 0.0F : kick(e - last.at()), last != null && last.left());
		float fadeIn = Mth.clamp(age / 0.5F, 0.0F, 1.0F);
		float fadeOut = 1.0F - Mth.clamp((age - FIRE) / FADE, 0.0F, 1.0F);
		return new Clone(pos, yaw, pose, aim, 0.88F * fadeIn * fadeOut * fadeOut);
	}

	/** 발마다 다른 자세 — 바로 앞 자세와는 겹치지 않게. */
	static int poseIndex(int seed, int k, int shots) {
		int prev = -1;
		int pick = 0;
		for (int n = 0; n <= shots; n++) {
			pick = (int) (ScatterBody.rand(seed, 11, k * 16 + n, 0) * POSES.length);
			if (pick == prev) {
				pick = (pick + 1) % POSES.length;
			}
			prev = pick;
		}
		return pick;
	}

	/** 쏜 뒤 지난 시간 → 총구가 들린 각 (도, - 위). */
	private static float kick(float d) {
		float up = ScatterData.units(ScatterData.recoilUp);
		float down = ScatterData.units(ScatterData.recoilDown);
		float k = d < up ? d / up : 1.0F - (d - up) / down;
		return -22.0F * Mth.clamp(k, 0.0F, 1.0F);
	}

	/**
	 * 공격 자세 한 벌. 팔 값은 "겨눔 기준(-90 + 적 높이)" 에서 더하는 값, 왼팔은 이미 거울 처리된 값.
	 * bodyTurn: 몸을 적 쪽에서 얼마나 틀어 서는가 (+ 왼쪽으로 돌아 섬 → 오른팔이 적을 향해 뻗음).
	 */
	private record Stance(float bodyTurn, float rootPitch, float rootRoll, float rootY, float twist,
						  float rPitch, float rYaw, float rAbd, float lPitch, float lYaw, float lAbd,
						  float rLeg, float lLeg, float rLegAbd, float lLegAbd, float rGun, float lGun) {
		ScatterBody.Pose pose(float aimPitch, float kick, boolean left) {
			float aim = -90.0F + aimPitch;
			float rk = left ? 0.0F : kick;
			float lk = left ? kick : 0.0F;
			return new ScatterBody.Pose(0.0F, 0.0F, rootPitch, rootRoll, rootY,
					twist, 0.0F, Mth.clamp(aimPitch - rootPitch, -50.0F, 50.0F), 1.0F,
					aim + rPitch + rk, rYaw, rAbd, aim + lPitch + lk, lYaw, lAbd,
					rLeg, lLeg, rLegAbd, lLegAbd, rGun, lGun);
		}
	}

	private static final Stance[] POSES = {
			// 두 손 정조준 · 다리 벌려 버팀
			new Stance(0, 4, 0, -1, 0, 0, 6, 4, 0, -6, -4, -14, 10, 8, 8, 0, 0),
			// 한쪽 무릎 꿇고 쏘기
			new Stance(0, 8, 0, -4.5F, 0, 0, 5, 2, 0, -5, -2, -85, 70, 4, 6, 0, 0),
			// 몸을 옆으로 돌려 오른팔 쭉 · 왼팔은 반대편으로 뻗어 딴 데를 쏨
			new Stance(55, 0, -8, -1, 20, 0, 50, 6, 10, 70, -60, -10, 12, 10, 12, 15, -30),
			// 두 팔 엇갈려 쏘기 (가위)
			new Stance(0, 6, 0, -1, 0, 0, -28, 0, 0, 28, 0, -20, 14, 6, 6, 25, -25),
			// 옆으로 몸을 눕히며 공중 사격 · 다리 접음
			new Stance(-35, -8, 28, 2, -15, 0, -35, 8, -20, 55, -40, -70, -35, 10, 10, -30, 30),
			// 낮게 미끄러지며 쏘기
			new Stance(20, 24, 14, -5, 10, -10, 12, 4, -10, -2, -4, -75, 25, 8, 4, 0, 0),
			// 한 손은 적, 한 손은 하늘로 치켜들고 쏨
			new Stance(-20, -4, -6, 0.5F, -10, 0, -12, 4, -85, -8, -20, -30, 20, 6, 10, 10, -40),
			// 등 돌린 채 어깨 너머로 뒤쏘기
			new Stance(150, -6, 0, -1, -45, 10, -80, 10, 10, -80, -10, -10, 10, 6, 6, 90, 90)};

	// ── 자리 ─────────────────────────────────────────────

	/** 이번 재생의 자리들 — 처음 부를 때 적을 골라 고정 (재생이 없으면 null). */
	private static Hop @Nullable [] hops(Entity self) {
		SkillAnims.Play play = SkillAnims.find(self.getId(), SkillAnimPayload.GS_SCATTER);
		if (play == null) {
			return null;
		}
		Hops h = HOPS.get(self.getId());
		if (h == null || h.play != play) {
			int seed = ScatterView.seed(self.getId());
			Vec3 center = self.position();
			Hop[] hops = new Hop[PULSES];
			for (int k = 0; k < PULSES; k++) {
				hops[k] = pick(self, seed, k, center);
			}
			h = new Hops(play, hops);
			HOPS.put(self.getId(), h);
		}
		return h.hops;
	}

	private static Hop pick(Entity self, int seed, int k, Vec3 center) {
		List<LivingEntity> enemies = enemies(self, center);
		double angle = ScatterBody.rand(seed, 7, k, 0) * Math.PI * 2.0;
		float jitter = (ScatterBody.rand(seed, 7, k, 4) - 0.5F) * 20.0F;
		if (!enemies.isEmpty()) {
			// 적마다 돌아가며 — 시작 적은 시드로
			int start = (int) (ScatterBody.rand(seed, 7, 0, 1) * enemies.size());
			LivingEntity t = enemies.get((start + k) % enemies.size());
			return new Hop(t.getId(), angle, 1.8 + ScatterBody.rand(seed, 7, k, 2) * 1.4, jitter);
		}
		// 적이 없으면 본체 둘레를 골고루 (황금각으로 돌며 3~7칸)
		angle = k * 2.39996 + ScatterBody.rand(seed, 7, 0, 3) * Math.PI * 2.0;
		return new Hop(-1, angle, 3.0 + ScatterBody.rand(seed, 7, k, 2) * 4.0, jitter);
	}

	/** 반경 안 적 — 같은 편 · 관전자 · 갑옷 거치대 빼고, 엔티티 번호 순 (보는 사람마다 같은 순서). */
	static List<LivingEntity> enemies(Entity self, Vec3 center) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(center, center).inflate(RADIUS + 0.5, 3.0, RADIUS + 0.5);
		for (LivingEntity e : self.level().getEntitiesOfClass(LivingEntity.class, box)) {
			if (e == self || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || self.isAlliedTo(e)) {
				continue;
			}
			if (e.position().subtract(center).horizontalDistance() <= RADIUS + 0.5) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingInt(Entity::getId));
		return out;
	}

	/** 그 자리의 발 위치 — 본체에서 8칸 안, 벽 · 땅속이면 안쪽으로 당김. */
	private static Vec3 spot(Entity self, Hop hop, Vec3 center, float partial) {
		Vec3 base = center;
		if (hop.target >= 0) {
			Entity t = self.level().getEntity(hop.target);
			if (t != null) {
				base = t.getPosition(partial);
			}
		}
		Vec3 want = base.add(Math.cos(hop.angle) * hop.dist, 0, Math.sin(hop.angle) * hop.dist);
		Vec3 off = want.subtract(center);
		if (off.length() > LEASH) {
			want = center.add(off.scale(LEASH / off.length()));
		}
		for (int i = 0; i < 5; i++) {
			Vec3 at = center.lerp(want, 1.0 - i * 0.2);
			if (self.level().noCollision(self, self.getDimensions(self.getPose()).makeBoundingBox(at))) {
				return at;
			}
		}
		return center;
	}

	// ── 그리기 · 사격 도움 ───────────────────────────────

	/** 분신 모습을 렌더 상태에 입힘 (자리 · 방향 · 자세). 모델 자리는 그리는 쪽이 옮깁니다. */
	public static void dress(AvatarRenderState state, Clone c) {
		AnimRenderState a = (AnimRenderState) state;
		a.overbreak$setScatter(c.pose(), 1.0F);
		state.bodyRot = c.yaw();
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		state.x = c.pos().x;
		state.y = c.pos().y;
		state.z = c.pos().z;
	}

	/** 한 발의 총구 자리 · 방향 — 붙은 적이 있으면 그 가슴으로 (살짝 흩어짐), 없으면 팔이 가리키는 쪽. */
	public static Vec3[] muzzle(Clone c, boolean left, int seed, int index) {
		Vec3[] m = ScatterBody.muzzle(c.pose(), left, c.pos(), c.yaw());
		Vec3 dir = m[1];
		if (c.aim() != null && dir.dot(c.aim().subtract(m[0]).normalize()) > 0.3) {
			dir = c.aim().subtract(m[0]).normalize();
		}
		double spread = Math.toRadians(c.aim() != null ? 3.0 : 8.0);
		double a = (ScatterBody.rand(seed, left ? 9 : 8, index, 0) - 0.5) * 2.0 * spread;
		double b = (ScatterBody.rand(seed, left ? 9 : 8, index, 1) - 0.5) * 2.0 * spread;
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-6) {
			side = new Vec3(1, 0, 0);
		}
		side = side.normalize();
		Vec3 up = side.cross(dir).normalize();
		dir = dir.add(side.scale(Math.tan(a))).add(up.scale(Math.tan(b))).normalize();
		return new Vec3[] {m[0], dir};
	}

	/** 월드를 나가거나 재생이 끝나면 비웁니다. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			HOPS.clear();
			return;
		}
		HOPS.entrySet().removeIf(en -> SkillAnims.find(en.getKey(), SkillAnimPayload.GS_SCATTER) != en.getValue().play);
	}
}
