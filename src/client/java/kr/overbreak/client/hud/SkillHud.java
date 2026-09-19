package kr.overbreak.client.hud;

import kr.overbreak.Overbreak;
import kr.overbreak.client.input.InputMode;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치 방식 스킬 HUD.
 *
 *   오른쪽 아래: [무기 한 칸] [우클릭] [웅크리기] [F] — 기울어진 칸에 흰색 단색 아이콘, 칸 아래 키 표시
 *     준비     : 밝은 아이콘
 *     쿨타임   : 칸이 어두워지고 아이콘이 회색, 남은 초(1초 미만은 소수 한 자리) + 아래 진행 막대
 *     효과 중  : 직업 색 테두리 · 아이콘
 *     사용 순간: 하얗게 번쩍이며 살짝 커짐 / 쿨타임 중 누름: 빨갛게 흔들림
 *   가운데 아래: 궁극기 게이지 원 (충전 % → 준비되면 아이콘이 금색으로 맥동, 옆에 Q)
 *
 * 조작 모드(직업 있음)일 때만 그리고, 바닐라 핫바 · 경험치 막대는 숨깁니다.
 */
public final class SkillHud {
	private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
	private static final Identifier HOTBAR_SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");

	private static final int PANEL_W = 30;
	private static final int PANEL_H = 26;
	private static final int GAP = 5;
	private static final int MARGIN = 12;
	private static final int ICON = 20;
	private static final float SKEW = -0.22F;
	private static final int ULT_GOLD = 0xFFFFC94A;

	private SkillHud() {}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, dt) -> {
			if (!InputMode.active()) {
				vanilla.extractRenderState(g, dt);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, vanilla -> (g, dt) -> {
			if (!InputMode.active()) {
				vanilla.extractRenderState(g, dt);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, vanilla -> (g, dt) -> {
			if (!InputMode.active()) {
				vanilla.extractRenderState(g, dt);
			}
		});
		for (var element : new net.minecraft.resources.Identifier[] {
				VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.ARMOR_BAR}) {
			HudElementRegistry.replaceElement(element, vanilla -> (g, dt) -> {
				if (!InputMode.active()) {
					vanilla.extractRenderState(g, dt);
				}
			});
		}
		// 채팅 위에 그려서 채팅 줄이 체력 숫자를 가리지 않게
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Overbreak.id("player_health"), PlayerHealthHud::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PlayerHealthHud::tick);
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, vanilla -> (g, dt) -> {
			if (!InputMode.active()) {
				vanilla.extractRenderState(g, dt);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Overbreak.id("skill_hud"), SkillHud::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Overbreak.id("hit_marker"), HitMarker::render);
		// 머리 위 체력바는 조준점 아래에 깔리게
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("health_bars"), HealthBars::render);
		// 황야의 무법자 조준 화면 (가장자리 어둠 · 조준 표식) — 체력바보다 먼저 깔아 표식이 위에 오게
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("deadeye"), DeadeyeHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("shade_screen"), ShadeScreen::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("thunder_screen"), ThunderScreen::render);
		// 피격 피드백 (방향 표시 · 붉은 가장자리) — 조준점 아래, 화면 효과보다 위
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("damage_feedback"), DamageFeedback::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(DamageFeedback::tick);
		// 공격속도 증가 — 화면 가장자리 금색
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, Overbreak.id("haste_screen"), HasteScreen::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(HasteScreen::tick);
		// 경기 점수판 (화면 위쪽 가운데) — 채팅 위에
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Overbreak.id("score_hud"), ScoreHud::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(ScoreHud::tick);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(DeadeyeHud::tick);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(HealthBars::tick);
	}

	/** 스킬 칸 i 의 위쪽 가운데 (튜토리얼 화살표가 가리킬 곳). */
	public static int[] slotAnchor(int i, int w, int h) {
		int n = 3;
		int y = h - MARGIN - 8 - PANEL_H;
		int firstX = w - MARGIN - n * PANEL_W - (n - 1) * GAP;
		return new int[] {firstX + i * (PANEL_W + GAP) + PANEL_W / 2, y};
	}

	/** 궁극기 원의 위쪽 가운데. */
	public static int[] ultAnchor(int w, int h) {
		return new int[] {w / 2, h - 18 - 14};
	}

	/** 무기 칸의 위쪽 가운데. */
	public static int[] weaponAnchor(int w, int h) {
		int y = h - MARGIN - 8 - PANEL_H;
		return new int[] {w - MARGIN - 22 + 10, y - 8 - 22};
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (!InputMode.active() || mc.player == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		HudLayouts.Layout layout = HudLayouts.get(HudState.classId);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int n = layout == null ? 0 : Math.min(layout.slots().size(), HudState.remaining.length);

		int labelH = 8;
		int y = h - MARGIN - labelH - PANEL_H;
		int firstX = w - MARGIN - n * PANEL_W - Math.max(0, n - 1) * GAP;

		// 무기 한 칸은 스킬 칸 위 오른쪽 끝 (좁은 화면에서 허기 막대와 겹치지 않게)
		weaponSlot(g, mc.player, w - MARGIN - 22, y - 8 - 22);
		ammo(g, mc.font, w - MARGIN - 22 - 7, y - 8 - 22, partial);
		if (HudState.stacksMax > 0) {
			stacks(g, w / 2, h / 2 + 14, partial);
		}
		if (HudState.meter >= 0) {
			meter(g, w / 2, h / 2 + (HudState.stacksMax > 0 ? 22 : 14), partial);
		}
		for (int i = 0; i < n; i++) {
			panel(g, mc.font, layout, i, firstX + i * (PANEL_W + GAP), y, partial);
		}
		if (layout != null && HudState.ultState != 0) {
			ult(g, mc.font, layout, w / 2, h - 18, partial);
		}
	}

	/** 핫바 첫 칸 모양만 잘라 무기 한 칸을 그립니다. */
	private static void weaponSlot(GuiGraphicsExtractor g, Player player, int x, int y) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, x, y, 21, 22);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, x + 21, y, 1, 22);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION, x - 1, y - 1, 24, 23);
		ItemStack stack = player.getInventory().getItem(0);
		g.item(player, stack, x + 3, y + 3, 1);
		g.itemDecorations(Minecraft.getInstance().font, stack, x + 3, y + 3);
	}

	/** 탄창 — 무기 칸 왼쪽. 큰 숫자(남은 탄) + 작은 "/탄창" + 탄 칸. 쏜 순간 숫자가 살짝 커졌다 돌아옴, 0 이면 빨강. */
	private static void ammo(GuiGraphicsExtractor g, Font font, int right, int top, float partial) {
		if (HudState.ammo < 0 || HudState.ammoMax <= 0) {
			return;
		}
		float kick = 1.0F - Mth.clamp((HudState.ticks - HudState.shotAt + kr.overbreak.client.ClientClock.partial(partial)) / 5.0F, 0.0F, 1.0F);
		// 탄창(몇 발짜리)만 0 이면 빨갛게 — 발키리 미사일 카운터(10)는 0 이 평상시
		int color = HudState.ammo == 0 && HudState.ammoMax < 10 ? 0xFFFF5A5A : 0xFFFFFFFF;
		String max = "/" + HudState.ammoMax;
		int maxW = font.width(max);
		g.text(font, max, right - maxW, top + 9, 0xFFA8ADB8);

		Component cur = Component.literal(String.valueOf(HudState.ammo)).withStyle(ChatFormatting.BOLD);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(right - maxW - 2, top + 17);
		float s = 2.0F + 0.35F * kick;
		pose.scale(s, s);
		g.text(font, cur, -font.width(cur), -8, color);
		pose.popMatrix();

		if (HudState.ammoMax > 10) {
			// 50발 같은 큰 탄창은 칸 대신 숫자만
			return;
		}
		int pipW = 5;
		int gap = 2;
		int total = HudState.ammoMax * pipW + (HudState.ammoMax - 1) * gap;
		for (int i = 0; i < HudState.ammoMax; i++) {
			int x0 = right - total + i * (pipW + gap);
			g.fill(x0, top + 20, x0 + pipW, top + 23, i < HudState.ammo ? 0xFFFFFFFF : 0x50FFFFFF);
		}
	}

	/** 조준점 아래 가로 칸 — 스택 (발키리: 명중 5번마다 미사일). 다 차서 터지면 칸 전체가 주황빛으로 번쩍. */
	private static void stacks(GuiGraphicsExtractor g, int cx, int top, float partial) {
		int n = Math.min(10, HudState.stacksMax);
		int cellW = 9;
		int cellH = 4;
		int gap = 2;
		int total = n * cellW + (n - 1) * gap;
		int x0 = cx - total / 2;
		float burst = 1.0F - Mth.clamp((HudState.ticks - HudState.stackBurstAt + kr.overbreak.client.ClientClock.partial(partial)) / 8.0F, 0.0F, 1.0F);
		for (int i = 0; i < n; i++) {
			int x = x0 + i * (cellW + gap);
			g.fill(x - 1, top - 1, x + cellW + 1, top + cellH + 1, 0x90000000);
			int color = burst > 0.0F ? alpha(0xFF8C3C, 0.35F + 0.65F * burst)
					: i < HudState.stacks ? 0xFFFFC94A : 0x40FFFFFF;
			g.fill(x, top, x + cellW, top + cellH, color);
		}
	}

	/** 조준점 아래 게이지 — 로켓 펀치 충전(파랑, 가득 차면 흰빛으로 맥동) · 파워 블록 방어량(회색, 강화 문턱에서 금색). */
	private static void meter(GuiGraphicsExtractor g, int cx, int top, float partial) {
		int width = 60;
		int height = 4;
		boolean full = HudState.meter >= 100;
		float t = HudState.ticks + kr.overbreak.client.ClientClock.partial(partial);
		int color = HudState.meterKind == 1
				? (full ? pulse(0xFFD8F0FF, t * 3.0F) : 0xFF4FB4FF)
				: HudState.meterKind == 3 ? 0xFFFFC94A
				: HudState.meterKind == 4 ? 0xFFE6E6E6
				: (full ? 0xFFFFC94A : 0xFFC8CCD4);
		int x0 = cx - width / 2;
		g.fill(x0 - 1, top - 1, x0 + width + 1, top + height + 1, 0xA0000000);
		g.fill(x0, top, x0 + Math.round(width * Math.min(100, HudState.meter) / 100.0F), top + height, color);
	}

	private static void panel(GuiGraphicsExtractor g, Font font, HudLayouts.Layout layout, int i, int x, int y, float partial) {
		HudLayouts.SlotDef def = layout.slots().get(i);
		int remaining = HudState.remaining[i];
		int total = HudState.total[i];
		boolean cooling = remaining > 0;
		boolean active = HudState.active[i];
		float used = 1.0F - Mth.clamp((HudState.ticks - HudState.usedAt[i] + kr.overbreak.client.ClientClock.partial(partial)) / 6.0F, 0.0F, 1.0F);
		float denied = 1.0F - Mth.clamp((HudState.ticks - HudState.deniedAt[i] + kr.overbreak.client.ClientClock.partial(partial)) / 8.0F, 0.0F, 1.0F);
		float shake = denied > 0.0F ? Mth.sin((HudState.ticks + kr.overbreak.client.ClientClock.partial(partial)) * 2.6F) * 2.0F * denied : 0.0F;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + PANEL_W / 2.0F + shake, y + PANEL_H / 2.0F);
		float scale = 1.0F + 0.12F * used;
		pose.scale(scale, scale);

		int hw = PANEL_W / 2;
		int hh = PANEL_H / 2;
		// 기울어진 칸 배경 · 테두리 · 진행 막대 · 번쩍임
		pose.pushMatrix();
		pose.mul(new Matrix3x2f(1.0F, 0.0F, SKEW, 1.0F, 0.0F, 0.0F));
		g.fill(-hw, -hh, hw, hh, cooling ? 0xB0141418 : 0x80202630);
		g.outline(-hw, -hh, PANEL_W, PANEL_H, active ? layout.accent() : cooling ? 0x40FFFFFF : 0x90FFFFFF);
		if (active) {
			g.outline(-hw + 1, -hh + 1, PANEL_W - 2, PANEL_H - 2, (layout.accent() & 0x00FFFFFF) | 0x80000000);
		}
		if (cooling) {
			int filled = Math.round(PANEL_W * (1.0F - remaining / (float) total));
			g.fill(-hw, hh - 2, -hw + filled, hh, 0xE0FFFFFF);
		}
		if (used > 0.0F) {
			g.fill(-hw, -hh, hw, hh, alpha(0xFFFFFF, used * 0.55F));
		}
		if (denied > 0.0F) {
			g.fill(-hw, -hh, hw, hh, alpha(0xFF3030, denied * 0.5F));
		}
		pose.popMatrix();

		int iconColor = cooling ? 0xFF5E6168 : active ? layout.accent() : 0xFFFFFFFF;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, def.icon(), -ICON / 2, -ICON / 2, ICON, ICON, iconColor);
		if (cooling) {
			String sec = remaining >= 20 ? String.valueOf((remaining + 19) / 20) : String.format("%.1f", remaining / 20.0F);
			g.centeredText(font, Component.literal(sec).withStyle(ChatFormatting.BOLD), 0, -4, 0xFFFFFFFF);
		}
		pose.popMatrix();

		// 키 표시
		pose.pushMatrix();
		pose.translate(x + PANEL_W / 2.0F, y + PANEL_H + 2.0F);
		pose.scale(0.75F, 0.75F);
		g.centeredText(font, def.key(), 0, 0, cooling ? 0xFF8A8D94 : 0xFFE6E6E6);
		pose.popMatrix();
	}

	/** 궁극기 게이지 원. */
	private static void ult(GuiGraphicsExtractor g, Font font, HudLayouts.Layout layout, int cx, int cy, float partial) {
		boolean ready = HudState.ultState == 2;
		float t = HudState.ticks + kr.overbreak.client.ClientClock.partial(partial);
		float readyPop = 1.0F - Mth.clamp((HudState.ticks - HudState.ultReadyAt + kr.overbreak.client.ClientClock.partial(partial)) / 10.0F, 0.0F, 1.0F);
		float denied = 1.0F - Mth.clamp((HudState.ticks - HudState.ultDeniedAt + kr.overbreak.client.ClientClock.partial(partial)) / 8.0F, 0.0F, 1.0F);
		int radius = 13;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		float scale = 1.0F + 0.2F * readyPop;
		pose.scale(scale, scale);

		// 안쪽 원판
		for (int dy = -radius + 2; dy <= radius - 2; dy++) {
			int half = (int) Math.floor(Math.sqrt((radius - 2) * (radius - 2) - dy * dy));
			g.fill(-half, dy, half + 1, dy + 1, 0x90101418);
		}
		// 게이지 고리 (위에서부터 시계 방향)
		int segments = 48;
		float frac = ready ? 1.0F : HudState.ultCharge / 100.0F;
		int onColor = ready ? pulse(ULT_GOLD, t) : 0xFFFFFFFF;
		for (int k = 0; k < segments; k++) {
			boolean on = k < Math.round(frac * segments);
			int color = on ? onColor : 0x70000000;
			if (denied > 0.0F && !on) {
				color = alpha(0xFF3030, 0.45F + 0.4F * denied);
			}
			pose.pushMatrix();
			pose.rotate((float) (Math.PI * 2.0 * k / segments));
			g.fill(-1, -radius - 1, 1, -radius + 2, color);
			pose.popMatrix();
		}
		if (ready) {
			// 바깥 광채
			float glow = 0.35F + 0.25F * Mth.sin(t * 0.25F);
			for (int k = 0; k < segments; k++) {
				pose.pushMatrix();
				pose.rotate((float) (Math.PI * 2.0 * k / segments));
				g.fill(-1, -radius - 3, 1, -radius - 2, alpha(0xFFC94A, glow));
				pose.popMatrix();
			}
			float iconScale = 1.0F + 0.06F * Mth.sin(t * 0.25F);
			pose.pushMatrix();
			pose.scale(iconScale, iconScale);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, layout.ultIcon(), -8, -8, 16, 16, ULT_GOLD);
			pose.popMatrix();
		} else {
			String pct = String.valueOf(HudState.ultCharge);
			g.centeredText(font, Component.literal(pct).withStyle(ChatFormatting.BOLD), 0, -4, 0xFFFFFFFF);
		}
		pose.popMatrix();

		if (ready) {
			pose.pushMatrix();
			pose.translate(cx + radius + 6, cy - 3);
			pose.scale(0.75F, 0.75F);
			g.text(font, "Q", 0, 0, ULT_GOLD);
			pose.popMatrix();
		}
	}

	private static int alpha(int rgb, float a) {
		return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static int pulse(int argb, float t) {
		float k = 0.85F + 0.15F * Mth.sin(t * 0.25F);
		int r = Math.round(((argb >> 16) & 255) * k);
		int gr = Math.round(((argb >> 8) & 255) * k);
		int b = Math.round((argb & 255) * k);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}
}
