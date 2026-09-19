package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.hammer.HammerKnight;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** 햄머나이트 — 체력 · 피해 10배 기준 수치. */
public final class HammerKnightTest implements CustomTestMethodInvoker {

	private static FakePlayer caster(GameTestHelper h, Vec3 rel) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_hammer"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, 0.0F); // yaw 0 = +Z
		Classes.give(p, Classes.byId(HammerKnight.ID));
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass hk() {
		return Classes.byId(HammerKnight.ID);
	}

	/** 시험 구역 가장자리 밖 허수아비가 벽 끼임 피해(1)를 받을 수 있어 2 까지 허용합니다. */
	private static void near(GameTestHelper h, double actual, double expected, String what) {
		h.assertTrue(Math.abs(actual - expected) <= 2.0, what + " 기대 " + expected + " 실측 " + actual);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5));
		near(h, p.getMaxHealth(), 300, "최대 체력 (200 + 100)");
		h.assertTrue(Attachments.profile(p).atkSpeed == 80, "공격속도 0.8");
		h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "오른손 철퇴");
		h.assertTrue(!p.getOffhandItem().isEmpty(), "왼손 방패");
		Classes.clear(p);
		h.assertTrue(p.getOffhandItem().isEmpty(), "해제하면 방패 회수");
		h.succeed();
	}

	@GameTest(maxTicks = 120)
	public void smashConeDamageAndStun(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 1, 3.5));
		Villager front = dummy(h, new Vec3(4.5, 1, 5.5));   // 정면 2칸
		Villager behind = dummy(h, new Vec3(4.5, 1, 1.0));  // 뒤 2.5칸
		hk().primary(p);
		h.assertTrue(Attachments.combatant(p).casting, "정신집중");
		h.runAfterDelay(T.of(8), () -> near(h, front.getHealth(), 1000, "0.5초 전에는 무피해"));
		h.runAfterDelay(T.of(13), () -> {
			near(h, front.getHealth(), 960, "지면 분쇄 피해 40");
			h.assertTrue(Attachments.combatant(front).stunT > 0, "기절");
			near(h, behind.getHealth(), 1000, "뒤는 안 맞음");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest
	public void concussionOncePerStun(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 1, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 1, 3.0));
		CrowdControl.stun(v, 40);
		Combatant c = Attachments.combatant(v);
		hk().onMeleeHit(p, v);
		near(h, v.getHealth(), 970, "첫 타 뇌진탕 30");
		h.assertTrue(c.stunT == T.of(50), "기절 0.5초 연장 (실측 " + c.stunT + ")");
		hk().onMeleeHit(p, v);
		near(h, v.getHealth(), 970, "같은 기절에는 다시 안 들어감");
		h.assertTrue(c.stunT == T.of(50), "연장도 한 번만 (실측 " + c.stunT + ")");

		// 풀렸다가 새로 기절하면 다시 발동
		CrowdControl.clearAll(v);
		CrowdControl.stun(v, 20);
		hk().onMeleeHit(p, v);
		near(h, v.getHealth(), 940, "새 기절에는 다시 30");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 120)
	public void crushPullsAndSeals(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 1, 4.5));
		Villager v = dummy(h, new Vec3(4.5, 1, 8.0));      // 3.5칸
		Villager far = dummy(h, new Vec3(4.5, 1, 12.0));   // 7.5칸 (끌어오는 범위 6칸 밖)
		hk().tertiary(p);
		h.runAfterDelay(T.of(6), () -> near(h, v.getHealth(), 1000, "선동작 중에는 안 끌림"));
		h.runAfterDelay(T.of(13), () -> {
			double d = Math.sqrt(Math.pow(v.getX() - p.getX(), 2) + Math.pow(v.getZ() - p.getZ(), 2));
			h.assertTrue(Math.abs(d - 1.0) < 0.05, "발밑 1칸으로 (거리 " + d + ")");
			near(h, v.getHealth(), 970, "피해 30");
			h.assertTrue(Attachments.combatant(v).sealT > 0, "균열 지대 봉인");
			h.assertTrue(Attachments.combatant(p).sealT == 0, "시전자는 안 걸림");
			near(h, far.getHealth(), 1000, "끌어오는 범위 밖은 영향 없음");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 240)
	public void quakeConeKnockdown(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(4.5, 1, 1.5));
		Villager front = dummy(h, new Vec3(4.5, 1, 11.5));  // 정면 10칸
		Villager wide = dummy(h, new Vec3(12.5, 1, 9.5));   // 45도 옆 (70도 부채꼴 밖)
		UltGauge.fill(p);
		hk().ult(p);
		h.runAfterDelay(T.of(40), () -> {
			// 10칸: 100 - 50 x 0.5 = 75 피해 + 뇌진탕 30 = 체력 895
			near(h, front.getHealth(), 895, "거리 10칸 피해 75 + 뇌진탕 30");
			h.assertTrue(Attachments.combatant(front).knockT > 0, "넘어뜨림");
			h.assertTrue(Attachments.combatant(front).stunT == 0, "기절이 아니라 넘어뜨림");
			near(h, wide.getHealth(), 1000, "70도 밖은 안 맞음");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
