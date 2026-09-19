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
 * 서버 → 본인 클라이언트: 지금 경기의 편 가르기 (테두리 색을 화면마다 다르게 그리기 위한 것).
 *
 * allies  같은 편의 엔티티 번호 (본인 제외) — 파란 테두리
 * foes    상대 편의 엔티티 번호 — 빨간 테두리
 *
 * 같은 사람이라도 보는 사람에 따라 색이 달라야 하므로 (내 편이 파랑) 스코어보드 팀 색 대신
 * 클라이언트가 직접 칠합니다. 비어 있는 payload 를 보내면 테두리가 사라집니다.
 */
public record TeamPayload(List<Integer> allies, List<Integer> foes) implements CustomPacketPayload {
	public static final Type<TeamPayload> TYPE = new Type<>(Overbreak.id("team"));
	public static final StreamCodec<ByteBuf, TeamPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TeamPayload::allies,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TeamPayload::foes,
			TeamPayload::new);

	public static final TeamPayload NONE = new TeamPayload(List.of(), List.of());

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void send(ServerPlayer p, TeamPayload teams) {
		if (canSend(p)) {
			ServerPlayNetworking.send(p, teams);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
