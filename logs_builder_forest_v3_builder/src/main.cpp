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

static const float GRID_SIZE = 1.0f;
static const float SNAP_RADIUS = 0.65f;

static Vector3 SnapToGrid(Vector3 p) {
    return {
        roundf(p.x / GRID_SIZE) * GRID_SIZE,
        p.y,
        roundf(p.z / GRID_SIZE) * GRID_SIZE
    };
}

static bool FindNearestSnap(Vector3 worldPos,
                            std::vector<PlacedPiece>& placed,
                            const std::vector<PieceDef>& defs,
                            Vector3& outPos) {
    for (auto& p : placed) {
        p.snapHighlight = false;
    }
    float best = SNAP_RADIUS;
    bool found = false;
    for (auto& p : placed) {
        const PieceDef& def = defs[(int)p.type];
        bool nearThis = false;
        for (const auto& sp : def.snapPoints) {
            Vector3 rotated = Vector3RotateByAxisAngle(sp.position, { 0, 1, 0 },
                p.rotationY * DEG2RAD);
            Vector3 worldSnap = Vector3Add(p.position, rotated);
            float d = Vector3Distance(worldSnap, worldPos);
            if (d < SNAP_RADIUS) {
                nearThis = true;
            }
            if (d < best) {
                best = d;
                outPos = worldSnap;
                found = true;
            }
        }
        p.snapHighlight = nearThis;
    }
    return found;
}

static int PickPiece(Ray ray,
                     const std::vector<PlacedPiece>& placed,
                     const std::vector<PieceDef>& defs) {
    int bestIndex = -1;
    float bestDist = std::numeric_limits<float>::max();
    for (int i = 0; i < (int)placed.size(); i++) {
        const PieceDef& def = defs[(int)placed[i].type];
        BoundingBox box = GetPieceWorldBounds(placed[i], def);
        RayCollision hit = GetRayCollisionBox(ray, box);
        if (hit.hit && hit.distance < bestDist) {
            bestDist = hit.distance;
            bestIndex = i;
        }
    }
    return bestIndex;
}

static void ClearSelection(std::vector<PlacedPiece>& placed) {
    for (auto& p : placed) {
        p.selected = false;
    }
}


// Intersect a camera/build ray with the procedural terrain. We march in small
// steps because the terrain is generated from the same deterministic height
// function used by gameplay. This avoids maintaining a second collision mesh.
static bool RaycastTerrain(const Ray& ray, const ForestTerrain& forest,
                           float maxDistance, Vector3& hitPoint) {
    const float step = 0.35f;
    Vector3 previous = ray.position;
    float previousSurface = GetTerrainHeight(forest, previous.x, previous.z) + 0.08f;
    bool previousInside = previous.y <= previousSurface;

    for (float t = step; t <= maxDistance; t += step) {
        Vector3 p = Vector3Add(ray.position, Vector3Scale(ray.direction, t));
        if (p.x < -forest.size * 0.5f || p.x > forest.size * 0.5f ||
            p.z < -forest.size * 0.5f || p.z > forest.size * 0.5f) {
            previous = p;
            previousSurface = GetTerrainHeight(forest, p.x, p.z) + 0.08f;
            previousInside = p.y <= previousSurface;
            continue;
        }

        float surface = GetTerrainHeight(forest, p.x, p.z) + 0.08f;
        bool inside = p.y <= surface;
        if (inside && !previousInside) {
            // Binary refinement gives a stable placement point without requiring
            // a high-resolution collision mesh.
            float lo = t - step;
            float hi = t;
            for (int i = 0; i < 8; ++i) {
                float mid = (lo + hi) * 0.5f;
                Vector3 m = Vector3Add(ray.position, Vector3Scale(ray.direction, mid));
                float ms = GetTerrainHeight(forest, m.x, m.z) + 0.08f;
                if (m.y <= ms) hi = mid; else lo = mid;
            }
            hitPoint = Vector3Add(ray.position, Vector3Scale(ray.direction, hi));
            hitPoint.y = GetTerrainHeight(forest, hitPoint.x, hitPoint.z);
            return true;
        }
        previous = p;
        previousSurface = surface;
        previousInside = inside;
    }
    return false;
}

static Vector3 ResolveCameraPosition(const Vector3& target, Vector3 desired,
                                     const ForestTerrain& forest) {
    // Keep the follow camera outside trunks. A simple segment test against the
    // procedural cylinders is sufficient and prevents the camera from hiding
    // inside a tree when orbiting around the mascot.
    Vector3 segment = Vector3Subtract(desired, target);
    float length = Vector3Length(segment);
    if (length > 0.001f) {
        Vector3 dir = Vector3Scale(segment, 1.0f / length);
        float allowed = length;
        for (const ForestTree& tree : forest.trees) {
            Vector3 center = { tree.position.x, desired.y, tree.position.z };
            Vector3 toTree = Vector3Subtract(center, target);
            float along = Vector3DotProduct(toTree, dir);
            if (along <= 0.0f || along >= length) continue;
            Vector3 closest = Vector3Add(target, Vector3Scale(dir, along));
            float radius = 0.45f + tree.scale * 0.18f;
            float dx = closest.x - tree.position.x;
            float dz = closest.z - tree.position.z;
            if (dx * dx + dz * dz < radius * radius) {
                allowed = std::min(allowed, std::max(1.0f, along - radius));
            }
        }
        desired = Vector3Add(target, Vector3Scale(dir, allowed));
    }

    // Also keep the camera above the terrain surface.
    float terrainY = GetTerrainHeight(forest, desired.x, desired.z) + 0.65f;
    if (desired.y < terrainY) desired.y = terrainY;
    return desired;
}

int main() {
    const int screenW = 1280;
    const int screenH = 800;
    InitWindow(screenW, screenH, "Lincoln Logs 3D");
    SetTargetFPS(60);
    rlImGuiSetup(true);

    Camera3D camera = { 0 };
    camera.position = { 8.0f, 7.0f, 8.0f };
    camera.target   = { 0.0f, 0.5f, 0.0f };
    camera.up       = { 0.0f, 1.0f, 0.0f };
    camera.fovy     = 45.0f;
    camera.projection = CAMERA_PERSPECTIVE;

    std::vector<PieceDef> pieceDefs = LoadPieceDefs();
    ForestTerrain forest = GenerateForestTerrain(0xC0FFEEu);
    Player player;
    InitPlayer(player, forest);
    float cameraYaw = 45.0f;
    float cameraPitch = 28.0f;
    const float cameraDistance = 11.0f;
    std::vector<PlacedPiece> placedPieces;

    // Starter logs
    {
        PlacedPiece a; a.type = PieceType::StraightLog; a.position = { 0.0f, 0.25f, 0.0f }; a.rotationY = 0.0f; placedPieces.push_back(a);
        PlacedPiece b; b.type = PieceType::StraightLog; b.position = { 1.5f, 0.25f, 0.0f }; b.rotationY = 0.0f; placedPieces.push_back(b);
        PlacedPiece c; c.type = PieceType::NotchedLog;  c.position = { 0.0f, 0.25f, 1.5f }; c.rotationY = 90.0f; placedPieces.push_back(c);
    }

    InventoryState inventory;
    bool isPlacing = false;
    PieceType placingType = PieceType::StraightLog;
    float placingRotation = 0.0f;
    int selectedIndex = -1;

    // Controller deadzone
    const float deadzone = 0.15f;

    while (!WindowShouldClose()) {
        // --- Controller Input --------------------------------------------
        // raylib gamepad IDs are zero-based; the first controller is ID 0.
        constexpr int gamepadId = 0;
        bool hasController = IsGamepadAvailable(gamepadId);
        
        // Camera orbit and player movement. The player is the builder mascot and world anchor.
        if (hasController && !inventory.isOpen) {
            Vector2 rightStick = {
                GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_RIGHT_X),
                GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_RIGHT_Y)
            };
            if (fabsf(rightStick.x) > deadzone) cameraYaw += rightStick.x * 120.0f * GetFrameTime();
            if (fabsf(rightStick.y) > deadzone) cameraPitch = Clamp(cameraPitch + rightStick.y * 70.0f * GetFrameTime(), 12.0f, 55.0f);
        } else if (!inventory.isOpen) {
            if (IsKeyDown(KEY_LEFT)) cameraYaw -= 90.0f * GetFrameTime();
            if (IsKeyDown(KEY_RIGHT)) cameraYaw += 90.0f * GetFrameTime();
            if (IsKeyDown(KEY_UP)) cameraPitch = Clamp(cameraPitch - 60.0f * GetFrameTime(), 12.0f, 55.0f);
            if (IsKeyDown(KEY_DOWN)) cameraPitch = Clamp(cameraPitch + 60.0f * GetFrameTime(), 12.0f, 55.0f);
        }

        UpdatePlayer(player, forest, GetFrameTime(), hasController, gamepadId, cameraYaw);

        float pitchRad = cameraPitch * DEG2RAD;
        float yawRad = cameraYaw * DEG2RAD;
        Vector3 cameraOffset = {
            std::sin(yawRad) * std::cos(pitchRad) * cameraDistance,
            std::sin(pitchRad) * cameraDistance,
            std::cos(yawRad) * std::cos(pitchRad) * cameraDistance
        };
        camera.target = Vector3Add(player.position, {0.0f, 0.4f, 0.0f});
        camera.position = ResolveCameraPosition(camera.target, Vector3Add(camera.target, cameraOffset), forest);

        // --- Ghost placement --------------------------------------------
        Vector3 ghostPos = { 0 };
        bool haveGhostPos = false;

        if (isPlacing && !inventory.isOpen) {
            Ray ray = GetMouseRay(hasController ? Vector2{screenW * 0.5f, screenH * 0.5f} : GetMousePosition(), camera);
            Vector3 terrainHit{};
            if (RaycastTerrain(ray, forest, 80.0f, terrainHit)) {
                Vector3 snappedSpot;
                if (FindNearestSnap(terrainHit, placedPieces, pieceDefs, snappedSpot)) {
                    ghostPos = snappedSpot;
                } else {
                    ghostPos = SnapToGrid(terrainHit);
                    ghostPos.y = GetTerrainHeight(forest, ghostPos.x, ghostPos.z);
                    for (auto& p : placedPieces) p.snapHighlight = false;
                }

                // Construction pieces sit on the terrain instead of assuming a
                // flat world. Higher-level pieces retain their snapped height.
                if (placingType == PieceType::Roof) ghostPos.y = std::max(ghostPos.y, 1.5f);
                else if (placingType == PieceType::Window) ghostPos.y = std::max(ghostPos.y, 1.0f);
                else if (ghostPos.y < GetTerrainHeight(forest, ghostPos.x, ghostPos.z) + 0.25f)
                    ghostPos.y = GetTerrainHeight(forest, ghostPos.x, ghostPos.z) + 0.25f;

                haveGhostPos = true;
            }

            // Rotate with controller (D-Pad Left/Right or RB/LB)
            if (hasController) {
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_LEFT) ||
                    IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_TRIGGER_1)) {
                    placingRotation -= 90.0f;
                    if (placingRotation < 0) placingRotation += 360.0f;
                }
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_RIGHT) ||
                    IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_TRIGGER_1)) {
                    placingRotation += 90.0f;
                    if (placingRotation >= 360.0f) placingRotation -= 360.0f;
                }
            } else if (IsKeyPressed(KEY_R)) {
                placingRotation += 90.0f;
                if (placingRotation >= 360.0f) placingRotation -= 360.0f;
            }

            // Place with controller (A button) or mouse
            bool placePressed = (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN)) ||
                               (!hasController && IsMouseButtonPressed(MOUSE_BUTTON_LEFT));
            
            if (haveGhostPos && placePressed) {
                if (!ImGui::GetIO().WantCaptureMouse) {
                    PlacedPiece pp;
                    pp.type = placingType;
                    pp.position = ghostPos;
                    pp.rotationY = placingRotation;
                    placedPieces.push_back(pp);
                }
            }

            // Cancel with controller (B button) or Escape
            bool cancelPressed = (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT)) ||
                                IsKeyPressed(KEY_ESCAPE);
            
            if (cancelPressed) {
                isPlacing = false;
                for (auto& p : placedPieces) p.snapHighlight = false;
            }
        } else {
            // Clear snap highlights when not placing
            for (auto& p : placedPieces) p.snapHighlight = false;

            // --- Piece selection -----------------------------------------
            bool selectPressed = (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_UP)) ||
                                (!hasController && IsMouseButtonPressed(MOUSE_BUTTON_LEFT));

            if (!inventory.isOpen && selectPressed) {
                if (!hasController || !ImGui::GetIO().WantCaptureMouse) {
                    Ray ray = GetMouseRay(hasController ? Vector2{screenW * 0.5f, screenH * 0.5f} : GetMousePosition(), camera);
                    int hit = PickPiece(ray, placedPieces, pieceDefs);
                    ClearSelection(placedPieces);
                    if (hit >= 0) {
                        placedPieces[hit].selected = true;
                        selectedIndex = hit;
                    } else {
                        selectedIndex = -1;
                    }
                }
            }

            // --- Controller movement for selected piece ------------------
            if (selectedIndex >= 0 && selectedIndex < (int)placedPieces.size() && hasController) {
                PlacedPiece& selected = placedPieces[selectedIndex];

                // Move with left stick
                Vector2 leftStick = {
                    GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_X),
                    GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_Y)
                };
                if (fabsf(leftStick.x) > deadzone || fabsf(leftStick.y) > deadzone) {
                    selected.position.x += leftStick.x * 0.05f;
                    selected.position.z += leftStick.y * 0.05f;
                }

                // Rotate with D-Pad
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_LEFT)) {
                    selected.rotationY -= 90.0f;
                    if (selected.rotationY < 0) selected.rotationY += 360.0f;
                }
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) {
                    selected.rotationY += 90.0f;
                    if (selected.rotationY >= 360.0f) selected.rotationY -= 360.0f;
                }

                // Move up/down with triggers
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_LEFT_TRIGGER_2)) {
                    selected.position.y += 0.5f;
                }
                if (IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_TRIGGER_2)) {
                    selected.position.y -= 0.5f;
                }
            }
        }

        // Delete selected piece
        bool deletePressed = (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_MIDDLE_RIGHT)) ||
                            IsKeyPressed(KEY_DELETE) || IsKeyPressed(KEY_BACKSPACE);
        
        if (!isPlacing && selectedIndex >= 0 && deletePressed) {
            placedPieces.erase(placedPieces.begin() + selectedIndex);
            selectedIndex = -1;
        }

        // --- Draw --------------------------------------------------------
        BeginDrawing();
        ClearBackground(Color{ 40, 44, 52, 255 });
        BeginMode3D(camera);

        DrawForestTerrain(forest);
        DrawPlayer(player);

        for (const auto& p : placedPieces) {
            DrawPlacedPiece(p, pieceDefs);
        }

        if (isPlacing && haveGhostPos) {
            const PieceDef& def = pieceDefs[(int)placingType];
            DrawModelEx(def.model, ghostPos, { 0, 1, 0 }, placingRotation,
                        { 1, 1, 1 }, Fade(SKYBLUE, 0.55f));

            // The mascot visibly carries the selected construction piece. This
            // becomes the visual bridge between inventory selection and placement.
            float playerYaw = player.yaw * DEG2RAD;
            Vector3 handForward = { std::sin(playerYaw), 0.0f, std::cos(playerYaw) };
            Vector3 heldPos = Vector3Add(player.position, Vector3Scale(handForward, 0.9f));
            heldPos.y += 0.15f;
            DrawModelEx(def.model, heldPos, { 0, 1, 0 }, placingRotation,
                        { 0.65f, 0.65f, 0.65f }, WHITE);
        }

        EndMode3D();

        // --- ImGui -------------------------------------------------------
        rlImGuiBegin();

        bool inventoryJustSelected = UpdateInventory(inventory, pieceDefs);
        if (inventoryJustSelected) {
            placingType = pieceDefs[inventory.selectedIndex].type;
            placingRotation = 0.0f;
            isPlacing = true;
            ClearSelection(placedPieces);
            selectedIndex = -1;
        }

        ImGui::SetNextWindowPos({ 10, 10 }, ImGuiCond_Always);
        ImGui::Begin("Help", nullptr,
                     ImGuiWindowFlags_NoResize | ImGuiWindowFlags_AlwaysAutoResize |
                     ImGuiWindowFlags_NoMove);

        ImGui::Text("Controller: Xbox One");
        ImGui::Separator();
        ImGui::Text("Left Stick: Move builder mascot");
        ImGui::Text("Right Stick: Orbit camera");
        ImGui::Text("Procedural forest: %.0fm x %.0fm | Trees: %d", forest.size, forest.size, (int)forest.trees.size());
        ImGui::Text("E / Start: Inventory");
        ImGui::Text("R / D-Pad: Rotate while placing");
        ImGui::Text("A: Jump / Place piece");
        ImGui::Text("Builder carries selected piece while placing");
        ImGui::Text("B: Cancel placing");
        ImGui::Text("Y: Select piece");
        ImGui::Text("Left Stick: Move selected");
        ImGui::Text("LT/RT: Move selected up/down");
        ImGui::Text("Menu: Delete selected");
        ImGui::Separator();
        ImGui::Text("Pieces: %d", (int)placedPieces.size());
        if (selectedIndex >= 0) {
            ImGui::Text("Selected: #%d", selectedIndex);
        }
        if (isPlacing) {
            ImGui::TextColored({ 0.3f, 0.7f, 1.0f, 1.0f }, "PLACING mode");
        }
        if (hasController) {
            ImGui::TextColored({ 0.2f, 1.0f, 0.2f, 1.0f }, "Controller Connected");
        }
        ImGui::End();

        rlImGuiEnd();
        EndDrawing();
    }

    UnloadForestTerrain(forest);
    UnloadPieceDefs(pieceDefs);
    rlImGuiShutdown();
    CloseWindow();
    return 0;
}
