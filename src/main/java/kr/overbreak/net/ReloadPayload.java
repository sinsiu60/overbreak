package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.input.InputRouter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 클라이언트 → 서버: 재장전 키(기본 R)를 누름. 모드 클라이언트 전용 — 바닐라에는 재장전 키가 없습니다. */
public record ReloadPayload() implements CustomPacketPayload {
	public static final ReloadPayload INSTANCE = new ReloadPayload();
	public static final Type<ReloadPayload> TYPE = new Type<>(Overbreak.id("reload"));
	public static final StreamCodec<ByteBuf, ReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> InputRouter.onReload(context.player())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
