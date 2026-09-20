package kr.overbreak.core;

import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 전장에서는 웅크리기 키가 자세를 낮추지 않습니다 — 웅크리기는 액티브2 스킬 키라서
 * 누를 때마다 몸이 주저앉고 눈높이가 꺼지면 조준이 흔들립니다 (0.2d).
 *
 * 키 상태({@code isShiftKeyDown}) 자체는 그대로 서버로 갑니다 — 스킬은 그 신호로 나갑니다.
 * 여기서 막는 것은 <b>자세</b>(Pose.CROUCHING) 뿐이라, 웅크리기로 붙는 이동 속도 감소도 함께 사라집니다.
 */
public final class Crouch {
	/** 클라이언트가 채워 넣는 판정 (본인 플레이어 + 조작 모드). 서버에서는 쓰이지 않습니다. */
	public static Predicate<Player> clientCheck = p -> false;

	private Crouch() {}

	/** 이 플레이어의 웅크린 자세를 막을 것인가. */
	public static boolean suppressed(Player p) {
		if (p instanceof ServerPlayer sp) {
			return Attachments.profile(sp).pvpClass != null;
		}
		return clientCheck.test(p);
	}
}
