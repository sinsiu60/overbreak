package kr.overbreak.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.warrior.Warrior;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.input.InputRouter;
import kr.overbreak.net.DialoguePayload;
import kr.overbreak.net.TutorialAckPayload;
import kr.overbreak.net.TutorialCuePayload;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 튜토리얼 — 「코어」가 화면 위 대사창으로 말하고, 써야 할 HUD 칸을 화살표로 가리킵니다.
 *
 *   0 부팅 (가상세계가 켜지는 연출 · 시야가 열림)   1 이동(WASD)   2 도약   3 웅크리기   4 장벽 통과
 *   5 규격 수신 · 체력 · 공격력 · 공격속도   6 평타 5회   7 출혈(패시브)   8 살육   9 광란의 포효
 *   10 피의 사슬   11 광란의 처형장   12 F8 규격 정보   13 마무리 → 완료 · 메인 화면
 *
 * 대사는 {@link DialoguePayload} 로 보냅니다 (모드가 없는 클라이언트는 채팅으로 대신). 목표 검사는 대사가 끝난 뒤에만 합니다.
 * 죽으면 그 단계 자리로 돌아가고 진행도는 유지됩니다. 훈련장 구역을 벗어나면 되돌립니다.
 */
public final class Tutorial {
	public static final String DUMMY_TAG = "overbreak.tut_dummy";

	static final int BOOT = 0;
	static final int MOVE = 1;
	static final int JUMP = 2;
	static final int SNEAK = 3;
	static final int GATE = 4;
	static final int CLASS = 5;
	static final int BASIC = 6;
	static final int BLEED = 7;
	static final int SLAY = 8;
	static final int FURY = 9;
	static final int CHAIN = 10;
	static final int ULT = 11;
	static final int INFO = 12;
	static final int OUTRO = 13;
	static final int DONE = 14;

	/** 부팅 연출 길이 (1/20초 단위). */
	private static final int BOOT_TIME = 100;
	/** 이동 단계 목표 — 움직인 총 거리 (0.1칸 단위). 시작 칸은 장벽으로 막혀 있어 직선 거리로는 못 채웁니다. */
	private static final int MOVE_GOAL = 60;
	private static final Identifier HOLD = Overbreak.id("tutorial_hold");
	private static final String CORE = "코어";

	record Line(String text, int mood, int delay) {}

	static final class Run {
		int stage;
		@Nullable List<Line> seq;
		int line;
		int wait;
		int next;
		/** 단계 목표 카운터 (걸음 · 점프 · 평타 · 적중). */
		int n;
		/** 안내 문구가 액션바를 쓰는 남은 틱. */
		int msg;
		boolean ultSeen;
		boolean infoSeen;
		boolean wasGround = true;
		Vec3 anchor = Vec3.ZERO;
		/** 직전 위치 (움직인 거리를 더하려고). */
		Vec3 last = Vec3.ZERO;
		/** 지금 가리키는 곳 (다시 살아났을 때 되살림). */
		int pointer = -1;
		String pointerLabel = "";
	}

	private static final Map<Integer, List<Line>> LINES = new HashMap<>();
	private static @Nullable Mannequin dummy;
	private static long lastHit;

	/** 대사 묶음 — 글자 수에 맞춰 넘어가는 시간을 스스로 잡습니다 (타자 속도 + 읽을 틈). */
	private static final class Script {
		private final List<Line> list = new ArrayList<>();

		Script say(String text) {
			return add(text, DialoguePayload.MOOD_PLAIN, 0);
		}

		Script sharp(String text) {
			return add(text, DialoguePayload.MOOD_SHARP, 0);
		}

		Script soft(String text) {
			return add(text, DialoguePayload.MOOD_SOFT, 0);
		}

		private Script add(String text, int mood, int extra) {
			// 클라이언트가 1초에 32글자를 찍음 → 글자당 0.625, 문장부호에서 쉬는 몫까지 넉넉히 0.8
			list.add(new Line(text, mood, (int) Math.round(text.length() * 0.8) + 22 + extra));
			return this;
		}
	}

	private static void script(int seq, Script s) {
		LINES.put(seq, List.copyOf(s.list));
	}

	static {
		script(10, new Script()
				.soft("…신호 연결.")
				.soft("생체 반응 정상. 시야 동기화 중이다.")
				.say("눈을 떠라. 여기는 훈련장이다.")
				.say("바깥은 아직 네 이름을 모른다. 그걸 바꾸고 싶다면, 우선 이 안에서 증명해라."));
		script(11, new Script()
				.say("몸부터 확인한다. W A S D - 앞 · 왼쪽 · 뒤 · 오른쪽.")
				.say("장벽이 열리기 전까지, 이 안에서 몸을 움직여 봐라."));
		script(12, new Script()
				.say("스페이스 - 도약. 세 번 뛰어라."));
		script(13, new Script()
				.say("웅크리기. 지금은 자세를 낮출 뿐이지만, 규격을 받으면 그 키는 기술이 된다.")
				.say("1초만 유지해라."));
		script(14, new Script()
				.say("기본 동작 확인. 장벽을 연다.")
				.say("안으로 들어와라."));
		script(15, new Script()
				.sharp("전투 규격을 전송한다.")
				.sharp("「워리어」 - 근접 지속 전투형.")
				.say("왼쪽 아래가 네 체력이다. 275 - 표준 200보다 일흔다섯 칸 두껍다.")
				.say("대신 발이 느리다. 규격마다 몸이 다르다. 두꺼우면 느리고, 빠르면 얇다.")
				.say("공격력 36. 한 번 휘두를 때 실리는 무게다.")
				.say("공격 속도는 1초에 한 번. 때린 직후 1초는 아무리 눌러도 안 맞는다.")
				.say("다만 그 1초에도 스킬은 나간다. 평타와 스킬은 다른 시계를 쓴다.")
				.say("스킬의 시계는 오른쪽 아래 아이콘이다. 다시 밝아지면 쓸 수 있다."));
		script(16, new Script()
				.say("정면에 표적을 배치했다. 그리고 네 체력을 절반으로 덜어냈다. 이유는 곧 알게 된다.")
				.say("좌클릭. 그게 네 손에 쥔 것의 가장 기본적인 사용법이다.")
				.say("표적을 다섯 번 때려라."));
		script(17, new Script()
				.say("표적에 남은 붉은 흔적이 보이나. 그게 출혈이다.")
				.say("평타가 쌓일수록 상처가 짙어지고, 다섯에 도달하면 스스로 터진다.")
				.say("터진 순간 네 체력이 50 돌아왔다. 그게 패시브 「피의 갈망」이다.")
				.say("워리어는 맞은 만큼 죽는 규격이 아니다. 벤 만큼 사는 규격이다."));
		script(18, new Script()
				.sharp("우클릭 - 살육.")
				.say("0.7초간 기를 모은 뒤 주위 3.5칸을 쓸어 벤다. 피해 50, 출혈 2스택.")
				.say("맞힌 적 한 명당 체력 35를 되찾는다. 그걸 보라고 다시 네 피를 덜었다.")
				.say("표적에게 직접 써 봐라."));
		script(19, new Script()
				.sharp("웅크리기 - 광란의 포효.")
				.say("이건 표적을 겨누는 규격이 아니다. 네 자신을 태우는 규격이다.")
				.say("5초 동안 이동속도가 50%, 공격력이 30% 오른다. 스킬 피해까지 함께.")
				.say("덤으로 주위 3칸의 적은 2초간 발이 묶인다. 붙어서 켜라.")
				.say("지금 한 번 켜 봐라."));
		script(20, new Script()
				.sharp("F - 피의 사슬.")
				.say("전방 7칸으로 사슬을 던진다. 처음 걸린 자는 네 발밑까지 끌려온다.")
				.say("도망치는 자를 붙잡고, 뒤에 숨은 자를 앞으로 꺼내는 규격이다.")
				.say("표적을 걸어서 끌어와라. 던지는 것만으로는 끝이 아니다."));
		script(21, new Script()
				.say("마지막 규격이다. 궁극기는 게이지가 가득 찼을 때만 열린다.")
				.say("게이지는 네가 준 피해로 차오른다. 아끼는 자에게는 오지 않는다.")
				.say("이번만 채워주겠다. 다음부터는 스스로 벌어라.")
				.sharp("Q - 광란의 처형장.")
				.say("반경 6칸에 8초간 처형장을 펼친다. 발동 순간 안의 적은 60 피해와 1초 기절.")
				.say("갇힌 적은 밖으로 나갈 수 없고 80% 둔화된다. 너는 오히려 빨라진다.")
				.say("써 봐라. 쓰면 게이지는 0 으로 돌아간다."));
		script(22, new Script()
				.say("수치를 전부 외울 필요는 없다.")
				.say("F8 - 규격 정보. 체력 · 공격력 · 스킬의 모든 수치가 거기 있다.")
				.say("지금 한 번 열어 봐라."));
		script(23, new Script()
				.say("규격 이관 완료. 이제부터 이 몸은 네 것이다.")
				.say("「워리어」는 시작일 뿐이다. 바깥에는 다른 규격들이 기다린다.")
				.say("어떤 것은 하늘을 날고, 어떤 것은 번개가 된다. 전부 네가 고르는 것이다.")
				.sharp("가라. 바깥에서 이름을 만들어라."));
		// 단계를 넘길 때의 짧은 말 (100 + 단계 x 10)
		script(110, new Script().soft("좋다. 다음."));
		script(120, new Script().soft("몸이 가볍군."));
		script(130, new Script().soft("자세 확인."));
		script(160, new Script().say("다섯 번. 손이 기억하게 해라."));
		script(190, new Script().say("켠 순간부터 5초다. 그 안에 결판을 내라. 아끼면 그냥 식는다."));
		script(200, new Script().say("걸었으면 이미 네 거리다. 이제 마지막 하나가 남았다."));
		script(210, new Script().soft("처형장 확인."));
		script(220, new Script().soft("기억해 둬라. 싸우다 막히면 F8."));
	}

	private Tutorial() {}

	static void init() {
		Warrior.SLAY_HIT_HOOKS.add(p -> {
			Run r = Session.of(p).tutorial;
			if (r != null && r.stage == SLAY) {
				r.n = 1;
			}
		});
		Warrior.GRAB_ARRIVE_HOOKS.add((p, e) -> {
			Run r = Session.of(p).tutorial;
			if (r != null && r.stage == CHAIN && e.entityTags().contains(DUMMY_TAG)) {
				r.n = 1;
			}
		});
	}

	/** 클라이언트만 아는 일 (F8 규격 정보를 열었다). */
	public static void onAck(ServerPlayer p, int what) {
		Run r = Session.of(p).tutorial;
		if (r != null && what == TutorialAckPayload.INFO_SCREEN && r.stage == INFO) {
			r.infoSeen = true;
		}
	}

	/** 시험용: 지금 단계 (튜토리얼 중이 아니면 -1). */
	public static int stage(ServerPlayer p) {
		Run r = Session.of(p).tutorial;
		return r == null ? -1 : r.stage;
	}

	/** 시험용: 단계를 건너뛰어 그 단계의 연출을 바로 봄. */
	public static void debugStage(ServerPlayer p, int stage) {
		Run r = Session.of(p).tutorial;
		if (r != null) {
			advance(p, r, stage);
		}
	}

	/** 표적이 맞은 순간 (AFTER_DAMAGE) — 평타 횟수. */
	static void onDamaged(LivingEntity target, DamageSource source) {
		if (target != dummy) {
			return;
		}
		lastHit = target.level().getGameTime();
		if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer p) {
			Run r = Session.of(p).tutorial;
			if (r != null && r.stage == BASIC) {
				r.n++;
			}
		}
	}

	// ── 시작 · 끝 ────────────────────────────────────────────

	static void start(ServerPlayer p) {
		Session s = Session.of(p);
		Classes.clear(p);
		Run r = new Run();
		s.tutorial = r;
		s.place = Session.Place.TUTORIAL;
		p.setGameMode(GameType.ADVENTURE);
		p.setHealth(p.getMaxHealth());
		ServerLevel level = p.level().getServer().overworld();
		// 장벽은 다른 사람이 훈련 중이 아닐 때만 다시 세웁니다 (지나간 사람을 가두지 않게)
		if (running(level) == 1) {
			EnergyBarrier.raise(level);
		}
		Places.TUTORIAL_START.teleport(p);
		r.anchor = p.position();
		r.last = p.position();
		// 가상세계가 켜지는 연출 — 그동안은 발이 묶입니다
		TutorialCuePayload.send(p, TutorialCuePayload.BOOT, BOOT_TIME, "");
		hold(p, true);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		Fx.sound(p, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
		talk(r, 10, MOVE);
		r.wait = 24;
	}

	/** 튜토리얼에서 나감 (메인 화면 · 접속 끊김). 진행도는 저장하지 않습니다 — 다음에는 처음부터. */
	static void stop(ServerPlayer p) {
		Session.of(p).tutorial = null;
		clearScreen(p);
	}

	/** 즉시 완료 처리 (관리자 명령). */
	public static void skip(ServerPlayer p) {
		Session.setTutorialDone(p, true);
		Session.of(p).tutorial = null;
		clearScreen(p);
		p.sendSystemMessage(core("훈련 기록 이관 완료. 규격 제한 해제."));
	}

	private static void clearScreen(ServerPlayer p) {
		hold(p, false);
		DialoguePayload.send(p, DialoguePayload.CLOSE);
		TutorialCuePayload.send(p, TutorialCuePayload.OFF, 0, "");
	}

	private static int running(ServerLevel level) {
		int n = 0;
		for (ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
			if (Session.of(o).tutorial != null) {
				n++;
			}
		}
		return n;
	}

	/** 부팅 연출 동안 발을 묶어 둠. */
	private static void hold(ServerPlayer p, boolean on) {
		if (on) {
			CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			CrowdControl.mod(p, Attributes.JUMP_STRENGTH, HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, HOLD);
			CrowdControl.unmod(p, Attributes.JUMP_STRENGTH, HOLD);
		}
	}

	// ── 매 틱 ───────────────────────────────────────────────

	static void tick(ServerPlayer p, Session s) {
		Run r = s.tutorial;
		if (r == null) {
			return;
		}
		if (r.msg > 0) {
			r.msg--;
		}
		if (!Places.inside(Places.TUTORIAL_ZONE, p)) {
			(r.stage <= GATE ? Places.TUTORIAL_START : Places.TUTORIAL_FIELD).teleport(p);
			if (r.msg <= 0) {
				Hud.actionbar(p, Hud.text("훈련장 밖으로는 나갈 수 없다", ChatFormatting.GRAY));
				r.msg = 40;
			}
		}
		if (r.stage == ULT && Attachments.profile(p).ultCharge >= 100) {
			r.ultSeen = true;
		}
		count(p, r);
		// 대사
		if (r.seq != null && --r.wait <= 0) {
			if (r.line < r.seq.size()) {
				Line line = r.seq.get(r.line++);
				// 묶음의 마지막 줄만 잠시 뒤 스스로 사라짐 — 중간 줄은 다음 줄이 바로 갈아 끼웁니다
				say(p, line, r.line >= r.seq.size() ? 60 : 0);
				r.wait = line.delay();
			} else {
				r.seq = null;
				if (r.next > 0) {
					advance(p, r, r.next);
					if (Session.of(p).tutorial != r) {
						return;
					}
				}
			}
		} else if (r.seq == null) {
			goal(p, r);
		}
		hud(p, r);
	}

	/** 목표 세기 — 대사 중에도 셉니다 (설명을 들으며 움직여도 인정). */
	private static void count(ServerPlayer p, Run r) {
		switch (r.stage) {
			case MOVE -> {
				// 움직인 거리를 더해 갑니다 (앞뒤좌우 아무 쪽이나 — 장벽에 막혀도 채울 수 있게)
				double step = p.position().subtract(r.last).horizontalDistance();
				if (step > 0.02 && step < 3.0) {
					r.n += (int) Math.round(step * 10);
				}
				r.last = p.position();
			}
			case JUMP -> {
				boolean ground = p.onGround();
				if (r.wasGround && !ground && p.getDeltaMovement().y > 0.02) {
					r.n++;
					Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5F, 1.2F + Math.min(3, r.n) * 0.2F);
				}
				r.wasGround = ground;
			}
			case SNEAK -> r.n = p.isShiftKeyDown() ? r.n + 1 : 0;
			default -> {
			}
		}
	}

	/** 표적 관리 — 평타~궁극기 단계인 사람이 있을 때만 세움. */
	static void tickDummy(ServerLevel level) {
		EnergyBarrier.tick(level);
		if (EnergyBarrier.up() && running(level) == 0) {
			EnergyBarrier.lower(level);
		}
		boolean need = false;
		for (ServerPlayer o : level.getServer().getPlayerList().getPlayers()) {
			Run r = Session.of(o).tutorial;
			if (r != null && r.stage >= BASIC && r.stage <= ULT) {
				need = true;
				break;
			}
		}
		if (!need) {
			if (dummy != null) {
				dummy.discard();
				dummy = null;
			}
			return;
		}
		Mannequin v = ensureDummy(level);
		if (v == null) {
			return;
		}
		if (v.getHealth() < 400.0F) {
			v.setHealth(v.getMaxHealth());
		}
		Vec3 home = Places.TUTORIAL_DUMMY;
		if (v.position().distanceToSqr(home) > 16.0 && level.getGameTime() - lastHit > Ticks.of(40)) {
			v.teleportTo(home.x, home.y, home.z);
			v.setDeltaMovement(Vec3.ZERO);
			v.setYRot(90.0F);
			v.setYHeadRot(90.0F);
			v.setYBodyRot(90.0F);
		}
	}

	private static @Nullable Mannequin ensureDummy(ServerLevel level) {
		if (dummy != null && dummy.isAlive() && !dummy.isRemoved()) {
			return dummy;
		}
		Vec3 home = Places.TUTORIAL_DUMMY;
		AABB near = new AABB(home, home).inflate(20.0);
		// 예전 판(주민)이 남아 있으면 치웁니다 — 0.1a 부터 마네킹입니다
		for (Villager old : level.getEntitiesOfClass(Villager.class, near,
				v -> v.entityTags().contains(DUMMY_TAG) || v.entityTags().contains("pvp.tut_dummy"))) {
			old.discard();
		}
		for (Mannequin old : level.getEntitiesOfClass(Mannequin.class, near, v -> v.entityTags().contains(DUMMY_TAG))) {
			old.discard();
		}
		Mannequin v = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
		if (v == null) {
			return null;
		}
		v.snapTo(home.x, home.y, home.z, 90.0F, 0.0F);
		v.addTag(DUMMY_TAG);
		Training.dress(v, Component.literal("훈련용 표적").withStyle(ChatFormatting.GRAY), true);
		level.addFreshEntity(v);
		Fx.particle(level, ParticleTypes.END_ROD, home.x, home.y + 1.0, home.z, 20, 0.3, 0.5, 0.3, 0.03);
		dummy = v;
		return v;
	}

	// ── 대사 · 화살표 ────────────────────────────────────────

	private static void talk(Run r, int seq, int next) {
		r.seq = LINES.get(seq);
		r.line = 0;
		r.wait = 8;
		r.next = next;
	}

	/** 화면 위 대사창으로. 모드가 없는 클라이언트는 채팅으로 대신 받습니다. */
	private static void say(ServerPlayer p, Line line) {
		say(p, line, 0);
	}

	/** @param hold 다 찍은 뒤 남겨 둘 시간 (0 = 다음 줄이 올 때까지 그대로) */
	private static void say(ServerPlayer p, Line line, int hold) {
		if (DialoguePayload.canSend(p)) {
			DialoguePayload.send(p, new DialoguePayload(CORE, line.text(), line.mood(), hold));
		} else {
			p.sendSystemMessage(core(line.text()));
			Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
		}
	}

	private static void point(ServerPlayer p, Run r, int where, String label) {
		r.pointer = where;
		r.pointerLabel = label;
		if (where < 0) {
			TutorialCuePayload.send(p, TutorialCuePayload.CLEAR, 0, "");
		} else {
			TutorialCuePayload.send(p, TutorialCuePayload.POINT, where, label);
		}
	}

	// ── 단계 ────────────────────────────────────────────────

	private static void advance(ServerPlayer p, Run r, int stage) {
		r.seq = null;
		r.stage = stage;
		r.n = 0;
		r.anchor = p.position();
		r.last = p.position();
		r.wasGround = p.onGround();
		PlayerProfile prof = Attachments.profile(p);
		ServerLevel level = p.level().getServer().overworld();
		switch (stage) {
			case MOVE -> {
				hold(p, false);
				point(p, r, -1, "");
				talk(r, 11, 0);
			}
			case JUMP -> talk(r, 12, 0);
			case SNEAK -> talk(r, 13, 0);
			case GATE -> {
				EnergyBarrier.lower(level);
				talk(r, 14, 0);
			}
			case CLASS -> {
				TutorialCuePayload.send(p, TutorialCuePayload.GLITCH, 16, "");
				giveWarrior(p);
				unlocked(p);
				point(p, r, TutorialCuePayload.P_HEALTH, "체력 275");
				talk(r, 15, BASIC);
			}
			case BASIC -> {
				ensureDummy(level);
				hpCut(p);
				point(p, r, TutorialCuePayload.P_WEAPON, "좌클릭");
				talk(r, 16, 0);
			}
			case BLEED -> {
				point(p, r, -1, "");
				talk(r, 17, SLAY);
			}
			case SLAY -> {
				unlocked(p);
				prof.setCooldown("wr_slay", 0);
				hpCut(p);
				point(p, r, TutorialCuePayload.P_SKILL1, "우클릭");
				talk(r, 18, 0);
			}
			case FURY -> {
				unlocked(p);
				prof.setCooldown("wr_fury", 0);
				point(p, r, TutorialCuePayload.P_SKILL2, "웅크리기");
				talk(r, 19, 0);
			}
			case CHAIN -> {
				unlocked(p);
				prof.setCooldown("wr_chain", 0);
				point(p, r, TutorialCuePayload.P_SKILL3, "F");
				talk(r, 20, 0);
			}
			case ULT -> {
				unlocked(p);
				UltGauge.fill(p);
				Fx.sound(p, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
				point(p, r, TutorialCuePayload.P_ULT, "Q");
				talk(r, 21, 0);
			}
			case INFO -> {
				point(p, r, -1, "");
				talk(r, 22, 0);
			}
			case OUTRO -> {
				point(p, r, -1, "");
				Hud.title(p, Hud.bold("OVERBREAK", ChatFormatting.RED), Hud.text("훈련 완료", ChatFormatting.GRAY), 10, 60, 20);
				Fx.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
				Session.setTutorialDone(p, true);
				talk(r, 23, DONE);
			}
			case DONE -> {
				Session.of(p).tutorial = null;
				clearScreen(p);
				Game.toMenu(p);
			}
			default -> {
			}
		}
	}

	private static void goal(ServerPlayer p, Run r) {
		PlayerProfile prof = Attachments.profile(p);
		boolean met = switch (r.stage) {
			case MOVE -> r.n >= MOVE_GOAL;
			case JUMP -> r.n >= 3;
			case SNEAK -> r.n >= 20;
			case GATE -> p.getX() > Places.FENCE_X + 1.5;
			case BASIC -> r.n >= 5;
			case SLAY, CHAIN -> r.n >= 1;
			case FURY -> prof.cooldown("wr_fury") > 0;
			case ULT -> r.ultSeen && prof.ultCharge < 100;
			case INFO -> r.infoSeen;
			default -> false;
		};
		if (!met) {
			return;
		}
		Fx.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.4F);
		r.n = -1000;
		r.ultSeen = false;
		int praise = 100 + r.stage * 10;
		int next = r.stage + 1;
		if (LINES.containsKey(praise)) {
			talk(r, praise, next);
		} else {
			advance(p, r, next);
		}
	}

	/** 입력 게이팅 — 평타 · 우클릭 · 웅크리기 · F · Q 는 각 단계부터. */
	static boolean allow(ServerPlayer p, InputRouter.Slot slot) {
		Run r = Session.of(p).tutorial;
		if (r == null) {
			return true;
		}
		int min = switch (slot) {
			case BASIC -> Tutorial.BASIC;
			case PRIMARY -> SLAY;
			case SECONDARY -> FURY;
			case TERTIARY -> CHAIN;
			case ULT -> Tutorial.ULT;
		};
		if (r.stage >= min) {
			return true;
		}
		Hud.actionbar(p, Hud.text("아직 해제되지 않은 규격이다", ChatFormatting.GRAY));
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, 0.5F);
		r.msg = 40;
		Attachments.profile(p).msgT = 40;
		return false;
	}

	/** 죽음을 막은 다음 틱 — 단계 자리 · 장비 · (궁극기 단계) 게이지 · 화살표 되살림. */
	static void respawn(ServerPlayer p) {
		Run r = Session.of(p).tutorial;
		if (r == null) {
			return;
		}
		PlayerProfile prof = Attachments.profile(p);
		if (r.stage >= CLASS && r.stage <= ULT && (prof.pvpClass == null || !Warrior.ID.equals(prof.pvpClass.id()))) {
			giveWarrior(p);
		}
		if (r.stage == ULT) {
			UltGauge.fill(p);
		} else if (r.stage != BOOT) {
			r.n = Math.max(0, r.n);
		}
		(r.stage <= GATE ? Places.TUTORIAL_START : Places.TUTORIAL_FIELD).teleport(p);
		r.anchor = p.position();
		r.last = p.position();
		if (r.pointer >= 0) {
			TutorialCuePayload.send(p, TutorialCuePayload.POINT, r.pointer, r.pointerLabel);
		}
		say(p, new Line("신호 재연결. 훈련을 이어간다.", DialoguePayload.MOOD_SOFT, 40), 60);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void giveWarrior(ServerPlayer p) {
		// 규격은 코어가 말로 건네주므로 채팅 알림은 생략
		Classes.announce = false;
		try {
			Classes.give(p, Classes.byId(Warrior.ID));
		} finally {
			Classes.announce = true;
		}
	}

	/** 아래쪽 액션바 — 지금 할 일과 진행도. */
	private static void hud(ServerPlayer p, Run r) {
		PlayerProfile prof = Attachments.profile(p);
		if (r.msg > 0 || prof.msgT >= 5) {
			return;
		}
		prof.msgT = 3;
		Component c = switch (r.stage) {
			case MOVE -> progress("W A S D 로 움직이기", Math.min(MOVE_GOAL, Math.max(0, r.n)) / 10, MOVE_GOAL / 10);
			case JUMP -> progress("스페이스로 도약", Math.min(3, Math.max(0, r.n)), 3);
			case SNEAK -> progress("웅크리기 유지", Math.min(20, Math.max(0, r.n)), 20);
			case GATE -> Hud.text("열린 장벽 안으로", ChatFormatting.GRAY);
			case CLASS -> Hud.text("전투 규격 수신 중...", ChatFormatting.GRAY);
			case BASIC -> progress("좌클릭으로 표적 타격", Math.min(5, Math.max(0, r.n)), 5);
			case SLAY -> key("우클릭", "살육", ChatFormatting.RED);
			case FURY -> key("웅크리기", "광란의 포효", ChatFormatting.RED);
			case CHAIN -> Component.empty().append(Hud.text("F - ", ChatFormatting.GRAY)).append(Hud.bold("피의 사슬", ChatFormatting.RED))
					.append(Hud.text("로 표적을 ", ChatFormatting.GRAY)).append(Hud.bold("끌어와라", ChatFormatting.WHITE));
			case ULT -> key("Q", "광란의 처형장", ChatFormatting.GOLD);
			case INFO -> Component.empty().append(Hud.bold("F8", ChatFormatting.AQUA)).append(Hud.text(" - 규격 정보 열기", ChatFormatting.GRAY));
			default -> null;
		};
		if (c != null) {
			Hud.actionbar(p, c);
		}
	}

	private static Component progress(String what, int now, int max) {
		return Component.empty().append(Hud.text(what + "  ", ChatFormatting.GRAY))
				.append(Hud.bold(String.valueOf(now), ChatFormatting.WHITE))
				.append(Hud.text(" / " + max, ChatFormatting.DARK_GRAY));
	}

	private static Component key(String key, String name, ChatFormatting color) {
		return Component.empty().append(Hud.text(key + " - ", ChatFormatting.GRAY)).append(Hud.bold(name, color))
				.append(Hud.text(" 사용", ChatFormatting.GRAY));
	}

	private static void unlocked(ServerPlayer p) {
		Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
		Fx.particle(p.level(), ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
	}

	/** 체력을 절반까지만 덜어냄 (이미 절반 아래면 그대로) — 회복이 눈에 보이게. */
	private static void hpCut(ServerPlayer p) {
		float half = p.getMaxHealth() / 2.0F;
		if (p.getHealth() > half) {
			p.setHealth(half);
		}
	}

	private static Component core(String text) {
		return Component.empty().append(Hud.text("「코어」 ", ChatFormatting.GRAY)).append(Hud.text(text, ChatFormatting.WHITE));
	}
}
