package kr.overbreak.classes.gunslinger;

import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * [패시브] 체공 훈풍 — 건슬링어를 공중에 붙잡아 두는 두 가지 이득.
 *
 *   활공   : 공중에서 점프 키를 누르고 있으면 떨어지는 속도가 확 줄어듭니다 (최대 2초).
 *            땅에 닿으면 다시 가득 찹니다. 느린 낙하로 걸어 클라이언트가 그대로 예측합니다 (끊김 없음).
 *   공중 명중: 공중에서 맞힌 총알은 무조건 치명타 150%, 그리고 맞힐 때마다
 *            반동 도약 · 사선 앵커의 남은 쿨타임이 0.5초씩 깎입니다.
 *
 * 낙하 피해는 늘 받지 않습니다 ({@link Gunslinger} 의 ALLOW_DAMAGE).
 */
public final class AeroDrift {
	/** 활공 지속 (시간 단위 · 2초). */
	public static final int GLIDE = 40;
	/** 상태 초기값 (틱). */
	public static final int GLIDE_TICKS_INIT = 40 * 3;
	/** 공중 명중 치명타 배율 (%). */
	public static final int CRIT_PERCENT = 150;
	/** 공중 명중 한 번에 깎이는 쿨타임 (시간 단위 · 0.5초). */
	public static final int REFUND = 10;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private AeroDrift() {}

	/** 지금 공중에 떠 있는가 — 치명타 · 반동 벡터 판정의 기준. */
	public static boolean airborne(ServerPlayer p) {
		return !p.onGround();
	}

	/** 공중에서 한 발 맞혔을 때 — 반동 도약 · 사선 앵커 쿨타임 환급. */
	public static void onAirHit(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		int back = Ticks.of(REFUND);
		refund(prof, Gunslinger.BOOST, back);
		refund(prof, Gunslinger.ANCHOR, back);
		Fx.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 1.9F);
	}

	private static void refund(PlayerProfile prof, String key, int ticks) {
		int left = prof.cooldown(key);
		if (left > 0) {
			prof.setCooldownTicks(key, Math.max(0, left - ticks));
		}
	}

	/** 직업 틱 — 활공 충전 · 유지 · 해제. */
	static void tick(ServerPlayer p, GunslingerState st) {
		boolean ground = p.onGround();
		if (ground && !st.wasGround) {
			// 착지: 활공을 다시 채웁니다
			st.glideT = Ticks.of(GLIDE);
		}
		st.wasGround = ground;
		if (ground) {
			st.glideT = Ticks.of(GLIDE);
			stop(p, st);
			return;
		}
		// 궁극기로 공중에 붙잡혀 있는 동안에는 활공이 끼어들지 않습니다
		// 웅크리기는 곡예 난사라, 활공은 공중에서 점프 키를 누르고 있을 때입니다
		boolean want = Attachments.profile(p).jumpDown && st.glideT > 0 && !st.inUlt();
		if (!want) {
			stop(p, st);
			return;
		}
		st.glideT--;
		if (!st.gliding) {
			st.gliding = true;
			SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_GLIDE, -1);
			Fx.sound(p, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.7F, 1.5F);
		}
		// 느린 낙하는 1초마다 다시 걸어 두면 충분합니다 (끊기면 바로 다시 떨어짐)
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Ticks.of(6), 0, false, false));
		p.resetFallDistance();
		if (Ticks.ambient()) {
			ServerLevel level = p.level();
			Fx.particle(level, Fx.dust(SKY, 0.8F), p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.45, 0.35, 0.45, 0);
			Fx.particle(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.05, 0.3, 0.01);
		}
	}

	static void stop(ServerPlayer p, GunslingerState st) {
		if (!st.gliding) {
			return;
		}
		st.gliding = false;
		p.removeEffect(MobEffects.SLOW_FALLING);
		SkillAnimPayload.stop(p, SkillAnimPayload.GS_GLIDE);
	}
}
