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
 * 서버 → 같은 월드 모두: 궤적 해방의 궤적 한 줄 (궤적의 깃털 궁극기).
 *
 * 적에게도 반드시 보내야 합니다 — 궤적이 보이는 것이 이 궁극기의 대응 수단입니다.
 * age: 생긴 뒤 지난 시간 (1/20초 단위) — 늦게 받은 사람은 그어지는 연출을 건너뜁니다.
 */
public record TrailPayload(int casterId, int index, Vec3 start, Vec3 end, boolean crit, int age) implements CustomPacketPayload {
	public static final Type<TrailPayload> TYPE = new Type<>(Overbreak.id("trail"));
	public static final StreamCodec<FriendlyByteBuf, TrailPayload> CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeVarInt(m.casterId);
				buf.writeVarInt(m.index);
				vec(buf, m.start);
				vec(buf, m.end);
				buf.writeBoolean(m.crit);
				buf.writeVarInt(m.age);
			},
			buf -> new TrailPayload(buf.readVarInt(), buf.readVarInt(), vec(buf), vec(buf), buf.readBoolean(), buf.readVarInt()));

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
	public static void broadcast(ServerPlayer caster, TrailPayload msg) {
		for (ServerPlayer p : caster.level().players()) {
			sendTo(p, msg);
		}
	}

	public static void sendTo(ServerPlayer viewer, TrailPayload msg) {
		if (ServerPlayNetworking.canSend(viewer, TYPE)) {
			ServerPlayNetworking.send(viewer, msg);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
