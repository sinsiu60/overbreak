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
 * 서버 → 본인 클라이언트: 튜토리얼 연출 지시.
 *
 * cue  BOOT      가상세계가 켜지는 연출 (시야가 좁은 틈에서 점점 넓어짐) — arg = 길이 (1/20초 단위)
 *      POINT     HUD 의 어딘가를 화살표로 가리킴 — arg = {@link #P_SKILL1} … , text = 화살표 밑에 붙는 글
 *      CLEAR     화살표 치우기
 *      GLITCH    짧은 지직거림 (규격 수신 등) — arg = 길이
 *      OFF       튜토리얼 연출 전부 끄기 (부팅 화면 · 대사창 · 화살표) — 튜토리얼을 벗어날 때
 */
public record TutorialCuePayload(int cue, int arg, String text) implements CustomPacketPayload {
	public static final int BOOT = 0;
	public static final int POINT = 1;
	public static final int CLEAR = 2;
	public static final int GLITCH = 3;
	public static final int OFF = 4;

	/** 가리킬 곳. */
	public static final int P_SKILL1 = 0;
	public static final int P_SKILL2 = 1;
	public static final int P_SKILL3 = 2;
	public static final int P_ULT = 3;
	public static final int P_WEAPON = 4;
	public static final int P_HEALTH = 5;
	public static final int P_CENTER = 6;

	public static final Type<TutorialCuePayload> TYPE = new Type<>(Overbreak.id("tutorial_cue"));
	public static final StreamCodec<ByteBuf, TutorialCuePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TutorialCuePayload::cue,
			ByteBufCodecs.VAR_INT, TutorialCuePayload::arg,
			ByteBufCodecs.stringUtf8(64), TutorialCuePayload::text,
			TutorialCuePayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, int cue, int arg, String text) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, new TutorialCuePayload(cue, arg, text));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
