package kr.overbreak.util;

import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 디스플레이 엔티티 — 데이터팩 summon item_display / block_display 대응.
 *
 * 보간 규칙(데이터팩에서 실측한 그대로):
 *   - transformation 을 한 번만 바꾸고 보간 시간을 주면 거기까지를 클라이언트가 그립니다
 *   - 매 틱 다시 쓰면 보간이 처음으로 되돌아가므로 바뀐 때만 씁니다
 *   - 소환과 같은 틱에 끝 모습을 주면 기본값에서 보간해 버리므로, 소환은 시작 모습으로 하고 다음 틱에 갱신합니다
 */
public final class Displays {
	private static final Quaternionf NO_ROT = new Quaternionf();

	private Displays() {}

	public static Transformation transform(float tx, float ty, float tz, float sx, float sy, float sz,
										   @Nullable Quaternionf left, @Nullable Quaternionf right) {
		return new Transformation(new Vector3f(tx, ty, tz), left == null ? NO_ROT : left,
				new Vector3f(sx, sy, sz), right == null ? NO_ROT : right);
	}

	public static Display.@Nullable ItemDisplay item(ServerLevel level, Vec3 at, float yaw, float pitch, ItemStack stack,
													 Transformation t, int teleportDuration) {
		Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
		if (d == null) {
			return null;
		}
		d.setItemStack(stack);
		common(d, at, yaw, pitch, t, teleportDuration);
		level.addFreshEntity(d);
		return d;
	}

	public static Display.@Nullable BlockDisplay block(ServerLevel level, Vec3 at, float yaw, float pitch, BlockState state,
													   Transformation t, int teleportDuration) {
		Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
		if (d == null) {
			return null;
		}
		d.setBlockState(state);
		common(d, at, yaw, pitch, t, teleportDuration);
		level.addFreshEntity(d);
		return d;
	}

	/** 보간으로 모양을 바꿉니다. start_interpolation 0 = 바로 시작. */
	/** @param time 보간 시간 (1/20초 단위) — 클라이언트가 틱 단위로 보간하므로 지금 틱레이트의 틱으로 바꿔 보냄 */
	public static void animate(Display d, Transformation to, int time) {
		d.setTransformationInterpolationDuration(kr.overbreak.core.tick.Ticks.of(time));
		d.setTransformationInterpolationDelay(0);
		d.setTransformation(to);
	}

	/** 위치·회전 이동 (teleport_duration 이 있으면 클라이언트가 부드럽게 따라갑니다). */
	public static void move(Display d, Vec3 to, float yaw, float pitch) {
		d.snapTo(to.x, to.y, to.z, yaw, pitch);
	}

	private static void common(Display d, Vec3 at, float yaw, float pitch, Transformation t, int teleportDuration) {
		d.snapTo(at.x, at.y, at.z, yaw, pitch);
		d.setTransformation(t);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		if (teleportDuration > 0) {
			// 1 = 매 틱 옮기는 모델이 한 틱 동안 따라감 (틱레이트와 무관하게 1틱). 그보다 길면 시간 단위로
			d.setPosRotInterpolationDuration(teleportDuration <= 1 ? teleportDuration : kr.overbreak.core.tick.Ticks.of(teleportDuration));
		}
	}
}
