#include "raylib.h"
#include "raymath.h"
#include "rlImGui.h"
#include "imgui.h"
#include "piece.h"
#include "inventory.h"
#include "terrain.h"
#include "player.h"
#include "map.h"
#include <vector>
#include <limits>
#include <cmath>
#include <array>
#include <string>

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
    bool thirdPerson = true; // Start in third-person mode
    float thirdPersonDistance = 5.0f;
    float thirdPersonHeight = 1.5f;
    int resolutionIndex = 0; // 0 = 1280x800, 1 = 1280x720, 2 = 1920x1080, 3 = 1600x900
    bool fullscreen = false;
    int skyboxIndex = 0; // Current skybox selection
    float brightness = 1.0f; // Light brightness
    float ambient = 0.4f; // Ambient light level
    bool fogEnabled = false; // Fog post-processing
    float fogDensity = 0.01f; // Fog density
};

int main() {
    const int screenW = 1280, screenH = 800;
    InitWindow(screenW, screenH, "Toyz Builder Engine v5");
    
    // Apply initial resolution settings
    if (settings.fullscreen) {
        ToggleFullscreen();
    }
    
    SetTargetFPS(60);
    rlImGuiSetup(true);

    Camera3D camera = {0};
    camera.up = {0,1,0};
    camera.fovy = 70; // wider FOV suits first-person building
    camera.projection = CAMERA_PERSPECTIVE;

    std::vector<PieceDef> pieceDefs = LoadPieceDefs();
    ForestTerrain forest = GenerateForestTerrain(0xC0FFEEu);
    
    // Initialize map system and create default maps
    MapSystem& mapSystem = GetMapSystem();
    mapSystem.CreateDefaultMaps();
    
    // Load the current map's pieces
    std::vector<PlacedPiece> placedPieces;
    Map* currentMap = mapSystem.GetCurrentMap();
    if (currentMap) {
        placedPieces = currentMap->pieces;
        // Update skybox from map
        for (int i = 0; i < (int)skyboxNames.size(); i++) {
            if (skyboxNames[i] == currentMap->skybox) {
                settings.skyboxIndex = i;
                break;
            }
        }
    } else {
        // Fallback to default pieces
        PlacedPiece a; a.type = PieceType::StraightLog; a.position = {0,0.25f,0}; a.rotationY=0; placedPieces.push_back(a);
        PlacedPiece b; b.type = PieceType::MagnetixBall; b.position = {1.5f,0.25f,0}; b.color = PieceColor::White; placedPieces.push_back(b);
    }
    
    // Load skybox texture
    if (settings.skyboxIndex >= 0 && settings.skyboxIndex < (int)skyboxNames.size()) {
        std::string skyboxPath = "assets/skyboxes/" + skyboxNames[settings.skyboxIndex] + ".jpg";
        skyboxTexture = LoadTexture(skyboxPath.c_str());
    }

    Player player;
    InitPlayer(player, forest);

    InventoryState inventory;
    inventory.isOpen = false; // start closed so the player spawns in control of the camera
    Hotbar hotbar; // what's "placing" is now just whatever's in the selected hotbar slot

    GameSettings settings;
    bool showOptions = false;
    
    // Resolution presets
    std::array<std::pair<int, int>, 4> resolutions = {{
        {1280, 800},
        {1280, 720},
        {1920, 1080},
        {1600, 900}
    }};
    
    // Skybox textures
    std::vector<std::string> skyboxNames = {
        "day1", "day2", "day3", "day4", "day5",
        "night1", "night2", "night3", "night4",
        "overcast1", "overcast2", "overcast3",
        "stomry1"
    };
    Texture2D skyboxTexture = {0};

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

        // --- Hotbar slot selection: number keys + scroll wheel, disabled
        // while a menu has input focus so scrolling the inventory doesn't
        // also spin the hotbar underneath it. ---
        if (!menuOpen) {
            for (int i = 0; i < Hotbar::SLOT_COUNT; i++) {
                if (IsKeyPressed(KEY_ONE + i)) hotbar.selected = i;
            }
            float wheel = GetMouseWheelMove();
            if (wheel != 0.0f) {
                hotbar.selected = ((hotbar.selected - (int)wheel) % Hotbar::SLOT_COUNT + Hotbar::SLOT_COUNT) % Hotbar::SLOT_COUNT;
            }
            if (hasController) {
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_1)) hotbar.selected = (hotbar.selected + 1) % Hotbar::SLOT_COUNT;
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_TRIGGER_1)) hotbar.selected = (hotbar.selected - 1 + Hotbar::SLOT_COUNT) % Hotbar::SLOT_COUNT;
            }
        }

        // What we're holding/placing is whatever's in the selected hotbar
        // slot - an empty slot means hands empty, nothing to place.
        int heldIdx = hotbar.HeldPieceIndex();
        bool isPlacing = (heldIdx >= 0 && heldIdx < (int)pieceDefs.size());
        PieceType placingType = isPlacing ? pieceDefs[heldIdx].type : PieceType::StraightLog;
        PieceColor placingColor = isPlacing ? pieceDefs[heldIdx].defaultColor : PieceColor::Red;

        if (IsKeyPressed(KEY_S)) buildMode = BuildMode::MagneticSnap;
        if (IsKeyPressed(KEY_T)) buildMode = BuildMode::ShrineRotate;
        if (IsKeyPressed(KEY_G)) showGizmo = !showGizmo;
        if (IsKeyPressed(KEY_H)) showHelpers = !showHelpers;
        if (IsKeyPressed(KEY_C)) settings.thirdPerson = !settings.thirdPerson; // Toggle camera mode
        
        // Ensure cursor stays locked during gameplay (first-person only)
        // In third-person mode, cursor is always visible for better control
        if (!menuOpen) {
            if (settings.thirdPerson) {
                EnableCursor();
            } else {
                if (IsWindowFocused()) {
                    DisableCursor();
                }
            }
        }

        // --- Look (mouse + right stick), only while no menu owns the cursor ---
        if (!menuOpen) {
            Vector2 mouseDelta = GetMouseDelta();
            // Fixed: X axis was correct, but Y axis was inverted. Now properly non-inverted by default.
            camYaw += mouseDelta.x * settings.mouseSensitivity;
            camPitch += mouseDelta.y * settings.mouseSensitivity * (settings.invertY ? -1.0f : 1.0f);

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

        // Camera setup - supports both first-person and third-person modes
        {
            float yawRad = camYaw * DEG2RAD;
            float pitchRad = camPitch * DEG2RAD;
            Vector3 lookDir = {
                cosf(pitchRad) * sinf(yawRad),
                sinf(pitchRad),
                cosf(pitchRad) * cosf(yawRad)
            };
            
            if (settings.thirdPerson) {
                // Third-person camera: positioned behind and above the player
                Vector3 playerEye = { player.position.x, player.position.y + player.eyeHeight, player.position.z };
                Vector3 offset = Vector3Scale(lookDir, -settings.thirdPersonDistance);
                offset.y += settings.thirdPersonHeight;
                camera.position = Vector3Add(playerEye, offset);
                camera.target = Vector3Add(playerEye, Vector3Scale(lookDir, 1.0f));
            } else {
                // First-person camera follows the player's eye position
                camera.position = { player.position.x, player.position.y + player.eyeHeight, player.position.z };
                camera.target = Vector3Add(camera.position, lookDir);
            }
        }
        
        // Set up lighting based on settings
        {
            // Ambient light
            float ambientLevel = settings.ambient * settings.brightness;
            SetAmbientLight(Color{
                (unsigned char)(40 * ambientLevel),
                (unsigned char)(44 * ambientLevel),
                (unsigned char)(52 * ambientLevel),
                255
            });
            
            // Directional light (sun)
            Vector3 lightDir = Vector3Normalize({0.5f, -1.0f, 0.5f});
            SetShadowsEnabled(true);
            
            // Fog post-processing
            if (settings.fogEnabled) {
                SetFogEnabled(true);
                SetFogDensity(settings.fogDensity);
                SetFogColor(Color{100, 120, 140, 255});
            } else {
                SetFogEnabled(false);
            }
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
                bool stacked = false;
                
                if (buildMode == BuildMode::MagneticSnap && FindNearestSnap(hit, placedPieces, pieceDefs, snappedSpot)) {
                    ghostPos = snappedSpot;
                    // For stacking: find the piece we're snapping to
                    int hitIndex = -1;
                    float bestSnapDist = SNAP_RADIUS;
                    for (int i = 0; i < (int)placedPieces.size(); i++) {
                        const PieceDef* defPtr = nullptr;
                        for (auto& d : pieceDefs) if (d.type == placedPieces[i].type) {
                            if (placedPieces[i].type == PieceType::MagnetixRod && d.defaultColor != placedPieces[i].color) continue;
                            defPtr = &d; break;
                        }
                        if (!defPtr) continue;
                        for (const auto& sp : defPtr->snapPoints) {
                            Vector3 rotated = Vector3RotateByAxisAngle(sp.position, {0,1,0}, placedPieces[i].rotationY * DEG2RAD);
                            rotated = Vector3RotateByAxisAngle(rotated, {1,0,0}, placedPieces[i].rotationX * DEG2RAD);
                            Vector3 worldSnap = Vector3Add(placedPieces[i].position, rotated);
                            float d = Vector3Distance(worldSnap, hit);
                            if (d < bestSnapDist) {
                                bestSnapDist = d;
                                hitIndex = i;
                            }
                        }
                    }
                    if (hitIndex >= 0) {
                        // Stack on top of the hit piece
                        const PieceDef* defPtr = nullptr;
                        for (auto& d : pieceDefs) if (d.type == placedPieces[hitIndex].type) {
                            if (placedPieces[hitIndex].type == PieceType::MagnetixRod && d.defaultColor != placedPieces[hitIndex].color) continue;
                            defPtr = &d; break;
                        }
                        if (defPtr) {
                            ghostPos.y = placedPieces[hitIndex].position.y + defPtr->halfExtents.y * 2.0f;
                            stacked = true;
                        }
                    }
                } else {
                    ghostPos = SnapToGrid(hit);
                    // Check for stacking on existing pieces at grid positions
                    for (const auto& p : placedPieces) {
                        Vector3 gridPos = SnapToGrid(p.position);
                        if (Vector3Distance(gridPos, ghostPos) < 0.1f) {
                            const PieceDef* defPtr = nullptr;
                            for (auto& d : pieceDefs) if (d.type == p.type) {
                                if (p.type == PieceType::MagnetixRod && d.defaultColor != p.color) continue;
                                defPtr = &d; break;
                            }
                            if (defPtr) {
                                ghostPos.y = p.position.y + defPtr->halfExtents.y * 2.0f;
                                stacked = true;
                                break;
                            }
                        }
                    }
                    if (!stacked && t > 0) {
                        ghostPos.y = 0.25f; // resting on the ground plane
                    }
                    for (auto& p : placedPieces) p.snapHighlight = false;
                }
                
                // Apply terrain height for ground placement
                if (!stacked && t > 0) {
                    ghostPos.y = GetTerrainHeight(forest, ghostPos.x, ghostPos.z) + 0.25f;
                }
                
                haveGhostPos = true;
            }

            if (hasController) {
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_LEFT)) { placingRotation -=90; if (placingRotation<0) placingRotation+=360; }
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) { placingRotation +=90; if (placingRotation>=360) placingRotation-=360; }
                // Gamepad triggers for height adjustment while placing
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_2)) { placingRotX += 15; if (placingRotX >= 360) placingRotX -= 360; }
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_TRIGGER_2)) { placingRotX -= 15; if (placingRotX < 0) placingRotX += 360; }
            }
            if (IsKeyPressed(KEY_R)) { placingRotation += 90; if (placingRotation >= 360) placingRotation -= 360; }
            if (IsKeyPressed(KEY_F)) { placingRotation -= 90; if (placingRotation < 0) placingRotation += 360; }
            if (IsKeyPressed(KEY_Q)) { placingRotX += 15; if (placingRotX >= 360) placingRotX -= 360; } // Tilt forward
            if (IsKeyPressed(KEY_E)) { placingRotX -= 15; if (placingRotX < 0) placingRotX += 360; } // Tilt backward

            bool placePressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
            if (haveGhostPos && placePressed) {
                PlacedPiece pp; pp.type = placingType; pp.position = ghostPos; pp.rotationY = placingRotation; pp.rotationX = placingRotX; pp.color = placingColor; pp.length = placingLength;
                placedPieces.push_back(pp);
            }
            if ((hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT)) || IsKeyPressed(KEY_ESCAPE)) {
                // "Cancel placing" now means empty-handed: clear the held hotbar slot.
                hotbar.slots[hotbar.selected] = -1;
                for (auto& p : placedPieces) p.snapHighlight = false;
            }
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
        
        // Draw skybox background
        if (skyboxTexture.id != 0) {
            DrawTexturePro(skyboxTexture,
                         {0, 0, (float)skyboxTexture.width, (float)skyboxTexture.height},
                         {0, 0, (float)GetScreenWidth(), (float)GetScreenHeight()},
                         {0, 0}, 0, WHITE);
        } else {
            ClearBackground(Color{40,44,52,255});
        }
        
        BeginMode3D(camera);
        DrawForestTerrain(forest);
        
        // Draw player in third-person mode
        if (settings.thirdPerson) {
            DrawPlayer(player);
        }
        
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

        // Hotbar is always visible; the full inventory only while open. Both
        // read/write the same Hotbar, so dragging or clicking a piece there
        // equips it directly into the selected slot.
        DrawHotbar(hotbar, pieceDefs);
        if (inventory.isOpen) {
            UpdateFullInventory(inventory, hotbar, pieceDefs);
        }

        if (showOptions) {
            ImGui::SetNextWindowSize(ImVec2(380, 420), ImGuiCond_FirstUseEver);
            ImGui::Begin("Options [V]", &showOptions);
            ImGui::Text("Controls");
            ImGui::BulletText("WASD / Left Stick: Move");
            ImGui::BulletText("Mouse / Right Stick: Look");
            ImGui::BulletText("Space / A: Jump");
            ImGui::BulletText("E / Menu button: Inventory");
            ImGui::BulletText("V / Menu button: This Options menu");
            ImGui::BulletText("R / F: Rotate ghost piece +/- 90 deg");
            ImGui::BulletText("Left Click / RT: Place  |  Del / RT: Delete selected");
            ImGui::BulletText("1-9 / Scroll / LB-RB: Select hotbar slot");
            ImGui::BulletText("Right-click a hotbar slot: Clear it");
            ImGui::Separator();
            ImGui::SliderFloat("Mouse Sensitivity", &settings.mouseSensitivity, 0.02f, 0.4f);
            ImGui::SliderFloat("Gamepad Look Speed", &settings.gamepadLookSensitivity, 40.0f, 300.0f);
            ImGui::Checkbox("Invert Y Look", &settings.invertY);
            ImGui::Separator();
            
            // Camera mode toggle
            if (ImGui::Checkbox("Third-Person Camera", &settings.thirdPerson)) {
                // Camera mode changed, update cursor lock state
                if (!menuOpen) DisableCursor();
            }
            if (settings.thirdPerson) {
                ImGui::SliderFloat("Camera Distance", &settings.thirdPersonDistance, 2.0f, 10.0f);
                ImGui::SliderFloat("Camera Height", &settings.thirdPersonHeight, 0.5f, 3.0f);
            }
            
            ImGui::Separator();
            ImGui::Text("Display Settings");
            
            // Resolution selection
            const char* resolutionNames[] = { "1280x800", "1280x720", "1920x1080", "1600x900" };
            if (ImGui::BeginCombo("Resolution", resolutionNames[settings.resolutionIndex])) {
                for (int i = 0; i < 4; i++) {
                    bool isSelected = (settings.resolutionIndex == i);
                    if (ImGui::Selectable(resolutionNames[i], isSelected)) {
                        settings.resolutionIndex = i;
                    }
                    if (isSelected) ImGui::SetItemDefaultFocus();
                }
                ImGui::EndCombo();
            }
            
            if (ImGui::Button("Apply Resolution")) {
                SetWindowSize(resolutions[settings.resolutionIndex].first, resolutions[settings.resolutionIndex].second);
            }
            
            ImGui::SameLine();
            if (ImGui::Checkbox("Fullscreen", &settings.fullscreen)) {
                ToggleFullscreen();
            }
            
            ImGui::Separator();
            ImGui::Text("Skybox Settings");
            
            // Skybox selection
            if (ImGui::BeginCombo("Skybox", skyboxNames[settings.skyboxIndex].c_str())) {
                for (int i = 0; i < (int)skyboxNames.size(); i++) {
                    bool isSelected = (settings.skyboxIndex == i);
                    if (ImGui::Selectable(skyboxNames[i].c_str(), isSelected)) {
                        settings.skyboxIndex = i;
                        // Reload skybox texture
                        if (skyboxTexture.id != 0) UnloadTexture(skyboxTexture);
                        std::string skyboxPath = "assets/skyboxes/" + skyboxNames[i] + ".jpg";
                        skyboxTexture = LoadTexture(skyboxPath.c_str());
                    }
                    if (isSelected) ImGui::SetItemDefaultFocus();
                }
                ImGui::EndCombo();
            }
            
            ImGui::Separator();
            ImGui::Text("Lighting Settings");
            ImGui::SliderFloat("Brightness", &settings.brightness, 0.1f, 2.0f);
            ImGui::SliderFloat("Ambient Light", &settings.ambient, 0.0f, 1.0f);
            ImGui::Checkbox("Enable Fog", &settings.fogEnabled);
            if (settings.fogEnabled) {
                ImGui::SliderFloat("Fog Density", &settings.fogDensity, 0.001f, 0.1f);
            }
            
            ImGui::Separator();
            
            // Map system controls
            ImGui::Text("Map System");
            Map* currentMap = mapSystem.GetCurrentMap();
            if (ImGui::Button("Save Current Map")) {
                if (currentMap) {
                    mapSystem.SaveMap("maps/" + currentMap->name + ".map");
                }
            }
            ImGui::SameLine();
            if (ImGui::Button("Create New Map")) {
                mapSystem.CreateNewMap("New Map " + std::to_string(mapSystem.GetMapCount() + 1), MapType::OUTDOOR);
            }
            
            // Map selection
            std::vector<std::string> mapNames = mapSystem.GetMapList();
            if (ImGui::BeginCombo("Select Map", currentMap ? currentMap->name.c_str() : "None")) {
                for (int i = 0; i < (int)mapNames.size(); i++) {
                    bool isSelected = (mapSystem.currentMapIndex == i);
                    if (ImGui::Selectable(mapNames[i].c_str(), isSelected)) {
                        mapSystem.SwitchToMap(i);
                        // Reload pieces from the selected map
                        placedPieces = mapSystem.GetCurrentMap()->pieces;
                    }
                    if (isSelected) ImGui::SetItemDefaultFocus();
                }
                ImGui::EndCombo();
            }
            
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
        
        // Map info
        Map* currentMap = mapSystem.GetCurrentMap();
        if (currentMap) {
            ImGui::Text("Map: %s (%s)", currentMap->name.c_str(), 
                       currentMap->type == MapType::OUTDOOR ? "Outdoor" : 
                       currentMap->type == MapType::INDOOR ? "Indoor" : "Cave");
        }
        ImGui::End();

        rlImGuiEnd();
        EndDrawing();
    }

    // Save current map before exiting
    Map* currentMap = mapSystem.GetCurrentMap();
    if (currentMap) {
        currentMap->pieces = placedPieces;
        mapSystem.SaveMap("maps/" + currentMap->name + ".map");
    }
    
    UnloadForestTerrain(forest);
    UnloadPieceDefs(pieceDefs);
    if (skyboxTexture.id != 0) UnloadTexture(skyboxTexture);
    rlImGuiShutdown();
    CloseWindow();
    return 0;
}
