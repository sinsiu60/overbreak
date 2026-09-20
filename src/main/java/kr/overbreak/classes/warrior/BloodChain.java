package kr.overbreak.classes.warrior;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Transformation;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [E] 피의 사슬 — 데이터팩 skill/chain/* 대응.
 *
 *   준비: 0.4초(8틱) 동안 왼손으로 사슬을 돌림 (정신집중 — 다른 스킬 · 평타 불가, 기절 · 에어본이면 끊김)
 *         갈고리가 왼손 옆 세로 원을 점점 빠르게 돔 ({@link ChainSpin})
 *   던지기: 준비가 끝난 순간의 조준 방향으로 왼손에서 던짐
 *   비행: 1틱 1칸 x 15틱 = 15칸, 벽에 막히면 소멸
 *   판정: 사슬(눈높이)과 대상 발밑 거리 1.8칸 — 키를 감안한 값
 *   적중: 25 피해 + 출혈 1 + 0.3초(6틱) 기절
 *   견인: 기절이 풀린 뒤(8틱 대기) 최대 40틱, 틱당 1.17칸 (0.1c 에서 다시 2배). 시전자 앞 1.3칸에 멈춤
 *         (남은 거리가 한 틱 이동보다 짧으면 남은 만큼만 움직이고, 도착하면 수평 속도를 0 으로 — 관성으로 뚫고 지나가지 않음)
 *   중단: 견인 중 기절·에어본에 걸리면 끊김
 *   쿨타임은 누른 순간부터 (준비가 끊겨도 돌아감)
 *
 * 데이터팩과 달라진 점 — 견인은 투명한 방어구 거치대에 태우는 대신 매 틱 속도를 싣습니다.
 */
final class BloodChain implements Effects.Active {
	static final int RANGE_TICKS = 15;
	static final double HIT_RADIUS = 1.8;
	static final int PIN_STUN = 6;
	/** 도착한 뒤 남는 둔화 (0.1 버전). */
	static final int ARRIVE_SLOW_TIME = 40;
	static final double ARRIVE_SLOW = 0.5;
	static final int PIN_WAIT = 8;
	static final int PULL_TICKS = 40;
	static final double PULL_SPEED = 1.17;
	/** 시전자 앞 이 거리에서 멈춥니다. */
	static final double STOP_DIST = 1.3;
	private static final double ARRIVE_EPS = 0.15;
	private static final int LINKS = 18;
	private static final double SPIN_RADIUS = 0.55;
	private static final int RED = Fx.rgb(0.75, 0.10, 0.12);

	private enum Phase { WINDUP, FLY, PIN, PULL }

	private final ServerPlayer caster;
	private final WarriorState state;
	private Phase phase = Phase.WINDUP;
	private Vec3 head;
	private float yaw;
	private float pitch;
	private int t;
	private boolean ended;
	private @Nullable LivingEntity target;
	private Display.@Nullable BlockDisplay hook;
	private final List<Display.BlockDisplay> links = new ArrayList<>();

	private BloodChain(ServerPlayer caster, WarriorState state) {
		this.caster = caster;
		this.state = state;
		this.yaw = caster.getYRot();
		this.pitch = 0.0F;
		this.head = leftHand();
	}

	static void cast(ServerPlayer p, WarriorState st) {
		Attachments.profile(p).setCooldown(Warrior.CHAIN, 160);
		if (st.chain != null) {
			st.chain.cleanup();
		}
		BloodChain ch = new BloodChain(p, st);
		st.chain = ch;
		Attachments.combatant(p).casting = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.CHAIN, -1);
		ServerLevel level = (ServerLevel) p.level();
		ch.hook = Displays.block(level, ch.head, ch.yaw, 0.0F,
				Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.NORTH),
				Displays.transform(-0.45F, -0.45F, -0.45F, 0.9F, 0.9F, 0.9F, null, null), 1);
		for (int i = 0; i < LINKS; i++) {
			Display.BlockDisplay l = Displays.block(level, p.position(), 0, 0,
					Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z),
					hiddenLink(), 1);
			if (l != null) {
				ch.links.add(l);
			}
		}
		Fx.sound(p, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
		Effects.add(ch);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		ServerLevel level = (ServerLevel) caster.level();
		return switch (phase) {
			case WINDUP -> windup(level);
			case FLY -> fly(level);
			case PIN -> pin(level);
			case PULL -> pull(level);
		};
	}

	/** 왼손 위치 (눈 기준 왼쪽 · 아래 · 앞). ^x 양수가 왼쪽입니다. */
	private Vec3 leftHand() {
		return Local.fromEyes(caster, 0.35, -0.4, 0.45);
	}

	/** 준비: 왼손 옆 세로 원을 따라 갈고리를 돌리고, 손에서 갈고리까지 짧은 사슬을 폅니다. */
	private boolean windup(ServerLevel level) {
		if (Attachments.combatant(caster).hardCc()) {
			Fx.sound(caster, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
			cleanup();
			return false;
		}
		t++;
		float bodyYaw = caster.getYRot();
		Vec3 center = Local.offset(caster.getEyePosition(), bodyYaw, 0.0F, 0.6, -0.05, 0.35);
		double theta = Math.toRadians(ChainSpin.angleDeg(Ticks.time(t)));
		// 앞 → 위 → 뒤 → 아래 로 도는 세로 원 (몸 옆에서 앞으로 돌려 던지기 좋은 방향)
		Vec3 spin = Local.offset(center, bodyYaw, 0.0F, 0.0, Math.sin(theta) * SPIN_RADIUS, Math.cos(theta) * SPIN_RADIUS);
		head = spin;
		if (hook != null) {
			float[] yp = Local.yawPitch(spin.subtract(center));
			Displays.move(hook, spin, yp[0], yp[1]);
		}
		drawLinks(spin);
		if (Ticks.every(t, 3)) {
			Fx.sound(caster, SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.8F, 0.7F + (float) Ticks.time(t) * 0.04F);
		}
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(RED, 0.8F), spin, 1, 0.02, 0.02, 0.02, 0);
		}
		if (t >= Ticks.of(ChainSpin.WINDUP_TICKS)) {
			fire();
		}
		return true;
	}

	/** 준비가 끝난 순간의 조준 방향으로 왼손에서 던집니다. */
	private void fire() {
		Attachments.combatant(caster).casting = false;
		float[] aim = Aim.yawPitch(caster);
		yaw = aim[0];
		pitch = aim[1];
		head = Local.offset(caster.getEyePosition(), yaw, pitch, 0.3, -0.3, 0.6);
		if (hook != null) {
			Displays.move(hook, head, yaw, pitch);
		}
		Fx.sound(caster, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9F, 1.3F);
		Fx.sound(caster, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.9F);
		phase = Phase.FLY;
		t = 0;
	}

	private boolean fly(ServerLevel level) {
		t++;
		int range = Ticks.of(RANGE_TICKS);
		Vec3 next = Local.offset(head, yaw, pitch, 0, 0, RANGE_TICKS / (double) range);
		boolean blocked = !level.getBlockState(BlockPos.containing(next)).isAir();
		if (!blocked && t <= range) {
			head = next;
		}
		if (hook != null) {
			Displays.move(hook, head, yaw, pitch);
		}
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(RED, 1.0F), head, 4, 0.12, 0.12, 0.12, 0);
			Fx.particle(level, ParticleTypes.CRIT, head, 2, 0.1, 0.1, 0.1, 0.02);
		}
		drawLinks(head);

		LivingEntity hit = firstHit(level);
		if (hit != null) {
			onHit(level, hit);
			return true;
		}
		if (blocked || t >= range) {
			Fx.particle(level, Fx.dust(RED, 1.1F), head, 10, 0.25, 0.25, 0.25, 0);
			cleanup();
			return false;
		}
		return true;
	}

	private @Nullable LivingEntity firstHit(ServerLevel level) {
		double r2 = HIT_RADIUS * HIT_RADIUS;
		AABB box = new AABB(head, head).inflate(HIT_RADIUS + 1);
		List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> kr.overbreak.util.Targets.hostile(caster, e) && e.isAlive()
						&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
						&& e.position().distanceToSqr(head) <= r2);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : list) {
			double d = e.position().distanceToSqr(head);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private void onHit(ServerLevel level, LivingEntity e) {
		target = e;
		if (hook != null) {
			hook.discard();
			hook = null;
		}
		SkillDamage.deal(e, caster, 250, SkillDamage.Kind.NORMAL);
		if (e.isAlive()) {
			Bleed.add(e, caster);
			CrowdControl.stun(e, PIN_STUN);
		}
		Fx.sound(e, SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.3F, 0.6F);
		Fx.particle(level, Fx.dust(RED, 1.3F), e.getX(), e.getY() + 1, e.getZ(), 25, 0.3, 0.4, 0.3, 0);
		phase = Phase.PIN;
		t = 0;
	}

	private boolean pin(ServerLevel level) {
		if (target == null || !target.isAlive()) {
			cleanup();
			return false;
		}
		t++;
		drawLinks(target.position().add(0, 1, 0));
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(RED, 1.0F), target.getX(), target.getY() + 1, target.getZ(), 6, 0.3, 0.4, 0.3, 0);
		}
		if (t >= Ticks.of(PIN_WAIT)) {
			phase = Phase.PULL;
			t = 0;
		}
		return true;
	}

	private boolean pull(ServerLevel level) {
		if (target == null || !target.isAlive()) {
			cleanup();
			return false;
		}
		t++;
		Combatant tc = Attachments.combatant(target);
		// 넘어뜨림 · 에어본은 사슬을 끊지만, 사슬이 건 기절은 도착할 때까지 이어집니다 (0.1 버전)
		if (tc.knockT > 0 || tc.airT > 0) {
			breakChain(level);
			return false;
		}
		// 끌려오는 동안 기절을 이어 갑니다 (소리 · 입자는 처음 걸 때만)
		CrowdControl.stun(target, 4, false);
		// 멈출 곳: 시전자에서 대상 쪽으로 STOP_DIST 떨어진 지점 (수평 기준)
		Vec3 toTarget = target.position().subtract(caster.position());
		Vec3 flat = new Vec3(toTarget.x, 0.0, toTarget.z);
		double flatLen = flat.length();
		Vec3 stop = flatLen < 1.0E-4 ? target.position() : caster.position().add(flat.scale(STOP_DIST / flatLen));
		Vec3 dir = stop.subtract(target.position());
		double len = dir.length();
		if (flatLen <= STOP_DIST + ARRIVE_EPS || len <= ARRIVE_EPS) {
			Vec3 old = target.getDeltaMovement();
			target.setDeltaMovement(0.0, Math.min(old.y, 0.0), 0.0);
			target.hurtMarked = true;
			// 도착 — 기절을 풀고 2초간 둔화
			CrowdControl.clearStun(target);
			CrowdControl.slow(target, ARRIVE_SLOW, ARRIVE_SLOW_TIME);
			Warrior.onGrabArrive(caster, target);
			breakChain(level);
			return false;
		}
		// 남은 거리가 한 틱 이동보다 짧으면 남은 만큼만 — 다음 틱에 정확히 멈출 곳에 닿습니다
		Vec3 v = dir.scale(Math.min(Ticks.speed(PULL_SPEED), len) / len);
		target.setDeltaMovement(v);
		target.hurtMarked = true;
		drawLinks(target.position().add(0, 1, 0));
		if (Ticks.every(t, 5)) {
			Fx.sound(target, SoundEvents.CHAIN_STEP, SoundSource.HOSTILE, 1.0F, 0.7F);
		}
		if (t >= Ticks.of(PULL_TICKS)) {
			breakChain(level);
			return false;
		}
		return true;
	}

	private void breakChain(ServerLevel level) {
		if (target != null) {
			Fx.sound(target, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 0.8F);
			Fx.particle(level, Fx.dust(RED, 1.0F), target.getX(), target.getY() + 1, target.getZ(), 15, 0.3, 0.5, 0.3, 0);
		}
		cleanup();
	}

	/**
	 * 시전자 왼손에서 머리 쪽으로 0.5칸씩 걸으며 링크를 폅니다. 머리에 0.3칸까지 닿으면 멈춥니다.
	 * 거리가 줄면 펴지는 링크 수도 저절로 줄어듭니다 (끌려올수록 사슬이 걷힘).
	 */
	private void drawLinks(Vec3 headPos) {
		Vec3 hand = leftHand();
		Vec3 dir = headPos.subtract(hand);
		double len = dir.length();
		float[] yp = Local.yawPitch(dir);
		for (int i = 0; i < links.size(); i++) {
			Display.BlockDisplay l = links.get(i);
			double d = i * 0.5;
			if (len - d > 0.3) {
				Vec3 pos = hand.add(dir.scale(d / Math.max(len, 1.0E-4)));
				Displays.move(l, pos, yp[0], yp[1]);
				l.setTransformation(shownLink());
			} else {
				l.setTransformation(hiddenLink());
			}
		}
	}

	private static Transformation shownLink() {
		return Displays.transform(-0.5F, -0.5F, 0F, 1F, 1F, 0.56F, null, null);
	}

	private static Transformation hiddenLink() {
		return Displays.transform(-0.5F, -0.5F, 0F, 0F, 0F, 0F, null, null);
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public Object owner() {
		return caster;
	}

	void cleanup() {
		if (phase == Phase.WINDUP) {
			Attachments.combatant(caster).casting = false;
		}
		if (!ended) {
			ended = true;
			SkillAnimPayload.stop(caster, SkillAnimPayload.CHAIN);
		}
		if (hook != null) {
			hook.discard();
			hook = null;
		}
		links.forEach(Display.BlockDisplay::discard);
		links.clear();
		if (state.chain == this) {
			state.chain = null;
		}
		phase = Phase.PULL;
		target = null;
	}
}
