package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.gunslinger.Gunslinger;
import kr.overbreak.classes.gunslinger.RecoilBoost;
import kr.overbreak.client.input.AirControl;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 공중 제어 (전 직업) · 반동 도약 · 궤적 추격 비행 — 진짜 플레이어로 잽니다 (0.2f).
 *
 *   공중 제어: 이동키로 약 0.25초 만에 초당 6칸 · 손을 떼면 관성으로 서서히 감속 · 계산식 (가속 · 급선회 · 최고 속도 넘었을 때)
 *   반동 도약: 수평을 보고 쓰면 약 8칸 날며 약 1.5칸 뜸 · 바닥을 보면 곧장 약 7칸 솟구침
 *   궤적 추격: 점프 키로 초당 5칸 상승 · 떼면 초당 1칸 하강 · 이동키로 초당 10칸
 */
public final class AirControlClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		math();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			onServer(sp, p -> Classes.give(p, Classes.byId(Gunslinger.ID)));
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(20));
			ctx.getInput().lookAt(0.0F, 0.0F);

			// 1) 공중 제어 — 높이 띄워 떨어지는 동안 W
			onServer(sp, p -> p.teleportTo(p.getX(), p.getY() + 30.0, p.getZ()));
			ctx.waitTicks(3);
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(Ticks.of(8));
			double top = speed(ctx);
			System.out.println("[AirControlClientTest] 0.4초 누른 뒤 수평 속도 초당 " + top + "칸");
			if (Math.abs(top - AirControl.NORMAL.maxSpeed()) > 0.8) {
				throw new AssertionError("공중 최고 속도 초당 6칸이어야 함: " + top);
			}
			ctx.getInput().releaseKey(o -> o.keyUp);
			ctx.waitTicks(Ticks.of(5));
			double coast = speed(ctx);
			System.out.println("[AirControlClientTest] 손 뗀 0.25초 뒤 초당 " + coast + "칸 (관성)");
			if (coast < 3.5 || coast > 5.8) {
				throw new AssertionError("손을 떼면 서서히 감속해야 함 (초당 약 5칸): " + coast);
			}
			reset(sp);
			ctx.waitTicks(Ticks.of(60));

			// 2) 반동 도약 — 수평 조준: 8칸 날며 약 1.5칸 뜸
			ctx.getInput().lookAt(0.0F, 0.0F);
			ctx.waitTicks(Ticks.of(5));
			Vec3 a0 = pos(ctx);
			onServer(sp, p -> Classes.byId(Gunslinger.ID).primary(p));
			ctx.waitTicks(Ticks.of(RecoilBoost.PROPULSION) + 2);
			Vec3 a1 = pos(ctx);
			double flat = Math.hypot(a1.x - a0.x, a1.z - a0.z);
			double rise = a1.y - a0.y;
			System.out.println("[AirControlClientTest] 수평 조준 도약 거리 " + a1.distanceTo(a0) + "칸 (수평 " + flat + ", 상승 " + rise + ")");
			if (Math.abs(a1.distanceTo(a0) - RecoilBoost.DISTANCE) > 0.5 || Math.abs(rise - 1.52) > 0.3) {
				throw new AssertionError("수평 조준 도약 — 8칸 · 약 1.5칸 상승이어야 함: 거리 " + a1.distanceTo(a0) + ", 상승 " + rise);
			}
			ctx.waitTicks(Ticks.of(40));
			reset(sp);

			// 3) 반동 도약 — 바닥 조준: 곧장 약 7칸
			ctx.getInput().lookAt(0.0F, 90.0F);
			ctx.waitTicks(Ticks.of(5));
			Vec3 b0 = pos(ctx);
			onServer(sp, p -> Classes.byId(Gunslinger.ID).primary(p));
			ctx.waitTicks(Ticks.of(RecoilBoost.PROPULSION) + 2);
			Vec3 b1 = pos(ctx);
			System.out.println("[AirControlClientTest] 바닥 조준 도약 상승 " + (b1.y - b0.y) + "칸, 수평 " + Math.hypot(b1.x - b0.x, b1.z - b0.z));
			if (Math.abs(b1.y - b0.y - RecoilBoost.FLOOR_RISE) > 0.5 || Math.hypot(b1.x - b0.x, b1.z - b0.z) > 0.3) {
				throw new AssertionError("바닥 조준 — 곧장 약 7칸이어야 함: " + (b1.y - b0.y));
			}
			ctx.waitTicks(Ticks.of(60));
			reset(sp);

			// 5) 사선 앵커 — 벽 끝(3칸 벽 윗부분)에 걸면 턱 위로 넘어감
			onServer(sp, p -> {
				int bx = (int) Math.floor(p.getX());
				int by = (int) Math.floor(p.getY());
				int bz = (int) Math.floor(p.getZ());
				for (int x = bx - 2; x <= bx + 2; x++) {
					for (int y = by; y < by + 3; y++) {
						for (int z = bz + 6; z <= bz + 7; z++) {
							p.level().setBlockAndUpdate(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
						}
					}
				}
			});
			ctx.waitTicks(Ticks.of(3));
			Vec3 d0 = pos(ctx);
			// 벽 윗부분 (발에서 2.6칸 높이) 을 겨눔
			double wallZ = Math.floor(d0.z) + 6.0;
			float pitch = (float) -Math.toDegrees(Math.atan2(d0.y + 2.6 - (d0.y + 1.62), wallZ - d0.z));
			ctx.getInput().lookAt(0.0F, pitch);
			ctx.waitTicks(Ticks.of(2));
			onServer(sp, p -> Classes.byId(Gunslinger.ID).tertiary(p));
			ctx.waitTicks(Ticks.of(20));
			Vec3 d1 = pos(ctx);
			System.out.println("[AirControlClientTest] 벽 끝 앵커 뒤 높이 " + (d1.y - d0.y) + "칸, 앞으로 " + (d1.z - d0.z) + "칸 (벽 앞면 " + (wallZ - d0.z) + ")");
			if (d1.y - d0.y < 2.9 || d1.z < wallZ) {
				throw new AssertionError("벽 끝에 걸면 턱 위로 넘어가야 함: 높이 " + (d1.y - d0.y) + ", z " + d1.z + " / 벽 " + wallZ);
			}
			ctx.takeScreenshot("air_anchor_ledge");
			onServer(sp, p -> p.teleportTo(p.getX(), p.getY() - 3.0, p.getZ() - 12.0));
			ctx.waitTicks(Ticks.of(20));
			reset(sp);

			// 6) 사선 앵커 — 머리 위 12칸 천장에 수직으로 걸고 점프로 끊으면 너무 높이 솟지 않음
			onServer(sp, p -> p.level().setBlockAndUpdate(net.minecraft.core.BlockPos.containing(p.getX(), p.getY() + 14.0, p.getZ()),
					net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
			ctx.getInput().lookAt(0.0F, -90.0F);
			ctx.waitTicks(Ticks.of(3));
			Vec3 e0 = pos(ctx);
			onServer(sp, p -> Classes.byId(Gunslinger.ID).tertiary(p));
			ctx.waitTicks(Ticks.of(2));
			Vec3 snapAt = pos(ctx);
			ctx.getInput().holdKey(o -> o.keyJump);
			double peak = snapAt.y;
			for (int i = 0; i < 30; i++) {
				ctx.waitTicks(Ticks.of(1));
				peak = Math.max(peak, pos(ctx).y);
			}
			ctx.getInput().releaseKey(o -> o.keyJump);
			System.out.println("[AirControlClientTest] 수직 앵커를 끊은 뒤 더 솟은 높이 " + (peak - snapAt.y) + "칸 (끊은 자리 " + (snapAt.y - e0.y) + "칸)");
			if (peak - snapAt.y > 3.2) {
				throw new AssertionError("수직으로 걸고 끊었을 때 너무 높이 솟음: " + (peak - snapAt.y));
			}
			ctx.waitTicks(Ticks.of(40));
			reset(sp);

			// 4) 궤적 추격 — 점프 키 상승 · 떼면 천천히 하강 · 이동키 초당 10칸
			ctx.getInput().lookAt(0.0F, 0.0F);
			onServer(sp, p -> {
				UltGauge.fill(p);
				Classes.byId(Gunslinger.ID).ult(p);
			});
			ctx.waitTicks(Ticks.of(2));
			Vec3 c0 = pos(ctx);
			ctx.getInput().holdKey(o -> o.keyJump);
			ctx.waitTicks(Ticks.of(20));
			ctx.getInput().releaseKey(o -> o.keyJump);
			Vec3 c1 = pos(ctx);
			System.out.println("[AirControlClientTest] 비행 1초 상승 " + (c1.y - c0.y) + "칸");
			if (Math.abs(c1.y - c0.y - 5.0) > 1.0) {
				throw new AssertionError("점프 키 1초 = 약 5칸 상승이어야 함: " + (c1.y - c0.y));
			}
			ctx.getInput().holdKey(o -> o.keyUp);
			ctx.waitTicks(Ticks.of(20));
			double fly = speed(ctx);
			ctx.getInput().releaseKey(o -> o.keyUp);
			Vec3 c2 = pos(ctx);
			ctx.waitTicks(Ticks.of(20));
			Vec3 c3 = pos(ctx);
			System.out.println("[AirControlClientTest] 비행 수평 속도 초당 " + fly + "칸, 손 뗀 1초 하강 " + (c2.y - c3.y) + "칸");
			if (Math.abs(fly - AirControl.FLIGHT.maxSpeed()) > 1.2) {
				throw new AssertionError("비행 이동키 초당 10칸이어야 함: " + fly);
			}
			if (Math.abs(c2.y - c3.y - AirControl.SINK) > 0.5) {
				throw new AssertionError("비행 중 손을 떼면 초당 1칸 하강이어야 함: " + (c2.y - c3.y));
			}
			ctx.takeScreenshot("air_pursuit_flight");
			onServer(sp, Classes::clear);
		}
	}

	/** 계산식 — 가속 · 급선회 · 관성 · 최고 속도를 넘었을 때. */
	private static void math() {
		double dt = 1.0 / 60.0;
		double[] v = {0.0, 0.0};
		for (int i = 0; i < 15; i++) {
			v = AirControl.step(v[0], v[1], 0.0, 1.0, dt, AirControl.NORMAL);
		}
		check(Math.abs(v[1] - 6.0) < 0.01, "0.25초 가속으로 초당 6칸: " + v[1]);
		double[] r = AirControl.step(0.0, 6.0, 0.0, -1.0, dt, AirControl.NORMAL);
		check(Math.abs(r[1] - (6.0 - 36.0 * dt)) < 1.0E-9, "반대 입력은 초당 36칸² 로 급선회: " + r[1]);
		double[] c = AirControl.step(0.0, 6.0, 0.0, 0.0, dt, AirControl.NORMAL);
		check(Math.abs(c[1] - (6.0 - 4.0 * dt)) < 1.0E-9, "입력이 없으면 초당 4칸² 감속: " + c[1]);
		double[] f = AirControl.step(0.0, 12.0, 0.0, 1.0, dt, AirControl.NORMAL);
		check(f[1] < 12.0 && f[1] > 11.9, "최고 속도를 넘으면 같은 쪽 입력은 감속만: " + f[1]);
		double[] t = AirControl.step(0.0, 12.0, 1.0, 0.0, dt, AirControl.NORMAL);
		check(Math.hypot(t[0], t[1]) < 12.0 && t[0] > 0.0, "옆 입력은 방향을 틀며 속력을 줄임: " + t[0] + ", " + t[1]);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}

	/** 수평 속도 (초당 칸) — 최근 한 틱 이동량. */
	private static double speed(ClientGameTestContext ctx) {
		Vec3 a = pos(ctx);
		ctx.waitTicks(1);
		Vec3 b = pos(ctx);
		return Math.hypot(b.x - a.x, b.z - a.z) * 20.0 * Ticks.k();
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
