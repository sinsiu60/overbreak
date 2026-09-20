package kr.overbreak.classes.warrior;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.math.Transformation;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
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
import org.jspecify.annotations.Nullable;

/**
 * [패시브] 피의 갈망 — 데이터팩 skill/bleed/* 대응.
 *
 *   때릴 때마다 출혈 1스택, 8초(160틱) 추가 타격 없으면 소멸
 *   스택 수만큼 거미 눈이 대상 주위를 균등 간격(360°÷스택, 반경 0.9, 높이 +1.0)으로 회전
 *   5스택 폭발 → 대상 50 피해(시전자 배율) · 시전자 체력 50 회복 · 살육 쿨 2초 감소
 */
public final class Bleed implements Effects.Active {
	public static final int MAX_STACKS = 5;
	static final int DURATION = 160;
	private static final int ORB_SPIN = 6;
	private static final float ORB_SCALE = 0.45F;

	private static final Map<LivingEntity, Bleed> ACTIVE = new IdentityHashMap<>();

	private final LivingEntity target;
	private @Nullable ServerPlayer source;
	private int stacks;
	private int timer;
	private double spin;
	private final List<Display.ItemDisplay> orbs = new ArrayList<>();

	private Bleed(LivingEntity target) {
		this.target = target;
	}

	/** 출혈 1스택. 5스택에 도달하면 그 자리에서 터집니다. */
	public static void add(LivingEntity target, @Nullable ServerPlayer source) {
		if (!target.isAlive()) {
			return;
		}
		Bleed b = ACTIVE.get(target);
		if (b == null) {
			b = new Bleed(target);
			ACTIVE.put(target, b);
			Effects.add(b);
		}
		b.source = source;
		b.stacks++;
		b.timer = Ticks.of(DURATION);
		ServerLevel level = (ServerLevel) target.level();
		Fx.sound(target, SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 0.4F, 1.8F);
		Fx.particle(level, ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1, target.getZ(), 3, 0.2, 0.3, 0.2, 0);
		if (b.stacks >= MAX_STACKS) {
			b.burst();
		} else {
			b.refreshOrbs();
		}
	}

	public static int stacks(LivingEntity target) {
		Bleed b = ACTIVE.get(target);
		return b == null ? 0 : b.stacks;
	}

	@Override
	public boolean tick() {
		if (stacks <= 0 || !target.isAlive() || target.isRemoved()) {
			end();
			return false;
		}
		if (--timer <= 0) {
			end();
			return false;
		}
		orbit();
		return true;
	}

	@Override
	public void cancel() {
		end();
	}

	private void burst() {
		stacks = 0;
		clearOrbs();
		ServerLevel level = (ServerLevel) target.level();
		Vec3 at = target.position().add(0, 1, 0);
		Fx.particle(level, ParticleTypes.DAMAGE_INDICATOR, at, 25, 0.4, 0.5, 0.4, 0.1);
		Fx.particle(level, ParticleTypes.CRIT, at, 30, 0.4, 0.5, 0.4, 0.3);
		Fx.particle(level, ParticleTypes.ENCHANTED_HIT, at, 15, 0.3, 0.4, 0.3, 0.2);
		Fx.sound(target, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2F, 0.5F);

		ServerPlayer src = source != null && source.isAlive() ? source : null;
		if (src != null) {
			reward(src);
		}
		SkillDamage.deal(target, src, 500, SkillDamage.Kind.NORMAL);
	}

	/** 폭발 보상: 체력 50 · 살육 쿨타임 2초 감소. */
	private static void reward(ServerPlayer p) {
		p.heal(50.0F);
		kr.overbreak.net.HealPayload.send(p, kr.overbreak.net.HealPayload.PULSE);
		PlayerProfile prof = Attachments.profile(p);
		prof.setCooldownTicks(Warrior.SLAY, prof.cooldown(Warrior.SLAY) - kr.overbreak.core.tick.Ticks.of(40));
		Fx.sound(p, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7F, 1.5F);
		prof.msgT = 40;
		Hud.actionbar(p, Component.empty()
				.append(Hud.bold("출혈 폭발!  ", ChatFormatting.DARK_RED))
				.append(Hud.text("체력 +50", ChatFormatting.RED))
				.append(Hud.text("  살육 -2초", ChatFormatting.GRAY)));
	}

	private void end() {
		stacks = 0;
		clearOrbs();
		ACTIVE.remove(target);
	}

	private void refreshOrbs() {
		clearOrbs();
		ServerLevel level = (ServerLevel) target.level();
		for (int i = 0; i < stacks; i++) {
			Transformation t = Displays.transform(0, 0, 0, ORB_SCALE, ORB_SCALE, ORB_SCALE, null, null);
			Display.ItemDisplay d = Displays.item(level, target.position(), 0, 0, new ItemStack(Items.SPIDER_EYE), t, 2);
			if (d != null) {
				d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
				orbs.add(d);
			}
		}
		orbit();
	}

	private void orbit() {
		if (orbs.isEmpty()) {
			return;
		}
		spin = (spin + Ticks.speed((double) ORB_SPIN)) % 360;
		int step = 360 / orbs.size();
		for (int i = 0; i < orbs.size(); i++) {
			float ang = (float) ((spin + step * i) % 360);
			Vec3 pos = Local.flat(target.position(), ang, 0, 1.0, 0.9);
			Displays.move(orbs.get(i), pos, 0, 0);
		}
	}

	private void clearOrbs() {
		orbs.forEach(o -> o.discard());
		orbs.clear();
	}

	/** 사망·초기화 때 대상에게 걸린 출혈을 지웁니다. */
	public static void clear(LivingEntity target) {
		Bleed b = ACTIVE.get(target);
		if (b != null) {
			b.end();
		}
	}
}
