#include "piece.h"

// --- Placeholder mesh generation -------------------------------------------
// Swap these Gen* calls for real imported models (.glb/.obj) once you have art.

static Model MakeLogModel(float length) {
    Mesh mesh = GenMeshCylinder(0.25f, length, 12);
    Model model = LoadModelFromMesh(mesh);
    return model;
}

static Model MakeCornerModel() {
    Mesh mesh = GenMeshCylinder(0.25f, 1.0f, 12);
    Model model = LoadModelFromMesh(mesh);
    return model;
}

static Model MakeRoofModel() {
    Mesh mesh = GenMeshCube(1.0f, 0.15f, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    return model;
}

static Model MakeWindowModel() {
    Mesh mesh = GenMeshCube(0.9f, 0.9f, 0.12f);
    Model model = LoadModelFromMesh(mesh);
    return model;
}

// Renders `model` into a small offscreen texture to use as an inventory icon.
static RenderTexture2D RenderIcon(Model model, int size = 128) {
    RenderTexture2D target = LoadRenderTexture(size, size);

    Camera3D iconCam = { 0 };
    iconCam.position = { 2.0f, 2.0f, 2.0f };
    iconCam.target = { 0.0f, 0.0f, 0.0f };
    iconCam.up = { 0.0f, 1.0f, 0.0f };
    iconCam.fovy = 35.0f;
    iconCam.projection = CAMERA_PERSPECTIVE;

    BeginTextureMode(target);
        ClearBackground(BLANK);
        BeginMode3D(iconCam);
            DrawModel(model, { 0, 0, 0 }, 1.0f, DARKBROWN);
        EndMode3D();
    EndTextureMode();

    return target;
}

std::vector<PieceDef> LoadPieceDefs() {
    std::vector<PieceDef> defs;

    {
        PieceDef d;
        d.type = PieceType::StraightLog;
        d.name = "Straight Log";
        d.model = MakeLogModel(1.0f);
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 0.3f, 0.3f, 0.6f };
        d.snapPoints = {
            { {0, 0,  0.5f}, {0, 0,  1} },
            { {0, 0, -0.5f}, {0, 0, -1} },
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::NotchedLog;
        d.name = "Notched Log";
        d.model = MakeLogModel(1.0f);
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 0.55f, 0.3f, 0.6f };
        d.snapPoints = {
            { {0, 0,  0.5f}, {0, 0,  1} },
            { {0, 0, -0.5f}, {0, 0, -1} },
            { {0.5f, 0, 0}, {1, 0, 0} },
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::CornerLog;
        d.name = "Corner Log";
        d.model = MakeCornerModel();
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 0.55f, 0.3f, 0.55f };
        d.snapPoints = {
            { {0.5f, 0, 0}, {1, 0, 0} },
            { {0, 0, 0.5f}, {0, 0, 1} },
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::Roof;
        d.name = "Roof Panel";
        d.model = MakeRoofModel();
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 0.55f, 0.15f, 0.55f };
        d.snapPoints = {
            { {0, 0.075f, 0}, {0, 1, 0} },
        };
        defs.push_back(d);
    }
    {
        PieceDef d;
        d.type = PieceType::Window;
        d.name = "Window";
        d.model = MakeWindowModel();
        d.icon = RenderIcon(d.model);
        d.halfExtents = { 0.5f, 0.5f, 0.15f };
        d.snapPoints = {
            { {0, 0,  0.1f}, {0, 0,  1} },
            { {0, 0, -0.1f}, {0, 0, -1} },
        };
        defs.push_back(d);
    }

    return defs;
}

void UnloadPieceDefs(std::vector<PieceDef>& defs) {
    for (auto& d : defs) {
        UnloadModel(d.model);
        UnloadRenderTexture(d.icon);
    }
    defs.clear();
}

void DrawPlacedPiece(const PlacedPiece& piece, const std::vector<PieceDef>& defs) {
    const PieceDef& def = defs[(int)piece.type];
    Color tint = DARKBROWN;
    if (piece.selected) {
        tint = YELLOW;
    } else if (piece.snapHighlight) {
        tint = RED; // glow when ghost is in valid snap range
    }
    DrawModelEx(def.model, piece.position, { 0, 1, 0 }, piece.rotationY,
                { 1, 1, 1 }, tint);
}

BoundingBox GetPieceWorldBounds(const PlacedPiece& piece, const PieceDef& def) {
    // Approximate: axis-aligned box around position expanded by max half-extent.
    // Good enough for selection; refine later with oriented boxes if needed.
    float r = fmaxf(fmaxf(def.halfExtents.x, def.halfExtents.y), def.halfExtents.z);
    Vector3 min = {
        piece.position.x - r,
        piece.position.y - r,
        piece.position.z - r
    };
    Vector3 max = {
        piece.position.x + r,
        piece.position.y + r,
        piece.position.z + r
    };
    return { min, max };
}
