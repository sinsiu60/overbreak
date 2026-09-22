package kr.overbreak.client.fx;

import static kr.overbreak.classes.ironcleaver.IronSpec.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import kr.overbreak.classes.ironcleaver.Ironcleaver;
import kr.overbreak.client.ClientClock;
import kr.overbreak.net.IronPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.sound.OverbreakSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * 참철 연출 — 서버 신호({@link IronPayload} · 스킬 애니메이션)를 받아 클라이언트가 전부 그리고 틉니다.
 *
 *   역경직 (본인 화면만): 내 평타 · 모아 베기가 누군가를 맞히면 1인칭 칼이 그 자리에서 잠깐 멈췄다가
 *     멈춘 만큼을 빠르게 따라잡음 (평타 1·2타 0.05초 · 3타 0.08초 · 모아 베기 0.06~0.12초) + 화면이 짧게 흔들림
 *   맞은 사람: 화면이 세기만큼 흔들림
 *   휘두르기 궤적: 판정 순간 몸 둘레에 부채꼴 빛 띠 (모아 베기는 단계 색 · 더 넓고 오래)
 *   대지 가르기: 지면을 따라 달리는 세로 칼날 (서버와 같은 규칙으로 땅을 탐) + 지나간 자리의 금
 *   천참: 모으는 동안 바닥에 붉은 직사각형 예고 · 머리 위로 자라는 기운의 칼날 → 베는 순간 14칸 빛의 칼날
 *   오오라: 모으기 단계 색으로 칼날이 빛남 (1인칭 · 3인칭 · 모두에게)
 *
 * 시간은 모두 1/20초 단위 연속 시계({@link ClientClock})입니다.
 */
public final class IronFx {
	/** 궤적 띠 · 예고 · 칼날 — 가장자리만 살짝 흐린 면. */
	private static final net.minecraft.resources.Identifier FILL = kr.overbreak.Overbreak.id("textures/effect/iron_fill.png");
	/** 역경직이 끝난 뒤 따라잡는 시간 (0.15초 — 그동안 1 + 멈춘 시간/0.15 배속). */
	private static final float CATCH = 3.0F;

	private record Stage(int stage, boolean perfect, double at) {}

	/** 부채꼴 빛 띠. yaw = 시전자 시선, from · to = 몸 기준 각도 (+ = 오른쪽), vertical = 세로 (위 + → 아래 -). */
	private record Arc(int entityId, Vec3 fallback, float yaw, float from, float to, boolean vertical, double born,
					   float sweep, float life, int color, float inner, float outer, float height, float bright) {}

	/** 대지 가르기 칼날 (클라이언트가 서버와 같은 규칙으로 움직임). */
	private static final class Wave {
		Vec3 pos;
		final Vec3 dir;
		double traveled;
		double born;
		double deadAt = Double.NaN;
		final List<Vec3> path = new ArrayList<>();
		final List<Double> pathAt = new ArrayList<>();

		Wave(Vec3 pos, Vec3 dir, double born) {
			this.pos = pos;
			this.dir = dir;
			this.born = born;
			path.add(pos);
			pathAt.add(born);
		}
	}

	/** 천참 예고 (모으기 시작 → 베는 순간까지). */
	private record Ult(int entityId, Vec3 at, float yaw, double born) {}

	/** 천참 베기 · 모아 베기 충격 (그리는 동안). */
	private record Cleave(Vec3 at, float yaw, double born, int stage) {}

	/** 늦게 틀 소리 · 궤적 (선딜이 끝나는 순간). */
	private record Pending(double at, int entityId, Runnable run) {}

	/** 상대가 검막을 쓴 순간부터 (적 화면에만 그림). */
	private record GuardTell(int entityId, double born) {}

	/** 참철 동작 번호 전부. */
	public static final int[] IRON_ANIMS = {SkillAnimPayload.IC_SWING_R, SkillAnimPayload.IC_SWING_L, SkillAnimPayload.IC_OVERHEAD,
			SkillAnimPayload.IC_CHARGE, SkillAnimPayload.IC_RELEASE, SkillAnimPayload.IC_BASH, SkillAnimPayload.IC_GUARD,
			SkillAnimPayload.IC_REND, SkillAnimPayload.IC_ULT};

	private static final Map<Integer, Stage> STAGES = new HashMap<>();
	private static final List<Arc> ARCS = new ArrayList<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Ult> ULTS = new ArrayList<>();
	private static final List<Cleave> CLEAVES = new ArrayList<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<GuardTell> GUARDS = new ArrayList<>();
	/** 시험용: 내 검막도 상대 화면처럼 그림 (3인칭 스크린샷 확인). */
	public static boolean guardTellOnSelf;
	/** 모으는 중 반복음 (엔티티별). */
	private static final Map<Integer, Loop> LOOPS = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	// 역경직 (본인)
	private static double hitAt = -1000.0;
	private static float stop;
	// 화면 흔들림 (본인 — 때림 · 맞음)
	private static double shakeAt = -1000.0;
	private static float shakeAmp;
	private static float shakeLen = 1.0F;
	private static int shakeSeed;
	// 1인칭 화면 가장자리 — 단계가 오른 순간 번쩍
	private static double stageFlashAt = -1000.0;

	private IronFx() {}

	// ── 받기 ─────────────────────────────────────────────

	public static void receive(IronPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		double now = ClientClock.now();
		Entity e = mc.level.getEntity(msg.entityId());
		boolean self = mc.player != null && msg.entityId() == mc.player.getId();
		switch (msg.kind()) {
			case IronPayload.STAGE -> {
				Stage before = STAGES.get(msg.entityId());
				if (msg.a() <= 0) {
					STAGES.remove(msg.entityId());
					return;
				}
				STAGES.put(msg.entityId(), new Stage(msg.a(), msg.b() != 0, now));
				boolean up = before == null || msg.a() > before.stage || (msg.b() != 0 && !before.perfect);
				if (up && e != null) {
					if (msg.b() != 0) {
						play(e, OverbreakSounds.IRON_CHARGE_PERFECT, 0.8F, 1.6F);
					} else {
						play(e, OverbreakSounds.IRON_CHARGE_STAGE, 0.6F, STAGE_PITCH[Math.min(3, msg.a()) - 1]);
					}
					// 1인칭 본인은 가루가 눈앞을 가리므로 뿌리지 않음 (화면 가장자리 번쩍 · 오오라로 알림)
					if (!(self && mc.options.getCameraType().isFirstPerson())) {
						burst(mc.level, e, msg.a(), msg.b() != 0);
					}
					if (self) {
						stageFlashAt = now;
					}
				}
			}
			case IronPayload.HIT -> {
				if (!self) {
					return;
				}
				hitAt = now;
				stop = hitstop(msg.a());
				shake(SHAKE_HIT[strength(msg.a())][0], SHAKE_HIT[strength(msg.a())][1]);
				if (mc.player != null) {
					play(mc.player, OverbreakSounds.IRON_SWING_HIT, 1.0F, 0.7F);
				}
			}
			case IronPayload.HURT -> {
				if (self) {
					shake(SHAKE_HURT[strength(msg.a())][0], SHAKE_HURT[strength(msg.a())][1]);
				}
			}
			case IronPayload.REND -> {
				Vec3 dir = forward(msg.yaw());
				Vec3 start = new Vec3(msg.x(), msg.y(), msg.z());
				Vec3 g = ground(mc.level, start, REND_STEP + 0.5);
				Wave w = new Wave(g != null ? g : start, dir, now);
				WAVES.add(w);
				if (self) {
					// 칼끝을 튕겨 올리는 순간 (스펙 0.2)
					shake(0.3F, 3.0F);
				}
				// 칼날이 달리는 동안 따라다니는 소리 (사라지면 끊김)
				mc.getSoundManager().play(new Loop(OverbreakSounds.IRON_REND_WAVE.value(), 0.6F, 0.6F, () -> Double.isNaN(w.deadAt) ? w.pos : null));
			}
			case IronPayload.ULT -> {
				ULTS.removeIf(u -> u.entityId == msg.entityId());
				ULTS.add(new Ult(msg.entityId(), new Vec3(msg.x(), msg.y(), msg.z()), msg.yaw(), now));
				if (e != null) {
					play(e, OverbreakSounds.IRON_ULT_CHARGE, 1.0F, 0.6F);
				}
			}
			case IronPayload.BLOCK -> {
				if (e != null) {
					play(e, OverbreakSounds.IRON_GUARD_BLOCK, 1.0F, 0.9F);
					play(e, OverbreakSounds.IRON_GUARD_BLOCK_CLANG, 0.3F, 1.5F);
					Vec3 f = forward(msg.yaw());
					Vec3 at = e.position().add(f.scale(0.8)).add(0, 1.2, 0);
					for (int i = 0; i < 12; i++) {
						mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
								f.x * 0.3 + (RANDOM.nextDouble() - 0.5) * 0.4, RANDOM.nextDouble() * 0.3, f.z * 0.3 + (RANDOM.nextDouble() - 0.5) * 0.4);
					}
				}
			}
			case IronPayload.BASH -> {
				Vec3 at = new Vec3(msg.x(), msg.y(), msg.z());
				playAt(at, OverbreakSounds.IRON_BASH_HIT, 1.0F, 0.8F);
				for (int i = 0; i < 8; i++) {
					mc.level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z,
							(RANDOM.nextDouble() - 0.5) * 0.6, RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.6);
				}
			}
			case IronPayload.CLEAVE -> cleave(mc, msg, e, now);
			default -> {
			}
		}
	}

	/** 단계 도달 소리 피치 (1단 · 2단 · 3단). */
	private static final float[] STAGE_PITCH = {0.8F, 1.0F, 1.3F};

	/**
	 * 동작 애니메이션 — 소리 · 궤적을 그 동작의 박자에 맞춰 예약합니다 (재생 시작 시각 기준이라 본인 예측 재생 · 늦게 받은 재생 모두 맞음).
	 *   평타: 선딜 시작 치켜듦 소리 → 선딜 끝 휘두름 소리 + 궤적
	 *   모으기: 모으는 동안 반복음 (단계마다 피치가 오름 · 끝나면 0.1초 페이드아웃)
	 *   검막: 치켜듦 소리 · 대지 가르기: 선딜 끝에 땅 긁는 소리
	 */
	public static void onAnim(SkillAnimPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || msg.anim() <= 0) {
			return;
		}
		int id = msg.entityId();
		kr.overbreak.client.anim.SkillAnims.Play play = kr.overbreak.client.anim.SkillAnims.find(id, msg.anim());
		if (play == null || play.predicted()) {
			// 본인 예측 재생을 서버가 확인한 것 — 누른 순간 이미 예약했음
			return;
		}
		schedule(mc, id, msg.anim(), play, msg.elapsed() > 0);
	}

	private static void schedule(Minecraft mc, int id, int anim, kr.overbreak.client.anim.SkillAnims.Play play, boolean late) {
		Entity e = mc.level.getEntity(id);
		double start = play.startTime();
		switch (anim) {
			case SkillAnimPayload.IC_SWING_R, SkillAnimPayload.IC_SWING_L, SkillAnimPayload.IC_OVERHEAD -> {
				int k = anim == SkillAnimPayload.IC_SWING_R ? 0 : anim == SkillAnimPayload.IC_SWING_L ? 1 : 2;
				// 같은 사람의 앞선 예약은 버림 (모으기 1단 전에 떼어 선딜을 건너뛴 휘두르기가 다시 오는 경우)
				PENDING.removeIf(p -> p.entityId == id);
				if (!late && e != null) {
					play(e, OverbreakSounds.IRON_SWING_WINDUP, 0.5F, 0.8F);
				}
				PENDING.add(new Pending(start + SWINGS[k].windup(), id, () -> swing(id, k)));
			}
			case SkillAnimPayload.IC_CHARGE -> {
				if (e != null && !LOOPS.containsKey(id)) {
					Loop loop = new Loop(OverbreakSounds.IRON_CHARGE_LOOP.value(), 0.4F, 0.85F, () -> {
						Entity now = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
						return now != null && kr.overbreak.client.anim.SkillAnims.playing(id, SkillAnimPayload.IC_CHARGE) ? now.position() : null;
					});
					loop.pitchFor = () -> 0.85F + 0.15F * stage(id);
					LOOPS.put(id, loop);
					mc.getSoundManager().play(loop);
				}
			}
			case SkillAnimPayload.IC_GUARD -> {
				if (e != null && !late) {
					play(e, OverbreakSounds.IRON_SWING_WINDUP, 0.8F, 0.9F);
				}
				if (e != null && enemy(mc, e)) {
					// 적 화면에만 — 쓰는 순간 금빛 번쩍임 + 막는 동안 앞 120° 방벽
					GUARDS.removeIf(g -> g.entityId == id);
					GUARDS.add(new GuardTell(id, start));
					if (!late) {
						Vec3 at = e.position().add(forward(e.getYRot()).scale(0.6)).add(0, 1.3, 0);
						for (int i = 0; i < 14; i++) {
							mc.level.addParticle(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, at.x, at.y, at.z,
									(RANDOM.nextDouble() - 0.5) * 0.5, (RANDOM.nextDouble() - 0.3) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.5);
						}
					}
				}
			}
			case SkillAnimPayload.IC_BASH -> {
				if (e != null && !late) {
					play(e, OverbreakSounds.IRON_BASH_DASH, 0.9F, 1.1F);
				}
			}
			case SkillAnimPayload.IC_REND -> PENDING.add(new Pending(start + REND_WINDUP, id, () -> {
				Entity now = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(id);
				if (now != null) {
					play(now, OverbreakSounds.IRON_REND_SCRAPE, 0.9F, 0.7F);
				}
			}));
			default -> {
			}
		}
	}

	/**
	 * 본인 LMB 누름 — 서버 확인 전에 지금 타수의 휘두르기를 곧바로 틉니다 (스펙 PART 21 예측 재생).
	 * 다른 참철 동작이 도는 중(후딜 · 모으기 · 스킬)이면 서버가 정하므로 틀지 않습니다.
	 * 서버가 같은 번호를 보내면 이어 가고, 안 오면(거부) {@link kr.overbreak.client.anim.SkillAnims} 가 기본 자세로 되돌립니다.
	 */
	public static void localPress(Minecraft mc) {
		if (mc.player == null || mc.level == null || !Ironcleaver.ID.equals(kr.overbreak.client.hud.HudState.classId())) {
			return;
		}
		int id = mc.player.getId();
		for (int anim : IRON_ANIMS) {
			if (kr.overbreak.client.anim.SkillAnims.playing(id, anim)) {
				return;
			}
		}
		int k = Math.floorMod(kr.overbreak.client.hud.HudState.ironCombo(), SWINGS.length);
		int anim = SWINGS[k].anim();
		kr.overbreak.client.anim.SkillAnims.predict(id, anim);
		kr.overbreak.client.anim.SkillAnims.Play play = kr.overbreak.client.anim.SkillAnims.find(id, anim);
		if (play != null) {
			schedule(mc, id, anim, play, false);
		}
	}

	/** 이 개체가 나의 상대인가 — 나도 아니고 우리 편도 아님 (개인전이면 나 말고 모두). */
	private static boolean enemy(Minecraft mc, Entity e) {
		if (e == mc.player) {
			return guardTellOnSelf;
		}
		return !kr.overbreak.client.hud.MatchTeams.ally(e);
	}

	private static void swing(int entityId, int k) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(entityId);
		if (e == null) {
			return;
		}
		Swing s = SWINGS[k];
		// 선딜 중에 모으기로 넘어갔거나 (예측이 거부돼) 끊겼으면 휘두르지 않음
		kr.overbreak.client.anim.SkillAnims.Play latest = kr.overbreak.client.anim.SkillAnims.latestOf(entityId, IRON_ANIMS);
		if (latest == null || latest.anim != s.anim() || !kr.overbreak.client.anim.SkillAnims.playing(entityId, s.anim())) {
			return;
		}
		double now = ClientClock.now();
		float yaw = e.getYRot();
		// 평타 궤적: 흰 회색 · 0.1초 · 밝기 낮게 (스펙 10-5)
		play(e, OverbreakSounds.IRON_SWING_WHOOSH, 1.0F, 0.6F);
		if (k == 2) {
			// 3타 — 벤데타 3타처럼 온 힘으로 내리꽂음: 굵고 밝은 세로 궤적 + 조금 뒤 땅에 박히는 충격
			ARCS.add(new Arc(entityId, e.position(), yaw, 100.0F, -40.0F, true, now, 0.6F, 3.0F, 0xFFFFFF,
					0.8F, (float) s.range() + 0.5F, 1.55F, 1.0F));
			PENDING.add(new Pending(now + SLAM_DELAY, entityId, () -> slam(entityId)));
		} else {
			float half = (float) s.arc() / 2.0F;
			ARCS.add(new Arc(entityId, e.position(), yaw, s.rightToLeft() ? half : -half, s.rightToLeft() ? -half : half, false,
					now, (float) s.active(), 2.0F, BASIC_TRAIL, 1.0F, (float) s.range(), 1.05F, 0.55F));
		}
	}

	/** 3타 칼끝이 땅에 닿는 시각 (선딜 끝 뒤, 1/20초 단위) — 1인칭 키프레임 착지와 같게. */
	private static final double SLAM_DELAY = 0.4;
	/** 3타 착지 충격 — 칼끝 자리 (몸 앞 칸). */
	private static final double SLAM_REACH = 2.2;

	/**
	 * 3타 착지 — 칼끝이 박힌 자리에서 퍼지는 충격 고리 · 사방으로 갈라지는 땅 균열 · 흙 파편 · 먼지 · 폭음.
	 * 본인이면 화면이 아래로 쿵 내려앉듯 흔들림.
	 */
	private static void slam(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity e = mc.level.getEntity(entityId);
		if (e == null) {
			return;
		}
		kr.overbreak.client.anim.SkillAnims.Play latest = kr.overbreak.client.anim.SkillAnims.latestOf(entityId, IRON_ANIMS);
		if (latest == null || latest.anim != SkillAnimPayload.IC_OVERHEAD) {
			return;
		}
		Vec3 f = forward(e.getYRot());
		Vec3 at = e.position().add(f.scale(SLAM_REACH));
		Vec3 g = ground(mc.level, at, 2.0);
		if (g != null) {
			at = g;
		}
		CLEAVES.add(new Cleave(at, e.getYRot(), ClientClock.now(), SLAM));
		playAt(at, OverbreakSounds.IRON_CHARGE_RELEASE_BOOM, 0.6F, 1.25F);
		playAt(at, OverbreakSounds.IRON_BASH_HIT, 0.9F, 0.6F);
		BlockState below = mc.level.getBlockState(BlockPos.containing(at.x, at.y - 0.01, at.z));
		for (int i = 0; i < 18; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2.0;
			double sp = 0.15 + RANDOM.nextDouble() * 0.3;
			if (!below.isAir()) {
				mc.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
						at.x, at.y + 0.1, at.z, Math.cos(a) * sp, 0.35 + RANDOM.nextDouble() * 0.35, Math.sin(a) * sp);
			}
			if (i % 2 == 0) {
				mc.level.addParticle(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, Math.cos(a) * 0.25, 0.02, Math.sin(a) * 0.25);
			}
			if (i % 3 == 0) {
				mc.level.addParticle(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, Math.cos(a) * 0.6, 0.4, Math.sin(a) * 0.6);
			}
		}
		if (e == mc.player) {
			shake(0.55F, 4.0F);
		}
	}

	/** Cleave 의 종류 — 3타 착지 충격. */
	private static final int SLAM = 6;

	/** 평타 궤적 색 (흰 회색). */
	private static final int BASIC_TRAIL = 0xD8DCE4;

	private static void cleave(Minecraft mc, IronPayload msg, @Nullable Entity e, double now) {
		if (msg.a() == 5) {
			// 천참 — 14칸 빛의 칼날 · 땅 울림 · 폭발 먼지
			ULTS.removeIf(u -> u.entityId == msg.entityId());
			Vec3 at = new Vec3(msg.x(), msg.y(), msg.z());
			CLEAVES.add(new Cleave(at, msg.yaw(), now, 5));
			playAt(at, OverbreakSounds.IRON_ULT_CLEAVE, 1.0F, 0.6F);
			playAt(at, OverbreakSounds.IRON_ULT_CLEAVE_THUNDER, 0.5F, 1.2F);
			Vec3 f = forward(msg.yaw());
			for (int i = 1; i <= ULT_LENGTH; i += 2) {
				Vec3 p = at.add(f.scale(i));
				mc.level.addParticle(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 0, 0, 0);
				mc.level.addParticle(ParticleTypes.LAVA, p.x, p.y + 0.2, p.z, 0, 0, 0);
			}
			if (mc.player != null && mc.player.position().distanceTo(at) < 18.0) {
				shake(0.5F, 4.0F);
			}
			return;
		}
		if (e == null) {
			return;
		}
		// 모아 베기 — 칼끝이 그린 호를 따라 단계 색 리본 (0.15초 · 진 참은 빨강 + 흰 코어)
		int stage = msg.a();
		float range = (float) RELEASE_RANGE[stage - 1];
		int color = STAGE_COLOR[Math.min(3, stage)];
		ARCS.add(new Arc(msg.entityId(), e.position(), e.getYRot(), 75.0F, -75.0F, false, now, (float) RELEASE_ACTIVE,
				3.0F, color, 0.8F, range, 1.0F, 1.0F));
		if (stage >= 4) {
			ARCS.add(new Arc(msg.entityId(), e.position(), e.getYRot(), 75.0F, -75.0F, false, now, (float) RELEASE_ACTIVE,
					3.0F, 0xFFFFFF, range * 0.62F, range * 0.78F, 1.02F, 1.0F));
		}
		play(e, OverbreakSounds.IRON_CHARGE_RELEASE, 1.0F, 0.5F);
		play(e, OverbreakSounds.IRON_CHARGE_RELEASE_BOOM, 0.4F, 1.4F);
		if (stage >= 2) {
			CLEAVES.add(new Cleave(e.position(), e.getYRot(), now, stage));
			Vec3 f = forward(e.getYRot());
			for (int i = 0; i < 6 * stage; i++) {
				double a = Math.toRadians((RANDOM.nextDouble() - 0.5) * 70.0);
				double r = 1.0 + RANDOM.nextDouble() * (range - 1.0);
				Vec3 d = new Vec3(f.x * Math.cos(a) - f.z * Math.sin(a), 0, f.x * Math.sin(a) + f.z * Math.cos(a));
				Vec3 p = e.position().add(d.scale(r));
				mc.level.addParticle(ParticleTypes.CLOUD, p.x, p.y + 0.1, p.z, d.x * 0.1, 0.05, d.z * 0.1);
				mc.level.addParticle(ParticleTypes.CRIT, p.x, p.y + 0.6, p.z, d.x * 0.4, 0.2, d.z * 0.4);
			}
		}
	}

	// ── 역경직 · 흔들림 ──────────────────────────────────

	/** 세기 → 멈추는 시간 (1/20초 단위). 평타 1·2타 0.05초 · 3타 0.08초 · 모아 베기 1단~진 참 0.06~0.12초 · 박치기 0.06초. */
	public static float hitstop(int strength) {
		return switch (strength) {
			case 0 -> 1.0F;
			case 1 -> 1.6F;
			case 2 -> 1.2F;
			case 3 -> 1.6F;
			case 4 -> 2.0F;
			case 5 -> 2.4F;
			case 6 -> 1.2F;
			default -> 1.0F;
		};
	}

	private static int strength(int s) {
		return Mth.clamp(s, 0, 6);
	}

	/**
	 * 화면 흔들림 {세기, 길이(1/20초 단위)} — 스펙 PART 4-3 · 5-3 · 15 의 세기를 {@link #SHAKE_SCALE} 배 한 도(°).
	 * 세기 순서: 평타 1·2타 · 3타 · 모아 베기 1단 · 2단 · 3단 · 진 참 · 박치기.
	 */
	private static final float[][] SHAKE_HIT = {
			{0.15F, 1.6F}, {0.25F, 2.0F}, {0.20F, 2.4F}, {0.30F, 2.4F}, {0.40F, 2.4F}, {0.50F, 2.4F}, {0.30F, 2.0F}};
	/** 맞은 사람 — 평타 0.10 · 0.06초 / 3타 0.18 · 0.08초 (모아 베기 · 박치기는 그보다 조금 세게). */
	private static final float[][] SHAKE_HURT = {
			{0.10F, 1.2F}, {0.18F, 1.6F}, {0.16F, 2.0F}, {0.22F, 2.0F}, {0.28F, 2.0F}, {0.34F, 2.0F}, {0.22F, 1.6F}};
	private static final float SHAKE_SCALE = 2.5F;

	private static void shake(float amp, float len) {
		double now = ClientClock.now();
		// 이미 더 센 흔들림이 도는 중이면 덮어쓰지 않음
		float left = currentShake(now);
		if (left > amp * SHAKE_SCALE) {
			return;
		}
		shakeAt = now;
		shakeAmp = amp * SHAKE_SCALE;
		shakeLen = len;
		shakeSeed = RANDOM.nextInt(1000);
	}

	private static float currentShake(double t) {
		float e = (float) (t - shakeAt);
		if (e < 0.0F || e >= shakeLen) {
			return 0.0F;
		}
		float k = 1.0F - e / shakeLen;
		return shakeAmp * k * k;
	}

	/**
	 * 지금 1인칭 동작이 역경직으로 밀린 시간 (1/20초 단위) — 동작 시간에서 이만큼 빼서 그립니다.
	 * 멈춤: 맞힌 순간부터 stop 동안 칼이 그 자리 (밀린 시간이 실제 시간만큼 늘어남)
	 * 따라잡기: 그 뒤 {@link #CATCH} 동안 밀린 시간이 0 으로 줄어듦 (그만큼 빠르게 재생)
	 *
	 * @param start 그 동작이 시작된 시각 — 역경직이 동작 시작 전이면 0
	 */
	public static float lag(double start, float partial) {
		if (hitAt < start) {
			return 0.0F;
		}
		float e = (float) (ClientClock.at(partial) - hitAt);
		if (e < 0.0F) {
			return 0.0F;
		}
		if (e < stop) {
			return e;
		}
		if (e < stop + CATCH) {
			return stop * (1.0F - (e - stop) / CATCH);
		}
		return 0.0F;
	}

	/** 역경직으로 멈춰 있는가 (1인칭 칼 떨림 · 시험). */
	public static boolean frozen(float partial) {
		float e = (float) (ClientClock.at(partial) - hitAt);
		return e >= 0.0F && e < stop;
	}

	/** 월드 화면 행렬 흔들림 (조준점은 그대로). */
	public static void applyCamera(PoseStack pose, float partial) {
		double t = ClientClock.at(partial);
		float amp = currentShake(t);
		if (amp <= 0.0F) {
			return;
		}
		double scale = Minecraft.getInstance().options.screenEffectScale().get();
		if (scale <= 0.0) {
			return;
		}
		float e = (float) (t - shakeAt);
		float x = (Mth.sin(e * 9.3F + shakeSeed) + 0.5F * Mth.sin(e * 17.1F + shakeSeed * 0.7F)) * amp * (float) scale;
		float y = (Mth.cos(e * 8.1F + shakeSeed * 1.3F) + 0.5F * Mth.sin(e * 15.7F + shakeSeed)) * amp * (float) scale;
		pose.mulPose(Axis.XP.rotationDegrees(x));
		pose.mulPose(Axis.YP.rotationDegrees(y));
	}

	// ── 오오라 ─────────────────────────────────────────────

	/**
	 * 칼날 오오라 색 (ARGB, 0 = 없음) — 모으기 단계 색 · 진 참 창이면 흰빛으로 번쩍 · 천참 모으기는 점점 짙은 빨강.
	 */
	public static int auraColor(int entityId, float partial) {
		double t = ClientClock.at(partial);
		for (Ult u : ULTS) {
			if (u.entityId == entityId) {
				float k = Mth.clamp((float) (t - u.born) / (float) ULT_CHARGE, 0.0F, 1.0F);
				float pulse = 0.75F + 0.25F * Mth.sin((float) t * (1.0F + 2.0F * k));
				return ARGB.color(Math.round((120 + 135 * k) * pulse), 0xFF3B3B);
			}
		}
		Stage s = STAGES.get(entityId);
		if (s == null) {
			return 0;
		}
		float since = (float) (t - s.at);
		float flash = 1.0F - Mth.clamp(since / 4.0F, 0.0F, 1.0F);
		if (s.perfect) {
			boolean white = ((int) (t * 3.0)) % 4 == 0;
			return ARGB.color(255, white ? 0xFFE0E0 : STAGE_COLOR[3]);
		}
		float pulse = 0.8F + 0.2F * Mth.sin((float) t * 0.9F);
		int base = STAGE_COLOR[Math.min(3, s.stage)];
		int alpha = Math.round(Math.min(255.0F, (110 + 40 * s.stage) * pulse + 120 * flash));
		return ARGB.color(alpha, flash > 0.5F ? 0xFFFFFF : base);
	}

	/** 지금 모으기 단계 (0 = 없음) — HUD · 시험. */
	public static int stage(int entityId) {
		Stage s = STAGES.get(entityId);
		return s == null ? 0 : s.stage;
	}

	public static boolean perfect(int entityId) {
		Stage s = STAGES.get(entityId);
		return s != null && s.perfect;
	}

	// ── 매 틱 ────────────────────────────────────────────

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			STAGES.clear();
			ARCS.clear();
			WAVES.clear();
			ULTS.clear();
			CLEAVES.clear();
			PENDING.clear();
			LOOPS.clear();
			GUARDS.clear();
			return;
		}
		GUARDS.removeIf(g -> ClientClock.now() - g.born > GUARD_TIME + 4.0 || mc.level.getEntity(g.entityId) == null);
		LOOPS.values().removeIf(l -> l.isStopped());
		double now = ClientClock.now();
		scrapeSparks(mc, now);
		bashDust(mc);
		List<Pending> due = new ArrayList<>();
		PENDING.removeIf(p -> {
			if (p.at <= now) {
				due.add(p);
				return true;
			}
			return false;
		});
		due.forEach(p -> p.run.run());
		ARCS.removeIf(a -> now - a.born > a.sweep + a.life);
		CLEAVES.removeIf(c -> now - c.born > (c.stage == 5 ? 14.0 : c.stage == SLAM ? 40.0 : 8.0));
		ULTS.removeIf(u -> now - u.born > ULT_CHARGE + 4.0 || mc.level.getEntity(u.entityId) == null);
		STAGES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		for (Wave w : WAVES) {
			stepWave(mc.level, w, now);
		}
		WAVES.removeIf(w -> !Double.isNaN(w.deadAt) && now - w.deadAt > 16.0);
	}

	/** 어깨 박치기 돌진 중 — 발뒤꿈치에서 먼지가 조금씩 뒤로 흩날림. */
	private static void bashDust(Minecraft mc) {
		for (net.minecraft.client.player.AbstractClientPlayer pl : mc.level.players()) {
			if (!kr.overbreak.client.anim.SkillAnims.playing(pl.getId(), SkillAnimPayload.IC_BASH) || !pl.onGround()) {
				continue;
			}
			Vec3 f = forward(pl.getYRot());
			Vec3 at = pl.position().subtract(f.scale(0.3));
			mc.level.addParticle(ParticleTypes.CLOUD, at.x + (RANDOM.nextDouble() - 0.5) * 0.4, at.y + 0.05, at.z + (RANDOM.nextDouble() - 0.5) * 0.4,
					-f.x * 0.08, 0.02, -f.z * 0.08);
		}
	}

	/** 대지 가르기 긁는 동안 (선딜 끝 ~ 발사) — 시전자 오른쪽 앞 땅에서 불꽃 · 흙이 튐. */
	private static void scrapeSparks(Minecraft mc, double now) {
		for (net.minecraft.client.player.AbstractClientPlayer pl : mc.level.players()) {
			kr.overbreak.client.anim.SkillAnims.Play play = kr.overbreak.client.anim.SkillAnims.find(pl.getId(), SkillAnimPayload.IC_REND);
			if (play == null) {
				continue;
			}
			double e = now - play.startTime();
			if (e < REND_WINDUP - 1.0 || e > REND_WINDUP + REND_DRAG) {
				continue;
			}
			Vec3 f = forward(pl.getYRot());
			Vec3 right = new Vec3(-f.z, 0, f.x).scale(-0.5);
			double u = Mth.clamp((e - REND_WINDUP + 1.0) / (REND_DRAG + 1.0), 0.0, 1.0);
			Vec3 at = pl.position().add(right.scale(1.0 - u)).add(f.scale(0.4 + 1.4 * u));
			BlockState below = mc.level.getBlockState(BlockPos.containing(at.x, at.y - 0.01, at.z));
			for (int i = 0; i < 4; i++) {
				mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.05, at.z,
						f.x * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.3, 0.2 + RANDOM.nextDouble() * 0.25, f.z * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.3);
			}
			if (!below.isAir()) {
				for (int i = 0; i < 2; i++) {
					mc.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
							at.x, at.y + 0.1, at.z, f.x * 0.2, 0.3, f.z * 0.2);
				}
			}
		}
	}

	/** 서버 GroundWave 와 같은 규칙 — 1칸 넘는 턱 · 벽 · 절벽이면 소멸. */
	private static void stepWave(ClientLevel level, Wave w, double now) {
		if (!Double.isNaN(w.deadAt)) {
			return;
		}
		double should = Math.min(REND_RANGE, (now - w.born) * REND_SPEED);
		while (w.traveled + 1.0E-6 < should) {
			double step = Math.min(0.5, should - w.traveled);
			Vec3 next = w.pos.add(w.dir.scale(step));
			Vec3 g = ground(level, next, REND_STEP + 0.01);
			if (g == null || Math.abs(g.y - w.pos.y) > REND_STEP + 1.0E-3) {
				w.deadAt = now;
				for (int i = 0; i < 8; i++) {
					level.addParticle(ParticleTypes.CRIT, w.pos.x, w.pos.y + 1.0, w.pos.z,
							(RANDOM.nextDouble() - 0.5) * 0.5, RANDOM.nextDouble() * 0.4, (RANDOM.nextDouble() - 0.5) * 0.5);
				}
				return;
			}
			w.pos = g;
			w.traveled += step;
			w.path.add(g);
			w.pathAt.add(now);
			level.addParticle(ParticleTypes.CRIT, g.x, g.y + 0.3, g.z, w.dir.x * 0.2, 0.15, w.dir.z * 0.2);
			// 불꽃 3 · 흙 2 (칸 반마다)
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.SMALL_FLAME, g.x + (RANDOM.nextDouble() - 0.5) * 0.6, g.y + 0.2 + RANDOM.nextDouble() * 1.2,
						g.z + (RANDOM.nextDouble() - 0.5) * 0.6, w.dir.x * 0.08, 0.05, w.dir.z * 0.08);
			}
			BlockState below = level.getBlockState(BlockPos.containing(g.x, g.y - 0.01, g.z));
			if (!below.isAir()) {
				level.addParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below),
						g.x, g.y + 0.1, g.z, w.dir.x * 0.3, 0.3, w.dir.z * 0.3);
			}
		}
		if (w.traveled >= REND_RANGE - 1.0E-6) {
			w.deadAt = now;
		}
	}

	/** 서버 GroundWave.ground 와 같은 규칙 (클라이언트 월드). */
	static @Nullable Vec3 ground(ClientLevel level, Vec3 pos, double reach) {
		int top = (int) Math.floor(pos.y + Math.min(reach, REND_STEP) + 0.5);
		int bottom = (int) Math.floor(pos.y - reach);
		for (int y = top; y >= bottom; y--) {
			BlockPos bp = BlockPos.containing(pos.x, y, pos.z);
			VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
			if (shape.isEmpty()) {
				continue;
			}
			double surface = bp.getY() + shape.max(Direction.Axis.Y);
			BlockPos above = BlockPos.containing(pos.x, surface + 0.01, pos.z);
			if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty() && above.getY() != bp.getY()) {
				return null;
			}
			return new Vec3(pos.x, surface, pos.z);
		}
		return null;
	}

	// ── 그리기 (월드) ─────────────────────────────────────

	public static void submit(PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized() || mc.level == null
				|| ARCS.isEmpty() && WAVES.isEmpty() && ULTS.isEmpty() && CLEAVES.isEmpty() && GUARDS.isEmpty()) {
			return;
		}
		Vec3 cam = camera.position();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double t = ClientClock.at(partial);
		List<Arc> arcs = List.copyOf(ARCS);
		List<Wave> waves = List.copyOf(WAVES);
		List<Ult> ults = List.copyOf(ULTS);
		List<Cleave> cleaves = List.copyOf(CLEAVES);
		List<GuardTell> guards = List.copyOf(GUARDS);
		ClientLevel level = mc.level;
		collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(FILL, true), (p, buffer) -> {
			for (Arc a : arcs) {
				drawArc(p, buffer, level, a, cam, t, partial);
			}
			for (Wave w : waves) {
				drawWave(p, buffer, w, cam, t);
			}
			for (Ult u : ults) {
				drawUlt(p, buffer, level, u, cam, t, partial);
			}
			for (Cleave c : cleaves) {
				drawCleave(p, buffer, c, cam, t);
			}
			for (GuardTell g : guards) {
				drawGuard(p, buffer, level, g, cam, t, partial);
			}
		});
	}

	/** 부채꼴 빛 띠 — 머리(지금 칼끝 각도) 쪽이 밝고 꼬리로 갈수록 옅음. 시전자를 따라 움직임. */
	private static void drawArc(PoseStack.Pose p, VertexConsumer buffer, ClientLevel level, Arc a, Vec3 cam, double t, float partial) {
		float e = (float) (t - a.born);
		if (e < 0.0F) {
			return;
		}
		Entity ent = level.getEntity(a.entityId);
		Vec3 base = ent != null ? ent.getPosition(partial) : a.fallback;
		float sweepK = a.sweep <= 0.0F ? 1.0F : Mth.clamp(e / a.sweep, 0.0F, 1.0F);
		float fade = 1.0F - Mth.clamp((e - a.sweep) / a.life, 0.0F, 1.0F);
		if (fade <= 0.0F) {
			return;
		}
		float head = Mth.lerp(sweepK, a.from, a.to);
		float span = a.to - a.from;
		// 꼬리는 머리를 따라오며 휘두른 폭의 70% 까지 — 끝나면 꼬리가 머리로 모여 사라짐
		float tailK = Math.max(0.0F, sweepK - 0.7F) + (1.0F - fade) * 0.3F;
		float tail = a.from + span * Math.min(sweepK, tailK);
		int n = 14;
		Vec3 center = base.add(0, a.height, 0);
		for (int i = 0; i < n; i++) {
			float u0 = i / (float) n;
			float u1 = (i + 1) / (float) n;
			float ang0 = Mth.lerp(u0, tail, head);
			float ang1 = Mth.lerp(u1, tail, head);
			int c0 = ARGB.color(Math.round(230 * a.bright * fade * u0), a.color);
			int c1 = ARGB.color(Math.round(230 * a.bright * fade * u1), a.color);
			Vec3 d0 = arcDir(a, ang0);
			Vec3 d1 = arcDir(a, ang1);
			band(p, buffer, center.add(d0.scale(a.outer)), center.add(d1.scale(a.outer)),
					center.add(d1.scale(a.inner)), center.add(d0.scale(a.inner)), cam, c0, c1);
		}
	}

	/** 몸 기준 각도 → 월드 방향. 가로: yaw 둘레 (+ = 오른쪽). 세로: 시선 방향 세로면 (+ = 위). */
	private static Vec3 arcDir(Arc a, float deg) {
		if (a.vertical) {
			Vec3 f = forward(a.yaw);
			double r = Math.toRadians(deg);
			return f.scale(Math.cos(r)).add(0, Math.sin(r), 0);
		}
		return forward(a.yaw + deg);
	}

	/** 대지 가르기 — 지면을 달리는 세로 칼날 + 지나간 자리의 금. */
	private static void drawWave(PoseStack.Pose p, VertexConsumer buffer, Wave w, Vec3 cam, double t) {
		// 금 (지나간 자리 · 1초에 걸쳐 사라짐)
		for (int i = 1; i < w.path.size(); i++) {
			float age = (float) (t - w.pathAt.get(i));
			float k = 1.0F - Mth.clamp(age / 40.0F, 0.0F, 1.0F);
			if (k <= 0.0F) {
				continue;
			}
			Vec3 a = w.path.get(i - 1).add(0, 0.04, 0);
			Vec3 b = w.path.get(i).add(0, 0.04, 0);
			Vec3 side = new Vec3(-w.dir.z, 0, w.dir.x).scale(0.18);
			int c = ARGB.color(Math.round(200 * k), 0xFF7A2A);
			band(p, buffer, a.add(side), b.add(side), b.subtract(side), a.subtract(side), cam, c, c);
		}
		if (!Double.isNaN(w.deadAt)) {
			return;
		}
		// 칼날: 앞으로 기운 지느러미 모양 (진행 방향 세로면) + 가로 빛
		Vec3 base = w.pos;
		Vec3 f = w.dir;
		Vec3 side = new Vec3(-f.z, 0, f.x);
		float flick = 0.9F + 0.1F * Mth.sin((float) t * 3.0F);
		int core = ARGB.color(Math.round(250 * flick), 0xFFF0D0);
		int glow = ARGB.color(Math.round(200 * flick), 0xFF7A2A);
		double h = REND_HEIGHT;
		// 세로 날 두 장 (진행 방향으로 선 면 · 가로로 선 면) — 어느 쪽에서 봐도 보이게
		band(p, buffer, base.add(f.scale(0.5)), base.add(f.scale(-0.2)).add(0, h, 0), base.add(f.scale(-0.9)).add(0, h * 0.5, 0),
				base.add(f.scale(-0.6)), cam, glow, core);
		band(p, buffer, base.add(side.scale(REND_WIDTH / 2.0)), base.add(side.scale(REND_WIDTH / 4.0)).add(0, h * 0.8, 0),
				base.subtract(side.scale(REND_WIDTH / 4.0)).add(0, h * 0.8, 0), base.subtract(side.scale(REND_WIDTH / 2.0)), cam, glow, core);
		// 흰 가장자리 — 칼날 등을 따라 가는 빛줄기
		BulletTrails.quad(p, buffer, base.add(f.scale(0.5)), base.add(f.scale(-0.2)).add(0, h, 0), cam, 0.06F, 0.0F,
				ARGB.color(Math.round(230 * flick), 0xFFFFFF), 0.0F);
	}

	/** 천참 예고 — 바닥의 붉은 직사각형 (테두리 + 모으는 만큼 앞으로 차오르는 채움) · 머리 위로 자라는 기운의 칼날. */
	private static void drawUlt(PoseStack.Pose p, VertexConsumer buffer, ClientLevel level, Ult u, Vec3 cam, double t, float partial) {
		float k = Mth.clamp((float) (t - u.born) / (float) ULT_CHARGE, 0.0F, 1.0F);
		Vec3 f = forward(u.yaw);
		Vec3 side = new Vec3(-f.z, 0, f.x).scale(ULT_WIDTH / 2.0);
		Vec3 o = u.at.add(0, 0.06, 0);
		Vec3 end = o.add(f.scale(ULT_LENGTH));
		float pulse = 0.7F + 0.3F * Mth.sin((float) t * (0.8F + 2.5F * k));
		int edge = ARGB.color(Math.round(230 * pulse), 0xFF3B3B);
		int fill = ARGB.color(Math.round((60 + 80 * k) * pulse), 0xFF2020);
		// 채움 (모은 만큼 앞으로)
		Vec3 mid = o.add(f.scale(ULT_LENGTH * k));
		band(p, buffer, o.add(side), mid.add(side), mid.subtract(side), o.subtract(side), cam, fill, fill);
		// 테두리
		double lw = 0.08;
		Vec3 sn = side.normalize().scale(lw);
		Vec3 fn = f.scale(lw);
		band(p, buffer, o.add(side).add(sn), end.add(side).add(sn), end.add(side).subtract(sn), o.add(side).subtract(sn), cam, edge, edge);
		band(p, buffer, o.subtract(side).add(sn), end.subtract(side).add(sn), end.subtract(side).subtract(sn), o.subtract(side).subtract(sn), cam, edge, edge);
		band(p, buffer, end.add(side).add(fn), end.subtract(side).add(fn), end.subtract(side).subtract(fn), end.add(side).subtract(fn), cam, edge, edge);
		band(p, buffer, o.add(side).add(fn), o.subtract(side).add(fn), o.subtract(side).subtract(fn), o.add(side).subtract(fn), cam, edge, edge);
		// 머리 위로 자라는 기운의 칼날 (1인칭 본인은 화면을 가리므로 짧게)
		Entity e = level.getEntity(u.entityId);
		if (e != null) {
			Vec3 feet = e.getPosition(partial);
			boolean own = e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
			double len = (own ? 2.0 : 5.5) * (0.25 + 0.75 * k);
			Vec3 a = feet.add(0, 2.0, 0);
			Vec3 b = a.add(0, len, 0);
			BulletTrails.quad(p, buffer, a, b, cam, 0.35F + 0.15F * k, 0.0F, ARGB.color(Math.round(150 * pulse), 0xFF3B3B), 0.1F);
			BulletTrails.quad(p, buffer, a, b, cam, 0.12F, 0.0F, ARGB.color(Math.round(230 * pulse), 0xFFE0E0), 0.1F);
		}
	}

	/** 천참 베기 (14칸 세로 빛의 칼날 · 사라지며 가라앉음) · 모아 베기 2단 이상 바닥 충격. */
	private static void drawCleave(PoseStack.Pose p, VertexConsumer buffer, Cleave c, Vec3 cam, double t) {
		float e = (float) (t - c.born);
		Vec3 f = forward(c.yaw);
		if (c.stage == SLAM) {
			drawSlam(p, buffer, c, cam, e);
			return;
		}
		if (c.stage == 5) {
			float k = 1.0F - Mth.clamp(e / 14.0F, 0.0F, 1.0F);
			// 칼날은 순식간에 내려와(0.1초) 땅에 박힌 채 옅어짐
			float drop = Mth.clamp(e / 2.0F, 0.0F, 1.0F);
			double top = 6.0 - 5.0 * drop;
			Vec3 o = c.at.add(0, 0.05, 0);
			Vec3 end = o.add(f.scale(ULT_LENGTH));
			int core = ARGB.color(Math.round(255 * k), 0xFFFFFF);
			int glow = ARGB.color(Math.round(200 * k), 0xFF3B3B);
			band(p, buffer, o.add(0, top, 0), end.add(0, top, 0), end, o, cam, glow, core);
			Vec3 side = new Vec3(-f.z, 0, f.x).scale(ULT_WIDTH / 2.0 * (0.4 + 0.6 * k));
			band(p, buffer, o.add(side), end.add(side), end.subtract(side), o.subtract(side), cam, glow, glow);
			return;
		}
		// 모아 베기 — 발밑에서 앞으로 퍼지는 충격 고리 (단계 색)
		float k = 1.0F - Mth.clamp(e / 8.0F, 0.0F, 1.0F);
		int color = c.stage >= 4 ? 0xFFFFFF : STAGE_COLOR[c.stage];
		double r0 = 0.5 + e * 0.6;
		double r1 = r0 + 0.4;
		int n = 10;
		Vec3 o = c.at.add(0, 0.08, 0);
		for (int i = 0; i < n; i++) {
			float a0 = -45.0F + 90.0F * i / n;
			float a1 = -45.0F + 90.0F * (i + 1) / n;
			Vec3 d0 = forward(c.yaw + a0);
			Vec3 d1 = forward(c.yaw + a1);
			int col = ARGB.color(Math.round(200 * k), color);
			band(p, buffer, o.add(d0.scale(r1)), o.add(d1.scale(r1)), o.add(d1.scale(r0)), o.add(d0.scale(r0)), cam, col, col);
		}
	}

	/** 검막 알림 색 (금빛 · 흰 코어). */
	private static final int GUARD_GOLD = 0xFFD84A;
	/** 번쩍임 길이 (0.25초). */
	private static final float FLASH = 5.0F;

	/**
	 * 상대의 검막 (적 화면에만) —
	 *   쓰는 순간: 가슴 앞에서 금빛 · 흰 십자 광채가 크게 번쩍 (0.25초에 걸쳐 커지며 사라짐)
	 *   막는 동안: 몸 앞 120° 에 선 금빛 반투명 방벽 (몸 방향을 따라 돎 · 윗단 · 아랫단이 밝게 · 끝날 때 사라짐)
	 */
	private static void drawGuard(PoseStack.Pose p, VertexConsumer buffer, ClientLevel level, GuardTell g, Vec3 cam, double t, float partial) {
		Entity e = level.getEntity(g.entityId);
		if (e == null) {
			return;
		}
		float age = (float) (t - g.born);
		boolean on = kr.overbreak.client.anim.SkillAnims.playing(g.entityId, SkillAnimPayload.IC_GUARD);
		Vec3 feet = e.getPosition(partial);
		float yaw = e.getViewYRot(partial);
		Vec3 f = forward(yaw);
		Vec3 right = new Vec3(-f.z, 0, f.x).scale(-1.0);
		// 번쩍임
		if (age < FLASH) {
			float k = age / FLASH;
			float fade = 1.0F - k;
			fade = fade * fade;
			Vec3 c = feet.add(f.scale(0.7)).add(0, 1.3, 0);
			double len = 0.8 + 2.6 * Math.sqrt(k);
			Vec3 up = new Vec3(0, 1, 0);
			int gold = ARGB.color(Math.round(230 * fade), GUARD_GOLD);
			int white = ARGB.color(Math.round(255 * fade), 0xFFFFFF);
			BulletTrails.quad(p, buffer, c.subtract(right.scale(len)), c.add(right.scale(len)), cam, 0.22F * fade + 0.04F, 0.0F, gold, 0.0F);
			BulletTrails.quad(p, buffer, c.subtract(up.scale(len * 0.8)), c.add(up.scale(len * 0.8)), cam, 0.22F * fade + 0.04F, 0.0F, gold, 0.0F);
			BulletTrails.quad(p, buffer, c.subtract(right.scale(len * 0.9)), c.add(right.scale(len * 0.9)), cam, 0.08F, 0.0F, white, 0.0F);
			BulletTrails.quad(p, buffer, c.subtract(up.scale(len * 0.7)), c.add(up.scale(len * 0.7)), cam, 0.08F, 0.0F, white, 0.0F);
			// 가운데 빛 덩이
			BulletTrails.quad(p, buffer, c.subtract(right.scale(0.4)), c.add(right.scale(0.4)), cam, 0.42F * (0.5F + fade), 0.0F,
					ARGB.color(Math.round(200 * fade), 0xFFF4C8), 0.0F);
		}
		// 방벽 — 들어갈 때 0.1초 · 끝나면 0.15초에 걸쳐 사라짐
		float in = Mth.clamp(age / 2.0F, 0.0F, 1.0F);
		float out;
		if (on) {
			out = 1.0F;
		} else {
			kr.overbreak.client.anim.SkillAnims.Play play = kr.overbreak.client.anim.SkillAnims.find(g.entityId, SkillAnimPayload.IC_GUARD);
			float over = play == null ? 99.0F : play.elapsed(partial) - play.end();
			out = 1.0F - Mth.clamp(over / 3.0F, 0.0F, 1.0F);
		}
		float k = Math.min(in, out);
		if (k <= 0.0F) {
			return;
		}
		float pulse = 0.8F + 0.2F * Mth.sin((float) t * 1.6F);
		int wall = ARGB.color(Math.round(80 * k * pulse), GUARD_GOLD);
		int rim = ARGB.color(Math.round(220 * k), GUARD_GOLD);
		double r = 1.05;
		double h0 = 0.1;
		double h1 = 2.1;
		int n = 8;
		double half = Math.toRadians(GUARD_FRONT / 2.0);
		for (int i = 0; i < n; i++) {
			double a0 = -half + 2.0 * half * i / n;
			double a1 = -half + 2.0 * half * (i + 1) / n;
			Vec3 d0 = f.scale(Math.cos(a0)).add(right.scale(Math.sin(a0)));
			Vec3 d1 = f.scale(Math.cos(a1)).add(right.scale(Math.sin(a1)));
			Vec3 b0 = feet.add(d0.scale(r)).add(0, h0, 0);
			Vec3 b1 = feet.add(d1.scale(r)).add(0, h0, 0);
			Vec3 t0 = feet.add(d0.scale(r)).add(0, h1, 0);
			Vec3 t1 = feet.add(d1.scale(r)).add(0, h1, 0);
			band(p, buffer, t0, t1, b1, b0, cam, wall, wall);
			// 윗단 · 아랫단 빛줄
			band(p, buffer, t0, t1, t1.add(0, -0.08, 0), t0.add(0, -0.08, 0), cam, rim, rim);
			band(p, buffer, b0.add(0, 0.08, 0), b1.add(0, 0.08, 0), b1, b0, cam, rim, rim);
		}
	}

	/** 어깨 박치기 돌진 시야각 — 0.05초에 ×1.15 로 넓어졌다가 끝나면 0.15초에 걸쳐 원래대로 (설정의 시야각 효과 세기를 따름). */
	private static final float BASH_FOV = 1.15F;

	public static float fovScale(float partial) {
		float k = bashAmount(partial);
		if (k <= 0.0F) {
			return 1.0F;
		}
		double effect = Minecraft.getInstance().options.fovEffectScale().get();
		return (float) (1.0 + (BASH_FOV - 1.0) * k * effect);
	}

	/** 지금 내 박치기 세기 0~1 (들어감 0.05초 · 빠짐 0.15초). */
	private static float bashAmount(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return 0.0F;
		}
		kr.overbreak.client.anim.SkillAnims.Play play = kr.overbreak.client.anim.SkillAnims.find(mc.player.getId(), SkillAnimPayload.IC_BASH);
		if (play == null) {
			return 0.0F;
		}
		float e = play.elapsed(partial);
		float in = Mth.clamp(e / 1.0F, 0.0F, 1.0F);
		float out = 1.0F - Mth.clamp((e - play.end()) / 3.0F, 0.0F, 1.0F);
		float k = Math.min(in, out);
		return k * k * (3.0F - 2.0F * k);
	}

	/** 박치기 돌진 속도선 — 화면 가장자리에서 가운데 쪽으로 흐르는 옅은 흰 줄 (본인 1인칭 · 은은하게). */
	private static void speedLines(net.minecraft.client.gui.GuiGraphicsExtractor g, int w, int h, double t, float k) {
		int n = 18;
		float cx = w / 2.0F;
		float cy = h / 2.0F;
		RandomSource r = RandomSource.create(7L);
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2.0 * i / n + (r.nextDouble() - 0.5) * 0.25;
			float speed = 0.6F + r.nextFloat() * 0.6F;
			// 가장자리 → 안쪽으로 흐름 (반지름 비율 1.0 → 0.55)
			float phase = (float) ((t * 0.18 * speed + r.nextDouble()) % 1.0);
			float r1 = 1.05F - 0.45F * phase;
			float r0 = r1 + 0.22F;
			float ex = (float) Math.cos(ang);
			float ey = (float) Math.sin(ang);
			float rx = Math.max(w, h) * 0.62F;
			int alpha = Math.round(110 * k * (1.0F - phase * 0.7F));
			if (alpha <= 2) {
				continue;
			}
			int color = (alpha << 24) | 0xFFFFFF;
			int steps = 48;
			for (int s = 0; s < steps; s++) {
				float rr = r0 + (r1 - r0) * s / steps;
				int x = Math.round(cx + ex * rx * rr);
				int y = Math.round(cy + ey * rx * rr);
				g.fill(x, y, x + 2, y + 2, color);
			}
		}
	}

	/** 3타 착지 — 빠르게 퍼지는 흰 충격 고리 두 겹 + 사방으로 갈라진 땅 균열 (2초에 걸쳐 사라짐). */
	private static void drawSlam(PoseStack.Pose p, VertexConsumer buffer, Cleave c, Vec3 cam, float e) {
		Vec3 o = c.at.add(0, 0.05, 0);
		int n = 24;
		for (int ring = 0; ring < 2; ring++) {
			float life = ring == 0 ? 5.0F : 8.0F;
			float k = 1.0F - Mth.clamp(e / life, 0.0F, 1.0F);
			if (k <= 0.0F) {
				continue;
			}
			float grow = 1.0F - (1.0F - Mth.clamp(e / life, 0.0F, 1.0F)) * (1.0F - Mth.clamp(e / life, 0.0F, 1.0F));
			double r1 = 0.4 + (ring == 0 ? 2.4 : 3.4) * grow;
			double r0 = r1 - (ring == 0 ? 0.45 : 0.25) * (0.4 + 0.6 * k);
			int col = ARGB.color(Math.round((ring == 0 ? 240 : 150) * k), ring == 0 ? 0xFFFFFF : 0xFFE2B0);
			for (int i = 0; i < n; i++) {
				double a0 = Math.PI * 2.0 * i / n;
				double a1 = Math.PI * 2.0 * (i + 1) / n;
				Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0));
				Vec3 d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
				band(p, buffer, o.add(d0.scale(r1)), o.add(d1.scale(r1)), o.add(d1.scale(r0)), o.add(d0.scale(r0)), cam, col, col);
			}
		}
		// 균열 — 칼끝 자리에서 사방으로 (앞쪽이 가장 길게)
		float crack = 1.0F - Mth.clamp((e - 20.0F) / 20.0F, 0.0F, 1.0F);
		float reach = Mth.clamp(e / 2.0F, 0.0F, 1.0F);
		RandomSource r = RandomSource.create((long) (c.born * 1000));
		for (int i = 0; i < 7; i++) {
			double ang = Math.toRadians(c.yaw) + (i - 3) * 0.55 + (r.nextDouble() - 0.5) * 0.3;
			Vec3 dir = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
			double len = (i == 3 ? 2.6 : 1.2 + r.nextDouble() * 0.9) * reach;
			Vec3 mid = o.add(dir.scale(len * 0.5)).add(new Vec3(-dir.z, 0, dir.x).scale((r.nextDouble() - 0.5) * 0.3));
			Vec3 end = o.add(dir.scale(len));
			int dark = ARGB.color(Math.round(210 * crack), 0x22160E);
			int hot = ARGB.color(Math.round(200 * crack * Math.max(0.0F, 1.0F - e / 12.0F)), 0xFF8A3A);
			crackLine(p, buffer, o, mid, cam, 0.07, dark);
			crackLine(p, buffer, mid, end, cam, 0.05, dark);
			if (hot != 0) {
				crackLine(p, buffer, o.add(0, 0.01, 0), mid.add(0, 0.01, 0), cam, 0.03, hot);
			}
		}
	}

	private static void crackLine(PoseStack.Pose p, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 cam, double half, int color) {
		Vec3 d = b.subtract(a);
		Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(half);
		band(p, buffer, a.add(side), b.add(side), b.subtract(side), a.subtract(side), cam, color, color);
	}

	/**
	 * 네 꼭짓점 면 한 장 (앞뒤 두 번). a→b 가 바깥 가장자리, d→c 가 안쪽 — 텍스처 v 가 가장자리 0 → 안쪽 1 이라 가운데가 밝음.
	 * ca = a·d 쪽 색, cb = b·c 쪽 색.
	 */
	private static void band(PoseStack.Pose p, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 cam, int ca, int cb) {
		Vec3 ra = a.subtract(cam);
		Vec3 rb = b.subtract(cam);
		Vec3 rc = c.subtract(cam);
		Vec3 rd = d.subtract(cam);
		vertex(p, buffer, ra, ca, 0.3F, 0.0F);
		vertex(p, buffer, rd, ca, 0.3F, 1.0F);
		vertex(p, buffer, rc, cb, 1.0F, 1.0F);
		vertex(p, buffer, rb, cb, 1.0F, 0.0F);
		vertex(p, buffer, rb, cb, 1.0F, 0.0F);
		vertex(p, buffer, rc, cb, 1.0F, 1.0F);
		vertex(p, buffer, rd, ca, 0.3F, 1.0F);
		vertex(p, buffer, ra, ca, 0.3F, 0.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int color, float u, float v) {
		buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	// ── 화면 (1인칭 본인) ──────────────────────────────────

	/**
	 * 모으는 동안 화면 가장자리가 단계 색으로 옅게 물듦 (단계가 오른 순간 번쩍 · 진 참 창은 흰빛).
	 */
	public static void hud(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !kr.overbreak.client.input.InputMode.active()) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		double t = ClientClock.at(partial);
		int w = g.guiWidth();
		int h = g.guiHeight();
		float bash = bashAmount(partial);
		if (bash > 0.0F && mc.options.getCameraType().isFirstPerson()) {
			speedLines(g, w, h, t, bash);
		}
		Stage s = STAGES.get(mc.player.getId());
		if (s != null) {
			float flash = 1.0F - Mth.clamp((float) (t - stageFlashAt) / 5.0F, 0.0F, 1.0F);
			int rgb = s.perfect ? 0xFFFFFF : STAGE_COLOR[Math.min(3, s.stage)];
			float alpha = (s.perfect ? 120.0F : 30.0F + 22.0F * s.stage) * (0.85F + 0.15F * Mth.sin((float) t * 1.2F)) + 90.0F * flash;
			edge(g, w, h, rgb, alpha);
		}
	}

	private static void edge(net.minecraft.client.gui.GuiGraphicsExtractor g, int w, int h, int rgb, float alpha) {
		int bands = 10;
		int depth = Math.min(w, h) / 6;
		for (int i = 0; i < bands; i++) {
			float s = 1.0F - i / (float) bands;
			int color = (Mth.clamp(Math.round(alpha * s * s), 0, 255) << 24) | rgb;
			int a = i * depth / bands;
			int b = (i + 1) * depth / bands;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}
	}

	// ── 반복음 ───────────────────────────────────────────

	/**
	 * 따라다니는 반복음 — where 가 null 을 돌려주면 0.1초(2) 에 걸쳐 줄어들고 멈춤.
	 * 모으기 반복음 (시전자를 따라감 · 단계마다 피치) · 대지 가르기 칼날 질주음 (칼날을 따라감).
	 */
	private static final class Loop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
		private final java.util.function.Supplier<@Nullable Vec3> where;
		private final float base;
		java.util.function.Supplier<Float> pitchFor;
		private float fade = 1.0F;

		Loop(SoundEvent sound, float volume, float pitch, java.util.function.Supplier<@Nullable Vec3> where) {
			super(sound, SoundSource.PLAYERS, RANDOM);
			this.where = where;
			this.base = volume;
			this.volume = volume;
			this.pitch = pitch;
			this.looping = true;
			this.delay = 0;
			Vec3 at = where.get();
			if (at != null) {
				this.x = at.x;
				this.y = at.y;
				this.z = at.z;
			}
		}

		@Override
		public void tick() {
			Vec3 at = where.get();
			if (at == null) {
				// 0.1초 페이드아웃 (클라이언트 틱 = 1/20초)
				fade -= 0.5F;
				if (fade <= 0.0F) {
					stop();
					return;
				}
			} else {
				this.x = at.x;
				this.y = at.y;
				this.z = at.z;
			}
			this.volume = base * Math.max(0.0F, fade);
			if (pitchFor != null) {
				this.pitch = pitchFor.get();
			}
		}
	}

	// ── 공통 ─────────────────────────────────────────────

	/** 단계가 오른 순간 칼 둘레로 튀는 빛 가루. */
	private static void burst(ClientLevel level, Entity e, int stage, boolean perfect) {
		Vec3 at = e.position().add(0, 1.2, 0);
		int n = perfect ? 24 : stage >= 3 ? 16 : stage == 2 ? 10 : 6;
		for (int i = 0; i < n; i++) {
			level.addParticle(perfect ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
					(RANDOM.nextDouble() - 0.5) * 0.5, (RANDOM.nextDouble() - 0.2) * 0.4, (RANDOM.nextDouble() - 0.5) * 0.5);
		}
	}

	static Vec3 forward(float yaw) {
		double y = Math.toRadians(yaw);
		return new Vec3(-Math.sin(y), 0, Math.cos(y));
	}

	private static void play(Entity e, Holder<SoundEvent> sound, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.EntityBoundSoundInstance(
				sound.value(), SoundSource.PLAYERS, volume, pitch, e, RANDOM.nextLong()));
	}

	private static void playAt(Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.value(), SoundSource.PLAYERS, volume, pitch,
				RANDOM, at.x, at.y, at.z));
	}

	/** 시험용: 대지 가르기 칼날이 지금 어디 있는가 (없으면 null). */
	public static @Nullable Vec3 wavePos() {
		for (Wave w : WAVES) {
			if (Double.isNaN(w.deadAt)) {
				return w.pos;
			}
		}
		return null;
	}

	/** 시험용: 그리는 중인 검막 알림 수 (적 화면에만). */
	public static int guardTells() {
		return GUARDS.size();
	}

	/** 시험용: 그리는 중인 것 수 (궤적 · 칼날 · 예고 · 베기). */
	public static int active() {
		return ARCS.size() + WAVES.size() + ULTS.size() + CLEAVES.size();
	}
}
