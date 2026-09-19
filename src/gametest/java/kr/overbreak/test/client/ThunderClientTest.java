package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.thunder.Thunder;
import kr.overbreak.client.anim.SkillAnims;
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

/** 뇌신 애니메이션 · 창 · 번개 연출 · 화면 효과 — 구간별 스크린샷. 이름: 시점_동작_경과틱. */
public final class ThunderClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ctx.runOnClient(mc -> {
			for (String name : new String[] {"thunder.cast", "thunder.dash", "thunder.field", "thunder.smite", "thunder.ult"}) {
				if (PlayerAnimations.get(name) == null) {
					throw new AssertionError("애니메이션 파일에 없음: " + name);
				}
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule fall_damage false");
			onServer(sp, p -> Classes.give(p, thunder()));
			ctx.getInput().lookAt(0.0F, 5.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

			ctx.getInput().pressKey(297);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			ctx.takeScreenshot("th_skillinfo");
			ctx.runOnClient(mc -> kr.overbreak.client.hud.SkillInfoScreen.debugHover = 5);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
			ctx.takeScreenshot("th_skillinfo_hover_5");
			ctx.runOnClient(mc -> kr.overbreak.client.hud.SkillInfoScreen.debugHover = -1);
			ctx.getInput().pressKey(297);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "th_tps_idle", 0);
			views(ctx, sp, "th_tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "th_front_idle", 0);
			views(ctx, sp, "th_front");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 5.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "th_fp_idle", 0);
			views(ctx, sp, "th_fp");
		}
	}

	private static void views(ClientGameTestContext ctx, TestSingleplayerContext sp, String prefix) {
		// 뇌격 (앞 7칸 허수아비 + 곁에 하나 → 연쇄)
		reset(sp);
		spawnDummyAt(sp, 0.0, 7.0);
		spawnDummyAt(sp, 2.5, 8.0);
		for (int i = 0; i < 3; i++) {
			onServer(sp, p -> {
				Thunder.state(p);
				thunder().tick(p);
			});
			onServer(sp, p -> {
				for (int k = 0; k < 11; k++) {
					thunder().tick(p);
				}
				thunder().basic(p);
			});
			burst(ctx, prefix + "_cast" + i, SkillAnimPayload.TH_CAST, 1, 2);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(4));
		}
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		clearVillagers(sp);

		// 섬전 (앞 3칸 허수아비)
		reset(sp);
		spawnDummyAt(sp, 0.0, 3.0);
		onServer(sp, p -> thunder().primary(p));
		burst(ctx, prefix + "_dash", SkillAnimPayload.TH_DASH, 1, 1, 1, 1, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		clearVillagers(sp);
		home(sp);

		// 뇌운 (앞 5칸 허수아비를 겨눔)
		reset(sp);
		spawnDummyAt(sp, 0.0, 5.0);
		onServer(sp, p -> thunder().secondary(p));
		burst(ctx, prefix + "_field", SkillAnimPayload.TH_FIELD, 3, 8, 12, 20);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(40));
		clearVillagers(sp);

		// 낙뢰 (앞 6칸)
		reset(sp);
		spawnDummyAt(sp, 0.0, 6.0);
		onServer(sp, p -> thunder().tertiary(p));
		burst(ctx, prefix + "_smite", SkillAnimPayload.TH_SMITE, 4, 6, 3, 2, 4);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));
		clearVillagers(sp);

		// 뇌신강림 (주위 셋)
		reset(sp);
		spawnDummyAt(sp, 0.0, 6.0);
		spawnDummyAt(sp, 4.0, 5.0);
		spawnDummyAt(sp, -4.0, 8.0);
		onServer(sp, p -> {
			UltGauge.fill(p);
			thunder().ult(p);
		});
		burst(ctx, prefix + "_ult", SkillAnimPayload.TH_ULT, 6, 13, 2, 10, 20, 4, 12);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));
		clearVillagers(sp);
		home(sp);
	}

	private static void burst(ClientGameTestContext ctx, String label, int anim, int... gaps) {
		for (int gap : gaps) {
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(gap));
			shot(ctx, label, anim);
		}
	}

	private static void spawnDummyAt(TestSingleplayerContext sp, double side, double forward) {
		onServer(sp, p -> {
			ServerLevel level = p.level();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v != null) {
				v.snapTo(p.getX() + side, p.getY(), p.getZ() + forward, 180.0F, 0.0F);
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

	private static PvpClass thunder() {
		return Classes.byId(Thunder.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
