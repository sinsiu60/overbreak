package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.game.Tutorial;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 튜토리얼에서 클라이언트만 아는 일이 일어남.
 *
 * what  INFO_SCREEN  F8 규격 정보 화면을 열었다
 *
 * 서버는 튜토리얼 중인 사람의 그 단계에서만 받아들입니다.
 */
public record TutorialAckPayload(int what) implements CustomPacketPayload {
	public static final int INFO_SCREEN = 0;

	public static final Type<TutorialAckPayload> TYPE = new Type<>(Overbreak.id("tutorial_ack"));
	public static final StreamCodec<ByteBuf, TutorialAckPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TutorialAckPayload::what,
			TutorialAckPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> Tutorial.onAck(context.player(), payload.what())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
