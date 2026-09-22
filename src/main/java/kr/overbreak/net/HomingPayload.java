package kr.overbreak.net;

import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 서버 → 같은 월드 모두: 궤적 추격(궤적의 깃털 궁극기)의 유도 탄 한 발.
 *
 * 출발점 · 방향 · 유도 대상(-1 = 없음)만 보내고, 곡선은 각 클라이언트가 서버와 같은 규칙으로 그립니다 (client/fx/HomingView).
 * 명중 판정은 서버가 따로 합니다 — 틱마다 탄 위치를 보내지 않아 가볍습니다.
 */
public record HomingPayload(int casterId, Vec3 start, Vec3 dir, int targetId) implements CustomPacketPayload {
	public static final Type<HomingPayload> TYPE = new Type<>(Overbreak.id("homing"));
	public static final StreamCodec<FriendlyByteBuf, HomingPayload> CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeVarInt(m.casterId);
				vec(buf, m.start);
				vec(buf, m.dir);
				buf.writeVarInt(m.targetId + 1);
			},
			buf -> new HomingPayload(buf.readVarInt(), vec(buf), vec(buf), buf.readVarInt() - 1));

	private static void vec(FriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 vec(FriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** 시전자와 같은 월드의 모든 플레이어에게. */
	public static void broadcast(ServerPlayer caster, HomingPayload msg) {
		for (ServerPlayer p : caster.level().players()) {
			if (ServerPlayNetworking.canSend(p, TYPE)) {
				ServerPlayNetworking.send(p, msg);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
