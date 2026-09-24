#pragma once
#include "piece.h"
#include <vector>

// --- Minecraft-style hotbar ---
// 9 slots, each either empty (-1) or an index into the PieceDef vector.
// The currently `selected` slot is what you're holding / about to place.
struct Hotbar {
    static const int SLOT_COUNT = 9;
    int slots[SLOT_COUNT] = { -1, -1, -1, -1, -1, -1, -1, -1, -1 };
    int selected = 0;

    int HeldPieceIndex() const { return slots[selected]; }
};

struct InventoryState {
    bool isOpen = false;
    int filterCategory = 0; // 0 = All, see UpdateFullInventory for the list
};

// Always call once per frame (regardless of isOpen) to draw the persistent
// bottom hotbar. Click a slot to select it; right-click clears it. Number
// keys (1-9) and scroll-wheel selection are handled by the caller in
// main.cpp so they can be disabled while a menu has input focus.
void DrawHotbar(Hotbar& hotbar, const std::vector<PieceDef>& defs);

// Call only while InventoryState::isOpen. Draws the full, categorized piece
// grid; dragging a piece icon onto a hotbar slot (drawn separately by
// DrawHotbar) assigns it there via ImGui's drag-and-drop.
void UpdateFullInventory(InventoryState& state, Hotbar& hotbar, const std::vector<PieceDef>& defs);
