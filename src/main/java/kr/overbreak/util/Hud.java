package kr.overbreak.util;

import kr.overbreak.core.tick.Ticks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * 액션바 · 타이틀. 클라이언트 HUD 가 붙기 전까지는 데이터팩과 같은 표현을 씁니다.
 * 바닐라 클라이언트로 접속해도 최소한 이것은 보입니다.
 */
public final class Hud {
	private Hud() {}

	public static void actionbar(ServerPlayer p, Component c) {
		p.connection.send(new ClientboundSetActionBarTextPacket(c));
	}

	public static void title(ServerPlayer p, Component title, Component subtitle, int in, int stay, int out) {
		// 제목 표시 시간은 클라이언트 틱으로 셈 → 시간 단위를 지금 틱레이트의 틱으로
		p.connection.send(new ClientboundSetTitlesAnimationPacket(kr.overbreak.core.tick.Ticks.of(in), kr.overbreak.core.tick.Ticks.of(stay), kr.overbreak.core.tick.Ticks.of(out)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** ■ 채움 / □ 빈칸, 10칸. 폭이 같은 문자라 값이 바뀌어도 줄이 흔들리지 않습니다. */
	public static String bar10(int filled) {
		int f = Math.max(0, Math.min(10, filled));
		return "■".repeat(f) + "□".repeat(10 - f);
	}

	public static MutableComponent text(String s, ChatFormatting... fmt) {
		return Component.literal(s).withStyle(fmt);
	}

	public static MutableComponent bold(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(color, ChatFormatting.BOLD);
	}
}
