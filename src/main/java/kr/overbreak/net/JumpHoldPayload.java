package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.core.Attachments;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 점프 키를 누름 · 뗌 (바뀐 순간에만).
 *
 * 서버는 점프 키 자체를 볼 수 없습니다 (바닐라는 실제로 뛴 결과만 옵니다).
 * 궤적의 깃털의 체공 훈풍처럼 "공중에서 점프 키를 누르고 있는 동안" 이 조건인 스킬이 이 신호를 씁니다.
 */
public record JumpHoldPayload(boolean down) implements CustomPacketPayload {
	public static final Type<JumpHoldPayload> TYPE = new Type<>(Overbreak.id("jump_hold"));
	public static final StreamCodec<ByteBuf, JumpHoldPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, JumpHoldPayload::down,
			JumpHoldPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> Attachments.profile(context.player()).jumpDown = payload.down()));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
