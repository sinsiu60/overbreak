package kr.overbreak.classes.gunslinger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.HomingPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [Q] 궤적 추격 — 궤적의 깃털 궁극기 (0.2f, 궤적 해방을 대체).
 *
 *   6초 동안 자유 비행: 중력 없음 · 이동키로 초당 10칸까지 (클라이언트 공중 제어의 비행 규칙) · 점프 키로 초당 5칸 상승,
 *   떼면 초당 1칸씩 가라앉음. 땅에 닿아도 끝나지 않음. 비행 중에는 늘 공중 판정 (평타는 모두 치명타)
 *   비행 중 반동 도약 · 사선 앵커 · 돌진 난사 모두 사용 가능 — 추진이 끝나면 비행 제어로 돌아옴
 *
 *   평타는 히트스캔 대신 빠른 유도 탄: 쏘는 순간 조준 12° 안 · 24칸 안 · 시야가 트인 적 중 각도가 가장 작은 적을 잡아
 *   초당 60칸으로 날며 매 틱 그 적의 가슴 쪽으로 초당 최대 720° 까지 방향을 틉니다. 대상이 없으면 직선
 *   블록에 닿으면 소멸 (벽을 돌아 휘지 못함) · 대상이 아닌 적에 먼저 닿으면 그 적에게 명중 · 0.4초(24칸) 안에 못 맞히면 소멸
 *   피해는 평타와 같음 (발당 20, 비행 중이라 늘 치명타 30) · 넉백 없음 · 이동기 쿨타임 환급 그대로
 *
 *   탄창 무한 · 재장전 없음 (R 무시) · 시작할 때 재장전 중이면 즉시 완료 · 끝나면 시작 전 잔탄 그대로
 *   기절 · 에어본을 맞으면 즉시 끝 (그대로 떨어짐) · 죽으면 끝 · 군중 제어 면역 없음 — 떨어뜨리는 것이 대응 수단
 *   궁극기 중에는 게이지가 차지 않습니다
 */
public final class TrailPursuit implements Effects.Active {
	/** 비행 시간 (시간 단위 · 6초). */
	public static final int DURATION = 120;
	/** 유도 원뿔 (도) · 대상 거리 (칸) · 탄 속도 (초당 칸) · 회전 한계 (초당 도) · 탄 수명 (시간 단위 · 0.4초). */
	public static final double CONE = 12.0;
	public static final double RANGE = 24.0;
	public static final double BULLET_SPEED = 60.0;
	public static final double TURN_RATE = 720.0;
	public static final int LIFE = 8;

	/** 날아가는 탄 하나. */
	private static final class Bullet {
		Vec3 pos;
		Vec3 dir;
		final @Nullable LivingEntity target;
		int age;

		Bullet(Vec3 pos, Vec3 dir, @Nullable LivingEntity target) {
			this.pos = pos;
			this.dir = dir;
			this.target = target;
		}
	}

	private final ServerPlayer caster;
	private final GunslingerState state;
	private final List<Bullet> bullets = new ArrayList<>();
	private int t;
	private boolean ended;

	private TrailPursuit(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.pursuit != null) {
			return;
		}
		UltGauge.consume(p);
		if (st.reloadT > 0) {
			DualPistols.cancelReload(p, st);
			st.ammo = DualPistols.MAG;
		}
		AeroDrift.stop(p, st);
		TrailPursuit r = new TrailPursuit(p, st);
		st.pursuit = r;
		p.setNoGravity(true);
		p.resetFallDistance();
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_PURSUIT, -1, DURATION);
		castFx(p);
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("궤적 추격", ChatFormatting.AQUA), Component.empty(), 0, 30, 10);
		}
		Effects.add(r);
	}

	/** 발동 — 하늘색 깃털 20개가 터지듯 흩어지고, 날개 펼치는 소리 + 금속 공명음. */
	private static void castFx(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 c = p.position().add(0, 1.1, 0);
		ItemParticleOption feather = new ItemParticleOption(ParticleTypes.ITEM, Items.FEATHER);
		for (int i = 0; i < 20; i++) {
			double a = i * Math.PI * 2.0 / 20.0;
			double up = (i % 3 - 1) * 0.25;
			Fx.particle(level, feather, c.x, c.y, c.z, 0, Math.cos(a), up + 0.15, Math.sin(a), 0.35);
		}
		Fx.particle(level, Fx.dust(Fx.rgb(0.55, 0.85, 1.0), 1.3F), c, 24, 0.6, 0.5, 0.6, 0);
		Fx.sound(p, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9F, 1.4F);
		Fx.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.6F);
		Fx.sound(p, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.8F);
	}

	/** 비행 중인가 (끝났으면 남은 탄만 날고 있음). */
	public boolean flying() {
		return !ended;
	}

	/** 남은 비행 시간 퍼센트. */
	public int remainingPercent() {
		return Mth.clamp((Ticks.of(DURATION) - t) * 100 / Math.max(1, Ticks.of(DURATION)), 0, 100);
	}

	// ── 유도 탄 ──────────────────────────────────────────

	/** 평타 한 발 — 유도 탄을 쏩니다 (히트스캔 대신). */
	void fire(Vec3 muzzle, Vec3 aim) {
		Vec3 eye = caster.getEyePosition();
		LivingEntity target = pick(caster.level(), caster, eye, aim);
		Vec3 dir = aim.normalize();
		bullets.add(new Bullet(muzzle, dir, target));
		HomingPayload.broadcast(caster, new HomingPayload(caster.getId(), muzzle, dir, target == null ? -1 : target.getId()));
	}

	/** 유도 대상 — 24칸 · 조준 12° 안 · 시야가 트인 적 중 각도가 가장 작은 적 (없으면 null). 시험에서 직접 부릅니다. */
	public static @Nullable LivingEntity pick(ServerLevel level, ServerPlayer caster, Vec3 eye, Vec3 aim) {
		Vec3 look = aim.normalize();
		double cos = Math.cos(Math.toRadians(CONE));
		LivingEntity best = null;
		double bestDot = cos;
		for (LivingEntity e : Targets.enemies(level, eye, RANGE, caster)) {
			Vec3 chest = chest(e);
			Vec3 to = chest.subtract(eye);
			double dist = to.length();
			if (dist > RANGE || dist < 1.0E-3) {
				continue;
			}
			double dot = look.dot(to.scale(1.0 / dist));
			if (dot < bestDot) {
				continue;
			}
			if (level.clip(new ClipContext(eye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getType() != HitResult.Type.MISS) {
				continue;
			}
			best = e;
			bestDot = dot;
		}
		return best;
	}

	static Vec3 chest(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.6, 0);
	}

	/** 탄 한 틱 — 대상 쪽으로 틀고, 블록 · 적에 닿는지. */
	private void stepBullets() {
		ServerLevel level = caster.level();
		double step = Ticks.speed(BULLET_SPEED / 20.0);
		double maxTurn = Math.toRadians(Ticks.speed(TURN_RATE / 20.0));
		Iterator<Bullet> it = bullets.iterator();
		while (it.hasNext()) {
			Bullet b = it.next();
			b.age++;
			if (b.age > Ticks.of(LIFE)) {
				it.remove();
				continue;
			}
			if (b.target != null && b.target.isAlive() && !b.target.isRemoved()) {
				b.dir = turn(b.dir, chest(b.target).subtract(b.pos), maxTurn);
			}
			Vec3 next = b.pos.add(b.dir.scale(step));
			HitResult wall = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
			Vec3 end = wall.getType() == HitResult.Type.MISS ? next : wall.getLocation();
			LivingEntity hit = firstHit(level, b.pos, end);
			if (hit != null) {
				hurt(level, hit);
				it.remove();
				continue;
			}
			if (wall.getType() != HitResult.Type.MISS) {
				it.remove();
				continue;
			}
			b.pos = next;
		}
	}

	/** 방향을 목표 쪽으로 최대 maxTurn(라디안) 만큼 돌림. 시험에서 직접 부릅니다. */
	public static Vec3 turn(Vec3 dir, Vec3 want, double maxTurn) {
		if (want.lengthSqr() < 1.0E-9) {
			return dir;
		}
		Vec3 w = want.normalize();
		double dot = Mth.clamp(dir.dot(w), -1.0, 1.0);
		double angle = Math.acos(dot);
		if (angle <= maxTurn) {
			return w;
		}
		// 두 방향이 이루는 평면에서 maxTurn 만큼만 회전
		Vec3 perp = w.subtract(dir.scale(dot));
		if (perp.lengthSqr() < 1.0E-9) {
			return dir;
		}
		perp = perp.normalize();
		return dir.scale(Math.cos(maxTurn)).add(perp.scale(Math.sin(maxTurn))).normalize();
	}

	/** 선분에 처음 닿는 적 (몸 상자 조금 넉넉히). */
	private @Nullable LivingEntity firstHit(ServerLevel level, Vec3 from, Vec3 to) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB sweep = new AABB(from, to).inflate(1.0);
		for (LivingEntity e : Targets.enemies(level, from.add(to).scale(0.5), from.distanceTo(to) / 2.0 + 2.0, caster)) {
			if (!e.getBoundingBox().intersects(sweep)) {
				continue;
			}
			var hit = e.getBoundingBox().inflate(0.2).clip(from, to);
			if (hit.isPresent()) {
				double d = from.distanceToSqr(hit.get());
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		return best;
	}

	/** 명중 — 평타와 같은 피해, 비행 중이라 늘 치명타 · 넉백 없음 · 이동기 쿨타임 환급. */
	private void hurt(ServerLevel level, LivingEntity e) {
		int damage = DualPistols.DAMAGE_100 * AeroDrift.CRIT_PERCENT / 100;
		HitPayload.critNext = true;
		try {
			SkillDamage.dealFine(e, caster, damage, SkillDamage.Kind.MULTI_NO_KB);
		} finally {
			HitPayload.critNext = false;
		}
		Vec3 c = chest(e);
		Fx.particle(level, Fx.dust(Fx.rgb(0.6, 0.9, 1.0), 1.2F), c, 8, 0.2, 0.25, 0.2, 0);
		Fx.particle(level, ParticleTypes.END_ROD, c, 4, 0.1, 0.1, 0.1, 0.06);
		Fx.sound(e, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.9F, 1.6F);
		AeroDrift.onAirHit(caster);
	}

	// ── 진행 ─────────────────────────────────────────────

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end(true);
			return false;
		}
		if (!ended) {
			t++;
			if (Attachments.combatant(caster).hardCc()) {
				// 기절 · 에어본 — 즉시 끝나고 그대로 떨어짐
				end(true);
			} else if (t >= Ticks.of(DURATION)) {
				end(false);
			} else {
				caster.resetFallDistance();
				if (Ticks.every(t, Ticks.of(2))) {
					// 발밑에 옅은 기류 · 손에 하늘빛
					ServerLevel level = caster.level();
					Fx.particle(level, Fx.dust(Fx.rgb(0.75, 0.93, 1.0), 0.8F), caster.getX(), caster.getY() - 0.1, caster.getZ(), 2, 0.25, 0.05, 0.25, 0);
					Vec3 hands = caster.getEyePosition().subtract(0, 0.45, 0);
					Fx.particleExcept(level, caster, Fx.dust(Fx.rgb(0.55, 0.85, 1.0), 0.6F), hands.x, hands.y, hands.z, 1, 0.3, 0.1, 0.3, 0);
				}
			}
		}
		stepBullets();
		return !ended || !bullets.isEmpty();
	}

	/** 끝 — 중력을 되돌리고 (돌진 난사로 떠 있는 중이면 그쪽이 풂) 짧은 하강음. */
	private void end(boolean interrupted) {
		if (ended) {
			return;
		}
		ended = true;
		if (state.pursuit == this) {
			state.pursuit = null;
		}
		if (state.scatter == null || !state.scatter.hovering()) {
			caster.setNoGravity(false);
		}
		if (interrupted) {
			SkillAnimPayload.stop(caster, SkillAnimPayload.GS_PURSUIT);
		}
		if (caster.isAlive()) {
			Fx.sound(caster, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
			Fx.sound(caster, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6F, 0.8F);
		}
	}

	/** 시험용: 지금 날고 있는 탄 수. */
	public int bulletCount() {
		return bullets.size();
	}

	@Override
	public void cancel() {
		end(true);
		bullets.clear();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
