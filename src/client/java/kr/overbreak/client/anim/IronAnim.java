package kr.overbreak.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import kr.overbreak.client.fx.IronFx;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * 참철 1인칭 대검 동작 — 키프레임 {시간, x, y, z, X축(도, + = 칼끝이 뒤로), Y축(도), Z축(도), 크기}.
 *
 * 대검 모델(models/item/ironcleaver_greatsword.json)의 1인칭 display 는 칼날이 위로 서고 조금 앞으로 기울게 잡아 두었습니다.
 * 그 위에 Z축 -90 이면 칼날이 오른쪽으로 눕고, 거기서 Y축을 돌리면 오른쪽(0) → 앞(90) → 왼쪽(180) 으로 가로 베기가 됩니다.
 * X축 -80 쯤은 누운 칼날의 면을 수평으로 (날이 앞장서게) 돌립니다.
 *
 * 역경직: 내 공격이 맞으면 {@link IronFx#lag} 만큼 동작 시간을 늦춰 그립니다 — 칼이 그 자리에서 멈췄다가 빠르게 따라잡음.
 * 박자는 서버 {@code IronSpec} 과 같습니다 (1타 · 2타 선딜 7 · 판정 2 · 후딜 9, 3타 9 · 2 · 11, 1/20초 단위).
 */
public final class IronAnim {
	private static final float[] REST = {0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F};

	/** 1타 — 칼을 오른쪽 위로 젖혔다가 화면을 대각선으로 가르며 왼쪽 아래로 (칼날은 늘 화면 안). */
	private static final float[][] SWING_R = {
			REST,
			{4.5F, 0.62F, -0.30F, -0.62F, 10F, -20F, -55F, 1.0F},
			{7.0F, 0.66F, -0.28F, -0.62F, 12F, -24F, -62F, 1.0F},
			{9.0F, -0.22F, -0.46F, -0.72F, -8F, 15F, 85F, 1.0F},
			{12.0F, -0.28F, -0.52F, -0.70F, -10F, 18F, 95F, 1.0F},
			{18.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 2타 — 왼쪽 위로 감았다가 오른쪽 아래로 되베기. */
	private static final float[][] SWING_L = {
			REST,
			{4.5F, -0.12F, -0.30F, -0.64F, 10F, 20F, 60F, 1.0F},
			{7.0F, -0.16F, -0.28F, -0.64F, 12F, 24F, 66F, 1.0F},
			{9.0F, 0.72F, -0.48F, -0.72F, -8F, -15F, -88F, 1.0F},
			{12.0F, 0.78F, -0.55F, -0.70F, -10F, -18F, -98F, 1.0F},
			{18.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 3타 — 머리 위로 치켜들었다가 (칼끝이 화면 위로) 앞으로 내려찍기. */
	private static final float[][] OVERHEAD = {
			REST,
			{6.5F, 0.30F, -0.14F, -0.80F, 40F, 10F, 10F, 1.0F},
			{9.0F, 0.28F, -0.10F, -0.78F, 46F, 10F, 10F, 1.0F},
			{11.0F, 0.16F, -0.62F, -0.86F, -95F, 8F, 0F, 1.0F},
			{16.0F, 0.18F, -0.64F, -0.84F, -92F, 8F, 0F, 1.0F},
			{22.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 참 모으기 — 칼을 오른쪽으로 젖혀 들고 점점 깊이 당김 (3단 1.8초에 가장 깊이). 오오라가 보이게 화면 오른쪽에 남김. */
	private static final float[][] CHARGE = {
			REST,
			{5.0F, 0.50F, -0.46F, -0.78F, 20F, -22F, -26F, 1.0F},
			{36.0F, 0.54F, -0.42F, -0.76F, 28F, -28F, -34F, 1.0F},
			{200.0F, 0.54F, -0.42F, -0.76F, 28F, -28F, -34F, 1.0F}};
	/** 모아 베기 — 당긴 자세에서 한 번에 화면을 가르며 왼쪽 아래로. */
	private static final float[][] RELEASE = {
			{0.0F, 0.54F, -0.42F, -0.76F, 28F, -28F, -34F, 1.0F},
			{0.8F, 0.40F, -0.40F, -0.70F, 5F, -5F, 10F, 1.05F},
			{2.4F, -0.36F, -0.54F, -0.70F, -12F, 28F, 112F, 1.05F},
			{7.0F, -0.42F, -0.60F, -0.64F, -14F, 30F, 120F, 1.0F},
			{14.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 어깨 박치기 — 칼을 아래로 내려 뒤로 돌리고 어깨부터. */
	private static final float[][] BASH = {
			REST,
			{2.0F, 0.62F, -0.78F, -0.46F, 40F, -25F, -20F, 1.0F},
			{7.0F, 0.64F, -0.80F, -0.44F, 42F, -25F, -20F, 1.0F},
			{8.0F, 0.64F, -0.80F, -0.44F, 42F, -25F, -20F, 1.0F}};
	/** 검막 — 칼날을 가로로 눕혀 얼굴 앞을 막음 (면이 앞). */
	private static final float[][] GUARD = {
			REST,
			{2.5F, 0.36F, -0.30F, -0.58F, 0F, 0F, 82F, 1.0F},
			{20.0F, 0.36F, -0.30F, -0.58F, 0F, 0F, 82F, 1.0F}};
	/** 대지 가르기 — 오른쪽 위로 들었다가 칼끝을 땅에 박고 앞으로 긁어 올림. */
	private static final float[][] REND = {
			REST,
			{5.0F, 0.62F, -0.16F, -0.54F, 60F, -10F, -14F, 1.0F},
			{6.0F, 0.62F, -0.14F, -0.54F, 62F, -10F, -14F, 1.0F},
			{7.5F, 0.32F, -0.66F, -0.92F, -96F, 0F, 0F, 1.0F},
			{10.0F, 0.32F, -0.56F, -1.04F, -78F, 0F, 0F, 1.0F},
			{16.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 천참 — 칼을 앞에 곧게 세우고 기를 모았다가 (1.2초) 땅이 갈라지게 내려침. */
	private static final float[][] ULT = {
			REST,
			{8.0F, 0.46F, -0.46F, -0.84F, 8F, -8F, -6F, 1.0F},
			{23.0F, 0.44F, -0.42F, -0.82F, 14F, -8F, -6F, 1.0F},
			{25.0F, 0.12F, -0.62F, -0.95F, -85F, 0F, 0F, 1.1F},
			{32.0F, 0.14F, -0.60F, -0.92F, -82F, 0F, 0F, 1.05F},
			{40.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};

	private IronAnim() {}

	public static boolean handles(int anim) {
		return anim >= SkillAnimPayload.IC_SWING_R && anim <= SkillAnimPayload.IC_ULT;
	}

	private static float[][] keys(int anim) {
		return switch (anim) {
			case SkillAnimPayload.IC_SWING_R -> SWING_R;
			case SkillAnimPayload.IC_SWING_L -> SWING_L;
			case SkillAnimPayload.IC_OVERHEAD -> OVERHEAD;
			case SkillAnimPayload.IC_CHARGE -> CHARGE;
			case SkillAnimPayload.IC_RELEASE -> RELEASE;
			case SkillAnimPayload.IC_BASH -> BASH;
			case SkillAnimPayload.IC_GUARD -> GUARD;
			case SkillAnimPayload.IC_REND -> REND;
			default -> ULT;
		};
	}

	/** 동작 시간 — 내 동작이면 역경직만큼 늦춤. */
	public static float time(SkillAnims.Play play, int entityId, float partial) {
		float e = play.elapsed(partial);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && entityId == mc.player.getId()) {
			e -= IronFx.lag(play.start, partial);
		}
		return e;
	}

	public static void firstPerson(PoseStack pose, int invert, SkillAnims.Play play, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int id = mc.player == null ? -1 : mc.player.getId();
		float e = time(play, id, partial);
		float end = play.end();
		if (IronFx.frozen(partial)) {
			// 역경직 — 멈춘 칼이 부르르 떨림
			float t = (float) (kr.overbreak.client.ClientClock.at(partial) * 7.0);
			pose.translate(Mth.sin(t * 3.1F) * 0.010F, Mth.cos(t * 2.3F) * 0.008F, 0.0F);
		}
		if (play.anim == SkillAnimPayload.IC_CHARGE && e < end) {
			// 단계가 오를수록 칼이 떨림
			int stage = IronFx.stage(id);
			float amp = stage == 0 ? 0.002F : 0.004F + 0.004F * stage;
			float t = e * 3.0F;
			pose.translate(Mth.sin(t * 1.7F) * amp, Mth.sin(t * 2.3F + 1.1F) * amp, 0.0F);
		}
		if (play.anim == SkillAnimPayload.IC_ULT && e < 24.0F) {
			float amp = 0.004F + 0.010F * Mth.clamp(e / 24.0F, 0.0F, 1.0F);
			pose.translate(Mth.sin(e * 5.1F) * amp, Mth.cos(e * 4.3F) * amp, 0.0F);
		}
		FirstPersonAnim.keyed(pose, invert, keys(play.anim), e, end, SkillAnims.fade(play.anim));
	}

	/**
	 * 3인칭 손 아이템 굴림 (도) — 가로 베기 · 모아 베기 · 검막은 칼날을 팔 축으로 눕혀 수평으로.
	 * 들어갈 때는 0.15초에 걸쳐 눕히고, 동작 가중치(w)로 끝날 때 다시 세웁니다.
	 */
	public static float itemRoll(int anim, float t, float w) {
		float r = switch (anim) {
			case SkillAnimPayload.IC_SWING_R, SkillAnimPayload.IC_RELEASE -> 90.0F;
			case SkillAnimPayload.IC_SWING_L -> -90.0F;
			case SkillAnimPayload.IC_GUARD -> 90.0F;
			default -> 0.0F;
		};
		if (r == 0.0F) {
			return 0.0F;
		}
		float in = anim == SkillAnimPayload.IC_RELEASE ? 1.0F : Mth.clamp(t / 3.0F, 0.0F, 1.0F);
		return r * in * w;
	}

	private static final java.util.Map<Integer, ItemStack> AURA = new java.util.HashMap<>();

	/**
	 * 칼날 오오라 — 대검과 같은 자세로 겹쳐 그리는 빛 테두리 모델 (overbreak:ironcleaver_aura, 흰 반투명 → 단계 색으로 염색).
	 * 같은 display 를 물려받아 칼날에 정확히 붙습니다. 최대 밝기로 그려 어두운 곳에서도 빛남.
	 */
	public static ItemStack auraStack(int argb) {
		int rgb = argb & 0xFFFFFF;
		return AURA.computeIfAbsent(rgb, c -> {
			ItemStack s = new ItemStack(net.minecraft.world.item.Items.STICK);
			s.set(DataComponents.ITEM_MODEL, kr.overbreak.Overbreak.id("ironcleaver_aura"));
			s.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(c));
			return s;
		});
	}

	/** 들고 있는 것이 참철 대검인가. */
	public static boolean greatsword(ItemStack stack) {
		return kr.overbreak.Overbreak.id("ironcleaver_greatsword").equals(stack.get(DataComponents.ITEM_MODEL));
	}
}
