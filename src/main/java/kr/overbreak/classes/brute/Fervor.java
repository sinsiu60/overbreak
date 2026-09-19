package kr.overbreak.classes.brute;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * [패시브] 투기(鬪氣) — 때리거나 맞을 때마다 1스택. 싸움이 길어질수록 단단해지고 빨라집니다.
 *
 *   최대 {@value #MAX} 스택 · 마지막으로 쌓은 뒤 {@value #DURATION} (1/20초 단위 = 5초) 동안 유지
 *   스택당 받는 피해 -2% (최대 -20%) · 이동속도 +1% (최대 +10%)
 *   무쌍(궁극기) 중에는 최대치로 고정됩니다
 *
 * 스택은 조준점 아래 칸으로 보입니다 (HudExtra.stacks).
 */
final class Fervor {
	static final int MAX = 10;
	/** 스택이 풀리기까지 (1/20초 단위). */
	static final int DURATION = 100;
	/** 스택당 받는 피해 감소 (%). */
	static final int GUARD_PERCENT = 2;
	/** 스택당 이동속도 (%). */
	static final int SPEED_PERCENT = 1;

	private static final Identifier SPEED = Overbreak.id("brute_fervor_speed");
	private static final int EMBER = Fx.rgb(1.00, 0.55, 0.20);

	private Fervor() {}

	/** 받는 피해 감소를 한 번만 걸어 둡니다 (Brute 의 정적 초기화에서 부릅니다). */
	static void init() {
		DamageModifiers.register((target, source, damage) -> {
			if (!(target instanceof ServerPlayer p)) {
				return damage;
			}
			BruteState st = Brute.stateOrNull(p);
			if (st == null || st.fervor <= 0) {
				return damage;
			}
			return damage * (100 - Math.min(MAX, st.fervor) * GUARD_PERCENT) / 100.0F;
		});
	}

	/** 때리거나 맞았을 때 한 스택. */
	static void gain(ServerPlayer p, BruteState st, int n) {
		int before = st.fervor;
		st.fervor = Math.min(MAX, st.fervor + n);
		st.fervorT = Ticks.of(DURATION);
		if (st.fervor > before) {
			apply(p, st);
			if (st.fervor == MAX && before < MAX) {
				Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.7F);
				Fx.particleExcept(p.level(), kr.overbreak.net.SkillAnimPayload.canSend(p) ? p : null,
						Fx.dust(EMBER, 1.3F), p.getX(), p.getY() + 1.0, p.getZ(), 18, 0.4, 0.6, 0.4, 0);
			}
		}
	}

	/** 매 틱 — 시간이 지나면 스택이 한 번에 풀립니다. 무쌍 중에는 최대치로 고정. */
	static void tick(ServerPlayer p, BruteState st) {
		if (st.rampageT > 0) {
			st.fervor = MAX;
			st.fervorT = Ticks.of(DURATION);
			apply(p, st);
		} else if (st.fervor > 0 && --st.fervorT <= 0) {
			st.fervor = 0;
			apply(p, st);
		}
		if (st.fervor > 0 && Ticks.ambient() && p.level() instanceof ServerLevel level) {
			// 본인 화면에서는 발밑 불씨가 시야를 가리므로 뺍니다 (스택은 HUD 칸으로 봅니다)
			ServerPlayer self = kr.overbreak.net.SkillAnimPayload.canSend(p) ? p : null;
			int n = 1 + st.fervor / 4;
			Fx.particleExcept(level, self, Fx.dust(EMBER, 0.7F + st.fervor * 0.05F),
					p.getX(), p.getY() + 0.4, p.getZ(), n, 0.3, 0.5, 0.3, 0);
			if (st.fervor >= MAX) {
				Fx.particleExcept(level, self, ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0.3, 0.1, 0.3, 0.01);
			}
		}
	}

	/** 스택이 바뀌었을 때만 이동속도 보정을 다시 겁니다. */
	private static void apply(ServerPlayer p, BruteState st) {
		if (st.fervorApplied == st.fervor) {
			return;
		}
		st.fervorApplied = st.fervor;
		if (st.fervor <= 0) {
			CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, SPEED);
		} else {
			CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SPEED,
					st.fervor * SPEED_PERCENT / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	static void clear(ServerPlayer p, BruteState st) {
		st.fervor = 0;
		st.fervorT = 0;
		apply(p, st);
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, SPEED);
	}
}
