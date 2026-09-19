package kr.overbreak.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 여러 틱에 걸쳐 도는 것들 — 채널링 · 투사체 · 장판 · 파면.
 * 데이터팩에서 마커 엔티티 + 태그로 하던 것을 자바 객체 목록으로 들고 갑니다.
 * 엔티티를 소환하지 않으므로 서버 재시작 때 떠돌이 마커가 남지 않습니다.
 *
 * <p>목록은 절대 도는 도중에 줄이지 않습니다. 끝난 효과는 자리를 null 로 비워 두었다가
 * 아무도 돌고 있지 않을 때 한 번에 걷어냅니다 — 효과 안에서 {@link #cancelOwnedBy}
 * ({@code 승부 결정 → 정리}) 를 불러도 ConcurrentModificationException 이 나지 않게.
 */
public final class Effects {
	/** 한 틱 진행. false 를 돌려주면 목록에서 빠집니다. */
	public interface Active {
		boolean tick();

		/** 사망·해제·초기화로 강제로 끝낼 때. */
		default void cancel() {}

		/** 이 효과의 주인 (정리할 때 주인 기준으로 찾습니다). 없으면 null. */
		default Object owner() {
			return null;
		}
	}

	private static final List<Active> LIST = new ArrayList<>();
	private static final List<Active> ADDED = new ArrayList<>();
	/** 지금 목록을 돌고 있는 깊이 (0 이 아니면 자리를 비우기만 하고 걷어내지 않습니다). */
	private static int walking;

	private Effects() {}

	public static void add(Active a) {
		if (walking > 0) {
			ADDED.add(a);
		} else {
			LIST.add(a);
		}
	}

	public static void tick() {
		walking++;
		try {
			// 도는 중에 늘어난 자리는 다음 틱에 — size() 를 미리 잡아 둡니다
			int n = LIST.size();
			for (int i = 0; i < n; i++) {
				Active a = LIST.get(i);
				if (a == null) {
					continue;
				}
				if (!a.tick() && LIST.get(i) == a) {
					LIST.set(i, null);
				}
			}
		} finally {
			walking--;
			sweep();
		}
	}

	/** 주인이 같은 효과를 전부 취소합니다. */
	public static void cancelOwnedBy(Object owner) {
		walking++;
		try {
			for (int i = 0; i < LIST.size(); i++) {
				Active a = LIST.get(i);
				if (a != null && a.owner() == owner) {
					LIST.set(i, null);
					a.cancel();
				}
			}
			ADDED.removeIf(a -> a.owner() == owner);
		} finally {
			walking--;
			sweep();
		}
	}

	public static void cancelAll() {
		walking++;
		try {
			for (int i = 0; i < LIST.size(); i++) {
				Active a = LIST.get(i);
				if (a != null) {
					LIST.set(i, null);
					a.cancel();
				}
			}
			ADDED.clear();
		} finally {
			walking--;
			sweep();
		}
	}

	/** 아무도 돌고 있지 않을 때만 빈자리를 걷어내고 새로 들어온 것을 넣습니다. */
	private static void sweep() {
		if (walking > 0) {
			return;
		}
		LIST.removeIf(Objects::isNull);
		if (!ADDED.isEmpty()) {
			LIST.addAll(ADDED);
			ADDED.clear();
		}
	}

	/** 시험용: 지금 도는 효과 수. */
	public static int size() {
		int n = 0;
		for (Active a : LIST) {
			if (a != null) {
				n++;
			}
		}
		return n + ADDED.size();
	}
}
