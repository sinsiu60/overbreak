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
 * 서버 → 본인: 경기 배경 음악 켜기 · 끄기 (매치 포인트 · 대난투 막판).
 * 클라이언트가 낮은 볼륨으로 서서히 깔았다가 서서히 뺍니다 (client/audio/MatchMusicPlayer).
 */
public record MusicPayload(boolean on) implements CustomPacketPayload {
	public static final Type<MusicPayload> TYPE = new Type<>(Overbreak.id("music"));
	public static final StreamCodec<ByteBuf, MusicPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, MusicPayload::on,
			MusicPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, boolean on) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new MusicPayload(on));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
