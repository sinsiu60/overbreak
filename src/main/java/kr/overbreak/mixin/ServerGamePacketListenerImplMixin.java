package kr.overbreak.mixin;

import kr.overbreak.input.InputRouter;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 손 바꾸기(F) · 버리기(Q) · 스윙(좌클릭)을 서버에서 직접 받습니다.
 *
 * 스레드 확인(ensureRunningOnSameThread) 뒤에 끼어듭니다. 패킷은 네트워크 스레드로 먼저 들어오고,
 * 그 확인이 예외를 던져 서버 스레드로 다시 보내는데, 앞에 끼면 우리 코드가 네트워크 스레드에서 돕니다.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	private static final String ENSURE_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

	@Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER), cancellable = true)
	private void overbreak$action(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		switch (packet.getAction()) {
			case SWAP_ITEM_WITH_OFFHAND -> {
				if (InputRouter.onSwapHands(this.player)) {
					ci.cancel();
				}
			}
			case DROP_ITEM, DROP_ALL_ITEMS -> {
				if (InputRouter.onDrop(this.player)) {
					ci.cancel();
				}
			}
			default -> {
			}
		}
	}

	/** 근접 직업이면 바닐라 팔 휘두르기 방송을 취소합니다 — 실제로 휘두를 때 기본 공격 애니메이션이 대신 나갑니다. */
	@Inject(method = "handleAnimate", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER), cancellable = true)
	private void overbreak$swing(ServerboundSwingPacket packet, CallbackInfo ci) {
		if (InputRouter.onSwing(this.player)) {
			ci.cancel();
		}
	}
}
