#include "piece.h"
#include <string>

// Shared textures for pieces (loaded once, unbound on UnloadPieceDefs)
static Texture2D g_texBark = {0};
static Texture2D g_texMetal = {0};
static Texture2D g_texPlanks = {0};
static Texture2D g_texStone = {0};
static Texture2D g_texGlass = {0};

static Texture2D TryLoadTex(const char* path) {
    Texture2D t = LoadTexture(path);
    if (t.id != 0) {
        SetTextureFilter(t, TEXTURE_FILTER_BILINEAR);
        SetTextureWrap(t, TEXTURE_WRAP_REPEAT);
    }
    return t;
}

static void ApplyDiffuse(Model& model, Texture2D tex, Color tint) {
    if (model.materialCount < 1) return;
    if (tex.id != 0) {
        SetMaterialTexture(&model.materials[0], MATERIAL_MAP_DIFFUSE, tex);
    }
    model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = tint;
}

static Model MakeLogModel(float length) {
    Mesh mesh = GenMeshCylinder(0.25f, length, 12);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texBark, WHITE);
    return model;
}
static Model MakeBallModel() {
    Mesh mesh = GenMeshSphere(0.22f, 12, 12);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texMetal, WHITE);
    return model;
}
static Model MakeRodModel(float len) {
    Mesh mesh = GenMeshCylinder(0.08f, len, 10);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texMetal, WHITE);
    return model;
}
static Model MakeTrianglePanel() {
    Mesh mesh = GenMeshCube(1.0f, 0.08f, 0.6f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texMetal, WHITE);
    return model;
}
static Model MakeLoloBlockModel() {
    Mesh mesh = GenMeshCube(1.0f, 1.0f, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texPlanks, WHITE);
    return model;
}
static Model MakeStoneBlockModel() {
    Mesh mesh = GenMeshCube(1.0f, 0.5f, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texStone, WHITE);
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
        default: return Color{220, 200, 170, 255}; // natural / light wood
    }
}

std::vector<PieceDef> LoadPieceDefs() {
    // Load once for all piece models (safe if assets/ missing — solid colors still work)
    if (g_texBark.id == 0)   g_texBark   = TryLoadTex("assets/textures/bark1.png");
    if (g_texMetal.id == 0)  g_texMetal  = TryLoadTex("assets/textures/metal1.png");
    if (g_texPlanks.id == 0) g_texPlanks = TryLoadTex("assets/textures/planks1.png");
    if (g_texStone.id == 0)  g_texStone  = TryLoadTex("assets/textures/stone1.png");
    if (g_texGlass.id == 0)  g_texGlass  = TryLoadTex("assets/textures/glass1.png");

    std::vector<PieceDef> defs;

    // --- Lincoln Logs ---
    {
        PieceDef d; d.type = PieceType::StraightLog; d.name = "Straight Log";
        d.model = MakeLogModel(1.0f); d.icon = RenderIcon(d.model, WHITE);
        d.snapPoints = { {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::NotchedLog; d.name = "Notched Log";
        d.model = MakeLogModel(1.0f); d.icon = RenderIcon(d.model, Color{230,210,180,255});
        d.snapPoints = { {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}}, {{0.5f,0,0},{1,0,0}} };
        defs.push_back(d);
    }
    // --- Magnetix ---
    {
        PieceDef d; d.type = PieceType::MagnetixBall; d.name = "MAG Ball";
        d.model = MakeBallModel(); d.icon = RenderIcon(d.model, LIGHTGRAY);
        d.isMagnetic = true; d.halfExtents = {0.25f,0.25f,0.25f};
        d.snapPoints = { {{0,0,0},{0,1,0}}, {{0.22f,0,0},{1,0,0}}, {{-0.22f,0,0},{-1,0,0}}, {{0,0,0.22f},{0,0,1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod RED";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, RED);
        d.isMagnetic = true; d.defaultColor = PieceColor::Red; d.length = PieceLength::MED;
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        // Tint the metal texture with rod color
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{255, 80, 80, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod BLUE";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, BLUE);
        d.isMagnetic = true; d.defaultColor = PieceColor::Blue;
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 120, 255, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixTriangle; d.name = "Xtreme Triangle";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, GREEN);
        d.isMagnetic = true; d.defaultColor = PieceColor::Green;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.3f},{0,0,1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 220, 100, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixSquare; d.name = "Xtreme Square";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, YELLOW);
        d.isMagnetic = true; d.defaultColor = PieceColor::Yellow;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{255, 230, 80, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixFlag; d.name = "Castle Flag";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, SKYBLUE);
        d.snapPoints = { {{0,0,0},{0,1,0}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{100, 180, 255, 255};
        defs.push_back(d);
    }
    // --- TECH ---
    {
        PieceDef d; d.type = PieceType::TechLight; d.name = "TECH Light (Flashing)";
        d.model = MakeBallModel(); d.icon = RenderIcon(d.model, RED);
        d.hasLight = true; d.snapPoints = { {{0,0,0},{0,1,0}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{255, 60, 40, 255};
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
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{255, 180, 200, 255};
        defs.push_back(d);
    }

    return defs;
}

void UnloadPieceDefs(std::vector<PieceDef>& defs) {
    for (auto& d : defs) { UnloadModel(d.model); UnloadRenderTexture(d.icon); }
    defs.clear();
    // Shared textures (models no longer reference them after UnloadModel)
    if (g_texBark.id)   { UnloadTexture(g_texBark);   g_texBark = {0}; }
    if (g_texMetal.id)  { UnloadTexture(g_texMetal);  g_texMetal = {0}; }
    if (g_texPlanks.id) { UnloadTexture(g_texPlanks); g_texPlanks = {0}; }
    if (g_texStone.id)  { UnloadTexture(g_texStone);  g_texStone = {0}; }
    if (g_texGlass.id)  { UnloadTexture(g_texGlass);  g_texGlass = {0}; }
}

void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs, Color ambientTint) {
    const PieceDef* defPtr = nullptr;
    for (auto& d : defs) {
        if (d.type == piece.type) {
            if (piece.type == PieceType::MagnetixRod) {
                if (d.defaultColor == piece.color) { defPtr = &d; break; }
            } else { defPtr = &d; break; }
        }
    }
    if (!defPtr) return;

    // Base tint: logs stay natural, colored pieces keep their color, selection overrides
    Color pieceTint = WHITE;
    if (piece.type == PieceType::StraightLog || piece.type == PieceType::NotchedLog) {
        pieceTint = WHITE; // bark texture shows through
    } else if (piece.type == PieceType::MagnetixRod || piece.type == PieceType::MagnetixTriangle
            || piece.type == PieceType::MagnetixSquare || piece.type == PieceType::MagnetixFlag
            || piece.type == PieceType::TechLight) {
        pieceTint = GetColorFromEnum(piece.color);
        if (piece.color == PieceColor::Natural) pieceTint = WHITE;
    } else if (piece.type == PieceType::MagnetixBall) {
        pieceTint = LIGHTGRAY;
    }

    if (piece.selected) pieceTint = YELLOW;
    else if (piece.snapHighlight) pieceTint = RED;

    float heightFactor = Clamp(piece.position.y / 12.0f, 0.0f, 1.0f);
    Color shadedTint = {
        (unsigned char)(pieceTint.r * (0.85f + heightFactor * 0.15f)),
        (unsigned char)(pieceTint.g * (0.85f + heightFactor * 0.15f)),
        (unsigned char)(pieceTint.b * (0.85f + heightFactor * 0.15f)),
        pieceTint.a
    };
    // Fold in day/night ambient last so selection/snap highlight colors
    // still read clearly even when dimmed at night.
    shadedTint = ColorTint(shadedTint, ambientTint);

    DrawModelEx(defPtr->model, piece.position, {0, 1, 0}, piece.rotationY, {1, 1, 1}, shadedTint);
    if (fabsf(piece.rotationX) > 0.01f) {
        DrawModelEx(defPtr->model, piece.position, {1, 0, 0}, piece.rotationX, {1, 1, 1}, Fade(shadedTint, 0.35f));
    }

    if (defPtr->isMagnetic && defPtr->type == PieceType::MagnetixRod) {
        Vector3 end1 = Vector3Add(piece.position, Vector3RotateByAxisAngle({-1,0,0}, {0,1,0}, piece.rotationY*DEG2RAD));
        Vector3 end2 = Vector3Add(piece.position, Vector3RotateByAxisAngle({1,0,0}, {0,1,0}, piece.rotationY*DEG2RAD));
        DrawSphere(end1, 0.11f, ambientTint);
        DrawSphere(end2, 0.11f, ambientTint);
        if (piece.snapHighlight) {
            DrawSphereWires(end1, 0.18f, 8, 8, YELLOW);
            DrawSphereWires(end2, 0.18f, 8, 8, YELLOW);
        }
    }
}

BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def) {
    float r = fmaxf(fmaxf(def.halfExtents.x, def.halfExtents.y), def.halfExtents.z);
    return { {piece.position.x - r, piece.position.y - r, piece.position.z - r},
             {piece.position.x + r, piece.position.y + r, piece.position.z + r} };
}
