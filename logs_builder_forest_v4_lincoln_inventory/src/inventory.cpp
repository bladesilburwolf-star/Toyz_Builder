#include "inventory.h"
#include "imgui.h"
#include "rlImGui.h"
#include "raylib.h"
#include <cmath>

static int WrapIndex(int index, int count) {
    if (count <= 0) return 0;
    while (index < 0) index += count;
    while (index >= count) index -= count;
    return index;
}

bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs,
                     bool hasController, int gamepadId) {
    if (defs.empty()) return false;

    bool toggle = IsKeyPressed(KEY_E) ||
                  (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_UP));
    if (toggle) state.isOpen = !state.isOpen;

    if (!state.isOpen) return false;

    state.selectedIndex = WrapIndex(state.selectedIndex, (int)defs.size());
    bool justSelected = false;

    // Controller navigation is intentionally discrete so the inventory feels like
    // a real game menu instead of requiring a mouse.
    if (hasController) {
        static float repeatTimer = 0.0f;
        repeatTimer -= GetFrameTime();
        Vector2 stick = {
            GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_X),
            GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_Y)
        };
        int move = 0;
        if (repeatTimer <= 0.0f) {
            if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_LEFT) || stick.x < -0.7f) move = -1;
            else if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_RIGHT) || stick.x > 0.7f) move = 1;
            else if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_UP) || stick.y < -0.7f) move = -state.columns;
            else if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_DOWN) || stick.y > 0.7f) move = state.columns;
            if (move != 0) repeatTimer = 0.18f;
        }
        if (move != 0) state.selectedIndex = WrapIndex(state.selectedIndex + move, (int)defs.size());

        if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN)) {
            justSelected = true;
            state.isOpen = false;
        }
        if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT) || IsKeyPressed(KEY_ESCAPE)) {
            state.isOpen = false;
        }
    }

    ImGui::SetNextWindowSize(ImVec2(560, 380), ImGuiCond_FirstUseEver);
    ImGui::SetNextWindowFocus();
    ImGui::Begin("Build Inventory", &state.isOpen,
                 ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize);

    ImGui::Text("BUILD INVENTORY");
    ImGui::Text("A / Click: Select    B / Esc: Close    D-Pad / Left Stick: Browse");
    ImGui::Separator();

    for (int i = 0; i < (int)defs.size(); ++i) {
        ImGui::PushID(i);
        bool highlighted = (i == state.selectedIndex);
        if (highlighted) ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.18f, 0.48f, 0.80f, 1.0f));
        if (rlImGuiImageButtonSize(defs[i].name.c_str(), &defs[i].icon.texture, {96, 96})) {
            state.selectedIndex = i;
            justSelected = true;
            state.isOpen = false;
        }
        if (highlighted) ImGui::PopStyleColor();
        ImGui::TextWrapped("%s", defs[i].name.c_str());
        if ((i + 1) % state.columns != 0) ImGui::SameLine();
        ImGui::PopID();
    }

    ImGui::Separator();
    ImGui::Text("Selected: %s", defs[state.selectedIndex].name.c_str());
    ImGui::End();
    return justSelected;
}
