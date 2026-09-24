#include "ui.h"
#include <cmath>

void DrawSteelPanel(Rectangle r, const char* title) {
    // Outer edge
    DrawRectangleRec(r, SSW::SteelEdge());
    Rectangle inner = { r.x + 2, r.y + 2, r.width - 4, r.height - 4 };
    // Vertical steel gradient (approximate with strips)
    int strips = (int)(inner.height / 4.0f);
    if (strips < 1) strips = 1;
    for (int i = 0; i < strips; i++) {
        float t = (float)i / (float)strips;
        Color c = {
            (unsigned char)(SSW::SteelTop().r * (1.0f - t) + SSW::SteelBot().r * t),
            (unsigned char)(SSW::SteelTop().g * (1.0f - t) + SSW::SteelBot().g * t),
            (unsigned char)(SSW::SteelTop().b * (1.0f - t) + SSW::SteelBot().b * t),
            245
        };
        DrawRectangle((int)inner.x, (int)(inner.y + i * 4), (int)inner.width, 5, c);
    }
    // Title bar
    DrawRectangle((int)inner.x, (int)inner.y, (int)inner.width, 28, SSW::Panel());
    DrawText(title, (int)inner.x + 12, (int)inner.y + 6, 18, SSW::Phosphor());
    // Bottom phosphor line
    DrawRectangle((int)inner.x, (int)(inner.y + inner.height - 3), (int)inner.width, 2, SSW::PhosphorDim());
}

bool DrawSteelButton(Rectangle r, const char* label, bool selected) {
    Vector2 m = GetMousePosition();
    bool hover = CheckCollisionPointRec(m, r);
    bool pressed = hover && IsMouseButtonPressed(MOUSE_BUTTON_LEFT);

    Color fill = selected || hover ? Color{35, 42, 38, 255} : Color{22, 24, 28, 255};
    Color border = selected || hover ? SSW::Phosphor() : SSW::SteelEdge();
    DrawRectangleRec(r, border);
    DrawRectangle((int)r.x + 1, (int)r.y + 1, (int)r.width - 2, (int)r.height - 2, fill);

    int tw = MeasureText(label, 18);
    Color textCol = selected || hover ? SSW::Phosphor() : SSW::White();
    DrawText(label, (int)(r.x + (r.width - tw) * 0.5f), (int)(r.y + r.height * 0.5f - 9), 18, textCol);
    return pressed;
}

PauseAction DrawPauseMenu(PauseMenuState& state, bool hasController) {
    if (!state.open) return PauseAction::None;

    int sw = GetScreenWidth(), sh = GetScreenHeight();
    // Dim world behind menu
    DrawRectangle(0, 0, sw, sh, Color{0, 0, 0, 160});

    float panelW = 360.0f;
    float panelH = state.showOptionsPanel ? 520.0f : 340.0f;
    Rectangle panel = { (sw - panelW) * 0.5f, (sh - panelH) * 0.5f, panelW, panelH };
    DrawSteelPanel(panel, "TOYZ BUILDER  //  PAUSED");

    // Keyboard / pad navigation
    if (IsKeyPressed(KEY_DOWN) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_DOWN)))
        state.selected = (state.selected + 1) % 5;
    if (IsKeyPressed(KEY_UP) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_UP)))
        state.selected = (state.selected + 4) % 5;

    const char* labels[5] = {
        "RESUME",
        "OPTIONS",
        "SAVE MAP",
        "NEW MAP",
        "EXIT TO DESKTOP"
    };
    PauseAction actions[5] = {
        PauseAction::Resume,
        PauseAction::ToggleOptions,
        PauseAction::SaveMap,
        PauseAction::NewMap,
        PauseAction::ExitGame
    };

    float bx = panel.x + 40.0f;
    float by = panel.y + 48.0f;
    float bw = panelW - 80.0f;
    float bh = 36.0f;
    float gap = 10.0f;

    PauseAction result = PauseAction::None;
    for (int i = 0; i < 5; i++) {
        Rectangle br = { bx, by + i * (bh + gap), bw, bh };
        if (DrawSteelButton(br, labels[i], state.selected == i)) {
            result = actions[i];
            state.selected = i;
        }
    }

    // Confirm with Enter / A
    bool confirm = IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)
        || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
    if (confirm) result = actions[state.selected];

    DrawText("ESC resume   |   arrows select   |   enter confirm",
             (int)(panel.x + 24), (int)(panel.y + panelH - 28), 14, SSW::PhosphorDim());

    return result;
}

void DrawOptionsPanel(Rectangle area,
                      float* mouseSens, float* gamepadSens, float* arrowSens,
                      bool* invertX, bool* invertY, bool* thirdPerson,
                      float* camDist, float* camHeight,
                      int* skyIndex, const std::vector<std::string>& skyNames,
                      int* resIndex, const char** resNames, int resCount,
                      bool* requestApplyRes, bool* requestToggleFs) {
    DrawSteelPanel(area, "OPTIONS");

    float x = area.x + 20;
    float y = area.y + 40;
    float w = area.width - 40;

    auto rowLabel = [&](const char* s) {
        DrawText(s, (int)x, (int)y, 16, SSW::PhosphorDim());
        y += 22;
    };
    auto slider = [&](const char* name, float* v, float mn, float mx) {
        rowLabel(TextFormat("%s  %.2f", name, *v));
        Rectangle track = { x, y, w, 14 };
        DrawRectangleRec(track, Color{40, 44, 50, 255});
        float t = (*v - mn) / (mx - mn);
        if (t < 0) t = 0; if (t > 1) t = 1;
        DrawRectangle((int)x, (int)y, (int)(w * t), 14, SSW::PhosphorDim());
        Vector2 m = GetMousePosition();
        if (CheckCollisionPointRec(m, track) && IsMouseButtonDown(MOUSE_BUTTON_LEFT)) {
            float nt = (m.x - x) / w;
            if (nt < 0) nt = 0; if (nt > 1) nt = 1;
            *v = mn + nt * (mx - mn);
        }
        y += 24;
    };
    auto toggle = [&](const char* name, bool* v) {
        Rectangle br = { x, y, w, 28 };
        if (DrawSteelButton(br, TextFormat("%s: %s", name, *v ? "ON" : "OFF"), *v))
            *v = !*v;
        y += 36;
    };

    slider("Mouse Sens", mouseSens, 0.05f, 0.8f);
    slider("Gamepad Look", gamepadSens, 40.0f, 300.0f);
    slider("Arrow Look", arrowSens, 30.0f, 180.0f);
    toggle("Invert X", invertX);
    toggle("Invert Y", invertY);
    toggle("Third Person", thirdPerson);
    if (*thirdPerson) {
        slider("Cam Distance", camDist, 2.0f, 14.0f);
        slider("Cam Height", camHeight, 0.5f, 5.0f);
    }

    // Sky cycle
    rowLabel("Sky");
    if (!skyNames.empty()) {
        Rectangle prev = { x, y, 40, 28 };
        Rectangle next = { x + w - 40, y, 40, 28 };
        Rectangle mid = { x + 44, y, w - 88, 28 };
        if (DrawSteelButton(prev, "<", false)) {
            *skyIndex = (*skyIndex - 1 + (int)skyNames.size()) % (int)skyNames.size();
        }
        DrawSteelButton(mid, skyNames[*skyIndex].c_str(), false);
        if (DrawSteelButton(next, ">", false)) {
            *skyIndex = (*skyIndex + 1) % (int)skyNames.size();
        }
        y += 36;
    }

    // Resolution
    if (resNames && resCount > 0) {
        rowLabel("Resolution");
        Rectangle prev = { x, y, 40, 28 };
        Rectangle next = { x + w - 40, y, 40, 28 };
        Rectangle mid = { x + 44, y, w - 88, 28 };
        if (DrawSteelButton(prev, "<", false))
            *resIndex = (*resIndex - 1 + resCount) % resCount;
        DrawSteelButton(mid, resNames[*resIndex], false);
        if (DrawSteelButton(next, ">", false))
            *resIndex = (*resIndex + 1) % resCount;
        y += 36;
        Rectangle apply = { x, y, w * 0.48f, 30 };
        Rectangle fs = { x + w * 0.52f, y, w * 0.48f, 30 };
        if (DrawSteelButton(apply, "APPLY RES", false)) *requestApplyRes = true;
        if (DrawSteelButton(fs, "FULLSCREEN", false)) *requestToggleFs = true;
        y += 36;
    }
}

void DrawGameHud(const char* mode, const char* mapName, int pieceCount, bool controller) {
    DrawText(TextFormat("MAP: %s", mapName), 12, 10, 16, SSW::Phosphor());
    DrawText(TextFormat("MODE: %s", mode), 12, 30, 16, SSW::PhosphorDim());
    DrawText(TextFormat("PIECES: %d", pieceCount), 12, 50, 16, SSW::PhosphorDim());
    DrawText(controller ? "PAD: CONNECTED" : "PAD: NONE", 12, 70, 14, SSW::White());
    DrawText("ESC pause  |  E inventory  |  C camera", 12, GetScreenHeight() - 24, 14, SSW::PhosphorDim());
}
