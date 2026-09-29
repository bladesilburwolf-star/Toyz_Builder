package com.toyzbuilder.engine;

import com.toyzbuilder.world.WorldGenerator;

/**
 * Title / mapgen / options menus — Serif black steel + phosphor green.
 * Uses Hud rects + text (no external font files).
 */
public final class MainMenu {

    public enum Screen {
        TITLE, MAPGEN, OPTIONS, MODS, SHADERS
    }

    public enum Action {
        NONE, PLAY_NEW, PLAY_CONTINUE, QUIT
    }

    private Screen screen = Screen.TITLE;
    private int titleIndex = 0;
    private int mapgenIndex = 0;
    private final OptionsMenu optionsMenu = new OptionsMenu();

    // mapgen fields
    public int seed = 0xC0FFEE;
    public int sizePreset = 1; // 0 small, 1 medium, 2 large
    public int worldType = 0;  // 0 normal, 1 flat, 2 amplified, 3 islands
    public boolean skyIslands = false;

    private static final String[] TITLE_ITEMS = {
            "New Game", "Continue", "Mods", "Shaders", "Options", "Quit"
    };
    // Coarse grids (Daggerfall-ish). Tris ≈ (res-1)^2 * 2.
    private static final String[] SIZE_LABELS = {
            "Small (~384u)", "Medium (~640u)", "Large (~960u)"
    };
    private static final String[] TYPE_LABELS = { "Normal", "Flat", "Amplified", "Islands" };

    private static final float G_R = 0.35f, G_G = 1.0f, G_B = 0.45f;
    private static final float D_R = 0.25f, D_G = 0.55f, D_B = 0.30f;

    public Screen getScreen() { return screen; }
    public void setScreen(Screen s) { screen = s; }

    public void draw(Hud hud, int w, int h) {
        hud.setGlyphScale(2.5f); // readable rect font on HD 6450
        // full opaque backdrop
        hud.rect(0, 0, w, h, 0.02f, 0.025f, 0.03f, 1f);
        // top steel bar
        hud.rect(0, 0, w, 48, 0.06f, 0.07f, 0.06f, 1f);
        hud.rect(0, 46, w, 2, 0.22f, 0.25f, 0.22f, 1f);
        hud.text(24, 16, "TOYZ BUILDER", G_R, G_G, G_B, 1f);
        hud.text(200, 18, "LWJGL / WorldGen", D_R, D_G, D_B, 1f);

        switch (screen) {
            case TITLE -> drawTitle(hud, w, h);
            case MAPGEN -> drawMapgen(hud, w, h);
            case OPTIONS -> optionsMenu.draw(hud, w, h);
            case MODS -> drawPlaceholder(hud, w, h, "MODS", "Mod loader placeholder — drop packs under assets/mods/");
            case SHADERS -> drawPlaceholder(hud, w, h, "SHADERS", "Shader packs placeholder — future OptiFine-style menu");
        }

        hud.rect(0, h - 28, w, 28, 0.04f, 0.05f, 0.04f, 1f);
        hud.text(16, h - 20, "Arrows / mouse  Enter select  ESC back", D_R, D_G, D_B, 1f);
    }

    private void drawTitle(Hud hud, int w, int h) {
        float panelW = 420, panelH = 320;
        float px = (w - panelW) * 0.5f;
        float py = 120;
        steelPanel(hud, px, py, panelW, panelH);
        hud.text(px + 24, py + 20, "MAIN MENU", G_R, G_G, G_B, 1f);
        hud.rect(px + 24, py + 40, panelW - 48, 2, 0.2f, 0.22f, 0.2f, 1f);

        for (int i = 0; i < TITLE_ITEMS.length; i++) {
            float y = py + 60 + i * 40;
            boolean on = i == titleIndex;
            if (on) {
                hud.rect(px + 20, y - 4, panelW - 40, 28, 0.10f, 0.14f, 0.10f, 1f);
                hud.rect(px + 20, y - 4, 4, 28, G_R, G_G, G_B, 1f);
            }
            hud.text(px + 40, y + 4, TITLE_ITEMS[i],
                    on ? G_R : D_R, on ? G_G : D_G, on ? G_B : D_B, 1f);
        }
    }

    private void drawMapgen(Hud hud, int w, int h) {
        float panelW = 520, panelH = 360;
        float px = (w - panelW) * 0.5f;
        float py = 100;
        steelPanel(hud, px, py, panelW, panelH);
        hud.text(px + 24, py + 18, "CREATE WORLD", G_R, G_G, G_B, 1f);
        hud.rect(px + 24, py + 38, panelW - 48, 2, 0.2f, 0.22f, 0.2f, 1f);

        String[] rows = {
                "Seed: " + (seed & 0x7fffffff),
                "Size: " + SIZE_LABELS[sizePreset],
                "Type: " + TYPE_LABELS[worldType],
                "Sky Islands: " + (skyIslands ? "ON" : "OFF"),
                "Generate World",
                "Back"
        };
        for (int i = 0; i < rows.length; i++) {
            float y = py + 60 + i * 40;
            boolean on = i == mapgenIndex;
            if (on) {
                hud.rect(px + 20, y - 4, panelW - 40, 28, 0.10f, 0.14f, 0.10f, 1f);
                hud.rect(px + 20, y - 4, 4, 28, G_R, G_G, G_B, 1f);
            }
            hud.text(px + 40, y + 4, rows[i],
                    on ? G_R : D_R, on ? G_G : D_G, on ? G_B : D_B, 1f);
        }
        hud.text(px + 24, py + panelH - 36, "Left/Right change value   Enter confirm", D_R, D_G, D_B, 1f);
    }

    private void drawPlaceholder(Hud hud, int w, int h, String title, String body) {
        float panelW = 560, panelH = 220;
        float px = (w - panelW) * 0.5f;
        float py = 180;
        steelPanel(hud, px, py, panelW, panelH);
        hud.text(px + 24, py + 24, title, G_R, G_G, G_B, 1f);
        hud.text(px + 24, py + 70, body, D_R, D_G, D_B, 1f);
        hud.text(px + 24, py + 120, "[ Enter / ESC ] Back to main menu", G_R, G_G, G_B, 1f);
    }

    private void steelPanel(Hud hud, float x, float y, float w, float h) {
        hud.rect(x, y, w, h, 0.04f, 0.05f, 0.04f, 1f);
        hud.rect(x, y, w, h * 0.3f, 0.08f, 0.09f, 0.08f, 1f);
        hud.rect(x, y, w, 2, 0.22f, 0.24f, 0.22f, 1f);
        hud.rect(x, y + h - 2, w, 2, 0.12f, 0.14f, 0.12f, 1f);
        hud.rect(x, y, 2, h, 0.22f, 0.24f, 0.22f, 1f);
        hud.rect(x + w - 2, y, 2, h, 0.12f, 0.14f, 0.12f, 1f);
    }

    /** Keyboard navigation. Returns an Action when the player starts a game or quits. */
    public Action handleKey(int key, boolean left, boolean right, boolean up, boolean down,
                            boolean enter, boolean esc) {
        if (screen == Screen.TITLE) {
            if (up) titleIndex = (titleIndex + TITLE_ITEMS.length - 1) % TITLE_ITEMS.length;
            if (down) titleIndex = (titleIndex + 1) % TITLE_ITEMS.length;
            if (enter) {
                return switch (titleIndex) {
                    case 0 -> { screen = Screen.MAPGEN; mapgenIndex = 0; yield Action.NONE; }
                    case 1 -> Action.PLAY_CONTINUE;
                    case 2 -> { screen = Screen.MODS; yield Action.NONE; }
                    case 3 -> { screen = Screen.SHADERS; yield Action.NONE; }
                    case 4 -> { screen = Screen.OPTIONS; optionsMenu.reset(); yield Action.NONE; }
                    case 5 -> Action.QUIT;
                    default -> Action.NONE;
                };
            }
            if (esc) return Action.QUIT;
            return Action.NONE;
        }

        if (screen == Screen.MAPGEN) {
            if (up) mapgenIndex = (mapgenIndex + 5) % 6;
            if (down) mapgenIndex = (mapgenIndex + 1) % 6;
            if (left || right) {
                int dir = right ? 1 : -1;
                switch (mapgenIndex) {
                    case 0 -> seed = (seed + dir * 9973);
                    case 1 -> sizePreset = Math.floorMod(sizePreset + dir, 3);
                    case 2 -> worldType = Math.floorMod(worldType + dir, 4);
                    case 3 -> skyIslands = !skyIslands;
                    default -> { }
                }
            }
            if (enter) {
                if (mapgenIndex == 4) return Action.PLAY_NEW;
                if (mapgenIndex == 5) { screen = Screen.TITLE; return Action.NONE; }
                // toggle on enter for sky islands / cycle others
                if (mapgenIndex == 3) skyIslands = !skyIslands;
                if (mapgenIndex == 0) seed = (int) (System.currentTimeMillis() & 0x7fffffff);
                if (mapgenIndex == 1) sizePreset = (sizePreset + 1) % 3;
                if (mapgenIndex == 2) worldType = (worldType + 1) % 4;
            }
            if (esc) { screen = Screen.TITLE; }
            return Action.NONE;
        }

        if (screen == Screen.OPTIONS) {
            if (optionsMenu.handleKey(left, right, up, down, enter, esc)) screen = Screen.TITLE;
            return Action.NONE;
        }

        // MODS / SHADERS
        if (enter || esc) screen = Screen.TITLE;
        return Action.NONE;
    }

    /** Mouse click in screen space (cursor free on menu). */
    public Action handleClick(float mx, float my, int w, int h) {
        // Approximate hit boxes matching draw layout
        if (screen == Screen.TITLE) {
            float panelW = 420, panelH = 320;
            float px = (w - panelW) * 0.5f;
            float py = 120;
            for (int i = 0; i < TITLE_ITEMS.length; i++) {
                float y = py + 60 + i * 40;
                if (mx >= px + 20 && mx <= px + panelW - 20 && my >= y - 4 && my <= y + 24) {
                    titleIndex = i;
                    return handleKey(0, false, false, false, false, true, false);
                }
            }
        } else if (screen == Screen.MAPGEN) {
            float panelW = 520, panelH = 360;
            float px = (w - panelW) * 0.5f;
            float py = 100;
            for (int i = 0; i < 6; i++) {
                float y = py + 60 + i * 40;
                if (mx >= px + 20 && mx <= px + panelW - 20 && my >= y - 4 && my <= y + 24) {
                    mapgenIndex = i;
                    return handleKey(0, false, false, false, false, true, false);
                }
            }
        } else if (screen == Screen.OPTIONS) {
            if (optionsMenu.handleClick(mx, my, w, h)) screen = Screen.TITLE;
        } else {
            return handleKey(0, false, false, false, false, true, false);
        }
        return Action.NONE;
    }

    /** Apply size/type presets onto generator settings. */
    public void applyTo(WorldGenerator.Settings cfg) {
        cfg.seed = seed == 0 ? (int) (System.currentTimeMillis() & 0x7fffffff) : seed;
        // Lower poly mainland: fewer verts, larger spacing keeps map size useful.
        // Old Medium was 257^2 verts ≈ 131k tris — too heavy for HD 6450.
        // Larger maps OK — TerrainChunks only draws near the camera.
        switch (sizePreset) {
            case 0 -> { cfg.resolution = 97;  cfg.spacing = 4.0f; }  // size 384
            case 2 -> { cfg.resolution = 193; cfg.spacing = 5.0f; }  // size 960
            case 3 -> { cfg.resolution = 193; cfg.spacing = 5.0f; cfg.worldSize = com.toyzbuilder.world.WorldBounds.MINETEST_WORLD_SIZE; } // Minetest-scale logical world
            default -> { cfg.resolution = 161; cfg.spacing = 4.0f; }  // size 640
        }
        switch (worldType) {
            case 1 -> { cfg.heightScale = 4f; cfg.waterLevel = 2f; }      // flat
            case 2 -> { cfg.heightScale = 48f; cfg.waterLevel = 4.5f; }   // amplified
            case 3 -> { cfg.heightScale = 32f; cfg.waterLevel = 8f; }     // islands (more water)
            default -> { cfg.heightScale = 28f; cfg.waterLevel = 4.5f; }
        }
        // skyIslands flag stored for future TreeField / generator hooks
    }
}