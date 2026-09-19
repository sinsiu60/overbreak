package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.thunder.Thunder;
import kr.overbreak.classes.thunder.ThunderState;
import kr.overbreak.core.Attachments;
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
 * 뇌신 — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 직업 틱이 저절로 돌지 않으므로 사격 간격 · 쿨타임은 직업 틱을 직접 불러 줄입니다 (스킬 효과는 서버가 돌림).
 * 바닥은 y=0, 표적은 테스트 구역(8칸) 안에 둡니다. 피해는 그 테스트의 시전자가 준 것만 셉니다.
 */
public final class ThunderTest implements CustomTestMethodInvoker {
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
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_thunder"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, thunder());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass thunder() {
		return Classes.byId(Thunder.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	private static void classTicks(FakePlayer p, int n) {
		for (int i = 0; i < T.of(n); i++) {
			thunder().tick(p);
		}
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 225, 0.01, "최대 체력 (200 + 25)");
		h.assertTrue(!thunder().meleeAllowed(), "근접 불가 (좌클릭 = 뇌격)");
		h.assertTrue(Attachments.profile(p).ultRate == 143, "게이지 피해 14당 2%");
		h.assertTrue(thunder().hudSlots(p).size() == 3, "스킬 HUD 3칸");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void boltChainsAndThirdHitDischarges(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager a = dummy(h, new Vec3(1.5, 0, 6.5));
		Villager b = dummy(h, new Vec3(4.5, 0, 6.5));
		ThunderState st = Thunder.state(p);
		thunder().basic(p);
		near(h, dealtBy(a, p), Thunder.BOLT_DAMAGE, 0.01, "뇌격 18");
		near(h, dealtBy(b, p), Thunder.CHAIN_DAMAGE, 0.01, "곁의 적에게 연쇄 9");
		h.assertTrue(st.charge(a) == 1 && st.charge(b) == 0, "맞은 적만 정전기 1스택");
		thunder().basic(p);
		near(h, dealtBy(a, p), Thunder.BOLT_DAMAGE, 0.01, "0.55초 안에는 다시 안 나감");
		classTicks(p, 11);
		thunder().basic(p);
		h.assertTrue(st.charge(a) == 2, "2스택");
		classTicks(p, 11);
		thunder().basic(p);
		near(h, dealtBy(a, p), Thunder.BOLT_DAMAGE * 3 + Thunder.DISCHARGE_DAMAGE, 0.01, "3번째에 감전 25 추가");
		h.assertTrue(st.charge(a) == 0, "감전하면 스택 초기화");
		h.assertTrue(Attachments.combatant(a).stunT > 0, "감전 기절");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 120)
	public void stepIsInvulnerableAndHits(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		// 가짜 플레이어는 실제로 움직이지 않으므로 표적을 출발점 판정 안에 둡니다
		Villager v = dummy(h, new Vec3(1.5, 0, 1.8));
		h.assertTrue(!Thunder.absorb(p, h.getLevel().damageSources().generic()), "평소에는 피해를 받음");
		thunder().primary(p);
		h.assertTrue(Thunder.state(p).stepping(), "섬전 시작 (기 모으기)");
		h.assertTrue(!Thunder.absorb(p, h.getLevel().damageSources().generic()), "기 모으는 동안은 아직 무적이 아님");
		h.assertTrue(Attachments.profile(p).cooldown("th_step") == T.of(180), "쿨타임 9초");
		h.runAfterDelay(T.of(5), () -> {
			h.assertTrue(Attachments.combatant(p).dashT > 0, "0.2초 기 모으기 뒤 0.2초 섬전");
			h.assertTrue(Thunder.absorb(p, h.getLevel().damageSources().generic()), "달리는 동안 피해 무효");
		});
		h.runAfterDelay(T.of(10), () -> {
			near(h, dealtBy(v, p), Thunder.STEP_DAMAGE, 0.01, "지나간 적 25");
			h.assertTrue(Thunder.state(p).charge(v) == 1, "정전기");
			h.assertTrue(!Thunder.absorb(p, h.getLevel().damageSources().generic()), "끝나면 다시 피해를 받음");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void stormFieldZapsAndSlows(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 12.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		thunder().secondary(p);
		h.runAfterDelay(T.of(25), () -> {
			double dealt = dealtBy(v, p);
			h.assertTrue(dealt >= 16 - 0.01, "0.5초마다 8 (1.25초에 2번 이상, 실측 " + dealt + ")");
			h.assertTrue(Attachments.combatant(v).slowT > 0, "구름 아래 둔화");
			h.assertTrue(Attachments.profile(p).cooldown("th_field") == T.of(260), "쿨타임 13초");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void smiteStrikesAfterWarning(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		thunder().tertiary(p);
		h.runAfterDelay(T.of(6), () -> near(h, dealtBy(v, p), 0, 0.01, "예고 중에는 피해 없음"));
		h.runAfterDelay(T.of(15), () -> {
			near(h, dealtBy(v, p), Thunder.SMITE_DAMAGE, 0.01, "벼락 45");
			h.assertTrue(Attachments.combatant(v).airT > 0, "띄우기");
			h.assertTrue(Thunder.state(p).charge(v) == 1, "정전기");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void descentStrikesThreeTimes(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		UltGauge.fill(p);
		thunder().ult(p);
		ThunderState st = Thunder.state(p);
		h.assertTrue(st.descending() && Attachments.combatant(p).ccImmune, "뇌신강림 · 군중 제어 면역");
		h.runAfterDelay(T.of(70), () -> {
			near(h, dealtBy(v, p), Thunder.ULT_DAMAGE * 3 + Thunder.DISCHARGE_DAMAGE, 0.01, "벼락 35 x 3 + 세 번째 감전 25");
			h.assertTrue(!st.descending() && !Attachments.combatant(p).ccImmune, "끝나면 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
