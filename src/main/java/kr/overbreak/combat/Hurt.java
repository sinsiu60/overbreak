package kr.overbreak.combat;

import kr.overbreak.net.HurtPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * 피격 피드백 — 맞은 사람에게 "어디서 맞았는지" 를 보냅니다 (오버워치식 방향 표시).
 *
 * 바닐라는 맞을 때마다 화면을 기울여 알려 주는데, 연사에 맞으면 화면이 계속 덜컹거려
 * 조준이 불가능해집니다. 그래서 기울기는 끄고 ({@code GameRendererBobMixin}) 방향 표시 · 붉은
 * 가장자리로 대신합니다 ({@code client/hud/DamageFeedback}).
 */
public final class Hurt {
	private Hurt() {}

	/**
	 * 맞은 사람에게 피격 표시를 보냅니다.
	 *
	 * @param hit 실제로 피해가 들어갔는가 (무적 시간에 씹혔으면 false)
	 */
	public static void feedback(LivingEntity target, @Nullable LivingEntity attacker, float amount, boolean hit) {
		if (!hit || amount <= 0.0F || !(target instanceof ServerPlayer p)) {
			return;
		}
		float angle = 0.0F;
		boolean directed = false;
		if (attacker != null && attacker != target) {
			double dx = attacker.getX() - p.getX();
			double dz = attacker.getZ() - p.getZ();
			if (dx * dx + dz * dz > 1.0E-4) {
				// 마인크래프트 yaw 규칙 (0 = +Z)
				angle = (float) Math.toDegrees(Math.atan2(-dx, dz));
				directed = true;
			}
		}
		HurtPayload.send(p, new HurtPayload(angle, amount, directed));
	}
}
