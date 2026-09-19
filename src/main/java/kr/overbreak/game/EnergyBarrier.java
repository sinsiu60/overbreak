package kr.overbreak.game;

import java.util.ArrayList;
import java.util.List;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 튜토리얼 시작 칸을 막는 에너지 장벽 — 실제로 막히고(보이지 않는 벽), 눈에는 푸른 빛의 장막으로 보입니다.
 *
 *   막기: barrier 블록 한 겹 (x 고정 · z {@link Places#FENCE_Z0}~{@link Places#FENCE_Z1} · 높이 {@link Places#FENCE_HEIGHT})
 *   보이기: 늘린 유리 판 두 겹(안쪽이 더 밝음) + 위아래 빛 레일 + 양 끝 기둥 + 위아래로 훑는 띠
 *   소리 · 입자: 낮은 웅웅거림과 푸른 불꽃 (1/20초마다)
 *   열릴 때: 훑는 띠가 번쩍하고 판이 위아래로 사라지며 전원이 꺼지는 소리
 */
final class EnergyBarrier {
	private static final String TAG = "overbreak.tut_barrier";
	private static final int GLOW = Fx.rgb(0.45, 0.85, 1.00);
	/** 장막의 두께 (칸). */
	private static final float THICK = 0.12F;

	private static final List<Display.BlockDisplay> PARTS = new ArrayList<>();
	private static Display.@org.jspecify.annotations.Nullable BlockDisplay scan;
	private static boolean up;
	private static boolean cleaned;
	private static long t;

	private EnergyBarrier() {}

	static boolean up() {
		return up;
	}

	private static double width() {
		return Places.FENCE_Z1 - Places.FENCE_Z0 + 1;
	}

	private static Vec3 corner() {
		// 판의 한쪽 아래 귀퉁이 (벽 한가운데 두께로 세움)
		return new Vec3(Places.FENCE_X + 0.5 - THICK / 2.0, Places.FENCE_Y, Places.FENCE_Z0);
	}

	private static net.minecraft.world.level.block.Block glass() {
		return Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE);
	}

	static void raise(ServerLevel level) {
		clearParts(level);
		for (int z = Places.FENCE_Z0; z <= Places.FENCE_Z1; z++) {
			for (int y = 0; y < Places.FENCE_HEIGHT; y++) {
				level.setBlockAndUpdate(new BlockPos(Places.FENCE_X, Places.FENCE_Y + y, z), Blocks.BARRIER.defaultBlockState());
			}
		}
		Vec3 at = corner();
		double w = width();
		float h = Places.FENCE_HEIGHT;
		float fw = (float) w;
		// 뼈대: 세로 빔 (1칸마다) · 가로 빔 (2칸마다) — 격자로 보이되 너머가 비칩니다
		for (int i = 0; i <= w; i++) {
			part(level, at.add(0, 0, i - 0.04), glass(), THICK, h, 0.08F, 0, 0);
		}
		for (int i = 0; i <= Places.FENCE_HEIGHT; i += 2) {
			part(level, at.add(0, i - 0.04, 0), glass(), THICK, 0.08F, fw, 0, 0);
		}
		// 위 · 아래 굵은 레일
		part(level, at.add(0, -0.12, 0), Blocks.SEA_LANTERN, THICK * 2.4F, 0.18F, fw, 0, 0);
		part(level, at.add(0, h - 0.06, 0), Blocks.SEA_LANTERN, THICK * 2.4F, 0.18F, fw, 0, 0);
		// 양 끝 기둥
		part(level, at.add(0, 0, -0.12), Blocks.SEA_LANTERN, THICK * 2.4F, h, 0.18F, 0, 0);
		part(level, at.add(0, 0, w - 0.06), Blocks.SEA_LANTERN, THICK * 2.4F, h, 0.18F, 0, 0);
		// 위아래로 훑는 띠 (이것만 장막처럼 채워진 면)
		scan = Displays.block(level, at, 0.0F, 0.0F, glass().defaultBlockState(),
				Displays.transform(0.0F, 0.0F, 0.0F, THICK * 1.6F, 0.45F, fw, null, null), 1);
		if (scan != null) {
			scan.addTag(TAG);
			PARTS.add(scan);
		}
		up = true;
		cleaned = true;
		t = 0;
		Vec3 mid = middle();
		Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/** 막던 블록만 치움. */
	private static void clearBlocks(ServerLevel level) {
		for (int z = Places.FENCE_Z0; z <= Places.FENCE_Z1; z++) {
			for (int y = 0; y < Places.FENCE_HEIGHT; y++) {
				BlockPos pos = new BlockPos(Places.FENCE_X, Places.FENCE_Y + y, z);
				if (level.getBlockState(pos).is(Blocks.BARRIER)) {
					level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** 전원이 꺼지듯 사라짐 — 블록은 바로 치우고, 장막은 접혔다가 지워집니다. */
	static void lower(ServerLevel level) {
		if (!up) {
			return;
		}
		up = false;
		clearBlocks(level);
		Vec3 mid = middle();
		double w = width();
		Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
		Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.7F);
		Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
		for (int i = 0; i < Places.FENCE_HEIGHT; i++) {
			Fx.particle(level, Fx.dust(GLOW, 2.0F), mid.x, Places.FENCE_Y + i + 0.5, mid.z, 10, 0.05, 0.2, w / 2.0, 0.02);
		}
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 60, 0.1, Places.FENCE_HEIGHT / 2.0, w / 2.0, 0.3);
		Fx.particle(level, ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 30, 0.1, Places.FENCE_HEIGHT / 2.0, w / 2.0, 0.08);
		// 가운데로 접히며 사라짐
		Vec3 at = corner();
		int h = Places.FENCE_HEIGHT;
		for (Display.BlockDisplay d : PARTS) {
			Displays.animate(d, Displays.transform(0.0F, h / 2.0F, 0.0F, THICK, 0.02F, (float) w, null, null), 6);
		}
		PARTS.forEach(d -> d.setPosRotInterpolationDuration(0));
		scan = null;
		schedule(level, at);
	}

	/** 접히는 연출이 끝나면 판을 치웁니다. */
	private static void schedule(ServerLevel level, Vec3 at) {
		kr.overbreak.skill.Effects.add(new kr.overbreak.skill.Effects.Active() {
			private int left = Ticks.of(8);

			@Override
			public boolean tick() {
				if (--left > 0) {
					return true;
				}
				clearParts(level);
				return false;
			}
		});
	}

	/** 1/20초마다 — 훑는 띠 · 불꽃 · 웅웅거림. */
	static void tick(ServerLevel level) {
		if (!up) {
			// 서버가 튜토리얼 도중에 꺼졌으면 막던 블록 · 장막이 남아 있을 수 있음 — 한 번만 치웁니다
			if (!cleaned) {
				cleaned = true;
				clearBlocks(level);
				clearParts(level);
			}
			return;
		}
		t++;
		double w = width();
		Vec3 at = corner();
		int h = Places.FENCE_HEIGHT;
		if (scan != null) {
			// 위아래로 천천히 오르내림
			double k = 0.5 - 0.5 * Math.cos(t * 0.06);
			Displays.move(scan, at.add(0.0, k * (h - 0.5), 0.0), 0.0F, 0.0F);
		}
		Vec3 mid = middle();
		// 장막 위의 푸른 불꽃
		Fx.particle(level, Fx.dust(GLOW, 1.2F), mid.x, mid.y, mid.z, 6, 0.02, h / 2.5, w / 2.5, 0.0);
		if (t % 4 == 0) {
			Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 3, 0.02, h / 2.5, w / 2.5, 0.02);
		}
		if (t % 40 == 0) {
			Fx.sound(level, mid.x, mid.y, mid.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}

	private static Vec3 middle() {
		return new Vec3(Places.FENCE_X + 0.5, Places.FENCE_Y + Places.FENCE_HEIGHT / 2.0,
				Places.FENCE_Z0 + width() / 2.0);
	}

	private static void part(ServerLevel level, Vec3 at, net.minecraft.world.level.block.Block block,
							 float sx, float sy, float sz, float ty, float tz) {
		Display.BlockDisplay d = Displays.block(level, at, 0.0F, 0.0F, block.defaultBlockState(),
				Displays.transform(0.0F, ty, tz, sx, sy, sz, null, null), 0);
		if (d != null) {
			d.addTag(TAG);
			PARTS.add(d);
		}
	}

	/** 남아 있는 장막 조각 정리 (서버를 껐다 켠 뒤에도). */
	private static void clearParts(ServerLevel level) {
		PARTS.forEach(Display.BlockDisplay::discard);
		PARTS.clear();
		scan = null;
		Vec3 mid = middle();
		AABB box = new AABB(mid, mid).inflate(Places.FENCE_HEIGHT + width());
		for (Display.BlockDisplay d : level.getEntitiesOfClass(Display.BlockDisplay.class, box, e -> e.entityTags().contains(TAG))) {
			d.discard();
		}
	}
}
