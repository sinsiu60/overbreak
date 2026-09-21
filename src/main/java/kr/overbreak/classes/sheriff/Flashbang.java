package kr.overbreak.classes.sheriff;

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
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
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
 * [E] 섬광 수류탄 — 데이터팩 skill/sheriff/flash/* 대응 (체력 10배 기준).
 *
 *   왼손에서 조준 방향으로 곧게 던짐 (포물선 없음) — 틱당 0.75칸 · 8틱 = 6칸
 *   벽 · 적 몸(1.2칸)에 닿거나 6칸을 다 날면 터짐. 던진 본인은 안 맞음
 *   반경 2.5칸에 25 · 1.2초 50% 둔화 · 1.2초 이동기 봉인 · 시전 중이던 스킬(정신집중)이 끊김 (기절은 아님)
 *   쿨타임 10초. 수류탄은 던진 사람이 죽어도 끝까지 날아감 (피해 귀속만 빠짐)
 */
final class Flashbang implements Effects.Active {
	static final int COOLDOWN = 200;
	static final double SPEED = 0.75;
	static final int LIFE = 8;
	static final double TOUCH = 1.2;
	static final double RADIUS = 3.5;
	static final int DAMAGE_100 = 2500;
	static final double SLOW = 0.8;
	static final int DURATION = 32;

	private final ServerPlayer caster;
	private final Vec3 dir;
	private final Display.@Nullable ItemDisplay model;
	private Vec3 pos;
	private int t = Ticks.of(LIFE);

	private Flashbang(ServerPlayer caster, Vec3 pos, Vec3 dir, Display.@Nullable ItemDisplay model) {
		this.caster = caster;
		this.pos = pos;
		this.dir = dir;
		this.model = model;
	}

	static ItemStack stack() {
		ItemStack s = new ItemStack(Items.SNOWBALL);
		s.set(DataComponents.ITEM_MODEL, Overbreak.id("flashbang"));
		return s;
	}

	static void cast(ServerPlayer p) {
		if (Cooldowns.blocked(p, Sheriff.FLASH, "섬광 수류탄", ChatFormatting.GOLD)) {
			return;
		}
		Attachments.profile(p).setCooldown(Sheriff.FLASH, COOLDOWN);
		Peacekeeper.interrupt(p, Sheriff.state(p));
		Vec3 dir = Aim.direction(p).normalize();
		// 왼손 앞 (^x 는 왼쪽이 양수)
		Vec3 start = Local.offset(p.getEyePosition(), p.getYRot(), p.getXRot(), 0.3, -0.2, 0.6);
		float[] yp = Local.yawPitch(dir);
		Transformation shape = Displays.transform(0.0F, 0.0F, 0.0F, 0.45F, 0.45F, 0.45F, null, null);
		Display.ItemDisplay model = Displays.item(p.level(), start, yp[0], yp[1], stack(), shape, 1);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SH_FLASH, -1);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.6F);
		Fx.sound(p, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F, 0.9F);
		Effects.add(new Flashbang(p, start, dir, model));
	}

	@Override
	public boolean tick() {
		ServerLevel level = (ServerLevel) caster.level();
		if (--t < 0) {
			burst(level, pos);
			return false;
		}
		Vec3 next = pos.add(dir.scale(Ticks.speed(SPEED)));
		BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (wall.getType() != HitResult.Type.MISS) {
			burst(level, wall.getLocation().subtract(dir.scale(0.1)));
			return false;
		}
		pos = next;
		if (model != null) {
			Displays.move(model, pos, model.getYRot() + Ticks.speed(40.0F), model.getXRot());
		}
		if (Ticks.ambient()) {
			Fx.particle(level, ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.03, 0.03, 0.03, 0.005);
		}
		// 몸에 닿으면 바로 터집니다 (거리는 발밑 기준이라 0.8칸 내려서 잽니다)
		if (!Targets.within(level, pos.add(0.0, -0.8, 0.0), TOUCH, e -> e != caster).isEmpty()) {
			burst(level, pos);
			return false;
		}
		return true;
	}

	private void burst(ServerLevel level, Vec3 at) {
		discardModel();
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.6F);
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 2.0F);
		Fx.particle(level, ColorParticleOption.create(ParticleTypes.FLASH, 0xD9FFFFFA), at.x, at.y + 0.5, at.z, 2, 0, 0, 0, 0);
		Fx.particle(level, ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 60, 0.4, 0.4, 0.4, 0.25);
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 40, 0.6, 0.6, 0.6, 0.3);

		ServerPlayer src = caster.isAlive() && !caster.isRemoved() ? caster : null;
		for (LivingEntity e : Targets.within(level, at, RADIUS, e -> e != caster)) {
			SkillDamage.dealFine(e, src, DAMAGE_100, SkillDamage.Kind.NO_KB);
			if (e.isAlive()) {
				CrowdControl.slow(e, SLOW, DURATION);
				CrowdControl.seal(e, DURATION);
				// 정신집중만 끊습니다 (기절은 아님) — 채널링 스킬들이 다음 틱에 보고 스스로 끊김
				Attachments.combatant(e).breakT = Ticks.of(2);
			}
			Fx.particle(level, ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
			Fx.sound(e, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 2.0F);
		}
	}

	private void discardModel() {
		if (model != null) {
			model.discard();
		}
	}

	@Override
	public void cancel() {
		discardModel();
	}
}
