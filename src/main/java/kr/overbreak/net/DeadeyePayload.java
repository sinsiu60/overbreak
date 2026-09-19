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
 * 서버 → 시전자 클라이언트: 황야의 무법자 조준 상태 (매 틱). 클라이언트가 가장자리 어둠 · 조준한 적 표식을 그립니다.
 *
 * elapsed 조준 시작 후 틱, total 조준 전체 틱 (0 = 끝남 · 숨김)
 * damage  발사 피해 (표식이 "처치 가능" 으로 바뀌는 기준 — 대상 체력 이하이면 빨갛게)
 * targets 조준한 엔티티 id
 */
public record DeadeyePayload(int elapsed, int total, int damage, List<Integer> targets) implements CustomPacketPayload {
	public static final Type<DeadeyePayload> TYPE = new Type<>(Overbreak.id("deadeye"));
	public static final StreamCodec<ByteBuf, DeadeyePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DeadeyePayload::elapsed,
			ByteBufCodecs.VAR_INT, DeadeyePayload::total,
			ByteBufCodecs.VAR_INT, DeadeyePayload::damage,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DeadeyePayload::targets,
			DeadeyePayload::new);

	public static final DeadeyePayload NONE = new DeadeyePayload(0, 0, 0, List.of());

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, DeadeyePayload msg) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, msg);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
