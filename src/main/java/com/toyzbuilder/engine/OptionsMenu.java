package com.toyzbuilder.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * OptiFine-style options screens (Performance / Quality / Controls), drawn with Hud rects+glyphs.
 * Owned by MainMenu. Every change calls GameSettings.touch(); Window applies + saves it.
 *
 * Hint text: the Hud glyph font is uppercase-only 5x7, ~48 chars fit per line at scale 2.
 */
public final class OptionsMenu {

    private enum Page { ROOT, PERFORMANCE, QUALITY, CONTROLS }

    private static final float G_R = 0.35f, G_G = 1.0f, G_B = 0.45f;
    private static final float D_R = 0.25f, D_G = 0.55f, D_B = 0.30f;
    private static final float L_R = 0.32f, L_G = 0.34f, L_B = 0.32f; // locked / greyed

    private static final float PANEL_W = 640f, ROW_H = 34f, TOP = 60f;

    private final GameSettings s = GameSettings.get();

    private Page page = Page.ROOT;
    private int index = 0;
    private final List<Row> root = new ArrayList<>();
    private final List<Row> perf = new ArrayList<>();
    private final List<Row> quality = new ArrayList<>();
    private final List<Row> controls = new ArrayList<>();

    /** One menu line. adjust(+1/-1); dir 0 = "activate" (Enter on a page/button row). */
    private static final class Row {
        final String label;
        final Supplier<String> value;   // null = button/page row
        final IntConsumer adjust;
        final boolean locked;
        final boolean marksCustom;      // changing this row flips preset to CUSTOM
        final String[] hint;

        Row(String label, Supplier<String> value, IntConsumer adjust, boolean locked,
            boolean marksCustom, String... hint) {
            this.label = label; this.value = value; this.adjust = adjust;
            this.locked = locked; this.marksCustom = marksCustom; this.hint = hint;
        }
    }

    public OptionsMenu() {
        buildRoot();
        buildPerformance();
        buildQuality();
        buildControls();
    }

    // ------------------------------------------------------------------ rows

    private void buildRoot() {
        root.add(button("PERFORMANCE...", () -> open(Page.PERFORMANCE),
                "VSYNC, FPS CAP, RENDER DISTANCE, FOG,", "TREES AND OTHER SPEED SETTINGS."));
        root.add(button("QUALITY...", () -> open(Page.QUALITY),
                "TRANSPARENCY, SHADOWS, REFLECTIONS,", "TEXTURE FILTERING, FOV."));
        root.add(button("CONTROLS...", () -> open(Page.CONTROLS),
                "MOUSE SENSITIVITY."));
        root.add(button("RESET ALL TO DEFAULTS", () -> { s.resetDefaults(); },
                "RESTORES THE LOW-END GPU DEFAULTS."));
        root.add(button("DONE", null, "BACK TO MAIN MENU."));
    }

    private void buildPerformance() {
        perf.add(new Row("PRESET",
                () -> GameSettings.PRESET_LABELS[s.preset],
                d -> { int p = s.preset == GameSettings.PRESET_CUSTOM ? (d > 0 ? 0 : 3)
                        : Math.floorMod(s.preset + (d == 0 ? 1 : d), 4);
                       s.applyPreset(p); },
                false, false,
                "QUICK PROFILES. POTATO IS FASTEST.", "NONE OF THEM ENABLE TRANSPARENCY,", "SHADOWS OR REFLECTIONS."));
        perf.add(new Row("VSYNC", () -> onOff(s.vsync),
                d -> { s.vsync = !s.vsync; touch(false); }, false, false,
                "LOCKS FPS TO YOUR MONITOR. STOPS TEARING.", "OFF = LOWER INPUT LAG, USE MAX FPS."));
        perf.add(new Row("MAX FPS",
                () -> s.fpsLimit() == 0 ? "UNLIMITED" : String.valueOf(s.fpsLimit()),
                d -> { s.fpsLimitIdx = cycle(s.fpsLimitIdx, d, GameSettings.FPS_LIMITS.length); touch(false); },
                false, false,
                "CAPS FRAME RATE. A STEADY 30 OR 60 IS", "SMOOTHER THAN A JUMPY UNLIMITED."));
        perf.add(new Row("RENDER DISTANCE",
                () -> s.renderDistanceIsMax() ? "MAX" : String.valueOf((int) s.renderDistance()),
                d -> { s.renderDistIdx = cycle(s.renderDistIdx, d, GameSettings.RENDER_DISTS.length); touch(true); },
                false, true,
                "HOW FAR TREES, ROCKS AND PIECES DRAW.", "ALSO SETS THE FAR PLANE AND FOG."));
        perf.add(new Row("FOG", () -> onOff(s.fog),
                d -> { s.fog = !s.fog; touch(true); }, false, true,
                "HIDES POP-IN AT THE EDGE OF THE VIEW.", "VERY CHEAP. LEAVE ON UNLESS YOU HATE IT."));
        perf.add(new Row("TREES", () -> GameSettings.TREE_LABELS[s.trees],
                d -> { s.trees = cycle(s.trees, d, 3); touch(true); }, false, true,
                "OFF = NONE. FAST = FAR TREES DRAW CANOPY", "ONLY (HALF THE DRAW CALLS). FANCY = FULL."));
        perf.add(new Row("TREE DENSITY", () -> GameSettings.DENSITY_LABELS[s.treeDensity],
                d -> { s.treeDensity = cycle(s.treeDensity, d, 3); touch(true); }, false, true,
                "DRAWS 1 OF EVERY 1, 2 OR 4 TREES."));
        perf.add(new Row("BOULDERS AND CRYSTALS", () -> onOff(s.landscape),
                d -> { s.landscape = !s.landscape; touch(true); }, false, true,
                "GROUND CLUTTER. EACH ONE IS A MODEL DRAW."));
        perf.add(new Row("MODEL BACKFACE CULLING", () -> onOff(s.modelCull),
                d -> { s.modelCull = !s.modelCull; touch(true); }, false, true,
                "SKIPS HIDDEN SIDES = LESS PIXEL WORK.", "IF A MODEL VANISHES OR LOOKS INSIDE-OUT,", "TURN THIS OFF."));
        perf.add(button("BACK", () -> open(Page.ROOT), "BACK TO OPTIONS."));
    }

    private void buildQuality() {
        quality.add(new Row("TRANSPARENCY", () -> onOff(s.transparency),
                d -> { s.transparency = !s.transparency; touch(false); }, false, false,
                "ALPHA BLENDING: SEE-THROUGH WATER AND", "UNDERWATER TINT. COSTS FILL RATE ON A", "HD 6450. OFF = SOLID WATER."));
        quality.add(new Row("SHADOWS", () -> "OFF (NOT AVAILABLE YET)", null, true, false,
                "NOT IMPLEMENTED. WILL STAY OFF BY DEFAULT."));
        quality.add(new Row("REFLECTIONS", () -> "OFF (NOT AVAILABLE YET)", null, true, false,
                "NOT IMPLEMENTED. WILL STAY OFF BY DEFAULT."));
        quality.add(new Row("TERRAIN BLEND",
                () -> s.terrainBlend == 0 ? "FAST" : "FANCY",
                d -> { s.terrainBlend = 1 - s.terrainBlend; touch(true); }, false, true,
                "FAST SKIPS UNUSED TERRAIN TEXTURE LAYERS.", "LOOKS THE SAME, MUCH LESS TEXTURE FETCHING."));
        quality.add(new Row("TEXTURE FILTER", () -> GameSettings.FILTER_LABELS[s.texFilter],
                d -> { s.texFilter = cycle(s.texFilter, d, 3); touch(true); }, false, true,
                "NEAREST = FASTEST, PIXELATED. BILINEAR =", "GOOD BALANCE. TRILINEAR = SMOOTHEST MIPS."));
        quality.add(new Row("ANISOTROPIC FILTER",
                () -> s.aniso() == 1 ? "OFF" : s.aniso() + "X",
                d -> { s.anisoIdx = cycle(s.anisoIdx, d, GameSettings.ANISO_LEVELS.length); touch(true); },
                false, true,
                "SHARPER GROUND AT GLANCING ANGLES. HEAVY", "ON LOW-END CARDS. NEEDS TEXTURE BANDWIDTH."));
        quality.add(new Row("FIELD OF VIEW", () -> String.valueOf(s.fov),
                d -> { s.fov = Math.max(50, Math.min(110, s.fov + 5 * (d == 0 ? 1 : d))); touch(false); },
                false, false,
                "WIDER = SEE MORE = MORE TO DRAW."));
        quality.add(new Row("HUD INFO", () -> GameSettings.HUD_LABELS[s.hudInfo],
                d -> { s.hudInfo = cycle(s.hudInfo, d, 3); touch(false); }, false, false,
                "FPS / POSITION / KEY HINTS IN-GAME."));
        quality.add(button("BACK", () -> open(Page.ROOT), "BACK TO OPTIONS."));
    }

    private void buildControls() {
        controls.add(new Row("MOUSE SENSITIVITY", () -> s.mouseSensPct + "%",
                d -> { s.mouseSensPct = Math.max(20, Math.min(300, s.mouseSensPct + 10 * (d == 0 ? 1 : d))); touch(false); },
                false, false,
                "100% = ORIGINAL SENSITIVITY."));
        controls.add(button("BACK", () -> open(Page.ROOT), "BACK TO OPTIONS."));
    }

    private static Row button(String label, Runnable onActivate, String... hint) {
        return new Row(label, null, d -> { if (onActivate != null) onActivate.run(); }, false, false, hint);
    }

    // ------------------------------------------------------------------ helpers

    private void touch(boolean custom) {
        if (custom) s.preset = GameSettings.PRESET_CUSTOM;
        s.touch();
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    private static int cycle(int v, int dir, int n) { return Math.floorMod(v + (dir == 0 ? 1 : dir), n); }

    private void open(Page p) { page = p; index = 0; }

    private List<Row> rows() {
        return switch (page) {
            case ROOT -> root;
            case PERFORMANCE -> perf;
            case QUALITY -> quality;
            case CONTROLS -> controls;
        };
    }

    private String title() {
        return switch (page) {
            case ROOT -> "OPTIONS";
            case PERFORMANCE -> "OPTIONS - PERFORMANCE";
            case QUALITY -> "OPTIONS - QUALITY";
            case CONTROLS -> "OPTIONS - CONTROLS";
        };
    }

    // ------------------------------------------------------------------ input

    /** @return true when the player leaves Options (back to title menu). */
    public boolean handleKey(boolean left, boolean right, boolean up, boolean down,
                             boolean enter, boolean esc) {
        List<Row> r = rows();
        if (up) index = (index + r.size() - 1) % r.size();
        if (down) index = (index + 1) % r.size();
        if (esc) {
            if (page == Page.ROOT) return true;
            open(Page.ROOT);
            return false;
        }
        Row row = r.get(index);
        if (row.locked || row.adjust == null) return false;
        if (row.value == null && !enter) return false; // buttons / page links: Enter only
        int dir = right ? 1 : (left ? -1 : 0);
        if (dir != 0 || enter) {
            if (page == Page.ROOT && index == root.size() - 1 && enter) return true; // DONE
            row.adjust.accept(dir);
        }
        return false;
    }

    /** @return true when the player leaves Options. */
    public boolean handleClick(float mx, float my, int w, int h) {
        List<Row> r = rows();
        float px = (w - PANEL_W) * 0.5f;
        float py = panelY(r.size(), h);
        for (int i = 0; i < r.size(); i++) {
            float y = py + TOP + i * ROW_H;
            if (mx >= px + 20 && mx <= px + PANEL_W - 20 && my >= y - 4 && my <= y + ROW_H - 8) {
                index = i;
                return handleKey(false, false, false, false, true, false);
            }
        }
        return false;
    }

    public void reset() { page = Page.ROOT; index = 0; }

    // ------------------------------------------------------------------ draw

    private float panelH(int rowCount) { return TOP + rowCount * ROW_H + 96; }
    private float panelY(int rowCount, int h) { return Math.max(56f, (h - panelH(rowCount)) * 0.5f - 6f); }

    public void draw(Hud hud, int w, int h) {
        List<Row> r = rows();
        float ph = panelH(r.size());
        float px = (w - PANEL_W) * 0.5f;
        float py = panelY(r.size(), h);

        // steel panel
        hud.rect(px, py, PANEL_W, ph, 0.04f, 0.05f, 0.04f, 1f);
        hud.rect(px, py, PANEL_W, ph * 0.18f, 0.08f, 0.09f, 0.08f, 1f);
        hud.rect(px, py, PANEL_W, 2, 0.22f, 0.24f, 0.22f, 1f);
        hud.rect(px, py + ph - 2, PANEL_W, 2, 0.12f, 0.14f, 0.12f, 1f);
        hud.rect(px, py, 2, ph, 0.22f, 0.24f, 0.22f, 1f);
        hud.rect(px + PANEL_W - 2, py, 2, ph, 0.12f, 0.14f, 0.12f, 1f);

        hud.setGlyphScale(2.5f);
        hud.text(px + 24, py + 18, title(), G_R, G_G, G_B, 1f);
        hud.rect(px + 24, py + 42, PANEL_W - 48, 2, 0.2f, 0.22f, 0.2f, 1f);

        for (int i = 0; i < r.size(); i++) {
            Row row = r.get(i);
            float y = py + TOP + i * ROW_H;
            boolean on = i == index;
            if (on) {
                hud.rect(px + 20, y - 4, PANEL_W - 40, ROW_H - 6, 0.10f, 0.14f, 0.10f, 1f);
                hud.rect(px + 20, y - 4, 4, ROW_H - 6, G_R, G_G, G_B, 1f);
            }
            float cr, cg, cb;
            if (row.locked) { cr = L_R; cg = L_G; cb = L_B; }
            else if (on) { cr = G_R; cg = G_G; cb = G_B; }
            else { cr = D_R; cg = D_G; cb = D_B; }
            hud.text(px + 40, y + 2, row.label, cr, cg, cb, 1f);
            if (row.value != null) {
                String v = row.value.get();
                float vx = px + PANEL_W - 32 - v.length() * 6f * 2.5f;
                hud.text(vx, y + 2, v, cr, cg, cb, 1f);
            }
        }

        // hint box for the selected row
        String[] hint = r.get(index).hint;
        float hy = py + TOP + r.size() * ROW_H + 6;
        hud.rect(px + 20, hy - 2, PANEL_W - 40, 2, 0.2f, 0.22f, 0.2f, 1f);
        hud.setGlyphScale(2.0f);
        for (int i = 0; i < hint.length && i < 3; i++) {
            hud.text(px + 24, hy + 6 + i * 18, hint[i], D_R, D_G, D_B, 1f);
        }
        hud.setGlyphScale(2.5f);
    }
}
