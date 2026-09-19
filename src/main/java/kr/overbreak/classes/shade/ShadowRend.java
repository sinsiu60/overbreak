package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [우클릭] 그림자 가르기.
 *
 *   바라보는 수평 방향으로 0.3초(6틱) 동안 틱당 1칸 = 6칸 돌진 (공중에서 써도 높이 유지). 적을 뚫고 지나갑니다
 *   지나가며 몸에서 1.5칸 안의 적마다 30 피해 (넉백 없음) + 그림자 낙인. 한 적은 한 번만
 *   벽에 막히면 그 자리에서 멈춤. 이동기라 균열 지대 · 섬광 봉인 중에는 못 씀
 *   쿨타임 8초 — 적을 처치하면 즉시 초기화
 */
final class ShadowRend implements Effects.Active {
	static final int COOLDOWN = 160;
	static final int TICKS = 6;
	/** 0.3초 동안 7.5칸 (0.1 버전). */
	static final double SPEED = 1.25;
	static final double REACH = 1.75;
	static final int DAMAGE_10 = 300;

	private final ServerPlayer caster;
	private final ShadeState state;
	private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
	private int t;

	private ShadowRend(ServerPlayer caster, ShadeState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, ShadeState st) {
		if (st.rend != null || st.storm != null) {
			return;
		}
		if (Shade.sealed(p) || Cooldowns.blocked(p, Shade.REND, "그림자 가르기", ChatFormatting.DARK_PURPLE)) {
			return;
		}
		Attachments.profile(p).setCooldown(Shade.REND, COOLDOWN);
		Vec3 dir = Motion.flatLook(p);
		Motion.dash(p, dir.x, dir.z, SPEED, TICKS, true);
		CrowdControl.track(p);
		ShadowRend r = new ShadowRend(p, st);
		st.rend = r;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SD_REND, -1);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.sound(p, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6F, 1.8F);
		r.slash();
		Effects.add(r);
	}

	/** 지금 자리에서 닿는 적을 벱니다. */
	private void slash() {
		ServerLevel level = caster.level();
		// 시전자 몸에서 나오는 입자는 1인칭 화면을 가리지 않게 남에게만
		if (t == 0 || Ticks.ambient()) {
			Fx.particleExcept(level, caster, Fx.dust(ShadowMark.PURPLE, 1.6F), caster.getX(), caster.getY() + 1.0, caster.getZ(), 10, 0.3, 0.5, 0.3, 0);
			Fx.particleExcept(level, caster, ParticleTypes.SMOKE, caster.getX(), caster.getY() + 0.8, caster.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
		}
		for (LivingEntity e : Targets.within(level, caster.position(), REACH, e -> e != caster)) {
			if (!hit.add(e)) {
				continue;
			}
			// 직전 평타가 만든 무적 시간에 씹히지 않게 관통 (0.1f 수정)
			SkillDamage.deal(e, caster, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
			ShadowMark.apply(state, e);
			Fx.particle(level, ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1.0, e.getZ(), 1, 0, 0, 0, 0);
			Fx.sound(e, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.4F);
		}
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end(false);
			return false;
		}
		t++;
		slash();
		if (t >= Ticks.of(TICKS) || (t > Ticks.of(1) && caster.horizontalCollision)) {
			end(true);
			return false;
		}
		return true;
	}

	private void end(boolean brake) {
		if (state.rend == this) {
			state.rend = null;
		}
		if (brake) {
			Motion.brake(caster);
		}
	}

	@Override
	public void cancel() {
		end(false);
	}

	@Override
	public Object owner() {
		return caster;
	}
}
