#include "raylib.h"
#include "raymath.h"
#include "rlImGui.h"
#include "imgui.h"
#include "piece.h"
#include "inventory.h"
#include "terrain.h"
#include "player.h"
#include "map.h"
#include "ui.h"
#include "weather.h"
#include <vector>
#include <limits>
#include <cmath>
#include <array>
#include <string>
#include <cstdlib>
#include <ctime>

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
    float mouseSensitivity = 0.35f;  // stronger default — mouse look was too weak
    float gamepadLookSensitivity = 160.0f; // deg/sec at full stick deflection
    float arrowLookSpeed = 90.0f;    // deg/sec when holding arrow keys
    bool invertY = false;
    bool invertX = true;  // left/right was inverted by default — keep this true until user toggles
    float lookDeadzone = 0.18f;
    bool thirdPerson = true; // Start in third-person mode
    float thirdPersonDistance = 5.0f;
    float thirdPersonHeight = 1.8f;
    int resolutionIndex = 0; // 0 = 1280x800, 1 = 1280x720, 2 = 1920x1080, 3 = 1600x900
    bool fullscreen = false;
    int skyboxIndex = 0; // Current skybox selection
    float brightness = 1.0f; // Light brightness
    float ambient = 0.4f; // Ambient light level
    bool fogEnabled = false;
    float fogDensity = 0.01f;
    float treeDrawDistance = 110.0f; // performance: cull trees beyond this
    bool autoDayNight = true; // when true, sky/lighting follow WorldClock instead of skyboxIndex
};

enum class AppState { Title, Playing };

int main() {
    // --- Settings / assets declared early so everything below can use them ---
    GameSettings settings;
    PauseMenuState pauseMenu;
    TitleMenuState titleMenu;
    AppState appState = AppState::Title;
    bool requestExit = false;

    std::array<std::pair<int, int>, 4> resolutions = {{
        {1280, 800},
        {1280, 720},
        {1920, 1080},
        {1600, 900}
    }};
    const char* resolutionNames[] = { "1280x800", "1280x720", "1920x1080", "1600x900" };

    // Sky presets (solid colors — reliable; jpg skyboxes optional later)
    std::vector<std::string> skyboxNames = {
        "day", "dusk", "night", "overcast", "storm"
    };
    Color skyColors[] = {
        {135, 206, 235, 255}, // day
        {255, 140, 90, 255},  // dusk
        {20, 24, 48, 255},    // night
        {150, 155, 160, 255}, // overcast
        {60, 70, 80, 255}     // storm
    };

    const int screenW = 1280, screenH = 800;
    InitWindow(screenW, screenH, "Toyz Builder Engine  —  Beta 1");
    // ESC must NOT close the window — only the pause menu / Alt+F4 exits
    SetExitKey(KEY_NULL);
    EnableCursor(); // title screen needs the mouse

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

    Player player;
    InitPlayer(player, forest);

    WorldClock worldClock;
    WeatherState weather;
    InitWeather(weather);
    // The existing sky-preset dropdown is repurposed as a manual override:
    // picking a preset jumps the clock/weather to match it and pauses the
    // auto cycle; picking "day" resumes the full day/night + weather sim.
    int lastSkyboxIndex = settings.skyboxIndex;

    InventoryState inventory;
    inventory.isOpen = false; // start closed so the player spawns in control of the camera
    Hotbar hotbar; // what's "placing" is now just whatever's in the selected hotbar slot

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

        // F11 or Alt+Enter = fullscreen toggle (always available)
        if (IsKeyPressed(KEY_F11) || (IsKeyDown(KEY_LEFT_ALT) && IsKeyPressed(KEY_ENTER))) {
            settings.fullscreen = !settings.fullscreen;
            ToggleFullscreen();
        }

        // ========== TITLE SCREEN ==========
        if (appState == AppState::Title) {
            EnableCursor();
            BeginDrawing();
            TitleAction tact = DrawTitleScreen(titleMenu, hasController,
                mapSystem.GetCurrentMap() ? mapSystem.GetCurrentMap()->name.c_str() : "");

            if (titleMenu.showSettings) {
                int sw = GetScreenWidth(), sh = GetScreenHeight();
                Rectangle opt = { (float)(sw / 2 - 180), 80.0f, 360.0f, (float)(sh - 120) };
                bool applyRes = false, toggleFs = false;
                DrawOptionsPanel(opt,
                    &settings.mouseSensitivity, &settings.gamepadLookSensitivity, &settings.arrowLookSpeed,
                    &settings.invertX, &settings.invertY, &settings.thirdPerson,
                    &settings.thirdPersonDistance, &settings.thirdPersonHeight,
                    &settings.skyboxIndex, skyboxNames,
                    &settings.resolutionIndex, resolutionNames, 4,
                    &settings.treeDrawDistance,
                    &applyRes, &toggleFs);
                if (applyRes) {
                    SetWindowSize(resolutions[settings.resolutionIndex].first,
                                  resolutions[settings.resolutionIndex].second);
                }
                if (toggleFs) {
                    settings.fullscreen = !settings.fullscreen;
                    ToggleFullscreen();
                }
                if (settings.skyboxIndex != lastSkyboxIndex) {
                    lastSkyboxIndex = settings.skyboxIndex;
                    settings.autoDayNight = (settings.skyboxIndex == 0);
                    weather.autoCycle = settings.autoDayNight;
                    switch (settings.skyboxIndex) {
                        case 0: worldClock.timeOfDay = 12.0f; weather.type = WeatherType::Clear; break; // day - resume auto
                        case 1: worldClock.timeOfDay = 18.0f; weather.type = WeatherType::Clear; break; // dusk
                        case 2: worldClock.timeOfDay = 22.0f; weather.type = WeatherType::Clear; break; // night
                        case 3: weather.type = WeatherType::Overcast; break; // overcast
                        case 4: weather.type = WeatherType::Storm; break; // storm
                    }
                }
                if (IsKeyPressed(KEY_ESCAPE)) titleMenu.showSettings = false;
                // Back button area: click outside or ESC
                if (DrawSteelButton({ opt.x, opt.y + opt.height - 36, opt.width, 30 }, "BACK", false))
                    titleMenu.showSettings = false;
            } else {
                if (tact == TitleAction::NewWorld) {
                    UnloadForestTerrain(forest);
                    unsigned int seed = (unsigned int)time(nullptr) ^ (unsigned int)(GetTime() * 1000.0);
                    forest = GenerateForestTerrain(seed);
                    placedPieces.clear();
                    selectedIndex = -1;
                    InitPlayer(player, forest);
                    mapSystem.CreateNewMap(TextFormat("World %u", seed & 0xFFFF), MapType::OUTDOOR);
                    Map* m = mapSystem.GetCurrentMap();
                    if (m) m->pieces.clear();
                    appState = AppState::Playing;
                    pauseMenu.open = false;
                    DisableCursor();
                } else if (tact == TitleAction::Continue) {
                    appState = AppState::Playing;
                    DisableCursor();
                } else if (tact == TitleAction::Settings) {
                    titleMenu.showSettings = true;
                } else if (tact == TitleAction::Exit) {
                    requestExit = true;
                }
            }
            EndDrawing();
            if (requestExit) break;
            continue;
        }
        // ========== END TITLE / IN-GAME ==========

        UpdateClock(worldClock, dt);
        UpdateWeather(weather, worldClock, dt, player.position);
        Color ambientTint = GetAmbientTint(worldClock, weather);

        // Hotbar early so ESC / menus know if we are placing
        int heldIdx = hotbar.HeldPieceIndex();
        bool isPlacing = (heldIdx >= 0 && heldIdx < (int)pieceDefs.size());
        PieceType placingType = isPlacing ? pieceDefs[heldIdx].type : PieceType::StraightLog;
        PieceColor placingColor = isPlacing ? pieceDefs[heldIdx].defaultColor : PieceColor::Red;

        // --- Menus: ESC = pause (never quits). E = inventory. ---
        if (IsKeyPressed(KEY_E) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE_LEFT))) {
            if (!pauseMenu.open) {
                inventory.isOpen = !inventory.isOpen;
                if (inventory.isOpen) EnableCursor(); else DisableCursor();
            }
        }
        // ESC: placing → cancel; inventory → close; options panel → back; else pause
        if (IsKeyPressed(KEY_ESCAPE)) {
            if (isPlacing && !pauseMenu.open && !inventory.isOpen) {
                hotbar.slots[hotbar.selected] = -1;
                isPlacing = false;
            } else if (inventory.isOpen) {
                inventory.isOpen = false;
                DisableCursor();
            } else if (pauseMenu.showOptionsPanel) {
                pauseMenu.showOptionsPanel = false;
            } else {
                pauseMenu.open = !pauseMenu.open;
                if (pauseMenu.open) EnableCursor(); else DisableCursor();
            }
        }
        bool menuOpen = inventory.isOpen || pauseMenu.open;

        // Hotbar selection — skip scroll while placing so wheel can rotate the ghost
        if (!menuOpen) {
            for (int i = 0; i < Hotbar::SLOT_COUNT; i++) {
                if (IsKeyPressed(KEY_ONE + i)) hotbar.selected = i;
            }
            if (!isPlacing) {
                float wheel = GetMouseWheelMove();
                if (wheel != 0.0f) {
                    hotbar.selected = ((hotbar.selected - (int)wheel) % Hotbar::SLOT_COUNT + Hotbar::SLOT_COUNT) % Hotbar::SLOT_COUNT;
                }
            }
            if (hasController) {
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_1)) hotbar.selected = (hotbar.selected + 1) % Hotbar::SLOT_COUNT;
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_TRIGGER_1)) hotbar.selected = (hotbar.selected - 1 + Hotbar::SLOT_COUNT) % Hotbar::SLOT_COUNT;
            }
        }
        // Refresh after hotbar changes
        heldIdx = hotbar.HeldPieceIndex();
        isPlacing = (heldIdx >= 0 && heldIdx < (int)pieceDefs.size());
        placingType = isPlacing ? pieceDefs[heldIdx].type : PieceType::StraightLog;
        placingColor = isPlacing ? pieceDefs[heldIdx].defaultColor : PieceColor::Red;

        // Build-mode keys: do NOT use KEY_S (conflicts with walk-back)
        if (IsKeyPressed(KEY_B)) buildMode = BuildMode::MagneticSnap;   // B = magnetic
        if (IsKeyPressed(KEY_T)) buildMode = BuildMode::ShrineRotate;   // T = shrine rotate
        if (IsKeyPressed(KEY_G)) showGizmo = !showGizmo;
        if (IsKeyPressed(KEY_H)) showHelpers = !showHelpers;
        if (IsKeyPressed(KEY_C)) settings.thirdPerson = !settings.thirdPerson; // Toggle camera mode
        
        // Cursor must stay locked during gameplay so GetMouseDelta() returns movement.
        // Only release while a menu is open. Re-assert every frame while focused.
        if (!menuOpen && IsWindowFocused()) {
            DisableCursor();
        }

        // --- Look: mouse + arrow keys + right stick ---
        if (!menuOpen && IsWindowFocused()) {
            float xSign = settings.invertX ? -1.0f : 1.0f;
            float ySign = settings.invertY ? -1.0f : 1.0f;

            // Mouse relative look (cursor locked). Do not gate on ImGui — that was killing mouse look.
            Vector2 mouseDelta = GetMouseDelta();
            camYaw   += mouseDelta.x * settings.mouseSensitivity * xSign;
            camPitch += mouseDelta.y * settings.mouseSensitivity * ySign;

            // Arrow keys — always available as a reliable keyboard look fallback
            float arrow = settings.arrowLookSpeed * dt;
            if (IsKeyDown(KEY_LEFT))  camYaw   -= arrow * xSign;
            if (IsKeyDown(KEY_RIGHT)) camYaw   += arrow * xSign;
            if (IsKeyDown(KEY_UP))    camPitch -= arrow * ySign;
            if (IsKeyDown(KEY_DOWN))  camPitch += arrow * ySign;

            // Gamepad right stick
            if (hasController) {
                float rx = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_X);
                float ry = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_Y);
                if (fabsf(rx) < settings.lookDeadzone) rx = 0;
                if (fabsf(ry) < settings.lookDeadzone) ry = 0;
                camYaw   += rx * settings.gamepadLookSensitivity * dt * xSign;
                camPitch += ry * settings.gamepadLookSensitivity * dt * ySign;
            }

            camPitch = Clamp(camPitch, -80.0f, 60.0f);
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
            // Look direction (where the player is aiming)
            Vector3 lookDir = {
                cosf(pitchRad) * sinf(yawRad),
                sinf(pitchRad),
                cosf(pitchRad) * cosf(yawRad)
            };
            Vector3 playerEye = { player.position.x, player.position.y + player.eyeHeight, player.position.z };

            if (settings.thirdPerson) {
                // Orbit behind the player: distance along the look axis, plus a fixed height lift.
                // Negative pitch (looking down) puts the camera above the player — correct for 3rd person.
                float dist = settings.thirdPersonDistance;
                Vector3 camPos = {
                    playerEye.x - lookDir.x * dist,
                    playerEye.y - lookDir.y * dist + settings.thirdPersonHeight,
                    playerEye.z - lookDir.z * dist
                };
                // Never allow the camera under the terrain mesh
                float groundY = GetTerrainHeight(forest, camPos.x, camPos.z) + 0.6f;
                if (camPos.y < groundY) camPos.y = groundY;

                camera.position = camPos;
                camera.target   = playerEye;
                camera.up       = {0, 1, 0};
            } else {
                // First-person camera follows the player's eye position
                camera.position = playerEye;
                camera.target   = Vector3Add(camera.position, lookDir);
                camera.up       = {0, 1, 0};
            }
        }
        
        // Lighting / fog settings are stored in GameSettings for the Options menu.
        // Raylib does not expose SetAmbientLight / SetFog* / SetShadowsEnabled as
        // free functions — real fog/ambient will be added later via a simple shader.
        // For now we just keep the values so the UI still works.
        (void)settings.ambient;
        (void)settings.brightness;
        (void)settings.fogEnabled;
        (void)settings.fogDensity;

        // --- Building: raycast from the screen-center crosshair (not the OS
        // cursor, since the cursor is hidden/locked during normal play) ---
        Vector3 ghostPos = {0}; bool haveGhostPos = false;
        Ray crosshairRay = GetScreenToWorldRay(
            { GetScreenWidth() / 2.0f, GetScreenHeight() / 2.0f }, camera);

        // Manual height offset while placing (PageUp/Down or pad triggers)
        static float placeHeightOffset = 0.0f;
        bool ghostStacked = false; // used for green-vs-blue ghost tint

        if (isPlacing && !menuOpen) {
            // Aim point: walk the look ray out and project onto terrain Y
            Vector3 aim = Vector3Add(crosshairRay.position, Vector3Scale(crosshairRay.direction, 6.0f));
            if (fabsf(crosshairRay.direction.y) > 0.0001f) {
                // Approximate intersection with a flat plane at player height, then snap Y to terrain
                float planeY = player.position.y;
                float t = (planeY - crosshairRay.position.y) / crosshairRay.direction.y;
                if (t > 0.3f && t < 14.0f) {
                    aim = Vector3Add(crosshairRay.position, Vector3Scale(crosshairRay.direction, t));
                }
            }

            Vector3 snappedSpot;
            bool stacked = false;
            int stackOnIndex = -1;

            if (buildMode == BuildMode::MagneticSnap && FindNearestSnap(aim, placedPieces, pieceDefs, snappedSpot)) {
                ghostPos = snappedSpot;
                // Find which piece we snapped to for stack height / auto-rotate
                float best = SNAP_RADIUS + 0.01f;
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
                        float d = Vector3Distance(worldSnap, aim);
                        if (d < best) { best = d; stackOnIndex = i; }
                    }
                }
                if (stackOnIndex >= 0) {
                    const PieceDef* defPtr = nullptr;
                    for (auto& d : pieceDefs) if (d.type == placedPieces[stackOnIndex].type) {
                        if (placedPieces[stackOnIndex].type == PieceType::MagnetixRod && d.defaultColor != placedPieces[stackOnIndex].color) continue;
                        defPtr = &d; break;
                    }
                    if (defPtr) {
                        ghostPos.y = placedPieces[stackOnIndex].position.y + defPtr->halfExtents.y * 2.0f;
                        stacked = true;
                    }
                }
            } else {
                ghostPos = SnapToGrid(aim);
                // Stack onto the tallest piece near this XZ (easy vertical stacking)
                float bestY = -9999.0f;
                for (int i = 0; i < (int)placedPieces.size(); i++) {
                    float dx = placedPieces[i].position.x - ghostPos.x;
                    float dz = placedPieces[i].position.z - ghostPos.z;
                    if (dx * dx + dz * dz > 0.55f * 0.55f) continue;
                    const PieceDef* defPtr = nullptr;
                    for (auto& d : pieceDefs) if (d.type == placedPieces[i].type) {
                        if (placedPieces[i].type == PieceType::MagnetixRod && d.defaultColor != placedPieces[i].color) continue;
                        defPtr = &d; break;
                    }
                    if (!defPtr) continue;
                    float top = placedPieces[i].position.y + defPtr->halfExtents.y;
                    if (top > bestY) {
                        bestY = top;
                        stackOnIndex = i;
                        ghostPos.x = placedPieces[i].position.x; // keep centered on piece below
                        ghostPos.z = placedPieces[i].position.z;
                        ghostPos.y = top + defPtr->halfExtents.y;
                        stacked = true;
                    }
                }
                if (!stacked) {
                    ghostPos.y = GetTerrainHeight(forest, ghostPos.x, ghostPos.z) + 0.25f;
                }
                for (auto& p : placedPieces) p.snapHighlight = false;
            }

            // Manual raise/lower always available
            ghostPos.y += placeHeightOffset;
            haveGhostPos = true;
            ghostStacked = stacked;

            // --- Easy rotate ---
            // Scroll wheel rotates 90° (Shift = 15° fine step). R/F still work.
            float wheel = GetMouseWheelMove();
            if (wheel != 0.0f) {
                float step = IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT) ? 15.0f : 90.0f;
                placingRotation += (wheel > 0 ? step : -step);
                while (placingRotation >= 360.0f) placingRotation -= 360.0f;
                while (placingRotation < 0.0f) placingRotation += 360.0f;
            }
            // Lincoln-log style: when stacking a log on a log, auto-alternate 90°
            if (stacked && stackOnIndex >= 0) {
                bool belowIsLog = (placedPieces[stackOnIndex].type == PieceType::StraightLog
                    || placedPieces[stackOnIndex].type == PieceType::NotchedLog);
                bool placingLog = (placingType == PieceType::StraightLog || placingType == PieceType::NotchedLog);
                if (belowIsLog && placingLog && !IsKeyDown(KEY_LEFT_SHIFT)) {
                    // Suggest crossed orientation (user can still override with R/F/scroll)
                    float alt = placedPieces[stackOnIndex].rotationY + 90.0f;
                    while (alt >= 360.0f) alt -= 360.0f;
                    // Only auto-set when user hasn't manually rotated this frame via wheel
                    if (wheel == 0.0f && !IsKeyPressed(KEY_R) && !IsKeyPressed(KEY_F)) {
                        placingRotation = alt;
                    }
                }
            }

            if (hasController) {
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_LEFT)) { placingRotation -=90; if (placingRotation<0) placingRotation+=360; }
                if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) { placingRotation +=90; if (placingRotation>=360) placingRotation-=360; }
                if (IsGamepadButtonDown(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_2)) placeHeightOffset += 2.0f * GetFrameTime();
                if (IsGamepadButtonDown(0, GAMEPAD_BUTTON_LEFT_TRIGGER_2)) placeHeightOffset -= 2.0f * GetFrameTime();
            }
            if (IsKeyPressed(KEY_R)) { placingRotation += 90; if (placingRotation >= 360) placingRotation -= 360; }
            if (IsKeyPressed(KEY_F)) { placingRotation -= 90; if (placingRotation < 0) placingRotation += 360; }
            if (IsKeyPressed(KEY_Q)) { placingRotX += 15; if (placingRotX >= 360) placingRotX -= 360; }
            if (IsKeyPressed(KEY_Z)) { placingRotX -= 15; if (placingRotX < 0) placingRotX += 360; }
            if (IsKeyPressed(KEY_PAGE_UP) || IsKeyPressed(KEY_KP_ADD)) placeHeightOffset += 0.25f;
            if (IsKeyPressed(KEY_PAGE_DOWN) || IsKeyPressed(KEY_KP_SUBTRACT)) placeHeightOffset -= 0.25f;
            if (IsKeyPressed(KEY_HOME)) placeHeightOffset = 0.0f;

            bool placePressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT)
                || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
            if (haveGhostPos && placePressed) {
                PlacedPiece pp; pp.type = placingType; pp.position = ghostPos;
                pp.rotationY = placingRotation; pp.rotationX = placingRotX;
                pp.color = placingColor; pp.length = placingLength;
                placedPieces.push_back(pp);
                placeHeightOffset = 0.0f; // reset lift after a successful place
            }
            if ((hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT)) || IsKeyPressed(KEY_ESCAPE)) {
                hotbar.slots[hotbar.selected] = -1;
                placeHeightOffset = 0.0f;
                for (auto& p : placedPieces) p.snapHighlight = false;
            }
        } else if (!menuOpen) {
            for (auto& p : placedPieces) p.snapHighlight = false;
            // Mouse left always selects; X button also selects when pad is present
            bool selectPressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT)
                || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_UP));
            if (selectPressed) {
                int hit = PickPiece(crosshairRay, placedPieces, pieceDefs);
                ClearSelection(placedPieces);
                if (hit>=0) { placedPieces[hit].selected=true; selectedIndex=hit; } else selectedIndex=-1;
            }
        }

        bool deletePressed = (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE)) || IsKeyPressed(KEY_DELETE) || IsKeyPressed(KEY_BACKSPACE);
        if (!isPlacing && !menuOpen && selectedIndex>=0 && deletePressed) { placedPieces.erase(placedPieces.begin()+selectedIndex); selectedIndex=-1; }

        BeginDrawing();

        // Sky: auto day/night + weather when enabled, else the manual preset
        // (kept for a quick forced look, and as a safe fallback).
        Color skyCol;
        if (settings.autoDayNight) {
            skyCol = GetSkyColor(worldClock, weather);
        } else {
            int si = settings.skyboxIndex;
            if (si < 0) si = 0;
            if (si > 4) si = 4;
            skyCol = skyColors[si];
        }
        ClearBackground(skyCol);

        BeginMode3D(camera);
        DrawForestTerrain(forest, camera.position, settings.treeDrawDistance, ambientTint);
        DrawCaveMouths(forest, camera.position, settings.treeDrawDistance, ambientTint);
        DrawWeatherParticles(weather, player.position);
        if (settings.thirdPerson) DrawPlayer(player);
        for (const auto& p : placedPieces) DrawPlacedPiece(p, pieceDefs, ambientTint);
        if (isPlacing && haveGhostPos) {
            const PieceDef* defPtr = nullptr;
            for (auto& d : pieceDefs) if (d.type==placingType) { if (placingType==PieceType::MagnetixRod && d.defaultColor!=placingColor) continue; defPtr=&d; break; }
            Color ghostCol = ghostStacked ? Fade(Color{80, 220, 100, 255}, 0.65f) : Fade(SKYBLUE, 0.55f);
            if (defPtr) DrawModelEx(defPtr->model, ghostPos, {0,1,0}, placingRotation, {1,1,1}, ghostCol);
            if (showGizmo) {
                DrawCircle3D(ghostPos, 0.55f, {1,0,0}, 90, RED);
                DrawCircle3D(ghostPos, 0.55f, {0,1,0}, 0, GREEN);
                DrawCircle3D(ghostPos, 0.55f, {0,0,1}, 0, BLUE);
            }
        }
        EndMode3D();

        DrawWeatherOverlay(weather, GetScreenWidth(), GetScreenHeight());

        if (!menuOpen) {
            int cx = GetScreenWidth() / 2, cy = GetScreenHeight() / 2;
            DrawLine(cx-8, cy, cx+8, cy, RAYWHITE);
            DrawLine(cx, cy-8, cx, cy+8, RAYWHITE);
            if (isPlacing && haveGhostPos) {
                DrawText(TextFormat("Rot %d  [Scroll / R / F]   Height +%.2f",
                    (int)placingRotation, placeHeightOffset),
                    cx - 160, cy + 28, 16, RAYWHITE);
            }
        }

        // Phosphor HUD (not ImGui debug window)
        if (!pauseMenu.open) {
            Map* hudMap = mapSystem.GetCurrentMap();
            DrawGameHud(buildMode == BuildMode::MagneticSnap ? "MAGNETIC" : "SHRINE",
                        hudMap ? hudMap->name.c_str() : "none",
                        (int)placedPieces.size(), hasController, GetFPS());
            const char* clockStr = TextFormat("DAY %d   %s   %s", worldClock.dayCount, GetTimeString(worldClock), GetWeatherName(weather.type));
            int cw = MeasureText(clockStr, 16);
            DrawText(clockStr, GetScreenWidth() - cw - 14, 10, 16, SSW::Phosphor());
        }

        // Inventory still uses rlImGui for the grid for now
        rlImGuiBegin();
        DrawHotbar(hotbar, pieceDefs);
        if (inventory.isOpen && !pauseMenu.open) {
            UpdateFullInventory(inventory, hotbar, pieceDefs);
        }
        rlImGuiEnd();

        // --- Pause menu (steel + phosphor) ---
        if (pauseMenu.open) {
            PauseAction act = DrawPauseMenu(pauseMenu, hasController);
            if (act == PauseAction::Resume) {
                pauseMenu.open = false;
                pauseMenu.showOptionsPanel = false;
                DisableCursor();
            } else if (act == PauseAction::ToggleOptions) {
                pauseMenu.showOptionsPanel = !pauseMenu.showOptionsPanel;
            } else if (act == PauseAction::SaveMap) {
                Map* m = mapSystem.GetCurrentMap();
                if (m) {
                    m->pieces = placedPieces;
                    mapSystem.SaveMap("maps/" + m->name + ".map");
                }
            } else if (act == PauseAction::NewMap) {
                // Classic-Cube style: new seed → new terrain + empty pieces
                UnloadForestTerrain(forest);
                // MSVC: ^ requires integral operands (GetTime returns double)
                unsigned int seed = (unsigned int)time(nullptr) ^ (unsigned int)(GetTime() * 1000.0);
                forest = GenerateForestTerrain(seed);
                placedPieces.clear();
                selectedIndex = -1;
                InitPlayer(player, forest);
                mapSystem.CreateNewMap(TextFormat("World %u", seed & 0xFFFF), MapType::OUTDOOR);
                Map* m = mapSystem.GetCurrentMap();
                if (m) m->pieces.clear();
                pauseMenu.open = false;
                pauseMenu.showOptionsPanel = false;
                DisableCursor();
            } else if (act == PauseAction::ExitToTitle) {
                // Save then return to title
                Map* m = mapSystem.GetCurrentMap();
                if (m) {
                    m->pieces = placedPieces;
                    mapSystem.SaveMap("maps/" + m->name + ".map");
                }
                pauseMenu.open = false;
                pauseMenu.showOptionsPanel = false;
                inventory.isOpen = false;
                appState = AppState::Title;
                titleMenu.showSettings = false;
                EnableCursor();
            } else if (act == PauseAction::ExitGame) {
                requestExit = true;
            }

            if (pauseMenu.showOptionsPanel) {
                int sw = GetScreenWidth(), sh = GetScreenHeight();
                Rectangle opt = { sw * 0.5f + 20.0f, sh * 0.5f - 280.0f, 340.0f, 560.0f };
                bool applyRes = false, toggleFs = false;
                DrawOptionsPanel(opt,
                    &settings.mouseSensitivity, &settings.gamepadLookSensitivity, &settings.arrowLookSpeed,
                    &settings.invertX, &settings.invertY, &settings.thirdPerson,
                    &settings.thirdPersonDistance, &settings.thirdPersonHeight,
                    &settings.skyboxIndex, skyboxNames,
                    &settings.resolutionIndex, resolutionNames, 4,
                    &settings.treeDrawDistance,
                    &applyRes, &toggleFs);
                if (applyRes) {
                    SetWindowSize(resolutions[settings.resolutionIndex].first,
                                  resolutions[settings.resolutionIndex].second);
                }
                if (toggleFs) {
                    settings.fullscreen = !settings.fullscreen;
                    ToggleFullscreen();
                }
                if (settings.skyboxIndex != lastSkyboxIndex) {
                    lastSkyboxIndex = settings.skyboxIndex;
                    settings.autoDayNight = (settings.skyboxIndex == 0);
                    weather.autoCycle = settings.autoDayNight;
                    switch (settings.skyboxIndex) {
                        case 0: worldClock.timeOfDay = 12.0f; weather.type = WeatherType::Clear; break; // day - resume auto
                        case 1: worldClock.timeOfDay = 18.0f; weather.type = WeatherType::Clear; break; // dusk
                        case 2: worldClock.timeOfDay = 22.0f; weather.type = WeatherType::Clear; break; // night
                        case 3: weather.type = WeatherType::Overcast; break; // overcast
                        case 4: weather.type = WeatherType::Storm; break; // storm
                    }
                }
            }
        }

        EndDrawing();

        if (requestExit) break;
    }

    // Save current map on exit
    Map* exitMap = mapSystem.GetCurrentMap();
    if (exitMap) {
        exitMap->pieces = placedPieces;
        mapSystem.SaveMap("maps/" + exitMap->name + ".map");
    }

    UnloadForestTerrain(forest);
    UnloadPieceDefs(pieceDefs);
    rlImGuiShutdown();
    CloseWindow();
    return 0;
}
