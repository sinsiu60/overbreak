package kr.overbreak.classes.ironcleaver;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 참철 진행 상태 — 지금 하는 동작 하나와 그 틱 수. */
public final class IronState {
	public enum Phase { IDLE, SWING, CHARGE, RELEASE, BASH, GUARD, REND, ULT }

	public Phase phase = Phase.IDLE;
	/** 이번 동작이 시작된 뒤 지난 틱. */
	public int t;

	// 평타
	/** 다음에 나갈 타 (0 · 1 · 2). */
	public int combo;
	/** 지금 휘두르는 타. */
	public int swing;
	/** 마지막 동작이 끝난 뒤 지난 틱 (콤보 초기화). */
	public int idleT;
	/** 후딜 중 누른 입력 (끝나면 곧바로 다음 타). */
	public boolean buffered;
	/** 이번 누름이 "누르고 있으면 모으기" 로 이어질 수 있는가 (누른 순간 시작한 휘두르기). */
	public boolean pressFresh;
	/** 마지막으로 본 누름 시각 (서버 소수 틱) — 새 누름을 알아챔. */
	public double lastPress = Double.NaN;
	public double pressTick = Double.NaN;
	public final Set<LivingEntity> hit = new HashSet<>();
	public boolean anyHit;

	// 참 모으기
	/** 박치기 동안 멈춘 시간 (시간 단위) · 검막 보상으로 미리 채운 시간. */
	public double paused;
	public double bonus;
	public int stage;
	public boolean perfect;
	/** 발동한 단계 (1~3, 4 = 진 참). */
	public int releaseStage;

	// 어깨 박치기
	public Vec3 bashDir = Vec3.ZERO;
	public boolean bashFromCharge;
	public int bashT;
	/** 박치기로 모으기가 멈춘 틱 수 (끝나면 paused 에 더함). */
	public long bashPauseStart;

	// 검막
	public float guardYaw;
	public boolean guardSuccess;
	/** 막기 성공 보상이 남은 틱. */
	public int rewardT;

	// 대지 가르기 · 천참
	public Vec3 castDir = Vec3.ZERO;
	public Vec3 castAt = Vec3.ZERO;
	public float castYaw;
	public boolean fired;
}
