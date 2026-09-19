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
 * 클라이언트 → 서버: 우클릭을 누름 · 뗌 (바뀐 순간에만).
 *
 * 로켓 펀치처럼 "손을 떼는 순간" 이 중요한 스킬용입니다. 바닐라는 누르고 있으면 4틱마다 사용 패킷만 반복하므로
 * 서버가 뗀 순간을 최대 6틱 늦게 알게 됩니다. 이 신호가 오면 그 지연이 없습니다.
 */
public record RightHoldPayload(boolean down) implements CustomPacketPayload {
	public static final Type<RightHoldPayload> TYPE = new Type<>(Overbreak.id("right_hold"));
	public static final StreamCodec<ByteBuf, RightHoldPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, RightHoldPayload::down,
			RightHoldPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> InputRouter.onRightHold(context.player(), payload.down())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
