package kr.overbreak.client.hud;

import java.util.List;

import kr.overbreak.net.ScorePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치 방식 점수판 — 화면 위쪽 가운데.
 *
 *   1대1   [ 나 ] 2 : 1 [ 상대 ]  — 가운데 기울어진 판에 큰 숫자, 딴 점수만큼 아래에 칸이 채워집니다
 *   3대3   같은 모양에 [ 우리 팀 ] 파랑 : 빨강 [ 상대 팀 ], 아래에 라운드 · 생존 수
 *   대난투  위에 「대난투 · 먼저 50킬」, 아래에 순위표 (내 줄은 밝게)
 *   점수가 오른 순간 그 쪽 숫자가 잠깐 커지며 번쩍입니다.
 */
public final class ScoreHud {
	private static final int TOP = 6;
	private static final float SKEW = -0.22F;
	private static final int PANEL = 0xC00A0C12;
	private static final int MINE = 0xFF55E0FF;
	private static final int THEIRS = 0xFFFF6A6A;
	/** 팀전에서 우리 팀 — 테두리 색과 같은 파랑 ({@link MatchTeams#ALLY}). */
	private static final int TEAM_BLUE = 0xFF3FA2FF;
	private static final int DIM = 0xFF9AA0AC;

	private static ScorePayload state = ScorePayload.NONE;
	private static float ticks;
	private static float[] bumpAt = new float[0];
	private static int[] last = new int[0];

	private ScoreHud() {}

	public static void receive(ScorePayload msg) {
		if (msg.scores().size() != last.length) {
			last = new int[msg.scores().size()];
			bumpAt = new float[msg.scores().size()];
			java.util.Arrays.fill(bumpAt, -1000.0F);
			for (int i = 0; i < last.length; i++) {
				last[i] = msg.scores().get(i);
			}
		}
		for (int i = 0; i < last.length; i++) {
			int now = msg.scores().get(i);
			if (now > last[i]) {
				bumpAt[i] = ticks;
			}
			last[i] = now;
		}
		state = msg;
	}

	public static void tick(Minecraft mc) {
		ticks = (float) kr.overbreak.client.ClientClock.now();
		if (mc.level == null) {
			state = ScorePayload.NONE;
		}
	}

	/** 시험용. */
	public static ScorePayload state() {
		return state;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		ScorePayload s = state;
		if (mc.player == null || s.kind() == ScorePayload.NONE_KIND || s.names().isEmpty()) {
			return;
		}
		float t = ticks + kr.overbreak.client.ClientClock.partial(dt.getGameTimeDeltaPartialTick(false));
		if ((s.kind() == ScorePayload.DUEL || s.kind() == ScorePayload.TEAM) && s.names().size() >= 2) {
			duel(g, mc.font, s, t);
		} else {
			brawl(g, mc.font, s, t);
		}
	}

	// ── 1대1 ────────────────────────────────────────────────

	private static void duel(GuiGraphicsExtractor g, Font font, ScorePayload s, float t) {
		int cx = g.guiWidth() / 2;
		int selfIdx = Math.max(0, s.self());
		int otherIdx = selfIdx == 0 ? 1 : 0;
		int mine = s.scores().get(selfIdx);
		int theirs = s.scores().get(otherIdx);
		boolean team = s.kind() == ScorePayload.TEAM;
		int mineColor = team ? TEAM_BLUE : MINE;

		int boxW = 42;
		int boxH = 34;
		int gap = 10;
		panel(g, cx - boxW - gap / 2, TOP, boxW, boxH, PANEL);
		panel(g, cx + gap / 2, TOP, boxW, boxH, PANEL);
		bigNumber(g, font, String.valueOf(mine), cx - boxW / 2 - gap / 2, TOP + 7, mineColor, bump(selfIdx, t));
		bigNumber(g, font, String.valueOf(theirs), cx + boxW / 2 + gap / 2, TOP + 7, THEIRS, bump(otherIdx, t));
		g.text(font, ":", cx - font.width(":") / 2, TOP + 13, 0xFF6C7380);

		pips(g, cx - boxW - gap / 2 + 6, TOP + boxH - 6, boxW - 12, s.target(), mine, mineColor);
		pips(g, cx + gap / 2 + 6, TOP + boxH - 6, boxW - 12, s.target(), theirs, THEIRS);

		name(g, font, team ? s.names().get(selfIdx) : "나", cx - boxW - gap / 2 - 8, TOP + 12, true, mineColor);
		name(g, font, s.names().get(otherIdx), cx + boxW + gap / 2 + 8, TOP + 12, false, THEIRS);
		note(g, font, s.note(), TOP + boxH + 4);
	}

	private static void name(GuiGraphicsExtractor g, Font font, String text, int x, int y, boolean rightAligned, int color) {
		Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
		int w = font.width(c);
		g.text(font, c, rightAligned ? x - w : x, y, color);
	}

	/** 딴 점수만큼 채워지는 작은 칸. */
	private static void pips(GuiGraphicsExtractor g, int x, int y, int w, int target, int score, int color) {
		int n = Math.max(1, target);
		int gap = 2;
		int pw = Math.max(2, (w - (n - 1) * gap) / n);
		for (int i = 0; i < n; i++) {
			int x0 = x + i * (pw + gap);
			g.fill(x0, y, x0 + pw, y + 3, i < score ? color : 0x50FFFFFF);
		}
	}

	// ── 대난투 ──────────────────────────────────────────────

	private static void brawl(GuiGraphicsExtractor g, Font font, ScorePayload s, float t) {
		int rows = s.names().size();
		int w = 132;
		int rowH = 11;
		int h = 15 + rows * rowH + 4;
		int x = g.guiWidth() / 2 - w / 2;
		panel(g, x, TOP, w, h, PANEL);

		Component head = Component.literal("대난투").withStyle(ChatFormatting.BOLD);
		g.text(font, head, x + 6, TOP + 4, 0xFFFFC94A);
		String goal = "먼저 " + s.target() + "킬";
		g.text(font, goal, x + w - 6 - font.width(goal), TOP + 4, DIM);
		g.fill(x + 5, TOP + 14, x + w - 5, TOP + 15, 0x40FFFFFF);

		for (int i = 0; i < rows; i++) {
			int y = TOP + 18 + i * rowH;
			boolean me = i == s.self();
			int color = me ? MINE : 0xFFD8DBE2;
			if (me) {
				g.fill(x + 3, y - 2, x + w - 3, y + 9, 0x2055E0FF);
			}
			String rank = (i + 1) + ".";
			g.text(font, rank, x + 6, y, DIM);
			g.text(font, cut(font, s.names().get(i), w - 52), x + 20, y, color);
			Component score = Component.literal(String.valueOf(s.scores().get(i))).withStyle(ChatFormatting.BOLD);
			int sx = x + w - 8 - font.width(score);
			float b = bump(i, t);
			if (b > 0.01F) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(sx + font.width(score) / 2.0F, y + 4.0F);
				pose.scale(1.0F + 0.5F * b, 1.0F + 0.5F * b);
				g.text(font, score, -font.width(score) / 2, -4, 0xFFFFFFFF);
				pose.popMatrix();
			} else {
				g.text(font, score, sx, y, color);
			}
		}
		note(g, font, s.note(), TOP + h + 3);
	}

	private static String cut(Font font, String s, int max) {
		if (font.width(s) <= max) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && font.width(out + "…") > max) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}

	// ── 공용 ────────────────────────────────────────────────

	private static void note(GuiGraphicsExtractor g, Font font, String text, int y) {
		if (text.isEmpty()) {
			return;
		}
		g.text(font, text, g.guiWidth() / 2 - font.width(text) / 2, y, DIM);
	}

	/** 점수가 오른 뒤 0.5초 동안 1 → 0. */
	private static float bump(int i, float t) {
		if (i < 0 || i >= bumpAt.length) {
			return 0.0F;
		}
		return 1.0F - Mth.clamp((t - bumpAt[i]) / 10.0F, 0.0F, 1.0F);
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.fill(0, 0, w, h, color);
		g.fill(0, 0, w, 1, 0x30FFFFFF);
		pose.popMatrix();
	}

	/** 가운데 정렬 큰 숫자 (2배 크기). */
	private static void bigNumber(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color, float bump) {
		Component c = Component.literal(text).withStyle(ChatFormatting.BOLD);
		float scale = 2.0F + 0.6F * bump;
		int w = font.width(c);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, y + 5.0F);
		pose.scale(scale, scale);
		g.text(font, c, -w / 2, -4, bump > 0.01F ? 0xFFFFFFFF : color);
		pose.popMatrix();
	}

	/** 시험용: 목록을 비웁니다. */
	public static void reset() {
		state = ScorePayload.NONE;
		last = new int[0];
		bumpAt = new float[0];
	}
}
