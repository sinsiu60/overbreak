package kr.overbreak.classes.brute;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [웅크리기] 돌개바람 — 대검을 크게 휘돌리며 주위를 계속 벱니다.
 *
 *   {@value #DURATION} (1/20초 단위 = 1초) 동안 {@value #TICK_EVERY} (0.2초) 마다 주위 {@value #RADIUS}칸에 15 피해
 *   도는 동안 이동속도 +30% · 받는 피해 -30% · 넉백 면역 (저지불가) — 휘돌며 파고듭니다
 *   벨 때마다 투기 1스택 — 여럿에 둘러싸일수록 빨리 단단해집니다
 *   쿨타임 8초 (시작할 때부터)
 */
final class Whirl implements Effects.Active {
	static final int COOLDOWN = 160;
	static final int DURATION = 20;
	static final int TICK_EVERY = 4;
	static final double RADIUS = 3.5;
	/** 한 번 벨 때 (10배 기준). */
	static final int DAMAGE_10 = 150;
	/** 도는 동안 이동속도 증가 (0.1e 에서 감소 → 증가로). */
	static final double SPEED = 0.3;
	static final int GUARD_PERCENT = 30;

	private static final Identifier SPEED_ID = Overbreak.id("brute_whirl_speed");
	private static final int ORANGE = Fx.rgb(1.00, 0.55, 0.20);

	private final ServerPlayer caster;
	private final BruteState state;
	private int t;
	private double spin;
	private @Nullable GroundShape ring;

	private Whirl(ServerPlayer caster, BruteState state) {
		this.caster = caster;
		this.state = state;
	}

	/** 도는 동안 받는 피해가 줄어드는가 (Brute 가 DamageModifier 에서 봅니다). */
	static boolean guarding(BruteState st) {
		return st.whirl != null;
	}

	static void cast(ServerPlayer p, BruteState st) {
		Attachments.profile(p).setCooldown(Brute.WHIRL, COOLDOWN);
		Whirl w = new Whirl(p, st);
		st.whirl = w;
		Combatant c = Attachments.combatant(p);
		c.ccImmune = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.BR_WHIRL, -1);
		ServerLevel level = (ServerLevel) p.level();
		w.ring = GroundShape.sector(level, p.position(), 0.0F, 360.0, RADIUS, 0x30FF8030, 0xC0FFA860);
		w.ring.reveal(1.0);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.sound(p, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.9F, 0.8F);
		Attachments.profile(p).msgT = 25;
		Hud.actionbar(p, Component.empty().append(Hud.bold("돌개바람", ChatFormatting.GOLD))
				.append(Hud.text("  이동속도 +30% · 받는 피해 -30%", ChatFormatting.GRAY)));
		Effects.add(w);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		t++;
		ServerLevel level = (ServerLevel) caster.level();
		spin = (spin + Ticks.speed(90.0)) % 360;
		if (ring != null) {
			ring.moveTo(caster.position());
		}
		if (Ticks.ambient()) {
			Vec3 edge = Local.flat(caster.position(), (float) spin, 0, 1.0, RADIUS * 0.8);
			Fx.particle(level, Fx.dust(ORANGE, 1.2F), edge.x, edge.y, edge.z, 4, 0.15, 0.2, 0.15, 0);
			Fx.particle(level, ParticleTypes.SWEEP_ATTACK, edge.x, edge.y - 0.4, edge.z, 1, 0.1, 0.1, 0.1, 0);
		}
		if (t % Ticks.of(TICK_EVERY) == 0) {
			sweep(level);
		}
		if (t >= Ticks.of(DURATION)) {
			cleanup();
			return false;
		}
		return true;
	}

	private void sweep(ServerLevel level) {
		Fx.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.1F);
		boolean any = false;
		for (LivingEntity e : Targets.enemies(level, caster.position(), RADIUS, caster)) {
			SkillDamage.deal(e, caster, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 5, 0.2, 0.3, 0.2, 0.1);
			any = true;
		}
		if (any) {
			Fervor.gain(caster, state, 1);
		}
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
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SPEED_ID);
		Attachments.combatant(caster).ccImmune = false;
		if (ring != null) {
			ring.discard();
			ring = null;
		}
		if (state.whirl == this) {
			state.whirl = null;
		}
	}
}
