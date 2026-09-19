package kr.overbreak.client.item;

import com.mojang.serialization.MapCodec;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 아이템 모델 조건 {@code overbreak:charging} — 들고 있는 엔티티가 로켓 펀치를 충전 중인가.
 * 파쇄 건틀릿이 이 조건으로 파랗게 빛나는 모델로 바뀝니다 (1인칭 · 3인칭 · 다른 플레이어 모두).
 */
public record ChargingProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<ChargingProperty> MAP_CODEC = MapCodec.unit(new ChargingProperty());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		return owner != null && SkillAnims.playing(owner.getId(), SkillAnimPayload.IF_CHARGE);
	}

	@Override
	public MapCodec<ChargingProperty> type() {
		return MAP_CODEC;
	}
}
