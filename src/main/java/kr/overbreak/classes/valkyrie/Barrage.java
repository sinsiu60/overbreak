package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * [Q] 탄막 포격 — 데이터팩 ult/barrage/* 대응 (체력 10배 기준).
 *
 *   기 모으기 0.7초(14틱) — 정신집중이 아님: 움직일 수 있고 맞아도 끊기지 않음. 소리와 빛으로 크게 예고
 *   포격: 2틱마다 한 발씩 40발 (4초). 기본 공격과 같은 12칸 히트스캔, 발당 8.5 (머리 치명타 17), 전탄 340
 *   지속 동안: 저지불가 (시작할 때 걸려 있던 기절 · 에어본 · 둔화도 풀림, 무적은 아님) · 이동속도 -40%
 *     쓸 수 있는 것은 전술 로켓뿐 (기본 공격 · 차원 도약 · 과열 분사 잠김)
 *   남은 시간은 조준점 아래 금색 게이지 (기 모으기 + 포격 94틱을 한 줄로)
 */
final class Barrage implements Effects.Active {
	static final int WIND = 14;
	static final int SHOTS = 40;
	static final int INTERVAL = 2;
	static final int TOTAL = WIND + SHOTS * INTERVAL;
	static final int DAMAGE_100 = 850;
	private static final Identifier SLOW = Overbreak.id("vk_ult");
	private static final int GOLD = Fx.rgb(1.00, 0.85, 0.35);

	private final ServerPlayer caster;
	private final ValkyrieState state;
	private int wind = Ticks.of(WIND);
	private int shots = SHOTS;
	private int left = Ticks.of(TOTAL);
	private int t;

	private Barrage(ServerPlayer caster, ValkyrieState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, ValkyrieState st) {
		UltGauge.consume(p);
		Rifle.interrupt(p, st);
		Barrage b = new Barrage(p, st);
		st.barrage = b;
		st.holdT = 0;
		Combatant c = Attachments.combatant(p);
		c.ccImmune = true;
		CrowdControl.cleanse(p);
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SLOW, -0.40, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_BARRAGE, -1);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.3F, 0.8F);
		Fx.sound(p, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.9F, 1.6F);
		Hud.title(p, Hud.bold("탄막 포격", ChatFormatting.YELLOW), Component.empty(), 0, 20, 8);
		Effects.add(b);
	}

	/** 남은 시간 0~100 (HUD). */
	int remainingPercent() {
		return Math.max(0, left * 100 / Ticks.of(TOTAL));
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		left--;
		ServerLevel level = caster.level();
		if (!InputModePayload.canSend(caster)) {
			Attachments.profile(caster).msgT = 2;
			Hud.actionbar(caster, Component.empty()
					.append(Hud.bold(wind > 0 ? "기 모으는 중  " : "탄막 포격  ", wind > 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW))
					.append(Hud.bold(Hud.bar10(left * 10 / Ticks.of(TOTAL)), ChatFormatting.GOLD))
					.append(Hud.text("  저지불가", ChatFormatting.RED)));
		}
		if (wind > 0) {
			wind--;
			if (Ticks.ambient()) {
				Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY() + 1, caster.getZ(), 6, 0.45, 0.7, 0.45, 0.02);
				Fx.particle(level, Fx.dust(GOLD, 1.0F), caster.getX(), caster.getY() + 1.1, caster.getZ(), 4, 0.4, 0.5, 0.4, 0);
			}
			if (wind == Ticks.of(7)) {
				Fx.sound(caster, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.1F, 1.4F);
			}
			if (wind == 0) {
				Fx.sound(caster, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.2F, 1.2F);
				Fx.particle(level, ParticleTypes.END_ROD, caster.getX(), caster.getY() + 1, caster.getZ(), 25, 0.5, 0.6, 0.5, 0.15);
			}
			return true;
		}
		if (--t > 0) {
			return true;
		}
		t = Ticks.of(INTERVAL);
		shots--;
		Rifle.shoot(caster, 0.0, DAMAGE_100);
		Fx.sound(caster, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.7F);
		if (shots <= 0) {
			cleanup();
			return false;
		}
		return true;
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void cleanup() {
		Attachments.combatant(caster).ccImmune = false;
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW);
		// 궁극기가 끝난 자리에서는 정확도부터 다시
		state.holdT = 0;
		SkillAnimPayload.stop(caster, SkillAnimPayload.VK_BARRAGE);
		Fx.sound(caster, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		if (state.barrage == this) {
			state.barrage = null;
		}
	}
}
