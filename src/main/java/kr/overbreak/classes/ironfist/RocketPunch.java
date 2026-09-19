package kr.overbreak.classes.ironfist;

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
import kr.overbreak.input.InputRouter;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [우클릭 길게] 로켓 펀치 — 데이터팩 skill/punch/* 대응 (체력 10배 기준).
 *
 *   충전: 누르고 있는 동안 최대 1.2초(24틱). 오른손 건틀릿에 파란 기가 모임. 이동속도 -40%, 평타 · 스킬 불가
 *     손을 떼면 발사 (최소 5틱). 최대 충전 뒤 0.6초가 지나면 저절로 발사. 기절하면 취소 (쿨타임은 나감)
 *   돌진: 발사 순간의 조준 방향(수평)으로 12틱. 거리 5~12칸, 피해 30~60 (강화 = 피해 1.5배 · 거리 1.3배 — 15.6칸 · 90)
 *     공중에서도 고도 유지. 전방 90도 · 2.5칸 부채꼴에 적이 들어오는 순간 그 자리에서 멈춤
 *   명중: 대상을 1.5~4칸 밀쳐냄 (군중 제어, 8틱). 밀리는 10틱 동안 벽에 처박히면 40 추가 + 0.7초 기절 (1회)
 *     강화 펀치(파워 블록 60 이상 · 궁극기)는 벽 충돌 기절이 충전량에 따라 0.7 → 1.7초
 *   균열 지대 위(봉인)에서는 쓸 수 없음. 쿨타임 5초 (발사 · 취소 순간부터)
 */
final class RocketPunch implements Effects.Active {
	static final int MAX_CHARGE = 18;
	static final int GRACE = 12;
	static final int MIN_HOLD = 5;
	static final int COOLDOWN = 100;
	// 같은 거리를 30% 빠르게 (0.1 버전)
	static final int DASH = 9;
	static final double HIT_RADIUS = 2.5;
	static final double HALF_ARC = 45.0;
	static final int PUSH_TICKS = 8;
	private static final Identifier SLOW = Overbreak.id("if_charge");
	private static final int BLUE = Fx.rgb(0.30, 0.70, 1.00);

	private final ServerPlayer caster;
	private final IronFistState state;
	private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean dashing;
	private int ch;
	private int grace;
	private boolean held;
	private int dt;
	private double dx;
	private double dz;
	private float yaw;
	private int damage10;
	private double pushDistance;
	private int wallStun = WallWindow.STUN;

	private RocketPunch(ServerPlayer caster, IronFistState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, IronFistState st) {
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (st.punch != null || Cooldowns.blocked(p, IronFist.PUNCH, "로켓 펀치", ChatFormatting.RED)) {
			return;
		}
		RocketPunch r = new RocketPunch(p, st);
		st.punch = r;
		Attachments.combatant(p).casting = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SLOW, -0.40, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IF_CHARGE, -1);
		Fx.sound(p, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.1F, 0.6F);
		Fx.sound(p, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.6F);
		Effects.add(r);
	}

	boolean charging() {
		return !dashing;
	}

	/** 충전량 0~100 (HUD). */
	int chargePercent() {
		return Math.min(100, ch * 100 / Ticks.of(MAX_CHARGE));
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		return dashing ? dashTick() : chargeTick();
	}

	private boolean chargeTick() {
		if (Attachments.combatant(caster).interrupted()) {
			Attachments.profile(caster).setCooldown(IronFist.PUNCH, COOLDOWN);
			cleanup();
			Attachments.profile(caster).msgT = 25;
			Hud.actionbar(caster, Hud.text("로켓 펀치가 끊겼습니다", ChatFormatting.DARK_GRAY));
			return false;
		}
		int max = Ticks.of(MAX_CHARGE);
		if (ch < max) {
			ch++;
		}
		if (ch >= max) {
			grace++;
		}
		if (ch == max && grace == 1) {
			Fx.sound(caster, SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 0.8F, 1.8F);
		}
		// 오른손 쪽에 파란 기
		ServerLevel level = caster.level();
		Vec3 hand = Local.flat(caster.position(), caster.getYRot(), -0.45, 0.9, -0.15);
		if (Ticks.ambient()) {
			int chTime = Ticks.toTime(ch);
			Fx.particle(level, Fx.dust(BLUE, 0.9F + chTime / 40.0F), hand.x, hand.y, hand.z, 1 + chTime / 8, 0.12, 0.12, 0.12, 0);
		}

		boolean holding = InputRouter.holdingRight(caster);
		if (ch >= Ticks.of(MIN_HOLD) && !holding && held) {
			fire();
			return true;
		}
		if (holding) {
			held = true;
		}
		if (!InputModePayload.canSend(caster)) {
			Attachments.profile(caster).msgT = 2;
			Hud.actionbar(caster, Component.empty()
					.append(Hud.bold("로켓 펀치  ", ChatFormatting.GRAY))
					.append(Hud.bold(Hud.bar10(ch * 10 / max), ChatFormatting.AQUA)));
		}
		if (grace >= Ticks.of(GRACE)) {
			fire();
		}
		return true;
	}

	private void fire() {
		// 충전량을 시간 단위(0~24)로 — 데이터팩 정수 계산을 그대로 씀
		int max = Ticks.of(MAX_CHARGE);
		int r = (int) Math.round(Math.min(ch, max) * (double) MAX_CHARGE / max);
		int dist100 = r * 700 / MAX_CHARGE + 500;
		int dmg100 = r * 300 / MAX_CHARGE + 300;
		boolean empowered = state.empowerT > 0;
		if (empowered) {
			state.empowerT = 0;
			dmg100 = dmg100 * 150 / 100;
			// 파워 펀치는 기본 펀치보다 30% 멀리 — 날아가는 시간(DASH)은 같으므로 그만큼 빨라집니다 (0.1a)
			dist100 = dist100 * 130 / 100;
			// 강화 펀치의 벽 충돌 기절: 충전량에 따라 0.7초(14틱) → 1.7초(34틱)
			wallStun = WallWindow.STUN + (WallWindow.EMPOWERED_STUN_MAX - WallWindow.STUN) * r / MAX_CHARGE;
		}
		// 데이터팩 피해 0.1 단위 (pdmg / 10) 를 10배 기준으로
		damage10 = dmg100 / 10 * 10;
		pushDistance = (r * 250 / MAX_CHARGE + 150) / 100.0;

		Attachments.profile(caster).setCooldown(IronFist.PUNCH, COOLDOWN);
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW);
		SkillAnimPayload.stop(caster, SkillAnimPayload.IF_CHARGE);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.IF_PUNCH, -1);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.7F);
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 1.1F);
		if (empowered) {
			Fx.sound(caster, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 1.2F, 1.4F);
		}

		Vec3 aim = Aim.facing(caster);
		Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = Motion.flatLook(caster);
		}
		flat = flat.normalize();
		dx = flat.x;
		dz = flat.z;
		yaw = Local.yawPitch(flat)[0];
		// 속도 = 총 거리 / 12틱 (데이터팩 정수 나눗셈 그대로)
		double speed = (dist100 / DASH) / 100.0;
		Motion.dash(caster, dx, dz, speed, DASH, true);
		CrowdControl.track(caster);
		dashing = true;
		dt = Ticks.of(DASH);
	}

	private boolean dashTick() {
		dt--;
		ServerLevel level = caster.level();
		boolean stop = false;
		for (LivingEntity e : Targets.enemies(level, caster.position(), HIT_RADIUS, caster)) {
			if (!hit.contains(e) && Targets.inCone(caster.position(), yaw, HALF_ARC, HIT_RADIUS, e)) {
				hit.add(e);
				strike(e);
				stop = true;
			}
		}
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(BLUE, 1.2F), caster.getX(), caster.getY() + 0.9, caster.getZ(), 4, 0.3, 0.4, 0.3, 0);
		}
		if (stop || dt <= 0) {
			cleanup();
			Fx.sound(caster, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.4F);
			return false;
		}
		return true;
	}

	private void strike(LivingEntity e) {
		SkillDamage.deal(e, caster, damage10, SkillDamage.Kind.MULTI_NO_KB);
		if (e.isAlive()) {
			double tx = e.getX() - caster.getX();
			double tz = e.getZ() - caster.getZ();
			if (tx * tx + tz * tz < 1.0E-4) {
				tx = dx;
				tz = dz;
			}
			CrowdControl.push(e, tx, tz, pushDistance, PUSH_TICKS);
			Effects.add(new WallWindow(caster, e, yaw, wallStun));
		}
		Absorb.gain(caster);
		Fx.sound(e, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.3F, 0.7F);
		if (e.level() instanceof ServerLevel level) {
			Fx.particle(level, ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 2, 0.2, 0.3, 0.2, 0);
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

	private void cleanup() {
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW);
		Attachments.combatant(caster).casting = false;
		if (dashing) {
			Motion.brake(caster);
			SkillAnimPayload.stop(caster, SkillAnimPayload.IF_PUNCH);
		} else {
			SkillAnimPayload.stop(caster, SkillAnimPayload.IF_CHARGE);
		}
		if (state.punch == this) {
			state.punch = null;
		}
	}

	/**
	 * 밀려나는 동안 벽 판정 창 — 데이터팩 skill/punch/wall_tick · wall_look · wall_hit.
	 * 밀어낸 방향(시전자의 돌진 방향)으로 0.75칸 앞을 다리 · 가슴 높이에서 봅니다. 한 번 터지면 닫힙니다.
	 * 시전자가 죽어도 판정이 남습니다 (피해 귀속만 빠짐).
	 */
	static final class WallWindow implements Effects.Active {
		static final int WINDOW = 10;
		static final int DAMAGE = 400;
		/** 기본 벽 충돌 기절 0.7초. */
		static final int STUN = 14;
		/** 강화 펀치 최대 충전의 벽 충돌 기절 1.7초. */
		static final int EMPOWERED_STUN_MAX = 34;

		private final ServerPlayer source;
		private final LivingEntity target;
		private final double wx;
		private final double wz;
		private final int stun;
		private int t = Ticks.of(WINDOW);

		WallWindow(ServerPlayer source, LivingEntity target, float yaw, int stun) {
			this.source = source;
			this.target = target;
			this.stun = stun;
			double r = Math.toRadians(yaw);
			this.wx = -Math.sin(r);
			this.wz = Math.cos(r);
		}

		@Override
		public boolean tick() {
			if (!target.isAlive() || target.isRemoved()) {
				return false;
			}
			t--;
			if (wall(0.4) || wall(1.4)) {
				slam();
				return false;
			}
			return t > 0;
		}

		private boolean wall(double height) {
			ServerLevel level = (ServerLevel) target.level();
			BlockPos pos = BlockPos.containing(target.getX() + wx * 0.75, target.getY() + height, target.getZ() + wz * 0.75);
			return !level.getBlockState(pos).is(BlockTags.REPLACEABLE);
		}

		private void slam() {
			ServerPlayer src = source.isAlive() && !source.isRemoved() ? source : null;
			SkillDamage.deal(target, src, DAMAGE, SkillDamage.Kind.MULTI_NO_KB);
			if (target.isAlive()) {
				CrowdControl.stun(target, stun);
			}
			if (src != null) {
				Absorb.gain(src);
			}
			ServerLevel level = (ServerLevel) target.level();
			Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
					target.getX(), target.getY() + 1, target.getZ(), 40, 0.4, 0.6, 0.4, 0.4);
			Fx.particle(level, ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 3, 0.3, 0.3, 0.3, 0);
			Fx.sound(target, SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 1.4F, 0.6F);
			Fx.sound(target, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
		}
	}
}
