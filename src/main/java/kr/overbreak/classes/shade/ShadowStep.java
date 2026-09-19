package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [F 다시] 그림자 걸음 — 표창이 꽂힌 적의 등 뒤로 0.2초(4틱) 동안 빠르게 날아감.
 *
 *   순간이동이면 내가 어디로 갔는지 알기 어려워, 매 틱 남은 거리를 남은 틱으로 나눈 속도로 곧게 날아가게 합니다
 *     (높이 유지 · 벽 · 높낮이 차이는 도착 순간 등 뒤 자리로 맞춤). 날아가는 동안 잔상이 남음 (클라이언트 Afterimages)
 *   도착하면 대상을 바라보고 순간 베기 동작 (화면: 파란 섬광 · 시야 튕김 — 클라이언트 ShadeScreen)
 *   대상이 죽거나 사라지면 그 자리에서 멈춤
 */
final class ShadowStep implements Effects.Active {
	static final int TICKS = 4;

	private final ServerPlayer caster;
	private final ShadeState state;
	private final LivingEntity target;
	private int t;

	private ShadowStep(ServerPlayer caster, ShadeState state, LivingEntity target) {
		this.caster = caster;
		this.state = state;
		this.target = target;
	}

	static void cast(ServerPlayer p, ShadeState st, LivingEntity target) {
		if (st.step != null) {
			st.step.cancel();
		}
		ShadowStep s = new ShadowStep(p, st, target);
		st.step = s;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SD_STEP, -1, TICKS);
		Fx.particleExcept(p.level(), p, ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		Fx.particleExcept(p.level(), p, ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
		Fx.sound(p, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 2.0F);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.6F);
		s.steer();
		Effects.add(s);
	}

	/** 남은 거리 / 남은 틱 으로 이번 틱 속도를 실음. */
	private void steer() {
		Vec3 dest = Shade.behindPoint(caster, target, 0.0);
		Vec3 d = dest.subtract(caster.position());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		int left = Math.max(1, Ticks.of(TICKS) - t);
		if (flat < 1.0E-3) {
			return;
		}
		// 남은 틱에 나눠 도착 — 돌진 속도는 시간 단위당이라 틱당 거리를 시간으로 바꿔 넘김
		Motion.dash(caster, d.x, d.z, flat / Ticks.time(left), 1, true);
		CrowdControl.track(caster);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end();
			return false;
		}
		if (!target.isAlive() || target.isRemoved() || target.level() != caster.level()) {
			Motion.brake(caster);
			end();
			return false;
		}
		t++;
		if (t >= Ticks.of(TICKS)) {
			arrive();
			return false;
		}
		steer();
		return true;
	}

	private void arrive() {
		end();
		Shade.blinkBehind(caster, target, 0.0);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.SD_STRIKE, -1);
		Fx.sound(caster, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.5F);
		Fx.sound(caster, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.8F);
	}

	private void end() {
		if (state.step == this) {
			state.step = null;
		}
	}

	@Override
	public void cancel() {
		end();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
