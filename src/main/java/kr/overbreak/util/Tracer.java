package kr.overbreak.util;

import kr.overbreak.net.TracerPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 총알 궤적 — 데이터팩 util/beam 대응.
 *
 * 데이터팩은 블록 디스플레이를 길게 늘인 막대로 그렸지만, 모드에서는 엔티티를 만들지 않고
 * 근처 클라이언트에 궤적만 알려 줍니다. 클라이언트가 화면을 향한 2D 빛줄기 이미지로 짧게 그렸다 지웁니다.
 */
public final class Tracer {
	public static final int VALKYRIE = TracerPayload.VALKYRIE;
	public static final int IRONFIST = TracerPayload.IRONFIST;
	public static final int SHERIFF = TracerPayload.SHERIFF;
	public static final int LIGHTNING = TracerPayload.LIGHTNING;
	public static final int LIGHTNING_BIG = TracerPayload.LIGHTNING_BIG;
	public static final int GUNSLINGER = TracerPayload.GUNSLINGER;
	public static final int GUNSLINGER_L = TracerPayload.GUNSLINGER_L;

	private Tracer() {}

	public static void spawn(ServerLevel level, @Nullable Entity owner, Vec3 from, Vec3 to, int style) {
		if (from.distanceToSqr(to) < 0.05 * 0.05) {
			return;
		}
		TracerPayload.send(level, owner, from, to, style);
	}
}
