package kr.overbreak.client.anim.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blockbench 키프레임 칸에 쓰는 간단한 식 (Molang 일부).
 *
 *   숫자 · + - * / · 괄호 · 음수
 *   변수 (q. 또는 query.):
 *     anim_time        애니메이션 시작부터 지난 초
 *     life_time        캐릭터가 존재한 초 (반복 동작용)
 *     head_x_rotation  머리 위아래 각도 (도, + = 아래를 봄) — 조준 방향 따라가기
 *     head_y_rotation  머리 좌우 각도 (도)
 *   함수 (math.): sin · cos (각도는 도) · abs · sqrt · min · max · clamp(v, 최소, 최대) · lerp(a, b, t) · pi
 *
 * 모르는 변수는 0 으로 봅니다 — Blockbench 미리보기에서도 머리 각도는 0 이라 같은 모습이 나옵니다.
 */
public final class Molang {
	/** 식 한 개. */
	public interface Expr {
		double eval(Context c);
	}

	/** 식에 넘기는 값. */
	public static final class Context {
		public double animTime;
		public double lifeTime;
		public double headX;
		public double headY;
	}

	private Molang() {}

	public static Expr constant(double v) {
		return c -> v;
	}

	public static Expr parse(String source) {
		String s = source.trim().toLowerCase(Locale.ROOT);
		if (s.endsWith(";")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.startsWith("return ")) {
			s = s.substring(7);
		}
		try {
			double v = Double.parseDouble(s);
			return constant(v);
		} catch (NumberFormatException ignored) {
			// 식
		}
		Parser p = new Parser(s);
		Expr e = p.expr();
		p.skip();
		if (p.pos < s.length()) {
			throw new IllegalArgumentException("식을 끝까지 읽지 못했습니다: '" + source + "' (" + (p.pos + 1) + "번째 글자)");
		}
		return e;
	}

	private static final class Parser {
		private final String s;
		private int pos;

		Parser(String s) {
			this.s = s;
		}

		void skip() {
			while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
				pos++;
			}
		}

		boolean eat(char c) {
			skip();
			if (pos < s.length() && s.charAt(pos) == c) {
				pos++;
				return true;
			}
			return false;
		}

		Expr expr() {
			Expr left = term();
			while (true) {
				if (eat('+')) {
					Expr a = left;
					Expr b = term();
					left = c -> a.eval(c) + b.eval(c);
				} else if (eat('-')) {
					Expr a = left;
					Expr b = term();
					left = c -> a.eval(c) - b.eval(c);
				} else {
					return left;
				}
			}
		}

		Expr term() {
			Expr left = unary();
			while (true) {
				if (eat('*')) {
					Expr a = left;
					Expr b = unary();
					left = c -> a.eval(c) * b.eval(c);
				} else if (eat('/')) {
					Expr a = left;
					Expr b = unary();
					left = c -> {
						double d = b.eval(c);
						return d == 0.0 ? 0.0 : a.eval(c) / d;
					};
				} else {
					return left;
				}
			}
		}

		Expr unary() {
			if (eat('-')) {
				Expr a = unary();
				return c -> -a.eval(c);
			}
			if (eat('+')) {
				return unary();
			}
			return primary();
		}

		Expr primary() {
			skip();
			if (eat('(')) {
				Expr e = expr();
				if (!eat(')')) {
					throw new IllegalArgumentException("')' 가 없습니다: '" + s + "'");
				}
				return e;
			}
			int start = pos;
			if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
				while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
					pos++;
				}
				double v = Double.parseDouble(s.substring(start, pos));
				return constant(v);
			}
			while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) {
				pos++;
			}
			String name = s.substring(start, pos);
			if (name.isEmpty()) {
				throw new IllegalArgumentException("읽을 수 없는 글자: '" + s + "' (" + (pos + 1) + "번째 글자)");
			}
			if (eat('(')) {
				List<Expr> args = new ArrayList<>();
				if (!eat(')')) {
					do {
						args.add(expr());
					} while (eat(','));
					if (!eat(')')) {
						throw new IllegalArgumentException("')' 가 없습니다: '" + s + "'");
					}
				}
				return function(name, args);
			}
			return variable(name);
		}

		private Expr function(String name, List<Expr> a) {
			String fn = name.startsWith("math.") ? name.substring(5) : name;
			return switch (fn) {
				case "sin" -> c -> Math.sin(Math.toRadians(arg(a, 0).eval(c)));
				case "cos" -> c -> Math.cos(Math.toRadians(arg(a, 0).eval(c)));
				case "abs" -> c -> Math.abs(arg(a, 0).eval(c));
				case "sqrt" -> c -> Math.sqrt(Math.max(0.0, arg(a, 0).eval(c)));
				case "min" -> c -> Math.min(arg(a, 0).eval(c), arg(a, 1).eval(c));
				case "max" -> c -> Math.max(arg(a, 0).eval(c), arg(a, 1).eval(c));
				case "clamp" -> c -> Math.max(arg(a, 1).eval(c), Math.min(arg(a, 2).eval(c), arg(a, 0).eval(c)));
				case "lerp" -> c -> {
					double x = arg(a, 0).eval(c);
					return x + (arg(a, 1).eval(c) - x) * arg(a, 2).eval(c);
				};
				default -> throw new IllegalArgumentException("모르는 함수: " + name);
			};
		}

		private static Expr arg(List<Expr> a, int i) {
			return i < a.size() ? a.get(i) : constant(0.0);
		}

		private static Expr variable(String name) {
			String v = name.startsWith("query.") ? name.substring(6) : name.startsWith("q.") ? name.substring(2) : name;
			return switch (v) {
				case "anim_time" -> c -> c.animTime;
				case "life_time" -> c -> c.lifeTime;
				case "head_x_rotation" -> c -> c.headX;
				case "head_y_rotation" -> c -> c.headY;
				case "math.pi", "pi" -> constant(Math.PI);
				default -> constant(0.0);
			};
		}
	}
}
