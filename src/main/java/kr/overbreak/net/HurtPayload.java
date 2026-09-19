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
 * 서버 → 맞은 사람: 피격 피드백 (오버워치식 방향 표시).
 *
 * angle     때린 사람이 있는 방향 (월드 yaw, 도) — 클라이언트가 자기 시야각을 빼서 화면 각도로 씁니다
 * amount    받은 피해
 * directed  방향을 아는가 (환경 피해 등은 false — 사방이 번쩍)
 *
 * 화면 흔들림 대신 방향 표시 + 붉은 가장자리로 알려 줍니다 (바닐라 피격 기울기는 꺼 둡니다).
 */
public record HurtPayload(float angle, float amount, boolean directed) implements CustomPacketPayload {
	public static final Type<HurtPayload> TYPE = new Type<>(Overbreak.id("hurt"));
	public static final StreamCodec<ByteBuf, HurtPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, HurtPayload::angle,
			ByteBufCodecs.FLOAT, HurtPayload::amount,
			ByteBufCodecs.BOOL, HurtPayload::directed,
			HurtPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, HurtPayload hurt) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, hurt);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
