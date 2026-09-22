package kr.overbreak.client.hud.crosshair;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 조준점 설정 화면 — 오버워치 조준점 설정처럼 왼쪽에서 값을 바꾸면 오른쪽 미리보기에 바로 보입니다.
 * F9 (키 설정에서 바꿀 수 있음) 또는 로비의 「조준점」 으로 엽니다. 닫을 때 저장합니다. 게임은 멈추지 않습니다.
 */
public final class CrosshairScreen extends Screen {
	private static KeyMapping key;

	private final @Nullable Screen parent;
	private final CrosshairConfig cfg = CrosshairConfig.get();

	public CrosshairScreen(@Nullable Screen parent) {
		super(Component.literal("조준점 설정"));
		this.parent = parent;
	}

	public static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.overbreak.crosshair", 298, kr.overbreak.client.input.Keys.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.gui.screen() == null && mc.player != null) {
					mc.gui.setScreen(new CrosshairScreen(null));
				}
			}
		});
	}

	@Override
	protected void init() {
		// 두 줄 — 왼쪽: 모양 · 색 · 두께 · 길이 · 간격 · 원 반지름 / 오른쪽: 불투명도 · 테두리 · 가운데 점 · 점 불투명도 · 기본값 · 완료
		// 그 아래 한 줄: 색 세부 (R · G · B). 작은 화면에서도 다 들어가게
		int x = 12;
		int area = Math.max(200, (int) (width * 0.58F)) - x;
		int col = (area - 6) / 2;
		int x2 = x + col + 6;
		int h = 20;
		int step = 22;
		int y = 30;
		addRenderableWidget(Button.builder(typeLabel(), b -> {
			CrosshairConfig.Type[] all = CrosshairConfig.Type.values();
			cfg.type = all[(cfg.type.ordinal() + 1) % all.length];
			b.setMessage(typeLabel());
		}).bounds(x, y, col, h).build());
		addRenderableWidget(new IntSlider(x2, y, col, h, "불투명도", 0, 100, () -> cfg.opacity, v -> cfg.opacity = v));
		y += step;
		addRenderableWidget(Button.builder(colorLabel(), b -> {
			int i = presetIndex();
			cfg.color = CrosshairConfig.PRESETS[(i + 1) % CrosshairConfig.PRESETS.length];
			rebuild();
		}).bounds(x, y, col, h).build());
		addRenderableWidget(new IntSlider(x2, y, col, h, "테두리", 0, 100, () -> cfg.outline, v -> cfg.outline = v));
		y += step;
		addRenderableWidget(new IntSlider(x, y, col, h, "두께", 1, 10, () -> cfg.thickness, v -> cfg.thickness = v));
		addRenderableWidget(new IntSlider(x2, y, col, h, "가운데 점", 0, 10, () -> cfg.dotSize, v -> cfg.dotSize = v));
		y += step;
		addRenderableWidget(new IntSlider(x, y, col, h, "길이", 0, 40, () -> cfg.length, v -> cfg.length = v));
		addRenderableWidget(new IntSlider(x2, y, col, h, "점 불투명도", 0, 100, () -> cfg.dotOpacity, v -> cfg.dotOpacity = v));
		y += step;
		addRenderableWidget(new IntSlider(x, y, col, h, "가운데 간격", 0, 40, () -> cfg.gap, v -> cfg.gap = v));
		int half = (col - 4) / 2;
		addRenderableWidget(Button.builder(Component.literal("기본값"), b -> {
			cfg.reset();
			rebuild();
		}).bounds(x2, y, half, h).build());
		addRenderableWidget(Button.builder(Component.literal("완료"), b -> onClose()).bounds(x2 + half + 4, y, half, h).build());
		y += step;
		addRenderableWidget(new IntSlider(x, y, col, h, "원 반지름", 2, 40, () -> cfg.radius, v -> cfg.radius = v));
		y += step;
		// 색 세부 (빨강 · 초록 · 파랑) — 원하는 색을 직접
		int third = (area - 8) / 3;
		addRenderableWidget(new IntSlider(x, y, third, h, "빨강", 0, 255, () -> (cfg.color >> 16) & 0xFF,
				v -> cfg.color = (cfg.color & 0x00FFFF) | (v << 16)));
		addRenderableWidget(new IntSlider(x + third + 4, y, third, h, "초록", 0, 255, () -> (cfg.color >> 8) & 0xFF,
				v -> cfg.color = (cfg.color & 0xFF00FF) | (v << 8)));
		addRenderableWidget(new IntSlider(x + (third + 4) * 2, y, third, h, "파랑", 0, 255, () -> cfg.color & 0xFF,
				v -> cfg.color = (cfg.color & 0xFFFF00) | v));
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	private Component typeLabel() {
		return Component.literal("모양: " + cfg.type.label);
	}

	private Component colorLabel() {
		int i = presetIndex();
		String name = i >= 0 ? CrosshairConfig.PRESET_NAMES[i] : "사용자 색";
		return Component.literal("색: " + name).withStyle(s -> s.withColor(cfg.color));
	}

	private int presetIndex() {
		for (int i = 0; i < CrosshairConfig.PRESETS.length; i++) {
			if (CrosshairConfig.PRESETS[i] == cfg.color) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void onClose() {
		cfg.save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key != null && key.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xD80A0C12, 0xC0101420);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(font, Component.literal("조준점").withStyle(ChatFormatting.BOLD), 12, 12, 0xFFFFFFFF);
		g.text(font, "닫으면 저장 · F9 로 언제든 열기", 56, 12, 0xFF8A8F9A);
		// 미리보기 — 밝은 하늘 · 풀밭 · 어두운 바닥 세 가지 배경 위에
		int px = Math.max(200, (int) (width * 0.58F)) + 8;
		int pw = width - px - 12;
		int top = 30;
		int ph = height - top - 16;
		int band = ph / 3;
		int[] bg = {0xFF8CB8F0, 0xFF5E8C3A, 0xFF1E2028};
		String[] names = {"하늘", "풀밭", "어두운 곳"};
		for (int i = 0; i < 3; i++) {
			int y0 = top + i * band;
			g.fill(px, y0, px + pw, y0 + band - 2, bg[i]);
			g.text(font, names[i], px + 4, y0 + 4, 0xC0FFFFFF);
			CrosshairRenderer.draw(g, px + pw / 2.0F, y0 + (band - 2) / 2.0F, cfg);
		}
	}

	/** 정수 슬라이더 — 라벨: 값. */
	private static final class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntConsumer set;

		IntSlider(int x, int y, int w, int h, String label, int min, int max, IntSupplier get, IntConsumer set) {
			super(x, y, w, h, Component.empty(), (get.getAsInt() - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.set = set;
			updateMessage();
		}

		private int current() {
			return Mth.clamp((int) Math.round(min + value * (max - min)), min, max);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + current()));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}

	/** 시험용: 지금 설정. */
	public static CrosshairConfig config() {
		return CrosshairConfig.get();
	}

	/** 시험용: 이 화면을 연다. */
	public static void open(Minecraft mc) {
		mc.gui.setScreen(new CrosshairScreen(mc.gui.screen()));
	}
}
