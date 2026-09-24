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
    public enum TitleAction { None, NewWorld, Continue, Settings, Exit }

    public static class PauseMenuState {
        public boolean open = false;
        public boolean showOptionsPanel = false;
        public int selected = 0;
    }

    public static class TitleMenuState {
        public int selected = 0;
        public boolean showSettings = false;
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

        if (IsKeyPressed(KEY_DOWN) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_DOWN)))
            state.selected = (state.selected + 1) % 4;
        if (IsKeyPressed(KEY_UP) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_UP)))
            state.selected = (state.selected + 3) % 4;

        float panelW = 380f;
        float bx = (sw - panelW) * 0.5f;
        float by0 = sh * 0.42f;
        float bh = 44f;
        float gap = 12f;

        String[] labels = {
            "NEW WORLD",
            (lastMapName != null && !lastMapName.isEmpty()) ? String.format("CONTINUE  (%s)", lastMapName) : "CONTINUE",
            "SETTINGS",
            "EXIT"
        };
        TitleAction[] actions = { TitleAction.NewWorld, TitleAction.Continue, TitleAction.Settings, TitleAction.Exit };

        TitleAction result = TitleAction.None;
        for (int i = 0; i < 4; i++) {
            Rectangle br = Helpers.newRectangle(bx, by0 + i * (bh + gap), panelW, bh);
            if (drawSteelButton(br, labels[i], state.selected == i)) {
                result = actions[i];
                state.selected = i;
            }
        }

        boolean confirm = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)
            || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
        if (confirm) result = actions[state.selected];

        DrawText("WASD move  |  mouse look  |  E inventory  |  ESC pause", 20, sh - 36, 16, PHOSPHOR_DIM);
        DrawText("F11 fullscreen", 20, sh - 18, 14, PHOSPHOR_DIM);
        return result;
    }

    /** Mutable settings reference passed in and edited live, replacing the C pointer args. */
    public static class OptionsRequest {
        public boolean requestApplyRes = false;
        public boolean requestToggleFs = false;
    }

    public static void drawOptionsPanel(Rectangle area, ToyzBuilder.GameSettings s,
                                        List<String> skyNames, String[] resNames,
                                        OptionsRequest req) {
        drawSteelPanel(area, "SETTINGS");

        float x = area.x() + 16;
        float y = area.y() + 36;
        float w = area.width() - 32;

        y = rowLabel("Mouse Sens", x, y);
        y = slider(x, y, w, v -> s.mouseSensitivity = v, s.mouseSensitivity, 0.05f, 0.8f);
        y = rowLabel("Gamepad Look", x, y);
        y = slider(x, y, w, v -> s.gamepadLookSensitivity = v, s.gamepadLookSensitivity, 40f, 300f);
        y = rowLabel("Arrow Look", x, y);
        y = slider(x, y, w, v -> s.arrowLookSpeed = v, s.arrowLookSpeed, 30f, 180f);
        y = toggle("Invert X", x, y, w, v -> s.invertX = v, s.invertX);
        y = toggle("Invert Y", x, y, w, v -> s.invertY = v, s.invertY);
        y = toggle("Third Person", x, y, w, v -> s.thirdPerson = v, s.thirdPerson);
        if (s.thirdPerson) {
            y = rowLabel("Cam Distance", x, y);
            y = slider(x, y, w, v -> s.thirdPersonDistance = v, s.thirdPersonDistance, 2f, 14f);
            y = rowLabel("Cam Height", x, y);
            y = slider(x, y, w, v -> s.thirdPersonHeight = v, s.thirdPersonHeight, 0.5f, 5f);
        }
        y = rowLabel("Tree Draw Dist", x, y);
        y = slider(x, y, w, v -> s.treeDrawDistance = v, s.treeDrawDistance, 40f, 200f);

        y = rowLabel("Sky", x, y);
        if (!skyNames.isEmpty()) {
            Rectangle prev = Helpers.newRectangle(x, y, 36, 26);
            Rectangle next = Helpers.newRectangle(x + w - 36, y, 36, 26);
            Rectangle mid = Helpers.newRectangle(x + 40, y, w - 80, 26);
            if (drawSteelButton(prev, "<", false))
                s.skyboxIndex = (s.skyboxIndex - 1 + skyNames.size()) % skyNames.size();
            drawSteelButton(mid, skyNames.get(s.skyboxIndex), false);
            if (drawSteelButton(next, ">", false))
                s.skyboxIndex = (s.skyboxIndex + 1) % skyNames.size();
            y += 32;
        }

        if (resNames != null && resNames.length > 0) {
            y = rowLabel("Resolution", x, y);
            Rectangle prev = Helpers.newRectangle(x, y, 36, 26);
            Rectangle next = Helpers.newRectangle(x + w - 36, y, 36, 26);
            Rectangle mid = Helpers.newRectangle(x + 40, y, w - 80, 26);
            if (drawSteelButton(prev, "<", false))
                s.resolutionIndex = (s.resolutionIndex - 1 + resNames.length) % resNames.length;
            drawSteelButton(mid, resNames[s.resolutionIndex], false);
            if (drawSteelButton(next, ">", false))
                s.resolutionIndex = (s.resolutionIndex + 1) % resNames.length;
            y += 32;
            Rectangle apply = Helpers.newRectangle(x, y, w * 0.48f, 28);
            Rectangle fs = Helpers.newRectangle(x + w * 0.52f, y, w * 0.48f, 28);
            if (drawSteelButton(apply, "APPLY RES", false)) req.requestApplyRes = true;
            if (drawSteelButton(fs, "FULLSCREEN", false)) req.requestToggleFs = true;
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