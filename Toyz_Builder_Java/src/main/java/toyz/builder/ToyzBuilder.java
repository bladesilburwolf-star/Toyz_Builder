package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/** Port of main.cpp — Toyz Builder Engine, Beta 1. */
public final class ToyzBuilder {

    static final float GRID_SIZE = 1.0f;
    static final float SNAP_RADIUS = 0.65f; // magnetic snap mode

    // Options / control settings (editable from the Options menu)
    public static class GameSettings {
        public float mouseSensitivity = 0.35f;
        public float gamepadLookSensitivity = 160f;
        public float arrowLookSpeed = 90f;
        public boolean invertY = false;
        public boolean invertX = true;
        public float lookDeadzone = 0.18f;
        public boolean thirdPerson = true;
        public float thirdPersonDistance = 5f;
        public float thirdPersonHeight = 1.8f;
        public int resolutionIndex = 0;
        public boolean fullscreen = false;
        public int skyboxIndex = 0;
        public float brightness = 1f;
        public float ambient = 0.4f;
        public boolean fogEnabled = false;
        public float fogDensity = 0.01f;
        public float treeDrawDistance = 110f;
    }

    private enum AppState { Title, Playing }
    private enum BuildMode { MagneticSnap, ShrineRotate }

    public static void main(String[] args) {
        GameSettings settings = new GameSettings();
        UI.PauseMenuState pauseMenu = new UI.PauseMenuState();
        UI.TitleMenuState titleMenu = new UI.TitleMenuState();
        AppState appState = AppState.Title;
        boolean requestExit = false;

        int[][] resolutions = { {1280, 800}, {1280, 720}, {1920, 1080}, {1600, 900} };
        String[] resolutionNames = { "1280x800", "1280x720", "1920x1080", "1600x900" };

        List<String> skyboxNames = List.of("day", "dusk", "night", "overcast", "storm");
        Color[] skyColors = {
            Helpers.newColor(135, 206, 235, 255),
            Helpers.newColor(255, 140, 90, 255),
            Helpers.newColor(20, 24, 48, 255),
            Helpers.newColor(150, 155, 160, 255),
            Helpers.newColor(60, 70, 80, 255)
        };

        InitWindow(1280, 800, "Toyz Builder Engine  —  Beta 1 (Java)");
        SetExitKey(KEY_NULL);
        EnableCursor();
        SetTargetFPS(60);

        Camera3D camera = Helpers.newCamera(Helpers.newVector3(0, 0, 0), Helpers.newVector3(0, 0, 0),
                                            Helpers.newVector3(0, 1, 0), 70f, CAMERA_PERSPECTIVE);

        List<Piece.PieceDef> pieceDefs = Piece.loadPieceDefs();
        
        // Create a simple initial forest - will be replaced if NewWorld is clicked
        Terrain.ForestTerrain forest = Terrain.generateForestTerrain(0xC0FFEE);

        MapSystem mapSystem = MapSystem.get();
        mapSystem.createDefaultMaps();

        List<Piece.PlacedPiece> placedPieces = new ArrayList<>();
        MapSystem.Map currentMap = mapSystem.getCurrentMap();
        if (currentMap != null) {
            for (Piece.PlacedPiece p : currentMap.pieces) {
                Piece.PlacedPiece copy = new Piece.PlacedPiece();
                copy.type = p.type;
                copy.position = Helpers.newVector3(p.position.x(), p.position.y(), p.position.z());
                copy.rotationY = p.rotationY; copy.rotationX = p.rotationX; copy.rotationZ = p.rotationZ;
                copy.color = p.color; copy.length = p.length;
                placedPieces.add(copy);
            }
        } else {
            Piece.PlacedPiece a = new Piece.PlacedPiece();
            a.type = Piece.PieceType.StraightLog; a.position = Helpers.newVector3(0, 0.25f, 0); a.rotationY = 0;
            placedPieces.add(a);
            Piece.PlacedPiece b = new Piece.PlacedPiece();
            b.type = Piece.PieceType.MagnetixBall; b.position = Helpers.newVector3(1.5f, 0.25f, 0);
            b.color = Piece.PieceColor.White;
            placedPieces.add(b);
        }
        settings.skyboxIndex = 0;

        Player player = Player.init(forest);

        Hotbar.InventoryState inventory = new Hotbar.InventoryState();
        Hotbar.HotbarState hotbar = new Hotbar.HotbarState();

        Piece.PieceLength placingLength = Piece.PieceLength.MED;
        float placingRotation = 0, placingRotX = 0, placingRotZ = 0;
        int selectedIndex = -1;
        float[] placeHeightOffset = { 0f }; // mutable "static" lift state

        BuildMode buildMode = BuildMode.MagneticSnap;
        boolean showGizmo = true;
        boolean showHelpers = true;

        float camYaw = 0f, camPitch = -10f;

        DisableCursor();

        while (!WindowShouldClose()) {
            float dt = GetFrameTime();
            boolean hasController = IsGamepadAvailable(0);

            if (IsKeyPressed(KEY_F11) || (IsKeyDown(KEY_LEFT_ALT) && IsKeyPressed(KEY_ENTER))) {
                settings.fullscreen = !settings.fullscreen;
                ToggleFullscreen();
            }

            // ========== TITLE SCREEN ==========
            if (appState == AppState.Title) {
                EnableCursor();
                BeginDrawing();
                UI.TitleAction tact = UI.drawTitleScreen(titleMenu, hasController,
                    mapSystem.getCurrentMap() != null ? mapSystem.getCurrentMap().name : "");

                if (titleMenu.showSettings) {
                    int sw = GetScreenWidth(), sh = GetScreenHeight();
                    Rectangle opt = Helpers.newRectangle(sw / 2f - 180, 80, 360, sh - 120);
                    UI.OptionsRequest req = new UI.OptionsRequest();
                    UI.drawOptionsPanel(opt, settings, skyboxNames, resolutionNames, req);
                    if (req.requestApplyRes) {
                        SetWindowSize(resolutions[settings.resolutionIndex][0],
                                      resolutions[settings.resolutionIndex][1]);
                    }
                    if (req.requestToggleFs) {
                        settings.fullscreen = !settings.fullscreen;
                        ToggleFullscreen();
                    }
                    if (IsKeyPressed(KEY_ESCAPE)) titleMenu.showSettings = false;
                    if (UI.drawSteelButton(
                            Helpers.newRectangle(opt.x(), opt.y() + opt.height() - 36, opt.width(), 30),
                            "BACK", false))
                        titleMenu.showSettings = false;
                } else {
                    if (tact == UI.TitleAction.NewWorld) {
                        // Clear existing pieces
                        placedPieces.clear();
                        selectedIndex = -1;
                        
                        // Create new map with fresh seed
                        int seed = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
                        mapSystem.createNewMap(String.format("World %d", seed & 0xFFFF), MapSystem.MapType.OUTDOOR);
                        MapSystem.Map m = mapSystem.getCurrentMap();
                        if (m != null) m.pieces.clear();
                        
                        // Reinitialize player position
                        player.position = Helpers.newVector3(0, 
                            Terrain.getTerrainHeight(forest, 0, 0) + player.radius, 0);
                        player.velocity = Helpers.newVector3(0, 0, 0);
                        player.yaw = 0;
                        player.grounded = true;
                        
                        appState = AppState.Playing;
                        pauseMenu.open = false;
                        DisableCursor();
                    } else if (tact == UI.TitleAction.Continue) {
                        appState = AppState.Playing;
                        DisableCursor();
                    } else if (tact == UI.TitleAction.Settings) {
                        titleMenu.showSettings = true;
                    } else if (tact == UI.TitleAction.Exit) {
                        requestExit = true;
                    }
                }
                EndDrawing();
                if (requestExit) break;
                continue;
            }

            // ========== IN-GAME ==========
            int heldIdx = hotbar.heldPieceIndex();
            boolean isPlacing = (heldIdx >= 0 && heldIdx < pieceDefs.size());
            Piece.PieceType placingType = isPlacing ? pieceDefs.get(heldIdx).type : Piece.PieceType.StraightLog;
            Piece.PieceColor placingColor = isPlacing ? pieceDefs.get(heldIdx).defaultColor : Piece.PieceColor.Red;

            // Menus: ESC = pause (never quits). E = inventory.
            if (IsKeyPressed(KEY_E) || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE_LEFT))) {
                if (!pauseMenu.open) {
                    inventory.isOpen = !inventory.isOpen;
                    if (inventory.isOpen) EnableCursor(); else DisableCursor();
                }
            }
            if (IsKeyPressed(KEY_ESCAPE)) {
                if (isPlacing && !pauseMenu.open && !inventory.isOpen) {
                    hotbar.setSlot(hotbar.selected, -1);
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
            boolean menuOpen = inventory.isOpen || pauseMenu.open;

            // Hotbar selection — keys 1-9, wheel (unless placing), pad triggers
            if (!menuOpen) {
                for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
                    if (IsKeyPressed(KEY_ONE + i)) hotbar.selected = i;
                }
                if (!isPlacing) {
                    float wheel = GetMouseWheelMove();
                    if (wheel != 0f) {
                        hotbar.selected = (((hotbar.selected - (int) wheel) % Hotbar.SLOT_COUNT)
                                           + Hotbar.SLOT_COUNT) % Hotbar.SLOT_COUNT;
                    }
                }
                if (hasController) {
                    if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_1))
                        hotbar.selected = (hotbar.selected + 1) % Hotbar.SLOT_COUNT;
                    if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_TRIGGER_1))
                        hotbar.selected = (hotbar.selected - 1 + Hotbar.SLOT_COUNT) % Hotbar.SLOT_COUNT;
                }
            }
            heldIdx = hotbar.heldPieceIndex();
            isPlacing = (heldIdx >= 0 && heldIdx < pieceDefs.size());
            placingType = isPlacing ? pieceDefs.get(heldIdx).type : Piece.PieceType.StraightLog;
            placingColor = isPlacing ? pieceDefs.get(heldIdx).defaultColor : Piece.PieceColor.Red;

            if (IsKeyPressed(KEY_B)) buildMode = BuildMode.MagneticSnap;
            if (IsKeyPressed(KEY_T)) buildMode = BuildMode.ShrineRotate;
            if (IsKeyPressed(KEY_G)) showGizmo = !showGizmo;
            if (IsKeyPressed(KEY_H)) showHelpers = !showHelpers;
            if (IsKeyPressed(KEY_C)) settings.thirdPerson = !settings.thirdPerson;

            if (!menuOpen && IsWindowFocused()) DisableCursor();

            // --- Look: mouse + arrow keys + right stick ---
            if (!menuOpen && IsWindowFocused()) {
                float xSign = settings.invertX ? -1f : 1f;
                float ySign = settings.invertY ? -1f : 1f;

                Vector2 mouseDelta = GetMouseDelta();
                camYaw   += mouseDelta.x() * settings.mouseSensitivity * xSign;
                camPitch += mouseDelta.y() * settings.mouseSensitivity * ySign;

                float arrow = settings.arrowLookSpeed * dt;
                if (IsKeyDown(KEY_LEFT))  camYaw   -= arrow * xSign;
                if (IsKeyDown(KEY_RIGHT)) camYaw   += arrow * xSign;
                if (IsKeyDown(KEY_UP))    camPitch -= arrow * ySign;
                if (IsKeyDown(KEY_DOWN))  camPitch += arrow * ySign;

                if (hasController) {
                    float rx = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_X);
                    float ry = GetGamepadAxisMovement(0, GAMEPAD_AXIS_RIGHT_Y);
                    if (Math.abs(rx) < settings.lookDeadzone) rx = 0;
                    if (Math.abs(ry) < settings.lookDeadzone) ry = 0;
                    camYaw   += rx * settings.gamepadLookSensitivity * dt * xSign;
                    camPitch += ry * settings.gamepadLookSensitivity * dt * ySign;
                }

                camPitch = Math.max(-80f, Math.min(60f, camPitch));
            }

            // --- Movement (MCCE-style, relative to camYaw) ---
            if (!menuOpen) {
                Player.update(player, forest, dt, hasController, 0, camYaw, true);
            }

            // Camera setup — first-person or third-person
            float yawRad = (float) Math.toRadians(camYaw);
            float pitchRad = (float) Math.toRadians(camPitch);
            Vector3 lookDir = Helpers.newVector3(
                (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
                (float) Math.sin(pitchRad),
                (float) (Math.cos(pitchRad) * Math.cos(yawRad)));
            Vector3 playerEye = Helpers.newVector3(player.position.x(),
                                                   player.position.y() + player.eyeHeight,
                                                   player.position.z());

            if (settings.thirdPerson) {
                float dist = settings.thirdPersonDistance;
                Vector3 camPos = Helpers.newVector3(
                    playerEye.x() - lookDir.x() * dist,
                    playerEye.y() - lookDir.y() * dist + settings.thirdPersonHeight,
                    playerEye.z() - lookDir.z() * dist);
                float groundY = Terrain.getTerrainHeight(forest, camPos.x(), camPos.z()) + 0.6f;
                if (camPos.y() < groundY) camPos.y(groundY);
                camera._position(camPos);
                camera.target(playerEye);
            } else {
                camera._position(playerEye);
                camera.target(Vector3Add(playerEye, lookDir));
            }
            camera.up(Helpers.newVector3(0, 1, 0));

            // --- Building: raycast from the screen-center crosshair ---
            Vector3 ghostPos = Helpers.newVector3(0, 0, 0);
            boolean haveGhostPos = false;
            Ray crosshairRay = GetScreenToWorldRay(
                Helpers.newVector2(GetScreenWidth() / 2f, GetScreenHeight() / 2f), camera);
            boolean ghostStacked = false;

            if (isPlacing && !menuOpen) {
                Vector3 aim = Vector3Add(crosshairRay._position(), Vector3Scale(crosshairRay.direction(), 6f));
                if (Math.abs(crosshairRay.direction().y()) > 0.0001f) {
                    float planeY = player.position.y();
                    float t = (planeY - crosshairRay._position().y()) / crosshairRay.direction().y();
                    if (t > 0.3f && t < 14f) {
                        aim = Vector3Add(crosshairRay._position(), Vector3Scale(crosshairRay.direction(), t));
                    }
                }

                Vector3 snapped = Helpers.newVector3(0, 0, 0);
                boolean stacked = false;
                int stackOnIndex = -1;

                if (buildMode == BuildMode.MagneticSnap
                        && findNearestSnap(aim, placedPieces, pieceDefs, snapped)) {
                    ghostPos.x(snapped.x()).y(snapped.y()).z(snapped.z());
                    float best = SNAP_RADIUS + 0.01f;
                    for (int i = 0; i < placedPieces.size(); i++) {
                        Piece.PlacedPiece p = placedPieces.get(i);
                        Piece.PieceDef def = Piece.findDef(pieceDefs, p.type, p.color);
                        if (def == null) continue;
                        for (Piece.SnapPoint sp : def.snapPoints) {
                            Vector3 rotated = Vector3RotateByAxisAngle(sp.position,
                                Helpers.newVector3(0, 1, 0), (float) Math.toRadians(p.rotationY));
                            rotated = Vector3RotateByAxisAngle(rotated, Helpers.newVector3(1, 0, 0),
                                (float) Math.toRadians(p.rotationX));
                            Vector3 worldSnap = Vector3Add(p.position, rotated);
                            float d = Vector3Distance(worldSnap, aim);
                            if (d < best) { best = d; stackOnIndex = i; }
                        }
                    }
                    if (stackOnIndex >= 0) {
                        Piece.PlacedPiece below = placedPieces.get(stackOnIndex);
                        Piece.PieceDef def = Piece.findDef(pieceDefs, below.type, below.color);
                        if (def != null) {
                            ghostPos.y(below.position.y() + def.halfExtents.y() * 2f);
                            stacked = true;
                        }
                    }
                } else {
                    ghostPos.x(Math.round(aim.x() / GRID_SIZE) * GRID_SIZE)
                             .z(Math.round(aim.z() / GRID_SIZE) * GRID_SIZE);
                    float bestY = -9999f;
                    for (int i = 0; i < placedPieces.size(); i++) {
                        Piece.PlacedPiece p = placedPieces.get(i);
                        float dx = p.position.x() - ghostPos.x();
                        float dz = p.position.z() - ghostPos.z();
                        if (dx * dx + dz * dz > 0.55f * 0.55f) continue;
                        Piece.PieceDef def = Piece.findDef(pieceDefs, p.type, p.color);
                        if (def == null) continue;
                        float top = p.position.y() + def.halfExtents.y();
                        if (top > bestY) {
                            bestY = top;
                            stackOnIndex = i;
                            ghostPos.x(p.position.x())
                                    .y(top + def.halfExtents.y())
                                    .z(p.position.z());
                            stacked = true;
                        }
                    }
                    if (!stacked) {
                        ghostPos.y(Terrain.getTerrainHeight(forest, ghostPos.x(), ghostPos.z()) + 0.25f);
                    }
                    for (Piece.PlacedPiece p : placedPieces) p.snapHighlight = false;
                }

                ghostPos.y(ghostPos.y() + placeHeightOffset[0]);
                haveGhostPos = true;
                ghostStacked = stacked;

                // Easy rotate: wheel 90° (Shift = 15°); R/F still work
                float wheel = GetMouseWheelMove();
                if (wheel != 0f) {
                    float step = (IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT)) ? 15f : 90f;
                    placingRotation += (wheel > 0 ? step : -step);
                    while (placingRotation >= 360f) placingRotation -= 360f;
                    while (placingRotation < 0f) placingRotation += 360f;
                }
                // Lincoln-log style crossed stacking (hold Shift to override)
                if (stacked && stackOnIndex >= 0) {
                    Piece.PlacedPiece below = placedPieces.get(stackOnIndex);
                    boolean belowIsLog = below.type == Piece.PieceType.StraightLog
                                      || below.type == Piece.PieceType.NotchedLog;
                    boolean placingLog = placingType == Piece.PieceType.StraightLog
                                       || placingType == Piece.PieceType.NotchedLog;
                    if (belowIsLog && placingLog && !IsKeyDown(KEY_LEFT_SHIFT)) {
                        float alt = below.rotationY + 90f;
                        while (alt >= 360f) alt -= 360f;
                        if (wheel == 0f && !IsKeyPressed(KEY_R) && !IsKeyPressed(KEY_F)) {
                            placingRotation = alt;
                        }
                    }
                }

                if (hasController) {
                    if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_LEFT))  { placingRotation -= 90; if (placingRotation < 0) placingRotation += 360; }
                    if (IsGamepadButtonPressed(0, GAMEPAD_BUTTON_LEFT_FACE_RIGHT)) { placingRotation += 90; if (placingRotation >= 360) placingRotation -= 360; }
                    if (IsGamepadButtonDown(0, GAMEPAD_BUTTON_RIGHT_TRIGGER_2)) placeHeightOffset[0] += 2f * dt;
                    if (IsGamepadButtonDown(0, GAMEPAD_BUTTON_LEFT_TRIGGER_2))  placeHeightOffset[0] -= 2f * dt;
                }
                if (IsKeyPressed(KEY_R)) { placingRotation += 90; if (placingRotation >= 360) placingRotation -= 360; }
                if (IsKeyPressed(KEY_F)) { placingRotation -= 90; if (placingRotation < 0) placingRotation += 360; }
                if (IsKeyPressed(KEY_Q)) { placingRotX += 15; if (placingRotX >= 360) placingRotX -= 360; }
                if (IsKeyPressed(KEY_Z)) { placingRotX -= 15; if (placingRotX < 0) placingRotX += 360; }
                if (IsKeyPressed(KEY_PAGE_UP) || IsKeyPressed(KEY_KP_ADD)) placeHeightOffset[0] += 0.25f;
                if (IsKeyPressed(KEY_PAGE_DOWN) || IsKeyPressed(KEY_KP_SUBTRACT)) placeHeightOffset[0] -= 0.25f;
                if (IsKeyPressed(KEY_HOME)) placeHeightOffset[0] = 0f;

                boolean placePressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT)
                    || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
                if (haveGhostPos && placePressed) {
                    Piece.PlacedPiece pp = new Piece.PlacedPiece();
                    pp.type = placingType;
                    pp.position = Helpers.newVector3(ghostPos.x(), ghostPos.y(), ghostPos.z());
                    pp.rotationY = placingRotation; pp.rotationX = placingRotX; pp.rotationZ = placingRotZ;
                    pp.color = placingColor; pp.length = placingLength;
                    placedPieces.add(pp);
                    placeHeightOffset[0] = 0f;
                }
                if ((hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_RIGHT))
                        || IsKeyPressed(KEY_ESCAPE)) {
                    hotbar.setSlot(hotbar.selected, -1);
                    placeHeightOffset[0] = 0f;
                    for (Piece.PlacedPiece p : placedPieces) p.snapHighlight = false;
                }
            } else if (!menuOpen) {
                for (Piece.PlacedPiece p : placedPieces) p.snapHighlight = false;
                boolean selectPressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT)
                    || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_UP));
                if (selectPressed) {
                    int hit = pickPiece(crosshairRay, placedPieces, pieceDefs);
                    for (Piece.PlacedPiece p : placedPieces) p.selected = false;
                    if (hit >= 0) { placedPieces.get(hit).selected = true; selectedIndex = hit; } else selectedIndex = -1;
                }
            }

            boolean deletePressed = (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_MIDDLE))
                || IsKeyPressed(KEY_DELETE) || IsKeyPressed(KEY_BACKSPACE);
            if (!isPlacing && !menuOpen && selectedIndex >= 0 && deletePressed) {
                placedPieces.remove(selectedIndex);
                selectedIndex = -1;
            }

            // ================= RENDER =================
            BeginDrawing();
            int si = Math.max(0, Math.min(4, settings.skyboxIndex));
            ClearBackground(skyColors[si]);

            BeginMode3D(camera);
            Terrain.drawForestTerrain(forest, camera._position(), settings.treeDrawDistance);
            if (settings.thirdPerson) Player.draw(player);
            for (Piece.PlacedPiece p : placedPieces) Piece.drawPlacedPiece(p, pieceDefs);
            if (isPlacing && haveGhostPos) {
                Piece.PieceDef def = Piece.findDef(pieceDefs, placingType, placingColor);
                Color ghostCol = ghostStacked
                    ? Fade(Helpers.newColor(80, 220, 100, 255), 0.65f)
                    : Fade(SKYBLUE, 0.55f);
                if (def != null) {
                    DrawModelEx(def.model, ghostPos, Helpers.newVector3(0, 1, 0),
                                placingRotation, Helpers.newVector3(1, 1, 1), ghostCol);
                }
                if (showGizmo) {
                    DrawCircle3D(ghostPos, 0.55f, Helpers.newVector3(1, 0, 0), 90, RED);
                    DrawCircle3D(ghostPos, 0.55f, Helpers.newVector3(0, 1, 0), 0, GREEN);
                    DrawCircle3D(ghostPos, 0.55f, Helpers.newVector3(0, 0, 1), 0, BLUE);
                }
            }
            EndMode3D();

            if (!menuOpen) {
                int cx = GetScreenWidth() / 2, cy = GetScreenHeight() / 2;
                DrawLine(cx - 8, cy, cx + 8, cy, RAYWHITE);
                DrawLine(cx, cy - 8, cx, cy + 8, RAYWHITE);
                if (isPlacing && haveGhostPos) {
                    DrawText(String.format("Rot %d  [Scroll / R / F]   Height +%.2f",
                        (int) placingRotation, placeHeightOffset[0]), cx - 160, cy + 28, 16, RAYWHITE);
                }
            }

            if (!pauseMenu.open) {
                MapSystem.Map hudMap = mapSystem.getCurrentMap();
                UI.drawGameHud(buildMode == BuildMode.MagneticSnap ? "MAGNETIC" : "SHRINE",
                              hudMap != null ? hudMap.name : "none",
                              placedPieces.size(), hasController, GetFPS());
            }

            // Hotbar + inventory (custom raylib UI replacing rlImGui)
            Hotbar.drawHotbar(hotbar, pieceDefs);
            if (inventory.isOpen && !pauseMenu.open) {
                Hotbar.updateFullInventory(inventory, hotbar, pieceDefs);
            }

            // Pause menu
            if (pauseMenu.open) {
                UI.PauseAction act = UI.drawPauseMenu(pauseMenu, hasController);
                if (act == UI.PauseAction.Resume) {
                    pauseMenu.open = false;
                    pauseMenu.showOptionsPanel = false;
                    DisableCursor();
                } else if (act == UI.PauseAction.ToggleOptions) {
                    pauseMenu.showOptionsPanel = !pauseMenu.showOptionsPanel;
                } else if (act == UI.PauseAction.SaveMap) {
                    MapSystem.Map m = mapSystem.getCurrentMap();
                    if (m != null) {
                        m.pieces = new ArrayList<>(placedPieces);
                        mapSystem.saveMap("maps/" + m.name + ".map");
                    }
                } else if (act == UI.PauseAction.NewMap) {
                    placedPieces.clear();
                    selectedIndex = -1;
                    int seed = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);
                    mapSystem.createNewMap(String.format("World %d", seed & 0xFFFF), MapSystem.MapType.OUTDOOR);
                    MapSystem.Map m = mapSystem.getCurrentMap();
                    if (m != null) m.pieces.clear();
                    player.position = Helpers.newVector3(0, 
                        Terrain.getTerrainHeight(forest, 0, 0) + player.radius, 0);
                    player.velocity = Helpers.newVector3(0, 0, 0);
                    player.yaw = 0;
                    player.grounded = true;
                    pauseMenu.open = false;
                    pauseMenu.showOptionsPanel = false;
                    DisableCursor();
                } else if (act == UI.PauseAction.ExitToTitle) {
                    MapSystem.Map m = mapSystem.getCurrentMap();
                    if (m != null) {
                        m.pieces = new ArrayList<>(placedPieces);
                        mapSystem.saveMap("maps/" + m.name + ".map");
                    }
                    pauseMenu.open = false;
                    pauseMenu.showOptionsPanel = false;
                    inventory.isOpen = false;
                    appState = AppState.Title;
                    titleMenu.showSettings = false;
                    EnableCursor();
                } else if (act == UI.PauseAction.ExitGame) {
                    requestExit = true;
                }

                if (pauseMenu.showOptionsPanel) {
                    int sw = GetScreenWidth(), sh = GetScreenHeight();
                    Rectangle opt = Helpers.newRectangle(sw * 0.5f + 20f, sh * 0.5f - 280f, 340f, 560f);
                    UI.OptionsRequest req = new UI.OptionsRequest();
                    UI.drawOptionsPanel(opt, settings, skyboxNames, resolutionNames, req);
                    if (req.requestApplyRes) {
                        SetWindowSize(resolutions[settings.resolutionIndex][0],
                                      resolutions[settings.resolutionIndex][1]);
                    }
                    if (req.requestToggleFs) {
                        settings.fullscreen = !settings.fullscreen;
                        ToggleFullscreen();
                    }
                }
            }

            EndDrawing();
            if (requestExit) break;
        }

        // Save current map on exit
        MapSystem.Map exitMap = mapSystem.getCurrentMap();
        if (exitMap != null) {
            exitMap.pieces = new ArrayList<>(placedPieces);
            mapSystem.saveMap("maps/" + exitMap.name + ".map");
        }

        Terrain.unloadForestTerrain(forest);
        Piece.unloadPieceDefs(pieceDefs);
        CloseWindow();
    }

    // ---- helpers (ported free functions from main.cpp) ----

    private static boolean findNearestSnap(Vector3 worldPos, List<Piece.PlacedPiece> placed,
                                           List<Piece.PieceDef> defs, Vector3 outPos) {
        for (Piece.PlacedPiece p : placed) p.snapHighlight = false;
        float best = SNAP_RADIUS;
        boolean found = false;
        for (Piece.PlacedPiece p : placed) {
            Piece.PieceDef def = Piece.findDef(defs, p.type, p.color);
            if (def == null) continue;
            boolean nearThis = false;
            for (Piece.SnapPoint sp : def.snapPoints) {
                Vector3 rotated = Vector3RotateByAxisAngle(sp.position,
                    Helpers.newVector3(0, 1, 0), (float) Math.toRadians(p.rotationY));
                rotated = Vector3RotateByAxisAngle(rotated, Helpers.newVector3(1, 0, 0),
                    (float) Math.toRadians(p.rotationX));
                Vector3 worldSnap = Vector3Add(p.position, rotated);
                float d = Vector3Distance(worldSnap, worldPos);
                if (d < SNAP_RADIUS) nearThis = true;
                if (d < best) {
                    best = d;
                    outPos.x(worldSnap.x()).y(worldSnap.y()).z(worldSnap.z());
                    found = true;
                }
            }
            p.snapHighlight = nearThis;
        }
        return found;
    }

    private static int pickPiece(Ray ray, List<Piece.PlacedPiece> placed, List<Piece.PieceDef> defs) {
        int bestIndex = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < placed.size(); i++) {
            Piece.PieceDef def = Piece.findDef(defs, placed.get(i).type, placed.get(i).color);
            if (def == null) continue;
            RayCollision hit = GetRayCollisionBox(ray,
                Piece.getPieceWorldBounds(placed.get(i), def));
            if (hit.hit() && hit.distance() < bestDist) {
                bestDist = hit.distance();
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}