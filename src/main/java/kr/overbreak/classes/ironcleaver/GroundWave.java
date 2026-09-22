package kr.overbreak.classes.ironcleaver;

import static kr.overbreak.classes.ironcleaver.IronSpec.*;

import java.util.HashSet;
import java.util.Set;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Targets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * 대지 가르기 충격파 — 땅을 따라 초당 20칸으로 8칸 나아가는 세로 칼날 (서버가 움직이고 판정, 클라이언트는 같은 규칙으로 그림).
 *
 *   매 틱 발밑 지면 높이를 찾아 맞춤 — 올라가는 턱 · 내려가는 턱은 1칸까지 타고, 그보다 크면(벽 · 절벽) 소멸
 *   판정: 폭 2칸 × 높이 2.5칸, 적을 모두 관통 (대상마다 한 번) · 25 피해 + 40% 둔화 1.5초
 */
public final class GroundWave implements Effects.Active {
	private final ServerPlayer caster;
	private final Vec3 dir;
	private Vec3 pos;
	private double traveled;
	private final Set<LivingEntity> hit = new HashSet<>();

	GroundWave(ServerPlayer caster, Vec3 start, Vec3 dir) {
		this.caster = caster;
		this.dir = dir;
		Vec3 g = ground(caster.level(), start, REND_STEP + 0.5);
		this.pos = g != null ? g : start;
	}

	/**
	 * pos 둘레에서 딛고 설 지면 (발 위치) — 위아래로 reach 칸 안에서 가장 가까운 바닥 윗면 (반블록 · 계단은 모양대로). 없으면 null.
	 * 시험 · 시전 조건(공중이면 거부)에서도 씁니다.
	 */
	public static @Nullable Vec3 ground(ServerLevel level, Vec3 pos, double reach) {
		int top = (int) Math.floor(pos.y + Math.min(reach, REND_STEP) + 0.5);
		int bottom = (int) Math.floor(pos.y - reach);
		for (int y = top; y >= bottom; y--) {
			BlockPos bp = BlockPos.containing(pos.x, y, pos.z);
			BlockState s = level.getBlockState(bp);
			VoxelShape shape = s.getCollisionShape(level, bp);
			if (shape.isEmpty()) {
				continue;
			}
			double surface = bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
			// 그 위 두 칸이 비어 있어야 지면 (벽 속이 아님)
			BlockPos above = BlockPos.containing(pos.x, surface + 0.01, pos.z);
			if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty() && above.getY() != bp.getY()) {
				return null;
			}
			return new Vec3(pos.x, surface, pos.z);
		}
		return null;
	}

	@Override
	public boolean tick() {
		ServerLevel level = caster.level();
		double step = Ticks.speed(REND_SPEED);
		Vec3 next = pos.add(dir.scale(step));
		Vec3 g = ground(level, next, REND_STEP + 0.01);
		if (g == null || Math.abs(g.y - pos.y) > REND_STEP + 1.0E-3) {
			// 벽 · 절벽 — 소멸 (앞으로 흩어지는 파편)
			Fx.particle(level, ParticleTypes.CRIT, pos.x, pos.y + 1.0, pos.z, 10, 0.3, 0.6, 0.3, 0.2);
			return false;
		}
		pos = g;
		traveled += step;
		strike(level);
		if (Ticks.ambient()) {
			Fx.particle(level, ParticleTypes.LAVA, pos.x, pos.y + 0.1, pos.z, 1, 0.1, 0.0, 0.1, 0.0);
			Fx.particle(level, ParticleTypes.CRIT, pos.x, pos.y + 0.3, pos.z, 3, 0.2, 0.1, 0.2, 0.15);
		}
		return traveled < REND_RANGE && caster.isAlive();
	}

	/** 폭 2칸 × 높이 2.5칸 안의 적 — 대상마다 한 번. */
	private void strike(ServerLevel level) {
		for (LivingEntity e : Targets.enemies(level, pos, REND_WIDTH + 1.5, caster)) {
			if (hit.contains(e)) {
				continue;
			}
			double dx = e.getX() - pos.x;
			double dz = e.getZ() - pos.z;
			double along = dx * dir.x + dz * dir.z;
			double side = Math.abs(dx * -dir.z + dz * dir.x);
			double half = e.getBbWidth() / 2.0;
			if (along < -0.6 - half || along > 0.6 + half || side > REND_WIDTH / 2.0 + half) {
				continue;
			}
			if (e.getBoundingBox().maxY < pos.y || e.getBoundingBox().minY > pos.y + REND_HEIGHT) {
				continue;
			}
			hit.add(e);
			IronStrikes.deal(caster, e, REND_DAMAGE, false, 0);
			CrowdControl.slow(e, REND_SLOW, REND_SLOW_TIME);
		}
	}

	@Override
	public Object owner() {
		return caster;
	}
}
