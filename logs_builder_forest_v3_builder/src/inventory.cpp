#include "inventory.h"
#include "imgui.h"
#include "rlImGui.h"

bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs) {
    if (IsKeyPressed(KEY_E)) {
        state.isOpen = !state.isOpen;
    }

    bool justSelected = false;
    if (!state.isOpen) return justSelected;

    // Cursor is only useful while the inventory is open; raylib's DisableCursor()
    // (used for FPS-style look) should be paired with EnableCursor() on open.
    ImGui::SetNextWindowSize(ImVec2(420, 320), ImGuiCond_FirstUseEver);
    ImGui::Begin("Inventory", &state.isOpen,
                  ImGuiWindowFlags_NoCollapse);

    const int columns = 4;
    ImGui::Columns(columns, nullptr, false);

    for (int i = 0; i < (int)defs.size(); i++) {
        const PieceDef& def = defs[i];
        ImGui::PushID(i);

        // rlImGui bridges a raylib RenderTexture2D into an ImGui image button.
        rlImGuiImageButtonSize(def.name.c_str(), &def.icon.texture, { 80, 80 });

        if (ImGui::IsItemClicked()) {
            state.selectedIndex = i;
            justSelected = true;
        }

        ImGui::TextWrapped("%s", def.name.c_str());
        ImGui::NextColumn();
        ImGui::PopID();
    }

    ImGui::Columns(1);
    ImGui::End();

    return justSelected;
}
