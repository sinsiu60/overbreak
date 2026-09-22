package kr.overbreak.classes.gunslinger;

import java.util.List;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.HitboxRewind;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.net.ViewPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.sound.OverbreakSounds;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import kr.overbreak.util.Tracer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [웅크리기] 돌진 난사 — 앞으로 6칸 치고 나가 멈춰 서서 사방을 쓸어 버립니다 (0.2d, 시온 궁극기를 액티브 크기로 줄인 결).
 *
 * 단계 (1/20초 단위 — 애니메이션도 같은 값으로 비례해 늘고 줄어듭니다)
 *   기 모으기 {@link #WINDUP}  0.10초  이동 가능 · 평타/스킬 잠김
 *   돌진      {@link #DASH}    0.25초  시선의 수평 방향으로 6칸 등속 직선 (위아래를 봐도 수평 · 높이 유지 · 적은 뚫고 지나감)
 *   제동      {@link #BRAKE}   0.10초  수평 속도 0, 이후 중력 정상
 *   난사      {@link #SCATTER} 0.90초  0.15초마다 반경 5칸 원형 판정 6번 · 이동 속도 ×0.5
 *   마무리    {@link #RECOVER} 0.25초  총 돌리기
 *
 * 판정은 탄 궤적과 따로입니다 — 18발 궤적은 보여 주기용이고, 피해는 펄스마다 반경 안의 모든 적에게 들어갑니다
 * (40도 간격 궤적 사이에 선 적이 운에 따라 빗나가지 않게). 핑만큼 되감은 히트박스로 재고, 벽 너머는 맞지 않습니다.
 *
 * 이동기라 균열 지대 위에서는 쿨타임 없이 거부됩니다. 쿨타임 8초는 시전 즉시 들어가고, 끊겨도 돌려주지 않습니다.
 * 기절 · 에어본 등으로 끊기면 그 자리에서 끝 — 난사 전이면 난사는 나가지 않습니다.
 * 쓰는 동안 시점은 3인칭으로 잡혔다가 끝나면 쓰던 시점으로 돌아옵니다.
 */
public final class DashScatter implements Effects.Active {
	// ── 단계 길이 (1/20초 단위) ─────────────────────────────
	public static final int WINDUP = 2;
	public static final int DASH = 5;
	public static final int BRAKE = 2;
	public static final int SCATTER = 18;
	public static final int RECOVER = 5;
	public static final int DASH_START = WINDUP;
	public static final int BRAKE_START = DASH_START + DASH;
	public static final int SCATTER_START = BRAKE_START + BRAKE;
	public static final int RECOVER_START = SCATTER_START + SCATTER;
	/** 전체 길이 (1.6초). */
	public static final int LENGTH = RECOVER_START + RECOVER;

	/** 쿨타임 (시간 단위 · 8초). */
	public static final int COOLDOWN = 160;
	/** 돌진 거리 (칸). 속도는 거리 / 돌진 길이로 정해 단계 길이를 바꿔도 거리가 그대로입니다. */
	public static final double DISTANCE = 6.0;
	/** 서버가 받아 주는 최대 이동 (칸) — 넘으면 이 거리로 되돌립니다. */
	public static final double MAX_DISTANCE = DISTANCE * 1.25;
	public static final int PULSES = 6;
	/** 펄스 간격 (시간 단위 · 0.15초). */
	public static final int PULSE_GAP = 3;
	/** 판정 반경 (칸). 탄 궤적 길이도 같습니다. */
	public static final double RADIUS = 5.0;
	/** 펄스 한 번의 피해 x100 (7.0 — 스펙 0.7 을 체력 10배 기준으로). */
	public static final int DAMAGE_100 = 1200;
	/** 적 한 명을 맞힐 때마다 장전되는 탄 (0.2e). */
	public static final int RELOAD_PER_HIT = 2;
	/** 보여 주기용 탄 수 (0.05초마다 한 발 · 오른손 → 왼손 번갈아). */
	public static final int SHOTS = 18;
	/** 한 발마다 도는 각 — 18발이면 두 바퀴. */
	public static final float SHOT_STEP = 40.0F;
	/** 난사 중 이동 속도 (×0.5). 둔화와는 곱으로 겹칩니다. */
	static final double SCATTER_SPEED = -0.5;
	static final Identifier SLOW = Overbreak.id("dash_scatter_slow");
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	public enum Phase { WINDUP, DASH, BRAKE, SCATTER, RECOVER }

	private final ServerPlayer caster;
	private final GunslingerState state;
	private int t;
	private Phase phase = Phase.WINDUP;
	private Vec3 dir = Vec3.ZERO;
	private Vec3 dashFrom = Vec3.ZERO;
	private int dashTick;
	/** 난사 모양 시드 (엔티티 · 시전 틱) — 모든 클라이언트가 같은 팔 방향을 봅니다. */
	private final int seed;
	/** 대쉬 방향 = 시전 순간의 시선 yaw (수평). */
	private final float dashYaw;
	/** 시전 순간의 시선 pitch (+ 아래) — 바라보는 방향으로 위아래도 돌진합니다. */
	private final float dashPitch;
	/** 공중에서 제동했으면 난사가 끝날 때까지 그 높이에 떠 있음 (중력 끔). */
	private boolean hovering;
	private int pulses;
	private boolean slowed;

	private DashScatter(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
		this.seed = seed(caster.getId(), caster.level().getGameTime());
		this.dashYaw = caster.getYRot();
		this.dashPitch = caster.getXRot();
	}

	/** 시드 = hash(엔티티, 시전 틱). */
	static int seed(int entityId, long tick) {
		long h = entityId * 0x9E3779B97F4A7C15L ^ tick * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
		return (int) (h ^ (h >>> 33));
	}

	public int seed() {
		return seed;
	}

	public float dashYaw() {
		return dashYaw;
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.scatter != null || st.inUlt()) {
			return;
		}
		// 이동기 봉인 — 쿨타임을 쓰지 않고 거부
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (Cooldowns.blocked(p, Gunslinger.SCATTER, "돌진 난사", ChatFormatting.AQUA)) {
			return;
		}
		DualPistols.interrupt(p, st);
		Attachments.profile(p).setCooldown(Gunslinger.SCATTER, COOLDOWN);
		Attachments.combatant(p).casting = true;
		DashScatter s = new DashScatter(p, st);
		st.scatter = s;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_SCATTER, -1, LENGTH);
		kr.overbreak.net.ScatterPayload.broadcast(p, s.seed, s.dashYaw);
		ViewPayload.send(p, true);
		s.windupFx();
		Effects.add(s);
	}

	/** 지금 단계. */
	public Phase phase() {
		return phase;
	}

	/** 시작부터 지난 시간 (1/20초 단위) — 늦게 보기 시작한 관전자에게 건너뛸 만큼. */
	public int elapsedTime() {
		return Ticks.toTime(t);
	}

	/** 시험용: 지금까지 난 펄스 수. */
	public int pulses() {
		return pulses;
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end(true);
			return false;
		}
		// 기절 · 넘어뜨림 · 에어본 · 밀쳐내기 — 어느 단계든 그 자리에서 끝 (난사 전이면 난사는 나가지 않음)
		if (Attachments.combatant(caster).hardCc()) {
			end(true);
			return false;
		}
		t++;
		switch (phase) {
			case WINDUP -> {
				if (t >= Ticks.of(DASH_START)) {
					startDash();
				}
			}
			case DASH -> {
				dashFx();
				// 벽에 부딪혀도 끊지 않습니다 — 난사가 시작될 때까지 돌진은 그대로 (0.2f)
				if (t >= Ticks.of(BRAKE_START) + 1) {
					startBrake();
				}
			}
			case BRAKE -> {
				if (t >= Ticks.of(SCATTER_START) + 1) {
					startScatter();
				}
			}
			case SCATTER -> {
				while (pulses < PULSES && t >= Ticks.of(SCATTER_START + pulses * PULSE_GAP)) {
					pulse();
				}
				if (t >= Ticks.of(RECOVER_START)) {
					startRecover();
				}
			}
			case RECOVER -> {
				if (Ticks.ambient()) {
					Vec3 hands = caster.getEyePosition().subtract(0, 0.4, 0);
					Fx.particle(caster.level(), ParticleTypes.SMOKE, hands, 2, 0.3, 0.1, 0.3, 0.01);
				}
				if (t >= Ticks.of(LENGTH)) {
					end(false);
					return false;
				}
			}
		}
		return true;
	}

	// ── 단계 전환 ───────────────────────────────────────────

	private void windupFx() {
		ServerLevel level = caster.level();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 muzzle = Local.fromEyes(caster, side * 0.3, -0.2, 0.45);
			Fx.particleExcept(level, caster, ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/**
	 * 돌진 — 시전 순간 바라본 방향 그대로 (위아래 포함) 6칸. 중력은 받지 않습니다.
	 * 땅 위에서 수평보다 아래를 보고 있었으면 정면(수평)으로 나갑니다 — 바닥에 처박히지 않게.
	 */
	public static Vec3 direction(float yawDeg, float pitchDeg, boolean onGround) {
		double yaw = Math.toRadians(yawDeg);
		double pitch = onGround && pitchDeg > 0.0F ? 0.0 : Math.toRadians(pitchDeg);
		double flat = Math.cos(pitch);
		return new Vec3(-Math.sin(yaw) * flat, -Math.sin(pitch), Math.cos(yaw) * flat);
	}

	private void startDash() {
		phase = Phase.DASH;
		dir = direction(dashYaw, dashPitch, caster.onGround());
		dashFrom = caster.position();
		dashTick = t;
		Motion.dash(caster, dir, DISTANCE / DASH, DASH);
		// 속도를 싣는 창을 한 틱 늘립니다 — 서버가 실은 속도는 클라이언트에 한 틱 늦게 닿아 첫 틱만큼(0.4칸) 모자랐습니다
		// (실측 5.6칸). 속도 공식(거리 / 돌진 길이)은 그대로 두고 제동도 한 틱 뒤로 미룹니다.
		Attachments.combatant(caster).dashT += 1;
		CrowdControl.track(caster);
		ServerLevel level = caster.level();
		Fx.ring(level, caster.position(), 0.8, 12, 0.05, ParticleTypes.CLOUD);
	}

	/** 돌진 중 속도선 · 궤적 (0.05초마다 한 벌). */
	private void dashFx() {
		if (!Ticks.ambient()) {
			return;
		}
		ServerLevel level = caster.level();
		Vec3 back = caster.position().add(0, 1.0, 0).subtract(dir.scale(0.6));
		Fx.particle(level, ParticleTypes.END_ROD, back, 2, 0.15, 0.4, 0.15, 0.0);
		Fx.particle(level, Fx.dust(SKY, 0.9F), back, 4, 0.25, 0.5, 0.25, 0);
	}

	/** 제동 — 속도 0 (세로까지). 서버가 받아 주는 거리(7.5칸)를 넘었으면 거기로 되돌립니다. */
	private void startBrake() {
		phase = Phase.BRAKE;
		Motion.stop(caster);
		// 공중이면 제동 · 난사 동안 떨어지지 않고 그 자리에 떠서 쏨 (마무리에서 다시 떨어짐)
		if (!caster.onGround()) {
			hover(true);
		}
		Vec3 now = caster.position();
		Vec3 moved = now.subtract(dashFrom);
		double dist = moved.length();
		if (dist > MAX_DISTANCE) {
			Vec3 clamp = dashFrom.add(moved.scale(MAX_DISTANCE / dist));
			caster.teleportTo(caster.level(), clamp.x, clamp.y, clamp.z, Relative.ROTATION, 0.0F, 0.0F, false);
		}
		ServerLevel level = caster.level();
		// 앞쪽 부채꼴로 흙먼지
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(-35 + i * 10);
			Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
			Vec3 at = caster.position().add(d.scale(0.6)).add(0, 0.1, 0);
			Fx.particle(level, ParticleTypes.POOF, at, 1, 0.05, 0.02, 0.05, 0.03);
		}
	}

	private void startScatter() {
		phase = Phase.SCATTER;
		CrowdControl.mod(caster, Attributes.MOVEMENT_SPEED, SLOW, SCATTER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		slowed = true;
	}

	private void startRecover() {
		phase = Phase.RECOVER;
		unslow();
		hover(false);
	}

	// ── 난사 ───────────────────────────────────────────────

	// 보여 주기용 탄(궤적 · 총구 화염 · 탄피 · 발사음)은 클라이언트의 3인칭 애니메이션이 발마다 총구 방향을 계산해 냅니다
	// (스펙 dash_scatter_anim_3p PART 3-5 — 애니메이션이 사격 방향의 유일한 원천). 서버는 판정 펄스만 맡습니다.

	/** 판정 한 번 — 지금 자리 기준 반경 5칸 안의 모든 적 (핑만큼 되감음 · 벽 너머 제외 · 넉백 없음). */
	private void pulse() {
		ServerLevel level = caster.level();
		Vec3 center = caster.position();
		Vec3 eye = caster.getEyePosition();
		int rewind = HitboxRewind.rewindTicks(caster);
		boolean air = AeroDrift.airborne(caster);
		int damage = air ? DAMAGE_100 * AeroDrift.CRIT_PERCENT / 100 : DAMAGE_100;
		boolean any = false;
		List<LivingEntity> near = Targets.enemies(level, center, RADIUS + 2.0, caster);
		for (LivingEntity e : near) {
			AABB box = HitboxRewind.boxAt(e, rewind);
			if (!inRadius(center.add(0, 0.9, 0), box) || !visible(level, eye, box)) {
				continue;
			}
			HitPayload.critNext = air;
			try {
				SkillDamage.dealFine(e, caster, damage, SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 5, 0.25, 0.35, 0.25, 0.2);
			any = true;
			// 한 명 맞힐 때마다 쌍권총에 2발 장전 (0.2e)
			state.ammo = Math.min(DualPistols.MAG, state.ammo + RELOAD_PER_HIT);
		}
		if (air && any) {
			AeroDrift.onAirHit(caster);
		}
		// 적중음은 시전자 본인에게만, 펄스당 한 번 (소리는 클라이언트가 틉니다 — 서버는 이 스킬 소리를 방송하지 않음)
		if (any) {
			kr.overbreak.net.ScatterHitPayload.send(caster, air);
		}
		// 판정 반경을 한 번 깜빡여 보여 줍니다 (0.1초)
		GroundShape.flash(level, center, 0.0F, 360.0, RADIUS, 0x307FD4FF, 0xE07FD4FF, 2);
		pulses++;
	}

	/** 판정 원(구) 안에 히트박스가 걸치는가. */
	static boolean inRadius(Vec3 c, AABB box) {
		double x = Math.max(box.minX, Math.min(c.x, box.maxX));
		double y = Math.max(box.minY, Math.min(c.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(c.z, box.maxZ));
		return c.distanceToSqr(x, y, z) <= RADIUS * RADIUS;
	}

	/** 시전자 눈 → 대상 몸통 사이에 벽이 없는가. */
	private boolean visible(ServerLevel level, Vec3 eye, AABB box) {
		Vec3 body = box.getCenter();
		return level.clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS;
	}

	// ── 정리 ───────────────────────────────────────────────

	private void hover(boolean on) {
		if (hovering == on) {
			return;
		}
		hovering = on;
		// 궤적 추격 비행 중이면 끝나도 중력 없음 그대로
		caster.setNoGravity(on || state.pursuit != null);
		if (on) {
			caster.setDeltaMovement(caster.getDeltaMovement().multiply(1, 0, 1));
			caster.hurtMarked = true;
		}
	}

	/** 시험용: 공중에 떠 있는 중인가. */
	public boolean hovering() {
		return hovering;
	}

	private void unslow() {
		if (slowed) {
			CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW);
			slowed = false;
		}
	}

	/** @param interrupted 끊겼는가 — 보는 사람들의 동작을 기본 자세로 되돌립니다 */
	private void end(boolean interrupted) {
		if (state.scatter == this) {
			state.scatter = null;
		}
		if (phase == Phase.DASH) {
			Motion.brake(caster);
		}
		unslow();
		hover(false);
		Attachments.combatant(caster).casting = false;
		if (interrupted) {
			SkillAnimPayload.stop(caster, SkillAnimPayload.GS_SCATTER);
		}
		if (!caster.hasDisconnected()) {
			ViewPayload.send(caster, false);
		}
	}

	@Override
	public void cancel() {
		end(true);
	}

	@Override
	public Object owner() {
		return caster;
	}
}
