package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.ironfist.Absorb;
import kr.overbreak.classes.ironfist.IronFist;
import kr.overbreak.classes.ironfist.IronFistState;
import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.input.InputRouter;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 파쇄권 — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 서버 플레이어 목록에 없어 직업 틱(재장전 · 비행 · 흡수 감쇠)이 저절로 돌지 않으므로 필요한 테스트에서 직접 부릅니다.
 */
public final class IronFistTest implements CustomTestMethodInvoker {

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float pitch) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_ironfist"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, fist());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass fist() {
		return Classes.byId(IronFist.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 200, 0.01, "최대 체력 (200 + 0)");
		near(h, p.getMaxAbsorption(), 120, 0.01, "추가 체력 상한 120");
		h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "오른손 건틀릿");
		h.assertTrue(!p.getOffhandItem().isEmpty(), "왼손 철권포");
		h.assertTrue(!fist().meleeAllowed(), "근접 불가 (좌클릭 = 철권포)");
		Classes.clear(p);
		near(h, p.getMaxAbsorption(), 0, 0.01, "해제하면 상한 원복");
		h.assertTrue(p.getOffhandItem().isEmpty(), "해제하면 철권포 회수");
		h.succeed();
	}

	/** 피해 이벤트 수 (탄환을 합산해 한 번에 들어가는지). */
	private static final Map<LivingEntity, Integer> HURTS = new IdentityHashMap<>();

	/** 대상별 · 공격자별 받은 피해 — 여러 테스트가 동시에 돌아 옆 구역의 긴 사거리 스킬이 섞일 수 있어, 시전자 기준으로 셉니다. */
	private static final Map<LivingEntity, Map<Entity, Float>> DEALT = new IdentityHashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			HURTS.merge(target, 1, Integer::sum);
			if (source.getEntity() != null) {
				DEALT.computeIfAbsent(target, k -> new IdentityHashMap<>()).merge(source.getEntity(), taken, Float::sum);
			}
		});
	}

	private static float dealtBy(LivingEntity target, Entity attacker) {
		return DEALT.getOrDefault(target, Map.of()).getOrDefault(attacker, 0.0F);
	}

	@GameTest
	public void pointBlankAllPellets(GameTestHelper h) {
		// 가슴을 겨눔 (1칸 앞 45도 아래) — 머리에 안 맞게
		FakePlayer p = caster(h, new Vec3(2.5, 0, 1.5), 45.0F);
		Villager v = dummy(h, new Vec3(2.5, 0, 2.5));
		HURTS.remove(v);
		fist().basic(p);
		near(h, v.getHealth(), 1000 - 38.5, 0.05, "1칸 몸통 전탄 11 x 3.5");
		h.assertTrue(HURTS.getOrDefault(v, 0) == 1, "맞은 탄환을 합산해 피해 한 번 (실측 " + HURTS.getOrDefault(v, 0) + "번)");
		h.assertTrue(IronFist.state(p).ammo == 3, "한 발 소모");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void headshotDoubles(GameTestHelper h) {
		// 눈높이로 1칸 앞 머리를 겨눔 — 11발 모두 머리
		FakePlayer p = caster(h, new Vec3(2.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(2.5, 0, 2.5));
		HURTS.remove(v);
		fist().basic(p);
		near(h, v.getHealth(), 1000 - 77.0, 0.05, "머리 전탄 11 x 3.5 x 2");
		h.assertTrue(HURTS.getOrDefault(v, 0) == 1, "치명타도 한 번에 (실측 " + HURTS.getOrDefault(v, 0) + "번)");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void threeShotsPerSecondAndReloadAfterShot(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		IronFistState st = IronFist.state(p);
		// 1초(20틱) 동안 좌클릭을 누르고 있음
		for (int i = 0; i < T.of(20); i++) {
			fist().basic(p);
			fist().tick(p);
		}
		h.assertTrue(st.ammo == 1, "1초에 3발 (남은 탄 실측 " + st.ammo + ")");
		fist().basic(p);
		h.assertTrue(st.ammo == 0, "다음 발은 곧바로 (20틱째)");
		for (int i = 0; i < T.of(kr.overbreak.classes.ironfist.HandCannon.RELOAD) - 1; i++) {
			fist().tick(p);
		}
		h.assertTrue(st.ammo == 0, "마지막 발사 뒤 0.7초 전에는 안 참");
		fist().tick(p);
		h.assertTrue(st.ammo == 1, "마지막 발사 0.7초 뒤 한 발");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 180)
	public void sameSpreadEveryShot(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(2.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(2.5, 0, 4.5));
		h.onEachTick(() -> fist().tick(p));
		fist().basic(p);
		float first = 1000 - v.getHealth();
		h.runAfterDelay(T.of(22), () -> {
			float before = v.getHealth();
			IronFistState st = IronFist.state(p);
			System.out.println("[overbreak-ironfist] 두 번째 발사 전 fireCd=" + st.fireCd + " ammo=" + st.ammo
					+ " 표적=" + v.position().subtract(p.position()) + " 살아있음=" + v.isAlive());
			fist().basic(p);
			float second = before - v.getHealth();
			System.out.println("[overbreak-ironfist] 3칸 철권포 피해 " + first + " / " + second);
			h.assertTrue(first > 0, "3칸에서 맞음 (실측 " + first + ")");
			near(h, second, first, 0.01, "탄퍼짐이 같아 두 번째 발사도 같은 피해");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 거리별 피해 (데이터팩 표 1칸 38.5 · 2칸 32.9 · 3칸 22.9 · 4칸 16.1 · 5칸 12.8 과 비교해 출력). */
	@GameTest
	public void damageByDistance(GameTestHelper h) {
		StringBuilder out = new StringBuilder("[overbreak-ironfist] 거리별 피해 (눈높이 조준 · 머리 치명타 포함)");
		for (int d = 1; d <= 5; d++) {
			double x = 0.5 + (d - 1) * 1.6;
			FakePlayer p = caster(h, new Vec3(x, 0, 1.5), 0.0F);
			Villager v = dummy(h, new Vec3(x, 0, 1.5 + d));
			fist().basic(p);
			float dmg = 1000 - v.getHealth();
			out.append("  ").append(d).append("칸 ").append(dmg);
			h.assertTrue(dmg <= 77.0F + 0.01F, d + "칸 피해가 머리 전탄보다 큼 " + dmg);
			Classes.clear(p);
		}
		System.out.println(out);
		h.succeed();
	}

	@GameTest
	public void magazineRefillsOneByOne(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(2.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(2.5, 0, 2.5));
		IronFistState st = IronFist.state(p);
		for (int i = 0; i < 4; i++) {
			st.fireCd = 0;
			fist().basic(p);
		}
		h.assertTrue(st.ammo == 0, "4발 소진 (실측 " + st.ammo + ")");
		float after4 = v.getHealth();
		st.fireCd = 0;
		fist().basic(p);
		near(h, v.getHealth(), after4, 0.01, "0발이면 나가지 않음");
		for (int i = 0; i < T.of(kr.overbreak.classes.ironfist.HandCannon.RELOAD); i++) {
			fist().tick(p);
		}
		h.assertTrue(st.ammo == 1, "0.7초 뒤 한 발 (실측 " + st.ammo + ")");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void absorbCapDecayAndGauge(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Absorb.gain(p);
		near(h, p.getAbsorptionAmount(), 20, 0.01, "적중 1명당 추가 체력 20");
		h.assertTrue(Attachments.profile(p).ultCharge == 2, "추가 체력 20 = 게이지 2% (실측 " + Attachments.profile(p).ultCharge + ")");
		for (int i = 0; i < 6; i++) {
			Absorb.gain(p);
		}
		near(h, p.getAbsorptionAmount(), 120, 0.01, "상한 120");
		h.assertTrue(Attachments.profile(p).ultCharge == 12, "120 = 게이지 12% (실측 " + Attachments.profile(p).ultCharge + ")");
		for (int i = 0; i < T.of(Absorb.HOLD); i++) {
			fist().tick(p);
		}
		near(h, p.getAbsorptionAmount(), 120, 0.01, "2초 동안은 유지");
		for (int i = 0; i < T.of(1); i++) {
			fist().tick(p);
		}
		near(h, p.getAbsorptionAmount(), 119, 0.01, "그 뒤 틱당 1 (초당 20)");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void powerBlockFrontOnly(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 0, 4.5), 0.0F);
		Villager front = dummy(h, new Vec3(4.5, 0, 6.5));
		Villager back = dummy(h, new Vec3(4.5, 0, 2.5));
		fist().secondary(p);
		// 가짜 플레이어는 "클라이언트 로딩 완료" 가 아니라 바닐라가 피해 자체를 막으므로, 받는 피해 수정 단계를 직접 확인합니다
		var type = SkillDamage.type(h.getLevel(), SkillDamage.Kind.MULTI);
		near(h, DamageModifiers.apply(p, new DamageSource(type, front, front), 100.0F), 0, 0.01, "정면 100 → 0 (전부 막음)");
		near(h, DamageModifiers.apply(p, new DamageSource(type, back, back), 100.0F), 100, 0.01, "뒤 100 → 그대로");
		fist().intercept(p, InputRouter.Slot.SECONDARY);
		h.assertTrue(IronFist.state(p).empowerT > 0, "70 막아 냄 → 로켓 펀치 강화");
		h.assertTrue(Attachments.profile(p).cooldown("if_block") == T.of(120), "해제 순간부터 쿨타임 6초");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 240)
	public void rocketPunchFullCharge(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 0, 2.5), 0.0F);
		Villager v = dummy(h, new Vec3(4.5, 0, 4.5));
		fist().primary(p);
		h.assertTrue(Attachments.combatant(p).casting, "충전 중 스킬 · 평타 잠금");
		h.runAfterDelay(T.of(14), () -> near(h, v.getHealth(), 1000, 0.01, "충전 중에는 안 나감"));
		h.runAfterDelay(T.of(34), () -> {
			near(h, v.getHealth(), 940, 0.05, "최대 충전 피해 60");
			h.assertTrue(Attachments.combatant(v).pushT > 0, "밀쳐내기 (군중 제어)");
			h.assertTrue(Attachments.profile(p).cooldown("if_punch") > 0, "쿨타임");
			h.assertTrue(!Attachments.combatant(p).casting, "명중하면 돌진 끝");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 240)
	public void rocketPunchWallSlam(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(4.5, 0, 3.5));
		h.setBlock(new BlockPos(4, 0, 4), Blocks.STONE);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		fist().primary(p);
		h.runAfterDelay(T.of(42), () -> {
			near(h, dealtBy(v, p), 100, 0.05, "펀치 60 + 벽 40");
			h.assertTrue(Attachments.combatant(v).stunT > 0, "벽 충돌 기절");
			h.assertTrue(Attachments.combatant(v).stunMax == T.of(14), "강화가 아니면 0.7초 (실측 " + Attachments.combatant(v).stunMax + "틱)");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 240)
	public void empoweredWallSlamLongStun(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(4.5, 0, 3.5));
		h.setBlock(new BlockPos(4, 0, 4), Blocks.STONE);
		h.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		IronFist.state(p).empowerT = 100;
		fist().primary(p);
		h.runAfterDelay(T.of(42), () -> {
			near(h, dealtBy(v, p), 130, 0.05, "강화 최대 충전 90 + 벽 40");
			h.assertTrue(Attachments.combatant(v).stunMax == T.of(34), "강화 최대 충전 벽 기절 1.7초 (실측 " + Attachments.combatant(v).stunMax + "틱)");
			h.assertTrue(Attachments.combatant(v).stunT > 14, "기본 기절(0.7초)보다 길게 남음 (실측 " + Attachments.combatant(v).stunT + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void seismicSlamWave(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 0, 1.5), 0.0F);
		Villager near3 = dummy(h, new Vec3(4.5, 0, 4.5));
		Villager far10 = dummy(h, new Vec3(4.5, 0, 11.5));
		Villager side = dummy(h, new Vec3(7.5, 0, 3.5)); // 56도 옆
		fist().tertiary(p);
		// 가짜 플레이어는 물리가 돌지 않아 떠오르지 않습니다. 땅에 있는 것으로 두면 유예 6틱 뒤 착지합니다.
		p.setOnGround(true);
		h.onEachTick(() -> fist().tick(p));
		h.runAfterDelay(T.of(20), () -> {
			near(h, near3.getHealth(), 960, 0.05, "3칸 파면 40");
			near(h, far10.getHealth(), 960, 0.05, "10칸 파면 40");
			near(h, side.getHealth(), 1000, 0.01, "90도 부채꼴 밖");
			h.assertTrue(Attachments.combatant(near3).slowT > 0, "둔화");
			near(h, p.getAbsorptionAmount(), 40, 0.01, "적중 2명 → 추가 체력 40");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 420)
	public void meteorStrikeBlast(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(3.5, 0, 2.5), 90.0F); // 발밑을 보고 씀
		Villager inner = dummy(h, new Vec3(3.5, 0, 4.5));
		Villager outer = dummy(h, new Vec3(3.5, 0, 6.5));
		UltGauge.fill(p);
		fist().ult(p);
		h.assertTrue(IronFist.state(p).empowerT > 0, "궁극기를 쓰면 로켓 펀치 강화");
		h.onEachTick(() -> fist().tick(p));
		h.runAfterDelay(T.of(95), () -> {
			// 중심에서 멀어질수록 줄어드는 피해 — 2칸 105, 4칸 60 (0.1a)
			near(h, inner.getHealth(), 895, 0.05, "2칸 105");
			near(h, outer.getHealth(), 940, 0.05, "4칸 60");
			h.assertTrue(Attachments.combatant(inner).stunT == 0 && Attachments.combatant(outer).stunT == 0, "기절 없음 (원작과 같음)");
			near(h, p.getAbsorptionAmount(), 120, 0.01, "착지하면 추가 체력 가득");
			h.assertTrue(IronFist.state(p).empowerT > 80, "착지 순간 강화 지속시간을 다시 채움 (실측 " + IronFist.state(p).empowerT + ")");
			h.assertTrue(!p.isNoGravity(), "중력 복구");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
