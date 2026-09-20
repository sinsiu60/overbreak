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
 * 서버 → 본인 클라이언트: 스킬로 체력을 되찾았다 — 화면 가장자리가 초록으로 번집니다.
 *
 * ticks 만큼 이어집니다. 흡혈 한 번은 짧게(반 초), 채널링 회복은 그 길이만큼 보냅니다.
 * 이미 보이고 있으면 더 긴 쪽으로 늘어납니다.
 */
public record HealPayload(int ticks) implements CustomPacketPayload {
	public static final Type<HealPayload> TYPE = new Type<>(Overbreak.id("heal"));
	public static final StreamCodec<ByteBuf, HealPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, HealPayload::ticks, HealPayload::new);

	/** 흡혈 한 번 (1/20초 단위). */
	public static final int PULSE = 10;

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/**
	 * 스킬로 회복했음을 본인에게 알립니다.
	 *
	 * @param timeUnits 1/20초 단위 길이 ({@link #PULSE} 이면 흡혈 한 번)
	 */
	public static void send(ServerPlayer p, int timeUnits) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new HealPayload(kr.overbreak.core.tick.Ticks.of(timeUnits)));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
