package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 본인: 스킬이 도는 동안 시점을 3인칭으로 잡아 둡니다 (곡예 난사처럼 제 몸이 보여야 하는 동작).
 *
 * third = true 면 지금 시점을 기억해 두고 어깨 너머 3인칭으로, false 면 기억해 둔 시점으로 되돌립니다.
 * 원래 3인칭이었으면 되돌려도 3인칭 그대로입니다.
 */
public record ViewPayload(boolean third) implements CustomPacketPayload {
	public static final Type<ViewPayload> TYPE = new Type<>(Overbreak.id("view"));
	public static final StreamCodec<ByteBuf, ViewPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ViewPayload::third,
			ViewPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, boolean third) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new ViewPayload(third));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
