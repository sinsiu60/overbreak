package kr.overbreak.classes.hammer;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * [패시브] 뇌진탕 — 데이터팩 class/hammer_knight/passive/* 대응.
 *
 *   기절 · 넘어뜨림 상태의 적을 때리면 CC 0.5초 연장 + 추가 피해 30 (무적 시간 무시)
 *   너프: 한 번의 기절(넘어뜨림)에 딱 한 번만. 새로 기절해야 다시 발동합니다
 *        (Combatant.ccId — 풀려 있던 상태에서 새로 걸릴 때만 번호가 바뀝니다. 연장 · 덧씌우기는 같은 번호)
 *
 * 발동 경로: 평타 적중 · 돌진 충격 적중 · 대지 진동파 적중
 */
final class Concussion {
	static final int EXTEND = 10;
	static final int DAMAGE = 300;
	private static final int GOLD = Fx.rgb(1.00, 0.88, 0.15);

	private Concussion() {}

	/** @return 발동했으면 true */
	static boolean apply(@Nullable ServerPlayer source, LivingEntity target) {
		if (target == source || !target.isAlive()) {
			return false;
		}
		if (target instanceof ServerPlayer tp && (tp.isCreative() || tp.isSpectator())) {
			return false;
		}
		Combatant c = Attachments.combatant(target);
		if (c.stunT <= 0 && c.knockT <= 0) {
			return false;
		}
		if (c.concussedId == c.ccId) {
			return false;
		}
		c.concussedId = c.ccId;
		CrowdControl.extendHard(target, EXTEND);
		SkillDamage.deal(target, source, DAMAGE, SkillDamage.Kind.PIERCE);
		if (target.level() instanceof ServerLevel level) {
			Fx.sound(target, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 1.7F);
			Fx.particle(level, Fx.dust(GOLD, 1.5F), target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.4, 0.3, 0);
			Fx.particle(level, ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
		}
		return true;
	}
}
