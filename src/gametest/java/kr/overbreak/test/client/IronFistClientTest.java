package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.ironfist.IronFist;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.core.Attachments;
import kr.overbreak.input.InputRouter;
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

/** 파쇄권 애니메이션 · 건틀릿 모델 · 충전 빛 · HUD — 구간별 스크린샷. 이름: 시점_동작_경과틱. */
public final class IronFistClientTest implements FabricClientGameTest {
	private static final int LEFT_MOUSE = 0;
	private static final int RIGHT_MOUSE = 1;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule fall_damage false");
			onServer(sp, p -> Classes.give(p, fist()));
			ctx.getInput().lookAt(0.0F, 15.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			shot(ctx, "if_tps_idle", 0);

			views(ctx, sp, "if_tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "if_front_idle", 0);
			views(ctx, sp, "if_front");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 10.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "if_fp_idle", 0);
			views(ctx, sp, "if_fp");
		}
	}

	private static void views(ClientGameTestContext ctx, TestSingleplayerContext sp, String prefix) {
		// 철권포 (좌클릭 실제 입력)
		reset(sp);
		ctx.getInput().pressMouse(LEFT_MOUSE);
		burst(ctx, prefix + "_shot", SkillAnimPayload.IF_SHOT, 1, 1, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

		// 파워 블록 (웅크리기로 켜고 끄기)
		reset(sp);
		onServer(sp, p -> fist().secondary(p));
		burst(ctx, prefix + "_block", SkillAnimPayload.IF_BLOCK, 2, 6);
		onServer(sp, p -> fist().intercept(p, InputRouter.Slot.SECONDARY));
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 로켓 펀치 (우클릭을 실제로 누르고 있다가 뗌)
		reset(sp);
		ctx.getInput().holdMouse(RIGHT_MOUSE);
		burst(ctx, prefix + "_charge", SkillAnimPayload.IF_CHARGE, 3, 8, 12);
		ctx.getInput().releaseMouse(RIGHT_MOUSE);
		burst(ctx, prefix + "_punch", SkillAnimPayload.IF_PUNCH, 1, 1, 3, 6);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));
		home(sp);

		// 지진 강타
		reset(sp);
		onServer(sp, p -> fist().tertiary(p));
		burst(ctx, prefix + "_slam_air", SkillAnimPayload.IF_SLAM_AIR, 2, 4, 4);
		// 착지 순간은 물리에 따라 달라서, 착지 애니메이션이 시작될 때까지 기다렸다가 찍습니다
		for (int i = 0; i < kr.overbreak.core.tick.Ticks.of(60) && ctx.computeOnClient(mc -> mc.player == null ? -1L
				: SkillAnims.elapsedTicks(mc.player.getId(), SkillAnimPayload.IF_SLAM_HIT)) < 0; i++) {
			ctx.waitTick();
		}
		burst(ctx, prefix + "_slam_hit", SkillAnimPayload.IF_SLAM_HIT, 1, 1, 1, 3, 5);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
		home(sp);

		// 파멸의 일격 (앞 5칸 허수아비)
		reset(sp);
		spawnDummy(sp, 0.0, 5.0);
		onServer(sp, p -> {
			UltGauge.fill(p);
			fist().ult(p);
		});
		burst(ctx, prefix + "_ult_rise", SkillAnimPayload.IF_ULT_RISE, 2, 2, 4, 8);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		shot(ctx, prefix + "_ult_aim", 0);
		onServer(sp, p -> fist().intercept(p, InputRouter.Slot.PRIMARY));
		burst(ctx, prefix + "_ult_drop", SkillAnimPayload.IF_ULT_DROP, 2, 2, 2, 4, 8);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
		clearVillagers(sp);
		home(sp);
	}

	private static void burst(ClientGameTestContext ctx, String label, int anim, int... gaps) {
		for (int gap : gaps) {
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(gap));
			shot(ctx, label, anim);
		}
	}

	private static void spawnDummy(TestSingleplayerContext sp, double right, double forward) {
		onServer(sp, p -> {
			ServerLevel level = p.level();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v != null) {
				v.snapTo(p.getX() - right, p.getY(), p.getZ() + forward, 180.0F, 0.0F);
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
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			IronFist.state(p).fireCd = 0;
			IronFist.state(p).ammo = 4;
		});
	}

	private static void home(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			p.teleportTo(p.level(), 0.5, p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5,
					java.util.Set.of(), 0.0F, p.getXRot(), true);
			p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass fist() {
		return Classes.byId(IronFist.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
