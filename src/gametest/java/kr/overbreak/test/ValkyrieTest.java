package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.valkyrie.Rifle;
import kr.overbreak.classes.valkyrie.Valkyrie;
import kr.overbreak.classes.valkyrie.ValkyrieState;
import kr.overbreak.core.Attachments;
import kr.overbreak.input.InputRouter;
import kr.overbreak.ult.UltGauge;
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
 * 발키리 — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 플레이어 목록에 없어 직업 틱이 저절로 돌지 않으므로, 좌클릭(basic) · 직업 틱(tick)을 직접 부릅니다.
 * 바닥은 y=0. 피해는 그 테스트의 시전자가 준 것만 셉니다 (옆 구역 스킬이 섞이지 않게).
 */
public final class ValkyrieTest implements CustomTestMethodInvoker {
	/** 표적 가슴 높이를 겨누는 피치 (6칸 앞). 0 이면 눈높이 = 머리. */
	private static final float CHEST_PITCH = 6.2F;

	private static final Map<LivingEntity, Map<Entity, Float>> DEALT = new IdentityHashMap<>();
	private static final Map<LivingEntity, Integer> HITS = new IdentityHashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (source.getEntity() != null) {
				DEALT.computeIfAbsent(target, k -> new IdentityHashMap<>()).merge(source.getEntity(), taken, Float::sum);
				HITS.merge(target, 1, Integer::sum);
			}
		});
	}

	private static float dealtBy(LivingEntity target, Entity attacker) {
		return DEALT.getOrDefault(target, Map.of()).getOrDefault(attacker, 0.0F);
	}

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float pitch) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_valkyrie"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, valk());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass valk() {
		return Classes.byId(Valkyrie.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	/** 좌클릭을 누른 채 n 틱. */
	private static void hold(FakePlayer p, int ticks) {
		for (int i = 0; i < T.of(ticks); i++) {
			valk().basic(p);
			valk().tick(p);
		}
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 200, 0.01, "최대 체력 (200 + 0)");
		h.assertTrue(!valk().meleeAllowed(), "근접 불가 (좌클릭 = 연사)");
		h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "연사 포탑");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void bodyShotAndHeadshot(GameTestHelper h) {
		FakePlayer body = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v1 = dummy(h, new Vec3(1.5, 0, 6.5));
		hold(body, 1);
		near(h, dealtBy(v1, body), 7, 0.01, "몸통 한 발 7");

		FakePlayer head = caster(h, new Vec3(5.5, 0, 0.5), 0.0F);
		Villager v2 = dummy(h, new Vec3(5.5, 0, 6.5));
		hold(head, 1);
		near(h, dealtBy(v2, head), 10.5, 0.01, "머리 약한 치명타 10.5 (1.5배)");
		Classes.clear(body);
		Classes.clear(head);
		h.succeed();
	}

	@GameTest
	public void groundFiveShotsPerSecond(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		hold(p, 20);
		near(h, dealtBy(v, p), 56 + 7.5, 0.01, "땅에서 1초에 8발 + 다섯 번째 명중 미사일");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void springBoostsFireRate(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		valk().secondary(p);
		ValkyrieState st = Valkyrie.state(p);
		h.assertTrue(st.floating, "차원 도약으로 뜸");
		h.assertTrue(Attachments.profile(p).cooldown("vk_spring") == T.of(240), "쿨타임 12초");
		// 가짜 플레이어는 땅에 닿지 않은 상태 → 가속 (초당 10발 = +25%)
		hold(p, 16);
		near(h, dealtBy(v, p), 56 + 7.5, 0.01, "도약 중 0.8초에 8발 (초당 10발) + 미사일 한 번");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void magazineFiftyThenReload(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		ValkyrieState st = Valkyrie.state(p);
		h.assertTrue(st.ammo == 50, "탄창 50발");
		st.ammo = 1;
		valk().basic(p);
		valk().tick(p);
		h.assertTrue(st.ammo == 0 && st.reloadT == T.of(Rifle.RELOAD), "마지막 발 → 재장전 시작");
		for (int i = 0; i < T.of(Rifle.RELOAD) - 1; i++) {
			valk().basic(p);
			valk().tick(p);
		}
		h.assertTrue(st.ammo == 0, "재장전 중에는 누르고 있어도 안 나감");
		valk().tick(p);
		h.assertTrue(st.ammo == 50 && st.reloadT == 0, "1.5초 뒤 50발 (실측 " + st.ammo + ")");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void sprintSpeedIsBase(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		net.minecraft.world.entity.ai.attributes.AttributeInstance speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		h.assertTrue(speed.getModifier(Classes.SPRINT_BASE) != null, "직업을 받으면 기본 속도가 달리기 속도 (+30%)");
		Classes.clear(p);
		h.assertTrue(speed.getModifier(Classes.SPRINT_BASE) == null, "직업을 지우면 원래 속도");
		h.succeed();
	}

	@GameTest
	public void manualReloadWithR(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		ValkyrieState st = Valkyrie.state(p);
		valk().reload(p);
		h.assertTrue(st.reloadT == 0, "가득 찬 탄창은 재장전 안 함");
		st.ammo = 20;
		valk().reload(p);
		h.assertTrue(st.reloadT == T.of(Rifle.RELOAD), "R → 재장전 시작");
		valk().tick(p);
		valk().reload(p);
		h.assertTrue(st.reloadT == T.of(Rifle.RELOAD) - 1, "재장전 중 다시 눌러도 처음부터 다시 하지 않음");
		for (int i = 0; i < T.of(Rifle.RELOAD) - 2; i++) {
			valk().basic(p);
			valk().tick(p);
		}
		h.assertTrue(st.ammo == 20, "재장전 중에는 누르고 있어도 안 나감");
		valk().tick(p);
		h.assertTrue(st.ammo == 50 && st.reloadT == 0, "1.5초 뒤 50발 (실측 " + st.ammo + ")");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void springSealedKeepsCooldown(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		CrowdControl.seal(p, 40);
		valk().secondary(p);
		h.assertTrue(!Valkyrie.state(p).floating && Attachments.profile(p).cooldown("vk_spring") == 0, "균열 위에서는 막히고 쿨타임도 안 먹음");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void spreadGrowsAfterOneSecond(GameTestHelper h) {
		near(h, Rifle.spread(20), 0.0, 1.0E-6, "1초까지 탄퍼짐 0");
		near(h, Rifle.spread(30), 1.425, 1.0E-6, "1.5초 절반");
		near(h, Rifle.spread(40), 2.85, 1.0E-6, "2초 최대");
		near(h, Rifle.spread(100), 2.85, 1.0E-6, "그 뒤 고정");
		h.succeed();
	}

	@GameTest
	public void missileEveryFiveHits(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager target = dummy(h, new Vec3(1.5, 0, 6.5));
		Villager beside = dummy(h, new Vec3(2.7, 0, 6.5));
		// 끊어 쏘기 (4틱마다 한 번 눌러 탄퍼짐 없이) 5발
		for (int i = 0; i < T.of(17); i++) {
			if (i % T.of(4) == 0) {
				valk().basic(p);
			}
			valk().tick(p);
		}
		near(h, dealtBy(target, p), 35 + 7.5, 0.01, "5발 35 + 미사일 7.5");
		near(h, dealtBy(beside, p), 7.5, 0.01, "옆 1.2칸도 미사일 7.5");
		h.assertTrue(Valkyrie.state(p).missileN == 0, "카운터 초기화");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 120)
	public void rocketBlastAndAirborne(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(3.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(3.5, 0, 4.5));
		Villager far = dummy(h, new Vec3(7.5, 0, 4.5));
		valk().primary(p);
		h.assertTrue(Attachments.profile(p).cooldown("vk_rocket") == T.of(100), "쿨타임 5초");
		h.runAfterDelay(T.of(10), () -> {
			near(h, dealtBy(v, p), 35, 0.01, "착탄 35");
			h.assertTrue(Attachments.combatant(v).slowT > 0, "둔화 (에어본 삭제)");
			near(h, dealtBy(far, p), 0, 0.01, "3칸 밖은 무피해");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void overheatConeTenShots(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager front = dummy(h, new Vec3(1.5, 0, 4.5));
		Villager side = dummy(h, new Vec3(4.5, 0, 4.5)); // 45도 옆 (60도 부채꼴 밖)
		valk().tertiary(p);
		h.runAfterDelay(T.of(25), () -> {
			near(h, dealtBy(front, p), 85, 0.01, "10발 x 8.5");
			h.assertTrue(Attachments.combatant(front).slowT > 0, "둔화");
			near(h, dealtBy(side, p), 0, 0.01, "부채꼴 밖");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void overheatStunCutsRemaining(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager front = dummy(h, new Vec3(1.5, 0, 4.5));
		valk().tertiary(p);
		h.runAfterDelay(T.of(6), () -> CrowdControl.stun(p, 30));
		h.runAfterDelay(T.of(25), () -> {
			float d = dealtBy(front, p);
			h.assertTrue(d > 0 && d < 85, "기절하면 남은 발수 소멸 (실측 " + d + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 360)
	public void barrageFortyShots(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		CrowdControl.stun(p, 40);
		UltGauge.fill(p);
		valk().ult(p);
		h.assertTrue(Attachments.combatant(p).ccImmune, "저지불가");
		h.assertTrue(Attachments.combatant(p).stunT == 0, "걸려 있던 기절이 풀림");
		h.assertTrue(valk().intercept(p, InputRouter.Slot.BASIC), "기본 공격 잠김");
		h.assertTrue(valk().intercept(p, InputRouter.Slot.SECONDARY), "차원 도약 잠김");
		h.assertTrue(!valk().intercept(p, InputRouter.Slot.PRIMARY), "전술 로켓은 사용 가능");
		h.runAfterDelay(T.of(10), () -> near(h, dealtBy(v, p), 0, 0.01, "기 모으는 0.7초 동안은 안 나감"));
		h.runAfterDelay(T.of(100), () -> {
			near(h, dealtBy(v, p), 340, 0.05, "40발 x 8.5");
			h.assertTrue(!Attachments.combatant(p).ccImmune, "끝나면 저지불가 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
