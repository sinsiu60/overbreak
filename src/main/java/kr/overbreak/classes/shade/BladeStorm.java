package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import kr.overbreak.combat.SkillDamage;
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
 * [Q] 천검난무.
 *
 *   반경 10칸 안에서 시야가 트인 적 최대 4명을 가까운 순으로 고름 (아무도 없으면 쓰지 않고 게이지도 그대로)
 *   0.2초(4틱)마다 한 번씩 모두 6번 — 고른 적을 번갈아 돌며 등 뒤 · 옆으로 순간이동해 25 피해 (넉백 없음) + 그림자 낙인
 *     혼자면 6번 모두 한 명에게 (150), 네 명이면 2 · 2 · 1 · 1번
 *   베는 1.2초 동안 피해 · 군중 제어를 받지 않고 다른 행동은 잠김. 끝나면 0.25초 더 피해를 받지 않음
 */
final class BladeStorm implements Effects.Active {
	static final double RANGE = 10.0;
	static final int MAX_TARGETS = 4;
	static final int STRIKES = 6;
	static final int GAP = 4;
	static final int DAMAGE_10 = 250;
	static final int GRACE = 5;

	private final ServerPlayer caster;
	private final ShadeState state;
	private final List<LivingEntity> targets;
	private int t;
	private int strikes;

	private BladeStorm(ServerPlayer caster, ShadeState state, List<LivingEntity> targets) {
		this.caster = caster;
		this.state = state;
		this.targets = targets;
	}

	/** 반경 안 · 시야가 트인 적, 가까운 순. */
	static List<LivingEntity> pick(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		List<LivingEntity> list = new ArrayList<>();
		for (LivingEntity e : Targets.enemies(level, p.position(), RANGE, p)) {
			if (level.clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
					.getType() == HitResult.Type.MISS) {
				list.add(e);
			}
		}
		list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		return new ArrayList<>(list.subList(0, Math.min(MAX_TARGETS, list.size())));
	}

	static void cast(ServerPlayer p, ShadeState st) {
		if (st.storm != null) {
			return;
		}
		List<LivingEntity> targets = pick(p);
		if (targets.isEmpty()) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("반경 10칸 안에 벨 적이 없다", ChatFormatting.GRAY));
			return;
		}
		UltGauge.consume(p);
		if (st.rend != null) {
			st.rend.cancel();
		}
		st.evadeT = 0;
		BladeStorm s = new BladeStorm(p, st, targets);
		st.storm = s;
		Combatant c = Attachments.combatant(p);
		c.casting = true;
		c.ccImmune = true;
		ServerLevel level = p.level();
		Fx.sound(p, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.6F);
		Fx.sound(p, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 0.8F);
		Fx.particleExcept(level, p, Fx.dust(ShadowMark.PURPLE, 2.0F), p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 0.8, 0.5, 0);
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("천검난무", ChatFormatting.DARK_PURPLE), Component.empty(), 0, 20, 8);
		}
		s.strike();
		Effects.add(s);
	}

	/** 다음 적에게 순간이동해 한 번 벱니다. @return 벨 적이 남아 있으면 true */
	private boolean strike() {
		targets.removeIf(e -> !e.isAlive() || e.isRemoved() || e.level() != caster.level());
		if (targets.isEmpty()) {
			return false;
		}
		LivingEntity e = targets.get(strikes % targets.size());
		// 등 뒤에서 시작해 한 번 벨 때마다 120도씩 돌아가며 들어감
		Shade.blinkBehind(caster, e, strikes * 120.0);
		strikes++;
		SkillDamage.deal(e, caster, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
		ShadowMark.apply(state, e);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.SD_STRIKE, -1);
		ServerLevel level = caster.level();
		Fx.particle(level, ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1.0, e.getZ(), 2, 0.3, 0.3, 0.3, 0);
		// 시전자는 대상 바로 뒤에 서 있으므로 큰 입자는 남에게만
		Fx.particleExcept(level, caster, Fx.dust(ShadowMark.PURPLE, 1.6F), e.getX(), e.getY() + 1.0, e.getZ(), 20, 0.4, 0.6, 0.4, 0);
		Fx.sound(e, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.2F + strikes * 0.08F);
		Fx.sound(e, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 1.5F);
		return true;
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end();
			return false;
		}
		t++;
		caster.setDeltaMovement(Vec3.ZERO);
		caster.resetFallDistance();
		if (!Ticks.every(t, GAP)) {
			return true;
		}
		if (strikes >= STRIKES || !strike()) {
			finish();
			return false;
		}
		return true;
	}

	/** 남은 지속 0~100 (HUD). */
	int remainingPercent() {
		int total = Ticks.of(STRIKES * GAP);
		return Math.max(0, (total - t) * 100 / total);
	}

	private void finish() {
		end();
		state.graceT = Ticks.of(GRACE);
		// 끝나자마자 평타로 낙인을 터뜨릴 수 있게
		Attachments.profile(caster).atkCd = 0;
		Fx.sound(caster, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.8F);
	}

	private void end() {
		if (state.storm == this) {
			state.storm = null;
		}
		Combatant c = Attachments.combatant(caster);
		c.casting = false;
		c.ccImmune = false;
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
