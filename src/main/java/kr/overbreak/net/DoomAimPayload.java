package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 서버 → 시전자: 파멸의 일격이 겨누는 자리 (0.2a).
 *
 * 조준하는 동안 클라이언트는 강제로 3인칭이 되고 시야가 이 자리를 따라갑니다 (둠피스트 궁극기).
 * 끝나면 {@code active = false} 를 한 번 보내 원래 시점으로 되돌립니다.
 */
public record DoomAimPayload(boolean active, double x, double y, double z) implements CustomPacketPayload {
	public static final Type<DoomAimPayload> TYPE = new Type<>(Overbreak.id("doom_aim"));
	public static final StreamCodec<ByteBuf, DoomAimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DoomAimPayload::active,
			ByteBufCodecs.DOUBLE, DoomAimPayload::x,
			ByteBufCodecs.DOUBLE, DoomAimPayload::y,
			ByteBufCodecs.DOUBLE, DoomAimPayload::z,
			DoomAimPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, Vec3 target) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new DoomAimPayload(true, target.x, target.y, target.z));
		}
	}

	public static void stop(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new DoomAimPayload(false, 0, 0, 0));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
