#include "inventory.h"
#include "imgui.h"
#include "rlImGui.h"
#include <string>

static int CategoryOf(PieceType t) {
    switch (t) {
        case PieceType::StraightLog:
        case PieceType::NotchedLog:
        case PieceType::CornerLog:
        case PieceType::Roof:
        case PieceType::Window:
        case PieceType::Stairs:
        case PieceType::TreeTrunkHorizS:
        case PieceType::TreeTrunkHorizM:
        case PieceType::TreeTrunkHorizL:
        case PieceType::TreeTrunkVertS:
        case PieceType::TreeTrunkVertM:
        case PieceType::TreeTrunkVertL:
            return 1; // Lincoln Logs
        case PieceType::MagnetixBall:
        case PieceType::MagnetixRod:
        case PieceType::MagnetixTriangle:
        case PieceType::MagnetixSquare:
        case PieceType::MagnetixPentagon:
        case PieceType::MagnetixHexagon:
        case PieceType::MagnetixFlag:
            return 2; // Magnetix
        case PieceType::TechLight:
        case PieceType::TechEngine:
        case PieceType::TechPulley:
            return 3; // Tech
        case PieceType::LoloBlock:
        case PieceType::LoloPlayer:
            return 4; // Lolo
        default:
            return 0;
    }
}

static const char* CategoryName(int c) {
    switch (c) {
        case 1: return "Lincoln Logs";
        case 2: return "Magnetix";
        case 3: return "Tech";
        case 4: return "Lolo";
        default: return "All";
    }
}

static const char* HOTBAR_PAYLOAD = "PIECE_DEF_INDEX";

void DrawHotbar(Hotbar& hotbar, const std::vector<PieceDef>& defs) {
    const float slotSize = 56.0f;
    const float padding = 6.0f;
    const float totalWidth = Hotbar::SLOT_COUNT * (slotSize + padding) - padding;
    ImGuiIO& io = ImGui::GetIO();
    float startX = (io.DisplaySize.x - totalWidth) * 0.5f;
    float startY = io.DisplaySize.y - slotSize - 20.0f;

    ImGui::SetNextWindowPos(ImVec2(startX - 8, startY - 8), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(totalWidth + 16, slotSize + 16), ImGuiCond_Always);
    ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.05f, 0.05f, 0.05f, 0.55f));
    ImGui::Begin("##Hotbar", nullptr,
        ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoScrollbar |
        ImGuiWindowFlags_NoNav | ImGuiWindowFlags_NoFocusOnAppearing);

    for (int i = 0; i < Hotbar::SLOT_COUNT; i++) {
        if (i > 0) ImGui::SameLine(0, padding);
        ImGui::PushID(i);

        bool isSelected = (hotbar.selected == i);
        ImVec4 border = isSelected ? ImVec4(1, 0.85f, 0.2f, 1) : ImVec4(0.4f, 0.4f, 0.4f, 1);
        ImGui::PushStyleColor(ImGuiCol_Border, border);
        ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, isSelected ? 3.0f : 1.5f);

        int pieceIdx = hotbar.slots[i];
        if (pieceIdx >= 0 && pieceIdx < (int)defs.size()) {
            rlImGuiImageButtonSize("##slot", &defs[pieceIdx].icon.texture, { slotSize, slotSize });
        } else {
            ImGui::Button("##slot_empty", ImVec2(slotSize, slotSize));
        }

        if (ImGui::IsItemClicked(ImGuiMouseButton_Left)) {
            hotbar.selected = i;
        }
        if (ImGui::IsItemClicked(ImGuiMouseButton_Right)) {
            hotbar.slots[i] = -1;
        }

        if (ImGui::BeginDragDropTarget()) {
            if (const ImGuiPayload* payload = ImGui::AcceptDragDropPayload(HOTBAR_PAYLOAD)) {
                int dropped = *(const int*)payload->Data;
                hotbar.slots[i] = dropped;
            }
            ImGui::EndDragDropTarget();
        }

        // Slot number label (1-9), bottom-left corner of the slot.
        ImVec2 minPos = ImGui::GetItemRectMin();
        ImGui::GetWindowDrawList()->AddText(ImVec2(minPos.x + 3, minPos.y + 2), IM_COL32(255,255,255,200),
                                             (i == 8) ? "9" : std::to_string(i + 1).c_str());

        ImGui::PopStyleVar();
        ImGui::PopStyleColor();
        ImGui::PopID();
    }

    ImGui::End();
    ImGui::PopStyleColor();
}

void UpdateFullInventory(InventoryState& state, Hotbar& hotbar, const std::vector<PieceDef>& defs) {
    ImGui::SetNextWindowSize(ImVec2(560, 420), ImGuiCond_FirstUseEver);
    ImGui::Begin("Inventory [E]", &state.isOpen);

    ImGui::TextDisabled("Drag a piece onto a hotbar slot below to equip it.");
    ImGui::Separator();

    // Category tabs
    for (int c = 0; c <= 4; c++) {
        if (c > 0) ImGui::SameLine();
        bool active = (state.filterCategory == c);
        if (active) ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.26f,0.59f,0.98f,0.8f));
        if (ImGui::Button(CategoryName(c))) state.filterCategory = c;
        if (active) ImGui::PopStyleColor();
    }
    ImGui::Separator();

    const int columns = 6;
    ImGui::Columns(columns, nullptr, false);
    for (int i = 0; i < (int)defs.size(); i++) {
        const PieceDef& def = defs[i];
        if (state.filterCategory != 0 && CategoryOf(def.type) != state.filterCategory) continue;

        ImGui::PushID(i);
        rlImGuiImageButtonSize(def.name.c_str(), &def.icon.texture, { 64, 64 });

        if (ImGui::BeginDragDropSource(ImGuiDragDropFlags_None)) {
            ImGui::SetDragDropPayload(HOTBAR_PAYLOAD, &i, sizeof(int));
            rlImGuiImageSize(&def.icon.texture, 48, 48);
            ImGui::Text("%s", def.name.c_str());
            ImGui::EndDragDropSource();
        }

        // Left-click also equips straight into the currently selected hotbar
        // slot, for anyone who'd rather click than drag.
        if (ImGui::IsItemClicked(ImGuiMouseButton_Left)) {
            hotbar.slots[hotbar.selected] = i;
        }

        ImGui::TextWrapped("%s", def.name.c_str());
        ImGui::NextColumn();
        ImGui::PopID();
    }
    ImGui::Columns(1);

    ImGui::End();
}
