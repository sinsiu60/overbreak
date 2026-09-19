package kr.overbreak.client.item;

import com.mojang.serialization.MapCodec;
import kr.overbreak.client.anim.ReloadAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 아이템 모델 조건 {@code overbreak:magazine_out} — 들고 있는 엔티티가 재장전 중이고 탄창이 총에서 빠져 있는가.
 * 연사 포탑이 이 조건으로 탄창 없는 모델로 바뀝니다 (1인칭 · 3인칭 · 다른 플레이어 모두).
 */
public record MagazineOutProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<MagazineOutProperty> MAP_CODEC = MapCodec.unit(new MagazineOutProperty());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		return owner != null && ReloadAnim.magazineOut(owner.getId(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	@Override
	public MapCodec<MagazineOutProperty> type() {
		return MAP_CODEC;
	}
}
