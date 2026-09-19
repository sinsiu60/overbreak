package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.input.InputRouter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 좌클릭. 기본 공격은 스윙 패킷이 아니라 이 신호로 받습니다.
 *
 * held false = 누른 순간, true = 누르고 있는 동안 매 틱 (공격속도만큼 연속 공격).
 * 공격 가능 여부(공격속도 · 정신집중 · 기절)는 서버가 판단하고, 막히면 아무 일도 없습니다.
 */
public record LeftClickPayload(boolean held) implements CustomPacketPayload {
	public static final Type<LeftClickPayload> TYPE = new Type<>(Overbreak.id("left_click"));
	public static final StreamCodec<ByteBuf, LeftClickPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, LeftClickPayload::held,
			LeftClickPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> InputRouter.onLeftClick(context.player())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
