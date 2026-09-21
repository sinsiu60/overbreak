package kr.overbreak.client.anim.scatter;

import static kr.overbreak.classes.gunslinger.DashScatter.BRAKE_START;
import static kr.overbreak.classes.gunslinger.DashScatter.DASH_START;
import static kr.overbreak.classes.gunslinger.DashScatter.LENGTH;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER_START;

import java.util.HashMap;
import java.util.Map;

import kr.overbreak.client.anim.scatter.ScatterData.Ease;
import kr.overbreak.client.anim.scatter.ScatterData.SprayType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 돌진 난사 3인칭 몸 동작 (스펙 dash_scatter_anim_3p) — 조준 없이 미친 듯이 돌며 사방팔방 갈겨대는 난사.
 *
 * 모든 값은 "재생 경과 시간 + 시드" 만으로 계산합니다 (순차 난수 없음, 인덱스 해시).
 * 그래서 어느 클라이언트에서, 언제부터 보기 시작해도 같은 순간 같은 팔 방향이 나옵니다.
 *
 * 의미 축 → 바닐라 ModelPart (도 → 라디안은 적용하는 쪽에서)
 *   팔 pitch : xRot = pitch                (0 늘어뜨림 · -90 정면 수평 · -180 머리 위 · + 뒤로)
 *   팔 yaw   : yRot = -yaw                 (+ = 몸의 왼쪽 방향 → 오른팔은 안쪽 · 왼팔은 바깥쪽)
 *   팔 abd   : zRot = +abd                 (+ = 몸의 오른쪽 방향 → 오른팔은 바깥 · 왼팔은 안쪽)
 *              왼팔은 스펙대로 오른팔 값의 yaw · abd 부호를 뒤집어 넣으므로 양팔이 거울처럼 벌어집니다
 *   다리 pitch: xRot = pitch (- 앞) · 다리 abd: 오른다리 zRot = +abd, 왼다리 zRot = -abd (둘 다 + 바깥)
 *   상체 twist: body.yRot = +twist (+ 오른 어깨가 뒤로) — 어깨 자리 · 팔 yRot 도 같이 돌립니다 (바닐라 휘두르기와 같은 방식)
 *   머리      : xRot = pitch (- 위) · yRot = yaw
 *   루트      : 렌더러 setupRotations 에서 엉덩이 높이를 축으로 pitch(+ 앞으로 숙임) · roll(+ 오른쪽) · Y
 */
public final class ScatterBody {
	/** 한 순간의 자세 (도 · 픽셀). 팔 · 총 값은 이미 왼팔 거울 처리가 끝난 값입니다. */
	public record Pose(float rootYaw, float wobbleYaw, float rootPitch, float rootRoll, float rootY,
					   float twist, float headYaw, float headPitch, float headWeight,
					   float rPitch, float rYaw, float rAbd, float lPitch, float lYaw, float lAbd,
					   float rLeg, float lLeg, float rLegAbd, float lLegAbd,
					   float rGun, float lGun) {}

	/** 한 발 — 어느 팔이 몇 번째로 언제 쐈는가. */
	public record Shot(boolean left, int index, float at) {}

	private static final int R = 0;
	private static final int LEFT = 1;

	/** 공중 여부가 바뀐 시각 (엔티티별) — 도약 다리 블렌드용. */
	private static final Map<Integer, float[]> AIR = new HashMap<>();

	private ScatterBody() {}

	// ── 결정론적 난수 ─────────────────────────────────────

	/** 0 이상 1 미만 — 시드 · 팔 · 발 번호 · 용도로만 정해집니다 (몇 번째 발이든 바로 계산). */
	static float rand(int seed, int arm, int index, int use) {
		long h = seed * 0x9E3779B97F4A7C15L;
		h ^= (arm + 1) * 0xC2B2AE3D27D4EB4FL;
		h ^= (index + 1) * 0x165667B19E3779F9L;
		h ^= (use + 1) * 0xD6E8FEB86659FD93L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 40) / (float) (1L << 24);
	}

	private static float range(float u, float a, float b) {
		return a + (b - a) * u;
	}

	/** 뽑힌 유형 (같은 유형 세 번 연속이면 세 번째는 바깥 난사로). */
	static SprayType type(int seed, int arm, int index) {
		SprayType prev2 = null;
		SprayType prev1 = null;
		SprayType cur = null;
		for (int i = 0; i <= index; i++) {
			SprayType raw = pick(rand(seed, arm, i, 0));
			SprayType eff = raw == prev1 && raw == prev2 ? ScatterData.types.getFirst() : raw;
			prev2 = prev1;
			prev1 = eff;
			cur = eff;
		}
		return cur;
	}

	private static SprayType pick(float u) {
		float total = 0.0F;
		for (SprayType t : ScatterData.types) {
			total += t.weight();
		}
		float x = u * total;
		for (SprayType t : ScatterData.types) {
			x -= t.weight();
			if (x < 0.0F) {
				return t;
			}
		}
		return ScatterData.types.getLast();
	}

	/** 오른팔 기준 한 발의 {pitch, yaw, 총 기울기} — 왼팔은 yaw · 기울기 부호를 뒤집음. */
	static float[] shotDir(int seed, int arm, int index) {
		SprayType t = type(seed, arm, index);
		float pitch = range(rand(seed, arm, index, 1), t.pitchMin(), t.pitchMax());
		float yaw = range(rand(seed, arm, index, 2), t.yawMin(), t.yawMax());
		float roll = range(rand(seed, arm, index, 3), -ScatterData.gunRoll, ScatterData.gunRoll);
		float s = arm == LEFT ? -1.0F : 1.0F;
		return new float[] {pitch, yaw * s, roll * s};
	}

	// ── 발 시각 ───────────────────────────────────────────

	public static float fireAt(int arm, int k) {
		float offset = arm == LEFT ? ScatterData.armOffset : 0.0F;
		return SCATTER_START + ScatterData.units(offset + k * ScatterData.armFireInterval);
	}

	/** (t0, t1] 사이에 나간 발 — 양팔 모두, 시각 순. */
	public static java.util.List<Shot> shotsBetween(float t0, float t1) {
		java.util.List<Shot> out = new java.util.ArrayList<>();
		for (int k = 0; k < ScatterData.shotsPerArm; k++) {
			for (int arm = R; arm <= LEFT; arm++) {
				float at = fireAt(arm, k);
				if (at > t0 && at <= t1 && at < RECOVER_START) {
					out.add(new Shot(arm == LEFT, k, at));
				}
			}
		}
		out.sort(java.util.Comparator.comparingDouble(Shot::at));
		return out;
	}

	/** t 에 가장 최근 발 (양팔 합쳐) — 없으면 null. */
	private static Shot lastShot(float t) {
		Shot best = null;
		for (int arm = R; arm <= LEFT; arm++) {
			int k = lastIndex(arm, t);
			if (k >= 0) {
				float at = fireAt(arm, k);
				if (best == null || at > best.at()) {
					best = new Shot(arm == LEFT, k, at);
				}
			}
		}
		return best;
	}

	/** 이 팔이 t 까지 쏜 마지막 발 번호 (-1 = 아직). */
	private static int lastIndex(int arm, float t) {
		int k = -1;
		for (int i = 0; i < ScatterData.shotsPerArm; i++) {
			float at = fireAt(arm, i);
			if (at <= t && at < RECOVER_START) {
				k = i;
			}
		}
		return k;
	}

	// ── 자세 ──────────────────────────────────────────────

	/**
	 * @param e        재생 경과 (1/20초 단위)
	 * @param onGround 지금 땅에 있는가 (도약 다리)
	 * @param lod      멀리서 보는가 (32칸 밖 — 잔동작 생략)
	 */
	public static Pose pose(int entityId, int seed, float e, boolean onGround, boolean lod) {
		float t = Math.max(0.0F, Math.min(e, LENGTH));

		// 루트 회전 (대쉬 방향 기준 누적) · 휘청임
		float rootYaw = ScatterData.rootYaw.sample(t);
		float wobbleYaw = 0.0F;
		float wobblePitch = 0.0F;
		Shot last = lastShot(t);
		if (last != null) {
			float k = kick(t - last.at(), ScatterData.units(ScatterData.wobbleUp), ScatterData.units(ScatterData.wobbleDown));
			if (k > 0.0F) {
				float dir = ScatterData.rootYaw.slope(last.at());
				wobbleYaw = -dir * ScatterData.wobbleYaw * k;
				int arm = last.left() ? LEFT : R;
				wobblePitch = range(rand(seed, arm, last.index(), 4), -ScatterData.wobblePitch, ScatterData.wobblePitch) * k;
			}
		}
		float rootPitch = ScatterData.rootPitch.sample(t) + wobblePitch;
		float rootRoll = ScatterData.rootRoll.sample(t);
		float rootY = ScatterData.rootY.sample(t);

		// 상체 비틀림 — 쏜 팔 쪽으로 (난사 중에만)
		float twist = 0.0F;
		if (!lod && last != null && t < RECOVER_START) {
			float to = last.left() ? -ScatterData.twistAngle : ScatterData.twistAngle;
			Shot before = lastShot(last.at() - 0.001F);
			float from = before == null ? 0.0F : (before.left() ? -ScatterData.twistAngle : ScatterData.twistAngle);
			float x = (t - last.at()) / ScatterData.units(ScatterData.twistTime);
			twist = Mth.lerp(Ease.EXPO_OUT.apply(x), from, to);
		}

		// 머리 — 돌진 중 고개 들기, 난사 중 마구 흔들림
		float headYaw = 0.0F;
		float headPitch = 0.0F;
		float headWeight;
		if (t < DASH_START) {
			headWeight = 0.0F;
		} else if (t < SCATTER_START) {
			headWeight = Mth.clamp((t - DASH_START) / 1.0F, 0.0F, 1.0F);
			headPitch = ScatterData.headDashPitch;
		} else if (t < RECOVER_START) {
			headWeight = 1.0F;
			if (!lod && last != null) {
				int arm = last.left() ? LEFT : R;
				float ny = range(rand(seed, arm, last.index(), 5), -ScatterData.headYaw, ScatterData.headYaw);
				float np = range(rand(seed, arm, last.index(), 6), ScatterData.headPitchMin, ScatterData.headPitchMax);
				Shot before = lastShot(last.at() - 0.001F);
				float py = 0.0F;
				float pp = ScatterData.headDashPitch;
				if (before != null) {
					int ba = before.left() ? LEFT : R;
					py = range(rand(seed, ba, before.index(), 5), -ScatterData.headYaw, ScatterData.headYaw);
					pp = range(rand(seed, ba, before.index(), 6), ScatterData.headPitchMin, ScatterData.headPitchMax);
				}
				float x = Ease.EXPO_OUT.apply((t - last.at()) / ScatterData.units(ScatterData.headSnap));
				headYaw = Mth.lerp(x, py, ny);
				headPitch = Mth.lerp(x, pp, np);
			}
		} else {
			headWeight = 1.0F - Mth.clamp((t - RECOVER_START) / ScatterData.units(ScatterData.recoverTurn), 0.0F, 1.0F);
		}

		// 팔
		float[] r = arm(seed, R, t);
		float[] l = arm(seed, LEFT, t);

		// 다리 — 기본 키 · 발구름 · 도약
		float rLeg = ScatterData.legRPitch.sample(t);
		float lLeg = ScatterData.legLPitch.sample(t);
		float abd = ScatterData.legAbd.sample(t);
		if (!lod) {
			float from = ScatterData.toTime(ScatterData.stompFrom);
			float to = ScatterData.toTime(ScatterData.stompTo);
			float every = ScatterData.units(ScatterData.stompEvery);
			if (t >= from && t < to && every > 0.0F) {
				int n = (int) Math.floor((t - from) / every);
				float since = t - from - n * every;
				float up = ScatterData.units(ScatterData.stompUp);
				float down = ScatterData.units(ScatterData.stompDown);
				float k = since < up ? Ease.QUAD_OUT.apply(since / up) : 1.0F - Ease.QUAD_IN.apply((since - up) / down);
				k = Mth.clamp(k, 0.0F, 1.0F);
				boolean rightLifts = n % 2 == 0;
				rLeg += (rightLifts ? ScatterData.stompLift : ScatterData.stompOther) * k;
				lLeg += (rightLifts ? ScatterData.stompOther : ScatterData.stompLift) * k;
			}
		}
		float air = airBlend(entityId, e, onGround && true, t >= BRAKE_START && t < RECOVER_START);
		if (air > 0.0F) {
			rLeg = Mth.lerp(air, rLeg, ScatterData.airLeg);
			lLeg = Mth.lerp(air, lLeg, ScatterData.airLeg);
			abd = Mth.lerp(air, abd, 0.0F);
		}

		// 손에 든 총
		float rGun = gun(seed, R, t, lod);
		float lGun = gun(seed, LEFT, t, lod);

		return new Pose(rootYaw, wobbleYaw, rootPitch, rootRoll, rootY, twist, headYaw, headPitch, headWeight,
				r[0], r[1], r[2], l[0], l[1], l[2], rLeg, lLeg, abd, abd, rGun, lGun);
	}

	/** 오른팔 기준 값 → 이 팔 값 (왼팔은 yaw · abd 부호 반전). */
	private static float[] mirror(int arm, float pitch, float yaw, float abd) {
		float s = arm == LEFT ? -1.0F : 1.0F;
		return new float[] {pitch, yaw * s, abd * s};
	}

	/** 한 팔의 {pitch, yaw, abd} — 이미 거울 처리된 값. */
	static float[] arm(int seed, int arm, float t) {
		if (t < BRAKE_START) {
			return mirror(arm, ScatterData.armPitch.sample(t), ScatterData.armYaw.sample(t), ScatterData.armAbd.sample(t));
		}
		float[] dash = mirror(arm, ScatterData.armPitch.sample(BRAKE_START), ScatterData.armYaw.sample(BRAKE_START),
				ScatterData.armAbd.sample(BRAKE_START));
		float[] entry = mirror(arm, ScatterData.entryPitch, ScatterData.entryYaw, 0.0F);
		if (t >= RECOVER_START) {
			float[] from = arm(seed, arm, RECOVER_START - 0.001F);
			float gatherT = ScatterData.toTime(ScatterData.gatherAt);
			float[] gather = mirror(arm, ScatterData.gatherPitch, ScatterData.gatherYaw, 0.0F);
			if (t < gatherT) {
				float x = Ease.QUAD_OUT.apply((t - RECOVER_START) / Math.max(0.01F, gatherT - RECOVER_START));
				return lerp3(x, from, gather);
			}
			float x = Ease.QUAD_IN_OUT.apply((t - gatherT) / Math.max(0.01F, LENGTH - gatherT));
			return lerp3(x, gather, new float[3]);
		}
		// 난사 들어가기 — 제동 동안 양옆 위로 확
		float entryX = Ease.BACK_OUT.apply((t - BRAKE_START) / Math.max(0.01F, SCATTER_START - BRAKE_START));
		float[] base = lerp3(entryX, dash, entry);
		int k = -1;
		float snap = ScatterData.units(ScatterData.snapTime);
		for (int i = 0; i < ScatterData.shotsPerArm; i++) {
			if (fireAt(arm, i) - snap <= t) {
				k = i;
			}
		}
		if (k < 0) {
			return base;
		}
		float[] prev = k == 0 ? base : dirOf(seed, arm, k - 1);
		float[] cur = dirOf(seed, arm, k);
		float fire = fireAt(arm, k);
		float[] v;
		if (t < fire) {
			v = lerp3(Ease.EXPO_OUT.apply((t - (fire - snap)) / snap), prev, cur);
		} else {
			v = cur;
			// 반동 — 총구가 위로 크게 튐 (하늘로 쏜 발은 뒤로, 바닥으로 쏜 발은 절반)
			SprayType type = type(seed, arm, k);
			float kick = kick(t - fire, ScatterData.units(ScatterData.recoilUp), ScatterData.units(ScatterData.recoilDown));
			v = new float[] {v[0] + type.recoil() * type.recoilScale() * kick, v[1], v[2]};
		}
		return v;
	}

	private static float[] dirOf(int seed, int arm, int k) {
		float[] d = shotDir(seed, arm, k);
		return new float[] {d[0], d[1], 0.0F};
	}

	/** 총 기울기 (roll) — 난사 중 발마다 무작위, 마무리에서 한 바퀴. */
	private static float gun(int seed, int arm, float t, boolean lod) {
		if (t >= RECOVER_START) {
			float spin = ScatterData.gunSpin.sample(t);
			// 한 바퀴가 끝나면 -360 = 0 — 끝난 뒤 되돌아올 때 거꾸로 한 바퀴 돌지 않게
			if (spin <= -359.0F) {
				spin += 360.0F;
			}
			return spin;
		}
		if (lod || t < SCATTER_START - ScatterData.units(ScatterData.snapTime)) {
			return 0.0F;
		}
		int k = lastIndex(arm, t + ScatterData.units(ScatterData.snapTime));
		if (k < 0) {
			return 0.0F;
		}
		float prev = k == 0 ? 0.0F : shotDir(seed, arm, k - 1)[2];
		float cur = shotDir(seed, arm, k)[2];
		float fire = fireAt(arm, k);
		float snap = ScatterData.units(ScatterData.snapTime);
		return t < fire ? Mth.lerp(Ease.EXPO_OUT.apply((t - (fire - snap)) / snap), prev, cur) : cur;
	}

	/** 튀었다가(up) 돌아오는(down) 세기 0~1. */
	private static float kick(float since, float up, float down) {
		if (since < 0.0F) {
			return 0.0F;
		}
		if (since < up) {
			return Ease.EXPO_OUT.apply(since / up);
		}
		if (since < up + down) {
			return 1.0F - Ease.QUAD_OUT.apply((since - up) / down);
		}
		return 0.0F;
	}

	/** 공중 다리 세기 0~1 — 들어갈 때 quad out, 착지할 때 back out. */
	private static float airBlend(int id, float e, boolean onGround, boolean active) {
		boolean airborne = active && !onGround;
		float[] s = AIR.computeIfAbsent(id, k -> new float[] {0.0F, -100.0F, 0.0F});
		if ((s[0] > 0.5F) != airborne) {
			s[2] = level(s, e);
			s[0] = airborne ? 1.0F : 0.0F;
			s[1] = e;
		}
		return level(s, e);
	}

	private static float level(float[] s, float e) {
		float since = e - s[1];
		if (s[0] > 0.5F) {
			return Mth.lerp(Ease.QUAD_OUT.apply(since / ScatterData.units(ScatterData.airEnter)), s[2], 1.0F);
		}
		return Mth.lerp(Ease.BACK_OUT.apply(since / ScatterData.units(ScatterData.airExit)), s[2], 0.0F);
	}

	/** 재생이 바뀌면 공중 기록을 지웁니다. */
	public static void forget(int id) {
		AIR.remove(id);
	}

	private static float[] lerp3(float x, float[] a, float[] b) {
		return new float[] {Mth.lerp(x, a[0], b[0]), Mth.lerp(x, a[1], b[1]), Mth.lerp(x, a[2], b[2])};
	}

	// ── 총구 방향 (사격 이벤트) ────────────────────────────

	/**
	 * 한 발의 총구 자리와 방향 (월드) — 루트 yaw · pitch · roll + 상체 비틀림 + 팔 pitch · yaw 를 모두 합친 것.
	 * 총은 팔을 곧게 이어 겨눈다고 봅니다 (팔이 가리키는 쪽 = 총구 방향).
	 *
	 * @param feet   엔티티 발 위치
	 * @param bodyYaw 모델이 바라보는 월드 방향 (대쉬 방향 + 루트 yaw + 휘청임, 마인크래프트 yaw)
	 * @return {총구 위치, 방향}
	 */
	public static Vec3[] muzzle(Pose p, boolean left, Vec3 feet, float bodyYaw) {
		double y = Math.toRadians(bodyYaw);
		Vec3 f = new Vec3(-Math.sin(y), 0, Math.cos(y));
		Vec3 u = new Vec3(0, 1, 0);
		Vec3 rt = new Vec3(-Math.cos(y), 0, -Math.sin(y));
		// 루트 pitch (+ 앞으로 숙임) · roll (+ 오른쪽)
		double pr = Math.toRadians(p.rootPitch());
		Vec3 u1 = u.scale(Math.cos(pr)).add(f.scale(Math.sin(pr)));
		Vec3 f1 = f.scale(Math.cos(pr)).subtract(u.scale(Math.sin(pr)));
		double rr = Math.toRadians(p.rootRoll());
		Vec3 u2 = u1.scale(Math.cos(rr)).add(rt.scale(Math.sin(rr)));
		Vec3 rt2 = rt.scale(Math.cos(rr)).subtract(u1.scale(Math.sin(rr)));
		// 상체 비틀림 (+ 오른 어깨가 뒤로 = 가슴이 오른쪽으로)
		double tw = Math.toRadians(p.twist());
		Vec3 f3 = f1.scale(Math.cos(tw)).add(rt2.scale(Math.sin(tw)));
		Vec3 rt3 = rt2.scale(Math.cos(tw)).subtract(f1.scale(Math.sin(tw)));
		// 팔: yaw (+ 몸의 왼쪽) 로 수평 방향을 틀고 pitch 로 올림
		float pitch = left ? p.lPitch() : p.rPitch();
		float yaw = left ? p.lYaw() : p.rYaw();
		double w = Math.toRadians(yaw);
		Vec3 fw = f3.scale(Math.cos(w)).subtract(rt3.scale(Math.sin(w)));
		double a = Math.toRadians(pitch);
		Vec3 dir = u2.scale(-Math.cos(a)).subtract(fw.scale(Math.sin(a))).normalize();
		// 어깨: 엉덩이(0.70) 에서 몸통 위로 0.59, 옆으로 0.29 (모델 0.9375배)
		Vec3 hip = feet.add(0, 0.70 + p.rootY() / 16.0 * 0.9375, 0);
		Vec3 shoulder = hip.add(u2.scale(0.59)).add(rt3.scale(left ? -0.29 : 0.29));
		Vec3 tip = shoulder.add(dir.scale(0.95));
		return new Vec3[] {tip, dir};
	}
}
