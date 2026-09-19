package kr.overbreak.util;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 파티클 · 소리 한 줄짜리 도우미. 데이터팩의 particle / playsound 대응. */
public final class Fx {
	private Fx() {}

	/** 0xRRGGBB + 크기. 데이터팩 dust{color:[r,g,b],scale:s} 와 같습니다. */
	public static DustParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb, scale);
	}

	public static int rgb(double r, double g, double b) {
		return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
	}

	public static void particle(ServerLevel level, ParticleOptions p, double x, double y, double z,
								int count, double dx, double dy, double dz, double speed) {
		level.sendParticles(p, x, y, z, count, dx, dy, dz, speed);
	}

	public static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count,
								double dx, double dy, double dz, double speed) {
		level.sendParticles(p, at.x, at.y, at.z, count, dx, dy, dz, speed);
	}

	/**
	 * 한 명을 빼고 보냅니다. 시전자 몸에 붙어 나오는 큰 파티클(휩쓸기 등)이
	 * 1인칭 화면을 가리지 않게 할 때 씁니다. except 가 null 이면 모두에게 보냅니다.
	 */
	public static void particleExcept(ServerLevel level, @Nullable ServerPlayer except, ParticleOptions p,
									  double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		if (except == null) {
			particle(level, p, x, y, z, count, dx, dy, dz, speed);
			return;
		}
		for (ServerPlayer viewer : level.players()) {
			if (viewer != except) {
				level.sendParticles(viewer, p, false, false, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}

	public static void sound(ServerLevel level, double x, double y, double z,
							 SoundEvent sound, SoundSource source, float volume, float pitch) {
		level.playSound(null, x, y, z, sound, source, volume, pitch);
	}

	public static void sound(ServerLevel level, double x, double y, double z,
							 Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
		level.playSound(null, x, y, z, sound.value(), source, volume, pitch);
	}

	public static void sound(Entity at, SoundEvent sound, SoundSource source, float volume, float pitch) {
		if (at.level() instanceof ServerLevel level) {
			sound(level, at.getX(), at.getY(), at.getZ(), sound, source, volume, pitch);
		}
	}

	public static void sound(Entity at, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
		sound(at, sound.value(), source, volume, pitch);
	}

	/** 바닥 원 (수평, 점 n개). 데이터팩 발밑 링 대응. */
	public static void ring(ServerLevel level, Vec3 center, double radius, int points, double yOffset, ParticleOptions p) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(p, center.x + Math.cos(a) * radius, center.y + yOffset,
					center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
	}

	public static Holder<SoundEvent> holder(SoundEvent sound) {
		return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
	}
}
