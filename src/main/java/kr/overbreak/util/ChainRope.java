package kr.overbreak.util;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Transformation;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.Vec3;

/**
 * 실제 사슬 모델 한 줄 — 두 점 사이를 0.5칸 간격 사슬 블록 디스플레이로 잇습니다 (피의 사슬과 같은 모양).
 *
 * 필요한 만큼만 링크를 늘리고, 거리가 줄면 남는 링크는 크기 0 으로 숨깁니다.
 * 링크는 앞(+Z)이 이어지는 방향을 보도록 회전합니다.
 */
public final class ChainRope {
	private static final double STEP = 0.5;
	private static final Transformation SHOWN = Displays.transform(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.56F, null, null);
	private static final Transformation HIDDEN = Displays.transform(-0.5F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F, null, null);

	private final ServerLevel level;
	private final int maxLinks;
	private final List<Display.BlockDisplay> links = new ArrayList<>();
	private final List<Boolean> shown = new ArrayList<>();

	public ChainRope(ServerLevel level, int maxLinks) {
		this.level = level;
		this.maxLinks = maxLinks;
	}

	public void draw(Vec3 from, Vec3 to) {
		Vec3 dir = to.subtract(from);
		double len = dir.length();
		int needed = len < 0.3 ? 0 : Math.min(maxLinks, (int) Math.ceil((len - 0.2) / STEP));
		while (links.size() < needed) {
			Display.BlockDisplay d = Displays.block(level, from, 0.0F, 0.0F,
					Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), HIDDEN, 1);
			if (d == null) {
				break;
			}
			links.add(d);
			shown.add(false);
		}
		float[] yp = Local.yawPitch(dir);
		for (int i = 0; i < links.size(); i++) {
			Display.BlockDisplay link = links.get(i);
			boolean show = i < needed;
			if (show) {
				Displays.move(link, from.add(dir.scale(i * STEP / len)), yp[0], yp[1]);
			}
			if (show != shown.get(i)) {
				link.setTransformation(show ? SHOWN : HIDDEN);
				shown.set(i, show);
			}
		}
	}

	public void discard() {
		links.forEach(Display.BlockDisplay::discard);
		links.clear();
		shown.clear();
	}
}
