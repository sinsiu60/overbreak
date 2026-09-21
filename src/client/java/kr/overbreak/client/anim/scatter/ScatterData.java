package kr.overbreak.client.anim.scatter;

import static kr.overbreak.classes.gunslinger.DashScatter.BRAKE;
import static kr.overbreak.classes.gunslinger.DashScatter.BRAKE_START;
import static kr.overbreak.classes.gunslinger.DashScatter.DASH;
import static kr.overbreak.classes.gunslinger.DashScatter.DASH_START;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.WINDUP;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 돌진 난사 3인칭 동작 값 — assets/overbreak/animations/dash_scatter_3p.json (F3+T 로 다시 읽음).
 *
 * 파일의 시각은 스펙 표 그대로 "기본 단계 길이 기준 초" 입니다
 *   기 모으기 0~0.10 · 돌진 0.10~0.35 · 난사 들어가기 0.35~0.40 · 난사 0.40~1.25 · 마무리 1.25~1.50
 * 읽을 때 서버 {@code DashScatter} 의 단계로 비례해 옮깁니다 (단계 길이를 바꾸면 그 안의 키가 같이 늘고 줄어듦).
 * 스펙에는 없는 서버의 "제동" 단계(0.1초)가 스펙의 "난사 들어가기(스키드 · 팔 펼침)" 를 맡습니다 —
 * 그래서 연사는 서버가 판정을 시작하는 순간(난사 시작)에 첫 발이 나갑니다.
 *
 * 파일에 없는 값은 아래 기본값을 씁니다. 파일이 깨졌으면 로그에 남기고 이전 값을 유지합니다.
 */
public final class ScatterData {
	private static final Logger LOG = LoggerFactory.getLogger("overbreak/dash_scatter_3p");
	public static final Identifier FILE = Overbreak.id("animations/dash_scatter_3p.json");

	/** 이징. */
	public enum Ease {
		LINEAR, HOLD, QUAD_IN, QUAD_OUT, QUAD_IN_OUT, CUBIC_OUT, EXPO_OUT, BACK_OUT, SINE_IN_OUT;

		/** "quad_out" · "ease_out_quad" (스펙 표기 — 곡선 이름이 뒤) 둘 다 받음. */
		static Ease of(String s) {
			String n = s.toUpperCase(java.util.Locale.ROOT).replace("EASE_", "");
			try {
				return valueOf(n);
			} catch (IllegalArgumentException ex) {
				int cut = n.lastIndexOf('_');
				try {
					return cut < 0 ? LINEAR : valueOf(n.substring(cut + 1) + "_" + n.substring(0, cut));
				} catch (IllegalArgumentException ex2) {
					LOG.warn("모르는 이징 '{}' — linear 로 씁니다", s);
					return LINEAR;
				}
			}
		}

		public float apply(float x) {
			x = Mth.clamp(x, 0.0F, 1.0F);
			return switch (this) {
				case LINEAR -> x;
				case HOLD -> 0.0F;
				case QUAD_IN -> x * x;
				case QUAD_OUT -> 1.0F - (1.0F - x) * (1.0F - x);
				case QUAD_IN_OUT -> x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 2) / 2.0F;
				case CUBIC_OUT -> 1.0F - (float) Math.pow(1.0F - x, 3);
				case EXPO_OUT -> x >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * x);
				case BACK_OUT -> {
					float c1 = 1.70158F;
					float c3 = c1 + 1.0F;
					yield 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
				}
				case SINE_IN_OUT -> -(Mth.cos(Mth.PI * x) - 1.0F) / 2.0F;
			};
		}
	}

	/** 한 값의 키프레임 줄 — 시각은 서버 시간 단위(1/20초)로 옮겨 둡니다. */
	public static final class Channel {
		final float[] at;
		final float[] value;
		final Ease[] ease;

		Channel(float[] at, float[] value, Ease[] ease) {
			this.at = at;
			this.value = value;
			this.ease = ease;
		}

		/** 스펙 초 · 값 · 이징 묶음에서. */
		static Channel of(Object... keys) {
			int n = keys.length / 3;
			float[] at = new float[n];
			float[] v = new float[n];
			Ease[] e = new Ease[n];
			for (int i = 0; i < n; i++) {
				at[i] = toTime(((Number) keys[i * 3]).doubleValue());
				v[i] = ((Number) keys[i * 3 + 1]).floatValue();
				e[i] = (Ease) keys[i * 3 + 2];
			}
			return new Channel(at, v, e);
		}

		static Channel parse(JsonArray arr) {
			List<float[]> rows = new ArrayList<>();
			List<Ease> eases = new ArrayList<>();
			for (JsonElement el : arr) {
				JsonArray k = el.getAsJsonArray();
				rows.add(new float[] {toTime(k.get(0).getAsDouble()), k.get(1).getAsFloat()});
				eases.add(k.size() > 2 ? Ease.of(k.get(2).getAsString()) : Ease.LINEAR);
			}
			float[] at = new float[rows.size()];
			float[] v = new float[rows.size()];
			for (int i = 0; i < rows.size(); i++) {
				at[i] = rows.get(i)[0];
				v[i] = rows.get(i)[1];
			}
			return new Channel(at, v, eases.toArray(new Ease[0]));
		}

		/** 시각 t (1/20초 단위) 의 값. 키 사이는 뒤쪽 키의 이징. */
		public float sample(float t) {
			if (at.length == 0) {
				return 0.0F;
			}
			if (t <= at[0]) {
				return value[0];
			}
			for (int i = 0; i < at.length - 1; i++) {
				if (t < at[i + 1]) {
					float span = at[i + 1] - at[i];
					float x = span <= 0.0F ? 1.0F : (t - at[i]) / span;
					return Mth.lerp(ease[i + 1].apply(x), value[i], value[i + 1]);
				}
			}
			return value[value.length - 1];
		}

		/** 기울기 부호 (+1 / -1 / 0) — 루트가 지금 어느 쪽으로 돌고 있는가. */
		public float slope(float t) {
			float a = sample(t - 0.25F);
			float b = sample(t + 0.25F);
			return Math.signum(b - a);
		}

		/** 첫 키 · 마지막 키 시각. */
		public float first() {
			return at.length == 0 ? 0.0F : at[0];
		}

		public float last() {
			return at.length == 0 ? 0.0F : at[at.length - 1];
		}
	}

	/** 난사 한 발의 유형 (스펙 PART 3-3). */
	public record SprayType(String name, float weight, float pitchMin, float pitchMax, float yawMin, float yawMax,
							float recoil, float recoilScale) {}

	private static final Ease L = Ease.LINEAR;

	// ── 값 (기본값 = 스펙 표) ──────────────────────────────

	public static float armFireInterval = 0.06F;
	public static float armOffset = 0.03F;
	public static float snapTime = 0.02F;
	public static int shotsPerArm = 15;
	public static float gunRoll = 30.0F;
	public static float recoilPitch = -20.0F;
	public static float recoilUp = 0.015F;
	public static float recoilDown = 0.025F;
	public static List<SprayType> types = List.of(
			new SprayType("outer", 60, -150, -50, -90, -10, -20, 1.0F),
			new SprayType("sky", 15, -178, -155, -30, 0, 15, 1.0F),
			new SprayType("ground", 10, -45, -20, -60, -10, -20, 0.5F),
			new SprayType("across", 15, -100, -70, 30, 55, -20, 1.0F));

	public static Channel rootYaw = Channel.of(0.35, 0, L, 0.45, 60, Ease.QUAD_IN, 0.80, 540, L, 0.85, 560, Ease.QUAD_OUT,
			0.90, 520, Ease.QUAD_IN, 1.20, 200, L, 1.25, 180, Ease.CUBIC_OUT);
	public static Channel rootPitch = Channel.of(0.0, 0, L, 0.10, 15, Ease.QUAD_OUT, 0.15, 25, Ease.QUAD_IN, 0.35, 25, Ease.HOLD,
			0.40, -10, Ease.BACK_OUT, 0.85, -5, L, 0.90, -5, Ease.QUAD_IN_OUT, 1.20, 0, L, 1.25, 0, Ease.QUAD_OUT, 1.50, 0, L);
	public static Channel rootRoll = Channel.of(0.0, 0, L, 0.10, 0, Ease.QUAD_OUT, 0.35, 0, Ease.HOLD, 0.40, -15, Ease.BACK_OUT,
			0.85, -15, L, 0.90, 15, Ease.QUAD_IN_OUT, 1.20, 15, L, 1.25, 0, Ease.QUAD_OUT, 1.50, 0, L);
	public static Channel rootY = Channel.of(0.0, 0, L, 0.10, -1, Ease.QUAD_OUT, 0.35, -1, Ease.HOLD, 0.40, -2, Ease.BACK_OUT,
			0.90, -2, Ease.QUAD_IN_OUT, 1.20, -1, L, 1.25, 0, Ease.QUAD_OUT, 1.50, 0, L);
	/** 대쉬 시작 뒤 이만큼(초) 동안 대쉬 방향으로 돌아섬 · 마무리에서 실제 몸 방향으로 돌아오는 시간. */
	public static float dashSnap = 0.05F;
	public static float recoverTurn = 0.15F;

	public static float wobbleYaw = 6.0F;
	public static float wobblePitch = 3.0F;
	public static float wobbleUp = 0.012F;
	public static float wobbleDown = 0.018F;

	public static float twistAngle = 15.0F;
	public static float twistTime = 0.015F;

	public static float headDashPitch = -20.0F;
	public static float headYaw = 20.0F;
	public static float headPitchMin = -25.0F;
	public static float headPitchMax = 0.0F;
	public static float headSnap = 0.02F;

	/** 오른팔 기준 — 기 모으기 · 돌진 (난사 들어가기 · 마무리는 따로). */
	public static Channel armPitch = Channel.of(0.0, 0, L, 0.10, -80, Ease.QUAD_OUT, 0.15, 45, Ease.QUAD_IN, 0.35, 45, Ease.HOLD);
	public static Channel armYaw = Channel.of(0.0, 0, L, 0.10, 40, Ease.QUAD_OUT, 0.15, 0, Ease.QUAD_IN, 0.35, 0, Ease.HOLD);
	public static Channel armAbd = Channel.of(0.0, 0, L, 0.10, 0, Ease.QUAD_OUT, 0.15, 15, Ease.QUAD_IN, 0.35, 15, Ease.HOLD);
	/** 난사 들어가기 — 양팔을 양옆 위로 확 (오른팔 기준). */
	public static float entryPitch = -120.0F;
	public static float entryYaw = -60.0F;
	/** 마무리 — 양손 앞으로 모았다가 기본으로 (오른팔 기준, 스펙 1.33 · 1.50). */
	public static float gatherAt = 1.33F;
	public static float gatherPitch = -60.0F;
	public static float gatherYaw = 10.0F;

	public static Channel legRPitch = Channel.of(0.0, 0, L, 0.10, -15, Ease.QUAD_OUT, 0.15, -40, Ease.QUAD_IN, 0.35, -40, Ease.HOLD,
			0.40, -50, Ease.BACK_OUT, 0.60, -50, Ease.HOLD, 0.65, 0, Ease.QUAD_OUT, 1.25, 0, L, 1.50, 0, Ease.QUAD_IN_OUT);
	public static Channel legLPitch = Channel.of(0.0, 0, L, 0.10, 15, Ease.QUAD_OUT, 0.15, 40, Ease.QUAD_IN, 0.35, 40, Ease.HOLD,
			0.40, 25, Ease.BACK_OUT, 0.60, 25, Ease.HOLD, 0.65, 0, Ease.QUAD_OUT, 1.25, 0, L, 1.50, 0, Ease.QUAD_IN_OUT);
	public static Channel legAbd = Channel.of(0.0, 0, L, 0.60, 0, Ease.HOLD, 0.65, 15, Ease.QUAD_OUT, 1.25, 15, L, 1.50, 0, Ease.QUAD_IN_OUT);

	public static float stompFrom = 0.65F;
	public static float stompTo = 1.25F;
	public static float stompEvery = 0.10F;
	public static float stompLift = -25.0F;
	public static float stompOther = 10.0F;
	public static float stompUp = 0.04F;
	public static float stompDown = 0.06F;

	public static float airLeg = -45.0F;
	public static float airEnter = 0.05F;
	public static float airExit = 0.05F;

	/** 마무리 총 한 바퀴 (roll, 누적 각도). */
	public static Channel gunSpin = Channel.of(1.25, 0, L, 1.43, -360, Ease.CUBIC_OUT, 1.50, -360, L);

	/** 이 거리(칸) 밖에서는 머리 · 상체 비틀림 · 발구름 · 총 기울기를 생략. */
	public static float lodDistance = 32.0F;

	private ScatterData() {}

	/** 스펙 초 → 서버 시간 단위 (1/20초) — 단계 비례. */
	public static float toTime(double s) {
		if (s <= 0.10) {
			return (float) (s / 0.10 * WINDUP);
		}
		if (s <= 0.35) {
			return (float) (DASH_START + (s - 0.10) / 0.25 * DASH);
		}
		if (s <= 0.40) {
			return (float) (BRAKE_START + (s - 0.35) / 0.05 * BRAKE);
		}
		if (s <= 1.25) {
			return (float) (SCATTER_START + (s - 0.40) / 0.85 * SCATTER);
		}
		return (float) (RECOVER_START + (s - 1.25) / 0.25 * RECOVER);
	}

	/** 초 → 시간 단위 (길이 · 간격). */
	public static float units(float seconds) {
		return seconds * 20.0F;
	}

	// ── 다시 읽기 (F3+T) ───────────────────────────────────

	public static void init() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Overbreak.id("dash_scatter_3p"),
				(ResourceManagerReloadListener) ScatterData::reload);
	}

	private static void reload(ResourceManager rm) {
		rm.getResource(FILE).ifPresent(res -> {
			try (Reader r = res.openAsReader()) {
				load(JsonParser.parseReader(r).getAsJsonObject());
				LOG.info("돌진 난사 3인칭 동작 값을 읽었습니다");
			} catch (Exception ex) {
				LOG.warn("dash_scatter_3p.json 을 읽지 못했습니다 — 이전 값을 씁니다: {}", ex.toString());
			}
		});
	}

	static void load(JsonObject o) {
		JsonObject spray = obj(o, "spray");
		if (spray != null) {
			armFireInterval = f(spray, "armFireInterval", armFireInterval);
			armOffset = f(spray, "armOffset", armOffset);
			snapTime = f(spray, "snapTime", snapTime);
			shotsPerArm = (int) f(spray, "shotsPerArm", shotsPerArm);
			gunRoll = f(spray, "gunRoll", gunRoll);
			recoilPitch = f(spray, "recoil", recoilPitch);
			recoilUp = f(spray, "recoilUp", recoilUp);
			recoilDown = f(spray, "recoilDown", recoilDown);
			if (spray.has("types")) {
				List<SprayType> list = new ArrayList<>();
				for (JsonElement el : spray.getAsJsonArray("types")) {
					JsonObject t = el.getAsJsonObject();
					JsonArray p = t.getAsJsonArray("pitch");
					JsonArray y = t.getAsJsonArray("yaw");
					list.add(new SprayType(t.get("name").getAsString(), t.get("weight").getAsFloat(),
							p.get(0).getAsFloat(), p.get(1).getAsFloat(), y.get(0).getAsFloat(), y.get(1).getAsFloat(),
							f(t, "recoil", recoilPitch), f(t, "recoilScale", 1.0F)));
				}
				if (!list.isEmpty()) {
					types = List.copyOf(list);
				}
			}
		}
		JsonObject root = obj(o, "root");
		if (root != null) {
			rootYaw = ch(root, "yaw", rootYaw);
			rootPitch = ch(root, "pitch", rootPitch);
			rootRoll = ch(root, "roll", rootRoll);
			rootY = ch(root, "y", rootY);
			dashSnap = f(root, "dashSnap", dashSnap);
			recoverTurn = f(root, "recoverTurn", recoverTurn);
		}
		JsonObject wobble = obj(o, "wobble");
		if (wobble != null) {
			wobbleYaw = f(wobble, "yaw", wobbleYaw);
			wobblePitch = f(wobble, "pitch", wobblePitch);
			wobbleUp = f(wobble, "up", wobbleUp);
			wobbleDown = f(wobble, "down", wobbleDown);
		}
		JsonObject twist = obj(o, "twist");
		if (twist != null) {
			twistAngle = f(twist, "angle", twistAngle);
			twistTime = f(twist, "time", twistTime);
		}
		JsonObject head = obj(o, "head");
		if (head != null) {
			headDashPitch = f(head, "dashPitch", headDashPitch);
			headYaw = f(head, "yaw", headYaw);
			headPitchMin = f(head, "pitchMin", headPitchMin);
			headPitchMax = f(head, "pitchMax", headPitchMax);
			headSnap = f(head, "snap", headSnap);
		}
		JsonObject arms = obj(o, "arms");
		if (arms != null) {
			armPitch = ch(arms, "pitch", armPitch);
			armYaw = ch(arms, "yaw", armYaw);
			armAbd = ch(arms, "abd", armAbd);
			entryPitch = f(arms, "entryPitch", entryPitch);
			entryYaw = f(arms, "entryYaw", entryYaw);
			gatherAt = f(arms, "gatherAt", gatherAt);
			gatherPitch = f(arms, "gatherPitch", gatherPitch);
			gatherYaw = f(arms, "gatherYaw", gatherYaw);
		}
		JsonObject legs = obj(o, "legs");
		if (legs != null) {
			legRPitch = ch(legs, "rPitch", legRPitch);
			legLPitch = ch(legs, "lPitch", legLPitch);
			legAbd = ch(legs, "abd", legAbd);
		}
		JsonObject stomp = obj(o, "stomp");
		if (stomp != null) {
			stompFrom = f(stomp, "from", stompFrom);
			stompTo = f(stomp, "to", stompTo);
			stompEvery = f(stomp, "every", stompEvery);
			stompLift = f(stomp, "lift", stompLift);
			stompOther = f(stomp, "other", stompOther);
			stompUp = f(stomp, "up", stompUp);
			stompDown = f(stomp, "down", stompDown);
		}
		JsonObject air = obj(o, "air");
		if (air != null) {
			airLeg = f(air, "legPitch", airLeg);
			airEnter = f(air, "enter", airEnter);
			airExit = f(air, "exit", airExit);
		}
		if (o.has("gunSpin")) {
			gunSpin = Channel.parse(o.getAsJsonArray("gunSpin"));
		}
		lodDistance = f(o, "lodDistance", lodDistance);
	}

	private static JsonObject obj(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
	}

	private static float f(JsonObject o, String key, float def) {
		return o.has(key) ? o.get(key).getAsFloat() : def;
	}

	private static Channel ch(JsonObject o, String key, Channel def) {
		return o.has(key) && o.get(key).isJsonArray() ? Channel.parse(o.getAsJsonArray(key)) : def;
	}
}
