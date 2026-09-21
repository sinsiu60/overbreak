package kr.overbreak.classes.gunslinger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.net.TrailPayload;
import kr.overbreak.net.TrailPhasePayload;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [Q] 궤적 해방 — 궤적의 깃털 궁극기 (0.2e, 차원 회전 포격을 대체).
 *
 *   발동 즉시 (시전 동작 없음) 5초 동안 평타가 탄창 무한 · 재장전 없음이 되고, 쏜 총알마다 그 궤적이 하늘색 선으로 공중에 남습니다
 *   (평타 1발당 1줄 · 최대 25줄). 궤적은 총구 → 첫 적중 지점 / 벽 / 16칸 중 가장 가까운 곳이며 월드에 고정됩니다
 *   공중(체공 훈풍의 공중 판정)에서 쏜 궤적은 치명 궤적입니다 (굵고 밝음)
 *   평타 피해 · 치명타 · 이동기 쿨타임 환급은 평소 그대로 — 궁극기는 궤적과 무한 탄창만 더합니다
 *   반동 도약은 궁극기 동안 쿨타임 1.5초
 *
 *   5초가 지나거나, 발동 1초 뒤부터 Q 를 다시 누르면 → 0.5초 예고 (사격 불가 · 이동 · 도약 · 앵커 가능)
 *   → 모든 궤적이 동시에 폭발: 궤적 반경 1칸 캡슐 안의 적에게 줄당 12 (치명 궤적 18), 여러 줄은 누적 · 적 1명당 최대 120,
 *     적마다 피해는 한 번에 몰아서. 지연 보상(되감기) 없음 · 벽 무시 · 넉백 없음 · 아군과 본인은 피해 없음
 *
 *   시전자가 죽으면 (수집 · 예고 어느 때든) 폭발 없이 궤적이 흘러내리며 사라집니다 — 핵심 대응 수단
 *   기절 · 에어본은 끊지 않습니다 (시간은 그대로 흐름, 기절 중에는 못 쏘니 궤적만 못 늘어남)
 *   궁극기 중에는 게이지가 차지 않습니다 (평타 · 폭발 모두)
 */
public final class TrailRelease implements Effects.Active {
	/** 수집 시간 (시간 단위 · 5초). */
	public static final int DURATION = 100;
	/** 조기 해방이 되는 시각 (시간 단위 · 1초). */
	public static final int EARLY = 20;
	/** 예고 (시간 단위 · 0.5초). */
	public static final int TELEGRAPH = 10;
	public static final int MAX_TRAILS = 25;
	/** 궁극기 동안 반동 도약 쿨타임 (시간 단위 · 1.5초). */
	public static final int BOOST_COOLDOWN = 30;
	/** 궤적 최대 길이 (칸) — 평타 사거리와 같음. */
	public static final double MAX_LENGTH = 16.0;
	/** 폭발 판정 반경 (칸). */
	public static final double HIT_RADIUS = 1.0;
	/** 줄당 피해 x100 (12.0) · 치명 궤적 배율 (%) · 적 1명당 상한 x100 (120.0). */
	public static final int DAMAGE_100 = 1200;
	public static final int CRIT_PERCENT = 150;
	public static final int CAP_100 = 12000;

	/** 궤적 한 줄 (월드 좌표 고정). */
	public record Trail(Vec3 start, Vec3 end, boolean crit, int born) {}

	private final ServerPlayer caster;
	private final GunslingerState state;
	private final List<Trail> trails = new ArrayList<>();
	/** 발동 뒤 지난 틱 · 예고를 시작한 틱 (-1 = 아직 수집 중). */
	private int t;
	private int telegraphAt = -1;
	private boolean done;

	private TrailRelease(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.release != null) {
			return;
		}
		UltGauge.consume(p);
		// 재장전 중이었으면 즉시 끝낸 것으로 (탄창 가득) — 궁극기 동안은 재장전이 없습니다
		if (st.reloadT > 0) {
			DualPistols.cancelReload(p, st);
			st.ammo = DualPistols.MAG;
		}
		TrailRelease r = new TrailRelease(p, st);
		st.release = r;
		TrailPhasePayload.broadcast(p, TrailPhasePayload.COLLECT);
		castFx(p);
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("궤적 해방", ChatFormatting.AQUA), Component.empty(), 0, 30, 10);
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

	// ── 상태 ─────────────────────────────────────────────

	/** 궤적을 모으는 중인가 (사격 가능 · 무한 탄창 · 도약 쿨타임 1.5초). */
	public boolean collecting() {
		return telegraphAt < 0 && !done;
	}

	public int trailCount() {
		return trails.size();
	}

	public int critCount() {
		return (int) trails.stream().filter(Trail::crit).count();
	}

	public List<Trail> trails() {
		return trails;
	}

	/** 남은 시간 퍼센트 — 수집은 5초, 예고는 0.5초 기준. */
	public int remainingPercent() {
		if (telegraphAt >= 0) {
			int left = Ticks.of(TELEGRAPH) - (t - telegraphAt);
			return Mth.clamp(left * 100 / Math.max(1, Ticks.of(TELEGRAPH)), 0, 100);
		}
		return Mth.clamp((Ticks.of(DURATION) - t) * 100 / Math.max(1, Ticks.of(DURATION)), 0, 100);
	}

	/** 발동 뒤 지난 시간 (시간 단위). */
	public int elapsedTime() {
		return Ticks.toTime(t);
	}

	// ── 입력 ─────────────────────────────────────────────

	/** 평타 한 발이 나간 순간 (서버 히트스캔 결과) — 수집 중이고 25줄 전이면 궤적 한 줄. */
	void onShot(Vec3 muzzle, Vec3 end, boolean crit) {
		if (!collecting() || trails.size() >= MAX_TRAILS) {
			return;
		}
		Vec3 d = end.subtract(muzzle);
		if (d.length() > MAX_LENGTH) {
			end = muzzle.add(d.normalize().scale(MAX_LENGTH));
		}
		Trail tr = new Trail(muzzle, end, crit, t);
		trails.add(tr);
		TrailPayload.broadcast(caster, new TrailPayload(caster.getId(), trails.size() - 1, muzzle, end, crit, 0));
	}

	/** Q 재입력 — 발동 1초 뒤부터 곧장 예고로. 그 전에는 무시. */
	void requestRelease() {
		if (collecting() && t >= Ticks.of(EARLY)) {
			startTelegraph();
		}
	}

	private void startTelegraph() {
		telegraphAt = t;
		TrailPhasePayload.broadcast(caster, TrailPhasePayload.TELEGRAPH);
		// 3인칭: 두 총을 손가락으로 한 바퀴 돌림
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.GS_RELEASE_TWIRL, -1, TELEGRAPH);
	}

	// ── 진행 ─────────────────────────────────────────────

	@Override
	public boolean tick() {
		if (done) {
			return false;
		}
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			vanish();
			return false;
		}
		t++;
		if (collecting()) {
			if (t >= Ticks.of(DURATION)) {
				startTelegraph();
			} else if (Ticks.ambient()) {
				// 두 총이 하늘색으로 빛남 — 손 언저리에 옅은 빛
				Vec3 hands = caster.getEyePosition().subtract(0, 0.45, 0);
				Fx.particleExcept(caster.level(), caster, Fx.dust(Fx.rgb(0.55, 0.85, 1.0), 0.6F), hands.x, hands.y, hands.z, 2, 0.35, 0.15, 0.35, 0);
			}
			return true;
		}
		if (t - telegraphAt >= Ticks.of(TELEGRAPH)) {
			detonate();
			return false;
		}
		return true;
	}

	/** 폭발 — 모든 궤적을 동시에 판정, 적마다 누적(상한 120)해 한 번에. */
	private void detonate() {
		done = true;
		TrailPhasePayload.broadcast(caster, TrailPhasePayload.DETONATE);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.GS_RELEASE_SNAP, -1);
		// 궁극기 상태는 피해를 다 넣은 뒤에 풉니다 — 폭발 피해로 게이지가 차지 않게
		try {
			explode();
		} finally {
			if (state.release == this) {
				state.release = null;
			}
		}
	}

	private void explode() {
		if (trails.isEmpty()) {
			return;
		}
		ServerLevel level = caster.level();
		Map<LivingEntity, int[]> sum = damage(level, caster, trails);
		for (Map.Entry<LivingEntity, int[]> en : sum.entrySet()) {
			LivingEntity e = en.getKey();
			int total = Math.min(CAP_100, en.getValue()[0]);
			HitPayload.critNext = en.getValue()[1] > 0;
			try {
				SkillDamage.dealFine(e, caster, total, SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			// 피해에 비례한 하늘색 타격 섬광
			int n = 4 + total / 1000;
			Fx.particle(level, Fx.dust(Fx.rgb(0.6, 0.9, 1.0), 1.4F), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), n, 0.3, 0.4, 0.3, 0);
			Fx.particle(level, ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), n / 2, 0.2, 0.3, 0.2, 0.08);
		}
	}

	/**
	 * 궤적 폭발 피해 합산 (x100) — 적마다 {합, 치명 궤적 수}. 되감기 없이 지금 히트박스로, 벽은 보지 않습니다.
	 * 시험에서 직접 부릅니다.
	 */
	public static Map<LivingEntity, int[]> damage(ServerLevel level, ServerPlayer caster, List<Trail> trails) {
		AABB all = null;
		for (Trail tr : trails) {
			AABB b = new AABB(tr.start, tr.end);
			all = all == null ? b : all.minmax(b);
		}
		Map<LivingEntity, int[]> sum = new HashMap<>();
		if (all == null) {
			return sum;
		}
		all = all.inflate(HIT_RADIUS + 2.0);
		Vec3 center = all.getCenter();
		double radius = Math.sqrt(all.getXsize() * all.getXsize() + all.getYsize() * all.getYsize() + all.getZsize() * all.getZsize()) / 2.0;
		for (LivingEntity e : Targets.enemies(level, center, radius, caster)) {
			AABB box = e.getBoundingBox();
			if (!box.intersects(all)) {
				continue;
			}
			int total = 0;
			int crits = 0;
			for (Trail tr : trails) {
				if (segmentBoxDistance(tr.start, tr.end, box) <= HIT_RADIUS) {
					total += tr.crit ? DAMAGE_100 * CRIT_PERCENT / 100 : DAMAGE_100;
					if (tr.crit) {
						crits++;
					}
				}
			}
			if (total > 0) {
				sum.put(e, new int[] {total, crits});
			}
		}
		return sum;
	}

	/**
	 * 선분과 상자(AABB) 사이의 최단 거리 — 캡슐(선분 + 반지름) 교차 판정용.
	 * 선분 위 점에서 상자까지의 거리는 매개변수에 대해 볼록하므로 삼분 탐색으로 최솟값을 찾습니다.
	 */
	public static double segmentBoxDistance(Vec3 a, Vec3 b, AABB box) {
		double lo = 0.0;
		double hi = 1.0;
		for (int i = 0; i < 40; i++) {
			double m1 = lo + (hi - lo) / 3.0;
			double m2 = hi - (hi - lo) / 3.0;
			if (pointBox(a.lerp(b, m1), box) < pointBox(a.lerp(b, m2), box)) {
				hi = m2;
			} else {
				lo = m1;
			}
		}
		return pointBox(a.lerp(b, (lo + hi) / 2.0), box);
	}

	private static double pointBox(Vec3 p, AABB box) {
		double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** 사망 · 해제 — 폭발 없이 흘러내리며 사라짐. */
	private void vanish() {
		if (done) {
			return;
		}
		done = true;
		if (state.release == this) {
			state.release = null;
		}
		TrailPhasePayload.broadcast(caster, TrailPhasePayload.VANISH);
	}

	/** 새로 보기 시작한 사람에게 지금 궤적 전체와 단계를 한 번에. */
	void sendTo(ServerPlayer viewer) {
		TrailPhasePayload.sendTo(viewer, new TrailPhasePayload(caster.getId(), TrailPhasePayload.COLLECT, Ticks.toTime(t)));
		for (int i = 0; i < trails.size(); i++) {
			Trail tr = trails.get(i);
			TrailPayload.sendTo(viewer, new TrailPayload(caster.getId(), i, tr.start, tr.end, tr.crit, Ticks.toTime(t - tr.born)));
		}
		if (telegraphAt >= 0) {
			TrailPhasePayload.sendTo(viewer, new TrailPhasePayload(caster.getId(), TrailPhasePayload.TELEGRAPH, Ticks.toTime(t - telegraphAt)));
		}
	}

	/** 시험용: 지금 곧장 예고로 (5초를 기다리지 않음). */
	public void forceTelegraph() {
		if (collecting()) {
			startTelegraph();
		}
	}

	@Override
	public void cancel() {
		vanish();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
