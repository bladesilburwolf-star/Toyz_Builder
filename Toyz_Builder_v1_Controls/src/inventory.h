#pragma once
#include "piece.h"
#include <vector>

struct InventoryState {
    bool isOpen = true; // start open like your BPRoom1 mockup
    int selectedIndex = -1;
    // BPRoom1 categories
    PieceType filterType = PieceType::StraightLog; // ROD/BALL/GEOMETRY/TECH
    PieceColor filterColor = PieceColor::Red;
    PieceLength filterLength = PieceLength::MED;
    PieceOrient filterOrient = PieceOrient::HORIZ;
    bool showLength = true;
    bool showOrient = true;
};

bool UpdateInventory(InventoryState& state, const std::vector<PieceDef>& defs);
