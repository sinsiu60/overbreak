package kr.overbreak.test;

import java.lang.reflect.Method;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.game.Training;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

/** 훈련용 더미 — 0.1a 부터 주민이 아니라 마네킹입니다. 맞고 · 밀리고 · 군중 제어가 걸려야 합니다. */
public final class DummyTest implements CustomTestMethodInvoker {
	@GameTest
	public void mannequinIsAValidDummy(GameTestHelper h) {
		Mannequin m = h.spawn(EntityTypes.MANNEQUIN, new Vec3(2.5, 0, 2.5));
		Training.dress(m, Component.literal("시험용 더미"), false);
		h.assertTrue(Math.abs(m.getMaxHealth() - 1000.0F) < 0.01F, "체력 1000 (실측 " + m.getMaxHealth() + ")");
		h.assertTrue(Math.abs(m.getHealth() - 1000.0F) < 0.01F, "가득 찬 채로 시작");

		// 스킬 피해가 들어가야 합니다 (10배 기준 500 = 50)
		SkillDamage.deal(m, null, 500, SkillDamage.Kind.NORMAL);
		h.assertTrue(m.getHealth() < 1000.0F, "피해를 받음 (실측 " + m.getHealth() + ")");

		// 군중 제어도 걸려야 합니다 (넉백 · 기절이 눈에 보이도록)
		CrowdControl.stun(m, 20);
		h.assertTrue(Attachments.combatant(m).stunT > 0, "기절");
		CrowdControl.clearAll(m);
		m.discard();
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
