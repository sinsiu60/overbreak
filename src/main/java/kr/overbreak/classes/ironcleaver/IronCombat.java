package kr.overbreak.classes.ironcleaver;

import static kr.overbreak.classes.ironcleaver.IronSpec.*;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.tick.GameClock;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.input.InputTiming;
import kr.overbreak.net.IronPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 참철 동작 — LMB 판별(평타 · 모아 베기) · 어깨 박치기 · 검막 · 대지 가르기 · 천참을 한 상태 기계로.
 *
 *   LMB 누름 → 지금 콤보 타수의 선딜 시작. 0.25초 안에 떼면 그대로 휘두르기, 계속 누르고 있으면 모아 베기로 이어짐 (단계는 누른 순간부터)
 *   모으기 1단(0.6초) 전에 떼면 지금 타수의 휘두르기가 곧바로 판정 (선딜은 이미 지남)
 *   누름 · 뗌 시각은 클라이언트가 잰 값(서브틱)을 서버가 본 구간 ±0.15초 안으로 잘라 씁니다 (진 참 0.2초 창이 핑에 밀리지 않게)
 *   후딜 중 누른 입력은 저장했다가 후딜이 끝나는 즉시 다음 타 · 마지막 동작 뒤 1.2초 입력이 없으면 1타로
 *
 * 동작 중 이동속도 · 넉백 저항 · 점프는 속성 수정자로 걸고 풉니다. 모든 판정 · 피해는 서버, 연출은 클라이언트가 신호를 받아 합니다.
 */
public final class IronCombat {
	private static final Identifier SPEED = kr.overbreak.Overbreak.id("ironcleaver_action_speed");
	private static final Identifier KNOCK = kr.overbreak.Overbreak.id("ironcleaver_weight");
	private static final Identifier JUMP = kr.overbreak.Overbreak.id("ironcleaver_no_jump");

	private IronCombat() {}

	// ── 매 틱 ────────────────────────────────────────────

	static void tick(ServerPlayer p, IronState st) {
		Combatant c = Attachments.combatant(p);
		if (st.rewardT > 0) {
			st.rewardT--;
		}
		// 기절 · 에어본 — 하던 동작을 끊음 (모으기면 아무것도 나가지 않음). 천참 모으기는 면역이라 여기 오지 않음
		if (c.hardCc() && st.phase != IronState.Phase.IDLE && st.phase != IronState.Phase.ULT) {
			interrupt(p, st);
		}
		pollPress(p, st);
		st.t++;
		switch (st.phase) {
			case IDLE -> {
				st.idleT++;
				if (st.idleT >= Ticks.of(COMBO_RESET)) {
					st.combo = 0;
				}
			}
			case SWING -> swingTick(p, st);
			case CHARGE -> chargeTick(p, st);
			case RELEASE -> releaseTick(p, st);
			case BASH -> bashTick(p, st);
			case GUARD -> guardTick(p, st);
			case REND -> rendTick(p, st);
			case ULT -> ultTick(p, st);
		}
		applyMods(p, st);
	}

	/** 새 누름이 들어왔는가 (클라이언트가 보낸 LMB 누름 시각이 바뀜). */
	private static void pollPress(ServerPlayer p, IronState st) {
		double press = InputTiming.pressedAt(p, 0);
		if (Double.isNaN(press) || press == st.lastPress) {
			return;
		}
		st.lastPress = press;
		if (Attachments.combatant(p).hardCc()) {
			return;
		}
		switch (st.phase) {
			case IDLE -> startSwing(p, st, st.combo, press, true);
			case SWING, RELEASE -> st.buffered = true;
			case GUARD -> {
				// 검막 중 LMB — 막기를 끝내고 곧바로 모으기
				endGuard(p, st);
				startCharge(p, st, press);
			}
			default -> {
			}
		}
	}

	/** 기절 · 에어본 · 직업 해제 — 하던 것을 모두 멈춤. */
	static void interrupt(ServerPlayer p, IronState st) {
		if (st.phase == IronState.Phase.CHARGE) {
			SkillAnimPayload.stop(p, SkillAnimPayload.IC_CHARGE);
			stage(p, st, 0, false);
		}
		if (st.phase == IronState.Phase.BASH) {
			Motion.brake(p);
		}
		if (st.phase == IronState.Phase.GUARD) {
			SkillAnimPayload.stop(p, SkillAnimPayload.IC_GUARD);
		}
		toIdle(st);
		applyMods(p, st);
	}

	private static void toIdle(IronState st) {
		st.phase = IronState.Phase.IDLE;
		st.t = 0;
		st.idleT = 0;
	}

	// ── 평타 ─────────────────────────────────────────────

	static void startSwing(ServerPlayer p, IronState st, int k, double pressTick, boolean fresh) {
		startSwing(p, st, k, pressTick, fresh, true);
	}

	private static void startSwing(ServerPlayer p, IronState st, int k, double pressTick, boolean fresh, boolean broadcast) {
		st.phase = IronState.Phase.SWING;
		st.t = 0;
		st.swing = k;
		st.hit.clear();
		st.anyHit = false;
		st.buffered = false;
		st.pressFresh = fresh;
		st.pressTick = pressTick;
		if (broadcast) {
			Swing s = SWINGS[k];
			SkillAnimPayload.broadcast(p, s.anim(), -1, (int) Math.round(s.total()));
		}
	}

	private static void swingTick(ServerPlayer p, IronState st) {
		Swing s = SWINGS[st.swing];
		int windup = Ticks.of(s.windup());
		int active = Math.max(1, Ticks.of(s.active()));
		int total = Ticks.of(s.total());
		// 선딜 중 계속 누르고 있으면 모아 베기로 (누른 순간부터 0.25초)
		if (st.t <= windup && st.pressFresh && InputTiming.down(p, 0)
				&& heldUnits(p, st.pressTick) >= HOLD) {
			startCharge(p, st, st.pressTick);
			return;
		}
		if (st.t > windup && st.t <= windup + active) {
			strikeSwing(p, st, s, (st.t - windup) / (double) active);
		}
		if (st.t >= total) {
			finishAction(p, st, (st.swing + 1) % SWINGS.length);
		}
	}

	/** 동작이 끝남 — 저장된 입력이 있으면 곧바로 다음 타. */
	private static void finishAction(ServerPlayer p, IronState st, int nextCombo) {
		st.combo = nextCombo;
		boolean buffered = st.buffered;
		toIdle(st);
		if (buffered) {
			double press = InputTiming.pressedAt(p, 0);
			startSwing(p, st, st.combo, press, InputTiming.down(p, 0));
		}
	}

	/**
	 * 휘두르기 판정 — 가로 베기는 판정 시간 동안 시작 각도에서 끝 각도로 쓸고 지나가며 그 사이에 든 적을 한 번씩,
	 * 내려찍기는 판정 첫 순간 직선 전체를 한 번.
	 */
	private static void strikeSwing(ServerPlayer p, IronState st, Swing s, double progress) {
		float yaw = p.getYRot();
		boolean line = s.arc() <= 0.0;
		// 내려찍기는 판정 첫 순간 한 번만 (끝났다는 표시로 시전자 자신을 넣어 둠)
		if (line && st.hit.contains(p)) {
			return;
		}
		double half = s.arc() / 2.0;
		double start = s.rightToLeft() ? half : -half;
		double end = -start;
		double cur = start + (end - start) * Math.min(1.0, progress);
		double lo = Math.min(start, cur);
		double hi = Math.max(start, cur);
		for (LivingEntity e : IronStrikes.around(p, s.range())) {
			if (st.hit.contains(e) || !IronStrikes.inHeight(p, e, SWING_LOW, SWING_HIGH) || !IronStrikes.sees(p, e)) {
				continue;
			}
			boolean in;
			if (line) {
				in = IronStrikes.inLine(p, yaw, e, s.range(), s.width());
			} else {
				double a = IronStrikes.relAngle(p, yaw, e);
				in = IronStrikes.reach(p, e) <= s.range() && a >= lo - 8.0 && a <= hi + 8.0;
			}
			if (!in) {
				continue;
			}
			st.hit.add(e);
			IronStrikes.deal(p, e, s.damage100(), false, st.swing == 2 ? 1 : 0);
			Fx.particle(p.level(), ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 1, 0, 0, 0, 0);
			if (!st.anyHit) {
				st.anyHit = true;
				IronStrikes.confirm(p, st.swing == 2 ? 1 : 0);
			}
		}
		if (line) {
			// 내려찍기는 한 번만 — 맞힌 적이 없어도 판정은 끝
			st.hit.add(p);
			ServerLevel level = p.level();
			Vec3 f = forward(yaw).scale(2.0);
			Fx.particle(level, ParticleTypes.CLOUD, p.getX() + f.x, p.getY() + 0.1, p.getZ() + f.z, 10, 0.5, 0.05, 0.5, 0.03);
		}
	}

	// ── 참 모으기 ────────────────────────────────────────

	static void startCharge(ServerPlayer p, IronState st, double pressTick) {
		st.phase = IronState.Phase.CHARGE;
		st.t = 0;
		st.pressTick = pressTick;
		st.paused = 0.0;
		// 검막 성공 보상 — 2단(1.2초 경과)부터
		st.bonus = st.rewardT > 0 ? STAGE_AT[1] : 0.0;
		st.rewardT = 0;
		st.stage = 0;
		st.perfect = false;
		double elapsed = chargeUnits(p, st);
		SkillAnimPayload.broadcastAt(p, SkillAnimPayload.IC_CHARGE, 200, (int) Math.floor(elapsed));
		int stage = stageOf(elapsed);
		if (stage > 0) {
			stage(p, st, stage, false);
		}
	}

	/** 누른 순간부터 지난 시간 (시간 단위) — 박치기로 멈춘 만큼 빼고 검막 보상만큼 더함. */
	static double chargeUnits(ServerPlayer p, IronState st) {
		return heldUnits(p, st.pressTick) - st.paused + st.bonus;
	}

	private static double heldUnits(ServerPlayer p, double pressTick) {
		return Math.max(0.0, GameClock.now() - pressTick);
	}

	/** 모은 시간 → 단계 (0 = 1단 전). */
	public static int stageOf(double units) {
		int s = 0;
		for (int i = 0; i < STAGE_AT.length; i++) {
			if (units >= STAGE_AT[i]) {
				s = i + 1;
			}
		}
		return s;
	}

	/** 진 참 창 (3단 도달 뒤 0.2초 안). */
	public static boolean perfectWindow(double units) {
		return units >= STAGE_AT[2] && units < STAGE_AT[2] + PERFECT;
	}

	private static void chargeTick(ServerPlayer p, IronState st) {
		double units = chargeUnits(p, st);
		int stage = stageOf(units);
		boolean perfect = perfectWindow(units);
		if (stage != st.stage || perfect != st.perfect) {
			stage(p, st, stage, perfect);
		}
		if (!InputTiming.down(p, 0)) {
			// 뗀 순간 — 클라이언트가 잰 누른 시간으로 단계를 정함
			double held = InputTiming.clientHeldUnits(p, 0, HOLD_TOLERANCE) - st.paused + st.bonus;
			int s = stageOf(held);
			if (s == 0) {
				// 1단 전 — 지금 타수의 휘두르기를 곧바로 판정 (선딜은 이미 지남)
				SkillAnimPayload.stop(p, SkillAnimPayload.IC_CHARGE);
				stage(p, st, 0, false);
				Swing sw = SWINGS[st.combo];
				startSwing(p, st, st.combo, st.pressTick, false, false);
				st.t = Ticks.of(sw.windup());
				SkillAnimPayload.broadcastAt(p, sw.anim(), (int) Math.round(sw.total()), (int) Math.round(sw.windup()));
				return;
			}
			release(p, st, perfectWindow(held) ? 4 : s);
			return;
		}
		if (units >= STAGE_AT[2] + AUTO_AFTER) {
			release(p, st, 3);
		}
	}

	/** 단계가 바뀜 — 모두에게 알림 (오오라 색 · 연출). */
	private static void stage(ServerPlayer p, IronState st, int stage, boolean perfect) {
		st.stage = stage;
		st.perfect = perfect;
		IronPayload.broadcast(p, IronPayload.of(IronPayload.STAGE, p, stage, perfect ? 1 : 0));
	}

	/** 모아 베기 — 앞 60° 부채꼴 전체를 한 번 (판정 0.12초 첫 순간). */
	private static void release(ServerPlayer p, IronState st, int stage) {
		SkillAnimPayload.stop(p, SkillAnimPayload.IC_CHARGE);
		st.phase = IronState.Phase.RELEASE;
		st.t = 0;
		st.releaseStage = stage;
		st.hit.clear();
		st.anyHit = false;
		int i = stage - 1;
		IronPayload.broadcast(p, IronPayload.of(IronPayload.STAGE, p, Math.min(3, stage), stage == 4 ? 1 : 0));
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IC_RELEASE, -1, (int) Math.round(RELEASE_ACTIVE + RELEASE_RECOVERY[i]));
		float yaw = p.getYRot();
		boolean guardBreak = stage >= GUARD_BREAK_STAGE;
		for (LivingEntity e : IronStrikes.around(p, RELEASE_RANGE[i])) {
			if (!IronStrikes.inHeight(p, e, SWING_LOW, RELEASE_HIGH) || !IronStrikes.sees(p, e)
					|| IronStrikes.reach(p, e) > RELEASE_RANGE[i] || Math.abs(IronStrikes.relAngle(p, yaw, e)) > RELEASE_ARC / 2.0 + 6.0) {
				continue;
			}
			st.hit.add(e);
			IronStrikes.deal(p, e, RELEASE_DAMAGE[i], guardBreak, 1 + stage);
			st.anyHit = true;
		}
		if (st.anyHit) {
			IronStrikes.confirm(p, 1 + stage);
		}
		IronPayload.broadcast(p, IronPayload.of(IronPayload.CLEAVE, p, stage, 0));
		st.combo = 0;
	}

	private static void releaseTick(ServerPlayer p, IronState st) {
		int i = st.releaseStage - 1;
		if (st.t >= Ticks.of(RELEASE_ACTIVE + RELEASE_RECOVERY[i])) {
			stage(p, st, 0, false);
			finishAction(p, st, 0);
		}
	}

	// ── RMB 어깨 박치기 ──────────────────────────────────

	static void bash(ServerPlayer p, IronState st) {
		if (st.phase != IronState.Phase.IDLE && st.phase != IronState.Phase.CHARGE) {
			return;
		}
		if (Attachments.combatant(p).sealT > 0) {
			sealed(p);
			return;
		}
		if (Cooldowns.blocked(p, Ironcleaver.BASH, "어깨 박치기", ChatFormatting.GRAY)) {
			return;
		}
		Attachments.profile(p).setCooldown(Ironcleaver.BASH, BASH_COOLDOWN);
		st.bashFromCharge = st.phase == IronState.Phase.CHARGE;
		st.bashPauseStart = GameClock.now();
		st.phase = IronState.Phase.BASH;
		st.bashT = 0;
		st.hit.clear();
		Vec3 dir = forward(p.getYRot());
		st.bashDir = dir;
		Motion.dash(p, dir.x, dir.z, BASH_DISTANCE / BASH_TIME, BASH_TIME, true);
		Attachments.combatant(p).dashT += 1;
		CrowdControl.track(p);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IC_BASH, -1, BASH_TIME + 2);
	}

	private static void bashTick(ServerPlayer p, IronState st) {
		st.bashT++;
		// 처음 닿은 적 한 명 — 20 · 돌진 방향으로 2칸 밀어냄 · 돌진 끝
		LivingEntity first = null;
		for (LivingEntity e : IronStrikes.around(p, 2.0)) {
			if (e.getBoundingBox().intersects(p.getBoundingBox().inflate(0.45).move(st.bashDir.scale(0.3)))) {
				first = e;
				break;
			}
		}
		boolean end = st.bashT >= Ticks.of(BASH_TIME) + 1 || (st.bashT > 1 && p.horizontalCollision);
		if (first != null) {
			IronStrikes.deal(p, first, BASH_DAMAGE, false, 6);
			CrowdControl.push(first, st.bashDir.x, st.bashDir.z, BASH_PUSH, 4);
			IronStrikes.confirm(p, 6);
			IronPayload.broadcast(p, new IronPayload(IronPayload.BASH, p.getId(), 0, 0, first.getX(), first.getY() + first.getBbHeight() * 0.6,
					first.getZ(), p.getYRot()));
			end = true;
		}
		if (end) {
			Attachments.combatant(p).dashT = 0;
			Attachments.combatant(p).dashHold = false;
			Motion.brake(p);
			if (st.bashFromCharge) {
				// 모으던 중이었으면 그대로 모으기로 — 돌진한 시간만큼 모으기 시계는 멈춰 있었음
				st.paused += GameClock.now() - st.bashPauseStart;
				st.phase = IronState.Phase.CHARGE;
			} else {
				toIdle(st);
			}
		}
	}

	// ── SHIFT 검막 ───────────────────────────────────────

	static void guard(ServerPlayer p, IronState st) {
		if (st.phase != IronState.Phase.IDLE) {
			return;
		}
		if (Cooldowns.blocked(p, Ironcleaver.GUARD, "검막", ChatFormatting.GRAY)) {
			return;
		}
		Attachments.profile(p).setCooldown(Ironcleaver.GUARD, GUARD_COOLDOWN);
		st.phase = IronState.Phase.GUARD;
		st.t = 0;
		st.guardYaw = p.getYRot();
		st.guardSuccess = false;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IC_GUARD, -1, GUARD_TIME);
		applyMods(p, st);
	}

	private static void guardTick(ServerPlayer p, IronState st) {
		st.guardYaw = p.getYRot();
		if (st.t >= Ticks.of(GUARD_TIME)) {
			endGuard(p, st);
		}
	}

	private static void endGuard(ServerPlayer p, IronState st) {
		if (st.guardSuccess) {
			st.rewardT = Ticks.of(GUARD_REWARD);
		}
		SkillAnimPayload.stop(p, SkillAnimPayload.IC_GUARD);
		toIdle(st);
	}

	/** 검막 중 앞 120° 에서 온 피해인가. */
	static boolean guardFront(ServerPlayer p, IronState st, Vec3 from) {
		double dx = from.x - p.getX();
		double dz = from.z - p.getZ();
		double a = Math.toDegrees(Math.atan2(-dx, dz));
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - st.guardYaw)) <= GUARD_FRONT / 2.0;
	}

	// ── E 대지 가르기 ────────────────────────────────────

	static void rend(ServerPlayer p, IronState st) {
		if (st.phase != IronState.Phase.IDLE) {
			return;
		}
		if (GroundWave.ground(p.level(), p.position(), REND_GROUND_REACH) == null) {
			// 공중 — 발밑 3칸 안에 땅이 없으면 쓸 수 없음 (쿨타임 안 씀)
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("발밑에 땅이 없어 대지를 가를 수 없다", ChatFormatting.GRAY));
			return;
		}
		if (Cooldowns.blocked(p, Ironcleaver.REND, "대지 가르기", ChatFormatting.GRAY)) {
			return;
		}
		Attachments.profile(p).setCooldown(Ironcleaver.REND, REND_COOLDOWN);
		st.phase = IronState.Phase.REND;
		st.t = 0;
		st.fired = false;
		st.castDir = forward(p.getYRot());
		st.castYaw = p.getYRot();
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IC_REND, -1, (int) Math.round(REND_WINDUP + REND_DRAG + REND_RECOVERY));
	}

	private static void rendTick(ServerPlayer p, IronState st) {
		if (!st.fired && st.t >= Ticks.of(REND_WINDUP + REND_DRAG)) {
			st.fired = true;
			Vec3 start = p.position().add(st.castDir.scale(0.8));
			GroundWave w = new GroundWave(p, start, st.castDir);
			Effects.add(w);
			IronPayload.broadcast(p, new IronPayload(IronPayload.REND, p.getId(), 0, 0, start.x, start.y, start.z, st.castYaw));
		}
		if (st.t >= Ticks.of(REND_WINDUP + REND_DRAG + REND_RECOVERY)) {
			toIdle(st);
		}
	}

	// ── Q 천참 ───────────────────────────────────────────

	static void ult(ServerPlayer p, IronState st) {
		if (st.phase == IronState.Phase.ULT) {
			return;
		}
		if (st.phase == IronState.Phase.CHARGE) {
			SkillAnimPayload.stop(p, SkillAnimPayload.IC_CHARGE);
			stage(p, st, 0, false);
		}
		UltGauge.consume(p);
		st.phase = IronState.Phase.ULT;
		st.t = 0;
		st.fired = false;
		st.castYaw = p.getYRot();
		st.castAt = p.position();
		Combatant c = Attachments.combatant(p);
		c.ccImmune = true;
		CrowdControl.cleanse(p);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IC_ULT, -1, (int) Math.round(ULT_CHARGE + ULT_RECOVERY));
		applyMods(p, st);
		IronPayload.broadcast(p, new IronPayload(IronPayload.ULT, p.getId(), 0, 0, st.castAt.x, st.castAt.y, st.castAt.z, st.castYaw));
	}

	private static void ultTick(ServerPlayer p, IronState st) {
		if (!st.fired && st.t >= Ticks.of(ULT_CHARGE)) {
			st.fired = true;
			cleave(p, st);
			Attachments.combatant(p).ccImmune = false;
		}
		if (st.t >= Ticks.of(ULT_CHARGE + ULT_RECOVERY)) {
			Attachments.combatant(p).ccImmune = false;
			toIdle(st);
		}
	}

	/** 천참 판정 — 앞 직선 14칸 × 폭 4칸 · 벽 관통 · 발밑 -1 ~ +4. */
	private static void cleave(ServerPlayer p, IronState st) {
		boolean any = false;
		for (LivingEntity e : IronStrikes.around(p, ULT_LENGTH + 2.0)) {
			if (!IronStrikes.inHeight(p, e, ULT_LOW, ULT_HIGH - p.getBbHeight())) {
				continue;
			}
			if (!IronStrikes.inLine(p, st.castYaw, e, ULT_LENGTH, ULT_WIDTH)) {
				continue;
			}
			IronStrikes.deal(p, e, ULT_DAMAGE, false, 5);
			any = true;
		}
		if (any) {
			IronStrikes.confirm(p, 5);
		}
		IronPayload.broadcast(p, new IronPayload(IronPayload.CLEAVE, p.getId(), 5, 0, st.castAt.x, st.castAt.y, st.castAt.z, st.castYaw));
	}

	// ── 속성 수정자 ──────────────────────────────────────

	/** 지금 동작에 맞춰 이동속도 · 넉백 저항 · 점프를 걸고 풂. */
	static void applyMods(ServerPlayer p, IronState st) {
		double speed = switch (st.phase) {
			case SWING, RELEASE -> SWING_SPEED;
			case CHARGE -> CHARGE_SPEED;
			case REND -> REND_CAST_SPEED;
			case GUARD, ULT -> -1.0;
			default -> 0.0;
		};
		double knock = switch (st.phase) {
			case IDLE -> 0.0;
			case CHARGE -> st.stage >= 2 ? 1.0 : ACTION_KB_RESIST;
			case ULT -> 1.0;
			default -> ACTION_KB_RESIST;
		};
		boolean noJump = st.phase == IronState.Phase.CHARGE || st.phase == IronState.Phase.GUARD || st.phase == IronState.Phase.ULT;
		set(p, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(p, Attributes.KNOCKBACK_RESISTANCE, KNOCK, knock, AttributeModifier.Operation.ADD_VALUE);
		set(p, Attributes.JUMP_STRENGTH, JUMP, noJump ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void set(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, Identifier id,
			double amount, AttributeModifier.Operation op) {
		var inst = p.getAttribute(attr);
		if (inst == null) {
			return;
		}
		var cur = inst.getModifier(id);
		if (amount == 0.0) {
			if (cur != null) {
				inst.removeModifier(id);
			}
			return;
		}
		if (cur == null || cur.amount() != amount) {
			inst.removeModifier(id);
			inst.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	static void clearMods(ServerPlayer p) {
		IronState none = new IronState();
		applyMods(p, none);
	}

	// ── 공통 ─────────────────────────────────────────────

	static Vec3 forward(float yaw) {
		double y = Math.toRadians(yaw);
		return new Vec3(-Math.sin(y), 0, Math.cos(y));
	}

	private static void sealed(ServerPlayer p) {
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(p).msgT = 30;
		Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
	}

	/** 모으기 게이지 퍼센트 (2.0초 = 100) — HUD. */
	static int chargePercent(ServerPlayer p, IronState st) {
		if (st.phase != IronState.Phase.CHARGE) {
			return -1;
		}
		return (int) Math.min(100, chargeUnits(p, st) * 100 / (STAGE_AT[2] + PERFECT));
	}
}
