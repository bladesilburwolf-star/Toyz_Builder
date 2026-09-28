package com.toyzbuilder.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Global video / performance / control options (OptiFine-style), saved to options.txt.
 *
 * DEFAULTS ARE TUNED FOR A RADEON HD 6450 (Caicos: 160 SPs, 4 ROPs, 64-bit DDR3).
 * That card is fill-rate / bandwidth bound, so the expensive-per-pixel features
 * (transparency, shadows, reflections, anisotropic filtering, MSAA) default to OFF.
 *
 * Adding a setting: add a field + default, add it to load()/save(), then add a Row in
 * OptionsMenu. If it needs a live side effect (vsync, texture filter...) apply it in
 * Window.applySettings(), which runs whenever {@link #version()} changes.
 */
public final class GameSettings {

    private static final GameSettings INSTANCE = new GameSettings();
    public static GameSettings get() { return INSTANCE; }

    private static final File FILE = new File("options.txt");

    // ---------- choice tables ----------
    public static final int[] FPS_LIMITS = { 0, 30, 60, 90, 120, 144 };          // 0 = unlimited
    public static final int[] RENDER_DISTS = { 48, 80, 112, 160, 224, 320, 480, 900 }; // world units; 900 = max
    public static final int[] ANISO_LEVELS = { 1, 2, 4, 8 };
    public static final String[] TREE_LABELS = { "OFF", "FAST", "FANCY" };
    public static final String[] DENSITY_LABELS = { "FULL", "HALF", "QUARTER" };
    public static final String[] FILTER_LABELS = { "NEAREST", "BILINEAR", "TRILINEAR" };
    public static final String[] HUD_LABELS = { "OFF", "FPS ONLY", "FULL" };
    public static final String[] PRESET_LABELS = { "POTATO", "LOW", "BALANCED", "HIGH", "CUSTOM" };
    public static final int PRESET_CUSTOM = 4;

    // ---------- performance ----------
    public boolean vsync = true;
    public int fpsLimitIdx = 2;        // 60
    public int renderDistIdx = 3;      // 160
    public boolean fog = true;
    public int trees = 1;              // 0 off, 1 fast, 2 fancy
    public int treeDensity = 0;        // 0 full, 1 half, 2 quarter
    public boolean landscape = true;   // boulders / crystals
    public boolean modelCull = false;  // back-face culling on GLB + tree meshes (safe = off)
    public int preset = 2;             // BALANCED

    // ---------- quality ----------
    /** Master switch for alpha blending: water, underwater tint, future glass/ice/leaves. */
    public boolean transparency = false;
    /** Not implemented yet. Kept OFF and locked in the menu. Renderer must check these first. */
    public boolean shadows = false;
    public boolean reflections = false;
    public int terrainBlend = 0;       // 0 fast (skip zero-weight layers), 1 fancy (all 4 taps)
    public int texFilter = 1;          // 0 nearest, 1 bilinear+mip, 2 trilinear
    public int anisoIdx = 0;           // 1x
    public int fov = 70;
    public int hudInfo = 2;            // 0 off, 1 fps, 2 full

    // ---------- controls ----------
    public int mouseSensPct = 100;     // 100% == 0.08 deg/pixel

    private int version = 1;

    private GameSettings() { }

    // ---------- derived ----------
    public float renderDistance() { return RENDER_DISTS[renderDistIdx]; }
    public boolean renderDistanceIsMax() { return renderDistIdx == RENDER_DISTS.length - 1; }
    public int fpsLimit() { return FPS_LIMITS[fpsLimitIdx]; }
    public int aniso() { return ANISO_LEVELS[anisoIdx]; }
    public float mouseSens() { return 0.08f * mouseSensPct / 100f; }
    /** Trees skipped: keep 1 of every N. */
    public int treeStride() { return treeDensity == 0 ? 1 : (treeDensity == 1 ? 2 : 4); }

    public int version() { return version; }
    public void touch() { version++; }

    // ---------- presets ----------
    /**
     * Presets touch performance + texture quality only. They NEVER enable transparency,
     * shadows or reflections, and they leave FOV / HUD / mouse alone.
     */
    public void applyPreset(int p) {
        switch (p) {
            case 0 -> { // POTATO
                renderDistIdx = 1; fog = true; trees = 0; treeDensity = 0; landscape = false;
                terrainBlend = 0; texFilter = 0; anisoIdx = 0; modelCull = true;
            }
            case 1 -> { // LOW
                renderDistIdx = 2; fog = true; trees = 1; treeDensity = 1; landscape = false;
                terrainBlend = 0; texFilter = 1; anisoIdx = 0; modelCull = false;
            }
            case 3 -> { // HIGH
                renderDistIdx = 5; fog = true; trees = 2; treeDensity = 0; landscape = true;
                terrainBlend = 1; texFilter = 2; anisoIdx = 2; modelCull = false;
            }
            default -> { // BALANCED (2) = shipped default
                p = 2;
                renderDistIdx = 3; fog = true; trees = 1; treeDensity = 0; landscape = true;
                terrainBlend = 0; texFilter = 1; anisoIdx = 0; modelCull = false;
            }
        }
        preset = p;
        touch();
    }

    public void resetDefaults() {
        vsync = true; fpsLimitIdx = 2;
        transparency = false; shadows = false; reflections = false;
        fov = 70; hudInfo = 2; mouseSensPct = 100;
        applyPreset(2);
    }

    // ---------- persistence ----------
    public void load() {
        resetDefaults();
        if (!FILE.isFile()) { touch(); return; }
        try (BufferedReader r = Files.newBufferedReader(FILE.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                int c = line.indexOf(':');
                if (line.isEmpty() || line.startsWith("#") || c <= 0) continue;
                apply(line.substring(0, c).trim(), line.substring(c + 1).trim());
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[Options] could not read options.txt: " + e);
        }
        // Shadows / reflections have no renderer yet: never trust a file that says ON.
        shadows = false;
        reflections = false;
        touch();
    }

    private void apply(String k, String v) {
        switch (k) {
            case "vsync" -> vsync = bool(v);
            case "fpsLimit" -> fpsLimitIdx = idx(v, fpsLimitIdx, FPS_LIMITS.length);
            case "renderDist" -> renderDistIdx = idx(v, renderDistIdx, RENDER_DISTS.length);
            case "fog" -> fog = bool(v);
            case "trees" -> trees = idx(v, trees, 3);
            case "treeDensity" -> treeDensity = idx(v, treeDensity, 3);
            case "landscape" -> landscape = bool(v);
            case "modelCull" -> modelCull = bool(v);
            case "preset" -> preset = idx(v, preset, PRESET_LABELS.length);
            case "transparency" -> transparency = bool(v);
            case "terrainBlend" -> terrainBlend = idx(v, terrainBlend, 2);
            case "texFilter" -> texFilter = idx(v, texFilter, 3);
            case "aniso" -> anisoIdx = idx(v, anisoIdx, ANISO_LEVELS.length);
            case "fov" -> fov = clamp(parse(v, fov), 50, 110);
            case "hudInfo" -> hudInfo = idx(v, hudInfo, 3);
            case "mouseSens" -> mouseSensPct = clamp(parse(v, mouseSensPct), 20, 300);
            default -> { }
        }
    }

    public void save() {
        try (BufferedWriter w = Files.newBufferedWriter(FILE.toPath(), StandardCharsets.UTF_8)) {
            w.write("# Toyz Builder options (edit in-game via Options)\n");
            line(w, "vsync", vsync);
            line(w, "fpsLimit", fpsLimitIdx);
            line(w, "renderDist", renderDistIdx);
            line(w, "fog", fog);
            line(w, "trees", trees);
            line(w, "treeDensity", treeDensity);
            line(w, "landscape", landscape);
            line(w, "modelCull", modelCull);
            line(w, "preset", preset);
            line(w, "transparency", transparency);
            line(w, "terrainBlend", terrainBlend);
            line(w, "texFilter", texFilter);
            line(w, "aniso", anisoIdx);
            line(w, "fov", fov);
            line(w, "hudInfo", hudInfo);
            line(w, "mouseSens", mouseSensPct);
        } catch (IOException e) {
            System.err.println("[Options] could not write options.txt: " + e);
        }
    }

    private static void line(BufferedWriter w, String k, Object v) throws IOException {
        w.write(k + ":" + v + "\n");
    }

    private static boolean bool(String v) { return v.equalsIgnoreCase("true") || v.equals("1"); }
    private static int parse(String v, int def) {
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int idx(String v, int def, int size) { return clamp(parse(v, def), 0, size - 1); }
}
