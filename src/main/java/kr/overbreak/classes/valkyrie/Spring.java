package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Motion;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * [웅크리기] 차원 도약 — 데이터팩 skill/spring/* + class/valkyrie/air_tick 대응.
 *
 *   조준 방향으로 크게 뛰어오른 뒤 천천히 떨어짐 (느린 낙하 최대 3초)
 *   이렇게 뜬 동안 땅에 안 닿아 있으면 연사 +25% (그냥 점프 · 낙하로는 안 오름)
 *   착지하면 즉시 걷힘 — 이륙 직후 0.5초는 아직 발이 땅에 붙어 있어 착지로 보지 않음
 *   이동기라 균열 지대 위에서는 막히고 쿨타임도 먹지 않음. 쿨타임 12초
 */
final class Spring {
	static final int COOLDOWN = 240;
	static final double POWER = 1.17;
	static final int GRACE = 10;
	static final int SLOW_FALL = 60;
	private static final int CYAN = Fx.rgb(0.45, 0.90, 1.00);

	private Spring() {}

	static void cast(ServerPlayer p, ValkyrieState st) {
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (Cooldowns.blocked(p, Valkyrie.SPRING, "차원 도약", ChatFormatting.AQUA)) {
			return;
		}
		Attachments.profile(p).setCooldown(Valkyrie.SPRING, COOLDOWN);
		Rifle.interrupt(p, st);
		Motion.launch(p, Aim.direction(p), POWER);
		st.floating = true;
		st.floatGrace = Ticks.of(GRACE);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Ticks.of(SLOW_FALL), 0, false, false));
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_FLOAT, -1);

		ServerLevel level = p.level();
		Fx.particle(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 40, 0.5, 0.1, 0.5, 0.08);
		Fx.particle(level, Fx.dust(CYAN, 1.4F), p.getX(), p.getY() + 0.5, p.getZ(), 30, 0.4, 0.5, 0.4, 0);
		Fx.particle(level, ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.3, 0.4, 0.3, 0.08);
		Fx.sound(p, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2F, 1.0F);
		Fx.sound(p, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0F, 1.4F);
		if (!InputModePayload.canSend(p)) {
			Attachments.profile(p).msgT = 15;
			Hud.actionbar(p, Component.empty()
					.append(Hud.bold("차원 도약", ChatFormatting.AQUA))
					.append(Hud.text("  공중에서 공격속도 +25%", ChatFormatting.WHITE)));
		}
	}

	/** 직업 틱 — 가속 여부 · 착지. 연사보다 먼저 불러야 같은 틱의 간격에 반영됩니다. */
	static void tick(ServerPlayer p, ValkyrieState st) {
		if (st.floatGrace > 0) {
			st.floatGrace--;
		}
		boolean boosted = st.floating && !p.onGround();
		if (boosted != st.boosted) {
			st.boosted = boosted;
			if (boosted) {
				Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 2.0F);
			} else {
				st.seq = 0;
				Fx.sound(p, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
			}
		}
		if (st.boosted && Ticks.ambient()) {
			Fx.particle(p.level(), Fx.dust(Fx.rgb(1.00, 0.85, 0.35), 0.7F), p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.25, 0.05, 0.25, 0);
		}
		if (st.floating && p.onGround() && st.floatGrace <= 0) {
			end(p, st);
		}
	}

	static void end(ServerPlayer p, ValkyrieState st) {
		if (!st.floating) {
			return;
		}
		st.floating = false;
		st.floatGrace = 0;
		st.boosted = false;
		st.seq = 0;
		p.removeEffect(MobEffects.SLOW_FALLING);
		SkillAnimPayload.stop(p, SkillAnimPayload.VK_FLOAT);
	}
}
