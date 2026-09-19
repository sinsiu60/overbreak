package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.combat.Aim;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 조준점. 어깨 너머 시점에서 화면 한가운데 조준선이 닿는 곳입니다.
 * 클라이언트는 어깨 너머 시점일 때만 매 틱 보냅니다. 서버 사용법은 {@link Aim}.
 */
public record AimPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final Type<AimPayload> TYPE = new Type<>(Overbreak.id("aim"));
	public static final StreamCodec<ByteBuf, AimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, AimPayload::x,
			ByteBufCodecs.DOUBLE, AimPayload::y,
			ByteBufCodecs.DOUBLE, AimPayload::z,
			AimPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> Aim.receive(context.player(), payload));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
