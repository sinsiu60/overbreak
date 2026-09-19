package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.shade.Shade;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.combat.MeleeCleave;
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

/** 셰이드 애니메이션 · 검 모델 · 표창 · HUD — 구간별 스크린샷. 이름: 시점_동작_경과틱. */
public final class ShadeClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ctx.runOnClient(mc -> {
			for (String name : new String[] {"shade.rend", "shade.evade", "shade.kunai", "shade.strike"}) {
				if (PlayerAnimations.get(name) == null) {
					throw new AssertionError("애니메이션 파일에 없음: " + name);
				}
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule fall_damage false");
			onServer(sp, p -> Classes.give(p, shade()));
			ctx.getInput().lookAt(0.0F, 5.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

			// F8 스킬 설명 화면 — 글이 칸 안에 들어가는지 (궁극기 카드 세부 수치까지)
			ctx.getInput().pressKey(297);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			ctx.takeScreenshot("sd_skillinfo");
			for (int card : new int[] {3, 5}) {
				ctx.runOnClient(mc -> kr.overbreak.client.hud.SkillInfoScreen.debugHover = card);
				ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
				ctx.takeScreenshot("sd_skillinfo_hover_" + card);
			}
			ctx.runOnClient(mc -> kr.overbreak.client.hud.SkillInfoScreen.debugHover = -1);
			ctx.getInput().pressKey(297);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "sd_tps_idle", 0);

			// 그림자 가르기 잔상 — 앞으로 돌진시킨 뒤 옆에서 봄 (돌진 방향은 시전 순간에 정해짐)
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
			onServer(sp, p -> shade().primary(p));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(1));
			ctx.getInput().lookAt(-90.0F, 20.0F);
			burst(ctx, "sd_side_rend", SkillAnimPayload.SD_REND, 1, 1, 1, 2, 3, 4);
			int ghosts = ctx.computeOnClient(mc -> kr.overbreak.client.fx.Afterimages.count(mc.player.getId()));
			System.out.println("[overbreak-shot] afterimages left " + ghosts);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));
			if (ctx.computeOnClient(mc -> kr.overbreak.client.fx.Afterimages.count(mc.player.getId())) != 0) {
				throw new AssertionError("잔상은 0.6초 뒤 모두 사라져야 함");
			}
			home(sp);
			ctx.getInput().lookAt(0.0F, 5.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			views(ctx, sp, "sd_tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "sd_front_idle", 0);
			views(ctx, sp, "sd_front");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 5.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "sd_fp_idle", 0);
			views(ctx, sp, "sd_fp");
		}
	}

	private static void views(ClientGameTestContext ctx, TestSingleplayerContext sp, String prefix) {
		// 평타
		reset(sp);
		onServer(sp, p -> MeleeCleave.swing(p, shade()));
		burst(ctx, prefix + "_basic", SkillAnimPayload.BASIC, 1, 1, 2);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 그림자 가르기 (앞 3칸 허수아비)
		reset(sp);
		spawnDummy(sp, 3.0);
		onServer(sp, p -> shade().primary(p));
		burst(ctx, prefix + "_rend", SkillAnimPayload.SD_REND, 1, 1, 1, 2, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		clearVillagers(sp);
		home(sp);

		// 잔영 회피
		reset(sp);
		onServer(sp, p -> shade().secondary(p));
		burst(ctx, prefix + "_evade", SkillAnimPayload.SD_EVADE, 2, 4, 8);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 그림자 표창 → 그림자 걸음 (앞 8칸 허수아비)
		reset(sp);
		spawnDummy(sp, 8.0);
		onServer(sp, p -> shade().tertiary(p));
		burst(ctx, prefix + "_kunai", SkillAnimPayload.SD_KUNAI, 1, 1, 1, 1, 2, 4);
		onServer(sp, p -> shade().tertiary(p));
		burst(ctx, prefix + "_step", SkillAnimPayload.SD_STEP, 1, 1, 1, 1, 1, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		clearVillagers(sp);
		home(sp);

		// 천검난무 (주위 셋)
		reset(sp);
		spawnDummyAt(sp, 0.0, 4.0);
		spawnDummyAt(sp, 3.0, 2.0);
		spawnDummyAt(sp, -3.0, 5.0);
		onServer(sp, p -> {
			UltGauge.fill(p);
			shade().ult(p);
		});
		burst(ctx, prefix + "_storm", SkillAnimPayload.SD_STRIKE, 1, 2, 4, 5);
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

	private static void spawnDummy(TestSingleplayerContext sp, double forward) {
		spawnDummyAt(sp, 0.0, forward);
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
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			Attachments.profile(p).atkCd = 0;
		});
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

	private static PvpClass shade() {
		return Classes.byId(Shade.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
