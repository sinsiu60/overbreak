package kr.overbreak.classes.sheriff;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.DeadeyePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [Q] 황야의 무법자 — 데이터팩 ult/deadeye/* 대응 (체력 10배 기준).
 *
 *   2초(40틱) 동안 앞 90도 · 20칸 안의 적을 조준 (벽에 가리면 안 됨). 한 번 조준된 적은 시야를 벗어나도 풀리지 않음
 *   2초가 끝나면 조준한 적 전원에게 120 + 강한 넉백 (조준 중에는 이 궁극기가 적을 밀지 않음)
 *   조준 중: 평타 · 다른 스킬 잠김, 이동은 자유. 기절하거나 섬광에 맞으면 취소 (게이지는 돌려받지 못함)
 *   화면(모드 클라이언트): 조준한 적 머리 위 표식이 차오르고 가장자리가 어두워짐 — {@link DeadeyePayload}
 */
final class Deadeye implements Effects.Active {
	static final int AIM = 40;
	static final double RANGE = 20.0;
	static final double HALF_ARC = 45.0;
	static final int DAMAGE_100 = 12000;
	static final double KNOCK = 0.9;
	private static final int GOLD = Fx.rgb(1.00, 0.80, 0.20);

	private final ServerPlayer caster;
	private final SheriffState state;
	private final Set<LivingEntity> marked = new LinkedHashSet<>();
	private int t;

	private Deadeye(ServerPlayer caster, SheriffState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, SheriffState st) {
		if (st.deadeye != null) {
			return;
		}
		UltGauge.consume(p);
		Peacekeeper.interrupt(p, st);
		if (st.fan != null) {
			st.fan.cancel();
		}
		Deadeye d = new Deadeye(p, st);
		st.deadeye = d;
		Attachments.combatant(p).casting = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SH_DEADEYE, -1, AIM);
		Fx.sound(p, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
		Fx.sound(p, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
		// 모드 클라이언트는 조준 화면(가장자리 어둠 · 표식)이 대신하므로 화면 가운데 제목을 띄우지 않습니다
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("황야의 무법자", ChatFormatting.GOLD), Component.empty(), 0, 30, 10);
		}
		d.send();
		Effects.add(d);
	}

	/** 남은 시간 0~100 (HUD). */
	int remainingPercent() {
		int aim = Ticks.of(AIM);
		return Math.max(0, (aim - t) * 100 / aim);
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
		scan();
		ServerLevel level = caster.level();
		if (Ticks.ambient()) {
			for (LivingEntity e : marked) {
				Fx.particle(level, Fx.dust(GOLD, 1.3F), e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 4, 0.12, 0.08, 0.12, 0);
			}
		}
		send();
		if (t >= Ticks.of(AIM)) {
			fire();
			return false;
		}
		return true;
	}

	/** 아직 표식이 없는 적 중 앞 90도 · 20칸 · 시야가 트인 적을 조준합니다. */
	private void scan() {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		float yaw = Aim.facingYawPitch(caster)[0];
		for (LivingEntity e : Targets.enemies(level, caster.position(), RANGE, caster)) {
			if (marked.contains(e) || !Targets.inCone(caster.position(), yaw, HALF_ARC, RANGE, e)) {
				continue;
			}
			if (level.clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			marked.add(e);
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 12, 0.15, 0.1, 0.15, 0);
			Fx.sound(e, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1.0F, 1.9F);
		}
	}

	private void fire() {
		ServerLevel level = caster.level();
		for (LivingEntity e : marked) {
			if (!e.isAlive() || e.isRemoved()) {
				continue;
			}
			SkillDamage.dealFine(e, caster, DAMAGE_100, SkillDamage.Kind.NO_KB);
			Peacekeeper.knockAway(caster, e, KNOCK);
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 40, 0.4, 0.6, 0.4, 0.5);
			Fx.particle(level, ColorParticleOption.create(ParticleTypes.FLASH, 0x4DFFFFD9), e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
			Fx.sound(e, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.4F, 0.7F);
		}
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.SH_DEADEYE_FIRE, -1);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 1.4F);
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 1.6F);
		if (!SkillAnimPayload.canSend(caster)) {
			Hud.title(caster, Hud.bold("탕.", ChatFormatting.GOLD), Component.empty(), 0, 15, 8);
		}
		cleanup();
	}

	private void interrupt() {
		cleanup();
		SkillAnimPayload.stop(caster, SkillAnimPayload.SH_DEADEYE);
		ServerLevel level = caster.level();
		Fx.particle(level, ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1, caster.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.4F);
		Attachments.profile(caster).msgT = 40;
		Hud.actionbar(caster, Component.empty()
				.append(Hud.bold("황야의 무법자", ChatFormatting.GOLD))
				.append(Hud.text("  조준이 끊겼다", ChatFormatting.GRAY)));
	}

	/** 본인 화면에 조준 상태 (남은 틱 · 조준한 적). 끝나면 빈 목록. */
	private void send() {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : marked) {
			if (e.isAlive() && !e.isRemoved()) {
				ids.add(e.getId());
			}
		}
		// 클라이언트에는 시간 단위로
		DeadeyePayload.send(caster, new DeadeyePayload(Ticks.toTime(t), AIM, DAMAGE_100 / 100, ids));
	}

	private void cleanup() {
		if (state.deadeye == this) {
			state.deadeye = null;
		}
		Attachments.combatant(caster).casting = false;
		marked.clear();
		if (!caster.hasDisconnected()) {
			DeadeyePayload.send(caster, DeadeyePayload.NONE);
		}
	}

	@Override
	public void cancel() {
		if (t < Ticks.of(AIM) && !caster.hasDisconnected()) {
			SkillAnimPayload.stop(caster, SkillAnimPayload.SH_DEADEYE);
		}
		cleanup();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
