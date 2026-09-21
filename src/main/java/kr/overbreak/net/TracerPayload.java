package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 서버 → 근처 클라이언트: 총알 궤적 한 줄. 클라이언트가 화면을 향한 2D 빛줄기 이미지로 그립니다 (client/fx/BulletTrails).
 *
 * ownerId 쏜 엔티티 (-1 = 없음) — 본인 1인칭이면 화면 속 총구에서 시작하도록 클라이언트가 맞춥니다
 * from/to 시작점 · 끝점 (몸 · 벽 · 사거리 끝)
 * style   0 = 발키리 (노랑), 1 = 파쇄권 산탄 (흰빛), 2 = 보안관 리볼버 (금색), 3 = 번개 (하늘색 지그재그), 4 = 굵은 벼락, 5 = 건슬링어 쌀권총 (하늘색)
 */
public record TracerPayload(int ownerId, double fx, double fy, double fz, double tx, double ty, double tz, int style)
		implements CustomPacketPayload {
	public static final int VALKYRIE = 0;
	public static final int IRONFIST = 1;
	public static final int SHERIFF = 2;
	public static final int LIGHTNING = 3;
	public static final int LIGHTNING_BIG = 4;
	/** 건슬링어 쌀권총 — 오른손 총 (하늘색). */
	public static final int GUNSLINGER = 5;
	/** 건슬링어 쌀권총 — 왼손 총. 1인칭에서 총구 자리가 반대라 스타일을 나눕니다. */
	public static final int GUNSLINGER_L = 6;
	/** 건슬링어 돌진 난사 — 판정 반경(5칸) 만큼만 뻗는 짧은 하늘색 궤적, 0.1초. */
	public static final int GUNSLINGER_SCATTER = 7;
	private static final double RANGE = 96.0;

	public static final Type<TracerPayload> TYPE = new Type<>(Overbreak.id("tracer"));
	public static final StreamCodec<ByteBuf, TracerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TracerPayload::ownerId,
			ByteBufCodecs.DOUBLE, TracerPayload::fx,
			ByteBufCodecs.DOUBLE, TracerPayload::fy,
			ByteBufCodecs.DOUBLE, TracerPayload::fz,
			ByteBufCodecs.DOUBLE, TracerPayload::tx,
			ByteBufCodecs.DOUBLE, TracerPayload::ty,
			ByteBufCodecs.DOUBLE, TracerPayload::tz,
			ByteBufCodecs.VAR_INT, TracerPayload::style,
			TracerPayload::new);

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerLevel level, @Nullable Entity owner, Vec3 from, Vec3 to, int style) {
		TracerPayload msg = new TracerPayload(owner == null ? -1 : owner.getId(), from.x, from.y, from.z, to.x, to.y, to.z, style);
		for (ServerPlayer viewer : PlayerLookup.around(level, from, RANGE)) {
			if (ServerPlayNetworking.canSend(viewer, TYPE)) {
				ServerPlayNetworking.send(viewer, msg);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
