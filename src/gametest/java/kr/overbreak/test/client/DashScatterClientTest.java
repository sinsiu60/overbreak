package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.gunslinger.DashScatter;
import kr.overbreak.classes.gunslinger.Gunslinger;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 돌진 난사 — 진짜 플레이어로 움직임을 잽니다 (가짜 플레이어는 서버에서 움직이지 않아 거리를 못 잽니다).
 *
 *   바라보는 방향으로 6칸 (땅에서 아래를 보면 정면) · 공중에서 높이 유지 · 웅크리기를 누른 채 난간 쪽으로 써도 안 끊김
 *   웅크리기 키를 실제로 눌러 쓰므로 입력 → 스킬 → 즉시 연출(예측 재생)까지 한 길로 지나갑니다.
 */
public final class DashScatterClientTest implements FabricClientGameTest {
	/** 거리 허용 오차 (칸) — 서버 · 클라이언트 틱 박자 차이. */
	private static final double TOL = 0.6;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			onServer(sp, p -> Classes.give(p, Classes.byId(Gunslinger.ID)));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(20));

			// 1) 땅에서 위(30도)를 보고 — 바라본 방향 그대로 6칸 (앞 5.2 · 위 3)
			ctx.getInput().lookAt(0.0F, -30.0F);
			Vec3 a0 = pos(ctx);
			ctx.getInput().holdKey(o -> o.keyShift);
			// 누른 즉시(서버 확인 전) 본인 화면에 연출이 시작됐는가
			ctx.waitTicks(1);
			ctx.runOnClient(mc -> {
				if (mc.player == null || SkillAnims.find(mc.player.getId(), SkillAnimPayload.GS_SCATTER) == null) {
					throw new AssertionError("웅크리기를 누른 즉시 연출이 시작되지 않음");
				}
			});
			ctx.waitTicks(Ticks.of(DashScatter.SCATTER_START) + 3);
			ctx.getInput().releaseKey(o -> o.keyShift);
			Vec3 a1 = pos(ctx);
			double up = a1.distanceTo(a0);
			if (Math.abs(up - DashScatter.DISTANCE) > TOL || Math.abs(a1.y - a0.y - 3.0) > TOL) {
				throw new AssertionError("위를 보고 돌진 " + up + "칸, 높이 " + (a1.y - a0.y) + " (6칸 · 위로 3칸이어야 함)");
			}
			System.out.println("[DashScatterClientTest] 위 30도 돌진 " + up + "칸, 높이 변화 " + (a1.y - a0.y));
			ctx.takeScreenshot("scatter_up_done");
			ctx.waitTicks(Ticks.of(DashScatter.LENGTH) + Ticks.of(20));
			reset(sp);

			// 1-2) 땅에서 아래(45도)를 보고 — 바닥으로 처박히지 않고 정면으로 6칸
			ctx.getInput().lookAt(0.0F, 45.0F);
			Vec3 d0 = pos(ctx);
			ctx.getInput().holdKey(o -> o.keyShift);
			ctx.waitTicks(Ticks.of(DashScatter.SCATTER_START) + 4);
			ctx.getInput().releaseKey(o -> o.keyShift);
			Vec3 d1 = pos(ctx);
			double flat = Math.hypot(d1.x - d0.x, d1.z - d0.z);
			if (Math.abs(flat - DashScatter.DISTANCE) > TOL || Math.abs(d1.y - d0.y) > 0.3) {
				throw new AssertionError("땅에서 아래를 보고 돌진 " + flat + "칸, 높이 " + (d1.y - d0.y) + " (수평 6칸이어야 함)");
			}
			System.out.println("[DashScatterClientTest] 땅 아래 45도 돌진 수평 " + flat + "칸, 높이 변화 " + (d1.y - d0.y));
			ctx.waitTicks(Ticks.of(DashScatter.LENGTH));
			reset(sp);

			// 2) 난간 — 웅크리기를 누른 채 3칸 높이 받침 끝으로 써도 떨어지며 끝까지 감
			onServer(sp, p -> {
				int bx = (int) Math.floor(p.getX());
				int by = (int) Math.floor(p.getY());
				int bz = (int) Math.floor(p.getZ());
				for (int x = bx - 1; x <= bx + 1; x++) {
					for (int z = bz - 1; z <= bz + 1; z++) {
						p.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x, by + 2, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
					}
				}
				p.teleportTo(bx + 0.5, by + 3.0, bz + 0.5);
			});
			ctx.waitTicks(Ticks.of(10));
			ctx.getInput().lookAt(0.0F, 0.0F);
			Vec3 b0 = pos(ctx);
			ctx.getInput().holdKey(o -> o.keyShift);
			ctx.waitTicks(Ticks.of(DashScatter.SCATTER_START) + 3);
			Vec3 b1 = pos(ctx);
			ctx.getInput().releaseKey(o -> o.keyShift);
			double edge = Math.hypot(b1.x - b0.x, b1.z - b0.z);
			if (edge < DashScatter.DISTANCE - TOL) {
				throw new AssertionError("웅크리기를 누른 채 난간에서 돌진이 " + edge + "칸에서 끊김 (모서리 멈춤)");
			}
			System.out.println("[DashScatterClientTest] 난간 돌진 " + edge + "칸");
			ctx.takeScreenshot("scatter_edge_done");
			ctx.waitTicks(Ticks.of(DashScatter.LENGTH));
			reset(sp);

			// 3) 공중 — 돌진하는 동안 높이를 붙잡음
			onServer(sp, p -> p.teleportTo(p.getX(), p.getY() + 12.0, p.getZ()));
			ctx.waitTicks(3);
			Vec3 c0 = pos(ctx);
			onServer(sp, p -> Classes.byId(Gunslinger.ID).secondary(p));
			ctx.waitTicks(Ticks.of(DashScatter.BRAKE_START) - 1);
			Vec3 c1 = pos(ctx);
			// 기 모으기 0.1초 동안은 떨어지므로, 돌진 동안의 높이 변화만 봅니다 (돌진 시작 무렵부터 제동 직전까지)
			if (c0.y - c1.y > 1.0) {
				throw new AssertionError("공중 돌진 중 " + (c0.y - c1.y) + "칸 떨어짐 (높이를 붙잡아야 함)");
			}
			System.out.println("[DashScatterClientTest] 공중 돌진 중 내려간 높이 " + (c0.y - c1.y));
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.takeScreenshot("scatter_air_dash");
			// 난사 동안은 떨어지지 않고 그 자리에 떠 있음
			ctx.waitTicks(Ticks.of(DashScatter.SCATTER_START - DashScatter.BRAKE_START) + 2);
			Vec3 c2 = pos(ctx);
			ctx.waitTicks(Ticks.of(DashScatter.RECOVER_START - DashScatter.SCATTER_START) - 4);
			Vec3 c3 = pos(ctx);
			if (Math.abs(c3.y - c2.y) > 0.05) {
				throw new AssertionError("공중 난사 중 높이가 " + (c3.y - c2.y) + " 바뀜 (떠 있어야 함)");
			}
			System.out.println("[DashScatterClientTest] 공중 난사 중 높이 변화 " + (c3.y - c2.y));
			ctx.waitTicks(Ticks.of(DashScatter.LENGTH));
			onServer(sp, p -> {
				if (p.isNoGravity()) {
					throw new AssertionError("끝난 뒤에도 중력이 꺼져 있음");
				}
			});
			reset(sp);

			// 4) 3인칭 몸 동작 — 단계마다 한 장 (기 모으기 · 돌진 · 난사 들어가기 · 난사 · 마무리)
			// 공중 시험에서 떨어져 이미 땅 위 — 착지만 기다림
			ctx.waitTicks(Ticks.of(20));
			// 돌진 끝자리 둘레에 적 둘 — 분신이 옮겨 다니며 쏘는 대상
			sp.getServer().runCommand("execute as @p at @s run summon minecraft:zombie ~3 ~ ~8.5 {NoAI:1b,Invulnerable:1b}");
			sp.getServer().runCommand("execute as @p at @s run summon minecraft:zombie ~-3 ~ ~9.5 {NoAI:1b,Invulnerable:1b}");
			ctx.waitTicks(Ticks.of(5));
			ctx.getInput().lookAt(0.0F, 10.0F);
			onServer(sp, p -> Classes.byId(Gunslinger.ID).secondary(p));
			int now = 0;
			for (int at : new int[] {1, 4, 8, 11, 14, 18, 22, 26, 29}) {
				ctx.waitTicks(Ticks.of(at - now));
				now = at;
				ctx.takeScreenshot("scatter_3p_" + at);
			}
			ctx.waitTicks(Ticks.of(DashScatter.LENGTH));
			onServer(sp, Classes::clear);
		}
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static void reset(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			Attachments.combatant(p).casting = false;
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}
}
