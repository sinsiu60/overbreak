package kr.overbreak.client.anim.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

/**
 * Blockbench 에서 내보낸 애니메이션 한 개 (Bedrock Animation 형식 .animation.json).
 *
 *   뼈대마다 rotation(도) · position(픽셀) · scale 채널이 있고, 채널은 시간(초) → 값 키프레임입니다.
 *   키프레임 값은 숫자 또는 식({@link Molang}), pre/post 로 순간 전환, lerp_mode "catmullrom" 은 부드러운 곡선.
 *   키프레임이 없는 뼈대 · 채널은 건드리지 않습니다 (바닐라 · 기본 자세 그대로).
 *   "timeline" (Blockbench 효과 탭의 Instructions) 에 {@code v.이름 = 숫자;} 를 적으면 그 시각부터 값이 바뀝니다
 *   — 예: 발키리 재장전의 {@code v.magazine_out} (1 = 탄창이 총에서 빠져 손에 있음).
 */
public final class PlayerAnimation {
	public static final String ROTATION = "rotation";
	public static final String POSITION = "position";
	public static final String SCALE = "scale";

	private record Keyframe(double time, Molang.Expr[] pre, Molang.Expr[] post, boolean smooth) {}

	private static final class Channel {
		final List<Keyframe> keys = new ArrayList<>();

		double[] sample(double t, Molang.Context c) {
			if (keys.size() == 1 || t <= keys.getFirst().time) {
				return eval(keys.getFirst().pre, c);
			}
			Keyframe last = keys.getLast();
			if (t >= last.time) {
				return eval(last.post, c);
			}
			for (int i = 0; i < keys.size() - 1; i++) {
				Keyframe a = keys.get(i);
				Keyframe b = keys.get(i + 1);
				if (t < b.time) {
					double f = (t - a.time) / Math.max(1.0E-6, b.time - a.time);
					double[] va = eval(a.post, c);
					double[] vb = eval(b.pre, c);
					double[] out = new double[3];
					if (a.smooth || b.smooth) {
						double[] v0 = i > 0 ? eval(keys.get(i - 1).post, c) : va;
						double[] v3 = i + 2 < keys.size() ? eval(keys.get(i + 2).pre, c) : vb;
						for (int k = 0; k < 3; k++) {
							out[k] = catmullRom(v0[k], va[k], vb[k], v3[k], f);
						}
					} else {
						for (int k = 0; k < 3; k++) {
							out[k] = va[k] + (vb[k] - va[k]) * f;
						}
					}
					return out;
				}
			}
			return eval(last.post, c);
		}

		private static double[] eval(Molang.Expr[] v, Molang.Context c) {
			return new double[] {v[0].eval(c), v[1].eval(c), v[2].eval(c)};
		}

		private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
		}
	}

	public final String name;
	public final double length;
	public final boolean loop;
	/** 뼈대 이름 → (채널 이름 → 채널). */
	private final Map<String, Map<String, Channel>> bones = new HashMap<>();
	/** 시각(초) → 그 시각에 바꾸는 변수들. */
	private final java.util.TreeMap<Double, Map<String, Double>> timeline = new java.util.TreeMap<>();

	private PlayerAnimation(String name, double length, boolean loop) {
		this.name = name;
		this.length = length;
		this.loop = loop;
	}

	public boolean has(String bone) {
		return bones.containsKey(bone);
	}

	/** 초 → 애니메이션 안의 시간 (반복이면 되감고, 아니면 끝에서 멈춤). */
	public double localTime(double seconds) {
		if (length <= 0.0) {
			return seconds;
		}
		return loop ? seconds % length : Math.min(seconds, length);
	}

	/** timeline 변수 값: 이 시각까지 마지막으로 적힌 값, 없으면 def. */
	public double variable(String name, double seconds, double def) {
		double t = localTime(seconds) + 1.0E-6;
		double v = def;
		for (Map.Entry<Double, Map<String, Double>> e : timeline.headMap(t, true).entrySet()) {
			Double set = e.getValue().get(name);
			if (set != null) {
				v = set;
			}
		}
		return v;
	}

	/** 뼈대 · 채널 값 (x, y, z). 키프레임이 없으면 null. */
	public double @Nullable [] sample(String bone, String channel, double seconds, Molang.Context c) {
		Map<String, Channel> ch = bones.get(bone);
		Channel k = ch == null ? null : ch.get(channel);
		if (k == null || k.keys.isEmpty()) {
			return null;
		}
		c.animTime = seconds;
		return k.sample(localTime(seconds), c);
	}

	// ── 읽기 ────────────────────────────────────────────────

	/**
	 * 파일 하나 → 이름(앞의 "animation." · "animation.overbreak." 를 뗀 것) → 애니메이션.
	 * @throws IllegalArgumentException 형식이 틀리면 어디가 틀렸는지 담아 던집니다
	 */
	public static Map<String, PlayerAnimation> parseFile(JsonObject root) {
		JsonObject anims = root.getAsJsonObject("animations");
		if (anims == null) {
			throw new IllegalArgumentException("\"animations\" 가 없습니다 (Blockbench → Export → Bedrock Animation 으로 내보낸 파일이어야 합니다)");
		}
		Map<String, PlayerAnimation> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
			String full = e.getKey();
			JsonObject a = e.getValue().getAsJsonObject();
			double length = a.has("animation_length") ? a.get("animation_length").getAsDouble() : 0.0;
			boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive()
					&& (a.get("loop").getAsJsonPrimitive().isBoolean() ? a.get("loop").getAsBoolean() : "true".equals(a.get("loop").getAsString()));
			PlayerAnimation anim = new PlayerAnimation(shortName(full), length, loop);
			if (a.has("timeline")) {
				try {
					timeline(anim, a.getAsJsonObject("timeline"));
				} catch (RuntimeException ex) {
					throw new IllegalArgumentException(full + " / timeline: " + ex.getMessage(), ex);
				}
			}
			JsonObject bones = a.getAsJsonObject("bones");
			if (bones != null) {
				for (Map.Entry<String, JsonElement> b : bones.entrySet()) {
					Map<String, Channel> channels = new HashMap<>();
					JsonObject bone = b.getValue().getAsJsonObject();
					for (String ch : new String[] {ROTATION, POSITION, SCALE}) {
						if (bone.has(ch)) {
							try {
								channels.put(ch, channel(bone.get(ch), SCALE.equals(ch)));
							} catch (RuntimeException ex) {
								throw new IllegalArgumentException(full + " / " + b.getKey() + " / " + ch + ": " + ex.getMessage(), ex);
							}
						}
					}
					anim.bones.put(b.getKey().toLowerCase(java.util.Locale.ROOT), channels);
				}
			}
			out.put(anim.name, anim);
		}
		return out;
	}

	/** {@code "0.3": "v.magazine_out = 1;"} 또는 문장 배열. 변수 대입만 읽고 나머지 문장은 무시합니다. */
	private static void timeline(PlayerAnimation anim, JsonObject t) {
		for (Map.Entry<String, JsonElement> e : t.entrySet()) {
			double time = Double.parseDouble(e.getKey());
			List<String> lines = new ArrayList<>();
			if (e.getValue().isJsonArray()) {
				e.getValue().getAsJsonArray().forEach(x -> lines.add(x.getAsString()));
			} else {
				lines.add(e.getValue().getAsString());
			}
			Map<String, Double> sets = anim.timeline.computeIfAbsent(time, k -> new HashMap<>());
			for (String line : lines) {
				for (String stmt : line.split(";")) {
					int eq = stmt.indexOf('=');
					if (eq <= 0) {
						continue;
					}
					String name = stmt.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
					if (name.startsWith("variable.")) {
						name = name.substring(9);
					} else if (name.startsWith("v.")) {
						name = name.substring(2);
					} else {
						continue;
					}
					sets.put(name, Molang.parse(stmt.substring(eq + 1)).eval(new Molang.Context()));
				}
			}
		}
	}

	public static String shortName(String full) {
		String n = full.toLowerCase(java.util.Locale.ROOT);
		if (n.startsWith("animation.overbreak.")) {
			return n.substring("animation.overbreak.".length());
		}
		return n.startsWith("animation.") ? n.substring("animation.".length()) : n;
	}

	private static Channel channel(JsonElement el, boolean scale) {
		Channel c = new Channel();
		if (el.isJsonObject() && !el.getAsJsonObject().has("vector")) {
			for (Map.Entry<String, JsonElement> k : el.getAsJsonObject().entrySet()) {
				double time = Double.parseDouble(k.getKey());
				JsonElement v = k.getValue();
				if (v.isJsonObject()) {
					JsonObject o = v.getAsJsonObject();
					Molang.Expr[] post = o.has("post") ? vector(o.get("post"), scale) : o.has("vector") ? vector(o.get("vector"), scale) : null;
					Molang.Expr[] pre = o.has("pre") ? vector(o.get("pre"), scale) : post;
					if (post == null) {
						post = pre;
					}
					if (pre == null) {
						throw new IllegalArgumentException(k.getKey() + "초 키프레임에 값이 없습니다");
					}
					boolean smooth = o.has("lerp_mode") && "catmullrom".equals(o.get("lerp_mode").getAsString());
					c.keys.add(new Keyframe(time, pre, post, smooth));
				} else {
					Molang.Expr[] vec = vector(v, scale);
					c.keys.add(new Keyframe(time, vec, vec, false));
				}
			}
			c.keys.sort(Comparator.comparingDouble(Keyframe::time));
		} else {
			JsonElement v = el.isJsonObject() ? el.getAsJsonObject().get("vector") : el;
			Molang.Expr[] vec = vector(v, scale);
			c.keys.add(new Keyframe(0.0, vec, vec, false));
		}
		return c;
	}

	private static Molang.Expr[] vector(JsonElement el, boolean scale) {
		if (el.isJsonArray()) {
			JsonArray a = el.getAsJsonArray();
			Molang.Expr[] out = new Molang.Expr[3];
			for (int i = 0; i < 3; i++) {
				out[i] = i < a.size() ? value(a.get(i)) : Molang.constant(scale ? 1.0 : 0.0);
			}
			return out;
		}
		Molang.Expr one = value(el);
		return new Molang.Expr[] {one, one, one};
	}

	private static Molang.Expr value(JsonElement el) {
		if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
			return Molang.constant(el.getAsDouble());
		}
		return Molang.parse(el.getAsString());
	}
}
