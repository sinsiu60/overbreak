package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.MeleeCleave;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** 근접 범위 평타 · 조준 보정. */
public final class MeleeAimTest implements CustomTestMethodInvoker {

	private static FakePlayer caster(GameTestHelper h) {
		FakePlayer p = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ob_melee"));
		Vec3 abs = h.absoluteVec(new Vec3(2.5, 1, 2.5));
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, 0.0F); // yaw 0 = +Z 를 봄
		Classes.give(p, Classes.byId(Warrior.ID));
		// 가짜 플레이어는 틱이 돌지 않아 손에 든 무기의 공격력이 속성에 반영되지 않습니다. 바닐라와 같은 방식으로 직접 겁니다.
		p.getInventory().getItem(0).forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
			var inst = p.getAttribute(attr);
			if (inst != null && !inst.hasModifier(mod.id())) {
				inst.addTransientModifier(mod);
			}
		});
		return p;
	}

	private static Villager dummy(GameTestHelper h, double x, double z) {
		Villager v = h.spawn(EntityTypes.VILLAGER, new Vec3(x, 1, z));
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		v.setHealth(100);
		return v;
	}

	@GameTest
	public void cleaveHitsFrontOnly(GameTestHelper h) {
		FakePlayer p = caster(h);
		PvpClass warrior = Classes.byId(Warrior.ID);
		Villager front = dummy(h, 2.5, 4.5);      // 정면 2칸
		Villager side = dummy(h, 4.0, 4.0);       // 오른쪽 앞 45도쯤 (부채꼴 안)
		Villager behind = dummy(h, 2.5, 0.5);     // 뒤 2칸
		Villager far = dummy(h, 2.5, 7.5);        // 정면 5칸 (사거리 밖)

		h.assertTrue(MeleeCleave.swing(p, warrior), "첫 스윙은 나가야 함");
		h.assertTrue(front.getHealth() < 100, "정면 적중 (체력 " + front.getHealth() + ")");
		h.assertTrue(side.getHealth() < 100, "부채꼴 안 옆 적중 (체력 " + side.getHealth() + ")");
		h.assertTrue(behind.getHealth() == 100, "뒤는 안 맞음");
		h.assertTrue(far.getHealth() == 100, "사거리 밖은 안 맞음");
		h.assertTrue(Math.abs(front.getHealth() - 45) < 0.01, "평타 피해 55 (체력 " + front.getHealth() + ")");
		h.assertTrue(Warrior.bleedStacks(front) == 1, "평타 출혈 1 (실측 " + Warrior.bleedStacks(front) + ")");

		PlayerProfile prof = Attachments.profile(p);
		h.assertTrue(prof.atkCd == T.of(20), "공격속도 1.0 → 20틱 잠금 (실측 " + prof.atkCd + ")");
		h.assertTrue(!MeleeCleave.swing(p, warrior), "잠금 중에는 안 나감");
		Classes.clear(p);
		h.succeed();
	}

	@GameTest
	public void aimPointCorrectsDirection(GameTestHelper h) {
		FakePlayer p = caster(h);
		PlayerProfile prof = Attachments.profile(p);
		Vec3 eye = p.getEyePosition();

		// 시선(+Z)에서 오른쪽으로 20도 — 받아들임
		double a = Math.toRadians(20);
		prof.aimPoint = eye.add(-Math.sin(a) * 10, 0, Math.cos(a) * 10);
		prof.aimTick = h.getLevel().getGameTime();
		Vec3 d = Aim.direction(p);
		h.assertTrue(Math.abs(d.x + Math.sin(a)) < 1.0E-3 && Math.abs(d.z - Math.cos(a)) < 1.0E-3, "20도 보정 적용 " + d);
		Vec3 f = Aim.facing(p);
		h.assertTrue(f.z > 0.999, "근접 · 부채꼴 방향은 조준점 보정 없이 캐릭터 정면 " + f);

		// 80도 — 조작 방지로 시선 사용
		double b = Math.toRadians(80);
		prof.aimPoint = eye.add(-Math.sin(b) * 10, 0, Math.cos(b) * 10);
		d = Aim.direction(p);
		h.assertTrue(d.z > 0.999, "80도는 무시하고 시선 " + d);

		// 아래를 보고 발 앞 바닥(수평 2칸)을 조준 — 카메라 옆 시차로 좌우가 틀어지지 않고 시선 방향
		prof.aimPoint = eye.add(-Math.sin(a) * 2, -1.5, Math.cos(a) * 2);
		prof.aimTick = h.getLevel().getGameTime();
		d = Aim.direction(p);
		h.assertTrue(Math.abs(d.x) < 1.0E-3, "가까운 바닥 조준점은 좌우 보정 없음 " + d);

		// 오래된 조준점 — 시선 사용
		prof.aimPoint = eye.add(-Math.sin(a) * 10, 0, Math.cos(a) * 10);
		prof.aimTick = h.getLevel().getGameTime() - 20;
		d = Aim.direction(p);
		h.assertTrue(d.z > 0.999, "오래된 조준점은 무시 " + d);
		Classes.clear(p);
		h.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
