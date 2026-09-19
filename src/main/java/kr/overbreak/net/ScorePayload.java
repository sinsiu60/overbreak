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
 * 서버 → 본인 클라이언트: 경기 점수판 (화면 위쪽 가운데 — 오버워치 방식).
 *
 * kind    0 = 없음 · {@link #DUEL} 1대1 · {@link #BRAWL} 대난투 · {@link #TEAM} 3대3 팀전
 * names   참가자 이름 (대난투는 점수 높은 순)
 * scores  같은 순서의 점수
 * self    이 화면 주인이 목록의 몇 번째인가 (-1 = 관전)
 * target  이기는 데 필요한 점수
 * note    아래 작은 글 (라운드 · 대기 안내 등)
 */
public record ScorePayload(int kind, List<String> names, List<Integer> scores, int self, int target, String note)
		implements CustomPacketPayload {
	public static final int NONE_KIND = 0;
	public static final int DUEL = 1;
	public static final int BRAWL = 2;
	public static final int TEAM = 3;

	public static final Type<ScorePayload> TYPE = new Type<>(Overbreak.id("score"));
	public static final StreamCodec<ByteBuf, ScorePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ScorePayload::kind,
			ByteBufCodecs.stringUtf8(48).apply(ByteBufCodecs.list()), ScorePayload::names,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ScorePayload::scores,
			ByteBufCodecs.VAR_INT, ScorePayload::self,
			ByteBufCodecs.VAR_INT, ScorePayload::target,
			ByteBufCodecs.stringUtf8(64), ScorePayload::note,
			ScorePayload::new);

	public static final ScorePayload NONE = new ScorePayload(NONE_KIND, List.of(), List.of(), -1, 0, "");

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void send(ServerPlayer p, ScorePayload score) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, score);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
