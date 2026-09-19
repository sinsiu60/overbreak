package kr.overbreak.test.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
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
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * 워리어 애니메이션 · TPS 카메라 — 실제 클라이언트로 스킬을 쓰고 구간마다 스크린샷을 남깁니다.
 * 스크린샷 이름: 시점_동작_경과틱.
 */
public final class WarriorAnimClientTest implements FabricClientGameTest {
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
			onServer(sp, p -> Classes.give(p, warrior()));
			ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
			ctx.getInput().lookAt(0.0F, 10.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			sp.getServer().runCommand("effect give @a minecraft:absorption 60 2");
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(3));
			// 접속 시 기본 시점이 어깨 너머 3인칭인지 · 조준점이 보이는지
			shot(ctx, "tps_idle", 0);

			skills(ctx, sp, "tps", true);
			basics(ctx, sp, "tps");
			walking(ctx, sp, "tps");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			shot(ctx, "front_idle", 0);
			// 정면 시점에서는 사슬 대상을 두지 않습니다 (카메라와 캐릭터 사이를 가려서 자세가 안 보임)
			skills(ctx, sp, "front", false);
			basics(ctx, sp, "front");
			walking(ctx, sp, "front");

			// 1인칭: 살육 · 기본 공격
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			resetCooldowns(sp);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			ctx.getInput().pressMouse(RIGHT_MOUSE);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(6));
			shot(ctx, "fp_slay", SkillAnimPayload.SLAY);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(8));
			shot(ctx, "fp_slay", SkillAnimPayload.SLAY);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			ctx.getInput().pressMouse(LEFT_MOUSE);
			burst(ctx, "fp_basic", SkillAnimPayload.BASIC, 1, 1, 1, 2);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
			ctx.getInput().pressMouse(LEFT_MOUSE);
			burst(ctx, "fp_back", SkillAnimPayload.BASIC_BACK, 1, 1, 1, 2);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
			// 1인칭 피의 사슬: 왼손으로 돌리고 던지기 (정면 5칸 허수아비)
			onServer(sp, p -> {
				Attachments.profile(p).cooldowns.clear();
				ServerLevel level = p.level();
				Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				if (v != null) {
					v.snapTo(p.getX(), p.getY(), p.getZ() + 5, 180.0F, 0.0F);
					v.setNoAi(true);
					v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
					v.setHealth(1000);
					level.addFreshEntity(v);
				}
			});
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
			onServer(sp, p -> warrior().tertiary(p));
			burst(ctx, "fp_chain", SkillAnimPayload.CHAIN, 2, 3, 3, 3, 2, 2, 3, 4, 6, 8);
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));
		}
	}

	private static void skills(ClientGameTestContext ctx, TestSingleplayerContext sp, String view, boolean chainTarget) {
		clearVillagers(sp);
		// 살육 — 우클릭 실제 입력
		resetCooldowns(sp);
		ctx.getInput().pressMouse(RIGHT_MOUSE);
		burst(ctx, view + "_slay", SkillAnimPayload.SLAY, 3, 4, 4, 3, 2, 2, 2, 3, 4);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

		resetCooldowns(sp);
		onServer(sp, p -> warrior().secondary(p));
		burst(ctx, view + "_fury", SkillAnimPayload.FURY, 1, 2, 3, 4, 5);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));

		resetCooldowns(sp);
		if (chainTarget) {
			onServer(sp, p -> {
				// 정면 5칸 허수아비 (끌려오는 데까지 보이게)
				ServerLevel level = p.level();
				Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				if (v != null) {
					v.snapTo(p.getX(), p.getY(), p.getZ() + 5, 180.0F, 0.0F);
					v.setNoAi(true);
				v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
				v.setHealth(1000);
					v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
					v.setHealth(1000);
					level.addFreshEntity(v);
				}
			});
		}
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
		onServer(sp, p -> warrior().tertiary(p));
		burst(ctx, view + "_chain", SkillAnimPayload.CHAIN, 3, 3, 3, 3, 2, 2, 3, 4, 6, 8);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(30));
		clearVillagers(sp);

		onServer(sp, p -> {
			// 처형장 안 4칸에 허수아비 — 가운데 말뚝에서 사슬이 이어지는지
			ServerLevel level = p.level();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v != null) {
				v.snapTo(p.getX() + 2.5, p.getY(), p.getZ() + 3.5, 180.0F, 0.0F);
				v.setNoAi(true);
				v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
				v.setHealth(1000);
				level.addFreshEntity(v);
			}
		});
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(2));
		onServer(sp, p -> {
			UltGauge.fill(p);
			warrior().ult(p);
		});
		burst(ctx, view + "_ult", SkillAnimPayload.ULT, 2, 2, 2, 3, 4, 6, 8);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(25));
		shot(ctx, view + "_ultcage", 0);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(100));
		shot(ctx, view + "_ultsink", 0);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
		shot(ctx, view + "_ultgone", 0);
		clearVillagers(sp);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(5));
	}

	/** 기본 공격 — 좌클릭 실제 입력, 정방향 한 번 · 역방향 한 번. */
	private static void basics(ClientGameTestContext ctx, TestSingleplayerContext sp, String view) {
		resetCooldowns(sp);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(25));
		ctx.getInput().pressMouse(LEFT_MOUSE);
		burst(ctx, view + "_basic", SkillAnimPayload.BASIC, 1, 1, 1, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(16));
		ctx.getInput().pressMouse(LEFT_MOUSE);
		burst(ctx, view + "_back", SkillAnimPayload.BASIC_BACK, 1, 1, 1, 2, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
	}

	/** 걸으면서 스킬 · 기본 공격 — 다리는 걷기 동작이어야 합니다. */
	private static void walking(ClientGameTestContext ctx, TestSingleplayerContext sp, String view) {
		resetCooldowns(sp);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(6));
		ctx.getInput().pressMouse(RIGHT_MOUSE);
		burst(ctx, view + "_walkslay", SkillAnimPayload.SLAY, 5, 4, 6, 3);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(12));
		onServer(sp, p -> Attachments.profile(p).atkCd = 0);
		ctx.getInput().pressMouse(LEFT_MOUSE);
		burst(ctx, view + "_walkbasic", SkillAnimPayload.BASIC, 2, 2);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(20));
		// 걸어간 만큼 되돌아와 다음 구간 구도를 맞춥니다
		onServer(sp, p -> p.teleportTo(p.level(), 0.5, p.getY(), 0.5, java.util.Set.of(), 0.0F, 10.0F, true));
		ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(10));
	}

	private static void burst(ClientGameTestContext ctx, String label, int anim, int... gaps) {
		for (int gap : gaps) {
			ctx.waitTicks(kr.overbreak.core.tick.Ticks.of(gap));
			shot(ctx, label, anim);
		}
	}

	private static void clearVillagers(TestSingleplayerContext sp) {
		sp.getServer().runCommand("kill @e[type=minecraft:villager]");
	}

	private static void resetCooldowns(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			Attachments.profile(p).atkCd = 0;
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass warrior() {
		return Classes.byId(Warrior.ID);
	}

	private static void shot(ClientGameTestContext ctx, String label, int anim) {
		long t = ctx.computeOnClient(mc -> mc.player == null ? -1L : SkillAnims.elapsedTicks(mc.player.getId(), anim));
		String name = label + "_" + (t < 0 ? "none" : String.format("t%02d", t));
		Path path = ctx.takeScreenshot(name);
		System.out.println("[overbreak-shot] " + path.toAbsolutePath());
	}
}
