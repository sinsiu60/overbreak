package kr.overbreak.client.mixin;

import kr.overbreak.client.hud.MatchTeams;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 팀전 중에는 같은 편 · 상대에게 테두리를 켭니다 (발광 효과를 주지 않고 내 화면에서만).
 * 색은 {@link EntityRendererMixin} 이 넣습니다.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void overbreak$teamOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MatchTeams.outline(entity) != 0) {
			cir.setReturnValue(true);
		}
	}
}
