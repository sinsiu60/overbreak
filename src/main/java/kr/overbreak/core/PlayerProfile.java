package kr.overbreak.core;

import java.util.HashMap;
import java.util.Map;

import kr.overbreak.classes.PvpClass;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 플레이어 전용 상태 — 직업, 쿨타임, 궁극기 게이지, 입력 이력.
 * 데이터팩의 pvp.class / pvp.cd_* / pvp.ult_* / pvp.msg_t 가 여기로 모입니다.
 */
public final class PlayerProfile {
	public @Nullable PvpClass pvpClass;

	/** 직업이 쓰는 자유 상태. 직업마다 자기 타입으로 넣고 꺼냅니다. */
	public @Nullable Object classState;

	// ── 공격속도 ──
	/** 초당 공격 횟수 x100. 0 이면 잠금 없음. */
	public int atkSpeed;
	/** 재공격까지 남은 틱. */
	public int atkCd;

	// ── 쿨타임 (스킬 키 → 남은 틱) ──
	public final Map<String, Integer> cooldowns = new HashMap<>();
	/** 마지막으로 그린 쿨타임 막대 단계. 바뀐 틱에만 아이템을 건드립니다. */
	public final Map<String, Integer> barShown = new HashMap<>();

	// ── 궁극기 게이지 ──
	/** 피해 누적 (체력 10배 기준). */
	public int ultRaw;
	/** 피해 1당 게이지 x100 (200 = 2%). */
	public int ultRate = 200;
	public boolean ultOn;
	public boolean ultHas;
	public int ultCharge;
	/** 피해가 소수(산탄 3.5 등)일 때 게이지에 아직 넣지 못한 나머지. */
	public float ultFrac;

	/** 다른 안내가 액션바를 쓰는 동안 게이지 HUD 가 양보하는 시간. */
	public int msgT;

	// ── 입력 이력 ──
	public boolean prevSneak;
	public long lastUseTick = -1000;
	/** 모드 클라이언트가 알려 준 우클릭 상태 (net/RightHoldPayload). rightKnown 이 false 면 사용 패킷 창으로 판단합니다. */
	public boolean rightDown;
	public boolean rightKnown;
	/** 모드 클라이언트가 알려 준 점프 키 상태 (net/JumpHoldPayload). */
	public boolean jumpDown;
	public long lastSwingTick = -1000;
	public long lastBlockHitTick = -1000;
	/** Q 를 누르면 클라이언트가 팔도 휘두릅니다. 그 스윙을 평타로 치지 않으려고 기록합니다. */
	public long lastDropTick = -1000;
	/** 클라이언트에 마지막으로 알린 조작 모드 (net/InputModePayload). */
	public boolean sentInputMode;
	/** 마지막으로 보낸 스킬 HUD (skill/HudSync). */
	public @Nullable Object lastHud;
	/** 근접 기본 공격 연속 횟수 (정방향 · 역방향 번갈아). 1.5초 쉬면 처음부터. */
	public int basicCombo;
	public long lastCleaveTick = -1000;

	// ── 조준 (어깨 너머 시점 보정, combat/Aim) ──
	/** 클라이언트가 보낸 조준점 — 화면 조준선이 닿는 곳. */
	public @Nullable Vec3 aimPoint;
	public long aimTick = -1000;

	// ── 킬 / 데스 ──
	public int streak;

	/** 남은 쿨타임 (틱). */
	public int cooldown(String key) {
		return cooldowns.getOrDefault(key, 0);
	}

	/** @param time 쿨타임 (1/20초 단위 — 160 = 8초). 저장은 지금 틱레이트의 틱 수. */
	public void setCooldown(String key, int time) {
		cooldowns.put(key, Math.max(0, kr.overbreak.core.tick.Ticks.of(time)));
	}

	/** 틱 수 그대로 (남은 쿨타임에서 빼고 더할 때). */
	public void setCooldownTicks(String key, int ticks) {
		cooldowns.put(key, Math.max(0, ticks));
	}

	@SuppressWarnings("unchecked")
	public <T> T state() {
		return (T) classState;
	}
}
