package kr.overbreak.input;

import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.InputModePayload;
import net.minecraft.server.level.ServerPlayer;

/** 직업이 생기거나 없어진 틱에 클라이언트 조작 모드를 바꿉니다 (좌클릭 신호 · 스윙 모션 없음). */
public final class InputModeSync {
	private InputModeSync() {}

	public static void tick(ServerPlayer p, PlayerProfile prof) {
		boolean active = prof.pvpClass != null;
		if (active != prof.sentInputMode) {
			prof.sentInputMode = active;
			InputModePayload.send(p, active);
		}
	}
}
