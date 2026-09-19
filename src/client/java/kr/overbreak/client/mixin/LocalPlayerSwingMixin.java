package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OVERBREAK 조작 모드에서는 본인 팔 휘두르기(스윙) 모션을 아예 하지 않습니다.
 * 좌클릭 · Q(버리기) 등 바닐라가 팔을 휘두르는 모든 경우가 막히고, 스윙 패킷도 나가지 않습니다.
 * 공격 모션은 서버가 보내는 기본 공격 애니메이션만 나옵니다.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
	private void overbreak$noSwing(InteractionHand hand, CallbackInfo ci) {
		if (InputMode.active()) {
			ci.cancel();
		}
	}
}
