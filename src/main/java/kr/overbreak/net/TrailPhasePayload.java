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
 * 서버 → 같은 월드 모두: 궤적 해방의 단계 (궤적의 깃털 궁극기).
 *
 *   COLLECT   : 수집 시작 (발동) — elapsed = 발동 뒤 지난 시간
 *   TELEGRAPH : 예고 시작 (0.5초 뒤 폭발)
 *   DETONATE  : 폭발 — 궤적이 선을 따라 터지고 사라짐
 *   VANISH    : 시전자 사망 · 해제 — 폭발 없이 흘러내리며 사라짐
 *
 * elapsed: 그 단계가 시작된 뒤 지난 시간 (1/20초 단위) — 늦게 받은 사람은 그만큼 연출을 건너뜁니다.
 */
public record TrailPhasePayload(int casterId, int phase, int elapsed) implements CustomPacketPayload {
	public static final int COLLECT = 0;
	public static final int TELEGRAPH = 1;
	public static final int DETONATE = 2;
	public static final int VANISH = 3;

	public static final Type<TrailPhasePayload> TYPE = new Type<>(Overbreak.id("trail_phase"));
	public static final StreamCodec<ByteBuf, TrailPhasePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TrailPhasePayload::casterId,
			ByteBufCodecs.VAR_INT, TrailPhasePayload::phase,
			ByteBufCodecs.VAR_INT, TrailPhasePayload::elapsed,
			TrailPhasePayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** 시전자와 같은 월드의 모든 플레이어에게. */
	public static void broadcast(ServerPlayer caster, int phase) {
		for (ServerPlayer p : caster.level().players()) {
			sendTo(p, new TrailPhasePayload(caster.getId(), phase, 0));
		}
	}

	public static void sendTo(ServerPlayer viewer, TrailPhasePayload msg) {
		if (ServerPlayNetworking.canSend(viewer, TYPE)) {
			ServerPlayNetworking.send(viewer, msg);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
