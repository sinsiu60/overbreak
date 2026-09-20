package kr.overbreak.classes.gunslinger;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.input.InputRouter;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 직업 9 · 궤적의 깃털 — 건슬링어 (공중 기동 원거리형).
 *
 *   체력 180 · 낙하 피해 없음 · 근접 불가 · 게이지 피해 1당 1%
 *   공중에서 싸우는 직업입니다: 쏘면 반대 방향으로 밀리고, 공중에서 맞힌 총알은 무조건 치명타이며,
 *   그 치명타가 다시 반동 도약 · 사선 앵커의 쿨타임을 깎아 공중에 더 오래 머물게 합니다.
 *
 * 조작 (모드 공통 배치에 맞춤)
 *   LMB         쌍권총 연사
 *   RMB         반동 도약
 *   SHIFT       곡예 난사 (활공은 점프 키로 옮겨 웅크리기가 비었습니다)
 *   E           사선 앵커
 *   Q           차원 회전 포격
 *   R           재장전
 */
public final class Gunslinger implements PvpClass {
	public static final String ID = "gunslinger";
	public static final String BOOST = "gs_boost";
	public static final String ACRO = "gs_acro";
	public static final String ANCHOR = "gs_anchor";
	private static final Map<String, Integer> TOTALS =
			Map.of(BOOST, RecoilBoost.COOLDOWN, ACRO, AeroAcrobatics.COOLDOWN, ANCHOR, WireAnchor.COOLDOWN);
	private static final int SKY = 0x7FD4FF;

	static {
		// 낙하 피해 면역 (늘) · 곡예 난사 무적 프레임
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !absorb(entity, source));
	}

	private static final ClassInfo INFO = new ClassInfo("궤적의 깃털", "공중 기동 원거리형", SKY,
			"쏜 반동으로 날아다니는 곡예 사수. 땅에 발을 붙이는 순간 약해지고, 공중에 떠 있는 동안은 모든 총알이 치명타입니다.",
			List.of(
					SkillInfo.stat("체력", "180"),
					SkillInfo.stat("공격력", "발당 20 (공중 30)"),
					SkillInfo.stat("공격속도", "0.2초에 1발"),
					SkillInfo.stat("탄창", "18발 · 1.25초 재장전"),
					SkillInfo.stat("낙하 피해", "받지 않음")),
			List.of(
					new SkillInfo("패시브", "체공 훈풍", null, "minecraft:feather",
							"공중에서 점프 키를 누르면 활공하고, 공중에서 맞힌 총알은 무조건 치명타",
							List.of(
									SkillInfo.stat("활공", "공중에서 점프 키를 누르고 있기 — 낙하 속도 -80%"),
									SkillInfo.stat("활공 시간", "최대 2초 · 착지하면 다시 참"),
									SkillInfo.stat("공중 치명타", "150% (평타 30 · 곡예 난사 22.5)"),
									SkillInfo.stat("쿨타임 환급", "공중 명중마다 반동 도약 · 사선 앵커 -0.5초"),
									SkillInfo.stat("낙하 피해", "언제나 받지 않음")), false),
					new SkillInfo("LMB", "쌍권총 연사", Overbreak.id("hud/skill/gunslinger_pistols"), null,
							"16칸 히트스캔을 좌우 총구로 번갈아 퍼부음",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 탄창"),
									SkillInfo.stat("피해", "발당 20 · 공중 30"),
									SkillInfo.stat("사거리", "16칸 · 거리 감소 없음"),
									SkillInfo.stat("사격 속도", "0.2초에 1발"),
									SkillInfo.stat("탄창", "18발 · R 또는 다 쓰면 1.25초 재장전"),
									SkillInfo.stat("공중 반동", "조준 반대 방향으로 약하게 밀려남"),
									SkillInfo.stat("넉백", "없음")), false),
					new SkillInfo("RMB", "반동 도약", Overbreak.id("hud/skill/gunslinger_boost"), null,
							"조준한 곳에 충격탄을 쏘고 그 반동으로 정반대로 날아감",
							List.of(
									SkillInfo.stat("분류", "광역 · 이동기"),
									SkillInfo.stat("폭발", "조준선 5칸 앞 (벽이면 벽)"),
									SkillInfo.stat("피해", "반경 3칸 25 + 넉백"),
									SkillInfo.stat("도약", "조준 반대 방향 — 바닥을 보면 약 7칸"),
									SkillInfo.stat("균열 지대", "봉인됨"),
									SkillInfo.stat("재사용 대기시간", "6초 (차원 회전 포격 중에는 없음)")), false),
					new SkillInfo("SHIFT", "곡예 난사", Overbreak.id("hud/skill/gunslinger_acro"), null,
							"한 바퀴 돌며 반경 8칸을 통째로 훑는 난사, 도는 동안 무적",
							List.of(
									SkillInfo.stat("분류", "광역 · 무적 · 조준 없음"),
									SkillInfo.stat("난사", "8번 (0.1초마다 · 사방으로)"),
									SkillInfo.stat("피해", "한 번당 15 (전부 120 · 공중 180)"),
									SkillInfo.stat("범위", "반경 8칸 안 모든 적 (벽 뒤도)"),
									SkillInfo.stat("시점", "도는 동안 3인칭 · 끝나면 원래대로"),
									SkillInfo.stat("무적", "도는 0.8초 동안"),
									SkillInfo.stat("탄창", "쓰지 않음"),
									SkillInfo.stat("재사용 대기시간", "9초")), false),
					new SkillInfo("E", "사선 앵커", Overbreak.id("hud/skill/gunslinger_anchor"), null,
							"12칸 와이어를 쏘아 붙는 곳으로 끌려감",
							List.of(
									SkillInfo.stat("분류", "이동기 · 군중제어"),
									SkillInfo.stat("사거리", "12칸"),
									SkillInfo.stat("적중", "20 피해 · 0.5초 기절 · 그 적의 머리 위로"),
									SkillInfo.stat("벽 적중", "그 지점으로 당겨짐"),
									SkillInfo.stat("헛방", "쿨타임 절반만"),
									SkillInfo.stat("균열 지대", "봉인됨"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("Q", "차원 회전 포격", Overbreak.id("hud/skill/gunslinger_ult"), null,
							"공중에 멈춰 선 채 지름 10칸을 3초간 갈아엎음",
							List.of(
									SkillInfo.stat("분류", "광역 · 채널링"),
									SkillInfo.stat("솟구침", "약 6칸 (0.4초)"),
									SkillInfo.stat("포격", "3초 · 0.25초마다 15 (초당 60 · 최대 180)"),
									SkillInfo.stat("범위", "조준한 땅의 반경 5칸"),
									SkillInfo.stat("포격 중", "반동 도약 쿨타임 없음 (공중 이동)"),
									SkillInfo.stat("군중제어", "면역"),
									SkillInfo.stat("충전", "피해 1당 1%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("궤적의 깃털", ChatFormatting.AQUA);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new GunslingerState();
		Classes.baseStats(p, -20, 0.0, 500);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.AQUA))
				.append(Hud.bold("궤적의 깃털", ChatFormatting.AQUA))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		GunslingerState st = stateOrNull(p);
		if (st != null) {
			DualPistols.cancelReload(p, st);
			AeroDrift.stop(p, st);
			st.acrobatics = null;
			st.bombardment = null;
		}
		Attachments.combatant(p).ccImmune = false;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:gunslinger_pistols", SkillItems.name("쌍권총", ChatFormatting.AQUA), BOOST,
				SkillItems.lore()
						.line("직업 · 궤적의 깃털", ChatFormatting.DARK_GRAY).blank()
						.bold("[LMB] 쌍권총 연사", ChatFormatting.AQUA)
						.line(" 16칸 히트스캔 · 발당 20 · 0.2초에 1발. 탄창 18발 (R 재장전 1.25초)", ChatFormatting.GRAY)
						.line(" 공중에서 맞히면 치명타 150% (30) · 공중에서 쏘면 반대로 밀려납니다.", ChatFormatting.GRAY).blank()
						.bold("[RMB] 반동 도약", ChatFormatting.AQUA)
						.line(" 조준한 곳에 반경 3칸 25 + 넉백, 그 반동으로 정반대로 약 7칸. 쿨타임 6초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 곡예 난사", ChatFormatting.AQUA)
						.line(" 한 바퀴 돌며 반경 8칸 안 모든 적에게 8번 · 한 번당 15. 도는 0.8초 무적. 쿨타임 9초", ChatFormatting.GRAY).blank()
						.bold("[E] 사선 앵커", ChatFormatting.AQUA)
						.line(" 12칸 와이어 · 적중 20 + 0.5초 기절 + 머리 위로 · 벽이면 그 자리로. 쿨타임 7초", ChatFormatting.GRAY).blank()
						.bold("[패시브] 체공 훈풍", ChatFormatting.AQUA)
						.line(" 공중에서 점프 키를 누르고 있으면 2초 활공 · 공중 명중마다 이동기 쿨타임 -0.5초 · 낙하 피해 없음", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		// 쌍권총 — 양손에 한 자루씩. 왼손 총은 모습만 있는 소품이라 버리기 · 칸 고정에 걸리지 않습니다
		inv.setItem(Inventory.SLOT_OFFHAND, SkillItems.prop("overbreak:gunslinger_pistols",
				SkillItems.name("쌍권총 (왼손)", ChatFormatting.AQUA)));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("궤적의 깃털", ChatFormatting.AQUA), SkillItems.lore()
				.line("공중 기동 원거리형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      180  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    발당 20  (공중 30)", ChatFormatting.GRAY)
				.line("공격속도  0.2초에 1발", ChatFormatting.GRAY)
				.line("이동속도  기본", ChatFormatting.GRAY)
				.line("탄창      18발", ChatFormatting.GRAY)
				.line("낙하 피해 받지 않음", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("firework_rocket", Hud.bold("차원 회전 포격", ChatFormatting.AQUA), SkillItems.lore()
				.line("궁극기 · 궤적의 깃털", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 차원 회전 포격", ChatFormatting.AQUA)
				.line(" 약 6칸 솟구쳐 공중에 멈춘 뒤 3초 동안 포격합니다.", ChatFormatting.GRAY)
				.line(" 조준한 땅의 반경 5칸에 0.25초마다 15 — 초당 60, 최대 180.", ChatFormatting.GRAY)
				.line(" 포격 중에는 반동 도약을 쿨타임 없이 써서 자리를 옮길 수 있습니다.", ChatFormatting.GRAY)
				.line(" 포격 중에는 군중 제어에 걸리지 않지만 피해는 그대로 받습니다.", ChatFormatting.RED).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void basic(ServerPlayer p) {
		DualPistols.fire(p, state(p));
	}

	/** 우클릭 — 반동 도약. */
	@Override
	public void primary(ServerPlayer p) {
		GunslingerState st = state(p);
		boolean ult = st.inUlt();
		RecoilBoost.cast(p, st);
		if (ult && st.bombardment != null) {
			st.bombardment.loosen();
		}
	}

	/** 웅크리기 — 곡예 난사. 활공은 공중에서 점프 키를 누르고 있을 때라 서로 겹치지 않습니다. */
	@Override
	public void secondary(ServerPlayer p) {
		AeroAcrobatics.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		WireAnchor.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		AerialBombardment.cast(p, state(p));
	}

	@Override
	public void reload(ServerPlayer p) {
		DualPistols.manualReload(p, state(p));
	}

	/** 포격 중에는 반동 도약만 받습니다 (다른 스킬 · 평타는 잠김). */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		GunslingerState st = state(p);
		if (st.bombardment == null) {
			return false;
		}
		return slot != InputRouter.Slot.PRIMARY;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 1당 게이지 1%. */
	@Override
	public int ultRate() {
		return 100;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		GunslingerState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(BOOST), RecoilBoost.COOLDOWN, st.inUlt()),
				new SkillSlot(prof.cooldown(ACRO), AeroAcrobatics.COOLDOWN, st.acrobatics != null),
				new SkillSlot(prof.cooldown(ANCHOR), WireAnchor.COOLDOWN, false));
	}

	/** 무기 칸 옆: 탄창 (N/18). 조준점 아래: 재장전 · 포격 남은 시간 · 활공 남은 시간. */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		GunslingerState st = state(p);
		if (st.bombardment != null) {
			return new HudExtra(st.ammo, DualPistols.MAG, st.bombardment.remainingPercent(), HudExtra.METER_DURATION);
		}
		if (st.reloadT > 0) {
			int reload = Ticks.of(DualPistols.RELOAD);
			return new HudExtra(st.ammo, DualPistols.MAG, (reload - st.reloadT) * 100 / reload, HudExtra.METER_RELOAD);
		}
		if (st.gliding) {
			return new HudExtra(st.ammo, DualPistols.MAG, st.glideT * 100 / Math.max(1, Ticks.of(AeroDrift.GLIDE)), HudExtra.METER_DURATION);
		}
		return new HudExtra(st.ammo, DualPistols.MAG, -1, 0);
	}

	@Override
	public void tick(ServerPlayer p) {
		GunslingerState st = state(p);
		Cooldowns.tick(p, TOTALS);
		DualPistols.tick(p, st);
		AeroDrift.tick(p, st);
		// 낙하 피해를 아예 받지 않는 직업이라 낙하 거리를 쌓아 두지 않습니다
		p.resetFallDistance();
	}

	// ── 공통 ────────────────────────────────────────────────

	/**
	 * 피해를 받지 않는 경우 — 낙하 피해는 늘, 곡예 난사로 도는 동안은 전부.
	 * 시험에서 직접 부릅니다.
	 */
	public static boolean absorb(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer sp) || !(Attachments.profile(sp).classState instanceof GunslingerState st)) {
			return false;
		}
		return source.is(DamageTypeTags.IS_FALL) || st.iframes();
	}

	/**
	 * 지금 속도에 한 번 더 얹는 힘 (바닐라 addVelocity 와 같은 뜻).
	 * {@link kr.overbreak.combat.Motion#launch} 는 속도를 통째로 갈아 끼우므로, 쏘면서 밀리는 약한 반동은 이쪽을 씁니다.
	 *
	 * @param power 시간 단위당 칸
	 */
	public static void impulse(ServerPlayer p, Vec3 dir, double power) {
		if (power <= 0 || dir.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 add = dir.normalize().scale(power * Ticks.step());
		p.setDeltaMovement(p.getDeltaMovement().add(add));
		p.hurtMarked = true;
	}

	public static GunslingerState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof GunslingerState)) {
			prof.classState = new GunslingerState();
		}
		return prof.state();
	}

	static @Nullable GunslingerState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof GunslingerState st ? st : null;
	}
}
