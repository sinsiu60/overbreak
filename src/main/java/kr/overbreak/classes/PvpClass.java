package kr.overbreak.classes;

import java.util.List;

import kr.overbreak.combat.MeleeCleave;
import kr.overbreak.input.InputRouter;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 직업 하나. 데이터팩의 class/&lt;직업&gt;/give · remove · items 와
 * skill/use · sneak · swap · drop · input_left 분기 한 줄씩을 합친 것입니다.
 *
 * 입력 차단(기절·정신집중·튜토리얼 게이팅)은 {@link kr.overbreak.input.InputRouter} 가 먼저 거르므로
 * 여기서는 스킬 자체만 씁니다.
 */
public interface PvpClass {
	/** 명령어·저장용 식별자 (예: "warrior"). */
	String id();

	Component displayName();

	/** 스탯 · 아이템 · 초기 상태. 이전 직업은 이미 해제된 뒤에 불립니다. */
	void give(ServerPlayer p);

	/** 스탯 · 진행 중인 스킬 · 소환물 정리. */
	void remove(ServerPlayer p);

	/** 아이템만 다시 지급 (직업 아이템이 사라졌을 때). */
	void giveItems(ServerPlayer p);

	/** 좌클릭 스킬 (원거리 직업의 평타). 근접 직업은 비워 둡니다 — 좌클릭이 {@link MeleeCleave} 로 갑니다. */
	default void basic(ServerPlayer p) {}

	/** 우클릭 = 액티브 1 */
	default void primary(ServerPlayer p) {}

	/** 웅크리기(누르는 순간) = 액티브 2 */
	default void secondary(ServerPlayer p) {}

	/** 인벤토리 키 E = 액티브 3 (모드가 없는 클라이언트는 손 바꾸기 F) */
	default void tertiary(ServerPlayer p) {}

	/** 아이템 버리기 Q = 궁극기 (게이지 100% 일 때만 불립니다) */
	default void ult(ServerPlayer p) {}

	/** 재장전 키 R (모드 클라이언트, net/ReloadPayload) — 탄창이 있는 직업만. */
	default void reload(ServerPlayer p) {}

	/**
	 * 재장전 중인가 — 재장전 중에 스킬 키를 누르면 재장전을 끊고 스킬이 나갑니다 (0.2e).
	 * 재장전으로 걸린 잠금(casting)은 스킬 입력을 막지 않습니다. 평타는 그대로 막힙니다.
	 */
	default boolean reloading(ServerPlayer p) {
		return false;
	}

	/** 지금 궁극기 게이지가 차는가 — 궁극기 도중에는 막는 직업이 있습니다 (궤적 해방). */
	default boolean ultCharging(ServerPlayer p) {
		return true;
	}

	/** 게이지 100% 에 hotbar 3 에 넣을 아이템. 없으면 EMPTY. */
	default ItemStack ultItem() {
		return ItemStack.EMPTY;
	}

	/** F8 스킬 설명 화면에 쓸 직업 · 스킬 설명. 없으면 화면에 "설명 준비 중". */
	default @org.jspecify.annotations.Nullable ClassInfo info() {
		return null;
	}

	/** 오른쪽 아래 스킬 HUD 칸 — 우클릭 · 웅크리기 · F 순서. 클라이언트 HudLayouts 의 아이콘 순서와 같아야 합니다. */
	default List<SkillSlot> hudSlots(ServerPlayer p) {
		return List.of();
	}

	/**
	 * 입력 선처리 — 기절 · 정신집중 차단보다 먼저 불립니다 (데이터팩 skill/&lt;직업&gt;/pre_use · pre_sneak · pre_swap).
	 * true 를 돌려주면 이번 입력은 여기서 끝납니다. 예: 충전 중 반복 우클릭 무시, 같은 키로 파워 블록 해제, 비행 중 F 캔슬.
	 */
	default boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		return false;
	}

	/** 스킬 HUD 추가 표시 (탄창 · 충전 게이지). */
	default HudExtra hudExtra(ServerPlayer p) {
		return HudExtra.NONE;
	}

	/** 매 틱 (진행 중 스킬 · 패시브). */
	default void tick(ServerPlayer p) {}

	/**
	 * 평타 한 번의 애니메이션 번호 — 번갈아 두 방향으로 휘두릅니다.
	 * 기본은 공용 휘두르기, 전용 동작이 있는 직업은 바꿉니다.
	 */
	default int basicAnim(boolean back) {
		return back ? kr.overbreak.net.SkillAnimPayload.BASIC_BACK : kr.overbreak.net.SkillAnimPayload.BASIC;
	}

	/** 근접 평타가 대상에게 들어간 순간 (출혈 등 패시브). */
	default void onMeleeHit(ServerPlayer p, LivingEntity target) {}

	/** 이 직업의 플레이어가 피해를 받은 순간 (맞을수록 쌓이는 패시브). */
	default void onDamaged(ServerPlayer p, float taken) {}

	/**
	 * true = 근접 직업: 좌클릭하면 앞 부채꼴 범위 평타 ({@link MeleeCleave}).
	 * false = 원거리 직업: 좌클릭이 {@link #basic} 으로 갑니다.
	 * 어느 쪽이든 바닐라 조준 근접 공격은 막습니다.
	 */
	default boolean meleeAllowed() {
		return true;
	}

	/** 근접 평타 사거리 (눈에서 대상 히트박스까지, 칸). */
	default double meleeRange() {
		return MeleeCleave.DEFAULT_RANGE;
	}

	/** 사람마다 달라지는 평타 사거리 (자버프로 넓어지는 직업). 기본은 {@link #meleeRange()}. */
	default double meleeRange(ServerPlayer p) {
		return meleeRange();
	}

	/** 근접 평타 부채꼴 전체 각도 (도, 조준 방향 중심). */
	default double meleeArcDegrees() {
		return MeleeCleave.DEFAULT_ARC;
	}

	/** 근접 평타가 넉백을 주는가. false 면 맞아도 밀리지 않습니다 (붙어서 싸우는 직업). */
	default boolean meleeKnockback() {
		return true;
	}

	/**
	 * 평타가 들어간 뒤 대상의 무적 시간을 지울 것인가.
	 * true 면 평타 → 스킬 콤보가 바로 이어집니다 (평타가 만든 0.5초 무적에 스킬이 씹히지 않게).
	 */
	default boolean meleeClearsInvulnerability() {
		return false;
	}

	/** 궁극기 게이지를 쓰는 직업인가 (콤보 댄서는 false). */
	default boolean usesGauge() {
		return true;
	}

	/** 피해 1당 게이지 x100. */
	default int ultRate() {
		return 200;
	}
}
