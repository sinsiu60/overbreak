package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 본인: 넉백을 받았다 — 클라이언트가 0.3초 동안 공중 제어를 끕니다 (넉백을 곧바로 상쇄하지 못하게, client/input/AirControl).
 * 속도 패킷만으로는 스킬 추진(내가 쓴 것)과 넉백(맞은 것)을 가를 수 없어 따로 알립니다.
 */
public record AirLockPayload() implements CustomPacketPayload {
	public static final AirLockPayload INSTANCE = new AirLockPayload();
	public static final Type<AirLockPayload> TYPE = new Type<>(Overbreak.id("air_lock"));
	public static final StreamCodec<ByteBuf, AirLockPayload> CODEC = StreamCodec.unit(INSTANCE);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, INSTANCE);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
