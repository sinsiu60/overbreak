package kr.overbreak.test.client;

import java.util.Locale;

import kr.overbreak.core.tick.TickRateConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 60틱 조작감 확인 — 서버 · 클라이언트 모두 1초 60틱으로 돌며, <b>게임 속 초</b>(틱 ÷ 60)로 잰 값이 원래 조작감(바닐라 1초 20틱에서 잰 기준값)과 같은지 봅니다.
 * 클라이언트 게임테스트는 틱을 한 번씩 직접 돌리므로 실제 시간은 쓰지 않습니다.
 *
 *   플레이어 걷기 속도 (칸/초): 클라이언트가 계산 · MovementScale 보정
 *   플레이어 점프 높이 (칸): 클라이언트 · 점프 힘 · 중력 보정
 *   주민 낙하 · 에어본 높이 · 넉백 거리/높이: 서버가 계산
 *   기절 2초: 스킬 시간 (Ticks)
 * 모두 기준값 대비 8% 안이면 통과. [tickrate] 로그로 남깁니다.
 */
public final class TickRateClientTest implements FabricClientGameTest {
	/** 원래 조작감 기준값 (바닐라 1초 20틱에서 이 시험으로 잰 값). */
	private static final Result REFERENCE = new Result(4.19, 1.252, 1.250, 2.000, 0.565, 1.989, 0.965);

	private record Result(double walkSpeed, double jumpHeight, double mobFall, double stunSeconds, double airHeight,
						  double knockDistance, double knockHeight) {}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			Result r20 = REFERENCE;
			Result r60 = measure(ctx, sp, TickRateConfig.DEFAULT);
			log(r60);
			check(r20.walkSpeed, r60.walkSpeed, 0.08, "플레이어 걷기 속도");
			check(r20.jumpHeight, r60.jumpHeight, 0.08, "플레이어 점프 높이");
			check(r20.mobFall, r60.mobFall, 0.08, "주민 20칸 낙하 시간");
			check(r20.stunSeconds, r60.stunSeconds, 0.08, "기절 2초");
			check(r20.airHeight, r60.airHeight, 0.08, "에어본 높이");
			check(r20.knockDistance, r60.knockDistance, 0.08, "넉백 수평 거리");
			check(r20.knockHeight, r60.knockHeight, 0.08, "넉백 높이");
		}
	}

	private static Result measure(ClientGameTestContext ctx, TestSingleplayerContext sp, int rate) {
		ctx.waitTicks(20);
		if (TickRateConfig.tickRate() != rate) {
			throw new AssertionError("틱레이트가 " + rate + " 이 아님");
		}
		// 서버가 실제로 1초에 몇 틱 도는가
		int s0 = sp.getServer().computeOnServer(server -> server.getTickCount());
		long w0 = System.nanoTime();
		ctx.waitTicks(40);
		int s1 = sp.getServer().computeOnServer(server -> server.getTickCount());
		System.out.println(String.format(Locale.ROOT, "[tickrate] 서버 %d틱 설정 → 실제 %.1f틱/초", rate, (s1 - s0) / ((System.nanoTime() - w0) / 1.0E9)));
		double k = rate / 20.0;
		if (ctx.computeOnClient(mc -> TickRateConfig.tickRate()) != rate) {
			throw new AssertionError("클라이언트 틱레이트가 " + rate + " 로 맞춰지지 않음");
		}
		// ── 플레이어 걷기: 2초(40 x k 틱) 동안 누르고 게임 속 초로 나눔 ──
		home(ctx, sp);
		Vec3 start = pos(ctx);
		int walkTicks = (int) Math.round(40 * k);
		ctx.getInput().holdKeyFor(opts -> opts.keyUp, walkTicks);
		double walk = horizontal(pos(ctx), start) / (walkTicks / (double) rate);
		ctx.waitTicks(20);

		// ── 플레이어 점프: 한 번 뛰고 가장 높이 오른 높이 ──
		home(ctx, sp);
		double floor = pos(ctx).y;
		double apex = floor;
		ctx.getInput().holdKeyFor(opts -> opts.keyJump, 1);
		for (int i = 0; i < 30 * k; i++) {
			ctx.waitTick();
			apex = Math.max(apex, pos(ctx).y);
		}
		double jump = apex - floor;
		ctx.waitTicks(20);

		// ── 주민 낙하: 서버가 움직임 — 20칸 위에 세우고 땅에 닿을 때까지 실제 시간 ──
		home(ctx, sp);
		double groundY = pos(ctx).y;
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			v.snapTo(4.5, groundY + 20.0, 4.5, 0.0F, 0.0F);
			v.addTag("tick_test");
			level.addFreshEntity(v);
		});
		int f0 = sp.getServer().computeOnServer(server -> server.getTickCount());
		int f1 = -1;
		for (int i = 0; i < 400; i++) {
			ctx.waitTick();
			boolean landed = sp.getServer().computeOnServer(server -> server.overworld()
					.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-10, -100, -10, 20, 400, 20), v -> v.entityTags().contains("tick_test"))
					.stream().anyMatch(v -> v.onGround()));
			if (landed) {
				f1 = sp.getServer().computeOnServer(server -> server.getTickCount());
				break;
			}
		}
		sp.getServer().runCommand("kill @e[tag=tick_test]");
		// 서버 틱 수 ÷ 틱레이트 = 게임 속 초 (이 시험 환경은 통합 서버를 클라이언트 틱에 묶어 실제로는 1초 20틱만 돌려서, 실제 시간으로는 못 잼)
		double fall = f1 < 0 ? 99.0 : (f1 - f0) / (double) rate;

		// ── 스킬 시간: 기절 40 게임 틱(2초)이 풀릴 때까지의 서버 틱 ÷ 틱레이트 ──
		int[] stunStart = new int[1];
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			v.snapTo(6.5, groundY, 6.5, 0.0F, 0.0F);
			v.addTag("tick_test");
			level.addFreshEntity(v);
			kr.overbreak.cc.CrowdControl.stun(v, 40);
			stunStart[0] = server.getTickCount();
		});
		int stunEnd = -1;
		for (int i = 0; i < 400; i++) {
			ctx.waitTick();
			boolean stunned = sp.getServer().computeOnServer(server -> server.overworld()
					.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-10, -100, -10, 20, 400, 20), v -> v.entityTags().contains("tick_test"))
					.stream().anyMatch(v -> kr.overbreak.core.Attachments.combatant(v).stunT > 0));
			if (!stunned) {
				stunEnd = sp.getServer().computeOnServer(server -> server.getTickCount());
				break;
			}
		}
		sp.getServer().runCommand("kill @e[tag=tick_test]");
		double stun = stunEnd < 0 ? 99.0 : (stunEnd - stunStart[0]) / (double) rate;
		// ── 에어본 (전술 로켓과 같은 0.8초 · 증폭 0): 가장 높이 뜬 높이 ──
		double air = serverApex(ctx, sp, groundY, k, v -> kr.overbreak.cc.CrowdControl.airborne(v, 16, 0));
		// ── 바닐라 넉백 (세기 0.4): 가장 높이 뜬 높이 · 땅에 닿을 때까지 수평 거리 ──
		double[] knock = new double[2];
		serverApex(ctx, sp, groundY, k, v -> v.knockback(0.4, -1.0, 0.0, v.damageSources().generic(), 1.0F), knock);
		return new Result(walk, jump, fall, stun, air, knock[1], knock[0]);
	}

	/** 땅에 선 주민에게 action 을 하고 1.5초 동안 가장 높은 높이 (out 이 있으면 [높이, 수평 거리]). */
	private static double serverApex(ClientGameTestContext ctx, TestSingleplayerContext sp, double groundY, double k,
									 java.util.function.Consumer<Villager> action, double... out) {
		String tag = "tick_apex" + (APEX_N++);
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			v.snapTo(8.5, groundY, 8.5, 0.0F, 0.0F);
			// 인공지능은 켜 둠 (끄면 물리도 멈춤) — 걷지 않게 이동속도만 0
			v.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
			v.addTag(tag);
			level.addFreshEntity(v);
		});
		ctx.waitTicks((int) Math.round(10 * k));
		double[] s = sp.getServer().computeOnServer(server -> {
			Villager v = apexTarget(server, tag);
			action.accept(v);
			v.hurtMarked = true;
			return new double[] {v.getX(), v.getY(), v.getZ()};
		});
		double top = s[1];
		double dist = 0.0;
		for (int i = 0; i < 30 * k; i++) {
			ctx.waitTick();
			double[] now = sp.getServer().computeOnServer(server -> {
				Villager v = apexTarget(server, tag);
				return new double[] {v.getX(), v.getY(), v.getZ(), v.onGround() ? 1 : 0};
			});
			top = Math.max(top, now[1]);
			if (i > 2 && now[3] > 0 && dist == 0.0) {
				dist = Math.hypot(now[0] - s[0], now[2] - s[2]);
			}
		}
		sp.getServer().runCommand("kill @e[tag=" + tag + "]");
		if (out.length >= 2) {
			out[0] = top - s[1];
			out[1] = dist;
		}
		return top - s[1];
	}

	private static int APEX_N;

	private static Villager apexTarget(net.minecraft.server.MinecraftServer server, String tag) {
		return server.overworld().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(-10, -100, -10, 20, 400, 20),
				v -> v.entityTags().contains(tag)).getFirst();
	}

	private static void home(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			var p = server.getPlayerList().getPlayers().getFirst();
			int y = p.level().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
			p.teleportTo(p.level(), 0.5, y, 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
			p.setDeltaMovement(Vec3.ZERO);
		});
		ctx.waitTicks(20);
	}

	private static Vec3 pos(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.position());
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void check(double at20, double at60, double tolerance, String what) {
		if (at20 < 1.0E-3) {
			throw new AssertionError(what + ": 기준값이 0");
		}
		double diff = Math.abs(at60 - at20) / Math.max(1.0E-6, Math.abs(at20));
		if (diff > tolerance) {
			throw new AssertionError(String.format(Locale.ROOT, "%s: 기준 %.3f / 60틱 %.3f (차이 %.1f%%, 허용 %.0f%%)",
					what, at20, at60, diff * 100.0, tolerance * 100.0));
		}
	}

	private static void log(Result r) {
		System.out.println(String.format(Locale.ROOT, "[tickrate] 60틱  걷기 %.2f칸/초  점프 %.3f칸  주민 20칸 낙하 %.3f초  기절 %.3f초  에어본 %.3f칸  넉백 %.3f칸 · 높이 %.3f칸",
				r.walkSpeed, r.jumpHeight, r.mobFall, r.stunSeconds, r.airHeight, r.knockDistance, r.knockHeight));
	}
}
