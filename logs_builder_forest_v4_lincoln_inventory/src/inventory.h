#pragma once
#include "piece.h"
#include <vector>

struct InventoryState {
    bool isOpen = false;
    int selectedIndex = 0;
    int columns = 4;
};

// Controller-friendly inventory. A selects, B closes, D-pad/left stick moves the cursor.
// E/Y toggles the inventory. Returns true when a piece was selected for placement.
bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs,
                     bool hasController, int gamepadId);
