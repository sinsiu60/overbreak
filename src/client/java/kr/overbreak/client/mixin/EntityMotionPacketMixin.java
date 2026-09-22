package kr.overbreak.client.mixin;

import kr.overbreak.client.input.AirControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 서버가 내 속도를 정해 보냄 (스킬 추진 · 넉백) — 공중 제어가 그 속도를 덮어쓰지 않게 알립니다 ({@link AirControl}). */
@Mixin(ClientPacketListener.class)
public abstract class EntityMotionPacketMixin {
	@Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
	private void overbreak$serverPushed(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && packet.id() == mc.player.getId()) {
			AirControl.pushed(mc.player);
		}
	}
}
