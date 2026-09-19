package kr.overbreak.classes.hammer;

import org.jspecify.annotations.Nullable;

/** 햄머나이트 한 명의 진행 상태. */
final class HammerState {
	/** 정신집중 중인 지면 분쇄. */
	@Nullable Smash smash;
	/** 돌진 중에 눌러 둔 지면 분쇄 — 돌진이 끝나면 360도로 터집니다. */
	boolean smashQueued;
	/** 기를 모으거나 돌진 중인 돌진 충격. */
	@Nullable Charge charge;
	/** 선동작 중인 중력 파쇄. */
	@Nullable Crush crush;
	/** 정신집중 · 파면이 진행 중인 대지 진동파. */
	@Nullable Quake quake;
}
