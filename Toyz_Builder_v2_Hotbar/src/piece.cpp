#include "piece.h"

static Model MakeLogModel(float length) {
    Mesh mesh = GenMeshCylinder(0.25f, length, 12);
    Model model = LoadModelFromMesh(mesh);
    return model;
}
static Model MakeBallModel() {
    Mesh mesh = GenMeshSphere(0.22f, 16, 16);
    Model model = LoadModelFromMesh(mesh);
    return model;
}
static Model MakeRodModel(float len) {
    Mesh mesh = GenMeshCylinder(0.08f, len, 10);
    Model model = LoadModelFromMesh(mesh);
    return model;
}
static Model MakeTrianglePanel() {
    // U-shaped triangle panel like Magnetix 60pc special parts
    Mesh mesh = GenMeshCube(1.0f, 0.08f, 0.6f);
    Model model = LoadModelFromMesh(mesh);
    return model;
}
static Model MakeLoloBlockModel() {
    Mesh mesh = GenMeshCube(1.0f, 1.0f, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    return model;
}

static RenderTexture2D RenderIcon(Model model, Color tint, int size = 128) {
    RenderTexture2D target = LoadRenderTexture(size, size);
    Camera3D iconCam = {0};
    iconCam.position = {2.0f, 2.0f, 2.0f};
    iconCam.target = {0,0,0};
    iconCam.up = {0,1,0};
    iconCam.fovy = 35.0f;
    iconCam.projection = CAMERA_PERSPECTIVE;
    BeginTextureMode(target);
        ClearBackground(BLANK);
        BeginMode3D(iconCam);
            DrawModel(model, {0,0,0}, 1.0f, tint);
        EndMode3D();
    EndTextureMode();
    return target;
}

Color GetColorFromEnum(PieceColor c) {
    switch(c) {
        case PieceColor::Red: return RED;
        case PieceColor::Green: return GREEN;
        case PieceColor::Blue: return BLUE;
        case PieceColor::Yellow: return YELLOW;
        case PieceColor::Purple: return PURPLE;
        case PieceColor::Orange: return ORANGE;
        case PieceColor::Pink: return PINK;
        case PieceColor::White: return WHITE;
        default: return DARKBROWN;
    }
}

std::vector<PieceDef> LoadPieceDefs() {
    std::vector<PieceDef> defs;

    // --- Lincoln Logs (your existing) ---
    {
        PieceDef d; d.type = PieceType::StraightLog; d.name = "Straight Log";
        d.model = MakeLogModel(1.0f); d.icon = RenderIcon(d.model, DARKBROWN);
        d.snapPoints = { {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::NotchedLog; d.name = "Notched Log";
        d.model = MakeLogModel(1.0f); d.icon = RenderIcon(d.model, BROWN);
        d.snapPoints = { {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}}, {{0.5f,0,0},{1,0,0}} };
        defs.push_back(d);
    }
    // --- Magnetix ---
    {
        PieceDef d; d.type = PieceType::MagnetixBall; d.name = "MAG Ball";
        d.model = MakeBallModel(); d.icon = RenderIcon(d.model, LIGHTGRAY);
        d.isMagnetic = true; d.halfExtents = {0.25f,0.25f,0.25f};
        // Balls snap from any direction
        d.snapPoints = { {{0,0,0},{0,1,0}}, {{0.22f,0,0},{1,0,0}}, {{-0.22f,0,0},{-1,0,0}}, {{0,0,0.22f},{0,0,1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod RED";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, RED);
        d.isMagnetic = true; d.defaultColor = PieceColor::Red; d.length = PieceLength::MED;
        // Magnetic ends - critical for your S snap mode
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod BLUE";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, BLUE);
        d.isMagnetic = true; d.defaultColor = PieceColor::Blue;
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixTriangle; d.name = "Xtreme Triangle";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, GREEN);
        d.isMagnetic = true; d.defaultColor = PieceColor::Green;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.3f},{0,0,1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixSquare; d.name = "Xtreme Square";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, YELLOW);
        d.isMagnetic = true; d.defaultColor = PieceColor::Yellow;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixFlag; d.name = "Castle Flag";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, SKYBLUE);
        d.snapPoints = { {{0,0,0},{0,1,0}} };
        defs.push_back(d);
    }
    // --- TECH ---
    {
        PieceDef d; d.type = PieceType::TechLight; d.name = "TECH Light (Flashing)";
        d.model = MakeBallModel(); d.icon = RenderIcon(d.model, RED);
        d.hasLight = true; d.snapPoints = { {{0,0,0},{0,1,0}} };
        defs.push_back(d);
    }
    // --- Lolo ---
    {
        PieceDef d; d.type = PieceType::LoloBlock; d.name = "Lolo Block (Pushable)";
        d.model = MakeLoloBlockModel(); d.icon = RenderIcon(d.model, BEIGE);
        d.halfExtents = {0.5f,0.5f,0.5f};
        d.snapPoints = {};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::LoloPlayer; d.name = "Lolo Mascot";
        d.model = MakeLoloBlockModel(); d.icon = RenderIcon(d.model, PINK);
        d.halfExtents = {0.4f,0.5f,0.4f};
        defs.push_back(d);
    }

    return defs;
}

void UnloadPieceDefs(std::vector<PieceDef>& defs) {
    for (auto& d : defs) { UnloadModel(d.model); UnloadRenderTexture(d.icon); }
    defs.clear();
}

void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs) {
    // Find def by type (since we have duplicate MagnetixRod types, find by color too)
    const PieceDef* defPtr = nullptr;
    for (auto& d : defs) {
        if (d.type == piece.type) {
            if (piece.type == PieceType::MagnetixRod) {
                if (d.defaultColor == piece.color) { defPtr = &d; break; }
            } else { defPtr = &d; break; }
        }
    }
    if (!defPtr) return;
    Color tint = GetColorFromEnum(piece.color);
    if (piece.type == PieceType::StraightLog || piece.type == PieceType::NotchedLog) tint = DARKBROWN;
    if (piece.selected) tint = YELLOW;
    else if (piece.snapHighlight) tint = RED;

    // Apply X/Z rotation for BOTW shrine mode (T)
    // Use DrawModelEx for Y, plus custom matrix for X/Z if needed
    Vector3 rotAxis = {0,1,0};
    float rotY = piece.rotationY + piece.rotationZ; // Z twist adds to Y for simplicity
    // For full XYZ we would use matrix, but raylib DrawModelEx only does one axis - keep simple for v5
    DrawModelEx(defPtr->model, piece.position, rotAxis, rotY, {1,1,1}, tint);

    // Draw magnetic tips for rods
    if (defPtr->isMagnetic && defPtr->type == PieceType::MagnetixRod) {
        Vector3 end1 = Vector3Add(piece.position, Vector3RotateByAxisAngle({-1,0,0}, {0,1,0}, rotY*DEG2RAD));
        Vector3 end2 = Vector3Add(piece.position, Vector3RotateByAxisAngle({1,0,0}, {0,1,0}, rotY*DEG2RAD));
        DrawSphere(end1, 0.11f, WHITE);
        DrawSphere(end2, 0.11f, WHITE);
        if (piece.snapHighlight) {
            DrawSphereWires(end1, 0.18f, 8,8, YELLOW);
            DrawSphereWires(end2, 0.18f, 8,8, YELLOW);
        }
    }
}

BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def) {
    float r = fmaxf(fmaxf(def.halfExtents.x, def.halfExtents.y), def.halfExtents.z);
    return { {piece.position.x - r, piece.position.y - r, piece.position.z - r},
             {piece.position.x + r, piece.position.y + r, piece.position.z + r} };
}
