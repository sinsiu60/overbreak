package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.client.camera.AimTracker;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 조준 보정 · 근접 범위 평타 — 실제 클라이언트 입력으로 확인합니다.
 *   1) 어깨 너머 시점에서 바닐라 선택(hitResult)이 화면 조준점과 같은 곳을 가리키는지
 *   2) 서버가 그 조준점을 받았는지
 *   3) 좌클릭 한 번에 앞의 적 둘이 함께 맞는지, 곧바로 다시 눌러도 공격속도 때문에 안 나가는지
 */
public final class AimMeleeClientTest implements FabricClientGameTest {
	private static final int LEFT_MOUSE = 0;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			onServer(sp, p -> Classes.give(p, Classes.byId(Warrior.ID)));
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
			sp.getConnection().waitForChunksRender();

			// 1) 땅을 40도로 내려다봄 — 조준점이 사거리 안의 땅에 찍힘
			ctx.getInput().lookAt(0.0F, 40.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			String pickCheck = ctx.computeOnClient(mc -> {
				Vec3 aim = AimTracker.compute(mc, 1.0F);
				HitResult hit = mc.hitResult;
				HitResult vanilla = mc.player.pick(4.5, 1.0F, false);
				if (aim == null || hit == null || hit.getType() != HitResult.Type.BLOCK) {
					return "조준점/선택 없음 aim=" + aim + " hit=" + hit;
				}
				double toAim = hit.getLocation().distanceTo(aim);
				double vanillaOff = vanilla.getLocation().distanceTo(aim);
				if (toAim > 0.2) {
					return "선택이 조준점과 다름: " + toAim;
				}
				if (vanillaOff < 0.4) {
					return "보정 전과 차이가 없음 (시점이 어깨 너머가 아님?): " + vanillaOff;
				}
				return "ok 선택-조준점 " + String.format("%.3f", toAim) + " / 보정 전 차이 " + String.format("%.3f", vanillaOff);
			});
			System.out.println("[overbreak-aim] " + pickCheck);
			if (!pickCheck.startsWith("ok")) {
				throw new AssertionError(pickCheck);
			}
			ctx.takeScreenshot("aim_ground");

			// 2) 서버가 조준점을 받았는지
			Vec3 clientAim = ctx.computeOnClient(mc -> AimTracker.compute(mc, 1.0F));
			double serverOff = sp.getServer().computeOnServer(server -> {
				Vec3 sa = Attachments.profile(server.getPlayerList().getPlayers().getFirst()).aimPoint;
				return sa == null ? 999.0 : sa.distanceTo(clientAim);
			});
			System.out.println("[overbreak-aim] 서버 조준점 차이 " + serverOff);
			if (serverOff > 0.5) {
				throw new AssertionError("서버 조준점이 클라이언트와 다름: " + serverOff);
			}
			// 아래(40도)를 볼 때 스킬 · 평타 방향이 오른쪽으로 틀어지지 않아야 함
			double yawOff = sp.getServer().computeOnServer(server -> {
				ServerPlayer sp2 = server.getPlayerList().getPlayers().getFirst();
				Vec3 d = kr.overbreak.combat.Aim.direction(sp2);
				Vec3 look = sp2.getLookAngle();
				double a1 = Math.atan2(d.z, d.x);
				double a2 = Math.atan2(look.z, look.x);
				return Math.abs(Math.toDegrees(Math.atan2(Math.sin(a1 - a2), Math.cos(a1 - a2))));
			});
			System.out.println("[overbreak-aim] 아래를 볼 때 좌우 틀어짐 " + yawOff + "도");
			if (yawOff > 3.0) {
				throw new AssertionError("아래를 볼 때 방향이 옆으로 틀어짐: " + yawOff);
			}

			// 3) 좌클릭 범위 평타 — 정면 2칸, 오른쪽 앞 대각선에 하나씩
			ctx.getInput().lookAt(0.0F, 5.0F);
			onServer(sp, p -> {
				spawnDummy(p, 0.0, 2.2, "front");
				spawnDummy(p, 1.4, 1.6, "side");
			});
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			ctx.takeScreenshot("melee_before");
			ctx.getInput().pressMouse(LEFT_MOUSE);
			boolean swungFirst = ctx.computeOnClient(mc -> mc.player.swinging);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			ctx.takeScreenshot("melee_after");
			float[] afterFirst = healths(sp);
			boolean animFirst = ctx.computeOnClient(mc -> SkillAnims.elapsedTicks(mc.player.getId(), SkillAnimPayload.BASIC) >= 0);
			// 기본 공격 애니메이션(6틱 + 복귀 4틱)이 끝난 뒤, 아직 공격속도 대기(20틱) 중에 다시 클릭
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			ctx.getInput().pressMouse(LEFT_MOUSE);
			boolean swungSecond = ctx.computeOnClient(mc -> mc.player.swinging);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			ctx.takeScreenshot("melee_locked_click");
			float[] afterSecond = healths(sp);
			long animSecond = ctx.computeOnClient(mc -> SkillAnims.elapsedTicks(mc.player.getId(), SkillAnimPayload.BASIC_BACK));
			String result = String.format("1타 후 정면 %.1f 옆 %.1f / 대기 중 2타 후 정면 %.1f 옆 %.1f / 바닐라 스윙 1타 %s 2타 %s / 1타 애니 %s · 대기 중 애니 %d",
					afterFirst[0], afterFirst[1], afterSecond[0], afterSecond[1], swungFirst, swungSecond, animFirst, animSecond);
			System.out.println("[overbreak-melee] " + result);
			if (swungFirst || swungSecond) {
				throw new AssertionError("바닐라 팔 휘두르기 모션이 나오면 안 됨: " + result);
			}
			if (!animFirst) {
				throw new AssertionError("공격이 나가면 기본 공격 애니메이션이 와야 함: " + result);
			}
			if (animSecond >= 0) {
				throw new AssertionError("공격속도 대기 중 클릭에는 애니메이션이 없어야 함: " + result);
			}
			if (!(afterFirst[0] < 1000 && afterFirst[1] < 1000)) {
				throw new AssertionError("좌클릭 한 번에 둘 다 맞아야 함: " + result);
			}
			if (afterSecond[0] != afterFirst[0]) {
				throw new AssertionError("공격속도 잠금 중 두 번째 클릭이 들어감: " + result);
			}
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(25));
			ctx.getInput().pressMouse(LEFT_MOUSE);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			float[] afterThird = healths(sp);
			System.out.println("[overbreak-melee] 1초 뒤 3타 후 정면 " + afterThird[0]);
			if (!(afterThird[0] < afterFirst[0])) {
				throw new AssertionError("잠금이 풀린 뒤에는 다시 맞아야 함: " + afterThird[0]);
			}

			// 4) 머리 위 체력바 — 추가체력(흡수)은 파란색
			sp.getServer().runCommand("effect give @a minecraft:absorption 30 1");
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			ctx.takeScreenshot("healthbar_absorption");
		}
	}

	private static void spawnDummy(ServerPlayer p, double right, double forward, String name) {
		ServerLevel level = p.level();
		Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (v == null) {
			return;
		}
		// yaw 0 = +Z 정면, 오른쪽 = -X
		v.snapTo(p.getX() - right, p.getY(), p.getZ() + forward, 180.0F, 0.0F);
		v.setNoAi(true);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		v.setHealth(1000);
		v.addTag("ob_" + name);
		level.addFreshEntity(v);
	}

	private static float[] healths(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> {
			float front = -1;
			float side = -1;
			for (var e : server.overworld().getAllEntities()) {
				if (e instanceof Villager v) {
					if (v.entityTags().contains("ob_front")) {
						front = v.getHealth();
					} else if (v.entityTags().contains("ob_side")) {
						side = v.getHealth();
					}
				}
			}
			return new float[] {front, side};
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}
}
