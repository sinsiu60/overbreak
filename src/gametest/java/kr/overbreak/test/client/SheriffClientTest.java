package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.sheriff.Sheriff;
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

/** 보안관 애니메이션 · 리볼버 모델 · 궤적 · 조준 화면 — 구간별 스크린샷. 이름: 시점_동작_경과틱. */
public final class SheriffClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ctx.runOnClient(mc -> {
			for (String name : new String[] {"sheriff.shot", "sheriff.fan", "sheriff.reload", "sheriff.roll", "sheriff.flash", "sheriff.deadeye", "sheriff.deadeye_fire"}) {
				if (PlayerAnimations.get(name) == null) {
					throw new AssertionError("애니메이션 파일에 없음: " + name);
				}
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule fall_damage false");
			onServer(sp, p -> Classes.give(p, sheriff()));
			ctx.getInput().lookAt(0.0F, 5.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			shot(ctx, "sh_tps_idle", 0);
			views(ctx, sp, "sh_tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "sh_front_idle", 0);
			views(ctx, sp, "sh_front");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0.0F, 5.0F);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "sh_fp_idle", 0);
			// 오른쪽 아래 스킬 HUD 를 잠깐 끄고 손잡이 · 주먹이 맞물리는지 확인 (F1 숨기기는 1인칭 손까지 숨겨서 못 씀)
			ctx.runOnClient(mc -> kr.overbreak.client.input.InputMode.receive(new kr.overbreak.net.InputModePayload(false)));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
			shot(ctx, "sh_fp_grip", 0);
			ctx.runOnClient(mc -> kr.overbreak.client.input.InputMode.receive(new kr.overbreak.net.InputModePayload(true)));
			views(ctx, sp, "sh_fp");
		}
	}

	private static void views(ClientGameTestContext ctx, TestSingleplayerContext sp, String prefix) {
		// 피스키퍼 (앞 7칸 허수아비)
		reset(sp);
		spawnDummy(sp, 7.0);
		onServer(sp, p -> sheriff().basic(p));
		burst(ctx, prefix + "_shot", SkillAnimPayload.SH_SHOT, 1, 1, 1, 1, 1, 1, 2);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));

		// 리볼버 난사
		reset(sp);
		onServer(sp, p -> sheriff().primary(p));
		burst(ctx, prefix + "_fan", SkillAnimPayload.SH_FAN, 1, 2, 3, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(15));
		clearVillagers(sp);

		// 재장전 (R 키와 같은 서버 처리)
		reset(sp);
		onServer(sp, p -> {
			Sheriff.state(p).ammo = 2;
			sheriff().reload(p);
		});
		burst(ctx, prefix + "_reload", SkillAnimPayload.SH_RELOAD, 2, 3, 3, 4, 4, 1, 1, 1, 1, 1, 4, 4, 4, 4, 4);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 전술 구르기 (정면)
		reset(sp);
		onServer(sp, p -> sheriff().secondary(p));
		burst(ctx, prefix + "_roll", SkillAnimPayload.SH_ROLL, 1, 1, 1, 2, 2, 1, 1, 1, 1, 1);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
		home(sp);

		// 섬광 수류탄
		reset(sp);
		onServer(sp, p -> sheriff().tertiary(p));
		burst(ctx, prefix + "_flash", SkillAnimPayload.SH_FLASH, 1, 2, 2, 3, 4);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));

		// 황야의 무법자 (앞에 둘, 옆에 하나)
		reset(sp);
		spawnDummy(sp, 6.0);
		spawnDummyAt(sp, 2.5, 9.0);
		spawnDummyAt(sp, -3.0, 11.0);
		onServer(sp, p -> {
			UltGauge.fill(p);
			sheriff().ult(p);
		});
		burst(ctx, prefix + "_deadeye", SkillAnimPayload.SH_DEADEYE, 3, 10, 12, 14);
		burst(ctx, prefix + "_deadeye_fire", SkillAnimPayload.SH_DEADEYE_FIRE, 1, 3);
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

	private static PvpClass sheriff() {
		return Classes.byId(Sheriff.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
