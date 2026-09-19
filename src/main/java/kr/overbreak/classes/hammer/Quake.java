package kr.overbreak.classes.hammer;

import kr.overbreak.core.tick.Ticks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [Q] 대지 진동파 — 데이터팩 ult/hammer/* 대응 (범위 · 효과 변경).
 *
 *   정신집중 0.8초(16틱): 망치를 뒤로 빼 힘을 모음 (이동 불가, 기절 · 넘어뜨림이면 끊김)
 *     바닥 표시: 조준 방향 70도 · 20칸 반투명 부채꼴 (조준을 따라 돌며 채워짐)
 *   발동: 매우 강하게 내려치고, 그 순간의 위치 · 방향으로 파면이 틱당 1.5칸씩 20칸까지 퍼짐
 *     파면이 닿는 순서대로 (대상당 1회): 피해 100(바로 앞) → 50(20칸) 선형 + 2초 넘어뜨림 + 뇌진탕 즉시 연계
 *     바닥 표시: 부채꼴 윤곽 + 앞으로 나아가는 밝은 파면 호
 *   데이터팩과 달라진 점: 180도 · 7칸 → 70도 · 20칸, 견인 삭제, 기절 → 넘어뜨림
 */
final class Quake implements Effects.Active {
	static final int CHANNEL = 16;
	static final double RANGE = 20.0;
	static final double ARC = 70.0;
	static final double SPEED = 1.5;
	static final int KNOCKDOWN = 40;
	static final int DAMAGE_NEAR = 1000;
	static final int DAMAGE_FAR = 500;
	private static final Identifier ROOT = Overbreak.id("hk_ult_root");

	private final ServerPlayer caster;
	private final HammerState state;
	private final GroundShape preview;
	private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
	private @Nullable GroundShape front;
	private boolean waving;
	private int t;
	private double radius;
	private Vec3 origin;
	private float yaw;

	private Quake(ServerPlayer caster, HammerState state, GroundShape preview) {
		this.caster = caster;
		this.state = state;
		this.preview = preview;
		this.origin = caster.position();
		this.yaw = Aim.facingYawPitch(caster)[0];
	}

	static void cast(ServerPlayer p, HammerState st) {
		UltGauge.consume(p);
		ServerLevel level = p.level();
		GroundShape preview = GroundShape.sector(level, p.position(), Aim.facingYawPitch(p)[0], ARC, RANGE, 0x30FFC83C, 0xD0FFD24A);
		preview.reveal(0.0);
		Quake q = new Quake(p, st, preview);
		st.quake = q;
		Attachments.combatant(p).casting = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		CrowdControl.mod(p, Attributes.JUMP_STRENGTH, ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.HK_ULT, -1);
		Fx.sound(p, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.1F, 0.5F);
		Attachments.profile(p).msgT = 40;
		Hud.actionbar(p, Component.empty()
				.append(Hud.bold("대지 진동파", ChatFormatting.GOLD))
				.append(Hud.text("  힘을 모으는 중...", ChatFormatting.YELLOW)));
		Effects.add(q);
	}

	@Override
	public boolean tick() {
		if (!waving) {
			return channel();
		}
		return wave();
	}

	private boolean channel() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		if (Attachments.combatant(caster).interrupted()) {
			cleanup();
			Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 1.0F, 0.4F);
			Attachments.profile(caster).msgT = 40;
			Hud.actionbar(caster, Hud.text("대지 진동파 시전이 끊겼다", ChatFormatting.GRAY));
			return false;
		}
		t++;
		yaw = Aim.facingYawPitch(caster)[0];
		origin = caster.position();
		preview.moveTo(origin);
		preview.setYaw(yaw);
		preview.reveal(t / (double) Ticks.of(CHANNEL));
		if (t >= Ticks.of(CHANNEL)) {
			fire();
		}
		return true;
	}

	private void fire() {
		unlock();
		waving = true;
		radius = 0.0;
		preview.setColors(0x18FFC83C, 0x90FFD24A);
		ServerLevel level = caster.level();
		front = GroundShape.arc(level, origin, yaw, ARC, 0.6, 0xF0FFFFE0, 0.45);
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6F, 0.5F);
		Fx.sound(caster, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.4F, 0.6F);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				origin.x, origin.y + 0.1, origin.z, 60, 1.0, 0.1, 1.0, 0.4);
		Hud.title(caster, Hud.bold("대지 진동파", ChatFormatting.GOLD), Component.empty(), 0, 25, 10);
	}

	private boolean wave() {
		radius = Math.min(RANGE, radius + Ticks.speed(SPEED));
		ServerLevel level = caster.level();
		if (front != null) {
			front.setRadius(Math.max(0.6, radius));
		}
		for (LivingEntity e : Targets.within(level, origin, radius, e -> e != caster)) {
			if (!hit.contains(e) && Targets.inCone(origin, yaw, ARC / 2.0, radius, e)) {
				hit.add(e);
				strike(e);
			}
		}
		if (radius >= RANGE) {
			if (front != null) {
				GroundShape.fadeOut(front, 5, null);
				front = null;
			}
			GroundShape.fadeOut(preview, 8, null);
			if (state.quake == this) {
				state.quake = null;
			}
			return false;
		}
		return true;
	}

	private void strike(LivingEntity e) {
		double dist = Math.sqrt(Math.pow(e.getX() - origin.x, 2) + Math.pow(e.getZ() - origin.z, 2));
		double k = Math.min(1.0, dist / RANGE);
		int damage = (int) Math.round(DAMAGE_NEAR - (DAMAGE_NEAR - DAMAGE_FAR) * k);
		ServerPlayer src = caster.isAlive() ? caster : null;
		SkillDamage.deal(e, src, damage, SkillDamage.Kind.NO_KB);
		if (e.isAlive()) {
			CrowdControl.knockdown(e, KNOCKDOWN);
			Concussion.apply(src, e);
		}
		Fx.sound(e, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.6F);
		if (e.level() instanceof ServerLevel level) {
			Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
					e.getX(), e.getY() + 0.2, e.getZ(), 20, 0.4, 0.1, 0.4, 0.3);
		}
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void unlock() {
		Attachments.combatant(caster).casting = false;
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, ROOT);
		CrowdControl.unmod(caster, Attributes.JUMP_STRENGTH, ROOT);
	}

	private void cleanup() {
		if (!waving) {
			unlock();
			SkillAnimPayload.stop(caster, SkillAnimPayload.HK_ULT);
		}
		preview.discard();
		if (front != null) {
			front.discard();
		}
		if (state.quake == this) {
			state.quake = null;
		}
	}
}
