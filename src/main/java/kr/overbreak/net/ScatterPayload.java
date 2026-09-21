package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 클라이언트: 돌진 난사 한 번의 시드 · 대쉬 방향.
 *
 * 난사 팔 방향은 무작위지만 시드 하나로 정해지므로(인덱스 해시), 이것만 받으면
 * 모든 클라이언트가 추가 패킷 없이 같은 순간 같은 방향으로 쏘는 모습을 봅니다.
 * dashYaw 는 대쉬가 나가는 방향 (시전 순간의 시선 yaw) — 몸이 이 방향을 기준으로 돕니다.
 */
public record ScatterPayload(int entityId, int seed, float dashYaw) implements CustomPacketPayload {
	public static final Type<ScatterPayload> TYPE = new Type<>(Overbreak.id("scatter"));
	public static final StreamCodec<ByteBuf, ScatterPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ScatterPayload::entityId,
			ByteBufCodecs.INT, ScatterPayload::seed,
			ByteBufCodecs.FLOAT, ScatterPayload::dashYaw,
			ScatterPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** 본인 + 보고 있는 사람 모두. */
	public static void broadcast(ServerPlayer caster, int seed, float dashYaw) {
		ScatterPayload msg = new ScatterPayload(caster.getId(), seed, dashYaw);
		if (ServerPlayNetworking.canSend(caster, TYPE)) {
			ServerPlayNetworking.send(caster, msg);
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(caster)) {
			if (viewer != caster && ServerPlayNetworking.canSend(viewer, TYPE)) {
				ServerPlayNetworking.send(viewer, msg);
			}
		}
	}

	/** 한 사람에게만 (도중에 보기 시작한 관전자). */
	public static void sendTo(ServerPlayer viewer, ServerPlayer caster, int seed, float dashYaw) {
		if (ServerPlayNetworking.canSend(viewer, TYPE)) {
			ServerPlayNetworking.send(viewer, new ScatterPayload(caster.getId(), seed, dashYaw));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
