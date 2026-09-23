#pragma once
#include "raylib.h"
#include "raymath.h"
#include <vector>
#include <string>

// A snap point defined in the piece's LOCAL space.
// `normal` points outward along the direction another piece would connect from.
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

// Static definition of a piece type: its mesh, icon, and where it snaps.
struct PieceDef {
    PieceType type;
    std::string name;
    Model model;              // 3D mesh used in the scene
    RenderTexture2D icon;     // pre-rendered thumbnail for the inventory grid
    std::vector<SnapPoint> snapPoints;
    float rotationStepDeg = 90.0f; // snap increment when rotating with the gizmo
    // Approximate half-extents for selection raycasts (local space before rotation)
    Vector3 halfExtents = { 0.3f, 0.3f, 0.6f };
};

// A placed instance in the world.
struct PlacedPiece {
    PieceType type;
    Vector3 position;
    float rotationY; // degrees, world space
    bool selected = false;
    bool snapHighlight = false; // true when ghost is near this piece's snap points
};

// Loads all piece definitions (meshes + snap points) and pre-renders icons.
std::vector<PieceDef> LoadPieceDefs();
void UnloadPieceDefs(std::vector<PieceDef>& defs);

// Renders a single placed piece into the 3D scene.
void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs);

// World-space axis-aligned bounding box for a placed piece (for selection).
BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def);
