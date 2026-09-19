package kr.overbreak.classes.hammer;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [우클릭] 지면 분쇄 — 데이터팩 skill/smash/* 대응.
 *
 *   0.5초(10틱) 정신집중 (망치를 머리 위로 들어 올림, 기절 · 넘어뜨림이면 끊김)
 *   발동: 조준 방향 120도 · 반경 3칸 → 피해 40 + 1.2초 기절 (넉백 없음)
 *   돌진 충격 중에 누르면 예약 → 돌진이 끝나는 자리에서 곧바로 360도로 내려찍음
 *   쿨타임 9초 (누른 순간부터)
 *   판정 범위: 바닥에 반투명 부채꼴 모델 (정신집중 동안 조준을 따라 돌고 채워짐)
 */
final class Smash implements Effects.Active {
	static final int CHANNEL = 10;
	static final double RADIUS = 4.0;
	static final double ARC = 120.0;
	static final int DAMAGE = 400;
	static final int STUN = 24;
	static final int COOLDOWN = 180;
	static final int FILL = 0x40FFC83C;
	static final int EDGE = 0xD8FFD24A;

	private final ServerPlayer caster;
	private final HammerState state;
	private final GroundShape shape;
	private int t;

	private Smash(ServerPlayer caster, HammerState state, GroundShape shape) {
		this.caster = caster;
		this.state = state;
		this.shape = shape;
	}

	static void cast(ServerPlayer p, HammerState st) {
		if (Cooldowns.blocked(p, HammerKnight.SMASH, "지면 분쇄", ChatFormatting.YELLOW)) {
			return;
		}
		Attachments.profile(p).setCooldown(HammerKnight.SMASH, COOLDOWN);
		if (st.charge != null) {
			st.smashQueued = true;
			Fx.sound(p, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Component.empty()
					.append(Hud.bold("지면 분쇄", ChatFormatting.YELLOW))
					.append(Hud.text("  돌진이 끝나면 내려찍습니다", ChatFormatting.GOLD)));
			return;
		}
		ServerLevel level = p.level();
		GroundShape shape = GroundShape.sector(level, p.position(), Aim.facingYawPitch(p)[0], ARC, RADIUS, FILL, EDGE);
		shape.reveal(0.0);
		Smash s = new Smash(p, st, shape);
		st.smash = s;
		Attachments.combatant(p).casting = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.HK_SMASH, -1);
		Fx.sound(p, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.1F, 0.6F);
		Fx.sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.9F, 0.6F);
		Effects.add(s);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		if (Attachments.combatant(caster).interrupted()) {
			interrupt();
			return false;
		}
		t++;
		float yaw = Aim.facingYawPitch(caster)[0];
		shape.moveTo(caster.position());
		shape.setYaw(yaw);
		shape.reveal(t / (double) Ticks.of(CHANNEL));
		if (t >= Ticks.of(CHANNEL)) {
			Attachments.combatant(caster).casting = false;
			state.smash = null;
			shape.setColors(0x70FFD24A, 0xFFFFE680);
			GroundShape.fadeOut(shape, 6, null);
			fire(caster, caster.position(), yaw, ARC);
			return false;
		}
		return true;
	}

	/** 돌진이 끝난 자리에서 곧바로 360도로 내려찍습니다 (예약 분쇄). */
	static void fireRound(ServerPlayer p) {
		SkillAnimPayload.broadcast(p, SkillAnimPayload.HK_SLAM, -1);
		GroundShape.flash(p.level(), p.position(), 0.0F, 360.0, RADIUS, 0x60FFD24A, 0xFFFFE680, 6);
		fire(p, p.position(), 0.0F, 360.0);
	}

	private static void fire(ServerPlayer p, Vec3 origin, float yaw, double arc) {
		ServerLevel level = p.level();
		Fx.sound(p, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.9F);
		Fx.sound(p, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.sound(p, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.9F, 0.7F);
		Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				origin.x, origin.y + 0.1, origin.z, 40, 1.2, 0.1, 1.2, 0.3);
		for (LivingEntity e : Targets.enemies(level, origin, RADIUS, p)) {
			if (arc >= 360.0 || Targets.inCone(origin, yaw, arc / 2.0, RADIUS, e)) {
				hit(p, e);
			}
		}
	}

	private static void hit(ServerPlayer p, LivingEntity e) {
		SkillDamage.deal(e, p, DAMAGE, SkillDamage.Kind.NO_KB);
		if (e.isAlive()) {
			CrowdControl.stun(e, STUN);
		}
	}

	private void interrupt() {
		cleanup();
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(caster).msgT = 30;
		Hud.actionbar(caster, Hud.text("지면 분쇄 시전이 끊겼다", ChatFormatting.GRAY));
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void cleanup() {
		Attachments.combatant(caster).casting = false;
		SkillAnimPayload.stop(caster, SkillAnimPayload.HK_SMASH);
		shape.discard();
		if (state.smash == this) {
			state.smash = null;
		}
	}
}
