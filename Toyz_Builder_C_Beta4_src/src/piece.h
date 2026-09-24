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
    // Lincoln Logs (existing)
    StraightLog,
    NotchedLog,
    CornerLog,
    Roof,
    Window,
    // Magnetix - from your 200pc and 60pc Special Parts boxes
    MagnetixBall,           // silver ball
    MagnetixRod,            // colored rod with magnetic ends
    MagnetixTriangle,       // Xtreme Triangles
    MagnetixSquare,         // Xtreme Squares / U panels
    MagnetixFlag,           // from Flashing Lights Castle box
    // Tech (from your BPRoom1 inventory: LIGHTS, ENGINE, PULLEY)
    TechLight,              // flashing light
    TechEngine,
    TechPulley,
    // Adventures of Lolo clone mascot
    LoloBlock,              // pushable block
    LoloPlayer,             // Lolo mascot
    // --- Everything below was added after maps started saving PieceType as
    // a raw int (see map.cpp's "PIECE <int>..." line) - new types MUST be
    // appended here, never inserted above, or every existing save's piece
    // types silently shift to the wrong thing on load. ---
    Stairs,                 // simple ramp/steps piece
    TreeTrunkHorizS, TreeTrunkHorizM, TreeTrunkHorizL,
    TreeTrunkVertS,  TreeTrunkVertM,  TreeTrunkVertL,
    MagnetixPentagon,
    MagnetixHexagon,
    COUNT
};

enum class PieceColor {
    Natural,
    Red, Green, Blue, Yellow,
    Purple, Orange, Pink, White,
    COUNT
};

enum class PieceLength { SHT, MED, LRG };
enum class PieceOrient { HORIZ, VERT, ANGLE };

struct PieceDef {
    PieceType type;
    std::string name;
    Model model;
    RenderTexture2D icon;
    std::vector<SnapPoint> snapPoints;
    float rotationStepDeg = 90.0f;
    Vector3 halfExtents = { 0.3f, 0.3f, 0.6f };
    // ToyzBuilder extensions
    PieceColor defaultColor = PieceColor::Natural;
    PieceLength length = PieceLength::MED;
    PieceOrient orient = PieceOrient::HORIZ;
    bool isMagnetic = false; // for Magnetix
    bool hasLight = false;   // for TECH
};

struct PlacedPiece {
    PieceType type;
    Vector3 position;
    float rotationY;
    bool selected = false;
    bool snapHighlight = false;
    PieceColor color = PieceColor::Natural;
    PieceLength length = PieceLength::MED;
    float rotationX = 0; // for BOTW shrine rotate mode
    float rotationZ = 0;
};

std::vector<PieceDef> LoadPieceDefs();
void UnloadPieceDefs(std::vector<PieceDef>& defs);
void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs, Color tint = WHITE);
BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def);
Color GetColorFromEnum(PieceColor c);
// True for piece types where multiple PieceDef entries share one PieceType
// but differ by color (all the Magnetix shapes) - callers that pick a
// PieceDef for a PlacedPiece must match on color too for these, not just type.
bool UsesColorVariants(PieceType t);
