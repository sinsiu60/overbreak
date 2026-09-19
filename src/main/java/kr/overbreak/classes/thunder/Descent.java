package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import java.util.List;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [Q] 뇌신강림 — 하늘로 떠올라 주위 모든 적에게 벼락을 세 번 내리꽂음.
 *
 *   0.4초 동안 약 3칸 솟구친 뒤 그 자리에 떠 있음 (움직일 수 없음 · 군중 제어 면역 · 피해는 받음 · 뇌격은 쏠 수 있음)
 *   1 · 2 · 3초에 18칸 안 · 시야가 트인 적마다 벼락: 35 피해 + 정전기 1스택 (세 번째에 감전)
 *   3.2초에 끝나고 떨어짐 (낙하 피해 없음)
 */
final class Descent implements Effects.Active {
	static final int RISE = 8;
	static final int WAVE_GAP = 20;
	static final int WAVES = 3;
	static final int LENGTH = WAVE_GAP * WAVES + 4;
	static final double RANGE = 18.0;
	static final int DAMAGE_10 = 350;

	private final ServerPlayer caster;
	private final ThunderState state;
	private int t;

	private Descent(ServerPlayer caster, ThunderState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, ThunderState st) {
		if (st.descent != null) {
			return;
		}
		UltGauge.consume(p);
		if (st.step != null) {
			st.step.cancel();
		}
		Descent d = new Descent(p, st);
		st.descent = d;
		Attachments.combatant(p).ccImmune = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.TH_ULT, -1, LENGTH);
		ServerLevel level = p.level();
		Fx.sound(p, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5F, 0.7F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
		Fx.particleExcept(level, p, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 80, 1.0, 0.2, 1.0, 0.6);
		Fx.ring(level, p.position(), 2.5, 32, 0.1, Fx.dust(StaticCharge.CYAN, 1.6F));
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("뇌신강림", ChatFormatting.AQUA), Component.empty(), 0, 30, 10);
		}
		Effects.add(d);
	}

	/** 남은 시간 0~100 (HUD). */
	int remainingPercent() {
		int length = Ticks.of(LENGTH);
		return Math.max(0, (length - t) * 100 / length);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end();
			return false;
		}
		t++;
		ServerLevel level = caster.level();
		int rise = Ticks.of(RISE);
		double vy = t <= rise ? Ticks.speed(0.7 * (1.0 - (t - 1) / (double) rise)) : 0.0;
		caster.setDeltaMovement(0.0, vy, 0.0);
		caster.hurtMarked = true;
		caster.resetFallDistance();
		// 몸에서 튀는 불꽃은 1인칭 화면을 가리지 않게 남에게만
		if (Ticks.ambient()) {
			Fx.particleExcept(level, caster, ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY() + 1.0, caster.getZ(), 6, 0.4, 0.8, 0.4, 0.15);
			Fx.particleExcept(level, caster, Fx.dust(StaticCharge.CYAN, 1.2F), caster.getX(), caster.getY() + 0.1, caster.getZ(), 4, 0.35, 0.05, 0.35, 0);
		}
		int gap = Ticks.of(WAVE_GAP);
		if (t % gap == 0 && t <= gap * WAVES) {
			wave(level, t / gap);
		}
		if (t >= Ticks.of(LENGTH)) {
			end();
			return false;
		}
		return true;
	}

	private void wave(ServerLevel level, int n) {
		Vec3 eye = caster.getEyePosition();
		List<LivingEntity> list = Targets.enemies(level, caster.position(), RANGE, caster);
		for (LivingEntity e : list) {
			if (level.clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
					.getType() != HitResult.Type.MISS) {
				continue;
			}
			Thunderbolt.strike(level, e.position(), caster, state, DAMAGE_10, 1.2, 0);
		}
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.TH_CAST, -1);
		Fx.sound(caster, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.2F, 0.8F + 0.15F * n);
	}

	private void end() {
		if (state.descent == this) {
			state.descent = null;
		}
		state.fallSafeT = Ticks.of(60);
		Combatant c = Attachments.combatant(caster);
		c.ccImmune = false;
	}

	@Override
	public void cancel() {
		if (t < Ticks.of(LENGTH) && !caster.hasDisconnected()) {
			SkillAnimPayload.stop(caster, SkillAnimPayload.TH_ULT);
		}
		end();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
