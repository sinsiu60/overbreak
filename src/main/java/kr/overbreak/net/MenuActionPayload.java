package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.game.Game;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 메인 화면에서 누른 것.
 *
 * action  TUTORIAL · TRAINING(arg = 직업) · QUEUE(arg = 직업, mode = 게임 종류) · CANCEL(대기 취소) · LOBBY(메인 화면으로) · FREE(관리자 자유 이동)
 * 서버가 조건(튜토리얼을 마쳤는가 · 메인 화면에 있는가)을 다시 확인합니다.
 */
public record MenuActionPayload(String action, String arg, String mode) implements CustomPacketPayload {
	public static final String TUTORIAL = "tutorial";
	public static final String TRAINING = "training";
	public static final String QUEUE = "queue";
	public static final String CANCEL = "cancel";
	public static final String LOBBY = "lobby";
	/** 관리자 전용 — 전장 · 훈련장을 나가 맵을 자유롭게 돌아다닙니다. */
	public static final String FREE = "free";
	/** 다시 살아날 때 쓸 규격 고르기 (arg = 직업). */
	public static final String PICK = "pick";
	/** 팀 격전 편 짜기에서 고른 편 (arg = "0" 청팀 · "1" 홍팀). */
	public static final String TEAM_PICK = "team_pick";
	public static final String MODE_DUEL = "duel";
	public static final String MODE_BRAWL = "brawl";
	/** 예전 이름 — 3대3 으로 봅니다. */
	public static final String MODE_TEAM = "team";
	public static final String MODE_TEAM2 = "team2";
	public static final String MODE_TEAM3 = "team3";

	public static final Type<MenuActionPayload> TYPE = new Type<>(Overbreak.id("menu_action"));
	public static final StreamCodec<ByteBuf, MenuActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), MenuActionPayload::action,
			ByteBufCodecs.stringUtf8(64), MenuActionPayload::arg,
			ByteBufCodecs.stringUtf8(32), MenuActionPayload::mode,
			MenuActionPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> Game.onAction(context.player(), payload.action(), payload.arg(), payload.mode())));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
