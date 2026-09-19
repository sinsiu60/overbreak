package kr.overbreak.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 마네킹의 비공개 설정을 씁니다 — 훈련용 더미로 세울 때 필요한 것만.
 *
 *   setHideDescription: 머리 위 기본 설명("마네킹")을 지웁니다. 우리는 직접 이름표를 붙입니다
 *   setImmovable:       참이면 밀리지 않습니다. 더미는 넉백이 보여야 하므로 false 로 둡니다
 */
@Mixin(Mannequin.class)
public interface MannequinAccess {
	@Invoker("setHideDescription")
	void overbreak$setHideDescription(boolean hide);

	@Invoker("setImmovable")
	void overbreak$setImmovable(boolean immovable);
}
