package kr.overbreak.classes.hammer;

import kr.overbreak.core.tick.Ticks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * [웅크리기] 돌진 충격 — 데이터팩 skill/charge/* 대응.
 *
 *   기 모으기: 0.3초(6틱) 제자리에서 방패를 앞으로 세움 (이동 불가)
 *   돌진: 그 순간의 조준 방향(수평)으로 틱당 0.55칸 x 8틱 — 매 틱 속도 방식이라 부드러움
 *   경로 1.6칸 안의 적 (대상당 1회): 피해 20 + 1.5초 50% 둔화, 뇌진탕 발동 가능
 *   앞이 발밑 · 머리 높이 둘 다 막히면 즉시 멈춤. 끝나면 제동
 *   예약해 둔 지면 분쇄가 있으면 끝난 자리에서 곧바로 360도 내려찍기 (기절 · 넘어뜨림이면 취소)
 *   균열 지대 위(봉인)에서는 쓸 수 없음 — 쿨타임을 먹기 전에 막습니다
 *   쿨타임 7초 · 기절 · 넘어뜨림 · 에어본에 걸리면 그 자리에서 끝남
 */
final class Charge implements Effects.Active {
	static final int WINDUP = 6;
	static final int DASH = 11;
	// 7.6칸 / 0.55초
	static final double SPEED = 0.69;
	static final double HIT_RADIUS = 1.6;
	static final int DAMAGE = 200;
	static final int COOLDOWN = 140;
	private static final Identifier ROOT = Overbreak.id("hk_charge_root");
	private static final int GOLD = Fx.rgb(1.00, 0.88, 0.15);

	private final ServerPlayer caster;
	private final HammerState state;
	private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean dashing;
	private int t;
	private double dx;
	private double dz;

	private Charge(ServerPlayer caster, HammerState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, HammerState st) {
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (st.charge != null || Cooldowns.blocked(p, HammerKnight.CHARGE, "돌진 충격", ChatFormatting.YELLOW)) {
			return;
		}
		Attachments.profile(p).setCooldown(HammerKnight.CHARGE, COOLDOWN);
		Charge c = new Charge(p, st);
		st.charge = c;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		CrowdControl.mod(p, Attributes.JUMP_STRENGTH, ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.HK_CHARGE, -1);
		Fx.sound(p, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.8F);
		Fx.sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		Effects.add(c);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		Combatant cb = Attachments.combatant(caster);
		if (cb.hardCc()) {
			end();
			return false;
		}
		t++;
		ServerLevel level = caster.level();
		if (!dashing) {
			if (t >= Ticks.of(WINDUP)) {
				startDash();
			}
			return true;
		}
		for (LivingEntity e : Targets.enemies(level, caster.position(), HIT_RADIUS, caster)) {
			if (hit.add(e)) {
				strike(e);
			}
		}
		if (Ticks.ambient()) {
			Fx.particle(level, ParticleTypes.CRIT, caster.getX(), caster.getY() + 0.5, caster.getZ(), 4, 0.3, 0.4, 0.3, 0.1);
		}
		if (t >= Ticks.of(DASH) || wallAhead(level)) {
			end();
			return false;
		}
		return true;
	}

	private void startDash() {
		unroot();
		Vec3 aim = Aim.facing(caster);
		Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = Motion.flatLook(caster);
		}
		flat = flat.normalize();
		dx = flat.x;
		dz = flat.z;
		Motion.dash(caster, dx, dz, SPEED, DASH, false);
		CrowdControl.track(caster);
		dashing = true;
		t = 0;
		Fx.sound(caster, SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		Fx.sound(caster, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** 발밑과 머리 높이가 둘 다 막혔을 때만 벽 (계단 · 반 블록은 물리가 넘어감). */
	private boolean wallAhead(ServerLevel level) {
		Vec3 ahead = caster.position().add(dx * 0.8, 0.0, dz * 0.8);
		BlockPos feet = BlockPos.containing(ahead.x, caster.getY() + 0.1, ahead.z);
		BlockPos head = feet.above();
		return !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
	}

	private void strike(LivingEntity e) {
		SkillDamage.deal(e, caster, DAMAGE, SkillDamage.Kind.NORMAL);
		if (e.isAlive()) {
			CrowdControl.slow(e, 0.5, 30);
			Concussion.apply(caster, e);
		}
		Fx.sound(e, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.8F);
		if (e.level() instanceof ServerLevel level) {
			Fx.particle(level, Fx.dust(GOLD, 1.3F), e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.4, 0.3, 0);
		}
	}

	/** 끝: 제동 · 예약 분쇄. */
	private void end() {
		boolean queued = state.smashQueued;
		cleanup();
		Fx.sound(caster, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.3F);
		if (!queued) {
			return;
		}
		if (Attachments.combatant(caster).hardCc()) {
			Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			return;
		}
		Smash.fireRound(caster);
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void unroot() {
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, ROOT);
		CrowdControl.unmod(caster, Attributes.JUMP_STRENGTH, ROOT);
	}

	private void cleanup() {
		unroot();
		if (dashing) {
			Motion.brake(caster);
		}
		SkillAnimPayload.stop(caster, SkillAnimPayload.HK_CHARGE);
		state.smashQueued = false;
		if (state.charge == this) {
			state.charge = null;
		}
	}
}
