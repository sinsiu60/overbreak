package kr.overbreak.classes.gunslinger;

import java.util.List;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [우클릭] 반동 도약 — 조준한 곳에 충격탄을 쏘고 그 반동으로 정반대로 날아갑니다.
 *
 *   충격탄은 조준선을 따라 최대 5칸 — 벽에 닿으면 거기서, 아니면 5칸 앞에서 터집니다
 *   터진 자리 반경 3칸에 25 피해 (넉백 있음)
 *   시전자는 조준 방향의 정반대로 튕겨 나갑니다 — 바닥을 보고 쏘면 7칸쯤 솟구칩니다
 *   균열 지대 위에서는 막힙니다 (이동기). 쿨타임 6초 — 차원 회전 포격 중에는 쿨타임 없이 계속 씁니다
 */
public final class RecoilBoost {
	/** 쿨타임 (시간 단위 · 6초). */
	public static final int COOLDOWN = 120;
	/** 충격탄이 날아가는 최대 거리 (칸). */
	static final double REACH = 5.0;
	static final double RADIUS = 3.0;
	/** 폭발 피해 x100 (25.0). */
	static final int DAMAGE_100 = 2500;
	/** 튕겨 나가는 세기 (시간 단위당 칸) — 수직으로 쏘면 약 7칸. */
	static final double POWER = 1.05;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private RecoilBoost() {}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.reloadT > 0 || st.acrobatics != null) {
			DualPistols.denied(p);
			return;
		}
		boolean free = st.inUlt();
		if (Attachments.combatant(p).sealT > 0 && !free) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (!free && Cooldowns.blocked(p, Gunslinger.BOOST, "반동 도약", ChatFormatting.AQUA)) {
			return;
		}
		if (!free) {
			Attachments.profile(p).setCooldown(Gunslinger.BOOST, COOLDOWN);
		}
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		Vec3 point = blastPoint(p, eye, dir);
		blast(p, level, point);
		// 반동: 조준 방향의 정반대. 땅을 딛고 있으면 최소 상승분이 붙어 확실히 뜹니다
		Motion.launch(p, dir.scale(-1.0), POWER);
		p.resetFallDistance();
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_BOOST, -1);
	}

	/** 조준선을 따라 벽까지 — 없으면 {@link #REACH} 칸 앞. */
	static Vec3 blastPoint(ServerPlayer p, Vec3 eye, Vec3 dir) {
		Vec3 far = eye.add(dir.scale(REACH));
		HitResult clip = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return clip.getType() == HitResult.Type.MISS ? far : clip.getLocation().subtract(dir.scale(0.2));
	}

	private static void blast(ServerPlayer p, ServerLevel level, Vec3 at) {
		List<LivingEntity> list = Targets.enemies(level, at, RADIUS, p);
		for (LivingEntity e : list) {
			SkillDamage.dealFine(e, p, DAMAGE_100, SkillDamage.Kind.NORMAL);
		}
		Fx.particle(level, ParticleTypes.EXPLOSION, at, 1, 0, 0, 0, 0);
		Fx.particle(level, ParticleTypes.CLOUD, at, 30, 0.6, 0.6, 0.6, 0.12);
		Fx.particle(level, Fx.dust(SKY, 1.5F), at, 26, 0.7, 0.7, 0.7, 0);
		Fx.particle(level, ParticleTypes.END_ROD, at, 14, 0.3, 0.3, 0.3, 0.14);
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 1.5F);
		Fx.sound(p, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}
}
