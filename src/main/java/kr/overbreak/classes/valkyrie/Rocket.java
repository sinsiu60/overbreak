package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import com.mojang.math.Transformation;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * [우클릭] 전술 로켓 — 데이터팩 skill/rocket/* 대응 (체력 10배 기준).
 *
 *   조준 방향으로 틱당 1.2칸, 최대 20틱(24칸). 벽 · 몸(1.3칸)에 닿거나 끝까지 가면 터짐
 *   착탄 반경 3칸에 35 + 0.8초 살짝 띄우기. 넉백 없음
 *   쿨타임 5초. 탄막 포격 중에도 쓸 수 있는 유일한 스킬
 *   로켓은 시전자가 죽어도 끝까지 날아갑니다 (피해 귀속만 빠짐)
 */
final class Rocket implements Effects.Active {
	static final double SPEED = 1.2;
	static final int LIFE = 20;
	static final double TOUCH = 1.3;
	static final double RADIUS = 3.0;
	static final int DAMAGE_100 = 3500;
	/** 맞으면 1초 동안 30% 둔화 (0.1 버전 — 에어본 삭제). */
	static final int SLOW_TIME = 20;
	static final double SLOW = 0.3;
	static final int COOLDOWN = 100;

	private final ServerPlayer caster;
	private final Vec3 dir;
	private final Display.@Nullable BlockDisplay model;
	private Vec3 pos;
	private int t = Ticks.of(LIFE);

	private Rocket(ServerPlayer caster, Vec3 pos, Vec3 dir, Display.@Nullable BlockDisplay model) {
		this.caster = caster;
		this.pos = pos;
		this.dir = dir;
		this.model = model;
	}

	static void cast(ServerPlayer p) {
		if (Cooldowns.blocked(p, Valkyrie.ROCKET, "전술 로켓", ChatFormatting.YELLOW)) {
			return;
		}
		Attachments.profile(p).setCooldown(Valkyrie.ROCKET, COOLDOWN);
		Vec3 dir = Aim.direction(p).normalize();
		Vec3 start = p.getEyePosition().add(dir.scale(0.6));
		float[] yp = Local.yawPitch(dir);
		// 엔드 로드(세로 막대)를 눕혀 앞(+Z)으로 향하게: 가운데를 원점에 두고 X 축 90도
		Transformation shape = Displays.transform(-0.25F, 0.25F, -0.25F, 0.5F, 0.5F, 0.5F,
				new Quaternionf().rotationX((float) Math.toRadians(90.0)), null);
		Display.BlockDisplay model = Displays.block(p.level(), start, yp[0], yp[1], Blocks.END_ROD.defaultBlockState(), shape, 1);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_ROCKET, -1);
		Fx.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.2F, 1.1F);
		Fx.sound(p, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
		Effects.add(new Rocket(p, start, dir, model));
	}

	@Override
	public boolean tick() {
		ServerLevel level = (ServerLevel) caster.level();
		if (--t <= 0) {
			blast(level, pos);
			return false;
		}
		Vec3 next = pos.add(dir.scale(Ticks.speed(SPEED)));
		BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (wall.getType() != HitResult.Type.MISS) {
			blast(level, wall.getLocation().subtract(dir.scale(0.1)));
			return false;
		}
		pos = next;
		if (model != null) {
			Displays.move(model, pos, model.getYRot(), model.getXRot());
		}
		if (Ticks.ambient()) {
			Fx.particle(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.06, 0.06, 0.06, 0.01);
			Fx.particle(level, ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.08, 0.08, 0.08, 0.01);
		}
		// 몸에 닿으면 바로 터집니다 (거리는 발밑 기준이라 0.8칸 내려서 잽니다)
		if (!Targets.within(level, pos.add(0.0, -0.8, 0.0), TOUCH, e -> e != caster).isEmpty()) {
			blast(level, pos);
			return false;
		}
		return true;
	}

	private void blast(ServerLevel level, Vec3 at) {
		discardModel();
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.3F, 1.0F);
		Fx.particle(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 0.6, 0.4, 0.6, 0);
		Fx.particle(level, Fx.dust(Rifle.ORANGE, 1.8F), at.x, at.y + 0.3, at.z, 40, 1.2, 0.8, 1.2, 0);
		Fx.particle(level, ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 0.9, 0.6, 0.9, 0.06);
		// 착탄 범위를 바닥에 잠깐 (공중에서 터지면 그 아래 지면)
		BlockHitResult ground = level.clip(new ClipContext(at, at.add(0.0, -4.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		Vec3 floor = ground.getType() == HitResult.Type.MISS ? at : ground.getLocation();
		GroundShape.flash(level, floor, 0.0F, 360.0, RADIUS, 0x50FF8C28, 0xE0FFB040, 6);

		ServerPlayer src = caster.isAlive() && !caster.isRemoved() ? caster : null;
		for (LivingEntity e : Targets.within(level, at, RADIUS, e -> e != caster)) {
			SkillDamage.dealFine(e, src, DAMAGE_100, SkillDamage.Kind.MULTI_NO_KB);
			if (e.isAlive()) {
				CrowdControl.slow(e, SLOW, SLOW_TIME);
			}
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.4, 0.3, 0.4);
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
