package kr.overbreak.client.anim;

import static kr.overbreak.classes.gunslinger.DashScatter.BRAKE;
import static kr.overbreak.classes.gunslinger.DashScatter.BRAKE_START;
import static kr.overbreak.classes.gunslinger.DashScatter.DASH;
import static kr.overbreak.classes.gunslinger.DashScatter.DASH_START;
import static kr.overbreak.classes.gunslinger.DashScatter.LENGTH;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SHOTS;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/**
 * 돌진 난사 1인칭 쌍권총 (스펙 PART 6-2).
 *
 * 키프레임 시각은 "몇 번째 단계의 몇 할" 로 적어 두고 서버 {@code DashScatter} 의 단계 길이로 풀어 씁니다 —
 * 단계 길이를 바꾸면 동작이 그 비율대로 늘고 줄어듭니다.
 *
 * 스펙 축 → 이 코드의 카메라 공간
 *   위치 (픽셀)  X 오른쪽+ · Y 위+ · Z 앞+   →  x = 0.56 + X/16, y = -0.52 + Y/16, z = -0.72 - Z/16
 *   회전 (도)    pitch 총구 아래+ · yaw 몸 안쪽+ · roll  →  가로축 = -pitch (여기서는 + 가 총구 위), 세로축 = yaw, 화면축 = roll
 *   왼손은 invert 로 X · yaw · roll 이 뒤집힙니다 (스펙의 "L팔 = R팔 부호 반전" 과 같음)
 */
public final class ScatterAnim {
	/** 이징. */
	private enum Ease { LINEAR, QUAD_OUT, QUAD_IN, QUAD_IN_OUT, CUBIC_OUT, EXPO_OUT, BACK_OUT, SINE_IN_OUT, HOLD }

	/** 키프레임 — 오른손 기준 스펙 값. ease 는 앞 키프레임에서 이 키프레임으로 가는 곡선. */
	private record Key(float at, float x, float y, float z, float pitch, float yaw, float roll, Ease ease) {}

	/** 단계 시작 + 그 단계 길이의 몇 할 (1/20초 단위). */
	private static float at(int phaseStart, int phaseLength, float frac) {
		return phaseStart + phaseLength * frac;
	}

	private static final Key[] KEYS = {
			new Key(0.0F, 0, 0, 0, 0, 0, 0, Ease.LINEAR),
			// 0.10 가슴 앞에서 두 총을 X 자로 교차
			new Key(at(DASH_START, DASH, 0.0F), -3, -1, -2, 0, 35, -20, Ease.QUAD_OUT),
			// 0.15 팔을 뒤로 젖힘 → 돌진 내내 유지
			new Key(at(DASH_START, DASH, 0.2F), 4, -3, -5, 40, -15, 10, Ease.QUAD_IN),
			new Key(at(BRAKE_START, BRAKE, 0.0F), 4, -3, -5, 40, -15, 10, Ease.HOLD),
			// 0.42 제동하며 앞으로 튕김
			new Key(at(BRAKE_START, BRAKE, 0.7F), 1, 1, 3, -15, 0, 0, Ease.BACK_OUT),
			// 0.45 사격 자세
			new Key(at(SCATTER_START, SCATTER, 0.0F), 2, 0, 2, 0, 0, 0, Ease.QUAD_OUT),
			// 난사 스윕 — 벌림 · 교차 · 벌림 · 교차 (구간당 0.225초)
			new Key(at(SCATTER_START, SCATTER, 0.25F), 2, 0, 2, 0, -70, 0, Ease.SINE_IN_OUT),
			new Key(at(SCATTER_START, SCATTER, 0.50F), 2, 0, 2, 0, 40, 0, Ease.SINE_IN_OUT),
			new Key(at(SCATTER_START, SCATTER, 0.75F), 2, 0, 2, 0, -70, 0, Ease.SINE_IN_OUT),
			new Key(at(SCATTER_START, SCATTER, 1.00F), 2, 0, 2, 0, 40, 0, Ease.SINE_IN_OUT),
			// 1.53 총 한 바퀴 (roll -360 은 쌓인 각도로 — 0 으로 줄이지 않음)
			new Key(at(RECOVER_START, RECOVER, 0.72F), 1, 1, 0, 0, 0, -360, Ease.CUBIC_OUT),
			// 1.60 기본 자세로
			new Key(LENGTH, 0, 0, 0, 0, 0, -360, Ease.QUAD_IN_OUT)};

	/** 총 한 바퀴가 끝나는 시각 — 이 뒤로는 roll -360 을 0 으로 봅니다 (끝난 뒤 되돌아올 때 거꾸로 한 바퀴 돌지 않게). */
	private static final float SPIN_DONE = KEYS[KEYS.length - 2].at;

	/** 한 발 반동이 올라가는 시간 · 돌아오는 시간 (1/20초 단위 · 0.03초 / 0.07초). */
	private static final float KICK_UP = 0.6F;
	private static final float KICK_DOWN = 1.4F;
	/** 반동 — 총구가 8도 들리고 1픽셀 뒤로. */
	private static final float KICK_PITCH = 8.0F;
	private static final float KICK_BACK = 1.0F;

	private ScatterAnim() {}

	/**
	 * 한 손의 총 자세를 정합니다 (호출 전 poseStack 은 손 변환이 없는 기본 상태).
	 *
	 * @param invert 이 손이 오른손이면 1, 왼손이면 -1
	 * @param left   이 손이 왼손인가 — 반동 차례(오른손 → 왼손 번갈아)를 고릅니다
	 * @param e      재생 경과 (1/20초 단위)
	 * @param end    본편이 끝나는 시각 (끊기면 앞당겨짐)
	 * @param fade   끝난 뒤 기본 자세로 돌아오는 시간
	 */
	public static void apply(PoseStack pose, int invert, boolean left, float e, float end, float fade) {
		float t = Math.min(e, end);
		float[] v = sample(t);
		kick(v, left, t);
		if (e > end) {
			// 끊겼거나 끝남 — 기본 자세로 되돌아옴
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			float k = 1.0F - q * q * (3.0F - 2.0F * q);
			for (int i = 0; i < v.length; i++) {
				v[i] *= k;
			}
		}
		// 스펙 값 → 카메라 공간
		pose.translate(invert * (0.56F + v[0] / 16.0F), -0.52F + v[1] / 16.0F, -0.72F - v[2] / 16.0F);
		pose.mulPose(Axis.YP.rotationDegrees(invert * v[4]));
		pose.mulPose(Axis.XP.rotationDegrees(-v[3]));
		pose.mulPose(Axis.ZP.rotationDegrees(invert * v[5]));
	}

	/** 오른손 기준 스펙 값 {X, Y, Z, pitch, yaw, roll}. */
	static float[] sample(float t) {
		float[] out = new float[6];
		if (t <= KEYS[0].at) {
			return out;
		}
		Key a = KEYS[KEYS.length - 1];
		Key b = a;
		float s = 1.0F;
		for (int i = 0; i < KEYS.length - 1; i++) {
			if (t < KEYS[i + 1].at) {
				a = KEYS[i];
				b = KEYS[i + 1];
				float span = b.at - a.at;
				float x = span <= 0.0F ? 1.0F : Mth.clamp((t - a.at) / span, 0.0F, 1.0F);
				s = ease(b.ease, x);
				break;
			}
		}
		out[0] = Mth.lerp(s, a.x, b.x);
		out[1] = Mth.lerp(s, a.y, b.y);
		out[2] = Mth.lerp(s, a.z, b.z);
		out[3] = Mth.lerp(s, a.pitch, b.pitch);
		out[4] = Mth.lerp(s, a.yaw, b.yaw);
		out[5] = Mth.lerp(s, a.roll, b.roll);
		if (t >= SPIN_DONE) {
			out[5] += 360.0F;
		}
		return out;
	}

	/** 반동 겹치기 — 난사 중 0.05초마다 오른손 → 왼손 번갈아. 이 손이 마지막으로 쏜 발만 봅니다. */
	private static void kick(float[] v, boolean left, float t) {
		float into = t - SCATTER_START;
		if (into < 0.0F) {
			return;
		}
		int last = Math.min(SHOTS - 1, (int) Math.floor(into));
		// 이 손 차례 중 가장 최근 발 (짝수 = 오른손, 홀수 = 왼손)
		int mine = (last % 2 == 1) == left ? last : last - 1;
		if (mine < 0) {
			return;
		}
		float since = into - mine;
		float w;
		if (since < KICK_UP) {
			w = ease(Ease.EXPO_OUT, since / KICK_UP);
		} else if (since < KICK_UP + KICK_DOWN) {
			w = 1.0F - ease(Ease.QUAD_OUT, (since - KICK_UP) / KICK_DOWN);
		} else {
			return;
		}
		// pitch - = 총구 위, Z - = 뒤로
		v[3] -= KICK_PITCH * w;
		v[2] -= KICK_BACK * w;
	}

	private static float ease(Ease e, float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return switch (e) {
			case LINEAR -> x;
			case QUAD_OUT -> 1.0F - (1.0F - x) * (1.0F - x);
			case QUAD_IN -> x * x;
			case QUAD_IN_OUT -> x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 2) / 2.0F;
			case CUBIC_OUT -> 1.0F - (float) Math.pow(1.0F - x, 3);
			case EXPO_OUT -> x >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * x);
			case BACK_OUT -> {
				float c1 = 1.70158F;
				float c3 = c1 + 1.0F;
				yield 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
			}
			case SINE_IN_OUT -> -(Mth.cos(Mth.PI * x) - 1.0F) / 2.0F;
			case HOLD -> 0.0F;
		};
	}

	// ── 카메라 (스펙 PART 6-4) ──────────────────────────────

	/** 시야각 배율 키 {시각, 배율, 이징}. */
	private record Fov(float at, float mul, Ease ease) {}

	private static final Fov[] FOV = {
			new Fov(0.0F, 1.00F, Ease.LINEAR),
			new Fov(at(DASH_START, DASH, 0.0F), 0.96F, Ease.QUAD_OUT),
			new Fov(at(DASH_START, DASH, 0.4F), 1.25F, Ease.EXPO_OUT),
			new Fov(at(BRAKE_START, BRAKE, 0.0F), 1.25F, Ease.HOLD),
			// 난사 동안 넓은 시야를 유지 — 사방으로 쏘는 궤적이 화면에 다 들어오게
			new Fov(at(BRAKE_START, BRAKE, 0.7F), 1.18F, Ease.QUAD_OUT),
			new Fov(at(RECOVER_START, RECOVER, 0.0F), 1.18F, Ease.HOLD),
			new Fov(LENGTH, 1.00F, Ease.QUAD_IN_OUT)};

	/** 제동 순간 흔들림 (도) · 길이 (1/20초 단위 · 0.1초). */
	private static final float BRAKE_SHAKE = 0.30F;
	private static final float BRAKE_SHAKE_LEN = 2.0F;
	/** 한 발 흔들림 (도) · 길이 (0.04초). */
	private static final float SHOT_SHAKE = 0.08F;
	private static final float SHOT_SHAKE_LEN = 0.8F;

	/** 시야각 배율 (설정의 시야각 효과 세기를 곱하기 전). */
	public static float fov(float e, float end, float fade) {
		float t = Math.min(e, end);
		float m = FOV[FOV.length - 1].mul;
		for (int i = 0; i < FOV.length - 1; i++) {
			if (t < FOV[i + 1].at) {
				Fov a = FOV[i];
				Fov b = FOV[i + 1];
				float x = Mth.clamp((t - a.at) / Math.max(1.0E-3F, b.at - a.at), 0.0F, 1.0F);
				m = Mth.lerp(ease(b.ease, x), a.mul, b.mul);
				break;
			}
		}
		if (e > end) {
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			m = Mth.lerp(q, m, 1.0F);
		}
		return m;
	}

	/**
	 * 화면 흔들림 {가로축, 세로축} (도) — 제동 한 번 + 난사 한 발마다. 기울기(roll)는 없습니다.
	 * 실제 시선은 건드리지 않고 화면 행렬만 흔듭니다 (조준점이 가리키는 곳은 그대로).
	 */
	public static float[] shake(float e, float end) {
		if (e > end) {
			return new float[2];
		}
		float amp = 0.0F;
		float brake = e - at(BRAKE_START, BRAKE, 0.7F);
		if (brake >= 0.0F && brake < BRAKE_SHAKE_LEN) {
			amp = Math.max(amp, BRAKE_SHAKE * (1.0F - brake / BRAKE_SHAKE_LEN));
		}
		float into = e - SCATTER_START;
		if (into >= 0.0F && into < SHOTS) {
			float since = into - (float) Math.floor(into);
			if (since < SHOT_SHAKE_LEN) {
				amp = Math.max(amp, SHOT_SHAKE * (1.0F - since / SHOT_SHAKE_LEN));
			}
		}
		if (amp <= 0.0F) {
			return new float[2];
		}
		return new float[] {amp * Mth.sin(e * 9.1F), amp * Mth.sin(e * 7.3F + 1.7F)};
	}
}
