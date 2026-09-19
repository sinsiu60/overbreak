package kr.overbreak.skill;

import java.util.Map;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** 쿨타임 — 데이터팩 pvp.cd_* 감소와 skill/cd/update(막대) 대응. */
public final class Cooldowns {
	private Cooldowns() {}

	/** 쿨타임이 돌고 있으면 안내하고 true. 데이터팩 on_cooldown 과 같은 문구입니다. */
	public static boolean blocked(ServerPlayer p, String key, String skillName, ChatFormatting color) {
		PlayerProfile prof = Attachments.profile(p);
		int left = prof.cooldown(key);
		if (left <= 0) {
			return false;
		}
		// 모드 클라이언트는 스킬 HUD 칸이 빨갛게 흔들리며 알려 줍니다
		if (!InputModePayload.canSend(p)) {
			int sec = (left + 19) / 20;
			prof.msgT = 40;
			Hud.actionbar(p, Component.empty()
					.append(Hud.bold(skillName, color))
					.append(Hud.text(" 재사용 대기 ", ChatFormatting.GRAY))
					.append(Hud.text(String.valueOf(sec), ChatFormatting.WHITE))
					.append(Hud.text("초", ChatFormatting.GRAY)));
		}
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.6F, 0.7F);
		return true;
	}

	public static void start(ServerPlayer p, String key, int ticks) {
		Attachments.profile(p).setCooldown(key, ticks);
	}

	/** 매 틱: 1 씩 줄이고, 막대 단계가 바뀐 스킬만 아이템을 갱신합니다. */
	public static void tick(ServerPlayer p, Map<String, Integer> totals) {
		PlayerProfile prof = Attachments.profile(p);
		for (Map.Entry<String, Integer> e : prof.cooldowns.entrySet()) {
			if (e.getValue() > 0) {
				e.setValue(e.getValue() - 1);
			}
		}
		if (InputModePayload.canSend(p)) {
			return; // 아이템 막대 대신 스킬 HUD
		}
		for (Map.Entry<String, Integer> t : totals.entrySet()) {
			int step = SkillItems.barStep(prof.cooldown(t.getKey()), t.getValue());
			Integer shown = prof.barShown.get(t.getKey());
			if (shown == null || shown != step) {
				SkillItems.applyBar(p, t.getKey(), step);
				prof.barShown.put(t.getKey(), step);
			}
		}
	}
}
