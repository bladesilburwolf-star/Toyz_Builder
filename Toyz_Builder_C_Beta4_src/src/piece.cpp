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

// Extrudes a convex 2D profile (in the XY plane) along Z by `depth`, with
// per-face flat normals (front/back caps fan-triangulated from vertex 0, so
// the profile must be convex and wound consistently - fine for triangles,
// regular n-gons, and simple wedge/ramp shapes; NOT valid for concave
// outlines like an L-shaped stair tread).
static Mesh GenMeshPrism(const std::vector<Vector2>& profile, float depth) {
    int n = (int)profile.size();
    int totalVerts = n * 4; // front cap + back cap + side quads (4 verts/edge)
    int totalTris = (n - 2) * 2 + n * 2;

    Mesh mesh = {0};
    mesh.vertexCount = totalVerts;
    mesh.triangleCount = totalTris;
    mesh.vertices = (float*)MemAlloc(totalVerts * 3 * sizeof(float));
    mesh.normals  = (float*)MemAlloc(totalVerts * 3 * sizeof(float));
    mesh.texcoords= (float*)MemAlloc(totalVerts * 2 * sizeof(float));
    mesh.indices  = (unsigned short*)MemAlloc(totalTris * 3 * sizeof(unsigned short));

    float hz = depth * 0.5f;
    int vi = 0, ii = 0;

    int frontStart = vi;
    for (int i = 0; i < n; i++) {
        mesh.vertices[vi*3+0]=profile[i].x; mesh.vertices[vi*3+1]=profile[i].y; mesh.vertices[vi*3+2]=hz;
        mesh.normals[vi*3+0]=0; mesh.normals[vi*3+1]=0; mesh.normals[vi*3+2]=1;
        mesh.texcoords[vi*2+0]=profile[i].x; mesh.texcoords[vi*2+1]=profile[i].y;
        vi++;
    }
    for (int i = 1; i < n - 1; i++) {
        mesh.indices[ii++] = (unsigned short)(frontStart+0);
        mesh.indices[ii++] = (unsigned short)(frontStart+i);
        mesh.indices[ii++] = (unsigned short)(frontStart+i+1);
    }

    int backStart = vi;
    for (int i = 0; i < n; i++) {
        mesh.vertices[vi*3+0]=profile[i].x; mesh.vertices[vi*3+1]=profile[i].y; mesh.vertices[vi*3+2]=-hz;
        mesh.normals[vi*3+0]=0; mesh.normals[vi*3+1]=0; mesh.normals[vi*3+2]=-1;
        mesh.texcoords[vi*2+0]=profile[i].x; mesh.texcoords[vi*2+1]=profile[i].y;
        vi++;
    }
    for (int i = 1; i < n - 1; i++) {
        mesh.indices[ii++] = (unsigned short)(backStart+0);
        mesh.indices[ii++] = (unsigned short)(backStart+i+1);
        mesh.indices[ii++] = (unsigned short)(backStart+i);
    }

    for (int i = 0; i < n; i++) {
        int j = (i + 1) % n;
        Vector2 a = profile[i], b = profile[j];
        Vector2 edge = { b.x - a.x, b.y - a.y };
        Vector2 nrm = { edge.y, -edge.x };
        float len = sqrtf(nrm.x*nrm.x + nrm.y*nrm.y);
        if (len > 0.0001f) { nrm.x /= len; nrm.y /= len; }

        int base = vi;
        float vx[4] = { a.x, b.x, b.x, a.x };
        float vy[4] = { a.y, b.y, b.y, a.y };
        float vz[4] = { hz, hz, -hz, -hz };
        float uvx[4] = { 0.0f, 1.0f, 1.0f, 0.0f };
        float uvy[4] = { 0.0f, 0.0f, 1.0f, 1.0f };
        for (int k = 0; k < 4; k++) {
            mesh.vertices[vi*3+0]=vx[k]; mesh.vertices[vi*3+1]=vy[k]; mesh.vertices[vi*3+2]=vz[k];
            mesh.normals[vi*3+0]=nrm.x; mesh.normals[vi*3+1]=nrm.y; mesh.normals[vi*3+2]=0;
            mesh.texcoords[vi*2+0]=uvx[k]; mesh.texcoords[vi*2+1]=uvy[k];
            vi++;
        }
        mesh.indices[ii++] = (unsigned short)(base+0);
        mesh.indices[ii++] = (unsigned short)(base+1);
        mesh.indices[ii++] = (unsigned short)(base+2);
        mesh.indices[ii++] = (unsigned short)(base+0);
        mesh.indices[ii++] = (unsigned short)(base+2);
        mesh.indices[ii++] = (unsigned short)(base+3);
    }

    UploadMesh(&mesh, false);
    return mesh;
}

static std::vector<Vector2> RegularPolygon(int n, float r) {
    std::vector<Vector2> pts;
    for (int i = 0; i < n; i++) {
        float a = (i * 360.0f / n) * DEG2RAD;
        pts.push_back({ cosf(a) * r, sinf(a) * r });
    }
    return pts;
}

static Model MakePolygonPanel(int sides, float radius, float thickness = 0.08f) {
    Mesh mesh = GenMeshPrism(RegularPolygon(sides, radius), thickness);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texMetal, WHITE);
    return model;
}

// Snap point per vertex of an n-gon panel, pointing outward - generic
// replacement for hand-placed edge snaps, used by the new Pentagon/Hexagon
// Magnetix shapes.
static std::vector<SnapPoint> PolygonSnapPoints(int sides, float radius) {
    std::vector<SnapPoint> sp;
    for (auto& v : RegularPolygon(sides, radius)) {
        Vector3 pos = { v.x, 0, v.y };
        Vector3 n = Vector3Normalize(pos);
        sp.push_back({ pos, n });
    }
    return sp;
}

static Model MakeRoofModel() {
    std::vector<Vector2> profile = { {-0.6f, 0.0f}, {0.6f, 0.0f}, {0.0f, 0.5f} };
    Mesh mesh = GenMeshPrism(profile, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texPlanks, WHITE);
    return model;
}

static Model MakeRampModel() {
    std::vector<Vector2> profile = { {-0.5f, 0.0f}, {0.5f, 0.0f}, {0.5f, 0.5f} };
    Mesh mesh = GenMeshPrism(profile, 1.0f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texPlanks, WHITE);
    return model;
}

static Model MakeWindowModel() {
    Mesh mesh = GenMeshCube(0.9f, 0.9f, 0.06f);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texGlass, WHITE);
    return model;
}

static Model MakeTrunkModel(float length, float radius = 0.38f) {
    Mesh mesh = GenMeshCylinder(radius, length, 14);
    Model model = LoadModelFromMesh(mesh);
    ApplyDiffuse(model, g_texBark, WHITE);
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

bool UsesColorVariants(PieceType t) {
    switch (t) {
        case PieceType::MagnetixRod:
        case PieceType::MagnetixTriangle:
        case PieceType::MagnetixSquare:
        case PieceType::MagnetixPentagon:
        case PieceType::MagnetixHexagon:
        case PieceType::Roof:
            return true;
        default:
            return false;
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
    {
        PieceDef d; d.type = PieceType::CornerLog; d.name = "Corner Log";
        d.model = MakeLogModel(0.6f); d.icon = RenderIcon(d.model, Color{225,200,165,255});
        d.snapPoints = { {{0,0,0.3f},{0,0,1}}, {{0.3f,0,0},{1,0,0}} };
        defs.push_back(d);
    }
    // Roofs: same wedge shape, several shingle colors (Natural = classic brown)
    {
        static const struct { PieceColor c; Color rgb; const char* n; } kRoofColors[] = {
            {PieceColor::Natural, Color{140, 95, 55, 255}, "Roof (Brown)"},
            {PieceColor::Red,     Color{175, 60, 55, 255}, "Roof (Red)"},
            {PieceColor::Green,   Color{70, 120, 70, 255}, "Roof (Green)"},
            {PieceColor::Blue,    Color{70, 95, 140, 255}, "Roof (Blue)"},
            {PieceColor::White,   Color{170, 175, 180, 255}, "Roof (Slate Gray)"},
        };
        for (auto& rc : kRoofColors) {
            PieceDef d; d.type = PieceType::Roof; d.name = rc.n;
            d.model = MakeRoofModel(); d.icon = RenderIcon(d.model, rc.rgb);
            d.defaultColor = rc.c;
            d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = rc.rgb;
            d.halfExtents = {0.6f, 0.5f, 0.5f};
            d.snapPoints = { {{-0.6f,0,0},{-1,0,0}}, {{0.6f,0,0},{1,0,0}}, {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
            defs.push_back(d);
        }
    }
    {
        PieceDef d; d.type = PieceType::Window; d.name = "Window";
        d.model = MakeWindowModel(); d.icon = RenderIcon(d.model, Color{200,225,235,255});
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Fade(WHITE, 0.85f);
        d.halfExtents = {0.45f, 0.45f, 0.06f};
        d.snapPoints = { {{0,0,0.45f},{0,0,1}}, {{0,0,-0.45f},{0,0,-1}} };
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::Stairs; d.name = "Stairs / Ramp";
        d.model = MakeRampModel(); d.icon = RenderIcon(d.model, Color{160,120,80,255});
        d.halfExtents = {0.5f, 0.5f, 0.5f};
        d.snapPoints = { {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}}, {{0.5f,0.5f,0},{1,0,0}} };
        defs.push_back(d);
    }
    // Tree trunks - horizontal (log-style, snap end to end) and vertical
    // (snap top/bottom), each in 3 sizes.
    {
        static const struct { PieceType t; float len; const char* n; } kHorizTrunks[] = {
            {PieceType::TreeTrunkHorizS, 0.8f, "Trunk Horiz S"},
            {PieceType::TreeTrunkHorizM, 1.6f, "Trunk Horiz M"},
            {PieceType::TreeTrunkHorizL, 2.6f, "Trunk Horiz L"},
        };
        for (auto& t : kHorizTrunks) {
            PieceDef d; d.type = t.t; d.name = t.n;
            d.model = MakeTrunkModel(t.len); d.icon = RenderIcon(d.model, Color{230,215,190,255});
            d.length = (t.len < 1.0f) ? PieceLength::SHT : (t.len < 2.0f ? PieceLength::MED : PieceLength::LRG);
            d.orient = PieceOrient::HORIZ;
            d.halfExtents = {0.38f, 0.38f, t.len * 0.5f};
            d.snapPoints = { {{0,0,t.len*0.5f},{0,0,1}}, {{0,0,-t.len*0.5f},{0,0,-1}} };
            defs.push_back(d);
        }
        static const struct { PieceType t; float len; const char* n; } kVertTrunks[] = {
            {PieceType::TreeTrunkVertS, 1.2f, "Trunk Vert S"},
            {PieceType::TreeTrunkVertM, 2.4f, "Trunk Vert M"},
            {PieceType::TreeTrunkVertL, 4.0f, "Trunk Vert L"},
        };
        for (auto& t : kVertTrunks) {
            PieceDef d; d.type = t.t; d.name = t.n;
            d.model = MakeTrunkModel(t.len); d.icon = RenderIcon(d.model, Color{215,200,175,255});
            d.length = (t.len < 1.5f) ? PieceLength::SHT : (t.len < 3.0f ? PieceLength::MED : PieceLength::LRG);
            d.orient = PieceOrient::VERT;
            d.halfExtents = {0.38f, t.len * 0.5f, 0.38f};
            d.snapPoints = { {{0,t.len*0.5f,0},{0,1,0}}, {{0,-t.len*0.5f,0},{0,-1,0}} };
            defs.push_back(d);
        }
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
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod GREEN";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, GREEN);
        d.isMagnetic = true; d.defaultColor = PieceColor::Green;
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 220, 110, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixRod; d.name = "MAG Rod YELLOW";
        d.model = MakeRodModel(2.0f); d.icon = RenderIcon(d.model, YELLOW);
        d.isMagnetic = true; d.defaultColor = PieceColor::Yellow;
        d.snapPoints = { {{-1,0,0},{-1,0,0}}, {{1,0,0},{1,0,0}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{255, 225, 70, 255};
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
        PieceDef d; d.type = PieceType::MagnetixTriangle; d.name = "Xtreme Triangle RED";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, RED);
        d.isMagnetic = true; d.defaultColor = PieceColor::Red;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.3f},{0,0,1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{230, 70, 70, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixTriangle; d.name = "Xtreme Triangle BLUE";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, BLUE);
        d.isMagnetic = true; d.defaultColor = PieceColor::Blue;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.3f},{0,0,1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 120, 255, 255};
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
        PieceDef d; d.type = PieceType::MagnetixSquare; d.name = "Xtreme Square RED";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, RED);
        d.isMagnetic = true; d.defaultColor = PieceColor::Red;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{230, 70, 70, 255};
        defs.push_back(d);
    }
    {
        PieceDef d; d.type = PieceType::MagnetixSquare; d.name = "Xtreme Square BLUE";
        d.model = MakeTrianglePanel(); d.icon = RenderIcon(d.model, BLUE);
        d.isMagnetic = true; d.defaultColor = PieceColor::Blue;
        d.snapPoints = { {{-0.5f,0,0},{-1,0,0}}, {{0.5f,0,0},{1,0,0}}, {{0,0,0.5f},{0,0,1}}, {{0,0,-0.5f},{0,0,-1}} };
        d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 120, 255, 255};
        defs.push_back(d);
    }
    // New polygon shapes - true regular n-gon panels via GenMeshPrism
    {
        static const struct { PieceColor c; Color rgb; const char* n; } kPentColors[] = {
            {PieceColor::Red,   Color{225, 75, 75, 255},   "Xtreme Pentagon RED"},
            {PieceColor::Green, Color{80, 210, 110, 255},  "Xtreme Pentagon GREEN"},
            {PieceColor::Blue,  Color{85, 125, 230, 255},  "Xtreme Pentagon BLUE"},
        };
        for (auto& pc : kPentColors) {
            PieceDef d; d.type = PieceType::MagnetixPentagon; d.name = pc.n;
            d.model = MakePolygonPanel(5, 0.5f); d.icon = RenderIcon(d.model, pc.rgb);
            d.isMagnetic = true; d.defaultColor = pc.c;
            d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = pc.rgb;
            d.halfExtents = {0.5f, 0.08f, 0.5f};
            d.snapPoints = PolygonSnapPoints(5, 0.5f);
            defs.push_back(d);
        }
    }
    {
        static const struct { PieceColor c; Color rgb; const char* n; } kHexColors[] = {
            {PieceColor::Purple, Color{170, 95, 220, 255}, "Xtreme Hexagon PURPLE"},
            {PieceColor::Orange, Color{240, 150, 60, 255}, "Xtreme Hexagon ORANGE"},
            {PieceColor::White,  Color{225, 228, 232, 255},"Xtreme Hexagon WHITE"},
        };
        for (auto& hc : kHexColors) {
            PieceDef d; d.type = PieceType::MagnetixHexagon; d.name = hc.n;
            d.model = MakePolygonPanel(6, 0.5f); d.icon = RenderIcon(d.model, hc.rgb);
            d.isMagnetic = true; d.defaultColor = hc.c;
            d.model.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = hc.rgb;
            d.halfExtents = {0.5f, 0.08f, 0.5f};
            d.snapPoints = PolygonSnapPoints(6, 0.5f);
            defs.push_back(d);
        }
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
            if (UsesColorVariants(piece.type)) {
                if (d.defaultColor == piece.color) { defPtr = &d; break; }
            } else { defPtr = &d; break; }
        }
    }
    if (!defPtr) return;

    // Base tint: logs stay natural, colored pieces keep their color, selection overrides
    Color pieceTint = WHITE;
    if (piece.type == PieceType::StraightLog || piece.type == PieceType::NotchedLog
            || piece.type == PieceType::CornerLog || piece.type == PieceType::Window
            || piece.type == PieceType::Stairs
            || piece.type == PieceType::TreeTrunkHorizS || piece.type == PieceType::TreeTrunkHorizM
            || piece.type == PieceType::TreeTrunkHorizL || piece.type == PieceType::TreeTrunkVertS
            || piece.type == PieceType::TreeTrunkVertM || piece.type == PieceType::TreeTrunkVertL) {
        pieceTint = WHITE; // texture/baked color shows through as-is
    } else if (piece.type == PieceType::MagnetixRod || piece.type == PieceType::MagnetixTriangle
            || piece.type == PieceType::MagnetixSquare || piece.type == PieceType::MagnetixFlag
            || piece.type == PieceType::MagnetixPentagon || piece.type == PieceType::MagnetixHexagon
            || piece.type == PieceType::Roof || piece.type == PieceType::TechLight) {
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
