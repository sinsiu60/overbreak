package kr.overbreak.client.hud.crosshair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/**
 * 커스텀 조준점 그리기 — 실제 화면 픽셀 단위 (GUI 배율을 되돌려 그림). 게임 화면과 설정 화면 미리보기가 같이 씁니다.
 *
 *   십자 : 가운데에서 간격만큼 떨어진 네 갈래 (두께 · 길이)
 *   원   : 반지름 · 두께의 고리
 *   점   : 가운데 점만 (가운데 점 설정은 모든 모양에 더해짐)
 *   테두리: 모든 선을 1픽셀 넓게 검게 먼저 깔아 밝은 배경에서도 보이게
 */
public final class CrosshairRenderer {
	private CrosshairRenderer() {}

	/**
	 * @param cx cy 가운데 (GUI 좌표)
	 */
	public static void draw(GuiGraphicsExtractor g, float cx, float cy, CrosshairConfig c) {
		int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(1.0F / scale, 1.0F / scale);
		int col = ARGB.color(Math.round(c.opacity * 2.55F), c.color);
		int line = ARGB.color(Math.round(c.outline * 2.55F * c.opacity / 100.0F), 0x000000);
		// 두 번 — 테두리(검게, 1픽셀 넓게) 먼저, 그 위에 본 색
		for (int pass = 0; pass < 2; pass++) {
			boolean edge = pass == 0;
			if (edge && c.outline <= 0) {
				continue;
			}
			int color = edge ? line : col;
			int o = edge ? 1 : 0;
			switch (c.type) {
				case CROSS -> cross(g, c, color, o);
				case CIRCLE -> circle(g, c, color, o);
				case CIRCLE_CROSS -> {
					cross(g, c, color, o);
					circle(g, c, color, o);
				}
				default -> {
				}
			}
			int dot = c.type == CrosshairConfig.Type.DOT ? Math.max(c.dotSize, 2) : c.dotSize;
			if (dot > 0) {
				int dc = edge ? ARGB.color(Math.round(c.outline * 2.55F * c.dotOpacity / 100.0F), 0x000000)
						: ARGB.color(Math.round(c.dotOpacity * 2.55F), c.color);
				int h0 = -dot / 2;
				g.fill(h0 - o, h0 - o, h0 + dot + o, h0 + dot + o, dc);
			}
		}
		pose.popMatrix();
	}

	private static void cross(GuiGraphicsExtractor g, CrosshairConfig c, int color, int o) {
		if (c.length <= 0) {
			return;
		}
		int t = c.thickness;
		int a = -t / 2;
		int gap = c.gap;
		int len = c.length;
		g.fill(gap - o, a - o, gap + len + o, a + t + o, color);
		g.fill(-gap - len - o, a - o, -gap + o, a + t + o, color);
		g.fill(a - o, gap - o, a + t + o, gap + len + o, color);
		g.fill(a - o, -gap - len - o, a + t + o, -gap + o, color);
	}

	private static void circle(GuiGraphicsExtractor g, CrosshairConfig c, int color, int o) {
		// 줄마다 고리의 좌우 구간을 채움 — 겹쳐 그리지 않아 반투명이어도 고르게 보임
		double outer = c.radius + c.thickness / 2.0 + o;
		double inner = Math.max(0.0, c.radius - c.thickness / 2.0 - o);
		int top = (int) Math.ceil(outer);
		for (int y = -top; y < top; y++) {
			double yc = y + 0.5;
			if (Math.abs(yc) > outer) {
				continue;
			}
			int xo = (int) Math.round(Math.sqrt(outer * outer - yc * yc));
			if (Math.abs(yc) >= inner) {
				g.fill(-xo, y, xo, y + 1, color);
			} else {
				int xi = (int) Math.round(Math.sqrt(inner * inner - yc * yc));
				g.fill(-xo, y, -xi, y + 1, color);
				g.fill(xi, y, xo, y + 1, color);
			}
		}
	}
}
