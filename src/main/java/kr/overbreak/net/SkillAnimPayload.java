package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 서버 → 클라이언트: 스킬 애니메이션 시작/중단.
 *
 * entityId 애니메이션을 재생할 엔티티 (시전자 · 넘어뜨림 대상)
 * anim     애니메이션 번호. 음수면 그 번호를 중단 (끊김 · 해제 · 사슬 종료)
 * hiddenId 모드 클라이언트에서 숨길 디스플레이 엔티티 (모델 애니메이션이 대신 보여 줌). 없으면 -1
 * duration 재생 길이 (틱). 0 이면 번호마다 정해진 길이. 넘어뜨림처럼 길이가 매번 다른 것에 씁니다
 * elapsed  이미 지난 시간 (1/20초 단위). 받는 쪽은 그만큼 건너뛰고 재생합니다 —
 *          늦게 보이기 시작한 관전자, 도중에 단계를 건너뛴 스킬(돌진 난사가 벽에 막혀 곧장 제동)에 씁니다
 *
 * 엔티티 본인(플레이어면)과 그 엔티티를 보고 있는 플레이어 모두에게 보냅니다.
 * 모드가 없는 클라이언트에는 보내지 않으므로 바닐라 클라이언트는 기존 연출을 그대로 봅니다.
 */
public record SkillAnimPayload(int entityId, int anim, int hiddenId, int duration, int elapsed) implements CustomPacketPayload {
	public static final int SLAY = 1;
	public static final int FURY = 2;
	public static final int CHAIN = 3;
	public static final int ULT = 4;
	/** 근접 기본 공격 — 정방향 (오른쪽 → 왼쪽 베기). */
	public static final int BASIC = 5;
	/** 근접 기본 공격 — 역방향 (왼쪽 → 오른쪽). 연속으로 휘두르면 번갈아 나옵니다. */
	public static final int BASIC_BACK = 6;
	/** 넘어뜨림 (대상 엔티티가 뒤로 쓰러짐). duration 을 씁니다. 같은 번호가 다시 오면 남은 시간을 늘립니다. */
	public static final int KNOCKDOWN = 7;
	/** 햄머나이트: 망치를 머리 위로 들었다가 내려찍기 (지면 분쇄 · 중력 파쇄). */
	public static final int HK_SMASH = 8;
	/** 햄머나이트: 곧바로 내려찍기 (돌진 뒤 예약 분쇄). */
	public static final int HK_SLAM = 9;
	/** 햄머나이트: 방패 돌진 (기 모으기 + 돌진). */
	public static final int HK_CHARGE = 10;
	/** 햄머나이트: 망치를 뒤로 빼 힘을 모았다가 강하게 내려치기 (대지 진동파). */
	public static final int HK_ULT = 11;
	/** 파쇄권: 왼손 철권포 발사 (반동). */
	public static final int IF_SHOT = 12;
	/** 파쇄권: 로켓 펀치 충전 (오른손 건틀릿을 뒤로 당김, 파란 기). 서버가 발사 · 취소 때 멈춥니다. */
	public static final int IF_CHARGE = 13;
	/** 파쇄권: 로켓 펀치 돌진 (오른팔을 앞으로 내지름). */
	public static final int IF_PUNCH = 14;
	/** 파쇄권: 파워 블록 (건틀릿을 앞으로 세워 막음). 서버가 해제 때 멈춥니다. */
	public static final int IF_BLOCK = 15;
	/** 파쇄권: 지진 강타 도약 (주먹을 머리 위로 치켜듦). 착지 · 취소 때 멈춥니다. */
	public static final int IF_SLAM_AIR = 16;
	/** 파쇄권: 지진 강타 착지 (땅에 주먹을 내리꽂음). */
	public static final int IF_SLAM_HIT = 17;
	/** 파쇄권: 파멸의 일격 솟구침 (웅크렸다가 주먹을 위로). */
	public static final int IF_ULT_RISE = 18;
	/** 파쇄권: 파멸의 일격 낙하 · 착탄 (주먹부터 내리꽂는 착지). */
	public static final int IF_ULT_DROP = 19;
	/** 발키리: 연사 한 발 (두 팔로 조준 + 반동). 쏠 때마다 다시 옵니다. */
	public static final int VK_SHOT = 20;
	/** 발키리: 전술 로켓 발사 (큰 반동). */
	public static final int VK_ROCKET = 21;
	/** 발키리: 차원 도약 (뛰어올랐다가 떠 있는 자세). 착지 때 멈춥니다. */
	public static final int VK_FLOAT = 22;
	/** 발키리: 과열 분사 (허리춤에서 좌우로 난사). */
	public static final int VK_OVERHEAT = 23;
	/** 발키리: 탄막 포격 (기 모으기 0.7초 + 버티고 선 채 연속 사격 4초). 끝날 때 멈춥니다. */
	public static final int VK_BARRAGE = 24;
	/** 발키리 재장전 (탄창 빼기 → 새 탄창 → 장전 손잡이). */
	public static final int VK_RELOAD = 25;
	/** 보안관 피스키퍼 한 발. */
	public static final int SH_SHOT = 26;
	/** 보안관 리볼버 난사 한 발 (한 발마다 보냄). */
	public static final int SH_FAN = 27;
	/** 보안관 재장전 (탄창 젖힘 → 스피드로더 → 탄창 굴림 → 탁). */
	public static final int SH_RELOAD = 28;
	/** 보안관 전술 구르기. */
	public static final int SH_ROLL = 29;
	/** 보안관 섬광 수류탄 (왼손 투척). */
	public static final int SH_FLASH = 30;
	/** 보안관 황야의 무법자 조준. */
	public static final int SH_DEADEYE = 31;
	/** 보안관 황야의 무법자 발사. */
	public static final int SH_DEADEYE_FIRE = 32;
	/** 셰이드 그림자 가르기 (검을 옆으로 빼고 돌진하며 가로 베기). */
	public static final int SD_REND = 33;
	/** 셰이드 잔영 회피 (몸을 숙이며 뒤로 흐려짐). */
	public static final int SD_EVADE = 34;
	/** 셰이드 그림자 표창 (왼손 투척). */
	public static final int SD_KUNAI = 35;
	/** 셰이드 순간이동 베기 (그림자 걸음 · 잔영 반격 · 천검난무 한 번). 벨 때마다 다시 옵니다. */
	public static final int SD_STRIKE = 36;
	/** 셰이드 그림자 걸음 — 등 뒤로 날아가는 동안 (duration = 날아가는 틱). 도착하면 SD_STRIKE. */
	public static final int SD_STEP = 37;
	/** 뇌신 뇌격 한 발 (창을 앞으로 내지름). 뇌신강림 벼락 때도 보냄. */
	public static final int TH_CAST = 38;
	/** 뇌신 섬전 — 번개가 되어 달리는 동안 (duration). 이 동안 모습이 사라짐. */
	public static final int TH_DASH = 39;
	/** 뇌신 뇌운 (창을 하늘로 치켜듦). */
	public static final int TH_FIELD = 40;
	/** 뇌신 낙뢰 (창을 들었다가 내리꽂음). */
	public static final int TH_SMITE = 41;
	/** 뇌신 뇌신강림 — 떠 있는 동안 (duration). */
	public static final int TH_ULT = 42;
	/** 투귀 평타 — 오른쪽 위에서 왼쪽 아래로 대각선 내려베기. */
	public static final int BR_BASIC = 43;
	/** 투귀 평타 (반대 방향) — 왼쪽 아래에서 오른쪽 위로 올려베기. */
	public static final int BR_BASIC_BACK = 44;
	/** 투귀 강타 (대검을 머리 위로 들었다가 내리찍음). */
	public static final int BR_BLOW = 45;
	/** 투귀 돌개바람 (몸을 크게 휘돌림). */
	public static final int BR_WHIRL = 46;
	/** 투귀 전열 재정비 (대검을 땅에 짚고 숨 고르기). */
	public static final int BR_REGROUP = 47;
	/** 투귀 무쌍 (포효). */
	public static final int BR_ULT = 48;
	/** 건슬링어 쌍권총 한 발 (번갈아 오른손 · 왼손 총구). */
	public static final int GS_SHOT = 49;
	/** 건슬링어 공중 재장전 (탄창 두 개를 위로 튕겨 올리고 총을 돌리다가 다시 받아 끼움). */
	public static final int GS_RELOAD = 50;
	/** 건슬링어 반동 도약 (두 총을 아래로 내리꽂음). */
	public static final int GS_BOOST = 51;
	/**
	 * 건슬링어 돌진 난사 — 기 모으기 → 돌진 → 제동 → 사방 난사 → 마무리 (단계 길이는 서버 DashScatter).
	 * 벽에 막혀 제동으로 건너뛰면 elapsed 를 제동 시작 시각으로 다시 보냅니다.
	 */
	public static final int GS_SCATTER = 52;
	/** 건슬링어 사선 앵커 (왼손을 앞으로 내뻗어 와이어 발사). */
	public static final int GS_ANCHOR = 53;
	/** 건슬링어 차원 회전 포격 (떠서 아래를 겨눈 채 연속 사격). duration 을 씁니다. */
	public static final int GS_ULT = 54;
	/** 건슬링어 체공 훈풍 활공 (두 팔을 벌리고 미끄러짐). 서버가 멈춥니다. */
	public static final int GS_GLIDE = 55;
	/** 건슬링어 쌀권총 — 왼손 차례. 오른손({@link #GS_SHOT}) 과 번갈아 나갑니다. */
	public static final int GS_SHOT_L = 56;

	public static final Type<SkillAnimPayload> TYPE = new Type<>(Overbreak.id("skill_anim"));
	public static final StreamCodec<ByteBuf, SkillAnimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SkillAnimPayload::entityId,
			ByteBufCodecs.VAR_INT, SkillAnimPayload::anim,
			ByteBufCodecs.VAR_INT, SkillAnimPayload::hiddenId,
			ByteBufCodecs.VAR_INT, SkillAnimPayload::duration,
			ByteBufCodecs.VAR_INT, SkillAnimPayload::elapsed,
			SkillAnimPayload::new);

	public SkillAnimPayload(int entityId, int anim, int hiddenId, int duration) {
		this(entityId, anim, hiddenId, duration, 0);
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** 애니메이션을 받을 수 있는 클라이언트(모드 설치)인가. */
	public static boolean canSend(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, TYPE);
	}

	public static void broadcast(ServerPlayer caster, int anim, int hiddenId) {
		broadcast(caster, anim, hiddenId, 0);
	}

	public static void broadcast(Entity entity, int anim, int hiddenId, int duration) {
		broadcastAt(entity, anim, duration, 0);
	}

	/** 이미 elapsed(1/20초 단위) 만큼 지난 것으로 보냅니다 — 단계 건너뛰기용. */
	public static void broadcastAt(Entity entity, int anim, int duration, int elapsed) {
		SkillAnimPayload msg = new SkillAnimPayload(entity.getId(), anim, -1, duration, elapsed);
		if (entity instanceof ServerPlayer self && canSend(self)) {
			ServerPlayNetworking.send(self, msg);
		}
		for (ServerPlayer viewer : PlayerLookup.tracking(entity)) {
			if (viewer != entity && canSend(viewer)) {
				ServerPlayNetworking.send(viewer, msg);
			}
		}
	}

	/** 한 사람에게만 — 도중에 시전자를 보기 시작한 관전자에게 지난 만큼 건너뛰어 보냅니다. */
	public static void sendTo(ServerPlayer viewer, Entity entity, int anim, int duration, int elapsed) {
		if (canSend(viewer)) {
			ServerPlayNetworking.send(viewer, new SkillAnimPayload(entity.getId(), anim, -1, duration, elapsed));
		}
	}

	public static void stop(Entity entity, int anim) {
		broadcast(entity, -anim, -1, 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
