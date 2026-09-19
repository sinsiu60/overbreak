package kr.overbreak.game;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 서버 맵의 장소 — 서버 월드(newserver/world)를 블록 단위로 확인한 좌표입니다. 서 있는 높이는 모두 -60 (발밑 블록 -61).
 *
 *   로비          월드 스폰 (0.5, -60, 0.5). 메인 화면 뒤로는 전장 남쪽 벽 위에서 전장을 내려다보는 자리를 보여 줌
 *   훈련장        모래 방 · 안쪽 x 51~75 / z -52~-30 · 벽 프리즈머린 벽돌 · 더미 (56.5, -60, -40.5)
 *   1대1 전장     안쪽 x 12~56 / z -4~44 · 장애물 몇 개 · 양 끝 가운데에서 마주 보고 시작
 *   튜토리얼      모래 방 · 안쪽 x 102~121 / z -22~-10 · 울타리 x 107 · 표적 (116.5, -60, -15.5)
 */
public final class Places {
	public record Spot(double x, double y, double z, float yaw, float pitch) {
		public Vec3 pos() {
			return new Vec3(x, y, z);
		}

		public void teleport(ServerPlayer p) {
			ServerLevel level = p.level().getServer().overworld();
			p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
			p.setDeltaMovement(Vec3.ZERO);
			p.resetFallDistance();
		}
	}

	/** 월드 스폰. 모드가 없는 클라이언트는 메인 화면 대신 여기 섭니다. */
	public static final Spot LOBBY = new Spot(0.5, -60.0, 0.5, 0.0F, 0.0F);
	/** 메인 화면 배경 — 관전 모드로 전장 남쪽 벽 위에 떠서 전장을 내려다봄. */
	public static final Spot MENU_VIEW = new Spot(34.5, -30.0, -14.5, 0.0F, 35.0F);

	public static final Spot TRAINING_SPAWN = new Spot(63.5, -60.0, -40.5, 90.0F, 0.0F);
	public static final Vec3 TRAINING_DUMMY = new Vec3(56.5, -60.0, -40.5);
	public static final AABB TRAINING_ROOM = new AABB(51.0, -62.0, -52.0, 76.0, -19.0, -29.0);

	/** 1대1 전장 시작 자리 — 남쪽 끝(북쪽을 봄) · 북쪽 끝(남쪽을 봄). */
	public static final Spot DUEL_A = new Spot(34.5, -60.0, -1.5, 0.0F, 0.0F);
	public static final Spot DUEL_B = new Spot(34.5, -60.0, 41.5, 180.0F, 0.0F);
	public static final AABB DUEL_ARENA = new AABB(12.0, -62.0, -4.0, 57.0, -19.0, 45.0);
	/** 3대3 팀전 시작 자리 — 청팀은 남쪽 줄(북쪽을 봄), 홍팀은 북쪽 줄(남쪽을 봄). */
	public static final Spot[] TEAM_BLUE = {
			new Spot(28.5, -60.0, -1.5, 0.0F, 0.0F),
			new Spot(34.5, -60.0, -1.5, 0.0F, 0.0F),
			new Spot(40.5, -60.0, -1.5, 0.0F, 0.0F)};
	public static final Spot[] TEAM_RED = {
			new Spot(40.5, -60.0, 41.5, 180.0F, 0.0F),
			new Spot(34.5, -60.0, 41.5, 180.0F, 0.0F),
			new Spot(28.5, -60.0, 41.5, 180.0F, 0.0F)};

	/** 대난투 시작 자리 — 같은 전장의 다섯 곳 (남 · 북 · 서 · 동 · 가운데). */
	public static final Spot[] BRAWL_SPAWNS = {
			new Spot(34.5, -60.0, -1.5, 0.0F, 0.0F),
			new Spot(34.5, -60.0, 41.5, 180.0F, 0.0F),
			new Spot(14.5, -60.0, 20.5, -90.0F, 0.0F),
			new Spot(54.5, -60.0, 20.5, 90.0F, 0.0F),
			new Spot(34.5, -60.0, 20.5, 0.0F, 0.0F)};

	public static final Spot TUTORIAL_START = new Spot(104.5, -60.0, -15.5, -90.0F, 0.0F);
	public static final Spot TUTORIAL_FIELD = new Spot(111.5, -60.0, -15.5, -90.0F, 0.0F);
	public static final Vec3 TUTORIAL_DUMMY = new Vec3(116.5, -60.0, -15.5);
	/** 데이터팩과 같은 구역 (x 96..128 / y -72..-40 / z -34..2). */
	public static final AABB TUTORIAL_ZONE = new AABB(96.0, -72.0, -34.0, 129.0, -39.0, 3.0);
	/** 시작 칸 에너지 장벽 — x 107, z -22 ~ -9, y -60 부터 위로 {@link #FENCE_HEIGHT} 칸. */
	public static final int FENCE_X = 107;
	public static final int FENCE_Z0 = -22;
	public static final int FENCE_Z1 = -9;
	public static final int FENCE_Y = -60;
	public static final int FENCE_HEIGHT = 12;

	private Places() {}

	public static boolean inside(AABB box, ServerPlayer p) {
		return box.contains(p.position());
	}

	public static BlockPos fence(int z) {
		return new BlockPos(FENCE_X, FENCE_Y, z);
	}
}
