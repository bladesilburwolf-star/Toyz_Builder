package toyz.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic survival crafting — tools and weapons from materials.
 * Inventory slots are simple counts (not full grid yet).
 */
public final class Crafting {

    public enum Item {
        WOOD(0, "Wood"),
        STONE(1, "Stone"),
        IRON_ORE(2, "Iron Ore"),
        STICK(3, "Stick"),
        WOODEN_SWORD(10, "Wooden Sword"),
        STONE_SWORD(11, "Stone Sword"),
        IRON_SWORD(12, "Iron Sword"),
        WOODEN_AXE(20, "Wooden Axe"),
        STONE_AXE(21, "Stone Axe"),
        WOODEN_PICK(30, "Wooden Pick"),
        STONE_PICK(31, "Stone Pick"),
        TORCH(40, "Torch");

        public final int id;
        public final String name;
        Item(int id, String name) { this.id = id; this.name = name; }
    }

    public static class Recipe {
        public final String name;
        public final Item result;
        public final int resultQty;
        public final Item[] needs;
        public final int[] qty;
        public final float damage; // weapon damage bonus, 0 for tools
        public Recipe(String name, Item result, int resultQty, Item[] needs, int[] qty, float damage) {
            this.name = name; this.result = result; this.resultQty = resultQty;
            this.needs = needs; this.qty = qty; this.damage = damage;
        }
    }

    public static class Bag {
        public final int[] count = new int[64]; // by Item.ordinal
        public Item equippedWeapon = null;
        public float weaponDamage = 4f; // fist

        public int get(Item i) { return count[i.ordinal()]; }
        public void add(Item i, int n) { count[i.ordinal()] = Math.max(0, count[i.ordinal()] + n); }
        public boolean has(Item i, int n) { return get(i) >= n; }
        public boolean consume(Item i, int n) {
            if (!has(i, n)) return false;
            add(i, -n);
            return true;
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();
    static {
        RECIPES.add(new Recipe("Stick x4", Item.STICK, 4,
                new Item[]{Item.WOOD}, new int[]{1}, 0));
        RECIPES.add(new Recipe("Wooden Sword", Item.WOODEN_SWORD, 1,
                new Item[]{Item.STICK, Item.WOOD}, new int[]{1, 2}, 8));
        RECIPES.add(new Recipe("Stone Sword", Item.STONE_SWORD, 1,
                new Item[]{Item.STICK, Item.STONE}, new int[]{1, 2}, 14));
        RECIPES.add(new Recipe("Iron Sword", Item.IRON_SWORD, 1,
                new Item[]{Item.STICK, Item.IRON_ORE}, new int[]{1, 3}, 22));
        RECIPES.add(new Recipe("Wooden Axe", Item.WOODEN_AXE, 1,
                new Item[]{Item.STICK, Item.WOOD}, new int[]{2, 3}, 6));
        RECIPES.add(new Recipe("Stone Axe", Item.STONE_AXE, 1,
                new Item[]{Item.STICK, Item.STONE}, new int[]{2, 3}, 10));
        RECIPES.add(new Recipe("Wooden Pick", Item.WOODEN_PICK, 1,
                new Item[]{Item.STICK, Item.WOOD}, new int[]{2, 3}, 5));
        RECIPES.add(new Recipe("Stone Pick", Item.STONE_PICK, 1,
                new Item[]{Item.STICK, Item.STONE}, new int[]{2, 3}, 9));
        RECIPES.add(new Recipe("Torch x4", Item.TORCH, 4,
                new Item[]{Item.STICK, Item.WOOD}, new int[]{1, 1}, 0));
    }

    public static List<Recipe> recipes() { return RECIPES; }

    public static boolean canCraft(Bag bag, Recipe r) {
        for (int i = 0; i < r.needs.length; i++)
            if (!bag.has(r.needs[i], r.qty[i])) return false;
        return true;
    }

    public static boolean craft(Bag bag, Recipe r) {
        if (!canCraft(bag, r)) return false;
        for (int i = 0; i < r.needs.length; i++) bag.consume(r.needs[i], r.qty[i]);
        bag.add(r.result, r.resultQty);
        // auto-equip weapons
        if (r.damage > 0) {
            bag.equippedWeapon = r.result;
            bag.weaponDamage = r.damage;
        }
        return true;
    }

    /** Starter kit for survival mode. */
    public static Bag starterBag() {
        Bag b = new Bag();
        b.add(Item.WOOD, 12);
        b.add(Item.STONE, 8);
        b.add(Item.STICK, 4);
        b.add(Item.WOODEN_SWORD, 1);
        b.equippedWeapon = Item.WOODEN_SWORD;
        b.weaponDamage = 8f;
        return b;
    }
}
