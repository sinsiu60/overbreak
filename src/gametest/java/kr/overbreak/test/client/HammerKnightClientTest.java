package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.hammer.HammerKnight;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.client.anim.SkillAnims;
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

/** 햄머나이트 애니메이션 · 판정 범위 모델 · 넘어뜨림 — 구간별 스크린샷. 이름: 동작_경과틱. */
public final class HammerKnightClientTest implements FabricClientGameTest {
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
			onServer(sp, p -> Classes.give(p, hk()));
			ctx.getInput().lookAt(0.0F, 20.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			shot(ctx, "hk_tps_idle", 0);

			// 지면 분쇄 (우클릭 실제 입력)
			ctx.getInput().pressMouse(RIGHT_MOUSE);
			burst(ctx, "hk_tps_smash", SkillAnimPayload.HK_SMASH, 3, 4, 4, 2, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

			// 기본 공격 부채꼴
			reset(sp);
			ctx.getInput().pressMouse(LEFT_MOUSE);
			burst(ctx, "hk_tps_basic", SkillAnimPayload.BASIC, 1, 2);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

			// 돌진 충격 → 곧바로 지면 분쇄 예약
			reset(sp);
			onServer(sp, p -> hk().secondary(p));
			burst(ctx, "hk_tps_charge", SkillAnimPayload.HK_CHARGE, 2, 3, 2);
			onServer(sp, p -> hk().primary(p));
			burst(ctx, "hk_tps_combo", SkillAnimPayload.HK_SLAM, 3, 2, 2, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			home(sp);

			// 중력 파쇄 (주위 허수아비)
			reset(sp);
			spawnDummy(sp, 0.0, 3.5);
			spawnDummy(sp, 2.5, 2.0);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			onServer(sp, p -> hk().tertiary(p));
			burst(ctx, "hk_tps_crush", SkillAnimPayload.HK_SMASH, 2, 3, 3, 3, 6, 20);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			clearVillagers(sp);

			// 대지 진동파 (정면 8칸 허수아비 → 넘어뜨림)
			reset(sp);
			spawnDummy(sp, 1.5, 5.0);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			onServer(sp, p -> {
				UltGauge.fill(p);
				hk().ult(p);
			});
			burst(ctx, "hk_tps_ult", SkillAnimPayload.HK_ULT, 3, 5, 6, 3, 1, 2, 3, 4, 6, 10);
			ctx.getInput().lookAt(0.0F, 30.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
			shot(ctx, "hk_tps_knocked", 0);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			shot(ctx, "hk_tps_knocked", 0);
			ctx.getInput().lookAt(0.0F, 20.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));
			clearVillagers(sp);

			// 정면 시점: 분쇄 · 돌진 · 진동파 자세
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			reset(sp);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			onServer(sp, p -> hk().primary(p));
			burst(ctx, "hk_front_smash", SkillAnimPayload.HK_SMASH, 5, 5, 2, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			reset(sp);
			onServer(sp, p -> hk().secondary(p));
			burst(ctx, "hk_front_charge", SkillAnimPayload.HK_CHARGE, 3, 3, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			home(sp);
			reset(sp);
			onServer(sp, p -> {
				UltGauge.fill(p);
				hk().ult(p);
			});
			burst(ctx, "hk_front_ult", SkillAnimPayload.HK_ULT, 4, 8, 5, 2, 4);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));

			// 1인칭
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 15.0F);
			reset(sp);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			onServer(sp, p -> hk().primary(p));
			burst(ctx, "hk_fp_smash", SkillAnimPayload.HK_SMASH, 3, 4, 3, 2, 4);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			reset(sp);
			onServer(sp, p -> hk().secondary(p));
			burst(ctx, "hk_fp_charge", SkillAnimPayload.HK_CHARGE, 2, 3, 2);
			onServer(sp, p -> hk().primary(p));
			burst(ctx, "hk_fp_combo", SkillAnimPayload.HK_SLAM, 3, 2, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			home(sp);
			reset(sp);
			onServer(sp, p -> {
				UltGauge.fill(p);
				hk().ult(p);
			});
			burst(ctx, "hk_fp_ult", SkillAnimPayload.HK_ULT, 4, 8, 5, 1, 3, 6);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));

			// 워리어 살육 범위 모델
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(0.0F, 25.0F);
			onServer(sp, p -> Classes.give(p, Classes.byId(Warrior.ID)));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			onServer(sp, p -> Classes.byId(Warrior.ID).primary(p));
			burst(ctx, "wr_tps_slay", SkillAnimPayload.SLAY, 3, 5, 5, 3);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
		}
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
			Attachments.profile(p).atkCd = 0;
		});
	}

	private static void home(TestSingleplayerContext sp) {
		onServer(sp, p -> p.teleportTo(p.level(), 0.5, p.getY(), 0.5, java.util.Set.of(), 0.0F, p.getXRot(), true));
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass hk() {
		return Classes.byId(HammerKnight.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
