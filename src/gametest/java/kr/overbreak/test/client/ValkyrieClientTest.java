package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.valkyrie.Valkyrie;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.anim.data.Molang;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.ult.UltGauge;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** 발키리 애니메이션 · 연사 포탑 모델 · 예광탄 · HUD — 구간별 스크린샷. 이름: 시점_동작_경과틱. */
public final class ValkyrieClientTest implements FabricClientGameTest {
	private static final int LEFT_MOUSE = 0;
	private static final int RIGHT_MOUSE = 1;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ctx.runOnClient(mc -> checkAnimationFile());
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule fall_damage false");
			onServer(sp, p -> Classes.give(p, valk()));
			ctx.getInput().lookAt(0.0F, 5.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			shot(ctx, "vk_tps_idle", 0);
			views(ctx, sp, "vk_tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "vk_front_idle", 0);
			views(ctx, sp, "vk_front");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 5.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "vk_fp_idle", 0);
			// 걷기: 달리기 키를 눌러도 달리지 않음 · 화면은 가만히, 무기만 발걸음마다 툭툭
			ctx.getInput().holdKey(o -> o.keySprint);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			for (int i = 0; i < 6; i++) {
				ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(1));
				Path walk = ctx.takeScreenshot(String.format("vk_fp_walk_%02d", i));
				System.out.println("[overbreak-shot] " + walk.toAbsolutePath());
			}
			String walkInfo = ctx.computeOnClient(mc -> "달리기 " + mc.player.isSprinting() + " · 속도 "
					+ String.format("%.4f", mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED))
					+ " · 시야 배율 " + String.format("%.3f", mc.player.getFieldOfViewModifier(true, 1.0F)));
			System.out.println("[overbreak-walk] " + walkInfo);
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.getInput().releaseKey(o -> o.keySprint);
			if (walkInfo.startsWith("달리기 true")) {
				throw new AssertionError("달리기가 막히지 않음: " + walkInfo);
			}
			home(sp);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			views(ctx, sp, "vk_fp");
		}
	}

	/** Blockbench 애니메이션 파일이 읽혀 있고, 식(시선 따라가기)이 계산되는지. */
	private static void checkAnimationFile() {
		for (String name : new String[] {"valkyrie.ready", "valkyrie.shot", "valkyrie.rocket", "valkyrie.float", "valkyrie.overheat", "valkyrie.barrage"}) {
			if (PlayerAnimations.get(name) == null) {
				throw new AssertionError("애니메이션 파일에 없음: " + name + " (읽힌 것: " + PlayerAnimations.names() + ")");
			}
		}
		Molang.Context c = new Molang.Context();
		c.headX = 10.0;
		double[] arm = PlayerAnimations.get("valkyrie.shot").sample("right_arm", PlayerAnimation.ROTATION, 0.0, c);
		if (arm == null || Math.abs(arm[0] + 80.0) > 0.01 || Math.abs(arm[1] + 5.73) > 0.01) {
			throw new AssertionError("valkyrie.shot 오른팔 0초 = -80, -5.73 이어야 함: " + java.util.Arrays.toString(arm));
		}
		double[] fp = PlayerAnimations.get("valkyrie.shot").sample("firstperson_item", PlayerAnimation.POSITION, 0.02, c);
		if (fp == null || Math.abs(fp[2] - 0.64) > 0.01) {
			throw new AssertionError("valkyrie.shot 1인칭 0.02초 z = 0.64 이어야 함: " + java.util.Arrays.toString(fp));
		}
	}

	private static void views(ClientGameTestContext ctx, TestSingleplayerContext sp, String prefix) {
		// 연사 (좌클릭을 실제로 누르고 있음, 앞 6칸 허수아비)
		reset(sp);
		spawnDummy(sp, 6.0);
		ctx.getInput().holdMouse(LEFT_MOUSE);
		burst(ctx, prefix + "_shot", SkillAnimPayload.VK_SHOT, 2, 3, 5, 10);
		ctx.getInput().releaseMouse(LEFT_MOUSE);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 전술 로켓 (우클릭)
		reset(sp);
		ctx.getInput().pressMouse(RIGHT_MOUSE);
		burst(ctx, prefix + "_rocket", SkillAnimPayload.VK_ROCKET, 1, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));
		clearVillagers(sp);

		// 차원 도약 + 공중 연사
		reset(sp);
		onServer(sp, p -> valk().secondary(p));
		burst(ctx, prefix + "_float", SkillAnimPayload.VK_FLOAT, 2, 5, 8);
		ctx.getInput().holdMouse(LEFT_MOUSE);
		burst(ctx, prefix + "_float_shot", SkillAnimPayload.VK_SHOT, 2, 4);
		ctx.getInput().releaseMouse(LEFT_MOUSE);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(40));
		home(sp);

		// 과열 분사
		reset(sp);
		onServer(sp, p -> valk().tertiary(p));
		burst(ctx, prefix + "_overheat", SkillAnimPayload.VK_OVERHEAT, 2, 4, 6);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));

		// 재장전 (R 키와 같은 서버 처리): 탄창 빼기 · 새 탄창 · 끼움 · 장전 손잡이
		reset(sp);
		onServer(sp, p -> {
			Valkyrie.state(p).ammo = 10;
			valk().reload(p);
		});
		burst(ctx, prefix + "_reload", SkillAnimPayload.VK_RELOAD, 2, 2, 3, 3, 3, 3, 3, 3, 3, 2, 2);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 탄막 포격
		reset(sp);
		spawnDummy(sp, 6.0);
		onServer(sp, p -> {
			UltGauge.fill(p);
			valk().ult(p);
		});
		burst(ctx, prefix + "_barrage", SkillAnimPayload.VK_BARRAGE, 3, 7, 8, 10, 20);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(60));
		clearVillagers(sp);
		home(sp);
	}

	private static void burst(ClientGameTestContext ctx, String label, int anim, int... gaps) {
		for (int gap : gaps) {
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(gap));
			shot(ctx, label, anim);
		}
	}

	private static void spawnDummy(TestSingleplayerContext sp, double forward) {
		onServer(sp, p -> {
			ServerLevel level = p.level();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v != null) {
				v.snapTo(p.getX(), p.getY(), p.getZ() + forward, 180.0F, 0.0F);
				v.setNoAi(true);
				v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
				v.setHealth(1000);
				level.addFreshEntity(v);
			}
		});
	}

	private static void clearVillagers(TestSingleplayerContext sp) {
		sp.getServer().runCommand("kill @e[type=minecraft:villager]");
	}

	private static void reset(TestSingleplayerContext sp) {
		onServer(sp, p -> Attachments.profile(p).cooldowns.clear());
	}

	private static void home(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			p.teleportTo(p.level(), 0.5, p.level().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5,
					java.util.Set.of(), 0.0F, p.getXRot(), true);
			p.setDeltaMovement(Vec3.ZERO);
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass valk() {
		return Classes.byId(Valkyrie.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
