package kr.overbreak.classes.thunder;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 직업 · 뇌신 (중거리 전격 마법사) — 모드 창작 직업 (데이터팩에 없음).
 *
 *   체력 225 (+25) · 뇌격 20 (0.55초 · 14칸 · 연쇄 9) · 이동속도 기본 · 근접 불가 · 게이지 피해 14당 2%
 *   번개로 정전기를 쌓아 3번째에 감전(25 + 0.5초 기절)시키는 연계형. 섬전으로 번개가 되어 파고들고 빠짐
 *
 * 밸런스 기준 (다른 원거리와 비교):
 *   뇌격 초당 약 33 + 감전 (3발마다 25) ≈ 초당 48 — 보안관(치명타 제외 초당 50) · 발키리(초당 45)와 같은 급
 *     대신 사거리가 14칸으로 가장 짧고 연쇄 · 기절이라는 부가 효과를 가짐
 *   감전 기절 0.5초는 같은 적에게 5초에 한 번뿐 — 스킬만으로 기절을 이어 걸 수 없음
 *   약점: 섬전 말고는 몸을 지킬 수단이 없고, 뇌신강림 중에는 떠서 멈춰 있어 집중 사격에 약함 (군중 제어만 면역)
 */
public final class Thunder implements PvpClass {
	public static final String ID = "thunder";
	static final String STEP = "th_step";
	static final String FIELD = "th_field";
	static final String SMITE = "th_smite";
	private static final Map<String, Integer> TOTALS = Map.of(STEP, LightningStep.COOLDOWN, FIELD, StormField.COOLDOWN, SMITE, Thunderbolt.COOLDOWN);

	static {
		// 섬전 중에는 피해를 받지 않음 (몸이 번개가 됨) · 강림 뒤 떨어질 때 낙하 피해 없음
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !absorb(entity, source));
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("뇌신", ChatFormatting.AQUA);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new ThunderState();
		Classes.baseStats(p, 25, 0.0, 0);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("뇌신", ChatFormatting.AQUA))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		ThunderState st = stateOrNull(p);
		if (st != null) {
			st.charges.clear();
			st.stunImmune.clear();
			st.fallSafeT = 0;
		}
		Attachments.combatant(p).ccImmune = false;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:thunder_spear", SkillItems.name("뇌신의 창", ChatFormatting.AQUA), STEP,
				SkillItems.lore()
						.line("직업 · 뇌신", ChatFormatting.DARK_GRAY).blank()
						.bold("[패시브] 정전기", ChatFormatting.AQUA)
						.line(" 번개에 맞을 때마다 1스택, 3스택에 감전 25 + 0.5초 기절.", ChatFormatting.GRAY).blank()
						.bold("[좌클릭] 뇌격", ChatFormatting.AQUA)
						.line(" 14칸 번개 20 · 0.55초. 5칸 안의 다른 적에게 튀어 9.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 섬전", ChatFormatting.AQUA)
						.line(" 번개가 되어 8칸 돌진 · 무적 · 지나간 적 25. 쿨타임 9초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 뇌운", ChatFormatting.AQUA)
						.line(" 조준한 곳에 4초 번개구름 · 둔화 · 0.5초마다 8. 쿨타임 13초", ChatFormatting.GRAY).blank()
						.bold("[F] 낙뢰", ChatFormatting.AQUA)
						.line(" 0.7초 뒤 조준한 곳에 벼락 45 · 띄우기. 쿨타임 11초", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("뇌신", ChatFormatting.AQUA), SkillItems.lore()
				.line("중거리 전격 마법사", ChatFormatting.DARK_GRAY).blank()
				.line("체력      225  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    뇌격 20  (연쇄 9)", ChatFormatting.GRAY)
				.line("공격속도  0.55초에 1회", ChatFormatting.GRAY)
				.line("이동속도  기본", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("lightning_rod", Hud.bold("뇌신강림", ChatFormatting.AQUA), SkillItems.lore()
				.line("궁극기 · 뇌신", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 뇌신강림", ChatFormatting.AQUA)
				.line(" 하늘로 떠올라 3초 동안 1초마다", ChatFormatting.GRAY)
				.line(" 18칸 안의 모든 적에게 벼락 35.", ChatFormatting.GRAY)
				.line(" 떠 있는 동안 군중 제어 면역 · 움직일 수 없음.", ChatFormatting.GRAY).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void basic(ServerPlayer p) {
		Bolt.fire(p, state(p));
	}

	@Override
	public void primary(ServerPlayer p) {
		LightningStep.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		StormField.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		Thunderbolt.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		Descent.cast(p, state(p));
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 14 당 게이지 2% (0.1c 너프). */
	@Override
	public int ultRate() {
		return 143;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		ThunderState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(STEP), LightningStep.COOLDOWN, st.step != null),
				new SkillSlot(prof.cooldown(FIELD), StormField.COOLDOWN, false),
				new SkillSlot(prof.cooldown(SMITE), Thunderbolt.COOLDOWN, false));
	}

	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		ThunderState st = state(p);
		if (st.descent != null) {
			return new HudExtra(-1, 0, st.descent.remainingPercent(), HudExtra.METER_DURATION);
		}
		return HudExtra.NONE;
	}

	@Override
	public void tick(ServerPlayer p) {
		ThunderState st = state(p);
		Cooldowns.tick(p, TOTALS);
		StaticCharge.tick(st);
		if (st.shotCd > 0) {
			st.shotCd--;
		}
		if (st.fallSafeT > 0) {
			st.fallSafeT--;
			p.resetFallDistance();
			if (p.onGround() && st.descent == null) {
				st.fallSafeT = 0;
			}
		}
	}

	// ── 공통 ────────────────────────────────────────────────

	/** 섬전 중이면 피해를 받지 않음. */
	public static boolean absorb(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
		// 기 모으는 동안은 아직 무적이 아닙니다 (0.1 버전)
		return target instanceof ServerPlayer sp && Attachments.profile(sp).classState instanceof ThunderState st
				&& st.step != null && st.step.dashing();
	}

	/** 조준한 곳 (벽 · 몸 · 사거리 끝) 아래의 땅 — 장판 · 벼락 자리. */
	static Vec3 groundTarget(ServerPlayer p, double range) {
		Hitscan.Hit hit = Hitscan.cast(p, p.getEyePosition(), Aim.direction(p), range);
		Vec3 at = hit.target() != null ? hit.target().position() : hit.end();
		// 벽에 닿았으면 살짝 앞으로 당긴 뒤 아래로 내려 땅을 찾음
		Vec3 back = at.subtract(Aim.direction(p).normalize().scale(0.3));
		BlockHitResult down = p.level().clip(new ClipContext(back.add(0.0, 0.5, 0.0), back.add(0.0, -12.0, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.MISS ? back : down.getLocation();
	}

	public static ThunderState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof ThunderState)) {
			prof.classState = new ThunderState();
		}
		return prof.state();
	}

	static @Nullable ThunderState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof ThunderState st ? st : null;
	}

	/** 시험용 수치. */
	public static final int BOLT_DAMAGE = Bolt.DAMAGE_10 / 10;
	public static final int CHAIN_DAMAGE = Bolt.CHAIN_10 / 10;
	public static final int DISCHARGE_DAMAGE = StaticCharge.DISCHARGE_10 / 10;
	public static final int STEP_DAMAGE = LightningStep.DAMAGE_10 / 10;
	public static final int SMITE_DAMAGE = Thunderbolt.DAMAGE_10 / 10;
	public static final int ULT_DAMAGE = Descent.DAMAGE_10 / 10;

	private static final ClassInfo INFO = new ClassInfo("뇌신", "중거리 전격 마법사", 0x5AD8FF,
			"정전기를 쌓아 감전시키고, 번개가 되어 전장을 가르는 벼락의 신.",
			List.of(
					SkillInfo.stat("체력", "225"),
					SkillInfo.stat("공격력", "뇌격 20 (연쇄 9)"),
					SkillInfo.stat("공격속도", "0.55초에 1회"),
					SkillInfo.stat("이동속도", "기본")),
			List.of(
					new SkillInfo("패시브", "정전기", null, "minecraft:lightning_rod",
							"번개에 맞을 때마다 쌓여 3번째에 감전",
							List.of(
									SkillInfo.stat("분류", "패시브 · 군중제어"),
									SkillInfo.stat("쌓이는 스킬", "뇌격 · 섬전 · 뇌운 · 낙뢰 · 뇌신강림"),
									SkillInfo.stat("유지", "마지막으로 쌓인 뒤 4초"),
									SkillInfo.stat("감전", "3스택에 25 피해 + 0.5초 기절"),
									SkillInfo.stat("기절 제한", "같은 적 5초에 한 번")), false),
					new SkillInfo("좌클릭", "뇌격", null, "minecraft:prismarine_crystals",
							"번개를 쏘고, 곁의 적에게 한 번 더 튐",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 연쇄"),
									SkillInfo.stat("피해", "20"),
									SkillInfo.stat("사거리", "14칸 (조준 보정)"),
									SkillInfo.stat("공격속도", "0.55초에 1회"),
									SkillInfo.stat("연쇄", "맞은 적 5칸 안 1명에게 9"),
									SkillInfo.stat("정전기", "맞은 적 1스택")), false),
					new SkillInfo("RMB", "섬전", Overbreak.id("hud/skill/thunder_step"), null,
							"번개가 되어 적을 꿰뚫고 내달림",
							List.of(
									SkillInfo.stat("분류", "이동기 · 무적 · 관통"),
									SkillInfo.stat("기 모으기", "0.2초 (제자리)"),
									SkillInfo.stat("거리", "8칸 (0.2초) · 적을 뚫고 지나감"),
									SkillInfo.stat("달리는 동안", "피해 무효 · 모습이 번개로 바뀜"),
									SkillInfo.stat("피해", "25 (지나간 적마다) + 정전기"),
									SkillInfo.stat("재사용 대기시간", "9초")), false),
					new SkillInfo("SHIFT", "뇌운", Overbreak.id("hud/skill/thunder_field"), null,
							"조준한 곳에 번개구름을 띄워 지짐",
							List.of(
									SkillInfo.stat("분류", "설치 · 광역 · 군중제어"),
									SkillInfo.stat("사거리", "20칸"),
									SkillInfo.stat("범위 · 지속", "반경 4.5칸 · 4초"),
									SkillInfo.stat("둔화", "15% (안에 있는 동안)"),
									SkillInfo.stat("피해", "0.5초마다 8 (최대 64)"),
									SkillInfo.stat("정전기", "한 적에게 1초에 1스택"),
									SkillInfo.stat("재사용 대기시간", "13초")), false),
					new SkillInfo("F", "낙뢰", Overbreak.id("hud/skill/thunder_smite"), null,
							"예고 후 조준한 곳에 벼락을 내리꽂음",
							List.of(
									SkillInfo.stat("분류", "설치 · 광역 · 군중제어"),
									SkillInfo.stat("사거리", "18칸"),
									SkillInfo.stat("예고", "0.7초 (바닥 고리)"),
									SkillInfo.stat("범위", "반경 2.5칸"),
									SkillInfo.stat("피해", "45 + 정전기"),
									SkillInfo.stat("띄우기", "0.4초"),
									SkillInfo.stat("재사용 대기시간", "11초")), false),
					new SkillInfo("Q", "뇌신강림", Overbreak.id("hud/skill/thunder_ult"), null,
							"떠올라 주위 모든 적에게 벼락 세 번",
							List.of(
									SkillInfo.stat("분류", "광역 · 저지불가 · 연속 공격"),
									SkillInfo.stat("대상", "18칸 안 · 시야 안의 모든 적"),
									SkillInfo.stat("벼락", "1초마다 3번 · 한 번에 35 + 정전기"),
									SkillInfo.stat("한 적에게", "105 + 세 번째에 감전"),
									SkillInfo.stat("떠 있는 동안", "움직일 수 없음 · 군중 제어 면역"),
									SkillInfo.stat("뇌격", "사용 가능"),
									SkillInfo.stat("충전", "피해 14당 2%")), true)));
}
