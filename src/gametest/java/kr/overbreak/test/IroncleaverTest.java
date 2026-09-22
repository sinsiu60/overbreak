package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.ironcleaver.Ironcleaver;
import kr.overbreak.classes.ironcleaver.IronSpec;
import kr.overbreak.classes.ironcleaver.IronState;
import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.GameClock;
import kr.overbreak.input.InputTiming;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 참철 — LMB 판별(짧게 · 누르기) · 3타 콤보 · 모으기 단계 · 진 참 · 방어 파쇄 · 어깨 박치기 · 검막 · 대지 가르기 · 천참.
 *
 * LMB 누름 · 뗌은 클라이언트 패킷 대신 {@link InputTiming#inject} 로 넣습니다 (서버 시각 = 지금 게임 틱).
 * 가짜 플레이어는 직업 틱이 저절로 돌지 않으므로 onEachTick 으로 직업 틱을 돌립니다.
 */
public final class IroncleaverTest implements CustomTestMethodInvoker {
	private static PvpClass ic() {
		return Classes.byId(Ironcleaver.ID);
	}

	private static FakePlayer caster(GameTestHelper h, Vec3 rel, float yaw, String name) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, yaw, 0.0F); // yaw 0 = +Z
		p.setOnGround(true);
		Classes.give(p, ic());
		return p;
	}

	private static FakePlayer caster(GameTestHelper h, Vec3 rel) {
		return caster(h, rel, 0.0F, "ob_ironcleaver");
	}

	private static Villager dummy(GameTestHelper h, Vec3 rel) {
		Villager v = h.spawn(EntityTypes.VILLAGER, rel);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(1000);
		return v;
	}

	private static void near(GameTestHelper h, double actual, double expected, String what) {
		h.assertTrue(Math.abs(actual - expected) <= 0.01, what + " 기대 " + expected + " 실측 " + actual);
	}

	private static void press(FakePlayer p) {
		InputTiming.inject(p, 0, true, GameClock.now(), Double.NaN);
	}

	private static void release(FakePlayer p) {
		InputTiming.inject(p, 0, false, GameClock.now() + 1.0E-3, Double.NaN);
	}

	/** 짧게 한 번 누름 (누르고 곧바로 뗌). */
	private static void tap(FakePlayer p) {
		press(p);
		release(p);
	}

	private static IronState st(FakePlayer p) {
		return Ironcleaver.state(p);
	}

	@GameTest
	public void stats(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		near(h, p.getMaxHealth(), 260, "최대 체력 (200 + 60)");
		h.assertTrue(!ic().meleeAllowed(), "바닐라 근접 대신 LMB 판별");
		h.assertTrue(Attachments.profile(p).ultRate == 100, "게이지 피해 1당 1%");
		Classes.clear(p);
		h.succeed();
	}

	/** 짧게 누를 때마다 45 → 45 → 90, 후딜 중 누른 입력은 이어짐 · 등 뒤는 안 맞음. */
	@GameTest(maxTicks = 300)
	public void threeHitCombo(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 3.5));
		Villager front = dummy(h, new Vec3(1.5, 0, 6.5));
		Villager behind = dummy(h, new Vec3(1.5, 0, 0.5));
		h.onEachTick(() -> ic().tick(p));
		tap(p);
		h.runAfterDelay(T.of(5), () -> near(h, front.getHealth(), 1000, "선딜(0.35초) 중에는 피해 없음"));
		h.runAfterDelay(T.of(11), () -> {
			near(h, 1000 - front.getHealth(), 45, "1타 45");
			// 후딜 중 누름 — 끝나는 즉시 2타
			tap(p);
		});
		h.runAfterDelay(T.of(29), () -> {
			near(h, 1000 - front.getHealth(), 90, "2타 45 (합 90)");
			tap(p);
		});
		h.runAfterDelay(T.of(50), () -> {
			near(h, 1000 - front.getHealth(), 180, "3타 내려찍기 90 (합 180)");
			near(h, behind.getHealth(), 1000, "등 뒤는 안 맞음");
		});
		h.runAfterDelay(T.of(62), () -> {
			h.assertTrue(st(p).combo == 0, "3타 뒤 1타로 (실측 " + st(p).combo + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 1.2초 쉬면 콤보가 1타로 돌아감. */
	@GameTest(maxTicks = 300)
	public void comboResets(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		h.onEachTick(() -> ic().tick(p));
		tap(p);
		h.runAfterDelay(T.of(20), () -> h.assertTrue(st(p).combo == 1, "1타 뒤 다음은 2타 (실측 " + st(p).combo + ")"));
		h.runAfterDelay(T.of(18 + 26), () -> {
			h.assertTrue(st(p).combo == 0, "1.2초 쉬면 1타로");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 누르고 있으면 모으기 — 1.2초(2단)에 떼면 80. */
	@GameTest(maxTicks = 300)
	public void chargeStageTwo(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		h.onEachTick(() -> ic().tick(p));
		press(p);
		h.runAfterDelay(T.of(8), () -> {
			h.assertTrue(st(p).phase == IronState.Phase.CHARGE, "0.25초 넘게 누르면 모으기 (실측 " + st(p).phase + ")");
			near(h, v.getHealth(), 1000, "모으는 중에는 휘두르지 않음");
		});
		h.runAfterDelay(T.of(14), () -> h.assertTrue(st(p).stage == 1, "0.6초 1단"));
		h.runAfterDelay(T.of(26), () -> {
			h.assertTrue(st(p).stage == 2, "1.2초 2단");
			h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "2단부터 넉백 면역");
			release(p);
		});
		h.runAfterDelay(T.of(30), () -> {
			near(h, 1000 - v.getHealth(), 80, "2단 모아 베기 80");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 1.8~2.0초에 떼면 진 참 130. */
	@GameTest(maxTicks = 300)
	public void perfectCleave(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		h.onEachTick(() -> ic().tick(p));
		press(p);
		h.runAfterDelay(T.of(38), () -> {
			h.assertTrue(st(p).perfect, "진 참 창 안");
			release(p);
		});
		h.runAfterDelay(T.of(42), () -> {
			near(h, 1000 - v.getHealth(), 130, "진 참 130");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 3단 뒤 1초(2.8초)까지 누르고 있으면 저절로 3단 140. */
	@GameTest(maxTicks = 400)
	public void autoRelease(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		h.onEachTick(() -> ic().tick(p));
		press(p);
		h.runAfterDelay(T.of(50), () -> near(h, v.getHealth(), 1000, "2.8초 전에는 아직"));
		h.runAfterDelay(T.of(60), () -> {
			near(h, 1000 - v.getHealth(), 140, "2.8초에 저절로 3단 140");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 모으기 1단 전에 떼면 지금 타수 휘두르기가 곧바로 (선딜은 이미 지남). */
	@GameTest(maxTicks = 300)
	public void releaseBeforeStageSwings(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 4.5));
		h.onEachTick(() -> ic().tick(p));
		press(p);
		h.runAfterDelay(T.of(9), () -> release(p));
		h.runAfterDelay(T.of(12), () -> {
			near(h, 1000 - v.getHealth(), 45, "곧바로 1타 45");
			Classes.clear(p);
			h.succeed();
		});
	}

	/**
	 * 방어 파쇄 — 검막(앞 ×0.2) 중인 참철에게 방어 파쇄 피해: 감소를 절반만 받아 70 → 42 (70 × (1 - 0.8 × 0.5)).
	 * 가짜 플레이어끼리는 서버 PvP 판정(클라이언트 로딩 · 편)에 막히므로 받는 피해 수정 단계를 직접 봅니다.
	 */
	@GameTest(maxTicks = 100)
	public void guardBreakHalvesReduction(GameTestHelper h) {
		FakePlayer a = caster(h, new Vec3(1.5, 0, 1.5), 0.0F, "ob_ic_attacker");
		FakePlayer v = caster(h, new Vec3(1.5, 0, 4.5), 180.0F, "ob_ic_victim");
		ic().secondary(v);
		DamageSource src = h.getLevel().damageSources().playerAttack(a);
		near(h, DamageModifiers.apply(v, src, 70.0F), 14, "검막 앞 ×0.2");
		DamageModifiers.guardBreak = IronSpec.GUARD_BREAK_FACTOR;
		float broken;
		try {
			broken = DamageModifiers.apply(v, src, 70.0F);
		} finally {
			DamageModifiers.guardBreak = 1.0F;
		}
		near(h, broken, 42, "방어 파쇄: 감소 80% → 40%");
		h.assertTrue(IronSpec.GUARD_BREAK_STAGE == 2, "2단부터 방어 파쇄");
		Classes.clear(a);
		Classes.clear(v);
		h.succeed();
	}

	/** 검막 — 앞 ×0.2 · 뒤는 그대로 · 막으면 다음 모으기가 2단부터. */
	@GameTest(maxTicks = 300)
	public void guardBlocksAndRewards(GameTestHelper h) {
		FakePlayer a = caster(h, new Vec3(1.5, 0, 1.5), 0.0F, "ob_ic_attacker");
		FakePlayer b = caster(h, new Vec3(1.5, 0, 7.5), 180.0F, "ob_ic_behind");
		FakePlayer v = caster(h, new Vec3(1.5, 0, 4.5), 180.0F, "ob_ic_victim");
		h.onEachTick(() -> ic().tick(v));
		ic().secondary(v);
		h.assertTrue(Attachments.profile(v).cooldown(Ironcleaver.GUARD) == T.of(180), "쿨타임 9초");
		near(h, DamageModifiers.apply(v, h.getLevel().damageSources().playerAttack(b), 40.0F), 40, "등 뒤에서 온 피해는 그대로");
		h.assertTrue(!st(v).guardSuccess, "뒤에서 맞은 건 막기 성공 아님");
		near(h, DamageModifiers.apply(v, h.getLevel().damageSources().playerAttack(a), 40.0F), 8, "앞에서 온 피해 ×0.2");
		h.assertTrue(st(v).guardSuccess, "막기 성공");
		// 막기가 끝난 뒤(1초) 누르고 있으면 모으기가 곧바로 2단부터
		h.runAfterDelay(T.of(2), () -> h.assertTrue(v.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 1.0E-6, "막는 동안 움직일 수 없음"));
		h.runAfterDelay(T.of(22), () -> {
			h.assertTrue(st(v).phase == IronState.Phase.IDLE, "1초 뒤 막기 끝");
			h.assertTrue(st(v).rewardT > 0, "보상 남음");
			press(v);
		});
		h.runAfterDelay(T.of(29), () -> {
			h.assertTrue(st(v).phase == IronState.Phase.CHARGE && st(v).stage >= 2,
					"보상 — 2단부터 (실측 " + st(v).phase + " " + st(v).stage + ")");
			Classes.clear(a);
			Classes.clear(b);
			Classes.clear(v);
			h.succeed();
		});
	}

	/** 검막 중 LMB — 막기를 끝내고 곧바로 모으기. */
	@GameTest(maxTicks = 200)
	public void guardIntoCharge(GameTestHelper h) {
		FakePlayer v = caster(h, new Vec3(1.5, 0, 1.5));
		h.onEachTick(() -> ic().tick(v));
		ic().secondary(v);
		h.runAfterDelay(T.of(5), () -> press(v));
		h.runAfterDelay(T.of(7), () -> {
			h.assertTrue(st(v).phase == IronState.Phase.CHARGE, "막는 중 LMB → 곧바로 모으기 (실측 " + st(v).phase + ")");
			Classes.clear(v);
			h.succeed();
		});
	}

	/**
	 * 어깨 박치기 — 처음 부딪친 적 20 · 밀어냄.
	 * 서버는 가짜 플레이어를 움직이지 않으므로(이동은 클라이언트 몫) 바로 앞에 붙여 둔 적으로 부딪침만 봅니다.
	 */
	@GameTest(maxTicks = 200)
	public void shoulderBash(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 2.6));
		double z0 = v.getZ();
		h.onEachTick(() -> ic().tick(p));
		ic().primary(p);
		h.assertTrue(Attachments.profile(p).cooldown(Ironcleaver.BASH) == T.of(140), "쿨타임 7초");
		h.runAfterDelay(T.of(14), () -> {
			near(h, 1000 - v.getHealth(), 20, "박치기 20");
			h.assertTrue(v.getZ() - z0 >= 1.0, "앞으로 밀려남 (실측 " + (v.getZ() - z0) + ")");
			h.assertTrue(st(p).phase == IronState.Phase.IDLE, "부딪치면 돌진 끝");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 평타 후딜은 스킬로 끊을 수 있음 — 선딜 · 판정 중에는 안 됨. 끊어도 다음 타수는 이어짐. */
	@GameTest(maxTicks = 200)
	public void skillsCancelRecovery(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		h.onEachTick(() -> ic().tick(p));
		tap(p);
		h.runAfterDelay(T.of(3), () -> {
			ic().secondary(p);
			h.assertTrue(st(p).phase == IronState.Phase.SWING, "선딜 중에는 스킬이 나가지 않음 (실측 " + st(p).phase + ")");
			h.assertTrue(ic().intercept(p, kr.overbreak.input.InputRouter.Slot.SECONDARY), "선딜 중 입력은 막힘");
		});
		h.runAfterDelay(T.of(12), () -> {
			h.assertTrue(!ic().intercept(p, kr.overbreak.input.InputRouter.Slot.SECONDARY), "후딜 중 입력은 통과");
			ic().secondary(p);
			h.assertTrue(st(p).phase == IronState.Phase.GUARD, "후딜을 끊고 검막 (실측 " + st(p).phase + ")");
			h.assertTrue(st(p).combo == 1, "다음 타수는 2타로 이어짐 (실측 " + st(p).combo + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 모으는 중 박치기 — 돌진하는 동안 모으기 시간은 멈추고, 끝나면 그대로 이어서 모음. */
	@GameTest(maxTicks = 300)
	public void bashPausesCharge(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 1.5));
		h.onEachTick(() -> ic().tick(p));
		press(p);
		h.runAfterDelay(T.of(10), () -> {
			h.assertTrue(st(p).phase == IronState.Phase.CHARGE, "모으는 중");
			ic().primary(p);
			h.assertTrue(st(p).phase == IronState.Phase.BASH, "모으는 중에도 박치기");
		});
		h.runAfterDelay(T.of(22), () -> {
			h.assertTrue(st(p).phase == IronState.Phase.CHARGE, "박치기 뒤 다시 모으기 (실측 " + st(p).phase + ")");
			h.assertTrue(st(p).paused >= 5.0, "돌진한 만큼 멈춤 (실측 " + st(p).paused + ")");
			// 누른 지 1.3초 — 멈춘 0.3초를 빼면 1.0초라 아직 1단
			h.assertTrue(st(p).stage == 1, "멈춘 만큼 늦게 (실측 " + st(p).stage + ")");
			Classes.clear(p);
			h.succeed();
		});
	}

	/** 대지 가르기 — 0.25초 만에 땅을 따라 달려 6칸 앞 적에게 85. 공중에서는 쓸 수 없음 (쿨타임 안 씀). */
	@GameTest(maxTicks = 300)
	public void earthRend(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(1.5, 0, 0.5));
		Villager v = dummy(h, new Vec3(1.5, 0, 6.5));
		h.onEachTick(() -> ic().tick(p));
		ic().tertiary(p);
		h.assertTrue(Attachments.profile(p).cooldown(Ironcleaver.REND) == T.of(200), "쿨타임 10초");
		FakePlayer air = caster(h, new Vec3(5.5, 5, 0.5), 0.0F, "ob_ic_air");
		air.setOnGround(false);
		ic().tertiary(air);
		h.assertTrue(Attachments.profile(air).cooldown(Ironcleaver.REND) == 0, "공중이면 쓰지 않음 (쿨타임도 안 씀)");
		h.runAfterDelay(T.of(24), () -> {
			near(h, 1000 - v.getHealth(), 85, "대지 가르기 85");
			Classes.clear(p);
			Classes.clear(air);
			h.succeed();
		});
	}

	/**
	 * 천참 — 1.2초 모았다가 앞 14칸 × 폭 4칸을 벽째로 140. 옆으로 비킨 적은 안 맞음.
	 * (시험 구조물 밖에 세운 적은 옆 시험이 치워 버릴 수 있어 구조물 안 7칸 거리로 봅니다)
	 */
	@GameTest(maxTicks = 300)
	public void heavenCleave(GameTestHelper h) {
		FakePlayer p = caster(h, new Vec3(2.5, 0, 0.5));
		Villager far = dummy(h, new Vec3(2.5, 0, 7.5));
		Villager side = dummy(h, new Vec3(6.8, 0, 4.5));
		for (int x = 0; x <= 4; x++) {
			for (int y = 0; y <= 2; y++) {
				h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
			}
		}
		h.onEachTick(() -> ic().tick(p));
		ic().ult(p);
		h.runAfterDelay(T.of(10), () -> {
			h.assertTrue(Attachments.combatant(p).ccImmune, "모으는 중 군중제어 면역");
			near(h, far.getHealth(), 1000, "1.2초 전에는 아직");
		});
		h.runAfterDelay(T.of(28), () -> {
			near(h, 1000 - far.getHealth(), 140, "벽 너머 7칸 140");
			near(h, side.getHealth(), 1000, "옆 4칸은 안 맞음");
			h.assertTrue(!Attachments.combatant(p).ccImmune, "벤 뒤 면역 풀림");
			Classes.clear(p);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
