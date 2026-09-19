package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.shade.Shade;
import kr.overbreak.classes.shade.ShadeState;
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
 * 셰이드 — 체력 · 피해 10배 기준 수치.
 *
 * 가짜 플레이어는 직업 틱이 저절로 돌지 않으므로 쿨타임 · 낙인 시간은 줄지 않습니다 (스킬 효과는 서버가 돌림).
 * 바닥은 y=0, 표적은 테스트 구역(8칸) 안에 둡니다. 피해는 그 테스트의 시전자가 준 것만 셉니다.
 */
public final class ShadeTest implements CustomTestMethodInvoker {
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
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_shade"));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, pitch); // yaw 0 = +Z
		Classes.give(p, shade());
		return p;
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel, double health) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth((float) health);
		return v;
	}

	private static PvpClass shade() {
		return Classes.byId(Shade.ID);
	}

	private static void near(GameTestHelper h, double actual, double expected, double tol, String what) {
		h.assertTrue(Math.abs(actual - expected) <= tol, what + " 기대 " + expected + " 실측 " + actual);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		near(h, p.getMaxHealth(), 180, 0.01, "최대 체력 (200 - 20)");
		h.assertTrue(shade().meleeAllowed(), "근접 직업");
		near(h, shade().meleeArcDegrees(), 80, 0.01, "평타 부채꼴 80도");
		h.assertTrue(Attachments.profile(p).atkSpeed == 125, "공격속도 1.25");
		h.assertTrue(Attachments.profile(p).ultRate == 200, "게이지 피해 10당 2%");
		h.assertTrue(shade().hudSlots(p).size() == 3, "스킬 HUD 3칸");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 180)
	public void rendDashesMarksAndMeleeBursts(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		// 가짜 플레이어는 서버에서 실제로 움직이지 않으므로 (이동은 클라이언트가 보냄) 표적을 출발점 판정 안에 둡니다
		Villager v = dummy(h, new Vec3(1.5, 0, 1.8), 1000);
		shade().primary(p);
		h.assertTrue(Attachments.combatant(p).dashT == T.of(6), "0.3초 돌진");
		h.assertTrue(Attachments.profile(p).cooldown("sd_rend") == T.of(160), "쿨타임 8초");
		h.runAfterDelay(T.of(9), () -> {
			near(h, dealtBy(v, p), Shade.REND_DAMAGE, 0.01, "지나간 적 30");
			ShadeState st = Shade.state(p);
			h.assertTrue(st.marked(v), "낙인");
			h.assertTrue(Attachments.combatant(p).dashT < 6, "돌진이 진행됨 (남은 틱 " + Attachments.combatant(p).dashT + ")");
			shade().onMeleeHit(p, v);
			near(h, dealtBy(v, p), Shade.REND_DAMAGE + Shade.MARK_DAMAGE, 0.01, "평타가 낙인을 터뜨려 25 추가");
			h.assertTrue(!st.marked(v), "터진 낙인은 사라짐");
			shade().onMeleeHit(p, v);
			near(h, dealtBy(v, p), Shade.REND_DAMAGE + Shade.MARK_DAMAGE, 0.01, "낙인이 없으면 추가 피해 없음");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void takedownResetsRend(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 1.8), 20);
		shade().primary(p);
		h.runAfterDelay(T.of(8), () -> {
			h.assertTrue(!v.isAlive(), "30 피해에 체력 20 표적 처치");
			h.assertTrue(Attachments.profile(p).cooldown("sd_rend") == 0, "처치하면 그림자 가르기 초기화");
			Classes.clear(p);
			h.succeed();
		});
	}

	@GameTest
	public void evadeAbsorbsAndCountersBehindAttacker(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(4.5, 0, 5.5), 1000);
		h.assertTrue(!Shade.absorb(p, h.getLevel().damageSources().mobAttack(v)), "평소에는 피해를 받음");
		Attachments.profile(p).setCooldown("sd_rend", 100);
		shade().secondary(p);
		ShadeState st = Shade.state(p);
		h.assertTrue(st.evadeTicks() == 14, "0.7초 회피");
		h.assertTrue(Shade.absorb(p, h.getLevel().damageSources().mobAttack(v)), "회피 중 피해 무효");
		h.assertTrue(p.distanceTo(v) < 1.6, "공격자 곁으로 순간이동 (거리 " + p.distanceTo(v) + ")");
		h.assertTrue(st.marked(v), "공격자에게 낙인");
		h.assertTrue(Attachments.profile(p).cooldown("sd_rend") == 0, "반격 → 그림자 가르기 초기화");
		h.assertTrue(st.evadeTicks() == 0, "반격하면 회피 끝");
		h.assertTrue(Shade.absorb(p, h.getLevel().damageSources().generic()), "반격 직후 0.3초는 피해 무효");
		for (int i = 0; i < T.of(6); i++) {
			shade().tick(p);
		}
		h.assertTrue(!Shade.absorb(p, h.getLevel().damageSources().generic()), "그 뒤에는 다시 피해를 받음");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest(maxTicks = 180)
	public void kunaiHitsThenStepsBehind(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5), 1000);
		shade().tertiary(p);
		h.assertTrue(Attachments.profile(p).cooldown("sd_kunai") == T.of(160), "쿨타임 8초");
		h.runAfterDelay(T.of(6), () -> {
			near(h, dealtBy(v, p), Shade.KUNAI_DAMAGE, 0.01, "표창 20");
			ShadeState st = Shade.state(p);
			h.assertTrue(st.marked(v), "낙인");
			h.assertTrue(Attachments.combatant(v).slowT > 0, "둔화");
			h.assertTrue(st.stepTicks() > 0, "그림자 걸음 대기");
			shade().tertiary(p);
			h.assertTrue(st.stepping() && st.stepTicks() == 0, "다시 F → 등 뒤로 날아가기 시작 (한 번만)");
			h.runAfterDelay(T.of(6), () -> {
				h.assertTrue(!st.stepping(), "0.2초 뒤 도착");
				h.assertTrue(p.distanceTo(v) < 1.6, "적 곁에 도착 (거리 " + p.distanceTo(v) + ")");
				Classes.clear(p);
				h.succeed();
			});
		});
	}

	@GameTest(maxTicks = 180)
	public void bladeStormStrikesWhileInvulnerable(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 2.5), 1000);
		UltGauge.fill(p);
		shade().ult(p);
		ShadeState st = Shade.state(p);
		h.assertTrue(st.storming(), "천검난무 시작");
		h.assertTrue(Attachments.combatant(p).ccImmune && Attachments.combatant(p).casting, "군중 제어 면역 · 행동 잠김");
		h.assertTrue(shade().intercept(p, InputRouter.Slot.PRIMARY), "다른 스킬 잠김");
		h.assertTrue(Shade.absorb(p, h.getLevel().damageSources().generic()), "베는 동안 피해 무효");
		h.runAfterDelay(T.of(30), () -> {
			double dealt = dealtBy(v, p);
			// 이웃 테스트의 생명체가 10칸 안에 있으면 베기가 나뉩니다 — 한 명뿐이면 150
			h.assertTrue(dealt >= Shade.STORM_DAMAGE * 2 - 0.01 && dealt <= Shade.STORM_DAMAGE * 6 + 0.01
					&& Math.abs(dealt / Shade.STORM_DAMAGE - Math.round(dealt / Shade.STORM_DAMAGE)) < 0.01, "한 번에 25 (실측 " + dealt + ")");
			h.assertTrue(!st.storming() && !Attachments.combatant(p).casting && !Attachments.combatant(p).ccImmune, "끝나면 해제");
			h.assertTrue(Attachments.profile(p).ultCharge < 100, "게이지 사용");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 스킬 → 평타 → 가르기 연계가 바닐라 무적 시간에 씹히지 않아야 합니다 (0.1f 수정). */
	@GameTest(maxTicks = 160)
	public void comboPiercesInvulnerability(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5), 0.0F);
		Villager v = dummy(h, new Vec3(1.5, 0, 3.0), 1000);
		// 먼저 스킬로 때려 0.5초 무적 시간을 만듭니다
		kr.overbreak.combat.SkillDamage.deal(v, p, 100, kr.overbreak.combat.SkillDamage.Kind.NORMAL);
		float afterSkill = v.getHealth();
		h.assertTrue(afterSkill < 1000.0F, "스킬 피해");

		kr.overbreak.combat.MeleeCleave.swing(p, shade());
		float afterMelee = v.getHealth();
		h.assertTrue(afterMelee < afterSkill, "직전 스킬 무적에 평타가 씹히지 않음 (실측 " + afterMelee + ")");

		shade().primary(p);
		h.runAfterDelay(T.of(3), () -> {
			h.assertTrue(v.getHealth() < afterMelee, "평타 직후 가르기도 들어감 (실측 " + v.getHealth() + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 잔영 회피로 반격에 성공하면 그 쿨타임을 절반 돌려받습니다 (0.1f). */
	@GameTest
	public void counterRefundsEvadeCooldown(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(2.5, 0, 2.5), 0.0F);
		FakePlayer foe = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_foe"));
		Vec3 at = h.absoluteVec(new Vec3(2.5, 0, 4.0));
		foe.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);

		shade().secondary(p);
		int full = Attachments.profile(p).cooldown("sd_evade");
		h.assertTrue(full == T.of(200), "잔영 회피 쿨타임 10초 (실측 " + full + ")");
		h.assertTrue(Shade.absorb(p, h.getLevel().damageSources().playerAttack(foe)), "회피 중 피해 무효");
		int left = Attachments.profile(p).cooldown("sd_evade");
		h.assertTrue(left == full / 2, "반격 성공 → 쿨타임 절반 반환 (실측 " + left + " / " + full + ")");
		Classes.clear(p);
		Classes.clear(foe);
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
