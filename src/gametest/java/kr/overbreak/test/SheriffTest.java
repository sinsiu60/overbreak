package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.sheriff.Sheriff;
import kr.overbreak.classes.sheriff.SheriffState;
import kr.overbreak.combat.DamageModifiers;
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
 * 보안관 — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 플레이어 목록에 없어 직업 틱이 저절로 돌지 않으므로 직업 틱(tick)만 직접 부릅니다.
 * 스킬 효과(난사 · 구르기 · 섬광 · 황야의 무법자)는 서버가 매 틱 돌리므로 runAfterDelay 로 기다립니다
 *   — Effects 를 직접 돌리면 같은 시각에 도는 다른 테스트의 스킬까지 빨라집니다.
 * 바닥은 y=0, 표적은 테스트 구역(8칸) 안에 둡니다. 피해는 그 테스트의 시전자가 준 것만 셉니다.
 */
public final class SheriffTest implements CustomTestMethodInvoker {
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

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float pitch) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_sheriff"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, sheriff());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass sheriff() {
		return Classes.byId(Sheriff.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	/** 직업 틱만 n 번 (사격 간격 · 재장전 · 구르기 피해 감소). */
	private static void classTicks(FakePlayer p, int n) {
		for (int i = 0; i < T.of(n); i++) {
			sheriff().tick(p);
		}
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 170, 0.01, "최대 체력 (200 - 30)");
		h.assertTrue(!sheriff().meleeAllowed(), "근접 불가 (좌클릭 = 피스키퍼)");
		h.assertTrue(Sheriff.state(p).ammo == 6, "탄창 6발");
		h.assertTrue(Attachments.profile(p).ultRate == 100, "게이지 피해 20당 2%");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void peacekeeperShotAndFireRate(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		sheriff().basic(p);
		near(h, dealtBy(v, p), 70, 0.01, "6칸 한 발 70");
		h.assertTrue(Sheriff.state(p).ammo == 5, "탄 하나 씀");
		sheriff().basic(p);
		near(h, dealtBy(v, p), 70, 0.01, "0.8초 안에는 다시 안 나감");
		// 먼 거리 피해는 거리만으로 정해집니다 (12칸 표적은 테스트 구역 밖이라 계산으로 확인)
		near(h, Sheriff.shotDamage100(6.0), 7000, 0, "6칸 70");
		near(h, Sheriff.shotDamage100(10.0), 7000, 0, "10칸까지 70");
		near(h, Sheriff.shotDamage100(15.0), 4550, 0, "15칸 45.5 (절반만큼 감소)");
		near(h, Sheriff.shotDamage100(20.0), 2100, 0, "20칸 21 (-70%)");
		near(h, Sheriff.shotDamage100(50.0), 2100, 0, "20칸 밖은 그대로 21");

		FakePlayer head = caster(h, new Vec3(5.5, 0, 0.5), 0.0F);
		Villager v2 = dummy(h, new Vec3(5.5, 0, 6.5));
		sheriff().basic(head);
		near(h, dealtBy(v2, head), 122.5, 0.01, "머리 치명타 175% (70 x 1.75)");
		Classes.clear(head);
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void sixShotsThenReload(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		SheriffState st = Sheriff.state(p);
		for (int i = 0; i < 6; i++) {
			sheriff().basic(p);
			if (i < 5) {
				classTicks(p, 16);
			}
		}
		h.assertTrue(st.ammo == 0 && st.reloadT == T.of(40), "6발 쏘면 곧바로 재장전 시작 (탄 " + st.ammo + ", 재장전 " + st.reloadT + ")");
		classTicks(p, 40);
		h.assertTrue(st.ammo == 6 && st.reloadT == 0, "2초 뒤 6발 (탄 " + st.ammo + ")");
		st.ammo = 3;
		sheriff().reload(p);
		h.assertTrue(st.reloadT == T.of(40), "R 키 재장전");
		classTicks(p, 16);
		sheriff().basic(p);
		h.assertTrue(st.ammo == 3, "재장전 중에는 안 나감");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 180)
	public void fanHammerSixShots(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 3.5));
		sheriff().primary(p);
		h.assertTrue(Attachments.combatant(p).casting, "난사 중 정신집중");
		near(h, dealtBy(v, p), 30, 0.01, "첫 발은 바로 · 정확 30");
		h.runAfterDelay(T.of(14), () -> {
			double dealt = dealtBy(v, p);
			// 3칸 표적이라 퍼져도 거의 맞음 — 발당 30, 탄창 6발이면 전탄 180
			h.assertTrue(dealt >= 120 && dealt <= 180.01 && Math.round(dealt) % 30 == 0, "난사 발당 30 (실측 " + dealt + ")");
			h.assertTrue(!Attachments.combatant(p).casting, "난사 끝나면 정신집중 해제");
			h.assertTrue(Attachments.profile(p).cooldown("sh_fan") == 0, "쿨타임 없음 — 탄창을 씁니다");
			h.assertTrue(Sheriff.state(p).ammo == 0, "탄창을 전부 씀");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest
	public void rollResetsFanAndHalvesDamage(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		SheriffState st0 = Sheriff.state(p);
		st0.ammo = 1;
		sheriff().secondary(p);
		h.assertTrue(st0.ammo == 6, "구르기 → 탄창 전부 회복 (6발)");
		h.assertTrue(Attachments.combatant(p).dashT == T.of(6), "0.3초 구르기");
		SheriffState st = Sheriff.state(p);
		h.assertTrue(st.guardT == T.of(6), "0.3초 피해 감소");
		near(h, DamageModifiers.apply(p, h.getLevel().damageSources().generic(), 40.0F), 20.0F, 0.01, "구르는 중 피해 50%");
		classTicks(p, 6);
		near(h, DamageModifiers.apply(p, h.getLevel().damageSources().generic(), 40.0F), 40.0F, 0.01, "끝나면 원래 피해");
		h.runAfterDelay(T.of(3), () -> {
			h.assertTrue(Attachments.combatant(p).dashT < T.of(6), "구르기 이동이 실제로 진행됨 (남은 틱 " + Attachments.combatant(p).dashT + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void flashbangSlowsSealsAndBreaksChannel(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		sheriff().tertiary(p);
		h.runAfterDelay(T.of(9), () -> {
			near(h, dealtBy(v, p), 25, 0.01, "섬광 피해 25");
			h.assertTrue(Attachments.combatant(v).slowT > 0, "둔화");
			h.assertTrue(Attachments.combatant(v).sealT > 0, "이동기 봉인");
			h.assertTrue(Attachments.combatant(v).stunT == 0, "기절은 아님");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest
	public void breakChannelCancelsDeadeye(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		UltGauge.fill(p);
		sheriff().ult(p);
		h.assertTrue(sheriff().intercept(p, InputRouter.Slot.BASIC), "조준 중 평타 잠김");
		Attachments.combatant(p).breakT = 2;
		h.runAfterDelay(T.of(3), () -> {
			h.assertTrue(!Sheriff.state(p).inUlt() && !Attachments.combatant(p).casting, "섬광(정신집중 끊기)에 조준 취소");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 240)
	public void deadeyeMarksConeAndFires(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager front = dummy(h, new Vec3(1.5, 0, 6.5));
		Villager side = dummy(h, new Vec3(7.5, 0, 1.5));
		UltGauge.fill(p);
		sheriff().ult(p);
		h.assertTrue(Sheriff.state(p).inUlt() && Attachments.combatant(p).casting, "조준 시작");
		h.runAfterDelay(T.of(30), () -> near(h, dealtBy(front, p), 0, 0.01, "조준 중에는 쏘지 않음"));
		h.runAfterDelay(T.of(44), () -> {
			near(h, dealtBy(front, p), 120, 0.01, "앞 90도 안 적 120");
			near(h, dealtBy(side, p), 0, 0.01, "옆(90도 밖) 적은 안 맞음");
			h.assertTrue(!Sheriff.state(p).inUlt() && !Attachments.combatant(p).casting, "끝나면 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
