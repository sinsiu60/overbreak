package kr.overbreak.ult;

import kr.overbreak.net.InputModePayload;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 궁극기 게이지 — 데이터팩 ult/recalc · charge_add · gain_pre/post · tick · hud 대응.
 *
 * 데이터팩은 스킬 피해가 damage_dealt 통계에 안 잡혀 "피해 전후로 통계를 비교" 했습니다.
 * 여기서는 AFTER_DAMAGE 한 곳이 평타·스킬·모든 경로의 피해를 받습니다.
 *
 *   raw = 피해 누적 (체력 10배 기준 — 피해 10 = raw 10 = 2%),  게이지% = raw x rate / 1000,  100% 값은 올림으로 구해 거기서 자름
 */
public final class UltGauge {
	/** 지속 피해 지대처럼 매 틱 피해가 들어가는 곳은 1초에 한 번만 채웁니다. 켜 둔 동안 충전하지 않습니다. */
	public static boolean muted;

	private UltGauge() {}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (muted || !(source.getEntity() instanceof ServerPlayer p) || p == target || taken <= 0) {
				return;
			}
			addTaken(p, taken);
		});
	}

	/** 받은 피해를 raw 로 — 소수 피해(산탄 발당 3.5)는 나머지를 모아 두어 반올림이 쌓이지 않게 합니다. */
	public static void addTaken(ServerPlayer p, float taken) {
		PlayerProfile prof = Attachments.profile(p);
		prof.ultFrac += taken;
		int whole = (int) Math.floor(prof.ultFrac + 1.0E-3F);
		prof.ultFrac -= whole;
		if (whole > 0) {
			addRaw(p, whole);
		}
	}

	/** 100% 에 해당하는 raw (올림). */
	public static int urmax(PlayerProfile prof) {
		int rate = prof.ultRate > 0 ? prof.ultRate : 200;
		return (100000 + rate - 1) / rate;
	}

	public static void addRaw(ServerPlayer p, int raw) {
		PlayerProfile prof = Attachments.profile(p);
		if (!prof.ultOn || (prof.pvpClass != null && !prof.pvpClass.ultCharging(p))) {
			return;
		}
		prof.ultRaw += raw;
		recalc(prof);
	}

	/** 게이지 퍼센트로 더합니다 (맵 에너지 +25% 등). */
	public static void addPercent(ServerPlayer p, int percent) {
		PlayerProfile prof = Attachments.profile(p);
		addRaw(p, urmax(prof) * percent / 100);
	}

	public static void fill(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.ultRaw = urmax(prof);
		recalc(prof);
	}

	public static void recalc(PlayerProfile prof) {
		int max = urmax(prof);
		prof.ultRaw = Math.max(0, Math.min(prof.ultRaw, max));
		int rate = prof.ultRate > 0 ? prof.ultRate : 200;
		prof.ultCharge = Math.min(100, prof.ultRaw * rate / 1000);
	}

	public static void reset(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.ultRaw = 0;
		prof.ultCharge = 0;
		prof.ultFrac = 0.0F;
		prof.ultHas = false;
		SkillItems.removeUlt(p);
	}

	/** 궁극기를 쓴 순간: 게이지 0 + 아이템 회수. */
	public static void consume(ServerPlayer p) {
		reset(p);
	}

	/** 매 틱: 100% 면 아이템 지급, 아니면 회수, 그리고 HUD. */
	public static void tick(ServerPlayer p, PlayerProfile prof) {
		PvpClass c = prof.pvpClass;
		if (c == null || !prof.ultOn) {
			return;
		}
		if (prof.ultCharge >= 100 && !prof.ultHas) {
			giveItem(p, c, prof);
		} else if (prof.ultCharge < 100 && prof.ultHas) {
			prof.ultHas = false;
			SkillItems.removeUlt(p);
		}
		if (prof.msgT <= 0 && Attachments.combatant(p).stunT <= 0 && !InputModePayload.canSend(p)) {
			hud(p, prof);
		}
	}

	private static void giveItem(ServerPlayer p, PvpClass c, PlayerProfile prof) {
		ItemStack item = c.ultItem();
		if (item.isEmpty()) {
			return;
		}
		prof.ultHas = true;
		if (!InputModePayload.canSend(p)) {
			// 모드 없는 클라이언트: 핫바 아이템과 액션바로 알림 (모드 클라이언트는 궁극기 게이지 HUD)
			p.getInventory().setItem(3, item);
			prof.msgT = 60;
			Hud.actionbar(p, Hud.bold("궁극기 준비 완료!", ChatFormatting.GOLD));
		}
		Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.sound(p, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.3F, 1.8F);
	}

	private static void hud(ServerPlayer p, PlayerProfile prof) {
		Component bar;
		if (prof.ultCharge >= 100) {
			bar = Component.empty()
					.append(Hud.text("궁극기  ", ChatFormatting.GRAY))
					.append(Hud.bold(Hud.bar10(10), ChatFormatting.GOLD))
					.append(Hud.bold("  준비 완료", ChatFormatting.GOLD));
		} else {
			bar = Component.empty()
					.append(Hud.text("궁극기  ", ChatFormatting.GRAY))
					.append(Hud.text(Hud.bar10(prof.ultCharge / 10), ChatFormatting.RED))
					.append(Hud.text("  ", ChatFormatting.GRAY))
					.append(Hud.text(String.valueOf(prof.ultCharge), ChatFormatting.WHITE))
					.append(Hud.text("%", ChatFormatting.GRAY));
		}
		Hud.actionbar(p, bar);
	}
}
