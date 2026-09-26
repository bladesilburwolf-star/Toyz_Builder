package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.List;

/**
 * Port of ui.h / ui.cpp — Serif System Works steel + phosphor UI.
 * Class name is UI (not Ui) to match the file name UI.java on Windows.
 */
public final class UI {

    public enum PauseAction { None, Resume, ToggleOptions, SaveMap, NewMap, ExitGame, ExitToTitle }
    public enum TitleAction { None, NewWorld, Continue, LoadMap, Survival, Settings, Exit }
    public enum WorldAction { None, Create, Back }

    public static class PauseMenuState {
        public boolean open = false;
        public boolean showOptionsPanel = false;
        public int selected = 0;
        /** 0=Controls 1=Graphics 2=Mods 3=Shaders */
        public int optionsTab = 0;
        /** Scroll offset for settings content (pixels). */
        public float optionsScroll = 0f;
    }

    public static class TitleMenuState {
        public int selected = 0;
        public boolean showSettings = false;
        public boolean showWorldMenu = false;
        public boolean showMapLoader = false;
        public int worldSelected = 0;
        public int worldTypeIndex = 1;
        public boolean structures = true;
        public int mapgenPresetIndex = 4; // V2=0 .. V7=5, default V6
        public boolean skyIslands = false;
        public String seedText = "RANDOM";
    }

    // Serif System Works palette (created once — JavaCPP structs are native memory)
    private static final Color STEEL_TOP = Helpers.newColor(28, 30, 34, 240);
    private static final Color STEEL_BOT = Helpers.newColor(12, 14, 16, 250);
    private static final Color STEEL_EDGE = Helpers.newColor(70, 75, 85, 255);
    private static final Color PHOSPHOR = Helpers.newColor(80, 255, 120, 255);
    private static final Color PHOSPHOR_DIM = Helpers.newColor(40, 160, 80, 255);
    private static final Color UI_WHITE = Helpers.newColor(230, 235, 240, 255);
    private static final Color PANEL = Helpers.newColor(18, 20, 24, 230);

    public static Color steelTop()    { return STEEL_TOP; }
    public static Color steelBot()    { return STEEL_BOT; }
    public static Color steelEdge()   { return STEEL_EDGE; }
    public static Color phosphor()    { return PHOSPHOR; }
    public static Color phosphorDim() { return PHOSPHOR_DIM; }
    public static Color white()       { return UI_WHITE; }
    public static Color danger()      { return Helpers.newColor(255, 90, 90, 255); }
    public static Color panel()       { return PANEL; }

    private UI() {}

    public static void drawSteelPanel(Rectangle r, String title) {
        DrawRectangleRec(r, STEEL_EDGE);
        float inX = r.x() + 2, inY = r.y() + 2, inW = r.width() - 4, inH = r.height() - 4;
        int strips = (int) (inH / 4f);
        if (strips < 1) strips = 1;
        for (int i = 0; i < strips; i++) {
            float t = (float) i / strips;
            Color c = Helpers.newColor(
                (int) (STEEL_TOP.r() * (1f - t) + STEEL_BOT.r() * t),
                (int) (STEEL_TOP.g() * (1f - t) + STEEL_BOT.g() * t),
                (int) (STEEL_TOP.b() * (1f - t) + STEEL_BOT.b() * t), 245);
            DrawRectangle((int) inX, (int) (inY + i * 4), (int) inW, 5, c);
        }
        DrawRectangle((int) inX, (int) inY, (int) inW, 28, PANEL);
        DrawText(title, (int) inX + 12, (int) inY + 6, 18, PHOSPHOR);
        DrawRectangle((int) inX, (int) (inY + inH - 3), (int) inW, 2, PHOSPHOR_DIM);
    }

    public static boolean drawSteelButton(Rectangle r, String label, boolean selected) {
        Vector2 m = GetMousePosition();
        boolean hover = CheckCollisionPointRec(m, r);
        boolean pressed = hover && IsMouseButtonPressed(MOUSE_BUTTON_LEFT);

        Color fill = (selected || hover) ? Helpers.newColor(35, 42, 38, 255)
                                         : Helpers.newColor(22, 24, 28, 255);
        Color border = (selected || hover) ? PHOSPHOR : STEEL_EDGE;
        DrawRectangleRec(r, border);
        DrawRectangle((int) r.x() + 1, (int) r.y() + 1, (int) r.width() - 2, (int) r.height() - 2, fill);

        int tw = MeasureText(label, 18);
        Color textCol = (selected || hover) ? PHOSPHOR : UI_WHITE;
        DrawText(label, (int) (r.x() + (r.width() - tw) * 0.5f), (int) (r.y() + r.height() * 0.5f - 9), 18, textCol);
        return pressed;
    }

    public static PauseAction drawPauseMenu(PauseMenuState state, boolean hasController) {
        if (!state.open) return PauseAction.None;

        int sw = GetScreenWidth(), sh = GetScreenHeight();
        DrawRectangle(0, 0, sw, sh, Helpers.newColor(0, 0, 0, 160));

        float panelW = 360f, panelH = 380f;
        Rectangle panel = Helpers.newRectangle((sw - panelW) * 0.5f, (sh - panelH) * 0.5f, panelW, panelH);
        drawSteelPanel(panel, "TOYZ BUILDER  //  PAUSED");

        if (IsKeyPressed(KEY_DOWN) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_DOWN)))
            state.selected = (state.selected + 1) % 6;
        if (IsKeyPressed(KEY_UP) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_UP)))
            state.selected = (state.selected + 5) % 6;

        String[] labels = { "RESUME", "SETTINGS", "SAVE MAP", "NEW WORLD", "EXIT TO TITLE", "EXIT TO DESKTOP" };
        PauseAction[] actions = { PauseAction.Resume, PauseAction.ToggleOptions, PauseAction.SaveMap,
                                  PauseAction.NewMap, PauseAction.ExitToTitle, PauseAction.ExitGame };

        float bx = panel.x() + 40f;
        float by = panel.y() + 48f;
        float bw = panelW - 80f;
        float bh = 36f;
        float gap = 10f;

        PauseAction result = PauseAction.None;
        for (int i = 0; i < 6; i++) {
            Rectangle br = Helpers.newRectangle(bx, by + i * (bh + gap), bw, bh);
            if (drawSteelButton(br, labels[i], state.selected == i)) {
                result = actions[i];
                state.selected = i;
            }
        }

        boolean confirm = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)
            || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
        if (confirm) result = actions[state.selected];

        DrawText("ESC resume   |   arrows select   |   enter confirm",
                 (int) (panel.x() + 24), (int) (panel.y() + panelH - 28), 14, PHOSPHOR_DIM);
        return result;
    }

    public static TitleAction drawTitleScreen(TitleMenuState state, boolean hasController, String lastMapName) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();

        for (int i = 0; i < sh; i += 4) {
            float t = (float) i / sh;
            Color c = Helpers.newColor((int) (18 * (1 - t) + 8 * t), (int) (20 * (1 - t) + 10 * t),
                                       (int) (24 * (1 - t) + 12 * t), 255);
            DrawRectangle(0, i, sw, 5, c);
        }

        String title = "TOYZ BUILDER ENGINE";
        String sub = "BETA 1";
        String by = "SERIF SYSTEM WORKS";
        int tw = MeasureText(title, 42);
        DrawText(title, (sw - tw) / 2, sh / 6, 42, PHOSPHOR);
        int sw2 = MeasureText(sub, 28);
        DrawText(sub, (sw - sw2) / 2, sh / 6 + 50, 28, UI_WHITE);
        int bw2 = MeasureText(by, 16);
        DrawText(by, (sw - bw2) / 2, sh / 6 + 90, 16, PHOSPHOR_DIM);

        final int MENU_N = 6;
        if (IsKeyPressed(KEY_DOWN) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_DOWN)))
            state.selected = (state.selected + 1) % MENU_N;
        if (IsKeyPressed(KEY_UP) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_UP)))
            state.selected = (state.selected + MENU_N - 1) % MENU_N;

        float panelW = 400f;
        float bx = (sw - panelW) * 0.5f;
        float by0 = sh * 0.36f;
        float bh = 40f;
        float gap = 8f;

        String cont = (lastMapName != null && !lastMapName.isEmpty())
            ? String.format("CONTINUE  (%s)", lastMapName) : "CONTINUE";
        String[] labels = {
            "NEW WORLD",
            cont,
            "LOAD MAP",
            "SURVIVAL MODE",
            "SETTINGS",
            "EXIT"
        };
        TitleAction[] actions = {
            TitleAction.NewWorld, TitleAction.Continue, TitleAction.LoadMap,
            TitleAction.Survival, TitleAction.Settings, TitleAction.Exit
        };

        TitleAction result = TitleAction.None;
        for (int i = 0; i < MENU_N; i++) {
            Rectangle br = Helpers.newRectangle(bx, by0 + i * (bh + gap), panelW, bh);
            if (drawSteelButton(br, labels[i], state.selected == i)) {
                result = actions[i];
                state.selected = i;
            }
        }

        boolean confirm = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)
            || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
        if (confirm) result = actions[state.selected];

        DrawText("WASD move | mouse look | E inventory | C craft | ESC pause", 20, sh - 36, 16, PHOSPHOR_DIM);
        DrawText("Ctrl+L map loader  |  F11 fullscreen", 20, sh - 18, 14, PHOSPHOR_DIM);
        return result;
    }

    /** Official map loader — lists maps/*.map, returns selected path or null. */
    public static class MapLoaderState {
        public boolean open = false;
        public int selected = 0;
        public java.util.List<String> files = new java.util.ArrayList<>();
    }

    public static String drawMapLoader(MapLoaderState state, boolean hasController) {
        if (!state.open) return null;
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        DrawRectangle(0, 0, sw, sh, Helpers.newColor(0, 0, 0, 160));
        float pw = 520, ph = 420;
        Rectangle panel = Helpers.newRectangle((sw - pw) * 0.5f, (sh - ph) * 0.5f, pw, ph);
        drawSteelPanel(panel, "MAP LOADER  //  maps/");

        if (state.files.isEmpty()) {
            DrawText("No .map files in maps/ folder", (int) panel.x() + 24, (int) panel.y() + 60, 18, PHOSPHOR_DIM);
            DrawText("ESC / Back to close", (int) panel.x() + 24, (int) panel.y() + (int) ph - 40, 14, PHOSPHOR);
            if (IsKeyPressed(KEY_ESCAPE)) state.open = false;
            return null;
        }
        if (state.selected >= state.files.size()) state.selected = state.files.size() - 1;
        if (IsKeyPressed(KEY_DOWN)) state.selected = (state.selected + 1) % state.files.size();
        if (IsKeyPressed(KEY_UP)) state.selected = (state.selected + state.files.size() - 1) % state.files.size();

        float y = panel.y() + 48;
        for (int i = 0; i < state.files.size(); i++) {
            String name = state.files.get(i);
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            String shortN = slash >= 0 ? name.substring(slash + 1) : name;
            boolean sel = i == state.selected;
            Color col = sel ? PHOSPHOR : WHITE;
            DrawText((sel ? "> " : "  ") + shortN, (int) panel.x() + 28, (int) y, 18, col);
            y += 26;
            if (y > panel.y() + ph - 70) break;
        }
        DrawText("Enter load  |  ESC cancel", (int) panel.x() + 24, (int) panel.y() + (int) ph - 40, 14, PHOSPHOR_DIM);

        if (IsKeyPressed(KEY_ESCAPE)) { state.open = false; return null; }
        if (IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)) {
            state.open = false;
            return state.files.get(state.selected);
        }
        return null;
    }

    public static WorldAction drawWorldCreation(TitleMenuState state, boolean hasController) {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        DrawRectangle(0, 0, sw, sh, Helpers.newColor(7, 9, 12, 255));
        float panelW = Math.min(620f, sw - 80f), panelH = Math.min(640f, sh - 60f);
        Rectangle panel = Helpers.newRectangle((sw - panelW) * 0.5f, (sh - panelH) * 0.5f, panelW, panelH);
        drawSteelPanel(panel, "CREATE NEW WORLD  //  WORLD GENERATOR");

        String[] types = { "FLAT", "REGULAR", "AMPLIFIED", "NETHER" };
        float x = panel.x() + 28f, y = panel.y() + 55f, w = panel.width() - 56f;
        DrawText("WORLD TYPE", (int)x, (int)y, 16, PHOSPHOR_DIM); y += 25;
        for (int i = 0; i < types.length; i++) {
            Rectangle r = Helpers.newRectangle(x + i * (w / 4f), y, w / 4f - 6f, 42);
            if (drawSteelButton(r, types[i], state.worldTypeIndex == i)) { state.worldTypeIndex = i; }
        }
        y += 65;
        DrawText("STRUCTURES", (int)x, (int)y, 16, PHOSPHOR_DIM);
        Rectangle sr = Helpers.newRectangle(x, y + 22, w, 42);
        if (drawSteelButton(sr, state.structures ? "GENERATE STRUCTURES" : "NO STRUCTURES", state.structures)) state.structures = !state.structures;

        y += 80;
        DrawText("MAPGEN PRESET  (V2..V7)", (int)x, (int)y, 16, PHOSPHOR_DIM); y += 22;
        String[] presets = { "V2", "V3", "V4", "V5", "V6", "V7" };
        float pw = w / 6f;
        for (int i = 0; i < presets.length; i++) {
            Rectangle r = Helpers.newRectangle(x + i * pw, y, pw - 6f, 36);
            if (drawSteelButton(r, presets[i], state.mapgenPresetIndex == i)) state.mapgenPresetIndex = i;
        }
        y += 50;
        DrawText("SKY ISLANDS", (int)x, (int)y, 16, PHOSPHOR_DIM);
        Rectangle skyR = Helpers.newRectangle(x, y + 20, w, 36);
        if (drawSteelButton(skyR, state.skyIslands ? "SKY ISLANDS ON" : "SKY ISLANDS OFF", state.skyIslands))
            state.skyIslands = !state.skyIslands;

        y += 75;
        DrawText("WORLD SEED  (letters/numbers allowed)", (int)x, (int)y, 16, PHOSPHOR_DIM);
        Rectangle seedBox = Helpers.newRectangle(x, y + 22, w, 42);
        DrawRectangleRec(seedBox, STEEL_EDGE);
        DrawRectangle((int)seedBox.x()+2, (int)seedBox.y()+2, (int)seedBox.width()-4, (int)seedBox.height()-4, PANEL);
        DrawText(state.seedText, (int)seedBox.x()+12, (int)seedBox.y()+11, 18, UI_WHITE);
        if (CheckCollisionPointRec(GetMousePosition(), seedBox) && IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) state.worldSelected = 2;
        if (state.worldSelected == 2) {
            if (IsKeyPressed(KEY_BACKSPACE) && state.seedText.length() > 0)
                state.seedText = state.seedText.substring(0, state.seedText.length()-1);
            int cp; while ((cp = GetCharPressed()) != 0) {
                if (cp >= 32 && cp < 127 && state.seedText.length() < 18)
                    state.seedText = state.seedText.equals("RANDOM") ? Character.toString((char)cp) : state.seedText + (char)cp;
            }
        }

        y += 90;
        DrawText("ADVANCED: deterministic terrain + biome + water + architecture", (int)x, (int)y, 14, PHOSPHOR_DIM);
        DrawText("Structure sizes are seed-driven and can be scaled by the engine later.", (int)x, (int)y+20, 14, PHOSPHOR_DIM);

        Rectangle back = Helpers.newRectangle(x, panel.y()+panel.height()-62, w*0.48f, 40);
        Rectangle create = Helpers.newRectangle(x+w*0.52f, panel.y()+panel.height()-62, w*0.48f, 40);
        WorldAction result = WorldAction.None;
        if (drawSteelButton(back, "BACK", false)) result = WorldAction.Back;
        if (drawSteelButton(create, "CREATE WORLD", true)) result = WorldAction.Create;
        if (IsKeyPressed(KEY_ESCAPE)) result = WorldAction.Back;
        if (IsKeyPressed(KEY_ENTER) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN))) result = WorldAction.Create;
        return result;
    }

    /** Mutable settings reference passed in and edited live, replacing the C pointer args. */
    public static class OptionsRequest {
        public boolean requestApplyRes = false;
        public boolean requestToggleFs = false;
        public boolean requestApplyGraphics = false;
        public int requestTab = -1;
        public float scroll = 0f;       // in
        public float scrollOut = 0f;    // out (clamped)
        public float contentHeight = 0f;
    }

    public static void drawOptionsPanel(Rectangle area, ToyzBuilder.GameSettings s,
                                         List<String> skyNames, String[] resNames,
                                         OptionsRequest req) {
        drawOptionsPanel(area, s, skyNames, resNames, req, 0);
    }

    /** Tabbed settings: 0=Controls 1=Graphics(OptiFine) 2=Mods 3=Shaders — scrollable */
    public static void drawOptionsPanel(Rectangle area, ToyzBuilder.GameSettings s,
                                         List<String> skyNames, String[] resNames,
                                         OptionsRequest req, int tab) {
        drawSteelPanel(area, "SETTINGS");
        float x = area.x() + 16f;
        float tabY = area.y() + 42f;
        float w = area.width() - 32f;

        String[] tabs = { "CONTROLS", "GRAPHICS", "MODS", "SHADERS" };
        float tw = w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Rectangle tr = Helpers.newRectangle(x + i * tw, tabY, tw - 2f, 26);
            if (drawSteelButton(tr, tabs[i], tab == i)) {
                req.requestTab = i;
                req.scrollOut = 0f; // reset scroll on tab change
            }
        }

        // Content region below tabs
        float contentTop = tabY + 34f;
        float contentH = area.y() + area.height() - contentTop - 12f;
        float contentX = x;
        float contentW = w - 10f; // room for scrollbar

        // Mouse wheel when cursor over panel
        Vector2 mouse = GetMousePosition();
        if (CheckCollisionPointRec(mouse, area)) {
            float wheel = GetMouseWheelMove();
            if (wheel != 0f) req.scroll = req.scroll - wheel * 36f;
        }

        float scroll = Math.max(0f, req.scroll);
        // Estimate content height per tab (fixed layout)
        float needH = switch (tab) {
            case 0 -> 420f;
            case 1 -> 620f;
            case 2 -> 360f;
            case 3 -> 400f;
            default -> 400f;
        };
        float maxScroll = Math.max(0f, needH - contentH);
        if (scroll > maxScroll) scroll = maxScroll;
        req.scrollOut = scroll;
        req.contentHeight = needH;

        BeginScissorMode((int) contentX, (int) contentTop, (int) contentW, (int) contentH);
        float y = contentTop - scroll;

        if (tab == 0) {
            y = rowLabel("MOUSE SENS", contentX, y);
            y = slider(contentX, y, contentW, v -> s.mouseSensitivity = v, s.mouseSensitivity, 0.05f, 1.2f);
            y = rowLabel("GAMEPAD LOOK", contentX, y);
            y = slider(contentX, y, contentW, v -> s.gamepadLookSensitivity = v, s.gamepadLookSensitivity, 40f, 320f);
            y = rowLabel("ARROW LOOK", contentX, y);
            y = slider(contentX, y, contentW, v -> s.arrowLookSpeed = v, s.arrowLookSpeed, 20f, 180f);
            y = toggle("INVERT X", contentX, y, contentW, v -> s.invertX = v, s.invertX);
            y = toggle("INVERT Y", contentX, y, contentW, v -> s.invertY = v, s.invertY);
            y = toggle("THIRD PERSON", contentX, y, contentW, v -> s.thirdPerson = v, s.thirdPerson);
            y = rowLabel("CAM DISTANCE", contentX, y);
            y = slider(contentX, y, contentW, v -> s.thirdPersonDistance = v, s.thirdPersonDistance, 2f, 14f);
            y = rowLabel("RESOLUTION", contentX, y);
            Rectangle prev = Helpers.newRectangle(contentX, y, 40, 28);
            Rectangle mid = Helpers.newRectangle(contentX + 44, y, contentW - 88, 28);
            Rectangle next = Helpers.newRectangle(contentX + contentW - 40, y, 40, 28);
            if (drawSteelButton(prev, "<", false))
                s.resolutionIndex = (s.resolutionIndex + resNames.length - 1) % resNames.length;
            drawSteelButton(mid, resNames[Math.max(0, Math.min(resNames.length - 1, s.resolutionIndex))], false);
            if (drawSteelButton(next, ">", false))
                s.resolutionIndex = (s.resolutionIndex + 1) % resNames.length;
            y += 32;
            Rectangle apply = Helpers.newRectangle(contentX, y, contentW * 0.48f, 28);
            Rectangle fs = Helpers.newRectangle(contentX + contentW * 0.52f, y, contentW * 0.48f, 28);
            if (drawSteelButton(apply, "APPLY RES", false)) req.requestApplyRes = true;
            if (drawSteelButton(fs, "FULLSCREEN", false)) req.requestToggleFs = true;
            y += 40;
            DrawText("Mouse wheel scrolls this panel", (int) contentX, (int) y, 12, PHOSPHOR_DIM);
        } else if (tab == 1) {
            y = rowLabel("RENDER DIST (CHUNKS)", contentX, y);
            y = slider(contentX, y, contentW, v -> s.renderChunks = Math.round(v), s.renderChunks, 2f, 12f);
            DrawText(String.format("%d (~%.0fu)", s.renderChunks, s.renderChunks * 64f),
                     (int)(contentX + contentW - 110), (int)(y - 18), 14, PHOSPHOR);
            y = rowLabel("ACTIVE DIST (CHUNKS)", contentX, y);
            y = slider(contentX, y, contentW, v -> s.activeChunks = Math.round(v), s.activeChunks, 2f, 10f);
            y = rowLabel("TREE DRAW DIST", contentX, y);
            y = slider(contentX, y, contentW, v -> s.treeDrawDistance = v, s.treeDrawDistance, 40f, 400f);
            y = rowLabel("BRIGHTNESS", contentX, y);
            y = slider(contentX, y, contentW, v -> s.brightness = v, s.brightness, 0.4f, 1.6f);
            y = rowLabel("AMBIENT", contentX, y);
            y = slider(contentX, y, contentW, v -> s.ambient = v, s.ambient, 0.1f, 1.0f);
            y = toggle("FOG", contentX, y, contentW, v -> s.fogEnabled = v, s.fogEnabled);
            if (s.fogEnabled) {
                y = rowLabel("FOG DENSITY", contentX, y);
                y = slider(contentX, y, contentW, v -> s.fogDensity = v, s.fogDensity, 0.001f, 0.04f);
                y = rowLabel("FOG START", contentX, y);
                y = slider(contentX, y, contentW, v -> s.fogStart = v, s.fogStart, 0.1f, 0.9f);
            }
            y = rowLabel("QUALITY", contentX, y);
            Rectangle qp = Helpers.newRectangle(contentX, y, 40, 28);
            Rectangle qm = Helpers.newRectangle(contentX + 44, y, contentW - 88, 28);
            Rectangle qn = Helpers.newRectangle(contentX + contentW - 40, y, 40, 28);
            if (drawSteelButton(qp, "<", false))
                s.graphicsQuality = (s.graphicsQuality + 2) % 3;
            drawSteelButton(qm, ToyzBuilder.GameSettings.QUALITY_NAMES[s.graphicsQuality], false);
            if (drawSteelButton(qn, ">", false))
                s.graphicsQuality = (s.graphicsQuality + 1) % 3;
            y += 32;
            y = toggle("SMOOTH LIGHT", contentX, y, contentW, v -> s.smoothLighting = v, s.smoothLighting);
            y = toggle("CLOUDS", contentX, y, contentW, v -> s.clouds = v, s.clouds);
            y = toggle("PARTICLES", contentX, y, contentW, v -> s.particles = v, s.particles);
            y = toggle("ENTITY SHADOWS", contentX, y, contentW, v -> s.entityShadows = v, s.entityShadows);
            y = rowLabel("MAX FPS (0=UNCAPPED)", contentX, y);
            y = slider(contentX, y, contentW, v -> s.maxFps = Math.round(v / 5f) * 5, s.maxFps, 0f, 240f);
            y = rowLabel("SKYBOX", contentX, y);
            Rectangle sp = Helpers.newRectangle(contentX, y, 40, 28);
            Rectangle sm = Helpers.newRectangle(contentX + 44, y, contentW - 88, 28);
            Rectangle sn = Helpers.newRectangle(contentX + contentW - 40, y, 40, 28);
            if (drawSteelButton(sp, "<", false))
                s.skyboxIndex = (s.skyboxIndex + skyNames.size() - 1) % skyNames.size();
            drawSteelButton(sm, skyNames.get(s.skyboxIndex % skyNames.size()), false);
            if (drawSteelButton(sn, ">", false))
                s.skyboxIndex = (s.skyboxIndex + 1) % skyNames.size();
            y += 36;
            if (drawSteelButton(Helpers.newRectangle(contentX, y, contentW, 28), "APPLY CHUNK DIST", false))
                req.requestApplyGraphics = true;
            y += 36;
            DrawText("Wheel = scroll  |  APPLY after render dist", (int) contentX, (int) y, 12, PHOSPHOR_DIM);
        } else if (tab == 2) {
            DrawText("Enable / disable feature packs", (int) contentX, (int) y, 14, PHOSPHOR_DIM);
            y += 22;
            y = toggle("SURVIVAL", contentX, y, contentW, v -> s.modSurvival = v, s.modSurvival);
            y = toggle("STRUCTURES", contentX, y, contentW, v -> s.modStructures = v, s.modStructures);
            y = toggle("WEATHER", contentX, y, contentW, v -> s.modWeather = v, s.modWeather);
            y = toggle("MOBS", contentX, y, contentW, v -> s.modMobs = v, s.modMobs);
            y = toggle("WARP PIPES", contentX, y, contentW, v -> s.modWarpPipes = v, s.modWarpPipes);
            y = toggle("NETHER", contentX, y, contentW, v -> s.modNether = v, s.modNether);
            y = toggle("MAGNETIX ZONE", contentX, y, contentW, v -> s.modMagnetix = v, s.modMagnetix);
            y = toggle("CRAFTING", contentX, y, contentW, v -> s.modCrafting = v, s.modCrafting);
            y += 12;
            DrawText("Mods apply on next New World", (int) contentX, (int) y, 13, PHOSPHOR_DIM);
            y += 18;
            DrawText("or zone warp where relevant.", (int) contentX, (int) y, 13, PHOSPHOR_DIM);
        } else if (tab == 3) {
            y = toggle("SHADERS ENABLED", contentX, y, contentW, v -> s.shaderEnabled = v, s.shaderEnabled);
            y += 6;
            DrawText("Preset (tint until GLSL)", (int) contentX, (int) y, 13, PHOSPHOR_DIM);
            y += 20;
            for (int i = 0; i < ToyzBuilder.GameSettings.SHADER_NAMES.length; i++) {
                Rectangle br = Helpers.newRectangle(contentX, y, contentW, 28);
                boolean on = s.shaderPreset == i && s.shaderEnabled;
                if (drawSteelButton(br, ToyzBuilder.GameSettings.SHADER_NAMES[i], on)) {
                    s.shaderPreset = i;
                    s.shaderEnabled = (i != 0);
                    req.requestApplyGraphics = true;
                }
                y += 32;
            }
            y += 8;
            DrawText("Keep OFF for best FPS on older GPUs", (int) contentX, (int) y, 12, PHOSPHOR_DIM);
        }
        EndScissorMode();

        // Scrollbar track
        if (maxScroll > 1f) {
            float barX = area.x() + area.width() - 14f;
            float barY = contentTop;
            float barH = contentH;
            DrawRectangle((int) barX, (int) barY, 8, (int) barH, Helpers.newColor(40, 44, 50, 255));
            float thumbH = Math.max(24f, barH * (contentH / needH));
            float thumbY = barY + (barH - thumbH) * (scroll / maxScroll);
            DrawRectangle((int) barX, (int) thumbY, 8, (int) thumbH, PHOSPHOR_DIM);
        }
    }

    private static float rowLabel(String s, float x, float y) {
        DrawText(s, (int) x, (int) y, 15, PHOSPHOR_DIM);
        return y + 18;
    }

    private interface FloatSetter { void set(float v); }
    private interface BoolSetter { void set(boolean v); }

    private static float slider(float x, float y, float w,
                                FloatSetter setter, float value, float mn, float mx) {
        Rectangle track = Helpers.newRectangle(x, y, w, 12);
        DrawRectangleRec(track, Helpers.newColor(40, 44, 50, 255));
        float t = Math.max(0f, Math.min(1f, (value - mn) / (mx - mn)));
        DrawRectangle((int) x, (int) y, (int) (w * t), 12, PHOSPHOR_DIM);
        Vector2 m = GetMousePosition();
        if (CheckCollisionPointRec(m, track) && IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
            float nt = Math.max(0f, Math.min(1f, (m.x() - x) / w));
            setter.set(mn + nt * (mx - mn));
        }
        return y + 20;
    }

    private static float toggle(String name, float x, float y, float w, BoolSetter setter, boolean value) {
        Rectangle br = Helpers.newRectangle(x, y, w, 26);
        if (drawSteelButton(br, String.format("%s: %s", name, value ? "ON" : "OFF"), value))
            setter.set(!value);
        return y + 32;
    }

    public static void drawGameHud(String mode, String mapName, int pieceCount, boolean controller, float fps) {
        DrawText(String.format("MAP: %s", mapName), 12, 10, 16, PHOSPHOR);
        DrawText(String.format("MODE: %s", mode), 12, 30, 16, PHOSPHOR_DIM);
        DrawText(String.format("PIECES: %d", pieceCount), 12, 50, 16, PHOSPHOR_DIM);
        DrawText(String.format("FPS: %.0f", fps), 12, 70, 14, UI_WHITE);
        DrawText(controller ? "PAD: ON" : "PAD: OFF", 12, 88, 14, UI_WHITE);
        DrawText("ESC pause  |  E inventory  |  C camera", 12, GetScreenHeight() - 24, 14, PHOSPHOR_DIM);
    }
}