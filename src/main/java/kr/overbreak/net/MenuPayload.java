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
 * 서버 → 본인 클라이언트: 메인 화면 상태.
 *
 * screen        CLOSED = 게임 중 (화면 닫기) · MAIN = 메인 화면 · QUEUE = 매칭 대기 · RESPAWN = 다시 살아날 때 규격 고르기
 * tutorialDone  튜토리얼을 마쳤는가 — 플레이를 누르면 튜토리얼인지 규격 선택인지 클라이언트가 고릅니다
 * detail        대기 중인 게임 종류 등 (화면에 쓰는 글)
 * admin         관리자(게임마스터 권한)인가 — 메인 화면에 「관리자 모드」 가 보입니다
 */
public record MenuPayload(int screen, boolean tutorialDone, String detail, boolean admin) implements CustomPacketPayload {
	public static final int CLOSED = 0;
	public static final int MAIN = 1;
	public static final int QUEUE = 2;
	/** 쓰러져 있는 동안 규격을 바꿀 수 있는 창 (대난투). */
	public static final int RESPAWN = 3;
	/** 팀 격전 편 짜기 창. */
	public static final int TEAM_DRAFT = 4;

	public static final Type<MenuPayload> TYPE = new Type<>(Overbreak.id("menu"));
	public static final StreamCodec<ByteBuf, MenuPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MenuPayload::screen,
			ByteBufCodecs.BOOL, MenuPayload::tutorialDone,
			ByteBufCodecs.STRING_UTF8, MenuPayload::detail,
			ByteBufCodecs.BOOL, MenuPayload::admin,
			MenuPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void send(ServerPlayer p, int screen, boolean tutorialDone, String detail) {
		if (canSend(p)) {
			ServerPlayNetworking.send(p, new MenuPayload(screen, tutorialDone, detail, admin(p)));
		}
	}

	/** 게임마스터 이상이면 관리자 기능을 보여 줍니다. */
	public static boolean admin(ServerPlayer p) {
		return p.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
