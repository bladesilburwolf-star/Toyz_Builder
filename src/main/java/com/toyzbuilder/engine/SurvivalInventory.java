package com.toyzbuilder.engine;

/**
 * Survival inventory UI — black steel + phosphor green, slot grid + hotbar.
 * Items are simple stacks for now (wood/stone/food/loot placeholders).
 */
public final class SurvivalInventory {

    public static final class Slot {
        public String id;   // null = empty
        public String label;
        public int count;
        public float r = 0.6f, g = 0.6f, b = 0.6f;
        /** Optional GLB under assets/models/ for held-item / future HUD icon. */
        public String modelPath;
    }

    public static final int COLS = 9;
    public static final int ROWS = 3;
    public static final int HOTBAR = 9;
    public static final int SIZE = COLS * ROWS + HOTBAR;

    private final Slot[] slots = new Slot[SIZE];
    private boolean open;
    private int selectedHot = 0;

    // layout cache
    private float panelX, panelY, panelW, panelH;
    private float gridX, gridY, cell, gap;
    private float hotX, hotY;

    private static final float GREEN_R = 0.35f, GREEN_G = 1.0f, GREEN_B = 0.45f;

    public SurvivalInventory() {
        for (int i = 0; i < SIZE; i++) slots[i] = new Slot();
        // starter kit + Morrowind test gear
        give("WOOD", "Wood", 16, 0.55f, 0.40f, 0.22f, null);
        give("STONE", "Stone", 8, 0.55f, 0.52f, 0.48f, null);
        give("FOOD", "Food", 4, 0.85f, 0.35f, 0.25f, null);
        give("TORCH", "Torch", 6, 0.95f, 0.75f, 0.25f, null);
        give("SWORD1", "Sword", 1, 0.70f, 0.70f, 0.75f, "sword1.glb");
        give("AXE1", "Axe", 1, 0.55f, 0.50f, 0.45f, "axe1.glb");
        give("PICK1", "Pickaxe", 1, 0.45f, 0.45f, 0.48f, "pickaxe1.glb");
        give("HAMMER1", "Hammer", 1, 0.50f, 0.48f, 0.50f, "hammer1.glb");
    }

    public void give(String id, String label, int count, float r, float g, float b) {
        give(id, label, count, r, g, b, null);
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean o) { open = o; }
    public void toggle() { open = !open; }
    public int selectedHot() { return selectedHot; }
    public void setSelectedHot(int i) {
        if (i >= 0 && i < HOTBAR) selectedHot = i;
    }
    public Slot[] slots() { return slots; }

    public void give(String id, String label, int count, float r, float g, float b, String modelPath) {
        for (Slot s : slots) {
            if (id.equals(s.id)) {
                s.count += count;
                if (s.modelPath == null) s.modelPath = modelPath;
                return;
            }
        }
        for (Slot s : slots) {
            if (s.id == null) {
                s.id = id;
                s.label = label;
                s.count = count;
                s.r = r; s.g = g; s.b = b;
                s.modelPath = modelPath;
                return;
            }
        }
    }

    /** Currently selected hotbar item (may be empty). */
    public Slot selectedSlot() {
        return slots[COLS * ROWS + selectedHot];
    }

    public void draw(Hud hud, int screenW, int screenH) {
        // always draw hotbar
        float hotSlot = 48f;
        gap = 6f;
        hotX = (screenW - (HOTBAR * hotSlot + (HOTBAR - 1) * gap)) * 0.5f;
        hotY = screenH - 70f;
        for (int i = 0; i < HOTBAR; i++) {
            float x = hotX + i * (hotSlot + gap);
            boolean sel = i == selectedHot;
            hud.rect(x - 2, hotY - 2, hotSlot + 4, hotSlot + 4,
                    sel ? GREEN_R : 0.15f, sel ? GREEN_G : 0.15f, sel ? GREEN_B : 0.15f, 1f);
            hud.rect(x, hotY, hotSlot, hotSlot, 0.06f, 0.07f, 0.06f, 1f);
            Slot s = slots[COLS * ROWS + i];
            if (s.id != null) {
                hud.rect(x + 8, hotY + 8, hotSlot - 16, hotSlot - 16, s.r, s.g, s.b, 1f);
                hud.text(x + 4, hotY + hotSlot - 16, String.valueOf(s.count),
                        GREEN_R, GREEN_G, GREEN_B, 1f);
            }
            // number key hint
            hud.text(x + 4, hotY + 4, String.valueOf(i + 1), 0.4f, 0.55f, 0.4f, 1f);
        }

        if (!open) return;

        panelW = Math.min(520, screenW - 80);
        panelH = 280;
        panelX = (screenW - panelW) * 0.5f;
        panelY = screenH * 0.5f - panelH * 0.5f - 20;

        // steel panel
        hud.rect(panelX, panelY, panelW, panelH, 0.04f, 0.05f, 0.04f, 1f);
        hud.rect(panelX, panelY, panelW, 28, 0.07f, 0.09f, 0.07f, 1f);
        hud.rect(panelX + 2, panelY + 2, panelW - 4, 2, GREEN_R * 0.5f, GREEN_G * 0.5f, GREEN_B * 0.5f, 1f);
        hud.text(panelX + 16, panelY + 8, "SURVIVAL INVENTORY", GREEN_R, GREEN_G, GREEN_B, 1f);
        hud.text(panelX + panelW - 120, panelY + 8, "E CLOSE", 0.4f, 0.55f, 0.4f, 1f);

        cell = 48f;
        gap = 8f;
        float gridW = COLS * cell + (COLS - 1) * gap;
        gridX = panelX + (panelW - gridW) * 0.5f;
        gridY = panelY + 48;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                float x = gridX + col * (cell + gap);
                float y = gridY + row * (cell + gap);
                hud.rect(x, y, cell, cell, 0.08f, 0.09f, 0.08f, 1f);
                hud.rect(x, y, cell, 2, 0.18f, 0.2f, 0.18f, 1f);
                Slot s = slots[idx];
                if (s.id != null) {
                    hud.rect(x + 8, y + 8, cell - 16, cell - 16, s.r, s.g, s.b, 1f);
                    hud.text(x + 4, y + cell - 16, String.valueOf(s.count),
                            GREEN_R, GREEN_G, GREEN_B, 1f);
                }
            }
        }

        hud.text(panelX + 16, panelY + panelH - 28,
                "1-9 HOTBAR   CLICK SLOT TO SELECT", 0.3f, 0.5f, 0.3f, 1f);
    }

    public void handleClick(float mx, float my) {
        if (!open) return;
        // hotbar click
        float hotSlot = 48f;
        for (int i = 0; i < HOTBAR; i++) {
            float x = hotX + i * (hotSlot + gap);
            if (mx >= x && mx <= x + hotSlot && my >= hotY && my <= hotY + hotSlot) {
                selectedHot = i;
                return;
            }
        }
        // bag click — swap with selected hotbar
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                float x = gridX + col * (cell + gap);
                float y = gridY + row * (cell + gap);
                if (mx >= x && mx <= x + cell && my >= y && my <= y + cell) {
                    int hot = COLS * ROWS + selectedHot;
                    Slot a = slots[idx];
                    Slot b = slots[hot];
                    String tid = a.id; String tl = a.label; int tc = a.count;
                    float tr = a.r, tg = a.g, tb = a.b; String tm = a.modelPath;
                    a.id = b.id; a.label = b.label; a.count = b.count;
                    a.r = b.r; a.g = b.g; a.b = b.b; a.modelPath = b.modelPath;
                    b.id = tid; b.label = tl; b.count = tc;
                    b.r = tr; b.g = tg; b.b = tb; b.modelPath = tm;
                    return;
                }
            }
        }
    }
}
