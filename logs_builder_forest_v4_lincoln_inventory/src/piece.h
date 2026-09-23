#pragma once
#include "raylib.h"
#include "raymath.h"
#include <vector>
#include <string>

struct SnapPoint {
    Vector3 position;
    Vector3 normal;
};

enum class PieceType {
    StraightLog,
    NotchedLog,
    CornerLog,
    Roof,
    Window,
    COUNT
};

struct PieceDef {
    PieceType type;
    std::string name;
    Model model;
    RenderTexture2D icon;
    std::vector<SnapPoint> snapPoints;
    float rotationStepDeg = 90.0f;
    Vector3 halfExtents = { 0.3f, 0.3f, 0.6f };
    bool isStructuralLog = false;
    float length = 1.0f;
    float radius = 0.25f;
};

struct PlacedPiece {
    PieceType type;
    Vector3 position;
    float rotationY = 0.0f;
    bool selected = false;
    bool snapHighlight = false;
    int layer = 0;
};

std::vector<PieceDef> LoadPieceDefs();
void UnloadPieceDefs(std::vector<PieceDef>& defs);
void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs);
BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def);
