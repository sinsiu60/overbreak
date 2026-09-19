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
 * 서버 → 본인 클라이언트: 화면 위쪽 대사창 한 줄 (「코어」 초상화 + 글자가 한 자씩 찍힘).
 *
 * speaker  말하는 이 ("코어"), 비면 창을 닫음
 * text     한 줄 (클라이언트가 타자기처럼 찍어 냄)
 * mood     0 평범 · 1 강조(붉은 눈) · 2 조용함
 * hold     다 찍은 뒤 남겨 둘 시간 (1/20초 단위) — 지나면 스스로 사라짐. 서버가 다음 줄을 보내면 바로 교체
 */
public record DialoguePayload(String speaker, String text, int mood, int hold) implements CustomPacketPayload {
	public static final int MOOD_PLAIN = 0;
	public static final int MOOD_SHARP = 1;
	public static final int MOOD_SOFT = 2;

	public static final DialoguePayload CLOSE = new DialoguePayload("", "", 0, 0);

	public static final Type<DialoguePayload> TYPE = new Type<>(Overbreak.id("dialogue"));
	public static final StreamCodec<ByteBuf, DialoguePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), DialoguePayload::speaker,
			ByteBufCodecs.stringUtf8(512), DialoguePayload::text,
			ByteBufCodecs.VAR_INT, DialoguePayload::mood,
			ByteBufCodecs.VAR_INT, DialoguePayload::hold,
			DialoguePayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void send(ServerPlayer p, DialoguePayload msg) {
		if (canSend(p)) {
			ServerPlayNetworking.send(p, msg);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
