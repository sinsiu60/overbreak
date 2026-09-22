package kr.overbreak.net;

import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 서버 → 클라이언트: 참철 연출 신호 하나로 묶음.
 *
 *   STAGE     모으기 단계가 바뀜 (a = 단계 0~3, b = 진 참 창이면 1) — 같은 월드 모두
 *   HIT       내 평타 · 모아 베기가 누군가를 맞힘 (a = 세기 0 평타 1·2타 · 1 3타 · 2~5 모아 베기 1단~진 참) — 시전자 본인만 (역경직 · 흔들림)
 *   HURT      참철에게 맞음 (a = 세기) — 맞은 사람만 (화면 흔들림)
 *   REND      대지 가르기 발사 (x y z 출발 · yaw 방향) — 같은 월드 모두
 *   ULT       천참 모으기 시작 (x y z 발 · yaw 방향) — 같은 월드 모두
 *   BLOCK     검막으로 막음 (yaw = 맞은 방향) — 같은 월드 모두
 *   CLEAVE    모아 베기 · 천참 판정 순간 (a = 단계 1~4, 5 = 천참) — 같은 월드 모두 (파편 · 충격파)
 *   BASH      어깨 박치기가 적을 들이받음 (x y z 맞은 자리) — 같은 월드 모두 (소리 · 파편)
 *
 * 참철 소리는 전부 클라이언트가 이 신호와 동작 애니메이션을 받아 틉니다 — 서버는 소리를 방송하지 않습니다 (시전자 중복 재생 방지).
 */
public record IronPayload(int kind, int entityId, int a, int b, double x, double y, double z, float yaw) implements CustomPacketPayload {
	public static final int STAGE = 0;
	public static final int HIT = 1;
	public static final int HURT = 2;
	public static final int REND = 3;
	public static final int ULT = 4;
	public static final int BLOCK = 5;
	public static final int CLEAVE = 6;
	public static final int BASH = 7;

	public static final Type<IronPayload> TYPE = new Type<>(Overbreak.id("ironcleaver"));
	public static final StreamCodec<FriendlyByteBuf, IronPayload> CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeVarInt(m.kind);
				buf.writeVarInt(m.entityId);
				buf.writeVarInt(m.a);
				buf.writeVarInt(m.b);
				buf.writeDouble(m.x);
				buf.writeDouble(m.y);
				buf.writeDouble(m.z);
				buf.writeFloat(m.yaw);
			},
			buf -> new IronPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** 같은 월드 모두에게. */
	public static void broadcast(Entity source, IronPayload msg) {
		if (source.level() instanceof net.minecraft.server.level.ServerLevel level) {
			for (ServerPlayer p : level.players()) {
				send(p, msg);
			}
		}
	}

	public static void send(ServerPlayer p, IronPayload msg) {
		if (ServerPlayNetworking.canSend(p, TYPE)) {
			ServerPlayNetworking.send(p, msg);
		}
	}

	public static IronPayload of(int kind, Entity e, int a, int b) {
		return new IronPayload(kind, e.getId(), a, b, e.getX(), e.getY(), e.getZ(), e.getYRot());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
