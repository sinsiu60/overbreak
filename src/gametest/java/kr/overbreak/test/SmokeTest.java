package kr.overbreak.test;

import java.lang.reflect.Method;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SmokeTest implements CustomTestMethodInvoker {
	@GameTest
	public void loads(GameTestHelper h) {
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
