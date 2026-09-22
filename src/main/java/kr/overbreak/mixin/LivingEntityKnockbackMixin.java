package kr.overbreak.mixin;

import kr.overbreak.core.tick.MovementScale;
import kr.overbreak.core.tick.TickRateConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 넉백 보정 — 바닐라 넉백은 "틱당 속도" 를 그대로 넣어서 60틱에서는 3배 멀리 · 높이 날아갑니다 (맞고 떠올라 낙하 피해까지).
 * 바닐라가 새로 더한 몫(이전 속도 절반은 그대로)을 20틱과 같은 궤적이 되게 바꿉니다.
 *
 *   수평: 공중에서는 공기 저항 합이 같게 (v / 0.09 = v' / (1 - 공기)). 땅에서 맞으면 20틱은 첫 틱 내내 땅 마찰을 받아 덜 날아가므로
 *         60틱에서 잰 거리로 맞춘 몫을 뺍니다.
 *   수직: 땅에 선 생명체는 20틱에서 세로 속도가 -0.0784 로 쉬고 있어 넉백 높이가 그만큼 깎입니다. 그 값으로 20틱 속도를 구한 뒤
 *         MovementScale 의 점프 배율(중력 보정과 짝)을 곱합니다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {
	/** 20틱 땅 위에서 쉬는 세로 속도 (중력 0.08 x 공기 0.98). */
	@Unique
	private static final double REST_Y_20 = -0.08 * 0.98;

	/** 60틱에서 땅에서 맞았을 때 공중 비율에서 뺄 몫 (TickRateClientTest 로 20틱 넉백 거리와 맞춤). */
	@Unique
	private static final double GROUND_LOSS_60 = 0.132;

	@Unique
	private Vec3 overbreak$before;
	@Unique
	private boolean overbreak$grounded;

	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"))
	private void overbreak$knockbackHead(double strength, double x, double z, DamageSource source, float f, boolean b, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		overbreak$before = self.getDeltaMovement();
		overbreak$grounded = self.onGround();
	}

	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("RETURN"))
	private void overbreak$knockbackReturn(double strength, double x, double z, DamageSource source, float f, boolean b, CallbackInfo ci) {
		Vec3 old = overbreak$before;
		overbreak$before = null;
		// 넉백을 받은 플레이어 — 0.3초 동안 공중 제어를 끔 (클라이언트)
		if ((Object) this instanceof net.minecraft.server.level.ServerPlayer sp) {
			kr.overbreak.net.AirLockPayload.send(sp);
		}
		double k = TickRateConfig.scale();
		if (old == null || Math.abs(k - 1.0) < 1.0E-6) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		Vec3 now = self.getDeltaMovement();
		if (now.equals(old)) {
			return;
		}
		MovementScale.Factors m = MovementScale.factors(k);
		double air = 1.0 - (1.0 - 0.91) * m.airDrag();
		double horizontal;
		// 공중: 20틱 거리 v / 0.09 = 새 거리 v' / (1 - 공기)
		horizontal = (1.0 - air) / (1.0 - 0.91);
		if (overbreak$grounded) {
			// 땅에서 맞으면 20틱은 첫 틱 내내 땅 마찰을 받아 덜 날아감 — 60틱에서 잰 거리 차이(13%)로 맞춘 값을 틱레이트 사이에 선형으로
			horizontal *= 1.0 - GROUND_LOSS_60 * (1.0 - 1.0 / k) / (1.0 - 1.0 / 3.0);
		}
		double hx = old.x / 2.0;
		double hz = old.z / 2.0;
		double y = now.y;
		if (now.y != old.y) {
			double push = strength * (1.0 - self.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			double y20 = Math.min(0.4, (overbreak$grounded ? REST_Y_20 : old.y / m.jump()) / 2.0 + push);
			y = y20 * m.jump();
		}
		self.setDeltaMovement(hx + (now.x - hx) * horizontal, y, hz + (now.z - hz) * horizontal);
	}
}
