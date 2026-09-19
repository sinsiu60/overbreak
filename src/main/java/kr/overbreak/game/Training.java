package kr.overbreak.game;

import kr.overbreak.core.tick.Ticks;
import java.util.List;

import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 훈련용 더미 — 훈련장 (56.5, -60, -40.5) 에 서는 마네킹 (0.1a 에서 주민 → 마네킹).
 *
 *   서버 맵에 예전 주민 더미가 서 있으면 지우고 마네킹으로 갈아 세웁니다
 *   체력 1000 · 스스로 움직이지 않음 (넉백 · 기절 · 둔화는 보임) · 40% 아래로 내려가면 가득 회복
 *   밀려나고 2초 동안 안 맞으면 제자리로
 */
public final class Training {
	public static final String TAG = "overbreak.dummy";
	private static final String DATAPACK_TAG = "pvp.dummy";
	static final double HEALTH = 1000.0;

	private static @Nullable Mannequin dummy;
	private static long lastHit;

	private Training() {}

	static void onDamaged(LivingEntity e) {
		if (e == dummy) {
			lastHit = e.level().getGameTime();
		}
	}

	/** 더미가 없으면 찾거나 세웁니다. */
	public static @Nullable Mannequin ensure(ServerLevel level) {
		if (dummy != null && dummy.isAlive() && !dummy.isRemoved() && dummy.level() == level) {
			return dummy;
		}
		Vec3 home = Places.TRAINING_DUMMY;
		AABB near = new AABB(home, home).inflate(24.0);
		// 예전 판(주민)이 남아 있으면 치웁니다
		for (Villager old : level.getEntitiesOfClass(Villager.class, near,
				v -> v.entityTags().contains(TAG) || v.entityTags().contains(DATAPACK_TAG))) {
			old.discard();
		}
		List<Mannequin> found = level.getEntitiesOfClass(Mannequin.class, near, m -> m.entityTags().contains(TAG));
		Mannequin v = found.isEmpty() ? EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND) : found.getFirst();
		if (v == null) {
			return null;
		}
		if (found.isEmpty()) {
			v.snapTo(home.x, home.y, home.z, -90.0F, 0.0F);
			level.addFreshEntity(v);
		}
		v.addTag(TAG);
		dress(v, Component.literal("훈련용 더미"), false);
		dummy = v;
		return v;
	}

	/**
	 * 훈련용 마네킹 공통 설정 — 체력 1000, 스스로 걷지 않음, 기본 설명("마네킹") 숨김.
	 * 밀리는 것은 그대로 두어 넉백 · 끌어오기가 눈에 보이게 합니다.
	 */
	public static void dress(Mannequin v, Component name, boolean nameVisible) {
		set(v.getAttribute(Attributes.MAX_HEALTH), HEALTH);
		set(v.getAttribute(Attributes.MOVEMENT_SPEED), 0.0);
		v.setHealth((float) HEALTH);
		v.setSilent(true);
		v.setCustomName(name);
		v.setCustomNameVisible(nameVisible);
		if (v instanceof kr.overbreak.mixin.MannequinAccess access) {
			access.overbreak$setHideDescription(true);
			access.overbreak$setImmovable(false);
		}
	}

	private static void set(@Nullable AttributeInstance attr, double value) {
		if (attr != null) {
			attr.setBaseValue(value);
		}
	}

	static void tick(ServerLevel level, boolean anyone) {
		if (!anyone && (dummy == null || !dummy.isAlive())) {
			return;
		}
		Mannequin v = ensure(level);
		if (v == null) {
			return;
		}
		if (v.getHealth() < v.getMaxHealth() * 0.4F) {
			v.setHealth(v.getMaxHealth());
			Fx.particle(level, ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.0, v.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
			Fx.sound(v, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 0.6F, 1.4F);
		}
		Vec3 home = Places.TRAINING_DUMMY;
		if (v.position().distanceToSqr(home) > 9.0 && level.getGameTime() - lastHit > Ticks.of(40)) {
			v.teleportTo(home.x, home.y, home.z);
			v.setDeltaMovement(Vec3.ZERO);
			v.setYRot(-90.0F);
			v.setYHeadRot(-90.0F);
			v.setYBodyRot(-90.0F);
			Fx.particle(level, ParticleTypes.PORTAL, home.x, home.y + 1.0, home.z, 25, 0.3, 0.5, 0.3, 0.05);
		}
	}
}
