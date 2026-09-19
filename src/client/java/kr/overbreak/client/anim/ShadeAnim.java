package kr.overbreak.client.anim;

import kr.overbreak.Overbreak;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 셰이드 1인칭 — 왼손에 쥔 표창 (서버 ShadowKunai 가 날리는 것과 같은 모델). */
public final class ShadeAnim {
	private static ItemStack kunai;

	private ShadeAnim() {}

	public static ItemStack kunaiStack() {
		if (kunai == null) {
			kunai = new ItemStack(Items.IRON_NUGGET);
			kunai.set(DataComponents.ITEM_MODEL, Overbreak.id("kunai"));
		}
		return kunai;
	}
}
