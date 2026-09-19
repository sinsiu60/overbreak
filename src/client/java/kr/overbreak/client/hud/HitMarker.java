package kr.overbreak.client.hud;

import kr.overbreak.net.HitPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * 오버워치 방식 히트 마커 · 피격음 — 내 피해가 들어가면 조준점 둘레에 X 표시가 튀어나왔다 사라집니다.
 *   피해: 흰색 X (0.3초), 짧은 금속성 "틱"
 *   처치: 붉은 X 가 크게 (0.6초), 맑은 "딩"
 *   치명타: 주황빛 붉은 X 가 튀어나옴 (0.4초), 둔탁한 "딩"
 * 같은 틱에 여러 명을 맞혀도(범위 공격) 소리는 한 번만 납니다.
 */
public final class HitMarker {
	private static final int HIT_TICKS = 6;
	private static final int KILL_TICKS = 12;

	private static float ticks;
	private static float hitAt = -1000;
	private static float killAt = -1000;
	private static float soundAt = -1000;
	private static float killSoundAt = -1000;
	private static float critAt = -1000;
	private static float critSoundAt = -1000;
	private static final int CRIT_TICKS = 8;

	private HitMarker() {}

	public static void receive(HitPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		hitAt = ticks;
		if (msg.kill()) {
			killAt = ticks;
			if (killSoundAt != ticks) {
				killSoundAt = ticks;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F, 0.9F));
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 0.6F));
			}
		}
		if (msg.crit()) {
			critAt = ticks;
			if (critSoundAt != ticks) {
				critSoundAt = ticks;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.25F, 0.9F));
			}
		}
		if (soundAt != ticks) {
			soundAt = ticks;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.9F, 0.9F));
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, 2.0F, 0.8F));
		}
	}

	public static void tick(Minecraft mc) {
		ticks = (float) kr.overbreak.client.ClientClock.now();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		float cx = (g.guiWidth() - 15) / 2.0F + 7.5F;
		float cy = (g.guiHeight() - 15) / 2.0F + 7.5F;

		float e = ticks - hitAt + kr.overbreak.client.ClientClock.partial(partial);
		if (e < HIT_TICKS) {
			float a = 1.0F - e / HIT_TICKS;
			cross(g, cx, cy, 4.0F + e * 0.5F, 4, 1, color(0xFFFFFF, a));
		}
		float c = ticks - critAt + kr.overbreak.client.ClientClock.partial(partial);
		if (c < CRIT_TICKS) {
			float a = 1.0F - c / CRIT_TICKS;
			float pop = c < 2.0F ? 1.4F - 0.2F * c : 1.0F;
			cross(g, cx, cy, (4.5F + c * 0.4F) * pop, 5, 1, color(0xFF6A3B, a));
		}
		float k = ticks - killAt + kr.overbreak.client.ClientClock.partial(partial);
		if (k < KILL_TICKS) {
			float a = 1.0F - k / KILL_TICKS;
			float pop = k < 2.0F ? 1.6F - 0.3F * k : 1.0F;
			cross(g, cx, cy, (5.0F + k * 0.4F) * pop, 7, 2, color(0xFF3B3B, a));
		}
	}

	/** 조준점 둘레 대각선 네 획. gap = 가운데에서 획 시작까지, len = 획 길이, half = 두께 절반. */
	private static void cross(GuiGraphicsExtractor g, float cx, float cy, float gap, int len, int half, int color) {
		Matrix3x2fStack pose = g.pose();
		for (int i = 0; i < 4; i++) {
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.rotate((float) Math.toRadians(45 + 90 * i));
			int start = Math.round(gap);
			g.fill(-half, -start - len, half, -start, color);
			pose.popMatrix();
		}
	}

	private static int color(int rgb, float a) {
		return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24) | rgb;
	}
}
