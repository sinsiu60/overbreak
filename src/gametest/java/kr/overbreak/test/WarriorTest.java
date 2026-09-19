package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.core.Attachments;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * 워리어 — README "검증 결과 (26.2 실서버)" 의 숫자와 같은지 봅니다.
 * 데이터팩에서는 플레이어 전용 경로라 헤드리스로 못 재던 것들을 가짜 플레이어로 잽니다.
 */
public final class WarriorTest implements CustomTestMethodInvoker {

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float yaw) {
		ServerLevel level = h.getLevel();
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "ob_test"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, yaw, 0);
		return p;
	}

	/** 체력 1000 · 제자리 · 직업 없음. 넉백은 그대로 받습니다 (데이터팩 더미와 같은 조건). */
	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass warrior() {
		return Classes.byId(Warrior.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, String what) {
		h.assertTrue(Math.abs(actual - expected) < 0.01, what + " 기대 " + expected + " 실측 " + actual);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5), 0);
		Classes.give(p, warrior());
		near(h, p.getMaxHealth(), 275, "최대 체력 (기본 200 + 워리어 75)");
		h.assertTrue(Attachments.profile(p).atkSpeed == 100, "공격속도 x100");
		h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "핫바 0 도끼");
		h.assertTrue(p.getInventory().getItem(1).isEmpty() && p.getInventory().getItem(2).isEmpty(), "스킬은 HUD — 핫바에는 무기 한 칸만");
		Classes.clear(p);
		near(h, p.getMaxHealth(), 20, "해제 후 체력");
		h.succeed();
	}

	@GameTest(maxTicks = 180)
	public void slayHitsAndBleeds(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5), 0);
		Classes.give(p, warrior());
		Villager v = dummy(h, new Vec3(1.5, 1, 3.5));
		warrior().primary(p);
		h.assertTrue(Attachments.combatant(p).casting, "정신집중 시작");
		h.runAfterDelay(T.of(13), () -> near(h, v.getHealth(), 1000, "0.7초 전에는 무피해"));
		h.runAfterDelay(T.of(16), () -> {
			near(h, v.getHealth(), 920, "살육 피해 80");
			h.assertTrue(Warrior.bleedStacks(v) == 2, "출혈 2 (실측 " + Warrior.bleedStacks(v) + ")");
			h.assertTrue(!Attachments.combatant(p).casting, "발동 뒤 정신집중 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void furyBoostsSkillDamage(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5), 0);
		Classes.give(p, warrior());
		Villager v = dummy(h, new Vec3(1.5, 1, 3.5));
		warrior().secondary(p);
		h.assertTrue(Attachments.combatant(p).dmgMul == 115, "분노 배율 115 (공격력 +15%)");
		warrior().primary(p);
		h.runAfterDelay(T.of(16), () -> {
			near(h, v.getHealth(), 908, "포효 중 살육 92 (80 x 1.15)");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void bleedBurst(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5), 0);
		Classes.give(p, warrior());
		p.setHealth(200);
		Attachments.profile(p).setCooldown("wr_slay", 100);
		Villager v = dummy(h, new Vec3(1.5, 1, 3.5));
		for (int i = 0; i < 5; i++) {
			warrior().onMeleeHit(p, v);
		}
		near(h, v.getHealth(), 950, "출혈 폭발 50");
		near(h, p.getHealth(), 250, "시전자 회복 50");
		h.assertTrue(Attachments.profile(p).cooldown("wr_slay") == T.of(60), "살육 쿨 -40");
		h.assertTrue(Warrior.bleedStacks(v) == 0, "스택 초기화");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 300)
	public void chainPullsTarget(GameTestHelper h) {
		// yaw 0 = +Z. 대상은 정면 5칸.
		// 둘 다 바닥(상대 y 0)에 둡니다 — 가짜 플레이어는 틱이 돌지 않아 떨어지지 않으므로, 공중에 두면
		// 준비(0.4초) 동안 허수아비만 바닥으로 떨어져 사슬이 머리 위로 지나갑니다.
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0);
		Classes.give(p, warrior());
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		boolean[] arrived = {false};
		Warrior.grabArriveListener = (c, e) -> arrived[0] = e == v;
		warrior().tertiary(p);
		h.runAfterDelay(T.of(6), () -> {
			near(h, v.getHealth(), 1000, "준비(0.4초) 중에는 던지지 않음");
			h.assertTrue(Attachments.combatant(p).casting, "준비 중 정신집중");
		});
		// 적중 틱을 기록 (준비 8틱 + 비행 몇 틱)
		long start = h.getTick();
		long[] hitTick = {-1};
		boolean[] castingAtHit = {false};
		h.onEachTick(() -> {
			if (hitTick[0] < 0 && v.getHealth() < 1000) {
				hitTick[0] = h.getTick() - start;
				castingAtHit[0] = Attachments.combatant(p).casting;
			}
		});
		h.runAfterDelay(T.of(65), () -> {
			h.assertTrue(hitTick[0] >= T.of(8) && hitTick[0] <= T.of(20), "준비 뒤 적중 (적중 틱 " + hitTick[0] + ", 체력 " + v.getHealth() + ")");
			h.assertTrue(!castingAtHit[0], "던진 뒤 정신집중 해제");
			near(h, 1000 - v.getHealth(), 25, "사슬 피해 25");
			h.assertTrue(arrived[0], "앞까지 끌려옴 (거리 " + v.position().distanceTo(p.position()) + ")");
			// 시전자(+Z 를 봄)를 뚫고 지나가지 않고 앞에 멈춤
			double ahead = v.getZ() - p.getZ();
			h.assertTrue(ahead > 0.8 && ahead < 2.0, "시전자 앞에 멈춤 (앞 거리 " + ahead + ")");
			Warrior.grabArriveListener = (c, e) -> {};
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void ultImpactAndStun(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5), 0);
		Classes.give(p, warrior());
		Villager v = dummy(h, new Vec3(1.5, 1, 5.5));
		UltGauge.fill(p);
		warrior().ult(p);
		near(h, v.getHealth(), 940, "처형장 발동 60");
		h.assertTrue(Attachments.combatant(v).stunT == T.of(20), "1초 기절 (실측 " + Attachments.combatant(v).stunT + ")");
		// 시전 순간 0 으로 비운 뒤, 처형장 피해 60 x 2%/피해 10 = 12% 가 다시 찹니다.
		h.assertTrue(Attachments.profile(p).ultCharge == 7, "게이지 소모 후 궁 피해만큼 충전 — 피해 16당 2% (실측 " + Attachments.profile(p).ultCharge + ")");
		h.assertTrue(p.getInventory().getItem(3).isEmpty(), "궁극기 아이템 회수");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 120)
	public void ultCageOnGround(GameTestHelper h) {
		// 공중 4칸에서 시전 — 케이지 모델은 바닥(상대 y=1)에 서야 함
		FakePlayer p = caster(h, new Vec3(4.5, 5, 4.5), 0);
		Classes.give(p, warrior());
		UltGauge.fill(p);
		warrior().ult(p);
		var displays = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
				p.getBoundingBox().inflate(10.0, 12.0, 10.0));
		h.assertTrue(!displays.isEmpty(), "케이지 모델 생성");
		// 가운데 말뚝(시전자 바로 아래)으로 판단 — 둘레는 시험 구역 밖이라 바닥 블록이 없을 수 있음
		var stake = displays.stream().min(java.util.Comparator.comparingDouble(
				d -> (d.getX() - p.getX()) * (d.getX() - p.getX()) + (d.getZ() - p.getZ()) * (d.getZ() - p.getZ()))).orElseThrow();
		double minY = displays.stream().mapToDouble(d -> d.getY()).min().orElse(0);
		double maxY = displays.stream().mapToDouble(d -> d.getY()).max().orElse(0);
		var below = net.minecraft.core.BlockPos.containing(stake.getX(), stake.getY() - 0.5, stake.getZ());
		var at = net.minecraft.core.BlockPos.containing(stake.getX(), stake.getY() + 0.5, stake.getZ());
		boolean onFloor = !h.getLevel().getBlockState(below).isAir() && h.getLevel().getBlockState(at).isAir();
		h.assertTrue(onFloor && Math.abs(stake.getY() - minY) < 0.05 && stake.getY() < p.getY() - 2.0 && maxY - minY < 3.1,
				"바닥에 설치 (시전자 " + p.getY() + " 말뚝 " + stake.getY() + " 최저 " + minY + " 최고 " + maxY + " 아래블록 " + h.getLevel().getBlockState(below) + ")");
		Classes.clear(p);
		displays.forEach(d -> d.discard());
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
