package toyz.builder;

import com.raylib.Helpers;
import com.raylib.Raylib.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Materials, tools, weapons, gadgets for Survival.
 * modelPlaceholder = true → GLB path reserved under assets/models/ when authored.
 */
public final class Crafting {

    public enum Kind { MATERIAL, WEAPON, TOOL, GADGET }

    public enum Item {
        WOOD("Wood", Kind.MATERIAL, false, "assets/models/item_wood.glb"),
        STONE("Stone", Kind.MATERIAL, false, "assets/models/item_stone.glb"),
        IRON_ORE("Iron Ore", Kind.MATERIAL, false, "assets/models/item_iron.glb"),
        STICK("Stick", Kind.MATERIAL, false, "assets/models/item_stick.glb"),

        WOODEN_SWORD("Wooden Sword", Kind.WEAPON, true, "assets/models/weapon_wood_sword.glb"),
        STONE_SWORD("Stone Sword", Kind.WEAPON, true, "assets/models/weapon_stone_sword.glb"),
        IRON_SWORD("Iron Sword", Kind.WEAPON, true, "assets/models/weapon_iron_sword.glb"),

        WOODEN_AXE("Wooden Axe", Kind.TOOL, true, "assets/models/tool_wood_axe.glb"),
        STONE_AXE("Stone Axe", Kind.TOOL, true, "assets/models/tool_stone_axe.glb"),
        WOODEN_PICK("Wooden Pick", Kind.TOOL, true, "assets/models/tool_wood_pick.glb"),
        STONE_PICK("Stone Pick", Kind.TOOL, true, "assets/models/tool_stone_pick.glb"),

        TORCH("Torch", Kind.GADGET, true, "assets/models/gadget_torch.glb"),
        BOW("Bow", Kind.WEAPON, true, "assets/models/weapon_bow.glb"),
        BOMB("Bomb", Kind.GADGET, true, "assets/models/gadget_bomb.glb"),
        LANTERN("Lantern", Kind.GADGET, true, "assets/models/gadget_lantern.glb"),
        SHIELD("Shield", Kind.GADGET, true, "assets/models/gadget_shield.glb");

        public final String name;
        public final Kind kind;
        public final boolean modelPlaceholder;
        public final String modelPath;

        Item(String name, Kind kind, boolean modelPlaceholder, String modelPath) {
            this.name = name;
            this.kind = kind;
            this.modelPlaceholder = modelPlaceholder;
            this.modelPath = modelPath;
        }
    }

    public static Kind kindOf(Item it) { return it == null ? Kind.MATERIAL : it.kind; }

    public static float damageOf(Item it) {
        if (it == null) return 2f;
        switch (it) {
            case WOODEN_SWORD: return 8f;
            case STONE_SWORD: return 14f;
            case IRON_SWORD: return 22f;
            case WOODEN_AXE: return 6f;
            case STONE_AXE: return 10f;
            case WOODEN_PICK: return 5f;
            case STONE_PICK: return 9f;
            case BOW: return 12f;
            case BOMB: return 30f;
            default: return 2f;
        }
    }

    public static Color colorOf(Item it) {
        if (it == null) return Helpers.newColor(80, 80, 90, 255);
        switch (it.kind) {
            case WEAPON: return Helpers.newColor(180, 60, 60, 255);
            case TOOL: return Helpers.newColor(80, 140, 200, 255);
            case GADGET: return Helpers.newColor(200, 160, 40, 255);
            default: return Helpers.newColor(90, 120, 70, 255);
        }
    }

    public static final class Recipe {
        public final String name;
        public final Item result;
        public final int resultQty;
        public final Item[] needs;
        public final int[] qty;
        public final float damage;

        public Recipe(String name, Item result, int resultQty, Item[] needs, int[] qty, float damage) {
            this.name = name;
            this.result = result;
            this.resultQty = resultQty;
            this.needs = needs;
            this.qty = qty;
            this.damage = damage;
        }
    }

    public static final class Bag {
        private final Map<Item, Integer> counts = new HashMap<>();
        public Item equippedWeapon;
        public float weaponDamage = 2f;

        public int get(Item i) { return counts.getOrDefault(i, 0); }
        public void add(Item i, int n) {
            if (i == null) return;
            int v = get(i) + n;
            if (v <= 0) counts.remove(i); else counts.put(i, v);
        }
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
        RECIPES.add(new Recipe("Bow", Item.BOW, 1,
                new Item[]{Item.STICK, Item.WOOD}, new int[]{3, 2}, 12));
        RECIPES.add(new Recipe("Bomb x2", Item.BOMB, 2,
                new Item[]{Item.STONE, Item.IRON_ORE}, new int[]{2, 1}, 30));
        RECIPES.add(new Recipe("Lantern", Item.LANTERN, 1,
                new Item[]{Item.IRON_ORE, Item.TORCH}, new int[]{1, 1}, 0));
        RECIPES.add(new Recipe("Shield", Item.SHIELD, 1,
                new Item[]{Item.WOOD, Item.IRON_ORE}, new int[]{4, 2}, 0));
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
        if (r.damage > 0) {
            bag.equippedWeapon = r.result;
            bag.weaponDamage = r.damage;
        }
        return true;
    }

    public static Bag starterBag() {
        Bag b = new Bag();
        b.add(Item.WOOD, 12);
        b.add(Item.STONE, 8);
        b.add(Item.STICK, 4);
        b.add(Item.WOODEN_SWORD, 1);
        b.add(Item.TORCH, 2);
        b.equippedWeapon = Item.WOODEN_SWORD;
        b.weaponDamage = 8f;
        return b;
    }
}
