package kr.overbreak.classes.warrior;

import kr.overbreak.core.tick.Ticks;
import com.mojang.math.Transformation;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * [우클릭] 살육 — 데이터팩 skill/slay/* 대응.
 *
 *   0.7초(14틱) 정신집중 → 주위 4.5칸 80 피해 + 출혈 2 + 적중 1명당 체력 35 회복
 *   기 모으는 동안 바닥 반투명 붉은 원판(모델)이 시계방향으로 채워지고, 도끼가 시야 앞에서 점점 빠르게 자전
 *   발동 후 8틱 동안 도끼가 주위를 틱당 72도로 휘돎
 *   쿨타임 5초 (시전 시작부터) · 기절당하면 끊김
 */
final class Slay implements Effects.Active {
	static final int CHARGE = 14;
	static final int END = 22;
	static final double RADIUS = 4.5;
	private static final int RING_COLOR = Fx.rgb(0.90, 0.05, 0.05);

	/** 원의 36개 점(10도 간격)이 각각 켜지는 틱. 데이터팩 range_ring 의 문턱을 그대로 옮겼습니다. */
	private static final int[] RING_THRESHOLD = {
			1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 6, 7, 7, 7,
			8, 8, 8, 9, 9, 10, 10, 10, 11, 11, 11, 12, 12, 12, 13, 13, 13};

	/** 옆으로 눕힌 도끼: X축 +90도 후 Z축 -45도 (스프라이트 대각선 보정). */
	private static final Quaternionf AXE_LEFT = new Quaternionf(0.70711F, 0F, 0F, 0.70711F);
	private static final Quaternionf AXE_RIGHT = new Quaternionf(0F, 0F, -0.38268F, 0.92388F);

	private final ServerPlayer caster;
	private final WarriorState state;
	private int t;
	private double anim;
	private Display.@Nullable ItemDisplay axe;
	/** 판정 범위 — 바닥 원판 모델 (기를 모으는 동안 시계방향으로 채워짐). */
	private @Nullable GroundShape range;

	Slay(ServerPlayer caster, WarriorState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, WarriorState st) {
		Attachments.profile(p).setCooldown(Warrior.SLAY, 100);
		Attachments.combatant(p).casting = true;
		Slay s = new Slay(p, st);
		st.slay = s;
		ServerLevel level = (ServerLevel) p.level();
		s.axe = Displays.item(level, p.position().add(0, 1, 0), 0, 0, new ItemStack(Items.IRON_AXE),
				Displays.transform(0, 0, 0, 0.9F, 0.9F, 0.9F, AXE_LEFT, AXE_RIGHT), 1);
		// 모드 클라이언트: 손에 든 도끼로 1인칭·3인칭 연출, 디스플레이 도끼는 숨김
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SLAY, s.axe != null ? s.axe.getId() : -1);
		s.range = GroundShape.sector(level, p.position(), p.getYRot(), 360.0, RADIUS, 0x40FF2A2A, 0xD8FF3B3B);
		s.range.reveal(0.0);
		Fx.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 1.6F);
		Fx.sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		Attachments.profile(p).msgT = 40;
		Hud.actionbar(p, Component.empty()
				.append(Hud.bold("살육", ChatFormatting.RED))
				.append(Hud.text("  기를 모으는 중...", ChatFormatting.GOLD)));
		Effects.add(s);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			stopAnim();
			cleanup();
			return false;
		}
		Combatant c = Attachments.combatant(caster);
		int charge = Ticks.of(CHARGE);
		if ((c.stunT > 0 || c.knockT > 0 || c.breakT > 0) && t < charge) {
			interrupt();
			return false;
		}
		t++;
		ServerLevel level = (ServerLevel) caster.level();

		double time = Ticks.time(t);
		if (t < charge) {
			// 점점 빨라지는 회전: 20틱에서 틱마다 3t 도씩 더하던 합 (1.5t² + 1.5t) 을 시간으로
			anim = (1.5 * time * time + 1.5 * time) % 360;
			if (t == 1 && axe != null) {
				// 소환과 같은 틱에 끝 모습을 주면 보간이 안 되므로 한 틱 뒤에 겁니다.
				Displays.animate(axe, Displays.transform(0, 0, 0, 1.5F, 1.5F, 1.5F, AXE_LEFT, AXE_RIGHT), CHARGE);
			}
			if (range != null) {
				range.moveTo(caster.position());
				range.reveal(t / (double) charge);
			}
			if (Ticks.ambient()) {
				Fx.particle(level, ParticleTypes.CRIT, caster.getX(), caster.getY() + 1, caster.getZ(), 3, 0.4, 0.5, 0.4, 0.05);
				Fx.particle(level, ParticleTypes.ENCHANTED_HIT, caster.getX(), caster.getY() + 1, caster.getZ(), 2, 0.35, 0.45, 0.35, 0.02);
			}
			if (t == Ticks.of(7)) {
				Fx.sound(caster, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.1F, 0.6F);
			}
			if (axe != null) {
				Displays.move(axe, Local.fromEyes(caster, 0.6, -0.35, 1.35), (float) anim, 0);
			}
		} else {
			anim = (anim + Ticks.speed(72.0)) % 360;
			if (t == charge) {
				impact(level);
			}
			if (axe != null) {
				Vec3 pos = Local.flat(caster.position(), (float) anim, 0, 1.1, 1.9);
				Displays.move(axe, pos, (float) anim, 0);
			}
			if (Ticks.ambient()) {
				Fx.particleExcept(level, firstPersonCaster(), ParticleTypes.SWEEP_ATTACK, caster.getX(), caster.getY() + 1, caster.getZ(), 2, 0.7, 0.2, 0.7, 0);
				Fx.particle(level, ParticleTypes.CRIT, caster.getX(), caster.getY() + 1, caster.getZ(), 6, 0.9, 0.3, 0.9, 0.15);
			}
		}

		if (t >= Ticks.of(END)) {
			cleanup();
			return false;
		}
		return true;
	}

	private void impact(ServerLevel level) {
		if (range != null) {
			range.moveTo(caster.position());
			range.reveal(1.0);
			range.setColors(0x70FF3030, 0xFFFF6060);
			GroundShape.fadeOut(range, 8, null);
			range = null;
		}
		Attachments.combatant(caster).casting = false;
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 1.1F);
		Fx.sound(caster, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.1F, 0.7F);
		Fx.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.5F);
		Fx.sound(caster, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.6F);
		Vec3 at = caster.position().add(0, 1, 0);
		Fx.particleExcept(level, firstPersonCaster(), ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 12, 1.4, 0.3, 1.4, 0);
		Fx.particle(level, ParticleTypes.CRIT, at, 60, 1.2, 0.4, 1.2, 0.4);
		Fx.particle(level, ParticleTypes.ENCHANTED_HIT, at, 30, 1.0, 0.4, 1.0, 0.3);
		if (axe != null) {
			Displays.animate(axe, Displays.transform(0, 0, 0, 2.6F, 2.6F, 2.6F, AXE_LEFT, AXE_RIGHT), 2);
		}

		int healN = 0;
		for (LivingEntity e : Targets.enemies(level, caster.position(), RADIUS, caster)) {
			Fx.particle(level, ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 2, 0.2, 0.2, 0.2, 0);
			SkillDamage.deal(e, caster, 800, SkillDamage.Kind.NORMAL);
			if (e.isAlive()) {
				Bleed.add(e, caster);
				Bleed.add(e, caster);
			}
			healN += 35;
			Warrior.onSlayHit(caster);
		}
		if (healN > 0) {
			caster.heal(healN);
			PlayerProfile prof = Attachments.profile(caster);
			prof.msgT = 40;
			Hud.actionbar(caster, Component.empty()
					.append(Hud.bold("살육 명중!", ChatFormatting.RED))
					.append(Hud.text("   체력 +", ChatFormatting.GREEN))
					.append(Hud.bold(String.valueOf(healN), ChatFormatting.GREEN)));
		}
	}

	/** 클라이언트 모드가 있으면 1인칭 도끼 애니메이션이 대신하므로, 몸에 붙는 휩쓸기 파티클은 시전자 화면에서 뺍니다. */
	private @Nullable ServerPlayer firstPersonCaster() {
		return SkillAnimPayload.canSend(caster) ? caster : null;
	}

	void interrupt() {
		stopAnim();
		cleanup();
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
		if (caster.level() instanceof ServerLevel level) {
			Fx.particle(level, ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1, caster.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
		}
		Attachments.profile(caster).msgT = 40;
		Hud.actionbar(caster, Hud.text("살육 시전이 끊겼다", ChatFormatting.GRAY));
	}

	@Override
	public void cancel() {
		stopAnim();
		cleanup();
	}

	@Override
	public Object owner() {
		return caster;
	}

	/** 끝까지 가지 못하고 끝날 때만 보냅니다. 정상 종료는 클라이언트가 같은 시간에 스스로 마칩니다. */
	private void stopAnim() {
		if (t < END && !caster.hasDisconnected()) {
			SkillAnimPayload.stop(caster, SkillAnimPayload.SLAY);
		}
	}

	private void cleanup() {
		if (range != null) {
			range.discard();
			range = null;
		}
		Attachments.combatant(caster).casting = false;
		if (axe != null) {
			axe.discard();
			axe = null;
		}
		if (state.slay == this) {
			state.slay = null;
		}
	}
}
