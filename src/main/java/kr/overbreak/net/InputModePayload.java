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
 * 서버 → 본인 클라이언트: OVERBREAK 조작 모드를 쓰는가 (= 직업이 있는가).
 *
 * 켜져 있으면 클라이언트는
 *   - 좌클릭을 바닐라 공격 · 블록 부수기로 쓰지 않고 {@link LeftClickPayload} 로만 보냅니다
 *   - 팔 휘두르기(스윙) 모션을 아예 하지 않습니다 (공격 모션은 서버의 기본 공격 애니메이션만)
 * 바뀔 때만 보냅니다 (input/InputModeSync).
 */
public record InputModePayload(boolean active) implements CustomPacketPayload {
	public static final Type<InputModePayload> TYPE = new Type<>(Overbreak.id("input_mode"));
	public static final StreamCodec<ByteBuf, InputModePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, InputModePayload::active,
			InputModePayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void send(ServerPlayer p, boolean active) {
		if (canSend(p)) {
			ServerPlayNetworking.send(p, new InputModePayload(active));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
