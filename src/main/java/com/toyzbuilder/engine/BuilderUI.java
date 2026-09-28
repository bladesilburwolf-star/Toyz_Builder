package com.toyzbuilder.engine;

import java.util.List;

/**
 * Serif System Works style inventory: opaque black-steel panels, phosphor green text.
 * Clickable category tabs + piece grid + hotbar.
 */
public final class BuilderUI {

    private static final int HOTBAR = 10;
    private boolean inventoryOpen = false;

    // layout cache for hit-testing (last draw)
    private float panelX, panelY, panelW, panelH;
    private float gridX, gridY, cell, cellGap;
    private int cols;
    private float hotX, hotY, hotSlot, hotGap;
    private int hotStart;
    private float tabX0, tabY0, tabW, tabH;

    // steel palette (opaque)
    private static final float STEEL0 = 0.04f, STEEL1 = 0.09f, STEEL2 = 0.14f;
    private static final float EDGE = 0.22f;
    private static final float GREEN_R = 0.35f, GREEN_G = 1.0f, GREEN_B = 0.45f;
    private static final float DIM_R = 0.25f, DIM_G = 0.55f, DIM_B = 0.30f;

    public boolean isInventoryOpen() { return inventoryOpen; }

    public void setInventoryOpen(boolean on) { inventoryOpen = on; }

    public void toggleInventory() { inventoryOpen = !inventoryOpen; }

    public void draw(Hud hud, Editor editor, int screenW, int screenH, AssetBank assets) {
        if (editor == null || !editor.isActive()) return;

        PieceCatalog.Def cur = editor.currentPiece();
        PieceCatalog.Group[] groups = PieceCatalog.Group.values();
        List<PieceCatalog.Def> list = PieceCatalog.byGroup(groups[editor.getGroupIndex()]);

        if (inventoryOpen) {
            panelX = 48;
            panelY = 36;
            panelW = screenW - 72;
            panelH = Math.min(560, screenH - 100);
            drawSteelPanel(hud, panelX, panelY, panelW, panelH);

            // title bar
            hud.rect(panelX, panelY, panelW, 30, 0.06f, 0.08f, 0.07f, 1f);
            hud.rect(panelX, panelY + 30, panelW, 2, EDGE, EDGE * 1.1f, EDGE, 1f);
            hud.text(panelX + 14, panelY + 9, "INVENTORY", GREEN_R, GREEN_G, GREEN_B, 1f);
            hud.text(panelX + 120, panelY + 9,
                    "click item  [ ] tabs  scroll  ESC close",
                    DIM_R, DIM_G, DIM_B, 1f);

            // category tabs
            tabX0 = panelX + 12;
            tabY0 = panelY + 40;
            tabW = 112;
            tabH = 26;
            float tx = tabX0;
            for (int g = 0; g < groups.length; g++) {
                boolean on = g == editor.getGroupIndex();
                float bg = on ? 0.16f : 0.08f;
                hud.rect(tx, tabY0, tabW - 4, tabH, bg, bg + 0.02f, bg, 1f);
                if (on) {
                    hud.rect(tx, tabY0 + tabH - 2, tabW - 4, 2, GREEN_R * 0.5f, GREEN_G * 0.7f, GREEN_B * 0.4f, 1f);
                }
                hud.text(tx + 8, tabY0 + 6, groups[g].label,
                        on ? GREEN_R : DIM_R, on ? GREEN_G : DIM_G, on ? GREEN_B : DIM_B, 1f);
                tx += tabW;
            }

            // piece grid
            cell = 64;
            cellGap = 6;
            gridX = panelX + 16;
            gridY = panelY + 82;
            cols = Math.max(1, (int) ((panelW - 32) / (cell + cellGap)));
            int maxRows = Math.max(1, (int) ((panelH - 148) / (cell + cellGap)));
            int maxCells = cols * maxRows;

            for (int i = 0; i < list.size() && i < maxCells; i++) {
                PieceCatalog.Def d = list.get(i);
                int col = i % cols;
                int row = i / cols;
                float x = gridX + col * (cell + cellGap);
                float y = gridY + row * (cell + cellGap);
                boolean on = d.id.equals(cur.id);
                // slot frame
                hud.rect(x, y, cell, cell, on ? 0.18f : 0.07f, on ? 0.22f : 0.08f, on ? 0.16f : 0.07f, 1f);
                if (on) {
                    hud.rect(x, y, cell, 2, GREEN_R, GREEN_G, GREEN_B, 1f);
                    hud.rect(x, y + cell - 2, cell, 2, GREEN_R, GREEN_G, GREEN_B, 1f);
                }
                // Material-backed icon: the same texture used by the world piece.
                float m = 7;
                Texture icon = assets == null ? null : assets.forPiece(d.id);
                hud.rect(x + 4, y + 4, cell - 8, cell - 22, 0.03f, 0.04f, 0.04f, 1f);
                if (icon != null) {
                    hud.image(icon, x + m, y + m, cell - m * 2, cell - m * 2 - 14,
                            1f, 1f, 1f, 1f);
                } else {
                    hud.rect(x + m, y + m, cell - m * 2, cell - m * 2 - 14, d.r, d.g, d.b, 1f);
                }
                String lab = d.label.length() > 10 ? d.label.substring(0, 10) : d.label;
                hud.text(x + 4, y + cell - 12, lab,
                        on ? GREEN_R : 0.72f, on ? GREEN_G : 0.78f, on ? GREEN_B : 0.72f, 1f);
            }

            // footer status
            hud.rect(panelX, panelY + panelH - 26, panelW, 26, 0.05f, 0.06f, 0.05f, 1f);
            hud.text(panelX + 12, panelY + panelH - 18,
                    String.format("%s   %d items   yaw %.0f   snap %.2f   %s",
                            cur.label, list.size(), editor.getPlaceYaw(), editor.getSnap(), editor.getStatus()),
                    GREEN_R, GREEN_G, GREEN_B, 1f);
        }

        // hotbar — always in edit mode, opaque
        hotSlot = 48;
        hotGap = 4;
        float total = HOTBAR * (hotSlot + hotGap);
        hotX = (screenW - total) * 0.5f;
        hotY = screenH - 68;
        hud.rect(hotX - 10, hotY - 10, total + 16, hotSlot + 20, STEEL0, STEEL1, STEEL0, 1f);
        hud.rect(hotX - 10, hotY - 10, total + 16, 2, EDGE, EDGE, EDGE, 1f);

        hotStart = Math.max(0, Math.min(list.size() - HOTBAR, indexInList(list, cur.id) - HOTBAR / 2));
        for (int i = 0; i < HOTBAR; i++) {
            float x = hotX + i * (hotSlot + hotGap);
            boolean has = hotStart + i < list.size();
            PieceCatalog.Def d = has ? list.get(hotStart + i) : null;
            boolean on = has && d.id.equals(cur.id);
            hud.rect(x, hotY, hotSlot, hotSlot, on ? 0.16f : 0.07f, on ? 0.20f : 0.08f, on ? 0.14f : 0.07f, 1f);
            if (on) {
                hud.rect(x, hotY, hotSlot, 2, GREEN_R, GREEN_G, GREEN_B, 1f);
            }
            if (has) {
                Texture icon = assets == null ? null : assets.forPiece(d.id);
                if (icon != null) {
                    hud.image(icon, x + 8, hotY + 8, hotSlot - 16, hotSlot - 16, 1f, 1f, 1f, 1f);
                } else {
                    hud.rect(x + 10, hotY + 10, hotSlot - 20, hotSlot - 20, d.r, d.g, d.b, 1f);
                }
            }
            String key = i == 9 ? "0" : String.valueOf(i + 1);
            hud.text(x + 3, hotY + 2, key, GREEN_R, GREEN_G, GREEN_B, 1f);
        }
    }

    private void drawSteelPanel(Hud hud, float x, float y, float w, float h) {
        // flat opaque steel — layered bands (no transparency)
        hud.rect(x, y, w, h, STEEL0, STEEL0 + 0.01f, STEEL0, 1f);
        hud.rect(x, y, w, h * 0.35f, STEEL1, STEEL1 + 0.02f, STEEL1, 1f);
        hud.rect(x, y + h * 0.35f, w, h * 0.35f, STEEL0 + 0.02f, STEEL1, STEEL0 + 0.02f, 1f);
        // border
        hud.rect(x, y, w, 2, EDGE, EDGE, EDGE, 1f);
        hud.rect(x, y + h - 2, w, 2, EDGE * 0.6f, EDGE * 0.6f, EDGE * 0.6f, 1f);
        hud.rect(x, y, 2, h, EDGE, EDGE, EDGE, 1f);
        hud.rect(x + w - 2, y, 2, h, EDGE * 0.6f, EDGE * 0.6f, EDGE * 0.6f, 1f);
    }

    /**
     * Click handler in screen space. Returns true if a UI element consumed the click
     * (so world place should not fire).
     */
    public boolean handleClick(Editor editor, float mx, float my) {
        if (editor == null || !editor.isActive()) return false;

        PieceCatalog.Group[] groups = PieceCatalog.Group.values();
        List<PieceCatalog.Def> list = PieceCatalog.byGroup(groups[editor.getGroupIndex()]);

        // hotbar click
        for (int i = 0; i < HOTBAR; i++) {
            float x = hotX + i * (hotSlot + hotGap);
            if (mx >= x && mx <= x + hotSlot && my >= hotY && my <= hotY + hotSlot) {
                int idx = hotStart + i;
                if (idx >= 0 && idx < list.size()) {
                    editor.setSelectedIndex(PieceCatalog.indexOf(list.get(idx).id));
                }
                return true;
            }
        }

        if (!inventoryOpen) return false;

        // tab click
        for (int g = 0; g < groups.length; g++) {
            float x = tabX0 + g * tabW;
            if (mx >= x && mx <= x + tabW - 4 && my >= tabY0 && my <= tabY0 + tabH) {
                editor.setGroupIndex(g);
                return true;
            }
        }

        // grid click
        if (cols > 0 && cell > 0) {
            int maxRows = Math.max(1, (int) ((panelH - 100) / (cell + cellGap)));
            int maxCells = cols * maxRows;
            for (int i = 0; i < list.size() && i < maxCells; i++) {
                int col = i % cols;
                int row = i / cols;
                float x = gridX + col * (cell + cellGap);
                float y = gridY + row * (cell + cellGap);
                if (mx >= x && mx <= x + cell && my >= y && my <= y + cell) {
                    editor.setSelectedIndex(PieceCatalog.indexOf(list.get(i).id));
                    return true;
                }
            }
        }

        // click on panel body still consumes (don't place through UI)
        if (mx >= panelX && mx <= panelX + panelW && my >= panelY && my <= panelY + panelH) {
            return true;
        }
        return false;
    }

    public void selectHotbar(Editor editor, int slot0to9) {
        List<PieceCatalog.Def> list = PieceCatalog.byGroup(
                PieceCatalog.Group.values()[editor.getGroupIndex()]);
        PieceCatalog.Def cur = editor.currentPiece();
        int start = Math.max(0, Math.min(list.size() - HOTBAR, indexInList(list, cur.id) - HOTBAR / 2));
        int idx = start + slot0to9;
        if (idx >= 0 && idx < list.size()) {
            editor.setSelectedIndex(PieceCatalog.indexOf(list.get(idx).id));
        }
    }

    private static int indexInList(List<PieceCatalog.Def> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equalsIgnoreCase(id)) return i;
        return 0;
    }
}
