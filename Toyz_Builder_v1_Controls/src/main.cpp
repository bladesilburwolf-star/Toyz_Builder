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
static const float SNAP_RADIUS = 0.65f; // S mode - magnetic snap

static Vector3 SnapToGrid(Vector3 p) {
    return { roundf(p.x / GRID_SIZE) * GRID_SIZE, p.y, roundf(p.z / GRID_SIZE) * GRID_SIZE };
}

static bool FindNearestSnap(Vector3 worldPos, std::vector<PlacedPiece>& placed, const std::vector<PieceDef>& defs, Vector3& outPos) {
    for (auto& p : placed) p.snapHighlight = false;
    float best = SNAP_RADIUS;
    bool found = false;
    for (auto& p : placed) {
        const PieceDef* defPtr = nullptr;
        for (auto& d : defs) if (d.type == p.type) {
            if (p.type == PieceType::MagnetixRod && d.defaultColor != p.color) continue;
            defPtr = &d; break;
        }
        if (!defPtr) continue;
        bool nearThis = false;
        for (const auto& sp : defPtr->snapPoints) {
            Vector3 rotated = Vector3RotateByAxisAngle(sp.position, {0,1,0}, p.rotationY * DEG2RAD);
            rotated = Vector3RotateByAxisAngle(rotated, {1,0,0}, p.rotationX * DEG2RAD);
            Vector3 worldSnap = Vector3Add(p.position, rotated);
            float d = Vector3Distance(worldSnap, worldPos);
            if (d < SNAP_RADIUS) nearThis = true;
            if (d < best) { best = d; outPos = worldSnap; found = true; }
        }
        p.snapHighlight = nearThis;
    }
    return found;
}

static int PickPiece(Ray ray, const std::vector<PlacedPiece>& placed, const std::vector<PieceDef>& defs) {
    int bestIndex = -1;
    float bestDist = std::numeric_limits<float>::max();
    for (int i=0;i<(int)placed.size();i++) {
        const PieceDef* defPtr = nullptr;
        for (auto& d : defs) if (d.type == placed[i].type) { defPtr = &d; break; }
        if (!defPtr) continue;
        BoundingBox box = GetPieceWorldBounds(placed[i], *defPtr);
        RayCollision hit = GetRayCollisionBox(ray, box);
        if (hit.hit && hit.distance < bestDist) { bestDist = hit.distance; bestIndex = i; }
    }
    return bestIndex;
}

static void ClearSelection(std::vector<PlacedPiece>& placed) { for (auto& p : placed) p.selected = false; }

// --- Options / control settings (also editable from the Options menu, V) ---
struct GameSettings {
    float mouseSensitivity = 0.12f;
    float gamepadLookSensitivity = 140.0f; // deg/sec at full stick deflection
    bool invertY = false;
    float lookDeadzone = 0.18f;
};

int main() {
    const int screenW = 1280, screenH = 800;
    InitWindow(screenW, screenH, "Toyz Builder Engine v5");
    SetTargetFPS(60);
    rlImGuiSetup(true);

    Camera3D camera = {0};
    camera.up = {0,1,0};
    camera.fovy = 70; // wider FOV suits first-person building
    camera.projection = CAMERA_PERSPECTIVE;

    std::vector<PieceDef> pieceDefs = LoadPieceDefs();
    ForestTerrain forest = GenerateForestTerrain(0xC0FFEEu);
    std::vector<PlacedPiece> placedPieces;
    {
        PlacedPiece a; a.type = PieceType::StraightLog; a.position = {0,0.25f,0}; a.rotationY=0; placedPieces.push_back(a);
        PlacedPiece b; b.type = PieceType::MagnetixBall; b.position = {1.5f,0.25f,0}; b.color = PieceColor::White; placedPieces.push_back(b);
    }

    Player player;
    InitPlayer(player, forest);

    InventoryState inventory;
    inventory.isOpen = false; // start closed so the player spawns in control of the camera

    GameSettings settings;
    bool showOptions = false;

    bool isPlacing = false;
    PieceType placingType = PieceType::StraightLog;
    PieceColor placingColor = PieceColor::Red;
    PieceLength placingLength = PieceLength::MED;
    float placingRotation = 0, placingRotX = 0, placingRotZ = 0;
    int selectedIndex = -1;

    // Modes from your request: S = magnetic snap, T = BOTW shrine rotate
    enum class BuildMode { MagneticSnap, ShrineRotate } buildMode = BuildMode::MagneticSnap;
    bool showGizmo = true;
    bool showHelpers = true;

    // --- Minecraft Console Edition-style look state ---
    // camYaw/camPitch drive BOTH the camera direction and (via UpdatePlayer's
    // cameraYaw param) the direction WASD/left-stick walks in, exactly like
    // MCCE: you always move relative to where you're looking.
    float camYaw = 0.0f;
    float camPitch = -10.0f;

    DisableCursor(); // FPS-style mouse look by default; released while a menu is open

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();
        bool hasController = IsGamepadAvailable(0);

        // --- Menu toggles (E = inventory, V = options) ---
        // Only one menu owns the cursor at a time.
        if (IsKeyPressed(KEY_E) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE_LEFT))) {
            if (!showOptions) {
                inventory.isOpen = !inventory.isOpen;
                if (inventory.isOpen) EnableCursor(); else DisableCursor();
            }
        }
        if (IsKeyPressed(KEY_V) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE_RIGHT))) {
            if (!inventory.isOpen) {
                showOptions = !showOptions;
                if (showOptions) EnableCursor(); else DisableCursor();
            }
        }
        bool menuOpen = inventory.isOpen || showOptions;

        if (IsKeyPressed(KEY_S)) buildMode = BuildMode::MagneticSnap;
        if (IsKeyPressed(KEY_T)) buildMode = BuildMode::ShrineRotate;
        if (IsKeyPressed(KEY_G)) showGizmo = !showGizmo;
        if (IsKeyPressed(KEY_H)) showHelpers = !showHelpers;

        // --- Look (mouse + right stick), only while no menu owns the cursor ---
        if (!menuOpen) {
            Vector2 mouseDelta = GetMouseDelta();
            camYaw += mouseDelta.x * settings.mouseSensitivity;
            camPitch += mouseDelta.y * settings.mouseSensitivity * (settings.invertY ? 1.0f : -1.0f);

            if (hasController) {
                float rx = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_X);
                float ry = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_Y);
                if (fabsf(rx) < settings.lookDeadzone) rx = 0;
                if (fabsf(ry) < settings.lookDeadzone) ry = 0;
                camYaw += rx * settings.gamepadLookSensitivity * dt;
                camPitch += ry * settings.gamepadLookSensitivity * dt * (settings.invertY ? -1.0f : 1.0f);
            }
            camPitch = Clamp(camPitch, -85.0f, 85.0f);
        }

        // --- Movement: WASD (keyboard) or left stick (gamepad), MCCE-style ---
        // relative to camYaw. Both input methods work at once - whichever the
        // player touches last just naturally wins each frame.
        if (!menuOpen) {
            UpdatePlayer(player, forest, dt, hasController, 0, camYaw, true);
        }

        // First-person camera follows the player's eye position and look direction.
        {
            float yawRad = camYaw * DEG2RAD;
            float pitchRad = camPitch * DEG2RAD;
            Vector3 lookDir = {
                cosf(pitchRad) * sinf(yawRad),
                sinf(pitchRad),
                cosf(pitchRad) * cosf(yawRad)
            };
            camera.position = { player.position.x, player.position.y + player.eyeHeight, player.position.z };
            camera.target = Vector3Add(camera.position, lookDir);
        }

        // --- Building: raycast from the screen-center crosshair (not the OS
        // cursor, since the cursor is hidden/locked during normal play) ---
        Vector3 ghostPos = {0}; bool haveGhostPos = false;
        Ray crosshairRay = GetScreenToWorldRay({ screenW / 2.0f, screenH / 2.0f }, camera);

        if (isPlacing && !menuOpen) {
            if (fabsf(crosshairRay.direction.y) > 0.0001f) {
                float t = -(crosshairRay.position.y - 0.0f) / crosshairRay.direction.y;
                // Also allow placing against the nearest existing piece along the
                // look ray, not just the floor, so you can build outward/upward.
                Vector3 floorHit = Vector3Add(crosshairRay.position, Vector3Scale(crosshairRay.direction, (t > 0 ? t : 6.0f)));
                Vector3 hit = floorHit;
                if (t <= 0) {
                    // Looking above the horizon: project a fixed reach distance instead.
                    hit = Vector3Add(crosshairRay.position, Vector3Scale(crosshairRay.direction, 6.0f));
                }

                Vector3 snappedSpot;
                if (buildMode == BuildMode::MagneticSnap && FindNearestSnap(hit, placedPieces, pieceDefs, snappedSpot)) {
                    ghostPos = snappedSpot;
                } else {
                    ghostPos = SnapToGrid(hit);
                    for (auto& p : placedPieces) p.snapHighlight = false;
                }
                if (t > 0) ghostPos.y = 0.25f; // resting on the ground plane
                haveGhostPos = true;
            }

            if (hasController) {
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_LEFT)) { placingRotation -=90; if (placingRotation<0) placingRotation+=360; }
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) { placingRotation +=90; if (placingRotation>=360) placingRotation-=360; }
            }
            if (IsKeyPressed(KEY_R)) { placingRotation += 90; if (placingRotation >= 360) placingRotation -= 360; }
            if (IsKeyPressed(KEY_F)) { placingRotation -= 90; if (placingRotation < 0) placingRotation += 360; }

            bool placePressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
            if (haveGhostPos && placePressed) {
                PlacedPiece pp; pp.type = placingType; pp.position = ghostPos; pp.rotationY = placingRotation; pp.rotationX = placingRotX; pp.color = placingColor; pp.length = placingLength;
                placedPieces.push_back(pp);
            }
            if ((hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT)) || IsKeyPressed(KEY_ESCAPE)) { isPlacing=false; for(auto& p:placedPieces) p.snapHighlight=false; }
        } else if (!menuOpen) {
            for (auto& p : placedPieces) p.snapHighlight = false;
            bool selectPressed = (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_UP)) || (!hasController && IsMouseButtonPressed(MOUSE_BUTTON_LEFT));
            if (selectPressed) {
                int hit = PickPiece(crosshairRay, placedPieces, pieceDefs);
                ClearSelection(placedPieces);
                if (hit>=0) { placedPieces[hit].selected=true; selectedIndex=hit; } else selectedIndex=-1;
            }
        }

        bool deletePressed = (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE)) || IsKeyPressed(KEY_DELETE) || IsKeyPressed(KEY_BACKSPACE);
        if (!isPlacing && !menuOpen && selectedIndex>=0 && deletePressed) { placedPieces.erase(placedPieces.begin()+selectedIndex); selectedIndex=-1; }

        BeginDrawing();
        ClearBackground(Color{40,44,52,255});
        BeginMode3D(camera);
        DrawForestTerrain(forest);
        for (const auto& p : placedPieces) DrawPlacedPiece(p, pieceDefs);
        if (isPlacing && haveGhostPos) {
            const PieceDef* defPtr = nullptr;
            for (auto& d : pieceDefs) if (d.type==placingType) { if (placingType==PieceType::MagnetixRod && d.defaultColor!=placingColor) continue; defPtr=&d; break; }
            if (defPtr) DrawModelEx(defPtr->model, ghostPos, {0,1,0}, placingRotation, {1,1,1}, Fade(SKYBLUE,0.55f));
            if (showGizmo) {
                DrawCircle3D(ghostPos, 0.6f, {1,0,0}, 90, RED);
                DrawCircle3D(ghostPos, 0.6f, {0,1,0}, 0, GREEN);
                DrawCircle3D(ghostPos, 0.6f, {0,0,1}, 0, BLUE);
            }
        }
        EndMode3D();

        // Crosshair (screen-space) so building/selecting has a clear aim point
        // now that the mouse cursor itself is hidden/locked.
        if (!menuOpen) {
            int cx = screenW/2, cy = screenH/2;
            DrawLine(cx-8, cy, cx+8, cy, RAYWHITE);
            DrawLine(cx, cy-8, cx, cy+8, RAYWHITE);
        }

        rlImGuiBegin();

        bool invSel = UpdateInventory(inventory, pieceDefs);
        if (invSel) {
            placingType = pieceDefs[inventory.selectedIndex].type;
            placingColor = pieceDefs[inventory.selectedIndex].defaultColor;
            placingRotation=0; isPlacing=true; ClearSelection(placedPieces); selectedIndex=-1;
        }

        if (showOptions) {
            ImGui::SetNextWindowSize(ImVec2(380, 260), ImGuiCond_FirstUseEver);
            ImGui::Begin("Options [V]", &showOptions);
            ImGui::Text("Controls");
            ImGui::BulletText("WASD / Left Stick: Move");
            ImGui::BulletText("Mouse / Right Stick: Look");
            ImGui::BulletText("Space / A: Jump");
            ImGui::BulletText("E / Menu button: Inventory");
            ImGui::BulletText("V / Menu button: This Options menu");
            ImGui::BulletText("R / F: Rotate ghost piece +/- 90 deg");
            ImGui::BulletText("Left Click / RT: Place  |  Del / RT: Delete selected");
            ImGui::Separator();
            ImGui::SliderFloat("Mouse Sensitivity", &settings.mouseSensitivity, 0.02f, 0.4f);
            ImGui::SliderFloat("Gamepad Look Speed", &settings.gamepadLookSensitivity, 40.0f, 300.0f);
            ImGui::Checkbox("Invert Y Look", &settings.invertY);
            ImGui::Separator();
            ImGui::Text("Gamepad: %s", hasController ? "Connected" : "Not connected");
            if (!showOptions) DisableCursor(); // closed via the window's own X button
            ImGui::End();
        }

        ImGui::SetNextWindowPos({10,10}, ImGuiCond_Always);
        ImGui::Begin("Toyz Builder Engine v5", nullptr, ImGuiWindowFlags_AlwaysAutoResize);
        ImGui::Text("Mode: %s [S/T]", buildMode==BuildMode::MagneticSnap?"MAGNETIC SNAP":"SHRINE ROTATE");
        if (ImGui::Button("Toggle Gizmo [G]")) showGizmo=!showGizmo;
        ImGui::SameLine(); if (ImGui::Button("Helpers [H]")) showHelpers=!showHelpers;
        ImGui::Separator();
        ImGui::Text("Pieces: %d | Selected: %d", (int)placedPieces.size(), selectedIndex);
        ImGui::Text("Controller: %s", hasController?"Xbox Connected":"None - Keyboard/Mouse");
        if (isPlacing) ImGui::TextColored(ImVec4(0.3f,0.7f,1,1),"PLACING - Click/RT to place, Esc/B to cancel");
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
