package kr.overbreak.classes.brute;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * [E] 전열 재정비 — 숨을 고르며 상처를 눌러 닫습니다.
 *
 *   {@value #DURATION} (1/20초 단위 = 1초) 동안 채널링. 걸을 수는 있지만 이동속도 -30%, 평타 · 다른 스킬 불가
 *   숨을 고르는 동안 받는 피해 {@value #GUARD_PERCENT}% 감소 — 맞으면서도 버틸 수 있게 (0.1f)
 *   끝까지 버티면 체력 {@value #HEAL} 회복 + 투기 {@value #FERVOR} 스택
 *   중간에 기절 · 넘어지면 끊기고, 그때까지 찬 만큼만 회복합니다 (쿨타임은 그대로)
 *   쿨타임 12초 (누른 순간부터)
 */
final class Regroup implements Effects.Active {
	static final int COOLDOWN = 240;
	static final int DURATION = 20;
	/** 끝까지 버텼을 때 회복량. */
	static final int HEAL = 60;
	static final int FERVOR = 5;
	static final double SLOW = 0.3;
	/** 채널링 중 받는 피해 감소 (%). */
	static final int GUARD_PERCENT = 40;

	private static final Identifier SLOW_ID = Overbreak.id("brute_regroup_slow");
	private static final int GREEN = Fx.rgb(0.45, 0.95, 0.45);

	private final ServerPlayer caster;
	private final BruteState state;
	private int t;
	private float healed;

	private Regroup(ServerPlayer caster, BruteState state) {
		this.caster = caster;
		this.state = state;
	}

	/** 숨을 고르는 동안인가 (Brute 가 DamageModifier 에서 봅니다). */
	static boolean guarding(BruteState st) {
		return st.regroup != null;
	}

	static void cast(ServerPlayer p, BruteState st) {
		Attachments.profile(p).setCooldown(Brute.REGROUP, COOLDOWN);
		Regroup r = new Regroup(p, st);
		st.regroup = r;
		Attachments.combatant(p).casting = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SLOW_ID, -SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.BR_REGROUP, -1);
		Fx.sound(p, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.9F, 0.7F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.2F);
		Attachments.profile(p).msgT = 25;
		Hud.actionbar(p, Component.empty().append(Hud.bold("전열 재정비", ChatFormatting.GREEN))
				.append(Hud.text("  숨을 고르는 중...  받는 피해 -40%", ChatFormatting.GRAY)));
		Effects.add(r);
	}

	/** 채널링 진행도 0~100 (조준점 아래 게이지). */
	int percent() {
		return Math.min(100, t * 100 / Math.max(1, Ticks.of(DURATION)));
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		Combatant c = Attachments.combatant(caster);
		if (c.stunT > 0 || c.knockT > 0 || c.breakT > 0) {
			interrupt();
			return false;
		}
		t++;
		int total = Ticks.of(DURATION);
		// 회복은 틱마다 조금씩 — 끊겨도 찬 만큼은 남습니다
		float step = HEAL / (float) total;
		caster.heal(step);
		healed += step;
		if (Ticks.ambient() && caster.level() instanceof ServerLevel level) {
			ServerPlayer self = SkillAnimPayload.canSend(caster) ? caster : null;
			Fx.particleExcept(level, self, Fx.dust(GREEN, 1.0F), caster.getX(), caster.getY() + 1.0, caster.getZ(), 4, 0.35, 0.5, 0.35, 0);
			Fx.particleExcept(level, self, ParticleTypes.HAPPY_VILLAGER, caster.getX(), caster.getY() + 1.0, caster.getZ(), 1, 0.3, 0.5, 0.3, 0);
		}
		if (t >= total) {
			finish();
			return false;
		}
		return true;
	}

	private void finish() {
		Fervor.gain(caster, state, FERVOR);
		Fx.sound(caster, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
		Attachments.profile(caster).msgT = 35;
		Hud.actionbar(caster, Component.empty().append(Hud.bold("전열 재정비", ChatFormatting.GREEN))
				.append(Hud.text("   체력 +" + Math.round(healed), ChatFormatting.GREEN))
				.append(Hud.text("   투기 +" + FERVOR, ChatFormatting.GOLD)));
		cleanup();
	}

	private void interrupt() {
		SkillAnimPayload.stop(caster, SkillAnimPayload.BR_REGROUP);
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(caster).msgT = 30;
		Hud.actionbar(caster, Component.empty().append(Hud.text("재정비가 끊겼다   ", ChatFormatting.GRAY))
				.append(Hud.text("체력 +" + Math.round(healed), ChatFormatting.DARK_GREEN)));
		cleanup();
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public Object owner() {
		return caster;
	}

	private void cleanup() {
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW_ID);
		Attachments.combatant(caster).casting = false;
		if (state.regroup == this) {
			state.regroup = null;
		}
	}
}
