package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import com.mojang.math.Transformation;
import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [E] 그림자 표창 → 그림자 걸음.
 *
 *   조준 방향으로 곧게 던짐 — 틱당 1.6칸 · 10틱 = 16칸. 벽에 닿으면 떨어짐
 *   처음 닿은 적(몸 1.3칸 — 0.1a 에서 30% 넓힘): 30 피해 (넉백 없음) · 30% 둔화 1.5초 · 그림자 낙인
 *   꽂힌 뒤 3초 안에 F 를 다시 누르면 0.2초 동안 그 적의 등 뒤 1.3칸으로 날아감 ({@link ShadowStep}, 20칸 안 · 이동기라 봉인 중 불가)
 *   쿨타임 8초 (던질 때부터)
 */
final class ShadowKunai implements Effects.Active {
	static final int COOLDOWN = 160;
	static final double SPEED = 1.6;
	static final int LIFE = 10;
	static final double TOUCH = 1.3;
	static final int DAMAGE_10 = 300;
	/** 머리에 맞으면 두 배 (0.1 버전). */
	static final int HEADSHOT_PERCENT = 200;
	static final double SLOW = 0.3;
	static final int SLOW_T = 30;
	static final int STEP_WINDOW = 60;
	static final double STEP_RANGE = 20.0;

	private final ServerPlayer caster;
	private final ShadeState state;
	private final Vec3 dir;
	private final Display.@Nullable ItemDisplay model;
	private Vec3 pos;
	private int t = Ticks.of(LIFE);

	private ShadowKunai(ServerPlayer caster, ShadeState state, Vec3 pos, Vec3 dir, Display.@Nullable ItemDisplay model) {
		this.caster = caster;
		this.state = state;
		this.pos = pos;
		this.dir = dir;
		this.model = model;
	}

	static ItemStack stack() {
		ItemStack s = new ItemStack(Items.IRON_NUGGET);
		s.set(DataComponents.ITEM_MODEL, Overbreak.id("kunai"));
		return s;
	}

	static void cast(ServerPlayer p, ShadeState st) {
		if (st.storm != null) {
			return;
		}
		// 표창이 꽂혀 있으면 두 번째 입력 = 그림자 걸음 (쿨타임과 무관)
		if (st.stepT > 0 && st.stepTarget != null) {
			step(p, st);
			return;
		}
		if (Cooldowns.blocked(p, Shade.KUNAI, "그림자 표창", ChatFormatting.DARK_PURPLE)) {
			return;
		}
		Attachments.profile(p).setCooldown(Shade.KUNAI, COOLDOWN);
		Vec3 dir = Aim.direction(p).normalize();
		// 왼손 앞 (^x 는 왼쪽이 양수)
		Vec3 start = Local.offset(p.getEyePosition(), p.getYRot(), p.getXRot(), 0.3, -0.15, 0.6);
		float[] yp = Local.yawPitch(dir);
		Transformation shape = Displays.transform(0.0F, 0.0F, 0.0F, 0.6F, 0.6F, 0.6F, null, null);
		Display.ItemDisplay model = Displays.item(p.level(), start, yp[0], yp[1], stack(), shape, 1);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SD_KUNAI, -1);
		Fx.sound(p, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9F, 1.8F);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 2.0F);
		ShadowKunai k = new ShadowKunai(p, st, start, dir, model);
		if (k.advance()) {
			Effects.add(k);
		}
	}

	private static void step(ServerPlayer p, ShadeState st) {
		LivingEntity target = st.stepTarget;
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != p.level()
				|| target.distanceTo(p) > STEP_RANGE) {
			st.stepT = 0;
			st.stepTarget = null;
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			return;
		}
		if (Shade.sealed(p)) {
			return;
		}
		st.stepT = 0;
		st.stepTarget = null;
		ShadowStep.cast(p, st, target);
	}

	/** 한 틱 날아감. @return 아직 날고 있으면 true */
	private boolean advance() {
		ServerLevel level = caster.level();
		if (--t < 0) {
			drop(level);
			return false;
		}
		double speed = Ticks.speed(SPEED);
		Vec3 next = pos.add(dir.scale(speed));
		// 몸에 닿는지 반 칸씩 확인 (20틱에서 틱당 1.6칸이라 건너뛰지 않게)
		for (int i = 1; i <= 4; i++) {
			Vec3 at = pos.add(dir.scale(speed * i / 4.0));
			LivingEntity e = Targets.nearest(Targets.within(level, at.add(0.0, -0.8, 0.0), TOUCH, x -> x != caster), at);
			if (e != null) {
				hit(level, e, kr.overbreak.combat.Hitscan.headshot(e, at));
				return false;
			}
		}
		BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (wall.getType() != HitResult.Type.MISS) {
			pos = wall.getLocation();
			drop(level);
			return false;
		}
		pos = next;
		if (model != null) {
			Displays.move(model, pos, model.getYRot(), model.getXRot());
		}
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(ShadowMark.PURPLE, 0.9F), pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0);
		}
		return true;
	}

	private void hit(ServerLevel level, LivingEntity e, boolean head) {
		discardModel();
		ServerPlayer src = caster.isAlive() && !caster.isRemoved() ? caster : null;
		kr.overbreak.net.HitPayload.critNext = head;
		try {
			SkillDamage.deal(e, src, DAMAGE_10 * (head ? HEADSHOT_PERCENT : 100) / 100, SkillDamage.Kind.NO_KB);
		} finally {
			kr.overbreak.net.HitPayload.critNext = false;
		}
		Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1.2, e.getZ(), 15, 0.2, 0.3, 0.2, 0.2);
		Fx.sound(e, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 1.5F);
		if (!e.isAlive() || src == null) {
			return;
		}
		CrowdControl.slow(e, SLOW, SLOW_T);
		ShadowMark.apply(state, e);
		state.stepTarget = e;
		state.stepT = Ticks.of(STEP_WINDOW);
		Attachments.profile(caster).msgT = 40;
		Hud.actionbar(caster, Hud.bold("F  그림자 걸음", ChatFormatting.LIGHT_PURPLE));
		Fx.sound(caster, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	private void drop(ServerLevel level) {
		discardModel();
		Fx.particle(level, ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 6, 0.05, 0.05, 0.05, 0.01);
		Fx.sound(level, pos.x, pos.y, pos.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	private void discardModel() {
		if (model != null) {
			model.discard();
		}
	}

	@Override
	public boolean tick() {
		return advance();
	}

	@Override
	public void cancel() {
		discardModel();
	}
}
