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
 * 서버 → 시전자 본인: 돌진 난사 판정 펄스가 적을 한 명 이상 맞혔다 (펄스당 최대 한 번).
 * 클라이언트가 위치 없는 적중음을 틉니다 — 공중 치명타 펄스면 높게 (client/fx/ScatterSounds).
 */
public record ScatterHitPayload(boolean crit) implements CustomPacketPayload {
	public static final Type<ScatterHitPayload> TYPE = new Type<>(Overbreak.id("scatter_hit"));
	public static final StreamCodec<ByteBuf, ScatterHitPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ScatterHitPayload::crit,
			ScatterHitPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, boolean crit) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new ScatterHitPayload(crit));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
