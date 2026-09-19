package kr.overbreak.net;

import java.util.List;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 본인 클라이언트: 팀 격전 편 짜기 화면의 내용.
 *
 * blue · red  두 팀에 이미 들어간 사람 이름
 * waiting     아직 편을 안 고른 사람 이름
 * mine        내가 고른 편 (0 = 청 · 1 = 홍 · -1 = 아직)
 * left        시작까지 남은 초 (0 이면 곧 시작)
 * size        한 팀 인원 (2 또는 3)
 */
public record TeamDraftPayload(List<String> blue, List<String> red, List<String> waiting, int mine, int left, int size)
		implements CustomPacketPayload {
	public static final Type<TeamDraftPayload> TYPE = new Type<>(Overbreak.id("team_draft"));
	private static final StreamCodec<ByteBuf, List<String>> NAMES = ByteBufCodecs.stringUtf8(48).apply(ByteBufCodecs.list());
	public static final StreamCodec<ByteBuf, TeamDraftPayload> CODEC = StreamCodec.composite(
			NAMES, TeamDraftPayload::blue,
			NAMES, TeamDraftPayload::red,
			NAMES, TeamDraftPayload::waiting,
			ByteBufCodecs.VAR_INT, TeamDraftPayload::mine,
			ByteBufCodecs.VAR_INT, TeamDraftPayload::left,
			ByteBufCodecs.VAR_INT, TeamDraftPayload::size,
			TeamDraftPayload::new);

	public static final TeamDraftPayload NONE = new TeamDraftPayload(List.of(), List.of(), List.of(), -1, 0, 3);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, TeamDraftPayload draft) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, draft);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
