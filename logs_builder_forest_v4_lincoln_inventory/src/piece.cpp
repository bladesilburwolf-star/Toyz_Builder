#include "piece.h"
#include <cmath>

static Model MakeHorizontalLogModel(float length) {
    Mesh mesh = GenMeshCylinder(0.25f, length, 16);
    Model model = LoadModelFromMesh(mesh);
    // GenMeshCylinder is vertical (Y axis). Bake a 90-degree X rotation so
    // the finished log lies horizontally and rotationY can control its heading.
    model.transform = MatrixRotateX(90.0f * DEG2RAD);
    return model;
}

static Model MakeRoofModel() {
    return LoadModelFromMesh(GenMeshCube(2.0f, 0.15f, 2.0f));
}

static Model MakeWindowModel() {
    return LoadModelFromMesh(GenMeshCube(0.9f, 0.9f, 0.12f));
}

static RenderTexture2D RenderIcon(Model model, int size = 128) {
    RenderTexture2D target = LoadRenderTexture(size, size);
    Camera3D iconCam = { 0 };
    iconCam.position = { 2.4f, 1.8f, 2.4f };
    iconCam.target = { 0.0f, 0.0f, 0.0f };
    iconCam.up = { 0.0f, 1.0f, 0.0f };
    iconCam.fovy = 35.0f;
    iconCam.projection = CAMERA_PERSPECTIVE;
    BeginTextureMode(target);
        ClearBackground(BLANK);
        BeginMode3D(iconCam);
            DrawModel(model, {0, 0, 0}, 1.0f, DARKBROWN);
        EndMode3D();
    EndTextureMode();
    return target;
}

std::vector<PieceDef> LoadPieceDefs() {
    std::vector<PieceDef> defs;
    const float logLength = 3.0f;

    {
        PieceDef d;
        d.type = PieceType::StraightLog;
        d.name = "Straight Log";
        d.model = MakeHorizontalLogModel(logLength);
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 1.55f, 0.3f, 0.3f };
        d.length = logLength;
        d.isStructuralLog = true;
        d.snapPoints = {
            {{-1.5f, 0, 0}, {-1,0,0}},
            {{ 1.5f, 0, 0}, { 1,0,0}}
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::NotchedLog;
        d.name = "Notched Log";
        d.model = MakeHorizontalLogModel(logLength);
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 1.55f, 0.3f, 0.3f };
        d.length = logLength;
        d.isStructuralLog = true;
        d.snapPoints = {
            {{-1.5f,0,0},{-1,0,0}}, {{1.5f,0,0},{1,0,0}},
            {{0,0.0f,0.0f},{0,1,0}}
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::CornerLog;
        d.name = "Corner Log";
        d.model = MakeHorizontalLogModel(logLength);
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 1.55f, 0.3f, 0.3f };
        d.length = logLength;
        d.isStructuralLog = true;
        d.snapPoints = {{{-1.5f,0,0},{-1,0,0}}, {{1.5f,0,0},{1,0,0}}};
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::Roof;
        d.name = "Roof Panel";
        d.model = MakeRoofModel();
        d.icon = RenderIcon(d.model);
        d.halfExtents = {1.0f,0.15f,1.0f};
        d.snapPoints = {{{0,0.075f,0},{0,1,0}}};
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::Window;
        d.name = "Window";
        d.model = MakeWindowModel();
        d.icon = RenderIcon(d.model);
        d.halfExtents = {0.5f,0.5f,0.15f};
        d.snapPoints = {{{0,0,0.1f},{0,0,1}}, {{0,0,-0.1f},{0,0,-1}}};
        defs.push_back(d);
    }
    return defs;
}

void UnloadPieceDefs(std::vector<PieceDef>& defs) {
    for (auto& d : defs) { UnloadModel(d.model); UnloadRenderTexture(d.icon); }
    defs.clear();
}

void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs) {
    const PieceDef& def = defs[(int)piece.type];
    Color tint = piece.selected ? YELLOW : (piece.snapHighlight ? ORANGE : DARKBROWN);
    DrawModelEx(def.model, piece.position, {0,1,0}, piece.rotationY, {1,1,1}, tint);
}

BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def) {
    float c = std::cos(piece.rotationY * DEG2RAD);
    float s = std::sin(piece.rotationY * DEG2RAD);
    float ex = std::fabs(def.halfExtents.x * c) + std::fabs(def.halfExtents.z * s);
    float ez = std::fabs(def.halfExtents.x * s) + std::fabs(def.halfExtents.z * c);
    return {{piece.position.x-ex, piece.position.y-def.halfExtents.y, piece.position.z-ez},
            {piece.position.x+ex, piece.position.y+def.halfExtents.y, piece.position.z+ez}};
}
