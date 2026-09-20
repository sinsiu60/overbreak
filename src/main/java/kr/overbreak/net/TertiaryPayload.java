package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.input.InputRouter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 액티브3 키(기본 E — 인벤토리 키)를 누름.
 *
 * 모드 클라이언트는 전장에 있는 동안 인벤토리가 열리지 않고 이 신호를 보냅니다.
 * 모드가 없는 클라이언트는 예전대로 손 바꾸기(F)로도 같은 스킬이 나갑니다.
 */
public record TertiaryPayload() implements CustomPacketPayload {
	public static final TertiaryPayload INSTANCE = new TertiaryPayload();
	public static final Type<TertiaryPayload> TYPE = new Type<>(Overbreak.id("tertiary"));
	public static final StreamCodec<ByteBuf, TertiaryPayload> CODEC = StreamCodec.unit(INSTANCE);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> InputRouter.onTertiary(context.player())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
