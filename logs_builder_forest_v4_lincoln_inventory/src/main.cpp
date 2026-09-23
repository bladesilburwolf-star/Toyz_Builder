#include "raylib.h"
#include "raymath.h"
#include "rlImGui.h"
#include "imgui.h"
#include "piece.h"
#include "inventory.h"
#include "terrain.h"
#include "player.h"
#include <vector>
#include <limits>
#include <cmath>
#include <algorithm>

static const float GRID_SIZE = 1.0f;
static const float SNAP_RADIUS = 1.05f;
static const float LOG_LAYER_HEIGHT = 0.50f;

static Vector3 SnapToGrid(Vector3 p) {
    return {roundf(p.x / GRID_SIZE) * GRID_SIZE, p.y, roundf(p.z / GRID_SIZE) * GRID_SIZE};
}

static Vector3 RotateY(Vector3 p, float degrees) {
    float r = degrees * DEG2RAD;
    return {p.x * cosf(r) - p.z * sinf(r), p.y, p.x * sinf(r) + p.z * cosf(r)};
}

static bool IsStructural(const PlacedPiece& p, const std::vector<PieceDef>& defs) {
    return defs[(int)p.type].isStructuralLog;
}

// Find a construction anchor. Lincoln logs alternate 90 degrees on each layer.
// A structural snap point is the intersection point where the next log crosses.
static bool FindConstructionAnchor(Vector3 worldPos, PieceType type, float& outRotation,
                                   Vector3& outPos, int& outLayer,
                                   std::vector<PlacedPiece>& placed,
                                   const std::vector<PieceDef>& defs,
                                   const ForestTerrain& forest) {
    for (auto& p : placed) p.snapHighlight = false;

    const PieceDef& newDef = defs[(int)type];
    float best = SNAP_RADIUS;
    int bestIndex = -1;
    Vector3 bestAnchor{};
    int bestLayer = 0;

    if (newDef.isStructuralLog) {
        for (int i = 0; i < (int)placed.size(); ++i) {
            if (!IsStructural(placed[i], defs)) continue;
            const PieceDef& supportDef = defs[(int)placed[i].type];
            for (const auto& sp : supportDef.snapPoints) {
                if (fabsf(sp.position.y) > 0.2f) continue;
                Vector3 anchor = Vector3Add(placed[i].position, RotateY(sp.position, placed[i].rotationY));
                float d = Vector3Distance(anchor, worldPos);
                if (d < best) {
                    best = d;
                    bestIndex = i;
                    bestAnchor = anchor;
                    bestLayer = placed[i].layer + 1;
                }
            }
        }

        if (bestIndex >= 0) {
            const PlacedPiece& support = placed[bestIndex];
            outRotation = fmodf(support.rotationY + 90.0f, 360.0f);
            outPos = bestAnchor;
            outPos.y = support.position.y + LOG_LAYER_HEIGHT;
            placed[bestIndex].snapHighlight = true;
            outLayer = bestLayer;
            return true;
        }

        // No existing structure: start a new foundation log on the terrain.
        outPos = SnapToGrid(worldPos);
        outPos.y = GetTerrainHeight(forest, outPos.x, outPos.z) + 0.25f;
        outRotation = 0.0f;
        outLayer = 0;
        return true;
    }

    // Non-log props prefer an existing structural piece, then fall back to terrain.
    for (int i = 0; i < (int)placed.size(); ++i) {
        if (!IsStructural(placed[i], defs)) continue;
        float d = Vector3Distance(placed[i].position, worldPos);
        if (d < best) {
            best = d;
            bestIndex = i;
        }
    }
    if (bestIndex >= 0) {
        const PlacedPiece& support = placed[bestIndex];
        outPos = support.position;
        outPos.y += (type == PieceType::Roof ? 1.6f : 1.0f);
        outRotation = support.rotationY;
        outLayer = support.layer + 1;
        placed[bestIndex].snapHighlight = true;
        return true;
    }

    outPos = SnapToGrid(worldPos);
    outPos.y = GetTerrainHeight(forest, outPos.x, outPos.z) + (type == PieceType::Roof ? 1.5f : 0.5f);
    outRotation = 0.0f;
    outLayer = 0;
    return true;
}

static bool IsOccupied(const Vector3& pos, int layer, const std::vector<PlacedPiece>& placed,
                       const std::vector<PieceDef>& defs) {
    for (const auto& p : placed) {
        if (!defs[(int)p.type].isStructuralLog) continue;
        if (p.layer != layer) continue;
        if (Vector3Distance(pos, p.position) < 0.35f) return true;
    }
    return false;
}

static int PickPiece(Ray ray, const std::vector<PlacedPiece>& placed,
                     const std::vector<PieceDef>& defs) {
    int bestIndex = -1;
    float bestDist = std::numeric_limits<float>::max();
    for (int i = 0; i < (int)placed.size(); ++i) {
        RayCollision hit = GetRayCollisionBox(ray, GetPieceWorldBounds(placed[i], defs[(int)placed[i].type]));
        if (hit.hit && hit.distance < bestDist) { bestDist = hit.distance; bestIndex = i; }
    }
    return bestIndex;
}

static void ClearSelection(std::vector<PlacedPiece>& placed) {
    for (auto& p : placed) p.selected = false;
}

static bool RaycastTerrain(const Ray& ray, const ForestTerrain& forest, float maxDistance, Vector3& hitPoint) {
    const float step = 0.35f;
    bool previousInside = ray.position.y <= GetTerrainHeight(forest, ray.position.x, ray.position.z) + 0.08f;
    for (float t = step; t <= maxDistance; t += step) {
        Vector3 p = Vector3Add(ray.position, Vector3Scale(ray.direction, t));
        float surface = GetTerrainHeight(forest, p.x, p.z) + 0.08f;
        bool inside = p.y <= surface;
        if (inside && !previousInside) {
            float lo = t - step, hi = t;
            for (int i = 0; i < 8; ++i) {
                float mid = (lo + hi) * 0.5f;
                Vector3 m = Vector3Add(ray.position, Vector3Scale(ray.direction, mid));
                if (m.y <= GetTerrainHeight(forest, m.x, m.z) + 0.08f) hi = mid; else lo = mid;
            }
            hitPoint = Vector3Add(ray.position, Vector3Scale(ray.direction, hi));
            hitPoint.y = GetTerrainHeight(forest, hitPoint.x, hitPoint.z);
            return true;
        }
        previousInside = inside;
    }
    return false;
}

static Vector3 ResolveCameraPosition(const Vector3& target, Vector3 desired, const ForestTerrain& forest) {
    Vector3 segment = Vector3Subtract(desired, target);
    float length = Vector3Length(segment);
    if (length > 0.001f) {
        Vector3 dir = Vector3Scale(segment, 1.0f / length);
        float allowed = length;
        for (const ForestTree& tree : forest.trees) {
            Vector3 toTree = Vector3Subtract({tree.position.x, desired.y, tree.position.z}, target);
            float along = Vector3DotProduct(toTree, dir);
            if (along <= 0 || along >= length) continue;
            Vector3 closest = Vector3Add(target, Vector3Scale(dir, along));
            float radius = 0.45f + tree.scale * 0.18f;
            float dx = closest.x - tree.position.x, dz = closest.z - tree.position.z;
            if (dx*dx + dz*dz < radius*radius) allowed = std::min(allowed, std::max(1.0f, along-radius));
        }
        desired = Vector3Add(target, Vector3Scale(dir, allowed));
    }
    desired.y = std::max(desired.y, GetTerrainHeight(forest, desired.x, desired.z) + 0.65f);
    return desired;
}

int main() {
    const int screenW = 1280, screenH = 800;
    InitWindow(screenW, screenH, "Lincoln Logs 3D");
    SetTargetFPS(60);
    rlImGuiSetup(true);

    Camera3D camera = {0};
    camera.up = {0,1,0}; camera.fovy = 45; camera.projection = CAMERA_PERSPECTIVE;

    std::vector<PieceDef> pieceDefs = LoadPieceDefs();
    ForestTerrain forest = GenerateForestTerrain(0xC0FFEEu);
    Player player; InitPlayer(player, forest);
    std::vector<PlacedPiece> placedPieces;

    // Small starter foundation: two crossed logs demonstrate the intended rules.
    float baseY = GetTerrainHeight(forest, 0, 0) + 0.25f;
    PlacedPiece a; a.type=PieceType::StraightLog; a.position={0,baseY,0}; a.rotationY=0; a.layer=0; placedPieces.push_back(a);
    PlacedPiece b; b.type=PieceType::StraightLog; b.position={0,baseY+LOG_LAYER_HEIGHT,0}; b.rotationY=90; b.layer=1; placedPieces.push_back(b);

    InventoryState inventory;
    bool isPlacing = false;
    PieceType placingType = PieceType::StraightLog;
    float placingRotation = 0;
    Vector3 ghostPos{};
    bool haveGhostPos = false;
    bool ghostValid = false;
    int ghostLayer = 0;
    int selectedIndex = -1;
    float cameraYaw = 45, cameraPitch = 28;
    const float cameraDistance = 9.5f;
    const float deadzone = 0.15f;

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();
        constexpr int gamepadId = 0;
        bool hasController = IsGamepadAvailable(gamepadId);

        // Inventory gets first refusal on Y/B/A while it is open.
        bool inventoryJustSelected = UpdateInventory(inventory, pieceDefs, hasController, gamepadId);
        if (inventoryJustSelected) {
            placingType = pieceDefs[inventory.selectedIndex].type;
            placingRotation = 0;
            isPlacing = true;
            selectedIndex = -1;
            ClearSelection(placedPieces);
        }

        if (!inventory.isOpen) {
            if (hasController) {
                Vector2 rs = {GetGamepadAxisMovement(gamepadId,GAMEPAD_AXIS_RIGHT_X), GetGamepadAxisMovement(gamepadId,GAMEPAD_AXIS_RIGHT_Y)};
                if (fabsf(rs.x)>deadzone) cameraYaw += rs.x*120*dt;
                if (fabsf(rs.y)>deadzone) cameraPitch = Clamp(cameraPitch + rs.y*70*dt, 12, 55);
            } else {
                if (IsKeyDown(KEY_LEFT)) cameraYaw -= 90*dt;
                if (IsKeyDown(KEY_RIGHT)) cameraYaw += 90*dt;
                if (IsKeyDown(KEY_UP)) cameraPitch = Clamp(cameraPitch-60*dt,12,55);
                if (IsKeyDown(KEY_DOWN)) cameraPitch = Clamp(cameraPitch+60*dt,12,55);
            }

            // A is jump only outside placement mode; during placement it is the build button.
            UpdatePlayer(player, forest, dt, hasController, gamepadId, cameraYaw, !isPlacing);

            float pitchRad=cameraPitch*DEG2RAD, yawRad=cameraYaw*DEG2RAD;
            camera.target = Vector3Add(player.position,{0,0.45f,0});
            Vector3 offset={sinf(yawRad)*cosf(pitchRad)*cameraDistance,sinf(pitchRad)*cameraDistance,cosf(yawRad)*cosf(pitchRad)*cameraDistance};
            camera.position=ResolveCameraPosition(camera.target,Vector3Add(camera.target,offset),forest);

            if (isPlacing) {
                Ray ray=GetMouseRay(hasController?Vector2{screenW*0.5f,screenH*0.5f}:GetMousePosition(),camera);
                Vector3 terrainHit{};
                haveGhostPos=RaycastTerrain(ray,forest,80,terrainHit);
                ghostValid=false;
                if (haveGhostPos) {
                    int candidateLayer=0; float candidateRotation=placingRotation; Vector3 candidatePos{};
                    FindConstructionAnchor(terrainHit,placingType,candidateRotation,candidatePos,candidateLayer,placedPieces,pieceDefs,forest);
                    if (pieceDefs[(int)placingType].isStructuralLog) placingRotation=candidateRotation;
                    else candidateRotation=placingRotation;
                    ghostPos=candidatePos; ghostLayer=candidateLayer;
                    ghostValid=!IsOccupied(ghostPos,ghostLayer,placedPieces,pieceDefs);
                    if (!pieceDefs[(int)placingType].isStructuralLog) ghostValid=true;
                }

                if (hasController) {
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_TRIGGER_1) || IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_FACE_LEFT)) placingRotation-=90;
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_RIGHT_TRIGGER_1) || IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) placingRotation+=90;
                    while (placingRotation<0) placingRotation+=360;
                    while (placingRotation>=360) placingRotation-=360;
                } else if (IsKeyPressed(KEY_R)) placingRotation+=90;

                bool placePressed=(hasController&&IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_RIGHT_FACE_DOWN))||(!hasController&&IsMouseButtonPressed(MOUSE_BUTTON_LEFT));
                if (placePressed && haveGhostPos && ghostValid && (!ImGui::GetIO().WantCaptureMouse || hasController)) {
                    PlacedPiece pp; pp.type=placingType; pp.position=ghostPos; pp.rotationY=placingRotation; pp.layer=ghostLayer;
                    placedPieces.push_back(pp);
                }

                bool cancelPressed=(hasController&&IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_RIGHT_FACE_RIGHT))||IsKeyPressed(KEY_ESCAPE);
                if (cancelPressed) { isPlacing=false; for(auto& p:placedPieces)p.snapHighlight=false; }
            } else {
                for(auto& p:placedPieces)p.snapHighlight=false;
                bool selectPressed=(hasController&&IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_RIGHT_FACE_LEFT))||(!hasController&&IsMouseButtonPressed(MOUSE_BUTTON_LEFT));
                if (selectPressed && (!hasController||!ImGui::GetIO().WantCaptureMouse)) {
                    Ray ray=GetMouseRay(hasController?Vector2{screenW*0.5f,screenH*0.5f}:GetMousePosition(),camera);
                    int hit=PickPiece(ray,placedPieces,pieceDefs); ClearSelection(placedPieces);
                    selectedIndex=hit; if(hit>=0)placedPieces[hit].selected=true;
                }
                if (selectedIndex>=0 && selectedIndex<(int)placedPieces.size() && hasController) {
                    auto& p=placedPieces[selectedIndex];
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_FACE_LEFT))p.rotationY-=90;
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_FACE_RIGHT))p.rotationY+=90;
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_LEFT_TRIGGER_2))p.position.y+=LOG_LAYER_HEIGHT;
                    if (IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_RIGHT_TRIGGER_2))p.position.y-=LOG_LAYER_HEIGHT;
                }
            }
        }

        bool deletePressed=(hasController&&IsGamepadButtonPressed(gamepadId,GAMEPAD_BUTTON_MIDDLE_RIGHT))||IsKeyPressed(KEY_DELETE)||IsKeyPressed(KEY_BACKSPACE);
        if (!inventory.isOpen&&!isPlacing&&selectedIndex>=0&&deletePressed) { placedPieces.erase(placedPieces.begin()+selectedIndex); selectedIndex=-1; }

        BeginDrawing(); ClearBackground(Color{40,44,52,255}); BeginMode3D(camera);
        DrawForestTerrain(forest); DrawPlayer(player);
        for(const auto& p:placedPieces)DrawPlacedPiece(p,pieceDefs);
        if(isPlacing&&haveGhostPos) {
            const PieceDef& def=pieceDefs[(int)placingType];
            Color ghostColor=ghostValid?Color{80,220,120,150}:Color{230,70,70,150};
            DrawModelEx(def.model,ghostPos,{0,1,0},placingRotation,{1,1,1},ghostColor);
            float py=player.yaw*DEG2RAD; Vector3 f={sinf(py),0,cosf(py)};
            Vector3 held=Vector3Add(player.position,Vector3Scale(f,0.9f)); held.y+=0.15f;
            DrawModelEx(def.model,held,{0,1,0},placingRotation,{0.65f,0.65f,0.65f},WHITE);
        }
        EndMode3D();

        rlImGuiBegin();
        ImGui::SetNextWindowPos({10,10},ImGuiCond_Always);
        ImGui::Begin("Controls",nullptr,ImGuiWindowFlags_NoResize|ImGuiWindowFlags_AlwaysAutoResize|ImGuiWindowFlags_NoMove);
        ImGui::Text("LINCOLN LOGS 3D"); ImGui::Separator();
        ImGui::Text("Left Stick: Move    Right Stick: Camera");
        ImGui::Text("A: Jump / Place    B: Cancel / Close");
        ImGui::Text("X: Select placed piece    Y: Inventory");
        ImGui::Text("LB/RB: Rotate build    LT/RT: Raise/Lower selected");
        ImGui::Text("Menu: Delete selected");
        ImGui::Text("Build rule: logs alternate 90 degrees per layer");
        ImGui::Text("Trees: %d | Pieces: %d",(int)forest.trees.size(),(int)placedPieces.size());
        if(isPlacing)ImGui::Text("BUILDING: %s [%s]",pieceDefs[(int)placingType].name.c_str(),ghostValid?"VALID":"BLOCKED");
        ImGui::End();
        rlImGuiEnd(); EndDrawing();
    }

    UnloadForestTerrain(forest); UnloadPieceDefs(pieceDefs); rlImGuiShutdown(); CloseWindow(); return 0;
}
