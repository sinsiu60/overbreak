package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.gunslinger.AeroAcrobatics;
import kr.overbreak.classes.gunslinger.AeroDrift;
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

	/** 땅에서 쏘면 20, 공중에서 쏘면 치명타 30. 0.2초에 한 발. */
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

		FakePlayer air = caster(h, new Vec3(5.5, 0, 0.5), CHEST_PITCH);
		Villager v2 = dummy(h, new Vec3(5.5, 0, 6.5));
		air.setOnGround(false);
		gs().basic(air);
		near(h, dealtBy(v2, air), 30, 0.01, "공중 치명타 150% (20 x 1.5)");
		Classes.clear(air);
		Classes.clear(p);
		h.succeed();
	}

	/** 공중 명중마다 반동 도약 · 사선 앵커 쿨타임이 0.5초씩 깎입니다. */
	@GameTest
	public void airHitRefundsCooldowns(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5), CHEST_PITCH);
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

	/** 곡예 난사 — 여덟 발 · 도는 동안 무적 · 웅크린 채 우클릭으로만 나감. */
	@GameTest(maxTicks = 200)
	public void acrobaticsEightShots(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(3.5, 0, 3.5), 0.0F);
		p.setOnGround(true);
		Villager v = dummy(h, new Vec3(3.5, 0, 5.5));
		p.setShiftKeyDown(true);
		gs().primary(p);
		GunslingerState st = Gunslinger.state(p);
		h.assertTrue(st.acrobatics != null, "웅크린 채 우클릭 = 곡예 난사");
		h.assertTrue(Gunslinger.absorb(p, h.getLevel().damageSources().generic()), "도는 동안 무적");
		h.assertTrue(Attachments.profile(p).cooldown(Gunslinger.ACRO) == T.of(AeroAcrobatics.COOLDOWN), "쿨타임 9초");
		h.runAfterDelay(T.of(AeroAcrobatics.DURATION + 6), () -> {
			h.assertTrue(st.acrobatics == null, "0.8초 뒤 끝남");
			h.assertTrue(!Gunslinger.absorb(p, h.getLevel().damageSources().generic()), "끝나면 무적 풀림");
			// 2칸 앞 표적은 여덟 발 가운데 정면 한 발만 확실히 맞습니다 (나머지는 사방으로)
			h.assertTrue(dealtBy(v, p) >= 15, "적어도 한 발 15 (실측 " + dealtBy(v, p) + ")");
			p.setShiftKeyDown(false);
			Classes.clear(p);
			h.succeed();
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

	/** 체공 훈풍 — 공중에서 웅크리면 활공, 착지하면 다시 참. */
	@GameTest(maxTicks = 200)
	public void aeroDriftGlide(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 3, 1.5), 0.0F);
		GunslingerState st = Gunslinger.state(p);
		p.setOnGround(false);
		p.setShiftKeyDown(true);
		classTicks(p, 4);
		h.assertTrue(st.gliding, "공중 + 웅크리기 = 활공");
		h.assertTrue(st.glideT < T.of(AeroDrift.GLIDE), "활공 시간이 줄어듦 (" + st.glideT + ")");
		h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "느린 낙하");
		p.setShiftKeyDown(false);
		classTicks(p, 1);
		h.assertTrue(!st.gliding, "웅크리기를 떼면 멈춤");
		p.setOnGround(true);
		classTicks(p, 1);
		h.assertTrue(st.glideT == T.of(AeroDrift.GLIDE), "착지하면 다시 가득 참");
		Classes.clear(p);
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
