package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Survival-only inventory + hotbar (separate from builder Hotbar/Inventory).
 * Slots hold Crafting.Item stacks. Weapons/tools are placeholders for future GLB.
 */
public final class SurvivalInv {

    public static final int HOTBAR_SLOTS = 9;
    public static final int BAG_SLOTS = 27; // 9x3

    public static final class Slot {
        public Crafting.Item item;
        public int count;
        public Slot() { clear(); }
        public void clear() { item = null; count = 0; }
        public boolean isEmpty() { return item == null || count <= 0; }
        public void set(Crafting.Item it, int n) {
            if (it == null || n <= 0) { clear(); return; }
            item = it; count = n;
        }
    }

    public static final class State {
        public final Slot[] hotbar = new Slot[HOTBAR_SLOTS];
        public final Slot[] bag = new Slot[BAG_SLOTS];
        public int selected = 0;
        public boolean isOpen = false;
        public int tab; // 0 items, 1 weapons, 2 tools, 3 gadgets

        public State() {
            for (int i = 0; i < HOTBAR_SLOTS; i++) hotbar[i] = new Slot();
            for (int i = 0; i < BAG_SLOTS; i++) bag[i] = new Slot();
        }
    }

    private SurvivalInv() {}

    /** Seed from Crafting.Bag starter kit into hotbar + bag. */
    public static State fromBag(Crafting.Bag bag) {
        State s = new State();
        // weapons first in hotbar
        if (bag.equippedWeapon != null) {
            s.hotbar[0].set(bag.equippedWeapon, Math.max(1, bag.get(bag.equippedWeapon)));
        }
        int hi = 1;
        Crafting.Item[] order = {
            Crafting.Item.WOODEN_AXE, Crafting.Item.STONE_AXE,
            Crafting.Item.WOODEN_PICK, Crafting.Item.STONE_PICK,
            Crafting.Item.TORCH, Crafting.Item.STICK,
            Crafting.Item.WOOD, Crafting.Item.STONE, Crafting.Item.IRON_ORE
        };
        for (Crafting.Item it : order) {
            int n = bag.get(it);
            if (n <= 0) continue;
            if (hi < HOTBAR_SLOTS && s.hotbar[hi].isEmpty()) {
                s.hotbar[hi++].set(it, n);
            } else {
                addToBag(s, it, n);
            }
        }
        // leftover counts from bag map
        for (Crafting.Item it : Crafting.Item.values()) {
            int n = bag.get(it);
            if (n <= 0) continue;
            if (alreadyStored(s, it)) continue;
            addToBag(s, it, n);
        }
        s.selected = 0;
        return s;
    }

    private static boolean alreadyStored(State s, Crafting.Item it) {
        for (Slot sl : s.hotbar) if (!sl.isEmpty() && sl.item == it) return true;
        for (Slot sl : s.bag) if (!sl.isEmpty() && sl.item == it) return true;
        return false;
    }

    public static void addToBag(State s, Crafting.Item it, int n) {
        if (it == null || n <= 0) return;
        // stack existing
        for (Slot sl : s.hotbar) {
            if (!sl.isEmpty() && sl.item == it) { sl.count += n; return; }
        }
        for (Slot sl : s.bag) {
            if (!sl.isEmpty() && sl.item == it) { sl.count += n; return; }
        }
        for (Slot sl : s.bag) {
            if (sl.isEmpty()) { sl.set(it, n); return; }
        }
        for (Slot sl : s.hotbar) {
            if (sl.isEmpty()) { sl.set(it, n); return; }
        }
    }

    public static Slot held(State s) {
        if (s.selected < 0 || s.selected >= HOTBAR_SLOTS) return null;
        return s.hotbar[s.selected];
    }

    public static Crafting.Item heldItem(State s) {
        Slot sl = held(s);
        return (sl == null || sl.isEmpty()) ? null : sl.item;
    }

    public static float heldDamage(State s) {
        Crafting.Item it = heldItem(s);
        if (it == null) return 2f;
        return Crafting.damageOf(it);
    }

    // ---- Drawing ----

    public static void drawHotbar(State s) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        float slot = 48f, gap = 4f;
        float total = HOTBAR_SLOTS * slot + (HOTBAR_SLOTS - 1) * gap;
        float x0 = (sw - total) * 0.5f;
        float y = sh - slot - 18f;

        DrawRectangle((int) (x0 - 8), (int) (y - 8), (int) (total + 16), (int) (slot + 16),
                Helpers.newColor(10, 12, 16, 200));

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            float x = x0 + i * (slot + gap);
            Rectangle r = Helpers.newRectangle(x, y, slot, slot);
            boolean sel = i == s.selected;
            DrawRectangleRec(r, Helpers.newColor(22, 24, 30, 255));
            DrawRectangleLinesEx(r, sel ? 2.5f : 1f, sel ? UI.phosphor() : Helpers.newColor(60, 70, 80, 255));
            Slot sl = s.hotbar[i];
            if (!sl.isEmpty()) {
                drawItemIcon(sl.item, x + 6, y + 4, 36);
                if (sl.count > 1) {
                    DrawText(String.valueOf(sl.count), (int) (x + 4), (int) (y + slot - 16), 14, WHITE);
                }
            }
            DrawText(String.valueOf(i + 1), (int) (x + 2), (int) (y + 2), 12, UI.phosphorDim());
        }
        DrawText("SURVIVAL  //  1-9 equip  E bag  C craft", (int) x0, (int) (y - 22), 13, UI.phosphorDim());
    }

    public static void drawInventory(State s) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        float pw = 520, ph = 420;
        Rectangle panel = Helpers.newRectangle((sw - pw) * 0.5f, (sh - ph) * 0.5f, pw, ph);
        UI.drawSteelPanel(panel, "SURVIVAL INVENTORY  //  LIGHT WORLD");

        String[] tabs = { "ALL", "WEAPONS", "TOOLS", "GADGETS" };
        float tx = panel.x() + 16, ty = panel.y() + 40;
        for (int i = 0; i < tabs.length; i++) {
            Rectangle tab = Helpers.newRectangle(tx + i * 100, ty, 92, 28);
            boolean on = s.tab == i;
            if (UI.drawSteelButton(tab, tabs[i], on)) s.tab = i;
        }

        float gx = panel.x() + 24, gy = panel.y() + 88;
        float cell = 52, gap = 6;
        for (int i = 0; i < BAG_SLOTS; i++) {
            int col = i % 9, row = i / 9;
            float x = gx + col * (cell + gap);
            float y = gy + row * (cell + gap);
            Rectangle r = Helpers.newRectangle(x, y, cell, cell);
            DrawRectangleRec(r, Helpers.newColor(18, 20, 26, 255));
            DrawRectangleLinesEx(r, 1f, Helpers.newColor(50, 60, 70, 255));
            Slot sl = s.bag[i];
            if (!sl.isEmpty() && passesTab(s.tab, sl.item)) {
                drawItemIcon(sl.item, x + 6, y + 4, 40);
                if (sl.count > 1)
                    DrawText(String.valueOf(sl.count), (int) (x + 4), (int) (y + cell - 16), 14, WHITE);
            }
        }

        // hotbar strip inside panel
        DrawText("HOTBAR", (int) gx, (int) (gy + 3 * (cell + gap) + 12), 14, UI.phosphor());
        float hy = gy + 3 * (cell + gap) + 32;
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            float x = gx + i * (cell + gap);
            Rectangle r = Helpers.newRectangle(x, hy, cell, cell);
            boolean sel = i == s.selected;
            DrawRectangleRec(r, Helpers.newColor(22, 24, 30, 255));
            DrawRectangleLinesEx(r, sel ? 2f : 1f, sel ? UI.phosphor() : Helpers.newColor(60, 70, 80, 255));
            Slot sl = s.hotbar[i];
            if (!sl.isEmpty()) {
                drawItemIcon(sl.item, x + 6, hy + 4, 40);
                if (sl.count > 1)
                    DrawText(String.valueOf(sl.count), (int) (x + 4), (int) (hy + cell - 16), 14, WHITE);
            }
        }

        DrawText("E / ESC close   |   placeholders → GLB later", (int) panel.x() + 16,
                (int) (panel.y() + ph - 28), 13, UI.phosphorDim());
    }

    private static boolean passesTab(int tab, Crafting.Item it) {
        if (tab == 0) return true;
        Crafting.Kind k = Crafting.kindOf(it);
        if (tab == 1) return k == Crafting.Kind.WEAPON;
        if (tab == 2) return k == Crafting.Kind.TOOL;
        if (tab == 3) return k == Crafting.Kind.GADGET || k == Crafting.Kind.MATERIAL;
        return true;
    }

    /** Icon: prefer texture, else colored tile + letter (GLB placeholder). */
    public static void drawItemIcon(Crafting.Item it, float x, float y, float size) {
        if (it == null) return;
        Texture tex = AssetBank.forCraftResult(it);
        if (tex != null && tex.id() != 0) {
            float sc = size / Math.max(tex.width(), 1);
            DrawTextureEx(tex, Helpers.newVector2(x, y), 0f, sc, WHITE);
            return;
        }
        Color bg = Crafting.colorOf(it);
        DrawRectangle((int) x, (int) y, (int) size, (int) size, bg);
        DrawRectangleLines((int) x, (int) y, (int) size, (int) size, UI.phosphorDim());
        String letter = it.name.length() > 0 ? it.name.substring(0, 1) : "?";
        DrawText(letter, (int) (x + size * 0.3f), (int) (y + size * 0.25f), (int) (size * 0.45f), WHITE);
        // GLB badge
        if (it.modelPlaceholder) {
            DrawText("GLB", (int) x + 2, (int) (y + size - 12), 10, Helpers.newColor(255, 220, 80, 220));
        }
    }

    public static void handleHotbarKeys(State s) {
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (IsKeyPressed(KEY_ONE + i)) s.selected = i;
        }
        float wheel = GetMouseWheelMove();
        if (wheel != 0) {
            s.selected = (s.selected - (int) Math.signum(wheel) + HOTBAR_SLOTS) % HOTBAR_SLOTS;
        }
    }
}
