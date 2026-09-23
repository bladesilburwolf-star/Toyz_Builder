#pragma once
#include "piece.h"
#include <vector>

struct InventoryState {
    bool isOpen = false;
    int selectedIndex = -1; // index into PieceDef vector, -1 = nothing selected
};

// Call every frame. Handles the E-key toggle and draws the ImGui grid when open.
// Returns true if a piece was just selected this frame (starts "placing" mode).
bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs);
