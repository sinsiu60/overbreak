package kr.overbreak.skill;

import java.util.ArrayList;
import java.util.List;

import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.HudPayload;
import kr.overbreak.net.InputModePayload;
import net.minecraft.server.level.ServerPlayer;

/** 매 틱 스킬 HUD 상태를 만들어, 바뀌었으면 본인 클라이언트에 보냅니다 (모드 클라이언트만). */
public final class HudSync {
	private HudSync() {}

	public static void tick(ServerPlayer p, PlayerProfile prof) {
		if (!InputModePayload.canSend(p)) {
			return;
		}
		HudPayload hud = build(p, prof);
		if (!hud.equals(prof.lastHud)) {
			prof.lastHud = hud;
			HudPayload.send(p, hud);
		}
	}

	private static HudPayload build(ServerPlayer p, PlayerProfile prof) {
		PvpClass c = prof.pvpClass;
		if (c == null) {
			return HudPayload.NONE;
		}
		List<SkillSlot> slots = c.hudSlots(p);
		List<Integer> remaining = new ArrayList<>(slots.size());
		List<Integer> total = new ArrayList<>(slots.size());
		List<Boolean> active = new ArrayList<>(slots.size());
		for (SkillSlot s : slots) {
			// 남은 시간은 틱 → 시간 단위 (전체 길이 total 은 직업이 시간 단위로 줌)
			remaining.add(Math.max(0, kr.overbreak.core.tick.Ticks.toTime(s.remaining())));
			total.add(Math.max(1, s.total()));
			active.add(s.active());
		}
		int ultState = !prof.ultOn ? 0 : prof.ultCharge >= 100 ? 2 : 1;
		HudExtra x = c.hudExtra(p);
		// 기절은 직업과 무관하므로 여기서 한 번에 붙입니다 (클라이언트가 시야를 굳힙니다)
		int flags = x.flags();
		if (kr.overbreak.core.Attachments.combatant(p).stunT > 0) {
			flags |= HudExtra.FLAG_STUN;
		}
		return new HudPayload(c.id(), remaining, total, active, prof.ultCharge, ultState, x.ammo(), x.ammoMax(), x.meter(), x.meterKind(),
				x.stacks(), x.stacksMax(), flags);
	}
}
