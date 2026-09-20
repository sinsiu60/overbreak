package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.brute.Brute;
import kr.overbreak.core.Attachments;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** 투귀 — 투기 스택 · 강타 · 돌개바람 · 전열 재정비 · 무쌍. */
public final class BruteTest implements CustomTestMethodInvoker {
	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float yaw) {
		ServerLevel level = h.getLevel();
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "ob_brute"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, yaw, 0);
		Classes.give(p, brute());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static PvpClass brute() {
		return Classes.byId(Brute.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, String what) {
		h.assertTrue(Math.abs(actual - expected) < 0.51, what + " 기대 " + expected + " 실측 " + actual);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 240, "최대 체력 (200 + 40)");
		h.assertTrue(brute().meleeAllowed(), "근접 직업");
		h.assertTrue(!brute().meleeKnockback(), "평타 넉백 없음");
		h.assertTrue(Attachments.profile(p).ultRate == 200, "게이지 피해 10당 2%");
		h.assertTrue(!p.getInventory().getItem(0).isEmpty(), "대검 지급");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 400)
	public void fervorStacksAndDecays(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 3.5));
		h.assertTrue(Brute.fervor(p) == 0, "처음에는 0스택");
		for (int i = 0; i < 4; i++) {
			// 평타가 들어간 순간의 패시브 훅 (공격속도 · 무적 시간에 막히지 않게 직접 부릅니다)
			brute().onMeleeHit(p, v);
		}
		h.assertTrue(Brute.fervor(p) == 4, "때린 만큼 쌓임 (실측 " + Brute.fervor(p) + ")");
		// 5초가 지나면 한 번에 풀립니다
		h.onEachTick(() -> brute().tick(p));
		h.runAfterDelay(T.of(104), () -> {
			h.assertTrue(Brute.fervor(p) == 0, "5초 뒤 풀림 (실측 " + Brute.fervor(p) + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void heavyBlowStunsAndScales(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 4.0));
		brute().primary(p);
		h.assertTrue(Attachments.combatant(p).casting, "준비 중 정신집중");
		h.assertTrue(Attachments.profile(p).cooldown("br_blow") == T.of(140), "쿨타임 7초");
		h.runAfterDelay(T.of(3), () -> near(h, v.getHealth(), 1000, "준비(0.3초) 중에는 피해 없음"));
		h.runAfterDelay(T.of(9), () -> {
			// 투기 0스택에서 기본 70
			near(h, 1000 - v.getHealth(), 70, "강타 70");
			h.assertTrue(Attachments.combatant(v).stunT > 0, "기절");
			h.assertTrue(Brute.fervor(p) == 3, "맞히면 투기 3스택 (실측 " + Brute.fervor(p) + ")");
			h.assertTrue(!Attachments.combatant(p).casting, "내리친 뒤 정신집중 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void whirlSweepsAround(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(2.5, 0, 2.5), 0.0F);
		Villager front = dummy(h, new Vec3(2.5, 0, 4.5));
		Villager behind = dummy(h, new Vec3(2.5, 0, 0.5));
		double baseSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		brute().secondary(p);
		h.assertTrue(Attachments.profile(p).cooldown("br_whirl") == T.of(160), "쿨타임 8초");
		h.assertTrue(Attachments.combatant(p).ccImmune, "도는 동안 저지불가");
		double whirlSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		h.assertTrue(whirlSpeed > baseSpeed, "도는 동안 더 빠름 (0.1e)");
		h.runAfterDelay(T.of(24), () -> {
			// 0.2초마다 15 x 5번 = 75, 등 뒤도 함께
			near(h, 1000 - front.getHealth(), 75, "앞쪽 전부 75");
			near(h, 1000 - behind.getHealth(), 75, "등 뒤도 맞음");
			h.assertTrue(!Attachments.combatant(p).ccImmune, "끝나면 저지불가 해제");
			// 끝나면 돌개바람 보정은 사라집니다 (투기 스택 보정은 남아 기본보다 조금 빠름)
			double after = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
			h.assertTrue(after < whirlSpeed && after >= baseSpeed, "끝나면 돌개바람 보정만 빠짐 (실측 " + after + ")");
			h.assertTrue(Brute.fervor(p) > 0, "베면서 투기가 쌓임 (실측 " + Brute.fervor(p) + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void regroupHeals(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		p.setHealth(100.0F);
		brute().tertiary(p);
		h.assertTrue(Attachments.combatant(p).casting, "채널링 중 정신집중");
		h.assertTrue(Attachments.profile(p).cooldown("br_regroup") == T.of(240), "쿨타임 12초");
		h.runAfterDelay(T.of(24), () -> {
			near(h, p.getHealth(), 160, "1초 동안 60 회복");
			h.assertTrue(Brute.fervor(p) == 5, "끝까지 버티면 투기 5스택 (실측 " + Brute.fervor(p) + ")");
			h.assertTrue(!Attachments.combatant(p).casting, "끝나면 정신집중 해제");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 전열 재정비 중에는 받는 피해가 40% 줄어듭니다 (0.1f). */
	@GameTest
	public void regroupReducesDamage(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager foe = dummy(h, new Vec3(1.5, 0, 3.5));
		// 가짜 플레이어는 바닐라가 피해 자체를 막으므로, 받는 피해 수정 단계를 직접 확인합니다
		var type = kr.overbreak.combat.SkillDamage.type(h.getLevel(), kr.overbreak.combat.SkillDamage.Kind.MULTI);
		var source = new net.minecraft.world.damagesource.DamageSource(type, foe, foe);
		near(h, kr.overbreak.combat.DamageModifiers.apply(p, source, 100.0F), 100, "그냥 맞으면 100 그대로");
		brute().tertiary(p);
		near(h, kr.overbreak.combat.DamageModifiers.apply(p, source, 100.0F), 60, "숨 고르는 동안은 60");
		Classes.clear(p);
		h.succeed();
	}

	/** E(인벤토리 키)로 들어온 신호가 액티브3 으로 이어지는가 — 0.2 에서 F 에서 옮겨 왔습니다. */
	@GameTest
	public void tertiaryKeyRuns(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		String regroup = Brute.skillKeys().get(2);
		h.assertTrue(Attachments.profile(p).cooldown(regroup) == 0, "아직 쓰지 않음");
		kr.overbreak.input.InputRouter.onTertiary(p);
		h.assertTrue(Attachments.profile(p).cooldown(regroup) > 0, "E 한 번에 전열 재정비가 나가고 쿨이 돎");
		Classes.clear(p);
		h.succeed();
	}

	/** 전열 재정비는 왼손에 강화 포션을 쥐고 시작해, 끝나면 병을 내려놓습니다 (0.2a). */
	@GameTest(maxTicks = 200)
	public void regroupHoldsPotion(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		h.assertTrue(p.getOffhandItem().isEmpty(), "쓰기 전에는 왼손이 비어 있음");
		brute().tertiary(p);
		h.assertTrue(!p.getOffhandItem().isEmpty(), "쓰는 동안 왼손에 강화 포션");
		h.onEachTick(() -> brute().tick(p));
		// 1초 채널링이 끝나면 병을 던져 깨뜨립니다
		h.runAfterDelay(T.of(30), () -> {
			h.assertTrue(p.getOffhandItem().isEmpty(), "다 마시면 병이 손에서 사라짐");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 440)
	public void rampageBuffs(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		UltGauge.fill(p);
		brute().ult(p);
		h.assertTrue(Brute.rampaging(p), "무쌍 시작");
		h.assertTrue(Brute.fervor(p) == 10, "투기 최대 (실측 " + Brute.fervor(p) + ")");
		h.assertTrue(Attachments.combatant(p).dmgMul == 150, "피해 1.5배");
		near(h, brute().meleeRange(p), 6.4, "평타 사거리 2배");
		h.onEachTick(() -> brute().tick(p));
		h.runAfterDelay(T.of(124), () -> {
			h.assertTrue(!Brute.rampaging(p), "6초 뒤 끝남");
			h.assertTrue(Attachments.combatant(p).dmgMul == 100, "피해 배율 복구");
			near(h, brute().meleeRange(p), 3.2, "평타 사거리 복구");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
