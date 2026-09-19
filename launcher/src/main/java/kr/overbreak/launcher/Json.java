package kr.overbreak.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 아주 작은 JSON 읽기 · 쓰기. 바깥 라이브러리를 쓰지 않기 위해 직접 둡니다.
 *
 * 읽으면 Map&lt;String,Object&gt; · List&lt;Object&gt; · String · Double · Boolean · null 로 나옵니다.
 * 쓸 때는 넣은 순서를 지킵니다 (launcher_profiles.json 을 다시 써도 사람이 보기 좋게).
 */
public final class Json {
	private final String src;
	private int at;

	private Json(String src) {
		this.src = src;
	}

	public static Object parse(String text) {
		Json j = new Json(text);
		j.ws();
		Object v = j.value();
		j.ws();
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> object(String text) {
		Object v = parse(text);
		return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
	}

	/** 중첩된 값 꺼내기 — get(map, "mod", "url"). 없으면 null. */
	@SuppressWarnings("unchecked")
	public static Object get(Object root, String... path) {
		Object cur = root;
		for (String key : path) {
			if (!(cur instanceof Map)) {
				return null;
			}
			cur = ((Map<String, Object>) cur).get(key);
		}
		return cur;
	}

	public static String str(Object root, String... path) {
		Object v = get(root, path);
		return v == null ? null : String.valueOf(v);
	}

	// ── 읽기 ────────────────────────────────────────────────

	private void ws() {
		while (at < src.length() && Character.isWhitespace(src.charAt(at))) {
			at++;
		}
	}

	private Object value() {
		ws();
		if (at >= src.length()) {
			return null;
		}
		char c = src.charAt(at);
		return switch (c) {
			case '{' -> map();
			case '[' -> list();
			case '"' -> string();
			case 't' -> word("true", Boolean.TRUE);
			case 'f' -> word("false", Boolean.FALSE);
			case 'n' -> word("null", null);
			default -> number();
		};
	}

	private Object word(String w, Object v) {
		at += w.length();
		return v;
	}

	private Map<String, Object> map() {
		Map<String, Object> out = new LinkedHashMap<>();
		at++; // {
		ws();
		if (at < src.length() && src.charAt(at) == '}') {
			at++;
			return out;
		}
		while (at < src.length()) {
			ws();
			String key = string();
			ws();
			at++; // :
			out.put(key, value());
			ws();
			if (at >= src.length() || src.charAt(at) == '}') {
				at++;
				break;
			}
			at++; // ,
		}
		return out;
	}

	private List<Object> list() {
		List<Object> out = new ArrayList<>();
		at++; // [
		ws();
		if (at < src.length() && src.charAt(at) == ']') {
			at++;
			return out;
		}
		while (at < src.length()) {
			out.add(value());
			ws();
			if (at >= src.length() || src.charAt(at) == ']') {
				at++;
				break;
			}
			at++; // ,
		}
		return out;
	}

	private String string() {
		StringBuilder b = new StringBuilder();
		at++; // "
		while (at < src.length()) {
			char c = src.charAt(at++);
			if (c == '"') {
				break;
			}
			if (c != '\\') {
				b.append(c);
				continue;
			}
			char e = src.charAt(at++);
			switch (e) {
				case 'n' -> b.append('\n');
				case 't' -> b.append('\t');
				case 'r' -> b.append('\r');
				case 'b' -> b.append('\b');
				case 'f' -> b.append('\f');
				case 'u' -> {
					b.append((char) Integer.parseInt(src.substring(at, at + 4), 16));
					at += 4;
				}
				default -> b.append(e);
			}
		}
		return b.toString();
	}

	private Object number() {
		int start = at;
		while (at < src.length() && "+-.eE0123456789".indexOf(src.charAt(at)) >= 0) {
			at++;
		}
		return Double.valueOf(src.substring(start, at));
	}

	// ── 쓰기 ────────────────────────────────────────────────

	public static String write(Object v) {
		StringBuilder b = new StringBuilder();
		write(v, b, 0);
		return b.toString();
	}

	@SuppressWarnings("unchecked")
	private static void write(Object v, StringBuilder b, int depth) {
		switch (v) {
			case null -> b.append("null");
			case Map<?, ?> m -> {
				b.append("{\n");
				int i = 0;
				for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
					indent(b, depth + 1);
					quote(e.getKey(), b);
					b.append(": ");
					write(e.getValue(), b, depth + 1);
					if (++i < m.size()) {
						b.append(',');
					}
					b.append('\n');
				}
				indent(b, depth);
				b.append('}');
			}
			case List<?> l -> {
				b.append('[');
				for (int i = 0; i < l.size(); i++) {
					if (i > 0) {
						b.append(", ");
					}
					write(l.get(i), b, depth);
				}
				b.append(']');
			}
			case String s -> quote(s, b);
			case Boolean bool -> b.append(bool);
			case Double d -> b.append(d == Math.floor(d) && !d.isInfinite() ? String.valueOf(d.longValue()) : d.toString());
			default -> b.append(v);
		}
	}

	private static void indent(StringBuilder b, int depth) {
		b.append("  ".repeat(depth));
	}

	private static void quote(String s, StringBuilder b) {
		b.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
				}
			}
		}
		b.append('"');
	}
}
