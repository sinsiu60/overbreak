package kr.overbreak.test;

import java.lang.reflect.Method;

import kr.overbreak.skill.Effects;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * 효과 목록이 도는 도중에 정리해도 터지지 않는가 — 1대1 매치에서 승부가 갈릴 때
 * (효과 안에서 준 피해 → 쓰러짐 → Effects.cancelOwnedBy) 서버가 꺼지던 문제 (0.1a).
 *
 * 목록은 서버가 매 틱 돌립니다. 여기서 직접 {@code Effects.tick()} 을 부르면 같이 돌고 있는
 * 다른 시험의 효과까지 한 틱 더 밀리므로, 효과를 넣어 두고 서버가 돌리게 둡니다.
 */
public final class EffectsTest implements CustomTestMethodInvoker {
	/** 매 틱 정해진 일을 하고, 정해진 틱 수만큼 살아 있는 효과. */
	private static final class Probe implements Effects.Active {
		final Object owner;
		final Runnable onTick;
		final int life;
		int ticks;

		Probe(Object owner, int life, Runnable onTick) {
			this.owner = owner;
			this.life = life;
			this.onTick = onTick;
		}

		@Override
		public boolean tick() {
			ticks++;
			onTick.run();
			return ticks < life;
		}

		@Override
		public Object owner() {
			return owner;
		}
	}

	@GameTest(maxTicks = 120)
	public void cancelInsideTick(GameTestHelper h) {
		Object a = new Object();
		Object b = new Object();
		Probe victim1 = new Probe(b, 1000, () -> {});
		Probe victim2 = new Probe(b, 1000, () -> {});
		Probe alive = new Probe(a, 1000, () -> {});
		// 도는 도중에 다른 주인의 효과를 전부 취소합니다 (= 쓰러진 순간의 정리).
		// 고치기 전에는 여기서 ConcurrentModificationException 이 나 서버가 꺼졌습니다.
		Probe killer = new Probe(a, 1, () -> {
			Effects.cancelOwnedBy(b);
			// 도는 도중에 새로 넣는 것도 안전해야 합니다 (다음 틱부터 돎)
			Effects.add(new Probe(a, 1, () -> {}));
		});
		Effects.add(victim1);
		Effects.add(alive);
		Effects.add(killer);
		Effects.add(victim2);

		h.runAfterDelay(T.of(10), () -> {
			h.assertTrue(killer.ticks == 1, "한 번만 돌고 빠짐 (실측 " + killer.ticks + ")");
			h.assertTrue(victim1.ticks <= 1 && victim2.ticks <= 1, "취소된 효과는 더 돌지 않음 (실측 "
					+ victim1.ticks + " · " + victim2.ticks + ")");
			h.assertTrue(alive.ticks >= T.of(8), "취소되지 않은 효과는 계속 돎 (실측 " + alive.ticks + ")");
			Effects.cancelOwnedBy(a);
			h.succeed();
		});
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
