package kr.overbreak.client.anim;

import kr.overbreak.Overbreak;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * 보안관 동작 공용 — 리볼버 판별 · 손에 드는 물건 · 애니메이션 파일의 timeline 변수.
 *   v.cylinder_out  재장전 중 탄창이 젖혀진 구간 (1인칭 총 모델이 탄창 없는 몸통으로 바뀌고 탄창을 따로 그림)
 *   v.loader        왼손에 스피드로더
 *   v.flash_held    왼손에 섬광 수류탄
 */
public final class SheriffAnim {
	public static final Identifier REVOLVER = Overbreak.id("revolver");
	public static final String CYLINDER_OUT = "cylinder_out";
	public static final String LOADER = "loader";
	public static final String FLASH_HELD = "flash_held";

	private static @Nullable ItemStack cylinder;
	private static @Nullable ItemStack loader;
	private static @Nullable ItemStack flashbang;

	private SheriffAnim() {}

	public static boolean revolver(ItemStack stack) {
		return REVOLVER.equals(stack.get(DataComponents.ITEM_MODEL));
	}

	private static ItemStack model(String name) {
		ItemStack s = new ItemStack(Items.STICK);
		s.set(DataComponents.ITEM_MODEL, Overbreak.id(name));
		return s;
	}

	public static ItemStack cylinderStack() {
		if (cylinder == null) {
			cylinder = model("revolver_cylinder");
		}
		return cylinder;
	}

	public static ItemStack loaderStack() {
		if (loader == null) {
			loader = model("speedloader");
		}
		return loader;
	}

	public static ItemStack flashbangStack() {
		if (flashbang == null) {
			flashbang = model("flashbang");
		}
		return flashbang;
	}

	/** 재장전 중 탄창이 젖혀진 구간인가. */
	public static boolean cylinderOut(int entityId, float partial) {
		return variable(entityId, SkillAnimPayload.SH_RELOAD, CYLINDER_OUT, partial) >= 0.5;
	}

	/** 재생 중인 동작의 timeline 변수 (본편이 끝났거나 없으면 0). */
	public static double variable(int entityId, int anim, String name, float partial) {
		SkillAnims.Play play = SkillAnims.find(entityId, anim);
		if (play == null) {
			return 0.0;
		}
		float e = play.elapsed(partial);
		if (e >= play.end()) {
			return 0.0;
		}
		PlayerAnimation data = PlayerAnimations.forSkill(anim);
		return data == null ? 0.0 : data.variable(name, Math.max(0.0F, e) / 20.0, 0.0);
	}
}
