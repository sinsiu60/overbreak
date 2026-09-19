package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.combat.HitboxRewind;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.core.tick.Duration;
import kr.overbreak.core.tick.GameClock;
import kr.overbreak.core.tick.TickRateConfig;
import kr.overbreak.input.InputTiming;
import kr.overbreak.net.SkillInputPayload;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** 틱 설정 · 되감기 판정 · 서브틱 입력 검증. */
public final class NetcodeTest implements CustomTestMethodInvoker {
	private static FakePlayer player(GameTestHelper h, String name, Vec3 rel, float yaw) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, yaw, 0.0F);
		return p;
	}

	@GameTest
	public void durationParsing(GameTestHelper h) {
		h.assertTrue(Math.abs(Duration.parse("7.0s").getOrThrow().seconds() - 7.0) < 1e-9, "7.0s");
		h.assertTrue(Math.abs(Duration.parse("300ms").getOrThrow().seconds() - 0.3) < 1e-9, "300ms");
		h.assertTrue(Duration.parse("7").error().isPresent(), "단위 없으면 오류");
		h.assertTrue(Duration.parse("-1s").error().isPresent(), "음수 오류");
		int rate = TickRateConfig.tickRate();
		h.assertTrue(TickRateConfig.ticks(0.7) == Math.round(0.7 * rate), "0.7초 → 틱");
		h.assertTrue(Math.abs(TickRateConfig.perTick(22.0) - 22.0 / rate) < 1e-9, "초당 → 틱당");
		h.succeed();
	}

	@GameTest
	public void rewindHitsWhereTargetWas(GameTestHelper h) {
		FakePlayer shooter = player(h, "ob_rw_a", new Vec3(1.5, 1, 0.5), 0.0F);
		// 가짜 플레이어는 월드 엔티티 목록에 없어 판정 대상이 못 되므로 주민에 기록을 붙여 씀 (플레이어는 GameLoop 가 기록)
		net.minecraft.world.entity.npc.villager.Villager target = h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new Vec3(1.5, 1, 6.5));
		target.setNoAi(true);
		HitboxRewind.forget(target);
		// 3틱 전에는 정면, 지금은 옆으로 3칸 비켜남
		HitboxRewind.record(target);
		Vec3 moved = h.absoluteVec(new Vec3(4.5, 1, 6.5));
		target.snapTo(moved.x, moved.y, moved.z, 180.0F, 0.0F);
		target.setBoundingBox(target.getDimensions(target.getPose()).makeBoundingBox(moved));
		HitboxRewind.record(target);
		HitboxRewind.record(target);
		HitboxRewind.record(target);
		Vec3 eye = shooter.getEyePosition();
		Vec3 dir = new Vec3(0, 0, 1);
		h.assertTrue(Hitscan.cast(shooter, eye, dir, 8.0, 0).target() != target, "되감지 않으면 비켜난 적은 안 맞음");
		h.assertTrue(Hitscan.cast(shooter, eye, dir, 8.0, 3).target() == target, "3틱 되감으면 정면에 있던 적이 맞음");
		h.assertTrue(HitboxRewind.ticksFor(1000) == Math.round(HitboxRewind.MAX_MS * TickRateConfig.tickRate() / 1000.0), "핑이 커도 200ms 까지만");
		h.succeed();
	}

	@GameTest
	public void subTickClampAndRateLimit(GameTestHelper h) {
		FakePlayer p = player(h, "ob_input", new Vec3(1.5, 1, 1.5), 0.0F);
		h.assertTrue(InputTiming.receive(p, new SkillInputPayload(1, true, 5.0F)), "범위 밖 값도 받음 (잘라서)");
		double at = InputTiming.pressedAt(p, 1);
		h.assertTrue(Math.abs(at - GameClock.now()) < 1e-9, "subTick 5.0 → 1.0 으로 자름 (" + at + ")");
		h.assertTrue(!InputTiming.receive(p, new SkillInputPayload(9, true, 0.5F)), "없는 슬롯 버림");
		h.assertTrue(!InputTiming.receive(p, new SkillInputPayload(0, true, Float.NaN)), "NaN 버림");
		int accepted = 0;
		for (int i = 0; i < 200; i++) {
			if (InputTiming.receive(p, new SkillInputPayload(0, i % 2 == 0, 0.5F))) {
				accepted++;
			}
		}
		h.assertTrue(accepted < InputTiming.MAX_PER_SECOND, "1초에 너무 많으면 버림 (받음 " + accepted + ")");
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
