#include "inventory.h"
#include "imgui.h"
#include "rlImGui.h"

bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs) {
    bool justSelected = false;
    if (!state.isOpen) return justSelected;

    // Match your BPRoom1 mockup exactly
    ImGui::SetNextWindowPos(ImVec2(0,0), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(1280, 120), ImGuiCond_Always);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize, 2);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 0);
    ImGui::Begin("E To Close Inventory", &state.isOpen, 
        ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoTitleBar);
    
    // Custom title bar like screenshot
    ImGui::Text("E To Close Inventory");
    ImGui::Separator();

    // Grid matching screenshot: 7 columns
    ImGui::Columns(7, "inv", false);

    // Row 1
    if (ImGui::Button("ROD", ImVec2(120,40))) { state.filterType = PieceType::MagnetixRod; }
    ImGui::NextColumn();
    if (ImGui::Button("BALL", ImVec2(120,40))) { state.filterType = PieceType::MagnetixBall; }
    ImGui::NextColumn();
    if (ImGui::Button("RED", ImVec2(120,40))) { state.filterColor = PieceColor::Red; }
    ImGui::NextColumn();
    if (ImGui::Button("GREEN", ImVec2(120,40))) { state.filterColor = PieceColor::Green; }
    ImGui::NextColumn();
    if (ImGui::Button("BLUE", ImVec2(120,40))) { state.filterColor = PieceColor::Blue; }
    ImGui::NextColumn();
    if (ImGui::Button("YELLOW", ImVec2(120,40))) { state.filterColor = PieceColor::Yellow; }
    ImGui::NextColumn();
    ImGui::Text("LENGTH\nSHT MED LRG");
    if (ImGui::Button("SHT##len")) state.filterLength = PieceLength::SHT;
    ImGui::SameLine(); if (ImGui::Button("MED##len")) state.filterLength = PieceLength::MED;
    ImGui::SameLine(); if (ImGui::Button("LRG##len")) state.filterLength = PieceLength::LRG;
    ImGui::NextColumn();

    // Row 2
    ImGui::NextColumn(); // reset
    ImGui::Columns(7, "inv2", false);
    if (ImGui::Button("GEOMETRY", ImVec2(120,40))) { state.filterType = PieceType::MagnetixTriangle; }
    ImGui::NextColumn();
    if (ImGui::Button("TECH\n(LIGHTS, ENGINE, PULLEY,ETC...)", ImVec2(120,40))) { state.filterType = PieceType::TechLight; }
    ImGui::NextColumn();
    if (ImGui::Button("PURPLE", ImVec2(120,40))) { state.filterColor = PieceColor::Purple; }
    ImGui::NextColumn();
    if (ImGui::Button("ORANGE", ImVec2(120,40))) { state.filterColor = PieceColor::Orange; }
    ImGui::NextColumn();
    if (ImGui::Button("PINK", ImVec2(120,40))) { state.filterColor = PieceColor::Pink; }
    ImGui::NextColumn();
    if (ImGui::Button("WHITE", ImVec2(120,40))) { state.filterColor = PieceColor::White; }
    ImGui::NextColumn();
    ImGui::Text("ORIENT\nHORIZ VERT ANGLE");
    if (ImGui::Button("HORIZ##o")) state.filterOrient = PieceOrient::HORIZ;
    ImGui::SameLine(); if (ImGui::Button("VERT##o")) state.filterOrient = PieceOrient::VERT;
    ImGui::SameLine(); if (ImGui::Button("ANGLE##o")) state.filterOrient = PieceOrient::ANGLE;

    ImGui::Columns(1);

    // Piece grid below the mockup header - filtered by current selection
    ImGui::Separator();
    ImGui::Text("Filtered pieces:");
    for (int i=0; i<(int)defs.size(); i++) {
        const auto& def = defs[i];
        bool matches = true;
        // Simple filter logic
        if (state.filterType == PieceType::MagnetixBall && def.type != PieceType::MagnetixBall) matches = false;
        if (state.filterType == PieceType::MagnetixRod && def.type != PieceType::MagnetixRod) matches = false;

        if (!matches) continue;
        ImGui::PushID(i);
        rlImGuiImageButtonSize(def.name.c_str(), &def.icon.texture, {60,60});
        if (ImGui::IsItemClicked()) {
            state.selectedIndex = i;
            justSelected = true;
        }
        ImGui::SameLine();
        ImGui::PopID();
    }

    ImGui::End();
    ImGui::PopStyleVar(2);
    return justSelected;
}
