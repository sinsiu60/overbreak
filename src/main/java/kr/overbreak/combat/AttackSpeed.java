package kr.overbreak.combat;

import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/**
 * 자체 공격속도 — 데이터팩 combat/on_hit · cd_tick 대응.
 *
 * 직업이 있는 플레이어의 바닐라 조준 근접 공격은 전부 막습니다 (공격 이벤트 FAIL).
 * 근접 직업의 좌클릭은 같은 클릭의 스윙 패킷에서 {@link MeleeCleave} 가 앞 범위로 처리하고 잠금을 겁니다.
 *
 *   공격속도 X = 1초에 X번.  잠금 틱 = 2000 / (X x 100)
 */
public final class AttackSpeed {
	private AttackSpeed() {}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer p)) {
				return InteractionResult.PASS;
			}
			PvpClass c = Attachments.profile(p).pvpClass;
			return c == null ? InteractionResult.PASS : InteractionResult.FAIL;
		});
	}

	public static void tick(PlayerProfile prof) {
		if (prof.atkCd > 0) {
			prof.atkCd--;
		}
	}
}
