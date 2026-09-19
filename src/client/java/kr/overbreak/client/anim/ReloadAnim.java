package kr.overbreak.client.anim;

import kr.overbreak.Overbreak;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * 발키리 재장전 동작 공용 — 탄창이 총에서 빠져 손에 있는 구간 (애니메이션 파일 timeline 의 v.magazine_out).
 *   이 구간에는 총 모델이 탄창 없는 모양으로 바뀌고(item/MagazineOutProperty), 탄창 모델을 왼손에 그립니다 (1인칭 · 3인칭).
 */
public final class ReloadAnim {
	public static final String MAGAZINE_OUT = "magazine_out";
	private static @Nullable ItemStack magazine;

	private ReloadAnim() {}

	/** 손에 든 탄창 그리기용 아이템 (모델 overbreak:valkyrie_magazine). */
	public static ItemStack magazineStack() {
		if (magazine == null) {
			magazine = new ItemStack(Items.STICK);
			magazine.set(DataComponents.ITEM_MODEL, Overbreak.id("valkyrie_magazine"));
		}
		return magazine;
	}

	/** 재장전 본편 경과 초 — 재장전 중이 아니면 -1. */
	public static float seconds(int entityId, float partial) {
		SkillAnims.Play play = SkillAnims.find(entityId, SkillAnimPayload.VK_RELOAD);
		if (play == null) {
			return -1.0F;
		}
		float e = play.elapsed(partial);
		return e < play.end() ? Math.max(0.0F, e) / 20.0F : -1.0F;
	}

	public static @Nullable PlayerAnimation animation() {
		return PlayerAnimations.forSkill(SkillAnimPayload.VK_RELOAD);
	}

	public static boolean magazineOut(int entityId, float partial) {
		float s = seconds(entityId, partial);
		PlayerAnimation anim = s < 0.0F ? null : animation();
		return anim != null && anim.variable(MAGAZINE_OUT, s, 0.0) >= 0.5;
	}
}
