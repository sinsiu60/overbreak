package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.gunslinger.AeroDrift;
import kr.overbreak.classes.gunslinger.DashScatter;
import kr.overbreak.classes.gunslinger.AerialBombardment;
import kr.overbreak.classes.gunslinger.Gunslinger;
import kr.overbreak.classes.gunslinger.GunslingerState;
import kr.overbreak.classes.gunslinger.RecoilBoost;
import kr.overbreak.classes.gunslinger.WireAnchor;
import kr.overbreak.core.Attachments;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * 궤적의 깃털 (건슬링어) — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 플레이어 목록에 없어 직업 틱이 저절로 돌지 않으므로 직업 틱(tick)만 직접 부릅니다.
 * "공중" 은 {@code onGround()} 로 갈리므로, 공중 판정이 필요한 시험은 바닥에서 띄운 자리에 세웁니다.
 */
public final class GunslingerTest implements CustomTestMethodInvoker {
	/** 표적 가슴 높이를 겨누는 피치 (6칸 앞). */
	private static final float CHEST_PITCH = 6.2F;

	private static final Map<LivingEntity, Map<Entity, Float>> DEALT = new IdentityHashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (source.getEntity() != null) {
				DEALT.computeIfAbsent(target, k -> new IdentityHashMap<>()).merge(source.getEntity(), taken, Float::sum);
			}
		});
	}

	private static float dealtBy(LivingEntity target, Entity attacker) {
		return DEALT.getOrDefault(target, Map.of()).getOrDefault(attacker, 0.0F);
	}

	private static PvpClass gs() {
		return Classes.byId(Gunslinger.ID);
	}

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float pitch) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_gunslinger"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, gs());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(2000);
		return v;
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	/** 직업 틱만 n 시간 단위 (사격 간격 · 재장전 · 활공). */
	private static void classTicks(FakePlayer p, int n) {
		for (int i = 0; i < T.of(n); i++) {
			gs().tick(p);
		}
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 180, 0.01, "최대 체력 (200 - 20)");
		h.assertTrue(!gs().meleeAllowed(), "근접 불가 (좌클릭 = 쌍권총)");
		h.assertTrue(Gunslinger.state(p).ammo == 18, "탄창 18발");
		h.assertTrue(Attachments.profile(p).ultRate == 100, "게이지 피해 1당 1%");
		Classes.clear(p);
		h.succeed();
	}

	/** 낙하 피해는 언제나 무효. */
	@GameTest
	public void fallDamageImmune(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		h.assertTrue(Gunslinger.absorb(p, h.getLevel().damageSources().fall()), "낙하 피해 무효");
		h.assertTrue(!Gunslinger.absorb(p, h.getLevel().damageSources().generic()), "다른 피해는 그대로 받음");
		Classes.clear(p);
		h.succeed();
	}

	/** 땅에서 쏘면 20, 땅에서 2칸 이상 떠서 쏘면 치명타 30. 0.2초에 한 발. */
	@GameTest
	public void pistolDamageAndFireRate(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		p.setOnGround(true);
		gs().basic(p);
		near(h, dealtBy(v, p), 20, 0.01, "땅에서 한 발 20");
		h.assertTrue(Gunslinger.state(p).ammo == 17, "탄 하나 씀");
		gs().basic(p);
		near(h, dealtBy(v, p), 20, 0.01, "0.2초 안에는 다시 안 나감");
		classTicks(p, 4);
		gs().basic(p);
		near(h, dealtBy(v, p), 40, 0.01, "0.2초 뒤 한 발 더");

		// 3칸 위에서 내려다보고 쏨
		FakePlayer air = caster(h, new Vec3(5.5, 3, 0.5), 31.0F);
		Villager v2 = dummy(h, new Vec3(5.5, 0, 6.5));
		air.setOnGround(false);
		gs().basic(air);
		near(h, dealtBy(v2, air), 30, 0.01, "공중 치명타 150% (20 x 1.5)");
		Classes.clear(air);
		Classes.clear(p);
		h.succeed();
	}

	/** 공중 치명타는 발밑으로 2칸이 비어 있어야 합니다 — 제자리 점프(1칸)로는 붙지 않음 (0.2d). */
	@GameTest
	public void airCritNeedsTwoBlocks(GameTestHelper h) {
		FakePlayer low = caster(h, new Vec3(1.5, 1, 1.5), 0.0F);
		low.setOnGround(false);
		h.assertTrue(!AeroDrift.airborne(low), "발밑 1칸 — 공중 치명타 아님");
		FakePlayer high = caster(h, new Vec3(4.5, 2.5, 1.5), 0.0F);
		high.setOnGround(false);
		h.assertTrue(AeroDrift.airborne(high), "발밑 2.5칸 — 공중 치명타");
		FakePlayer ground = caster(h, new Vec3(6.5, 0, 1.5), 0.0F);
		ground.setOnGround(true);
		h.assertTrue(!AeroDrift.airborne(ground), "땅 — 아님");
		Classes.clear(low);
		Classes.clear(high);
		Classes.clear(ground);
		h.succeed();
	}

	/** 공중 명중마다 반동 도약 · 사선 앵커 쿨타임이 0.5초씩 깎입니다. */
	@GameTest
	public void airHitRefundsCooldowns(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 3, 0.5), 31.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		p.setOnGround(false);
		Attachments.profile(p).setCooldown(Gunslinger.BOOST, RecoilBoost.COOLDOWN);
		Attachments.profile(p).setCooldown(Gunslinger.ANCHOR, WireAnchor.COOLDOWN);
		gs().basic(p);
		h.assertTrue(dealtBy(v, p) > 0, "공중에서 맞음");
		near(h, Attachments.profile(p).cooldown(Gunslinger.BOOST),
				T.of(RecoilBoost.COOLDOWN) - T.of(AeroDrift.REFUND), 0, "반동 도약 -0.5초");
		near(h, Attachments.profile(p).cooldown(Gunslinger.ANCHOR),
				T.of(WireAnchor.COOLDOWN) - T.of(AeroDrift.REFUND), 0, "사선 앵커 -0.5초");
		Classes.clear(p);
		h.succeed();
	}

	/** 18발을 다 쓰면 저절로 1.25초 재장전, 그동안 다른 행동이 잠깁니다. */
	@GameTest(maxTicks = 400)
	public void magazineAndReload(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		GunslingerState st = Gunslinger.state(p);
		for (int i = 0; i < 18; i++) {
			gs().basic(p);
			if (i < 17) {
				classTicks(p, 4);
			}
		}
		h.assertTrue(st.ammo == 0 && st.reloadT == T.of(25), "18발 쓰면 곧바로 재장전 (탄 " + st.ammo + ", 재장전 " + st.reloadT + ")");
		h.assertTrue(Attachments.combatant(p).casting, "재장전 중 정신집중 (행동 잠금)");
		classTicks(p, 25);
		h.assertTrue(st.ammo == 18 && st.reloadT == 0, "1.25초 뒤 18발 (탄 " + st.ammo + ")");
		h.assertTrue(!Attachments.combatant(p).casting, "재장전이 끝나면 잠금 풀림");
		st.ammo = 9;
		gs().reload(p);
		h.assertTrue(st.reloadT == T.of(25), "R 키 재장전");
		classTicks(p, 8);
		gs().basic(p);
		h.assertTrue(st.ammo == 9, "재장전 중에는 안 나감");
		Classes.clear(p);
		h.succeed();
	}

	/** 반동 도약 — 폭발 피해 + 조준 반대 방향으로 날아감. */
	@GameTest
	public void recoilBoostBlastAndLaunch(GameTestHelper h) {
		// 바닥을 보고 쏘면 위로 솟구칩니다 (피치 90 = 정면 아래)
		FakePlayer p = caster(h, new Vec3(2.5, 1, 2.5), 90.0F);
		p.setOnGround(true);
		p.setDeltaMovement(Vec3.ZERO);
		Villager v = dummy(h, new Vec3(2.5, 0, 2.5));
		gs().primary(p);
		near(h, dealtBy(v, p), 25, 0.01, "발밑 폭발 25");
		h.assertTrue(p.getDeltaMovement().y > 0.1, "위로 솟구침 (" + p.getDeltaMovement().y + ")");
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.BOOST) == T.of(RecoilBoost.COOLDOWN), "쿨타임 6초");
		Classes.clear(p);
		h.succeed();
	}

	/**
	 * 돌진 난사 — 시전 즉시 쿨타임 8초 · 기 모으기 뒤 돌진.
	 * 위를 보고 써도 수평으로 나가고, 높이를 붙잡습니다 (세로 속도 0).
	 * 가짜 플레이어는 움직이지 않으므로 돌진이 실어 주는 속도로 봅니다.
	 */
	@GameTest(maxTicks = 300)
	public void scatterCastAndLookDash(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), -60.0F);
		p.setOnGround(true);
		gs().secondary(p);
		GunslingerState st = Gunslinger.state(p);
		h.assertTrue(st.scatter != null, "웅크리기 = 돌진 난사");
		h.assertTrue(Attachments.combatant(p).casting, "쓰는 동안 평타 · 스킬 잠김");
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.SCATTER) == T.of(DashScatter.COOLDOWN), "쿨타임 8초 즉시");
		h.assertTrue(st.scatter.phase() == DashScatter.Phase.WINDUP, "기 모으기부터");
		h.runAfterDelay(T.of(DashScatter.DASH_START) + 3, () -> {
			h.assertTrue(st.scatter != null && st.scatter.phase() == DashScatter.Phase.DASH, "돌진 중");
			Vec3 v = p.getDeltaMovement();
			double expect = kr.overbreak.core.tick.Ticks.speed(DashScatter.DISTANCE / DashScatter.DASH);
			near(h, v.y, expect * Math.sin(Math.toRadians(60)), 1.0E-3, "위(60도)를 보면 그 방향으로 솟음");
			near(h, v.z, expect * Math.cos(Math.toRadians(60)), 1.0E-3, "앞으로는 cos 60 만큼");
			near(h, v.x, 0.0, 1.0E-6, "옆으로는 안 감");
			h.runAfterDelay(T.of(DashScatter.LENGTH), () -> {
				h.assertTrue(st.scatter == null, "1.6초 뒤 끝남");
				h.assertTrue(!Attachments.combatant(p).casting, "끝나면 잠금 풀림");
				Classes.clear(p);
				h.succeed();
			});
		});
	}

	/** 난사 — 반경 5칸 안 적은 6번 × 7 = 42, 밖은 0. 넉백 없음. */
	@GameTest(maxTicks = 300)
	public void scatterPulsesHitRadius(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		p.setOnGround(true);
		Villager in = dummy(h, new Vec3(1.5, 0, 4.5));
		Villager out = dummy(h, new Vec3(7.5, 0, 7.5));
		gs().secondary(p);
		h.runAfterDelay(T.of(DashScatter.LENGTH) + 6, () -> {
			near(h, dealtBy(in, p), DashScatter.PULSES * DashScatter.DAMAGE_100 / 100.0, 0.01, "반경 안 6번 × 7 = 42");
			near(h, dealtBy(out, p), 0, 0.01, "5칸 밖은 안 맞음");
			h.assertTrue(in.getDeltaMovement().horizontalDistance() < 0.01, "넉백 없음");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 벽 너머 적은 맞지 않고, 벽에 막힌 돌진은 곧장 제동으로 건너뜁니다. */
	@GameTest(maxTicks = 300)
	public void scatterWallBlocksAndSkipsToBrake(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		p.setOnGround(true);
		// 가짜 플레이어는 벽 쪽으로 움직이지 않으므로, 바로 앞(반 칸)에 벽을 세웁니다
		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 3; y++) {
				h.setBlock(new net.minecraft.core.BlockPos(x, y, 2), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		Villager behind = dummy(h, new Vec3(1.5, 0, 3.5));
		gs().secondary(p);
		GunslingerState st = Gunslinger.state(p);
		// 건너뛰지 않았다면 1.6초(32) — 건너뛰면 돌진 대부분이 빠져 30 전에 끝납니다
		h.runAfterDelay(T.of(30), () -> {
			h.assertTrue(st.scatter == null, "벽에 막혀 제동으로 건너뛰어 일찍 끝남");
			near(h, dealtBy(behind, p), 0, 0.01, "벽 너머는 안 맞음");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 이동기 봉인(균열 지대) — 쿨타임을 쓰지 않고 거부. */
	@GameTest
	public void scatterSealedRejectedWithoutCooldown(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Attachments.combatant(p).sealT = 100;
		gs().secondary(p);
		h.assertTrue(Gunslinger.state(p).scatter == null, "봉인 중에는 안 나감");
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.SCATTER) == 0, "쿨타임도 안 씀");
		Attachments.combatant(p).sealT = 0;
		Classes.clear(p);
		h.succeed();
	}

	/** 돌진 중 기절 — 난사 없이 그 자리에서 끝 (쿨타임은 그대로). */
	@GameTest(maxTicks = 300)
	public void scatterStunDuringDashCancels(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		p.setOnGround(true);
		Villager v = dummy(h, new Vec3(1.5, 0, 3.5));
		gs().secondary(p);
		GunslingerState st = Gunslinger.state(p);
		h.runAfterDelay(T.of(DashScatter.DASH_START) + 3, () -> {
			h.assertTrue(st.scatter != null && st.scatter.phase() == DashScatter.Phase.DASH, "돌진 중");
			kr.overbreak.cc.CrowdControl.stun(p, 20);
			h.runAfterDelay(T.of(DashScatter.LENGTH), () -> {
				h.assertTrue(st.scatter == null, "끊겨서 끝남");
				h.assertTrue(!Attachments.combatant(p).casting, "잠금 풀림");
				near(h, dealtBy(v, p), 0, 0.01, "난사는 나가지 않음");
				h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.SCATTER) > 0, "쿨타임은 돌려주지 않음");
				Classes.clear(p);
				h.succeed();
			});
		});
	}

	/** 땅 위에서 아래를 보고 쓰면 정면(수평)으로 · 공중에서 아래를 보면 그 방향으로. */
	@GameTest
	public void scatterDirectionRules(GameTestHelper h) {
		Vec3 ground = DashScatter.direction(0.0F, 40.0F, true);
		near(h, ground.y, 0.0, 1.0E-9, "땅 + 아래 → 수평");
		near(h, ground.z, 1.0, 1.0E-9, "땅 + 아래 → 정면");
		Vec3 air = DashScatter.direction(0.0F, 40.0F, false);
		near(h, air.y, -Math.sin(Math.toRadians(40)), 1.0E-9, "공중 + 아래 → 아래로");
		Vec3 up = DashScatter.direction(90.0F, -30.0F, true);
		near(h, up.y, Math.sin(Math.toRadians(30)), 1.0E-9, "땅 + 위 → 위로");
		near(h, up.x, -Math.cos(Math.toRadians(30)), 1.0E-9, "yaw 90 → -X");
		h.succeed();
	}

	/** 서버가 받아 주는 거리는 7.5칸 — 넘게 가 있으면 제동 때 되돌립니다. */
	@GameTest(maxTicks = 300)
	public void scatterClampsOvershoot(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		p.setOnGround(true);
		Vec3 start = p.position();
		gs().secondary(p);
		h.runAfterDelay(T.of(DashScatter.DASH_START) + 3, () -> {
			// 조작된 클라이언트처럼 한 번에 10칸 앞으로
			p.setPos(start.x, start.y, start.z + 10.0);
			h.runAfterDelay(T.of(DashScatter.DASH) + 3, () -> {
				double flat = Math.sqrt(Math.pow(p.getX() - start.x, 2) + Math.pow(p.getZ() - start.z, 2));
				near(h, flat, DashScatter.MAX_DISTANCE, 0.05, "7.5칸으로 되돌림");
				Gunslinger.state(p).scatter.cancel();
				Classes.clear(p);
				h.succeed();
			});
		});
	}

	/** 사선 앵커 — 적을 걸면 피해 + 기절 + 그 적의 머리 위로 끌려감. */
	@GameTest(maxTicks = 200)
	public void wireAnchorPullsOverhead(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		gs().tertiary(p);
		near(h, dealtBy(v, p), 20, 0.01, "와이어 적중 20");
		h.assertTrue(Attachments.combatant(v).stunT > 0, "0.5초 기절");
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.ANCHOR) == T.of(WireAnchor.COOLDOWN), "쿨타임 7초");
		// 가짜 플레이어는 서버 틱을 받지 않아 실제로 움직이지는 않으므로, 견인이 실어 주는 속도를 봅니다
		h.runAfterDelay(T.of(3), () -> {
			Vec3 pull = p.getDeltaMovement();
			h.assertTrue(pull.y > 0.01, "적 머리 위(위쪽)로 당겨짐 (y " + pull.y + ")");
			h.assertTrue(pull.z > 0.01, "적이 있는 +Z 쪽으로 당겨짐 (z " + pull.z + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/**
	 * 사선 앵커 벽 적중 — 그 지점으로 당겨지고 쿨타임은 온전히 돕니다.
	 * (헛방 = 쿨타임 절반은 시험 구역이 사방으로 막혀 있어 게임 시험으로는 만들 수 없습니다)
	 */
	@GameTest(maxTicks = 200)
	public void wireAnchorWallPull(GameTestHelper h) {
		// 발밑 바닥을 겨눕니다
		FakePlayer p = caster(h, new Vec3(3.5, 2, 3.5), 90.0F);
		p.setDeltaMovement(Vec3.ZERO);
		gs().tertiary(p);
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.ANCHOR) == T.of(WireAnchor.COOLDOWN), "벽 적중은 쿨타임 7초");
		h.runAfterDelay(T.of(3), () -> {
			h.assertTrue(p.getDeltaMovement().y < -0.01, "겨눈 바닥 쪽으로 당겨짐 (y " + p.getDeltaMovement().y + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 차원 회전 포격 — 솟구친 뒤 0.25초마다 15 (초당 60). */
	@GameTest(maxTicks = 400)
	public void bombardmentPulses(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(3.5, 0, 3.5), 90.0F);
		p.setOnGround(true);
		Villager v = dummy(h, new Vec3(3.5, 0, 3.5));
		Attachments.profile(p).ultHas = true;
		gs().ult(p);
		GunslingerState st = Gunslinger.state(p);
		h.assertTrue(st.inUlt(), "포격 시작");
		h.assertTrue(Attachments.combatant(p).ccImmune, "포격 중 군중제어 면역");
		// 솟구침(8) + 포격 1초 — 0.25초마다 한 번씩 네 번 = 60
		h.runAfterDelay(T.of(AerialBombardment.RISE + 21), () -> {
			float dealt = dealtBy(v, p);
			h.assertTrue(dealt >= 30 && dealt <= 75, "1초쯤에 초당 60 언저리 (실측 " + dealt + ")");
			h.runAfterDelay(T.of(AerialBombardment.DURATION + 10), () -> {
				h.assertTrue(!Gunslinger.state(p).inUlt(), "3초 뒤 끝남");
				h.assertTrue(!Attachments.combatant(p).ccImmune, "끝나면 면역 풀림");
				Classes.clear(p);
				h.succeed();
			});
		});
	}

	/** 포격 중에는 반동 도약만 쿨타임 없이 통과합니다. */
	@GameTest(maxTicks = 400)
	public void bombardmentFreeBoostOnly(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(5.5, 0, 5.5), 90.0F);
		p.setOnGround(true);
		Attachments.profile(p).ultHas = true;
		gs().ult(p);
		h.assertTrue(gs().intercept(p, kr.overbreak.input.InputRouter.Slot.TERTIARY), "포격 중 E 잠김");
		h.assertTrue(gs().intercept(p, kr.overbreak.input.InputRouter.Slot.BASIC), "포격 중 평타 잠김");
		h.assertTrue(!gs().intercept(p, kr.overbreak.input.InputRouter.Slot.PRIMARY), "포격 중 우클릭은 통과");
		gs().primary(p);
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.BOOST) == 0, "포격 중 반동 도약은 쿨타임이 붙지 않음");
		Gunslinger.state(p).bombardment.cancel();
		Classes.clear(p);
		h.succeed();
	}

	/** 체공 훈풍 — 떨어지기 시작한 뒤에만 점프 키로 활공 (그냥 점프로는 안 켜짐), 착지하면 다시 참. */
	@GameTest(maxTicks = 200)
	public void aeroDriftGlideNeedsDescent(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 4, 1.5), 0.0F);
		GunslingerState st = Gunslinger.state(p);
		Vec3 at = p.position();
		p.setOnGround(false);
		Attachments.profile(p).jumpDown = true;
		classTicks(p, 1);
		// 올라가는 중 (점프 직후)
		p.setPos(at.x, at.y + 0.2, at.z);
		classTicks(p, 1);
		h.assertTrue(!st.gliding, "올라가는 동안에는 점프 키를 눌러도 활공 안 함");
		// 정점을 지나 떨어지기 시작
		p.setPos(at.x, at.y + 0.1, at.z);
		classTicks(p, 1);
		h.assertTrue(st.descending && st.gliding, "떨어지기 시작하면 활공");
		classTicks(p, 3);
		h.assertTrue(st.glideT < T.of(AeroDrift.GLIDE), "활공 시간이 줄어듦 (" + st.glideT + ")");
		h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "느린 낙하");
		Attachments.profile(p).jumpDown = false;
		classTicks(p, 1);
		h.assertTrue(!st.gliding, "점프 키를 떼면 멈춤");
		p.setOnGround(true);
		classTicks(p, 1);
		h.assertTrue(st.glideT == T.of(AeroDrift.GLIDE) && !st.descending, "착지하면 다시 가득 참");
		Classes.clear(p);
		h.succeed();
	}

	/** 반동 도약 · 사선 앵커를 쓸 때마다 활공 +1초 — 땅에서 써도 남고, 착지하면 2초로. */
	@GameTest(maxTicks = 200)
	public void glideExtendsOnSkills(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(3.5, 0, 3.5), 90.0F);
		GunslingerState st = Gunslinger.state(p);
		p.setOnGround(true);
		classTicks(p, 1);
		int base = T.of(AeroDrift.GLIDE);
		gs().primary(p);
		near(h, st.glideT, base + T.of(AeroDrift.EXTEND), 0, "반동 도약 +1초");
		classTicks(p, 1);
		near(h, st.glideT, base + T.of(AeroDrift.EXTEND), 0, "땅에 있어도 연장분은 남음");
		gs().tertiary(p);
		near(h, st.glideT, base + 2 * T.of(AeroDrift.EXTEND), 0, "사선 앵커 +1초 (쌓임)");
		// 날아올랐다가 착지하면 2초로
		p.setOnGround(false);
		classTicks(p, 1);
		p.setOnGround(true);
		classTicks(p, 1);
		near(h, st.glideT, base, 0, "착지하면 2초로 돌아감");
		Classes.clear(p);
		h.succeed();
	}

	/** 재장전 중에는 활공 동작을 띄우지 않습니다 — 공중 재장전 동작을 덮어써 끊어먹었습니다 (0.2d). */
	@GameTest(maxTicks = 200)
	public void glideAnimWaitsForReload(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 4, 1.5), 0.0F);
		GunslingerState st = Gunslinger.state(p);
		Vec3 at = p.position();
		p.setOnGround(false);
		Attachments.profile(p).jumpDown = true;
		classTicks(p, 1);
		p.setPos(at.x, at.y - 0.1, at.z);
		st.ammo = 3;
		gs().reload(p);
		classTicks(p, 4);
		h.assertTrue(st.gliding, "재장전 중에도 활공(느린 낙하)은 먹힘");
		h.assertTrue(!st.glideAnim, "재장전 중에는 활공 동작을 띄우지 않음");
		// 재장전이 끝나면 그때 동작이 들어옵니다
		classTicks(p, 25);
		h.assertTrue(st.reloadT == 0, "재장전 끝");
		h.assertTrue(st.glideAnim, "재장전이 끝나면 활공 동작이 들어옴");
		Attachments.profile(p).jumpDown = false;
		Classes.clear(p);
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
