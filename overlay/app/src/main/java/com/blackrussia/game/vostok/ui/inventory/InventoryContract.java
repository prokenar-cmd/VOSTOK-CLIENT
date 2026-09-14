package com.blackrussia.game.vostok.ui.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable client-side contract for the VOSTOK inventory surface.
 *
 * 031G intentionally does not invent an item database. Until an item receives
 * tuned balance data, one unit weighs 100 grams. Real item definitions and
 * icons can be added later without changing the UI contract.
 */
public final class InventoryContract {
    public static final int DEFAULT_ITEM_WEIGHT_GRAMS = 100;
    public static final int DEFAULT_CAPACITY_GRAMS = 30_000;
    public static final int VISIBLE_SLOT_COUNT = 20;

    public static final int CATEGORY_GENERIC = 0;
    public static final int CATEGORY_FOOD = 1;
    public static final int CATEGORY_DRINK = 2;
    public static final int CATEGORY_MEDICAL = 3;
    public static final int CATEGORY_TOOL = 4;
    public static final int CATEGORY_CLOTHING = 5;
    public static final int CATEGORY_ACCESSORY = 6;
    public static final int CATEGORY_KEY = 7;
    public static final int CATEGORY_RESOURCE = 8;
    public static final int CATEGORY_ELECTRONICS = 9;

    public static final int EQUIP_NONE = 0;
    public static final int EQUIP_HEAD = 1;
    public static final int EQUIP_FACE = 2;
    public static final int EQUIP_TORSO = 3;
    public static final int EQUIP_HANDS = 4;
    public static final int EQUIP_BACK = 5;

    public static final int ACTION_USE = 1;
    public static final int ACTION_GIVE = 2;
    public static final int ACTION_DROP = 3;
    public static final int ACTION_EQUIP = 4;
    public static final int ACTION_UNEQUIP = 5;

    public interface ActionSink {
        void onInventoryAction(int action, Item item);
    }

    public static final class Item {
        public final int itemId;
        public final String name;
        public final String description;
        public final int count;
        public final int weightGrams;
        public final int category;
        public final int equipmentSlot;
        public final boolean equipped;
        public final boolean canUse;
        public final boolean canGive;
        public final boolean canDrop;
        public final String iconKey;

        public Item(int itemId,
                    String name,
                    String description,
                    int count,
                    int weightGrams,
                    int category,
                    int equipmentSlot,
                    boolean equipped,
                    boolean canUse,
                    boolean canGive,
                    boolean canDrop,
                    String iconKey) {
            this.itemId = itemId;
            this.name = clean(name, "Предмет");
            this.description = clean(description, "Описание предмета пока не задано.");
            this.count = Math.max(1, count);
            this.weightGrams = weightGrams > 0 ? weightGrams : DEFAULT_ITEM_WEIGHT_GRAMS;
            this.category = category;
            this.equipmentSlot = equipmentSlot;
            this.equipped = equipped;
            this.canUse = canUse;
            this.canGive = canGive;
            this.canDrop = canDrop;
            this.iconKey = iconKey == null ? "" : iconKey.trim();
        }

        public int totalWeightGrams() {
            long total = (long) count * (long) weightGrams;
            return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        }
    }

    public static final class Snapshot {
        public final List<Item> items;
        public final int maxWeightGrams;

        public Snapshot(List<Item> items, int maxWeightGrams) {
            List<Item> copy = new ArrayList<>();
            if (items != null) {
                for (Item item : items) {
                    if (item != null) copy.add(item);
                    if (copy.size() >= VISIBLE_SLOT_COUNT) break;
                }
            }
            this.items = Collections.unmodifiableList(copy);
            this.maxWeightGrams = maxWeightGrams > 0
                    ? maxWeightGrams : DEFAULT_CAPACITY_GRAMS;
        }

        public int totalWeightGrams() {
            long total = 0;
            for (Item item : items) total += item.totalWeightGrams();
            return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        }

        public static Snapshot empty() {
            return new Snapshot(Collections.emptyList(), DEFAULT_CAPACITY_GRAMS);
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private InventoryContract() {
    }
}
