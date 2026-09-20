package kr.overbreak.classes.hammer;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [E] 중력 파쇄 — 데이터팩 skill/crush/* 대응.
 *
 *   선동작 0.5초(10틱): 망치를 들어 올렸다가 내려찍음. 정신집중이 아님 (이동 · 평타 자유, 기절당해도 그대로 발동)
 *     바닥 표시: 반경 4칸 금색 고정 테두리 + 4 → 0.4칸으로 조여드는 보라색 고리 (= 남은 시간)
 *   발동: 반경 4칸 적을 전부 시전자 발밑 1칸(원래 있던 방향)으로 끌어당김 + 피해 20 + 0.8초 70% 둔화
 *   균열 지대: 시전 자리 반경 3칸 · 4초. 위에 있는 적은 이동기 봉인 (시전자 본인 제외)
 *     바닥 표시: 반투명 보라 원판 + 테두리 + 중심에서 뻗은 금 6갈래
 *   쿨타임 11초
 */
final class Crush implements Effects.Active {
	static final int WINDUP = 10;
	static final double RADIUS = 6.0;
	static final int DAMAGE = 300;
	static final int COOLDOWN = 220;
	static final double ZONE_RADIUS = 5.0;
	static final int ZONE_TICKS = 100;

	private final ServerPlayer caster;
	private final HammerState state;
	private final GroundShape bound;
	private final GroundShape closing;
	private int t;

	private Crush(ServerPlayer caster, HammerState state, GroundShape bound, GroundShape closing) {
		this.caster = caster;
		this.state = state;
		this.bound = bound;
		this.closing = closing;
	}

	static void cast(ServerPlayer p, HammerState st) {
		if (st.crush != null || Cooldowns.blocked(p, HammerKnight.CRUSH, "중력 파쇄", ChatFormatting.YELLOW)) {
			return;
		}
		Attachments.profile(p).setCooldown(HammerKnight.CRUSH, COOLDOWN);
		ServerLevel level = p.level();
		GroundShape bound = GroundShape.sector(level, p.position(), 0.0F, 360.0, RADIUS, 0x18FFD24A, 0xE0FFD24A);
		GroundShape closing = GroundShape.ring(level, p.position(), RADIUS, 0xF0B45CFF);
		Crush c = new Crush(p, st, bound, closing);
		st.crush = c;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.HK_SMASH, -1);
		Fx.sound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.9F, 0.7F);
		Effects.add(c);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		if (t < Ticks.of(WINDUP) && Attachments.combatant(caster).interrupted()) {
			cleanup();
			Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(caster).msgT = 30;
			kr.overbreak.util.Hud.actionbar(caster, kr.overbreak.util.Hud.text("중력 파쇄 시전이 끊겼다", net.minecraft.ChatFormatting.GRAY));
			return false;
		}
		t++;
		bound.moveTo(caster.position());
		closing.moveTo(caster.position());
		closing.setRadius(Math.max(0.4, RADIUS - 0.36 * Ticks.time(t)));
		if (t < Ticks.of(WINDUP)) {
			return true;
		}
		cleanup();
		fire();
		return false;
	}

	private void fire() {
		ServerLevel level = caster.level();
		Vec3 origin = caster.position();
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.7F);
		Fx.sound(caster, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
				origin.x, origin.y + 0.1, origin.z, 50, 1.5, 0.1, 1.5, 0.3);
		for (LivingEntity e : Targets.enemies(level, origin, RADIUS, caster)) {
			Vec3 to = e.position().subtract(origin);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			Vec3 dir = flat.lengthSqr() < 1.0E-4 ? Motion.flatLook(caster) : flat.normalize();
			Vec3 dest = origin.add(dir);
			e.teleportTo(dest.x, origin.y, dest.z);
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
			SkillDamage.deal(e, caster, DAMAGE, SkillDamage.Kind.NO_KB);
			if (e.isAlive()) {
				CrowdControl.slow(e, 0.7, 16);
			}
			Fx.sound(e, SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.9F, 0.7F);
		}
		Effects.add(new Zone(caster, origin));
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
		bound.discard();
		closing.discard();
		if (state.crush == this) {
			state.crush = null;
		}
	}

	/** 균열 지대 — 위에 있는 적의 이동기를 봉인합니다. */
	static final class Zone implements Effects.Active {
		private final ServerPlayer owner;
		private final ServerLevel level;
		private final Vec3 center;
		private final GroundShape shape;
		private int t;

		Zone(ServerPlayer owner, Vec3 center) {
			this.owner = owner;
			this.level = owner.level();
			this.center = center;
			this.shape = GroundShape.sector(level, center, 0.0F, 360.0, ZONE_RADIUS, 0x405A1E8C, 0xE0B050FF);
			for (int i = 0; i < 6; i++) {
				shape.crack(i * 60.0 + 11.0 * ((i * 7) % 3), 0.2, ZONE_RADIUS - 0.2, 0.12, 0xE0240A3A, i + 1);
			}
		}

		@Override
		public boolean tick() {
			t++;
			for (LivingEntity e : Targets.within(level, center, ZONE_RADIUS, e -> e != owner)) {
				CrowdControl.seal(e, 3);
				// 균열 지대 위에서는 늘 조금 느려집니다 (0.1 버전)
				CrowdControl.slow(e, 0.1, 3);
			}
			double time = Ticks.time(t);
			if (time >= ZONE_TICKS - 10) {
				shape.setAlpha((float) (ZONE_TICKS - time) / 10.0F);
			}
			if (t >= Ticks.of(ZONE_TICKS)) {
				shape.discard();
				return false;
			}
			return true;
		}

		@Override
		public void cancel() {
			shape.discard();
		}

		@Override
		public @Nullable Object owner() {
			return owner;
		}
	}
}
