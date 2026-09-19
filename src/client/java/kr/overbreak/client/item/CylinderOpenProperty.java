package kr.overbreak.client.item;

import com.mojang.serialization.MapCodec;
import kr.overbreak.client.anim.SheriffAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 아이템 모델 조건 {@code overbreak:cylinder_open} — 1인칭에서 재장전 중 탄창이 젖혀진 구간인가.
 * 리볼버가 탄창 없는 몸통으로 바뀌고, 탄창은 손 렌더에서 따로 그립니다 (RevolverHands). 3인칭은 그대로.
 */
public record CylinderOpenProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<CylinderOpenProperty> MAP_CODEC = MapCodec.unit(new CylinderOpenProperty());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		return owner != null && displayContext.firstPerson()
				&& SheriffAnim.cylinderOut(owner.getId(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	public MapCodec<CylinderOpenProperty> type() {
		return MAP_CODEC;
	}
}
