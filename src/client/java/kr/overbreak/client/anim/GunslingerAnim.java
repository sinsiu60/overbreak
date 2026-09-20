package kr.overbreak.client.anim;

import com.mojang.math.Axis;
import kr.overbreak.Overbreak;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * 건슬링어 쌍권총 전용 도우미 — 공중 재장전 중 하늘에 떠 있는 탄창 두 개.
 *
 * 동작 박자 (1/20초 단위, 서버 {@code DualPistols.RELOAD} = 25 와 같음)
 *   0~5   손목을 위로 튕겨 탄창 두 개를 쏘아 올림
 *   6~16  탄창이 떠 있는 동안 총 두 자루가 앞으로 한 바퀴 (1인칭 키프레임 {@code GS_RELOAD})
 *   17~21 떨어지는 탄창을 두 총을 위로 내질러 받아 끼움 ({@link #CATCH} 에 딱 맞물림)
 *   22~25 손 반동 후 평소 자세로
 *
 * 손에 들리는 총은 양손이 <b>같은 모델</b>(overbreak:gunslinger_pistols) 입니다.
 * 마인크래프트가 왼손 손 변환을 좌우로 뒤집어 주므로, 모델 display 의 firstperson_lefthand
 * translation 에서 +x 는 <b>화면 왼쪽</b>입니다 (오른손은 화면 오른쪽). 두 총을 밖으로 벌리려면 양쪽 모두 x 를 키우면 됩니다.
 *
 * 탄창은 위로 {@link #APEX} 칸까지만 올립니다 — 사양서의 1.5칸은 1인칭 화면 밖으로 완전히 사라져
 * 곡예가 보이지 않기 때문에, 화면 위쪽 가장자리에 걸치는 높이로 줄였습니다.
 */
public final class GunslingerAnim {
	/** 탄창이 손을 떠나는 시각. */
	public static final float EJECT = 0.0F;
	/** 탄창을 다시 받아 끼우는 시각 (사양서의 프레임 18). */
	public static final float CATCH = 18.0F;
	/** 탄창이 올라가는 높이 (칸, 카메라 기준). */
	private static final float APEX = 0.62F;
	/** 탄창이 도는 속도 (틱당 도). */
	private static final float MAG_SPIN = 56.0F;

	private static final Identifier PISTOLS = Overbreak.id("gunslinger_pistols");
	private static @Nullable ItemStack magazine;

	private GunslingerAnim() {}

	/** 지금 든 것이 쌍권총인가. */
	public static boolean pistols(ItemStack stack) {
		return PISTOLS.equals(stack.get(DataComponents.ITEM_MODEL));
	}

	/** 공중에 뜬 탄창을 그릴 아이템 (모델 overbreak:gunslinger_magazine). */
	public static ItemStack magazineStack() {
		if (magazine == null) {
			magazine = new ItemStack(Items.STICK);
			magazine.set(DataComponents.ITEM_MODEL, Overbreak.id("gunslinger_magazine"));
		}
		return magazine;
	}

	/** 재장전 본편 경과 (1/20초 단위) — 재장전 중이 아니면 -1. */
	public static float reloadTime(int entityId, float partial) {
		SkillAnims.Play play = SkillAnims.find(entityId, SkillAnimPayload.GS_RELOAD);
		if (play == null) {
			return -1.0F;
		}
		float e = play.elapsed(partial);
		return e < play.end() ? Math.max(0.0F, e) : -1.0F;
	}

	/** 지금 탄창 두 개가 공중에 떠 있는가. */
	public static boolean magsInAir(int entityId, float partial) {
		float e = reloadTime(entityId, partial);
		return e >= EJECT && e < CATCH;
	}

	/**
	 * 떠 있는 탄창 두 개의 자세 (카메라 공간 — 손 변환이 없는 기본 포즈 위에 곱합니다).
	 * 떠 있지 않으면 빈 배열.
	 *
	 * @param invert 주 손이 오른손이면 1, 왼손이면 -1
	 */
	public static Matrix4f[] flyingMags(int entityId, int invert, float partial) {
		float e = reloadTime(entityId, partial);
		if (e < EJECT || e >= CATCH) {
			return new Matrix4f[0];
		}
		float u = (e - EJECT) / (CATCH - EJECT);
		// 위로 던졌다가 받는 포물선 — 0 에서 시작해 한가운데서 가장 높고 다시 0
		float lift = APEX * 4.0F * u * (1.0F - u);
		// 조금 앞으로 나갔다가 돌아옵니다 (원근으로 화면에 남게)
		float push = -0.30F * Mth.sin(u * Mth.PI);
		float spin = e * MAG_SPIN;
		Matrix4f[] out = new Matrix4f[2];
		for (int i = 0; i < 2; i++) {
			// 두 자루의 탄창은 화면 좌우로 조금씩 벌어지며 올라갑니다
			float side = (i == 0 ? -1.0F : 1.0F);
			float x = invert * (0.44F + side * 0.10F) + side * 0.16F * Mth.sin(u * Mth.PI);
			out[i] = new Matrix4f()
					.translation(x, -0.50F + lift, -0.74F + push)
					.rotate(Axis.XP.rotationDegrees(spin * (i == 0 ? 1.0F : -1.0F)))
					.rotate(Axis.ZP.rotationDegrees(spin * 0.35F))
					.scale(0.7F)
					// 모델 가운데(8,8,8 의 절반 = 0.5칸)를 회전축으로
					.translate(-0.5F, -0.5F, -0.5F);
		}
		return out;
	}
}
