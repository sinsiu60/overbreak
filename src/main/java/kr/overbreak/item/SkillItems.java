package kr.overbreak.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * 직업 아이템 — 설명과 쿨타임 막대를 보여 주는 역할만 합니다 (발동은 조작으로).
 * 데이터팩과 같이 당근 낚싯대에 item_model 로 겉모습만 바꿉니다.
 *
 * 쿨타임 막대는 내구도(max_damage 20)입니다. unbreakable 을 넣으면 막대가 안 보입니다.
 */
public final class SkillItems {
	private static final String ROOT = "overbreak";
	public static final int BAR_STEPS = 20;

	private SkillItems() {}

	public static ItemStack skill(String model, Component name, String skillKey, List<Component> lore) {
		ItemStack s = new ItemStack(Items.CARROT_ON_A_STICK);
		s.set(DataComponents.ITEM_MODEL, modelId(model));
		s.set(DataComponents.ITEM_NAME, name);
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		s.set(DataComponents.MAX_DAMAGE, BAR_STEPS);
		s.set(DataComponents.DAMAGE, 0);
		s.set(DataComponents.LORE, new ItemLore(lore));
		s.set(DataComponents.CUSTOM_DATA, tag(skillKey, false));
		s.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
		return s;
	}

	/** "iron_axe" = 바닐라 모델, "overbreak:gauntlet" = 모드 리소스의 모델. */
	private static Identifier modelId(String model) {
		return model.indexOf(':') >= 0 ? Identifier.parse(model) : Identifier.withDefaultNamespace(model);
	}

	/** 궁극기 아이템 — 막대가 필요 없으므로 내구도 없이 만듭니다. */
	public static ItemStack ult(String model, Component name, List<Component> lore) {
		ItemStack s = new ItemStack(Items.CARROT_ON_A_STICK);
		s.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(model));
		s.set(DataComponents.ITEM_NAME, name);
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		s.set(DataComponents.LORE, new ItemLore(lore));
		s.set(DataComponents.CUSTOM_DATA, tag("ult", true));
		return s;
	}

	/** 인벤토리 좌측 최상단의 직업 스탯표. */
	public static ItemStack statSheet(Component name, List<Component> lore) {
		ItemStack s = new ItemStack(Items.PAPER);
		s.set(DataComponents.ITEM_NAME, name);
		s.set(DataComponents.MAX_STACK_SIZE, 1);
		s.set(DataComponents.LORE, new ItemLore(lore));
		s.set(DataComponents.CUSTOM_DATA, tag("none", false));
		return s;
	}

	private static CustomData tag(String skill, boolean ult) {
		CompoundTag inner = new CompoundTag();
		inner.putBoolean("item", true);
		inner.putBoolean("ult", ult);
		inner.putString("skill", skill);
		CompoundTag root = new CompoundTag();
		root.put(ROOT, inner);
		return CustomData.of(root);
	}

	public static boolean isClassItem(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().contains(ROOT);
	}

	public static boolean isUlt(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getCompoundOrEmpty(ROOT).getBooleanOr("ult", false);
	}

	public static @Nullable String skillKey(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		if (d == null) {
			return null;
		}
		return d.copyTag().getCompoundOrEmpty(ROOT).getStringOr("skill", null);
	}

	/** 직업이 준 아이템 전부 회수 (인벤토리 전체 + 왼손). */
	public static void clearClassItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isClassItem(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	public static void removeUlt(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isUlt(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * 남은 쿨타임을 막대 단계(0~19)로 바꿔 아이템에 반영합니다.
	 * 단계가 그대로면 아이템을 건드리지 않습니다 (슬롯 갱신 패킷 절약).
	 */
	public static int barStep(int remaining, int total) {
		if (remaining <= 0 || total <= 0) {
			return 0;
		}
		return remaining * (BAR_STEPS - 1) / total;
	}

	public static void applyBar(ServerPlayer p, String skillKey, int step) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (skillKey.equals(skillKey(s))) {
				s.set(DataComponents.DAMAGE, step);
				return;
			}
		}
	}

	// ── 설명 문구 도우미 ─────────────────────────────────────

	public static Lore lore() {
		return new Lore();
	}

	public static final class Lore {
		private final List<Component> lines = new ArrayList<>();

		public Lore line(String text, ChatFormatting color) {
			lines.add(plain(text, color));
			return this;
		}

		public Lore bold(String text, ChatFormatting color) {
			lines.add(plain(text, color).withStyle(ChatFormatting.BOLD));
			return this;
		}

		public Lore blank() {
			lines.add(Component.literal("").withStyle(st -> st.withItalic(false)));
			return this;
		}

		public List<Component> build() {
			return List.copyOf(lines);
		}

		private static MutableComponent plain(String text, ChatFormatting color) {
			return Component.literal(text).withStyle(st -> st.withItalic(false)).withStyle(color);
		}
	}

	public static MutableComponent name(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(st -> st.withItalic(false)).withStyle(color);
	}
}
