package kr.overbreak.net;

import java.util.List;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 본인 클라이언트: 스킬 HUD (오른쪽 아래 스킬 칸 · 가운데 아래 궁극기 게이지).
 *
 * classId   직업 (클라이언트가 아이콘 배치를 고름). 빈 문자열이면 HUD 없음
 * remaining 칸별 남은 쿨타임 (틱) — 우클릭 · 웅크리기 · F 순서
 * total     칸별 전체 쿨타임 (틱)
 * active    칸별 효과 중 · 시전 중
 * ultCharge 궁극기 게이지 0~100
 * ultState  0 = 게이지 없음, 1 = 충전 중, 2 = 준비 완료
 * ammo      남은 탄 (-1 = 탄창 없는 직업), ammoMax 탄창 크기
 * meter     조준점 아래 게이지 0~100 (-1 = 없음), meterKind 1 = 로켓 펀치 충전(파랑), 2 = 파워 블록 방어량(회색 → 금색),
 *           3 = 남은 지속시간(금색), 4 = 재장전(흰색)
 * stacks    조준점 아래 가로 칸 스택, stacksMax 칸 수 (0 = 없음)
 * flags     화면 연출 깃발 (1 = 공격속도 증가 — 화면 가장자리가 노랗게)
 *
 * 바뀐 틱에만 보냅니다 (skill/HudSync).
 */
public record HudPayload(String classId, List<Integer> remaining, List<Integer> total, List<Boolean> active,
						 int ultCharge, int ultState, int ammo, int ammoMax, int meter, int meterKind,
						 int stacks, int stacksMax, int flags) implements CustomPacketPayload {
	public static final Type<HudPayload> TYPE = new Type<>(Overbreak.id("hud"));
	private static final StreamCodec<ByteBuf, List<Integer>> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
	private static final StreamCodec<ByteBuf, List<Boolean>> BOOLS = ByteBufCodecs.BOOL.apply(ByteBufCodecs.list());

	// 칸이 13개라 StreamCodec.composite (최대 12개) 를 쓸 수 없어 직접 씁니다
	public static final StreamCodec<ByteBuf, HudPayload> CODEC = StreamCodec.of(HudPayload::write, HudPayload::read);

	private static void write(ByteBuf buf, HudPayload h) {
		ByteBufCodecs.STRING_UTF8.encode(buf, h.classId);
		INTS.encode(buf, h.remaining);
		INTS.encode(buf, h.total);
		BOOLS.encode(buf, h.active);
		for (int v : new int[] {h.ultCharge, h.ultState, h.ammo, h.ammoMax, h.meter, h.meterKind, h.stacks, h.stacksMax, h.flags}) {
			ByteBufCodecs.VAR_INT.encode(buf, v);
		}
	}

	private static HudPayload read(ByteBuf buf) {
		String classId = ByteBufCodecs.STRING_UTF8.decode(buf);
		List<Integer> remaining = INTS.decode(buf);
		List<Integer> total = INTS.decode(buf);
		List<Boolean> active = BOOLS.decode(buf);
		int[] v = new int[9];
		for (int i = 0; i < v.length; i++) {
			v[i] = ByteBufCodecs.VAR_INT.decode(buf);
		}
		return new HudPayload(classId, remaining, total, active, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
	}

	public static final HudPayload NONE = new HudPayload("", List.of(), List.of(), List.of(), 0, 0, -1, 0, -1, 0, 0, 0, 0);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerPlayer p, HudPayload hud) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, hud);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
