package kr.overbreak.classes.warrior;

import kr.overbreak.core.tick.Ticks;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.math.Transformation;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.ChainRope;
import kr.overbreak.util.Displays;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Targets;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * [Q] 광란의 처형장 — 데이터팩 ult/warrior/* 대응.
 *
 *   시전한 자리에 반경 6칸 · 8초(160틱) 고정
 *   발동 순간 반경 6칸 적에게 60 피해 + 1초 기절
 *   영역 안의 적 80% 둔화 (5틱마다 8틱짜리로 갱신 — 나가면 곧 풀림)
 *   한 번이라도 안에 들어온 적은 사슬에 묶여 경계를 넘으면 틱당 0.6칸 끌려 들어옴
 *   (한 번도 안 들어온 적은 묶이지 않음. 처형장이 사라지면 0.5초 안에 풀림)
 *
 * 연출 (실제 모델, 오버워치 마우가 궁극기 같은 케이지)
 *   둘레: 쇠창살 벽 24칸 x 3단 + 맨 위 가로 사슬 테두리. 땅속에서 솟아오르고, 끝나면 가라앉음
 *   가운데: 모루 말뚝. 구속된 적마다 말뚝에서 실제 사슬 모델이 이어짐
 *   공중에서 써도 발밑 바닥(아래로 최대 32칸)에 설치됩니다
 *
 * 시전자의 이동속도 +20% · 살육 쿨 2배 감소는 {@link Warrior} 가 셉니다.
 * 영역은 시전자와 무관하게 스스로 돕니다 (시전자가 죽어도 8초 유지).
 */
final class ExecutionGround implements Effects.Active {
	static final int DURATION = 160;
	static final double RADIUS = 6.0;
	private static final double BIND_RANGE = 14.0;
	private static final int BOUND_TICKS = 10;
	private static final double PULL_STEP = 0.6;

	private static final int SEGMENTS = 24;
	private static final int ROWS = 3;
	private static final int RISE_TICKS = 8;
	private static final int SINK_TICKS = 10;
	/** 둘레 한 칸 너비 (현의 길이 + 이음새가 벌어지지 않게 조금 겹침). */
	private static final float SEG_WIDTH = (float) (2.0 * RADIUS * Math.sin(Math.PI / SEGMENTS)) + 0.08F;
	/** 땅속에 숨겨 둔 높이. */
	private static final float SUNK = -(ROWS + 1.0F);
	private static final int DARK = Fx.rgb(0.60, 0.02, 0.02);

	private record Part(Display.BlockDisplay display, Transformation up, Transformation down) {}

	private final ServerPlayer caster;
	private final ServerLevel level;
	private final Vec3 center;
	private final Vec3 anchor;
	private int left = Ticks.of(DURATION);
	private int age;
	private int sinking = -1;
	private final List<Part> cage = new ArrayList<>();
	private Display.@Nullable BlockDisplay stake;
	/** 한 번이라도 영역에 들어온 적 → 남은 구속 틱. */
	private final Map<LivingEntity, Integer> bound = new IdentityHashMap<>();
	private final Map<LivingEntity, ChainRope> ropes = new IdentityHashMap<>();

	private ExecutionGround(ServerPlayer caster) {
		this.caster = caster;
		this.level = (ServerLevel) caster.level();
		this.center = ground(level, caster);
		this.anchor = center.add(0.0, 0.7, 0.0);
	}

	/** 시전자 발밑에서 아래로 바닥을 찾습니다. 없으면(허공) 시전 위치 그대로. */
	private static Vec3 ground(ServerLevel level, ServerPlayer p) {
		Vec3 from = p.position().add(0.0, 0.1, 0.0);
		Vec3 to = from.add(0.0, -32.0, 0.0);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? p.position() : hit.getLocation();
	}

	static void cast(ServerPlayer p) {
		ExecutionGround g = new ExecutionGround(p);
		ServerLevel level = g.level;
		Fx.sound(p, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2F, 1.4F);
		Fx.sound(p, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.7F);
		Fx.sound(p, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.6F);
		Fx.particle(level, Fx.dust(Fx.rgb(0.90, 0.05, 0.05), 2.0F), p.getX(), p.getY() + 1, p.getZ(), 120, 1.5, 0.8, 1.5, 0);
		Fx.particle(level, ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 80, 1.2, 0.6, 1.2, 0.6);
		Fx.particle(level, ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 40, 1.0, 0.6, 1.0, 0.4);

		for (LivingEntity e : Targets.enemies(level, g.center, RADIUS, p)) {
			SkillDamage.deal(e, p, 600, SkillDamage.Kind.NORMAL);
			CrowdControl.stun(e, 20);
			Fx.particle(level, Fx.dust(Fx.rgb(0.90, 0.05, 0.05), 1.6F), e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.4, 0.3, 0);
		}
		g.buildCage();
		Effects.add(g);
	}

	// ── 케이지 모델 ─────────────────────────────────────────

	private void buildCage() {
		BlockState bars = Blocks.IRON_BARS.defaultBlockState()
				.setValue(CrossCollisionBlock.EAST, true)
				.setValue(CrossCollisionBlock.WEST, true);
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		for (int s = 0; s < SEGMENTS; s++) {
			double theta = Math.PI * 2.0 * (s + 0.5) / SEGMENTS;
			double x = center.x + Math.cos(theta) * RADIUS;
			double z = center.z + Math.sin(theta) * RADIUS;
			// 블록의 X 축(창살 판의 긴 쪽)을 원의 접선 방향으로
			Quaternionf rot = new Quaternionf().rotationY((float) -(theta + Math.PI / 2.0));
			for (int row = 0; row < ROWS; row++) {
				part(bars, x, center.y + row, z, rot, 0.0F);
			}
			// 맨 위 테두리: 가로로 누운 사슬 (블록 가운데 높이를 테두리 높이에 맞춤)
			part(chain, x, center.y + ROWS, z, rot, -0.5F);
		}
		stake = Displays.block(level, center, 0.0F, 0.0F, Blocks.ANVIL.defaultBlockState(),
				Displays.transform(-0.4F, 0.0F, -0.4F, 0.8F, 0.8F, 0.8F, null, null), 0);
	}

	/** 블록 한 칸을 가로 SEG_WIDTH 로 늘리고 가운데를 기준점에 맞춰 회전합니다. 처음엔 땅속(SUNK)에 둡니다. */
	private void part(BlockState state, double x, double y, double z, Quaternionf rot, float offsetY) {
		Vector3f offset = rot.transform(new Vector3f(-SEG_WIDTH / 2.0F, 0.0F, -0.5F));
		Transformation up = Displays.transform(offset.x, offsetY, offset.z, SEG_WIDTH, 1.0F, 1.0F, rot, null);
		Transformation down = Displays.transform(offset.x, offsetY + SUNK, offset.z, SEG_WIDTH, 1.0F, 1.0F, rot, null);
		Display.BlockDisplay d = Displays.block(level, new Vec3(x, y, z), 0.0F, 0.0F, state, down, 0);
		if (d != null) {
			cage.add(new Part(d, up, down));
		}
	}

	// ── 매 틱 ───────────────────────────────────────────────

	@Override
	public boolean tick() {
		age++;
		if (age == 1) {
			// 소환한 다음 틱에 보간을 걸어야 솟아오르는 모습이 보입니다
			cage.forEach(part -> Displays.animate(part.display(), part.up(), RISE_TICKS));
			Fx.sound(level, center.x, center.y, center.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5F, 0.6F);
			Fx.sound(level, center.x, center.y, center.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.5F);
		}
		if (sinking >= 0) {
			if (++sinking >= Ticks.of(SINK_TICKS)) {
				cleanup();
				return false;
			}
			return true;
		}

		left--;

		// 영역 안의 적은 구속 표시를 새로 채웁니다 (밖에 있던 적은 묶이지 않음)
		for (LivingEntity e : Targets.within(level, center, RADIUS, e -> e != caster)) {
			bound.put(e, Ticks.of(BOUND_TICKS));
		}

		boolean sound = Ticks.every(left, 5);
		var it = bound.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			LivingEntity e = entry.getKey();
			int remain = entry.getValue() - 1;
			if (remain <= 0 || !e.isAlive() || e.isRemoved()) {
				it.remove();
				dropRope(e);
				continue;
			}
			entry.setValue(remain);
			double dist = e.position().distanceTo(center);
			if (dist > BIND_RANGE) {
				dropRope(e);
				continue;
			}
			if (dist > RADIUS) {
				pullIn(e, sound);
			}
			ropes.computeIfAbsent(e, k -> new ChainRope(level, 32))
					.draw(anchor, e.position().add(0.0, e.getBbHeight() * 0.55, 0.0));
		}

		if (Ticks.every(left, 5)) {
			for (LivingEntity e : Targets.within(level, center, RADIUS, e -> e != caster)) {
				CrowdControl.slow(e, 0.8, 8);
			}
		}
		if (left <= 0) {
			beginSink();
		}
		return true;
	}

	/** 경계를 넘어간 구속 대상을 중앙 쪽으로 0.6칸. */
	private void pullIn(LivingEntity e, boolean sound) {
		Vec3 dir = center.subtract(e.position()).normalize().scale(Ticks.speed(PULL_STEP));
		Vec3 to = e.position().add(dir);
		e.teleportTo(to.x, to.y, to.z);
		if (sound) {
			Fx.sound(e, SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
	}

	private void beginSink() {
		sinking = 0;
		ropes.values().forEach(ChainRope::discard);
		ropes.clear();
		bound.clear();
		cage.forEach(part -> Displays.animate(part.display(), part.down(), SINK_TICKS));
		if (stake != null) {
			stake.discard();
			stake = null;
		}
		Fx.sound(level, center.x, center.y, center.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
	}

	private void dropRope(LivingEntity e) {
		ChainRope rope = ropes.remove(e);
		if (rope != null) {
			rope.discard();
		}
	}

	@Override
	public void cancel() {
		cleanup();
	}

	private void cleanup() {
		cage.forEach(part -> part.display().discard());
		cage.clear();
		if (stake != null) {
			stake.discard();
			stake = null;
		}
		ropes.values().forEach(ChainRope::discard);
		ropes.clear();
	}

	@Override
	public Object owner() {
		return null;
	}
}
