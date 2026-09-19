package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * [패시브] 최선의 방어는 — 데이터팩 class/ironfist/absorb · absorb_tick · absorb_gauge 대응 (체력 10배 기준).
 *
 *   스킬로 적을 맞힐 때마다 흡수 체력 +20 (여러 명이면 인원수만큼, 최대 120). 평타로는 얻지 못함
 *   2초간 못 얻으면 초당 20씩 감소. 맞아서 깎이면 실제 값을 따라 내려감
 *   흡수 10 당 궁극기 게이지 1% (올림 — 20/60/120 흡수 = 정확히 2/6/12%)
 */
public final class Absorb {
	public static final int PER_HIT = 20;
	public static final int MAX = 120;
	public static final int HOLD = 40;

	private Absorb() {}

	/** 스킬 적중 1명당 한 번. */
	public static void gain(ServerPlayer p) {
		IronFistState st = IronFist.stateOrNull(p);
		if (st == null) {
			return;
		}
		if (st.absorb >= MAX) {
			st.absorbHold = Ticks.of(HOLD);
			return;
		}
		int before = st.absorb;
		st.absorb = Math.min(MAX, st.absorb + PER_HIT);
		st.absorbHold = Ticks.of(HOLD);
		gauge(p, st.absorb - before);
		apply(p, st);
		Fx.sound(p, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
	}

	/** 가득 채움 (파멸의 일격 착탄). 게이지는 오르지 않습니다 (데이터팩과 같음). */
	static void fill(ServerPlayer p, IronFistState st) {
		st.absorb = MAX;
		st.absorbHold = Ticks.of(HOLD);
		apply(p, st);
	}

	/** 흡수 → 게이지. raw = 올림(흡수 x 100 / 비율). 게이지% = raw x 비율 / 1000. */
	private static void gauge(ServerPlayer p, int amount) {
		PlayerProfile prof = Attachments.profile(p);
		if (amount <= 0 || !prof.ultOn) {
			return;
		}
		int rate = prof.ultRate > 0 ? prof.ultRate : 200;
		UltGauge.addRaw(p, (amount * 100 + rate - 1) / rate);
	}

	static void tick(ServerPlayer p, IronFistState st) {
		int real = (int) Math.floor(p.getAbsorptionAmount() + 1.0E-3F);
		if (real < st.absorb) {
			st.absorb = real;
		}
		if (st.absorbHold > 0) {
			st.absorbHold--;
			st.absorbDecay = 0.0;
			return;
		}
		if (st.absorb <= 0) {
			return;
		}
		// 초당 20 = 시간 단위마다 1
		st.absorbDecay += Ticks.step();
		if (st.absorbDecay < 1.0 - 1.0E-9) {
			return;
		}
		st.absorbDecay -= 1.0;
		st.absorb--;
		apply(p, st);
	}

	static void apply(ServerPlayer p, IronFistState st) {
		p.setAbsorptionAmount(st.absorb);
	}
}
