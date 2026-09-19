package kr.overbreak.test.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * 실제 서버 맵 둘러보기 — 서버 월드 폴더(OVERBREAK_SERVER_WORLD)의 지형 파일을 시험 월드에 <b>복사</b>해서 열고
 * 전장 · 훈련장 · 튜토리얼 · 스폰을 위에서 찍습니다. 서버 파일은 읽기만 합니다.
 * 환경 변수가 없으면 건너뜁니다.
 */
public final class MapSurveyClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		String src = System.getenv("OVERBREAK_SERVER_WORLD");
		if (TestFilter.skip(getClass()) || src == null || src.isBlank()) {
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			save = sp.getWorldSave();
		}
		copyTerrain(Path.of(src), save.getSaveDirectory());
		try (TestSingleplayerContext sp = save.open()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runOnServer(server -> {
				net.minecraft.server.level.ServerLevel level = server.overworld();
				// 방 경계: 서 있는 높이 -60 · 머리 -59 에서 네 방향으로 막힌 곳까지
				for (int[] c : new int[][] {{56, -40}, {34, 20}, {112, -16}, {104, -16}}) {
					level.getChunkAt(new net.minecraft.core.BlockPos(c[0], -60, c[1]));
					StringBuilder sb = new StringBuilder("[survey] from " + c[0] + "," + c[1] + " floor=" + level.getBlockState(new net.minecraft.core.BlockPos(c[0], -61, c[1])).getBlock());
					int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] d : dirs) {
						int n = 0;
						while (n < 80) {
							net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(c[0] + d[0] * (n + 1), -59, c[1] + d[1] * (n + 1));
							level.getChunkAt(bp);
							if (!level.getBlockState(bp).isAir()) {
								sb.append(" dir(").append(d[0]).append(",").append(d[1]).append(")=").append(n + 1).append(":").append(level.getBlockState(bp).getBlock());
								break;
							}
							n++;
						}
						if (n >= 80) sb.append(" dir(").append(d[0]).append(",").append(d[1]).append(")=open");
					}
					// 천장
					int up = 0;
					while (up < 40 && level.getBlockState(new net.minecraft.core.BlockPos(c[0], -59 + up + 1, c[1])).isAir()) up++;
					sb.append(" ceil=+").append(up + 1);
					System.out.println(sb);
				}
				// 전장 장애물 지도 (y -59, x 12..56, z -4..44)
				for (int z = -6; z <= 46; z++) {
					StringBuilder row = new StringBuilder("[arena] ");
					for (int x = 10; x <= 58; x++) {
						net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(x, -59, z);
						level.getChunkAt(bp);
						row.append(level.getBlockState(bp).isAir() ? '.' : '#');
					}
					System.out.println(row + " z=" + z);
				}
				for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
					System.out.println("[entity] " + e.getType() + " " + e.position() + " tags=" + e.entityTags());
				}
			});
			look(ctx, sp, "map_spawn", 0.5, -40, 0.5, 90);
			look(ctx, sp, "map_arena_top", 34, -20, 20, 90);
			look(ctx, sp, "map_arena_low", 34, -52, -10, 30);
			look(ctx, sp, "map_training_top", 56.5, -35, -40.5, 90);
			look(ctx, sp, "map_training_low", 56.5, -56, -30.5, 25);
			look(ctx, sp, "map_tutorial_top", 112, -30, -16, 90);
			look(ctx, sp, "map_area_wide", 60, 30, 0, 90);
		}
	}

	private static void look(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, double x, double y, double z, float pitch) {
		float yaw = pitch >= 80 ? 0.0F : 0.0F;
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		ctx.waitTicks(30);
		sp.getConnection().waitForChunksRender();
		ctx.waitTicks(10);
		ctx.takeScreenshot(name);
	}

	/** 지형 · 엔티티 · poi 만 복사 (level.dat 등 설정은 시험 월드 것 그대로). */
	private static void copyTerrain(Path server, Path save) {
		for (String part : new String[] {"region", "entities", "poi"}) {
			Path from = server.resolve("dimensions/minecraft/overworld").resolve(part);
			Path to = save.resolve("dimensions/minecraft/overworld").resolve(part);
			if (!Files.isDirectory(from)) {
				continue;
			}
			try (Stream<Path> files = Files.list(from)) {
				Files.createDirectories(to);
				for (Path f : files.toList()) {
					Files.copy(f, to.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
