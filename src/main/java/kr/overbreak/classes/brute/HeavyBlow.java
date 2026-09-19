package kr.overbreak.classes.brute;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [우클릭] 강타 — 대검을 머리 위로 들었다가 앞을 내리찍습니다.
 *
 *   0.3초(6틱) 준비 (정신집중 — 기절하면 끊김) → 앞 {@value #ARC}도 · {@value #RADIUS}칸
 *   피해 70 + 투기 스택당 4 (가득이면 110) · {@value #STUN} (1/20초 = 0.6초) 기절
 *   한 명이라도 맞히면 투기 3스택
 *   쿨타임 7초 (누른 순간부터)
 */
final class HeavyBlow implements Effects.Active {
	static final int COOLDOWN = 140;
	static final int WINDUP = 6;
	static final int END = 14;
	static final double RADIUS = 4.0;
	static final double ARC = 120.0;
	/** 기본 피해 (10배 기준). */
	static final int DAMAGE_10 = 700;
	/** 투기 스택당 추가 피해 (10배 기준). */
	static final int PER_STACK_10 = 40;
	static final int STUN = 12;
	static final int HIT_FERVOR = 3;
	private static final int ORANGE = Fx.rgb(1.00, 0.50, 0.15);

	private final ServerPlayer caster;
	private final BruteState state;
	private int t;
	private @Nullable GroundShape range;

	private HeavyBlow(ServerPlayer caster, BruteState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, BruteState st) {
		Attachments.profile(p).setCooldown(Brute.BLOW, COOLDOWN);
		Attachments.combatant(p).casting = true;
		HeavyBlow b = new HeavyBlow(p, st);
		st.blow = b;
		ServerLevel level = (ServerLevel) p.level();
		b.range = GroundShape.sector(level, p.position(), Aim.facingYawPitch(p)[0], ARC, RADIUS, 0x40FF7A28, 0xD8FFA050);
		b.range.reveal(0.0);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.BR_BLOW, -1);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.sound(p, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.5F);
		Attachments.profile(p).msgT = 20;
		Hud.actionbar(p, Component.empty().append(Hud.bold("강타", ChatFormatting.GOLD))
				.append(Hud.text("  내리찍는 중...", ChatFormatting.GRAY)));
		Effects.add(b);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		Combatant c = Attachments.combatant(caster);
		int windup = Ticks.of(WINDUP);
		if (t < windup && (c.stunT > 0 || c.knockT > 0 || c.breakT > 0)) {
			interrupt();
			return false;
		}
		t++;
		ServerLevel level = (ServerLevel) caster.level();
		if (t < windup) {
			if (range != null) {
				range.moveTo(caster.position());
				range.reveal(t / (double) windup);
			}
			if (Ticks.ambient()) {
				Fx.particleExcept(level, firstPerson(), Fx.dust(ORANGE, 1.0F),
						caster.getX(), caster.getY() + 1.6, caster.getZ(), 3, 0.3, 0.2, 0.3, 0);
			}
		} else if (t == windup) {
			impact(level);
		}
		if (t >= Ticks.of(END)) {
			cleanup();
			return false;
		}
		return true;
	}

	private void impact(ServerLevel level) {
		float yaw = Aim.facingYawPitch(caster)[0];
		if (range != null) {
			range.discard();
			range = null;
		}
		GroundShape.flash(level, caster.position(), yaw, ARC, RADIUS, 0x70FF8030, 0xFFFFC070, 7);
		Attachments.combatant(caster).casting = false;
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.9F);
		Fx.sound(caster, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.8F);
		Vec3 at = caster.position();
		Fx.particleExcept(level, firstPerson(), ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0);
		Fx.particleExcept(level, firstPerson(), Fx.dust(ORANGE, 1.4F), at.x, at.y + 0.4, at.z, 30, 1.2, 0.3, 1.2, 0);

		int damage = DAMAGE_10 + PER_STACK_10 * Math.min(Fervor.MAX, state.fervor);
		boolean any = false;
		for (LivingEntity e : Targets.enemies(level, at, RADIUS, caster)) {
			if (!Targets.inCone(at, yaw, ARC / 2.0, RADIUS, e)) {
				continue;
			}
			any = true;
			SkillDamage.deal(e, caster, damage, SkillDamage.Kind.NORMAL);
			if (e.isAlive()) {
				CrowdControl.stun(e, STUN);
			}
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
		}
		if (any) {
			Fervor.gain(caster, state, HIT_FERVOR);
			Attachments.profile(caster).msgT = 30;
			Hud.actionbar(caster, Component.empty().append(Hud.bold("강타 명중!  ", ChatFormatting.GOLD))
					.append(Hud.text(damage / 10 + " 피해", ChatFormatting.WHITE)));
		}
	}

	/** 모드 클라이언트는 1인칭 동작이 대신하므로, 몸에 붙는 입자는 시전자 화면에서 뺍니다. */
	private @Nullable ServerPlayer firstPerson() {
		return SkillAnimPayload.canSend(caster) ? caster : null;
	}

	private void interrupt() {
		SkillAnimPayload.stop(caster, SkillAnimPayload.BR_BLOW);
		cleanup();
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(caster).msgT = 30;
		Hud.actionbar(caster, Hud.text("강타가 끊겼다", ChatFormatting.GRAY));
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
		if (range != null) {
			range.discard();
			range = null;
		}
		Attachments.combatant(caster).casting = false;
		if (state.blow == this) {
			state.blow = null;
		}
	}
}
