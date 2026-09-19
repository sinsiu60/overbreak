package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.combat.Motion;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.input.InputRouter;
import kr.overbreak.item.SkillItems;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 직업 · 셰이드 (근접 암살형) — 모드 창작 직업 (데이터팩에 없음).
 *
 *   체력 180 (-20) · 공격력 28 (검 +27) · 공격속도 1.25 · 이동속도 +15% · 평타 앞 80도 부채꼴
 *   빠르고 약한 근접 — 낙인을 찍고 평타로 터뜨리는 연계로 한 명을 몰아 잡고, 회피 반격 · 표창 걸음으로 빠져나옴
 *
 * 밸런스 기준 (다른 근접 직업과 비교):
 *   평타 초당 35 (워리어 36 · 햄머나이트 36) — 대신 부채꼴이 좁고 체력이 가장 낮은 근접
 *   한 명 폭딜: 표창 20 → 걸음 → 평타 28 + 낙인 25 → 가르기 30 → 평타 28 + 낙인 25 = 약 156 / 2.5초
 *     → 보안관(170) 도 한 연계로는 못 잡고, 스킬 두 개(8초 쿨)를 모두 써야 함
 *   궁극기 천검난무는 한 명에게 150 · 여러 명이면 나뉘어 약해짐. 베는 동안 무적이라 피하는 용도로도 씀
 *   약점: 섬광 봉인 · 균열 지대에서 가르기 · 걸음 · 반격 이동이 막힘, 기절 한 번에 180 체력이 쉽게 녹음
 */
public final class Shade implements PvpClass {
	public static final String ID = "shade";
	static final String REND = "sd_rend";
	static final String EVADE = "sd_evade";
	static final String KUNAI = "sd_kunai";
	static final int EVADE_COOLDOWN = 200;
	static final int EVADE_TICKS = 14;
	static final double COUNTER_RANGE = 16.0;
	static final int COUNTER_GRACE = 6;
	private static final Map<String, Integer> TOTALS = Map.of(REND, ShadowRend.COOLDOWN, EVADE, EVADE_COOLDOWN, KUNAI, ShadowKunai.COOLDOWN);
	private static final Identifier BLADE_DAMAGE = Overbreak.id("shade_blade_damage");

	static {
		// 잔영 회피 · 천검난무 · 그 직후에는 피해 자체를 받지 않음 (넉백 · 경직도 없음)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !absorb(entity, source));
		// 적을 처치하면 그림자 가르기 쿨타임 초기화
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer sp && sp != entity && stateOrNull(sp) != null) {
				PlayerProfile prof = Attachments.profile(sp);
				if (prof.cooldown(REND) > 0) {
					prof.setCooldown(REND, 0);
					Fx.sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 0.8F);
					prof.msgT = 30;
					Hud.actionbar(sp, Hud.bold("처치 — 그림자 가르기 초기화", ChatFormatting.LIGHT_PURPLE));
				}
			}
		});
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("셰이드", ChatFormatting.DARK_PURPLE);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new ShadeState();
		Classes.baseStats(p, -20, 0.15, 125);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("셰이드", ChatFormatting.DARK_PURPLE))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		ShadeState st = stateOrNull(p);
		if (st != null) {
			st.marks.clear();
			st.evadeT = 0;
			st.graceT = 0;
			st.stepT = 0;
			st.stepTarget = null;
		}
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, blade());
		inv.setItem(9, SkillItems.statSheet(Hud.bold("셰이드", ChatFormatting.DARK_PURPLE), SkillItems.lore()
				.line("근접 암살형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      180  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    28", ChatFormatting.GRAY)
				.line("공격속도  1.25  (0.8초에 1회)", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 +15%", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	private static ItemStack blade() {
		ItemStack s = SkillItems.skill("overbreak:shade_blade", SkillItems.name("그림자 검", ChatFormatting.DARK_PURPLE), REND,
				SkillItems.lore()
						.line("직업 · 셰이드", ChatFormatting.DARK_GRAY).blank()
						.line("공격력 28 · 공격속도 1.25", ChatFormatting.GRAY).blank()
						.bold("[패시브] 그림자 낙인", ChatFormatting.LIGHT_PURPLE)
						.line(" 스킬에 맞은 적에게 4초 낙인. 낙인 찍힌 적을 평타로 베면 30 추가 피해.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 그림자 가르기", ChatFormatting.DARK_PURPLE)
						.line(" 7.5칸 돌진하며 지나간 적에게 30 · 낙인. 처치하면 초기화. 쿨타임 8초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 잔영 회피", ChatFormatting.DARK_PURPLE)
						.line(" 0.7초 피해 무효. 그동안 때린 적의 등 뒤로 이동 · 낙인 · 가르기 초기화. 쿨타임 10초", ChatFormatting.GRAY).blank()
						.bold("[F] 그림자 표창", ChatFormatting.DARK_PURPLE)
						.line(" 16칸 표창 30 (머리 60) · 둔화 · 낙인. 3초 안에 다시 F = 그 적 등 뒤로. 쿨타임 8초", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build());
		s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BLADE_DAMAGE, 27.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build());
		return s;
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("echo_shard", Hud.bold("천검난무", ChatFormatting.DARK_PURPLE), SkillItems.lore()
				.line("궁극기 · 셰이드", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 천검난무", ChatFormatting.DARK_PURPLE)
				.line(" 반경 10칸의 적 최대 4명을 번갈아 순간이동하며 6번 벱니다.", ChatFormatting.GRAY)
				.line(" 한 번에 25 · 낙인. 혼자 남은 적은 6번 모두 맞습니다.", ChatFormatting.GRAY)
				.line(" 베는 1.2초 동안 피해 · 군중 제어를 받지 않습니다.", ChatFormatting.GRAY).blank()
				.line(" 주위에 적이 없으면 발동하지 않습니다.", ChatFormatting.DARK_GRAY).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void primary(ServerPlayer p) {
		ShadowRend.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		ShadeState st = state(p);
		if (st.storm != null || st.evadeT > 0 || Cooldowns.blocked(p, EVADE, "잔영 회피", ChatFormatting.DARK_PURPLE)) {
			return;
		}
		Attachments.profile(p).setCooldown(EVADE, EVADE_COOLDOWN);
		st.evadeT = Ticks.of(EVADE_TICKS);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SD_EVADE, -1);
		Fx.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.4F);
		Fx.particleExcept(p.level(), p, Fx.dust(ShadowMark.PURPLE, 1.5F), p.getX(), p.getY() + 1.0, p.getZ(), 25, 0.35, 0.6, 0.35, 0);
	}

	@Override
	public void tertiary(ServerPlayer p) {
		ShadowKunai.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		BladeStorm.cast(p, state(p));
	}

	/** 천검난무 중에는 아무 입력도 받지 않습니다. */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		return state(p).storm != null;
	}

	@Override
	public void onMeleeHit(ServerPlayer p, LivingEntity target) {
		ShadowMark.burst(p, state(p), target);
	}

	@Override
	public double meleeArcDegrees() {
		return 80.0;
	}

	@Override
	public boolean meleeClearsInvulnerability() {
		return true;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		ShadeState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(REND), ShadowRend.COOLDOWN, st.rend != null),
				new SkillSlot(prof.cooldown(EVADE), EVADE_COOLDOWN, st.evadeT > 0),
				new SkillSlot(prof.cooldown(KUNAI), ShadowKunai.COOLDOWN, st.stepT > 0));
	}

	/** 조준점 아래: 천검난무 남은 시간 · 그림자 걸음을 쓸 수 있는 남은 시간 (금색 게이지). */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		ShadeState st = state(p);
		if (st.storm != null) {
			return new HudExtra(-1, 0, st.storm.remainingPercent(), HudExtra.METER_DURATION);
		}
		if (st.stepT > 0) {
			return new HudExtra(-1, 0, st.stepT * 100 / Ticks.of(ShadowKunai.STEP_WINDOW), HudExtra.METER_DURATION);
		}
		return HudExtra.NONE;
	}

	@Override
	public void tick(ServerPlayer p) {
		ShadeState st = state(p);
		Cooldowns.tick(p, TOTALS);
		ShadowMark.tick(st);
		if (st.evadeT > 0) {
			st.evadeT--;
			if (Ticks.ambient()) Fx.particleExcept(p.level(), p, Fx.dust(ShadowMark.PURPLE, 1.0F), p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.6, 0.3, 0);
		}
		if (st.graceT > 0) {
			st.graceT--;
		}
		if (st.stepT > 0 && --st.stepT == 0) {
			st.stepTarget = null;
		}
	}

	// ── 공통 ────────────────────────────────────────────────

	/** 피해를 받지 않는 상태면 true — 잔영 회피 중이면 반격까지 합니다. */
	public static boolean absorb(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer sp) || !(Attachments.profile(sp).classState instanceof ShadeState st)) {
			return false;
		}
		if (st.storm != null || st.graceT > 0) {
			return true;
		}
		if (st.evadeT <= 0) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != sp && attacker.isAlive()
				&& attacker.level() == sp.level() && attacker.distanceTo(sp) <= COUNTER_RANGE) {
			counter(sp, st, attacker);
		}
		return true;
	}

	/** 잔영 반격 — 공격자 등 뒤로 (봉인 중이면 그 자리) · 낙인 · 그림자 가르기 초기화. */
	private static void counter(ServerPlayer p, ShadeState st, LivingEntity attacker) {
		st.evadeT = 0;
		st.graceT = Ticks.of(COUNTER_GRACE);
		ServerLevel level = p.level();
		Fx.particleExcept(level, p, Fx.dust(ShadowMark.PURPLE, 1.8F), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.3, 0.6, 0.3, 0);
		if (Attachments.combatant(p).sealT <= 0) {
			blinkBehind(p, attacker, 0.0);
		}
		ShadowMark.apply(st, attacker);
		Attachments.profile(p).setCooldown(REND, 0);
		// 반격에 성공하면 잔영 회피 쿨타임을 절반 돌려받습니다 (0.1f)
		PlayerProfile prof = Attachments.profile(p);
		prof.setCooldownTicks(EVADE, prof.cooldown(EVADE) / 2);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SD_STRIKE, -1);
		Fx.sound(p, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.sound(p, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 2.0F);
		Attachments.profile(p).msgT = 30;
		Hud.actionbar(p, Hud.bold("반격 — 그림자 가르기 초기화", ChatFormatting.LIGHT_PURPLE));
	}

	/**
	 * 대상의 등 뒤 1.3칸(angleDeg 만큼 대상 둘레를 돈 자리)으로 순간이동해 대상을 바라봅니다.
	 * 그 자리가 막혀 있으면 더 가까운 자리 · 대상 자리 순으로 찾습니다.
	 */
	static void blinkBehind(ServerPlayer p, LivingEntity target, double angleDeg) {
		ServerLevel level = p.level();
		Fx.particleExcept(level, p, ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		Fx.particleExcept(level, p, ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
		Vec3 base = target.position();
		Vec3 dest = behindPoint(p, target, angleDeg);
		Vec3 look = base.subtract(dest);
		float yaw = look.horizontalDistanceSqr() < 1.0E-4 ? p.getYRot() : Local.yawPitch(look)[0];
		Motion.brake(p);
		p.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, 10.0F, true);
		p.resetFallDistance();
		Fx.particleExcept(level, p, ParticleTypes.PORTAL, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.6, 0.3, 0.2);
	}

	/** 대상 등 뒤 1.3칸 (막혀 있으면 0.8칸, 그것도 막히면 대상 자리). */
	static Vec3 behindPoint(ServerPlayer p, LivingEntity target, double angleDeg) {
		Vec3 base = target.position();
		for (double dist : new double[] {1.3, 0.8}) {
			Vec3 at = Local.flat(base, target.getYRot() + (float) angleDeg, 0.0, 0.0, -dist);
			if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
				return at;
			}
		}
		return base;
	}

	/** 이동기 봉인 중이면 안내하고 true. */
	static boolean sealed(ServerPlayer p) {
		if (Attachments.combatant(p).sealT <= 0) {
			return false;
		}
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(p).msgT = 30;
		Hud.actionbar(p, Hud.text("이동기가 봉인되어 있다", ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	public static ShadeState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof ShadeState)) {
			prof.classState = new ShadeState();
		}
		return prof.state();
	}

	static @Nullable ShadeState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof ShadeState st ? st : null;
	}

	/** 시험용 수치. */
	public static final int REND_DAMAGE = ShadowRend.DAMAGE_10 / 10;
	public static final int MARK_DAMAGE = ShadowMark.BURST_10 / 10;
	public static final int KUNAI_DAMAGE = ShadowKunai.DAMAGE_10 / 10;
	public static final int STORM_DAMAGE = BladeStorm.DAMAGE_10 / 10;

	private static final ClassInfo INFO = new ClassInfo("셰이드", "근접 암살형", 0x9B6BFF,
			"낙인을 찍고 평타로 터뜨려 한 명을 몰아 잡는 빠르고 약한 암살자.",
			List.of(
					SkillInfo.stat("체력", "180"),
					SkillInfo.stat("공격력", "28"),
					SkillInfo.stat("공격속도", "초당 1.25회"),
					SkillInfo.stat("이동속도", "기본 대비 +15%")),
			List.of(
					new SkillInfo("패시브", "그림자 낙인", null, "minecraft:amethyst_shard",
							"스킬에 맞은 적에게 낙인, 평타로 터뜨려 추가 피해",
							List.of(
									SkillInfo.stat("분류", "패시브 · 추가 피해"),
									SkillInfo.stat("낙인을 찍는 스킬", "가르기 · 표창 · 반격 · 천검난무"),
									SkillInfo.stat("지속", "4초"),
									SkillInfo.stat("터뜨리기", "낙인 찍힌 적을 평타로 벰"),
									SkillInfo.stat("추가 피해", "30 (넉백 없음)")), false),
					new SkillInfo("좌클릭", "그림자 베기", null, "minecraft:netherite_sword",
							"앞의 좁은 부채꼴을 빠르게 벰",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역"),
									SkillInfo.stat("피해", "28 (공격력)"),
									SkillInfo.stat("공격속도", "초당 1.25회"),
									SkillInfo.stat("사거리", "3칸"),
									SkillInfo.stat("범위", "앞 80도 부채꼴"),
									SkillInfo.stat("낙인", "터뜨려 30 추가")), false),
					new SkillInfo("RMB", "그림자 가르기", Overbreak.id("hud/skill/shade_rend"), null,
							"적을 뚫고 돌진하며 베고 낙인",
							List.of(
									SkillInfo.stat("분류", "이동기 · 근접 · 관통"),
									SkillInfo.stat("거리", "7.5칸 (0.3초) · 적을 뚫고 지나감"),
									SkillInfo.stat("피해", "30 (지나간 적마다 1번)"),
									SkillInfo.stat("판정", "몸에서 1.5칸"),
									SkillInfo.stat("낙인", "맞은 적 전원"),
									SkillInfo.stat("초기화", "적 처치 · 잔영 반격"),
									SkillInfo.stat("재사용 대기시간", "8초")), false),
					new SkillInfo("SHIFT", "잔영 회피", Overbreak.id("hud/skill/shade_evade"), null,
							"잠깐 피해를 흘리고, 때린 적의 등 뒤로 반격",
							List.of(
									SkillInfo.stat("분류", "회피 · 무적 · 반격"),
									SkillInfo.stat("지속", "0.7초"),
									SkillInfo.stat("받는 피해", "무효 (넉백 없음)"),
									SkillInfo.stat("반격", "16칸 안의 첫 공격자 등 뒤로 순간이동"),
									SkillInfo.stat("반격 효과", "낙인 · 그림자 가르기 초기화"),
									SkillInfo.stat("반격 뒤", "0.3초 더 피해 무효"),
									SkillInfo.stat("군중 제어", "막지 못함"),
									SkillInfo.stat("재사용 대기시간", "10초")), false),
					new SkillInfo("F", "그림자 표창", Overbreak.id("hud/skill/shade_kunai"), null,
							"표창을 꽂고, 다시 누르면 그 적의 등 뒤로",
							List.of(
									SkillInfo.stat("분류", "투사체 · 군중제어 · 이동기"),
									SkillInfo.stat("사거리", "16칸 (곧게 날아감)"),
									SkillInfo.stat("피해", "30 (머리 60)"),
									SkillInfo.stat("둔화", "30% · 1.5초"),
									SkillInfo.stat("낙인", "맞은 적"),
									SkillInfo.stat("그림자 걸음", "3초 안에 다시 F · 20칸 안 · 0.2초에 등 뒤로"),
									SkillInfo.stat("재사용 대기시간", "8초")), false),
					new SkillInfo("Q", "천검난무", Overbreak.id("hud/skill/shade_ult"), null,
							"주위 적들을 순간이동하며 번갈아 벰",
							List.of(
									SkillInfo.stat("분류", "이동기 · 저지불가 · 연속 공격"),
									SkillInfo.stat("대상", "반경 10칸 · 시야 안 · 최대 4명"),
									SkillInfo.stat("베기", "6번 (0.2초마다) · 한 번에 25"),
									SkillInfo.stat("한 명일 때", "6번 모두 (150)"),
									SkillInfo.stat("낙인", "벨 때마다"),
									SkillInfo.stat("베는 동안", "피해 · 군중 제어 무시 · 행동 잠김"),
									SkillInfo.stat("적이 없으면", "발동 안 함 (게이지 유지)"),
									SkillInfo.stat("충전", "피해 10당 2%")), true)));

	public static List<String> skillKeys() {
		return List.of(REND, EVADE, KUNAI);
	}
}
