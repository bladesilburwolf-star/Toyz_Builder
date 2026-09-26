package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/** Port of main.cpp — Toyz Builder Engine, Beta 1. */
public final class ToyzBuilder {

    static final float GRID_SIZE = 0.5f; // fine stud grid (Mega-Block scale, not 16/32)
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
        UI.MapLoaderState mapLoader = new UI.MapLoaderState();
        AppState appState = AppState.Title;
        String saveStatusMsg = "";
        float saveStatusTimer = 0f;
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

        InitWindow(1280, 800, "Toyz Builder Engine  —  Creative / Survival");
        SetExitKey(KEY_NULL);
        EnableCursor();
        SetTargetFPS(60);

        Camera3D camera = Helpers.newCamera(Helpers.newVector3(0, 0, 0), Helpers.newVector3(0, 0, 0),
                                            Helpers.newVector3(0, 1, 0), 70f, CAMERA_PERSPECTIVE);

        List<Piece.PieceDef> pieceDefs = Piece.loadPieceDefs();
        AssetBank.loadAll();
        
        // Create a simple initial forest - will be replaced if NewWorld is clicked
        Terrain.ForestTerrain forest = Terrain.generateForestTerrain(0xC0FFEE, Terrain.WorldType.NORMAL, true);

        MapSystem mapSystem = MapSystem.get();
        MapSystem.ensureMapsDir();
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
        GameMode gameMode = GameMode.CREATIVE;
        SurvivalInv.State survInv = new SurvivalInv.State(); // mirrored when survival

        Piece.PieceLength placingLength = Piece.PieceLength.MED;
        float placingRotation = 0, placingRotX = 0, placingRotZ = 0;
        int selectedIndex = -1;
        float[] placeHeightOffset = { 0f }; // mutable "static" lift state

        BuildMode buildMode = BuildMode.MagneticSnap;
        boolean showGizmo = false; // RGB axis rings off by default (toggle G)
        boolean showHelpers = true;

        float camYaw = 0f, camPitch = -10f;
		float worldTime = 0f;
		
        DisableCursor();

        while (!WindowShouldClose()) {
            float dt = GetFrameTime();
			if (Terrain.warpCooldown > 0f) Terrain.warpCooldown -= dt;
            if (saveStatusTimer > 0f) saveStatusTimer -= dt;
			worldTime += dt;
            boolean hasController = IsGamepadAvailable(0);
			Weather.update(dt, camera._position());
			
			if (IsKeyPressed(KEY_M)) Weather.cycle();
            if (IsKeyPressed(KEY_F11) || (IsKeyDown(KEY_LEFT_ALT) && IsKeyPressed(KEY_ENTER))) {
                settings.fullscreen = !settings.fullscreen;
                ToggleFullscreen();
            }

            // ========== TITLE SCREEN ==========
            if (appState == AppState.Title) {
                EnableCursor();
                BeginDrawing();
                UI.TitleAction tact = titleMenu.showWorldMenu ? UI.TitleAction.None : UI.drawTitleScreen(titleMenu, hasController,
                    mapSystem.getCurrentMap() != null ? mapSystem.getCurrentMap().name : "");

                if (titleMenu.showWorldMenu) {
                    UI.WorldAction wa = UI.drawWorldCreation(titleMenu, hasController);
                    if (wa == UI.WorldAction.Back) {
                        titleMenu.showWorldMenu = false;
                        titleMenu.worldSelected = 0;
                    } else if (wa == UI.WorldAction.Create) {
                        int seed;
                        String seedText = titleMenu.seedText == null ? "RANDOM" : titleMenu.seedText.trim();
                        if (seedText.isEmpty() || seedText.equalsIgnoreCase("RANDOM")) {
                            seed = (int) (System.currentTimeMillis() ^ System.nanoTime());
                        } else {
                            try { seed = Integer.parseInt(seedText); }
                            catch (NumberFormatException ex) { seed = seedText.hashCode(); }
                        }
                        Terrain.WorldType wt = Terrain.WorldType.values()[Math.max(0, Math.min(3, titleMenu.worldTypeIndex))];
                        placedPieces.clear();
                        selectedIndex = -1;
                        Terrain.pendingMapgenPreset = titleMenu.mapgenPresetIndex;
                        Terrain.pendingSkyIslands = titleMenu.skyIslands;
                        Terrain.pendingNether = (wt == Terrain.WorldType.NETHER);
                        forest = Terrain.generateForestTerrain(seed, wt, titleMenu.structures);
                        Terrain.homeSeed = seed;
                        Terrain.warpCooldown = 1.5f;
                        mapSystem.createNewMap(String.format("World %d", seed & 0xFFFF), MapSystem.MapType.OUTDOOR);
                        MapSystem.Map m = mapSystem.getCurrentMap();
                        if (m != null) {
                            m.pieces.clear();
                            MapSystem.StructureSettings sc = new MapSystem.StructureSettings();
                            sc.enabled = titleMenu.structures;
                            sc.globalScale = wt == Terrain.WorldType.AMPLIFIED ? 1.15f : 1.0f;
                            List<Piece.PlacedPiece> generated = MapSystem.generateStructures(forest, sc);
                            m.pieces.addAll(generated);
                            registerDoorPortals(forest, generated, forest.nether);
							
                        }
                        player = Player.init(forest);
                        titleMenu.showWorldMenu = false;
                        titleMenu.seedText = "RANDOM";
                        titleMenu.worldSelected = 0;
                        appState = AppState.Playing;
                        pauseMenu.open = false;
                        DisableCursor();
                    }
                } else if (titleMenu.showSettings) {
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
                        titleMenu.showWorldMenu = true;
                        titleMenu.worldSelected = 0;
                        titleMenu.seedText = "RANDOM";
                    } else if (tact == UI.TitleAction.Continue) {
                        // Shift+Continue = Physics_Debug_Room; plain Continue = current map pieces
                        if (IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT)) {
                            for (int mi = 0; mi < mapSystem.maps.size(); mi++) {
                                if ("Physics_Debug_Room".equals(mapSystem.maps.get(mi).name)) {
                                    mapSystem.currentMapIndex = mi;
                                    break;
                                }
                            }
                        }
                        MapSystem.Map cm = mapSystem.getCurrentMap();
                        if (cm != null) {
                            placedPieces = new ArrayList<>(cm.pieces);
                            for (Piece.PlacedPiece pp : placedPieces) {
                                if (MapSystem.isDynamicType(pp.type)) {
                                    pp.dynamic = true;
                                    pp.grounded = false;
                                }
                            }
                        }
                        Survival.disable();
                        gameMode = GameMode.CREATIVE;
                        appState = AppState.Playing;
                        DisableCursor();
                    } else if (tact == UI.TitleAction.LoadMap) {
                        mapLoader.files = MapSystem.listSavedMapFiles();
                        // also include bundled blueprint if present
                        java.nio.file.Path bp = java.nio.file.Paths.get("maps/World_60310.map");
                        if (java.nio.file.Files.exists(bp) && !mapLoader.files.contains(bp.toString()))
                            mapLoader.files.add(0, bp.toString());
                        mapLoader.selected = 0;
                        mapLoader.open = true;
                        titleMenu.showMapLoader = true;
                    } else if (tact == UI.TitleAction.Survival) {
                        // Survival world: prefer World_60310 blueprint, else Regular seed
                        placedPieces.clear();
                        selectedIndex = -1;
                        boolean loaded = false;
                        java.nio.file.Path bp = java.nio.file.Paths.get("maps/World_60310.map");
                        if (java.nio.file.Files.exists(bp) && mapSystem.loadMap(bp.toString())) {
                            MapSystem.Map m = mapSystem.getCurrentMap();
                            if (m != null) {
                                placedPieces = new ArrayList<>(m.pieces);
                                loaded = true;
                            }
                        }
                        if (!loaded) {
                            int seed = 60310;
                            forest = Terrain.generateForestTerrain(seed);
                            mapSystem.createNewMap("Survival_60310", MapSystem.MapType.OUTDOOR);
                        } else {
                            forest = Terrain.generateForestTerrain(60310);
                        }
                        player = Player.init(forest);
                        // spawn near first structure if any
                        if (!placedPieces.isEmpty()) {
                            Piece.PlacedPiece first = placedPieces.get(0);
                            player.position.x(first.position.x())
                                    .z(first.position.z())
                                    .y(Terrain.getTerrainHeight(forest, first.position.x(), first.position.z()) + player.radius);
                        }
                        Survival.reset(forest);
                        gameMode = GameMode.SURVIVAL;
                        survInv = Survival.inv;
                        appState = AppState.Playing;
                        DisableCursor();
                    } else if (tact == UI.TitleAction.Settings) {
                        titleMenu.showSettings = true;
                    } else if (tact == UI.TitleAction.Exit) {
                        requestExit = true;
                    }
                }

                // Map loader overlay on title
                if (mapLoader.open) {
                    String path = UI.drawMapLoader(mapLoader, hasController);
                    if (path != null) {
                        if (mapSystem.loadMap(path)) {
                            MapSystem.Map m = mapSystem.getCurrentMap();
                            placedPieces.clear();
                            if (m != null) {
                                placedPieces = new ArrayList<>(m.pieces);
                                for (Piece.PlacedPiece pp : placedPieces) {
                                    if (MapSystem.isDynamicType(pp.type)) {
                                        pp.dynamic = true; pp.grounded = false;
                                    }
                                }
                            }
                            forest = Terrain.generateForestTerrain(path.hashCode());
                            player = Player.init(forest);
                            if (!placedPieces.isEmpty()) {
                                Piece.PlacedPiece first = placedPieces.get(0);
                                player.position.x(first.position.x())
                                        .z(first.position.z())
                                        .y(Terrain.getTerrainHeight(forest, first.position.x(), first.position.z()) + player.radius);
                            }
                            Survival.disable();
                        gameMode = GameMode.CREATIVE;
                            appState = AppState.Playing;
                            DisableCursor();
                            saveStatusMsg = "Loaded " + path;
                            saveStatusTimer = 3f;
                        } else {
                            saveStatusMsg = "Load failed: " + path;
                            saveStatusTimer = 3f;
                        }
                        titleMenu.showMapLoader = false;
                    }
                }
                if (saveStatusTimer > 0f && !saveStatusMsg.isEmpty()) {
                int tw = MeasureText(saveStatusMsg, 20);
                DrawRectangle(GetScreenWidth()/2 - tw/2 - 12, 48, tw + 24, 32, Helpers.newColor(0, 0, 0, 180));
                DrawText(saveStatusMsg, GetScreenWidth()/2 - tw/2, 54, 20, Helpers.newColor(80, 255, 120, 255));
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
                    if (gameMode.isSurvival()) {
                        Survival.showCraft = false;
                        Survival.inv.isOpen = !Survival.inv.isOpen;
                        if (Survival.inv.isOpen) EnableCursor(); else DisableCursor();
                    } else {
                        inventory.isOpen = !inventory.isOpen;
                        if (inventory.isOpen) EnableCursor(); else DisableCursor();
                    }
                }
            }
            if (IsKeyPressed(KEY_ESCAPE)) {
                if (isPlacing && !pauseMenu.open && !inventory.isOpen) {
                    hotbar.setSlot(hotbar.selected, -1);
                    isPlacing = false;
                } else if (gameMode.isSurvival() && Survival.inv.isOpen) {
                    Survival.inv.isOpen = false;
                    DisableCursor();
                } else if (gameMode.isSurvival() && Survival.showCraft) {
                    Survival.showCraft = false;
                    DisableCursor();
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
            boolean menuOpen = inventory.isOpen || pauseMenu.open
                || (gameMode.isSurvival() && (Survival.inv.isOpen || Survival.showCraft));
            // Survival craft toggle
            if (gameMode.isSurvival() && Survival.enabled && IsKeyPressed(KEY_C)
                    && !pauseMenu.open && !Survival.inv.isOpen) {
                Survival.showCraft = !Survival.showCraft;
                if (Survival.showCraft) EnableCursor(); else DisableCursor();
            }
            if (Survival.showCraft) Survival.handleCraftInput();
            // Ctrl+L map loader in-game
            if ((IsKeyDown(KEY_LEFT_CONTROL) || IsKeyDown(KEY_RIGHT_CONTROL)) && IsKeyPressed(KEY_L)) {
                mapLoader.files = MapSystem.listSavedMapFiles();
                java.nio.file.Path bp2 = java.nio.file.Paths.get("maps/World_60310.map");
                if (java.nio.file.Files.exists(bp2) && !mapLoader.files.contains(bp2.toString()))
                    mapLoader.files.add(0, bp2.toString());
                mapLoader.selected = 0;
                mapLoader.open = true;
                EnableCursor();
            }

            // F6 — jump into Physics Debug Room (indoor pad for boulder/teeter tests)
            if (!menuOpen && IsKeyPressed(KEY_F6)) {
                for (int mi = 0; mi < mapSystem.maps.size(); mi++) {
                    if ("Physics_Debug_Room".equals(mapSystem.maps.get(mi).name)) {
                        mapSystem.currentMapIndex = mi;
                        MapSystem.Map dm = mapSystem.maps.get(mi);
                        placedPieces = new ArrayList<>(dm.pieces);
                        for (Piece.PlacedPiece pp : placedPieces) {
                            if (MapSystem.isDynamicType(pp.type)) {
                                pp.dynamic = true; pp.grounded = false; pp.velY = 0f;
                            }
                        }
                        selectedIndex = -1;
                        saveStatusMsg = "Physics Debug Room";
                        saveStatusTimer = 2.5f;
                        break;
                    }
                }
            }


            // Hotbar: Creative = build pieces | Survival = tools/weapons
            if (!menuOpen) {
                if (gameMode.isSurvival()) {
                    SurvivalInv.handleHotbarKeys(Survival.inv);
                    // no piece placement in survival
                    heldIdx = -1;
                    isPlacing = false;
                } else {
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
                    heldIdx = hotbar.heldPieceIndex();
                    isPlacing = (heldIdx >= 0 && heldIdx < pieceDefs.size());
                }
            } else {
                heldIdx = gameMode.isCreative() ? hotbar.heldPieceIndex() : -1;
                isPlacing = gameMode.isCreative() && heldIdx >= 0 && heldIdx < pieceDefs.size();
            }
            placingType = isPlacing ? pieceDefs.get(heldIdx).type : Piece.PieceType.StraightLog;
            placingColor = isPlacing ? pieceDefs.get(heldIdx).defaultColor : Piece.PieceColor.Red;

            // Creative-only build toggles
            if (gameMode.isCreative()) {
                if (IsKeyPressed(KEY_B)) buildMode = BuildMode.MagneticSnap;
                if (IsKeyPressed(KEY_T)) buildMode = BuildMode.ShrineRotate;
                if (IsKeyPressed(KEY_G)) showGizmo = !showGizmo;
                if (IsKeyPressed(KEY_H)) showHelpers = !showHelpers;
            }
            // C = craft in Survival, camera toggle in Creative
            if (gameMode.isCreative() && IsKeyPressed(KEY_C) && !pauseMenu.open && !inventory.isOpen) {
                settings.thirdPerson = !settings.thirdPerson;
            }

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
                Player.update(player, forest, placedPieces, pieceDefs, dt, hasController, 0,
              camYaw, !Survival.showCraft && !mapLoader.open);
                // Loading zones — cooldown prevents instant bounce-back
                if (Terrain.warpCooldown <= 0f) {
                    toyz.builder.terrain.ZonePortal zp = Terrain.checkZonePortal(
                            forest, player.position.x(), player.position.y(), player.position.z());
                    if (zp != null && zp.active) {
                        zp.active = false;
                        // Remember overworld seed when leaving it
                        if (!forest.nether && !forest.indoor && !forest.caveWorld
                                && forest.worldType != Terrain.WorldType.FOREST
                                && forest.worldType != Terrain.WorldType.DESERT
                                && forest.worldType != Terrain.WorldType.CORAL
                                && forest.worldType != Terrain.WorldType.SKY
                                && forest.worldType != Terrain.WorldType.INDUSTRIAL) {
                            Terrain.homeSeed = forest.seed;
                        }
                        Terrain.WorldType dest;
                        int destSeed;
                        Terrain.pendingNether = false;
                        Terrain.pendingTheme = null;
                        Terrain.pendingSkyIslands = false;
                        switch (zp.target) {
                            case NETHER:
                                dest = Terrain.WorldType.NETHER;
                                destSeed = Terrain.homeSeed ^ 0x4E455448 ^ zp.salt;
                                Terrain.pendingNether = true;
                                break;
                            case INDOOR:
                                dest = Terrain.WorldType.INDOOR;
                                destSeed = Terrain.homeSeed ^ 0x494E4452 ^ zp.salt;
                                break;
                            case CAVE:
                                dest = Terrain.WorldType.CAVE;
                                destSeed = Terrain.homeSeed ^ 0x43415645 ^ zp.salt;
                                break;
                            case FOREST:
                                dest = Terrain.WorldType.FOREST;
                                destSeed = Terrain.homeSeed ^ 0x46525354 ^ zp.salt;
                                Terrain.pendingTheme = Terrain.WorldType.FOREST;
                                break;
                            case DESERT:
                                dest = Terrain.WorldType.DESERT;
                                destSeed = Terrain.homeSeed ^ 0x44535254 ^ zp.salt;
                                Terrain.pendingTheme = Terrain.WorldType.DESERT;
                                break;
                            case CORAL:
                                dest = Terrain.WorldType.CORAL;
                                destSeed = Terrain.homeSeed ^ 0x43524C00 ^ zp.salt;
                                Terrain.pendingTheme = Terrain.WorldType.CORAL;
                                break;
                            case SKY:
                                dest = Terrain.WorldType.SKY;
                                destSeed = Terrain.homeSeed ^ 0x534B5900 ^ zp.salt;
                                Terrain.pendingTheme = Terrain.WorldType.SKY;
                                Terrain.pendingSkyIslands = true;
                                break;
                            case INDUSTRIAL:
                                dest = Terrain.WorldType.INDUSTRIAL;
                                destSeed = Terrain.homeSeed ^ 0x494E4453 ^ zp.salt;
                                Terrain.pendingTheme = Terrain.WorldType.INDUSTRIAL;
                                break;
                            case OVERWORLD:
                            default:
                                dest = Terrain.WorldType.NORMAL;
                                destSeed = Terrain.homeSeed;
                                break;
                        }
                        forest = Terrain.generateForestTerrain(destSeed, dest, dest != Terrain.WorldType.INDOOR);
                        player = Player.init(forest);
                        // Spawn clear of return portal (return is at 14,14)
                        player.position.x(0).z(0)
                                .y(Terrain.getTerrainHeight(forest, 0, 0) + player.radius);
                        placedPieces.clear();
                        try {
                            if (forest.indoor) {
                                buildIndoorRoom(placedPieces, forest);
                            } else {
                                MapSystem.StructureSettings sc = new MapSystem.StructureSettings();
                                sc.enabled = true;
                                List<Piece.PlacedPiece> gen = MapSystem.generateStructures(forest, sc);
                                placedPieces.addAll(gen);
                                registerDoorPortals(forest, gen, forest.nether);
                            }
                        } catch (Throwable ex) {
                            System.err.println("[Zone] structure fail: " + ex.getMessage());
                        }
                        Terrain.warpCooldown = 1.25f; // seconds before next warp
                        System.out.println("[Zone] → " + dest + " via " + zp.name
                                + " seed=" + destSeed + " cooldown=1.25s");
                    }
                }
                if (gameMode.isSurvival() && Survival.enabled) {
                    Survival.update(player, forest, dt);
                    if (!menuOpen && !Survival.showCraft && !Survival.inv.isOpen
                            && IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
                        Survival.tryAttack(player.position, camYaw);
                        Survival.tryMine(player, forest, placedPieces, pieceDefs, camYaw);
                    }
                }
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
                        Piece.PieceDef belowDef = Piece.findDef(pieceDefs, below.type, below.color);
                        Piece.PieceDef placeDef = (heldIdx >= 0 && heldIdx < pieceDefs.size())
                            ? pieceDefs.get(heldIdx) : null;
                        if (belowDef != null) {
                            float placeHalf = placeDef != null ? placeDef.halfExtents.y() : belowDef.halfExtents.y();
                            ghostPos.y(below.position.y() + belowDef.halfExtents.y() + placeHalf);
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
                        if (dx * dx + dz * dz > (GRID_SIZE * 0.85f) * (GRID_SIZE * 0.85f)) continue;
                        Piece.PieceDef def = Piece.findDef(pieceDefs, p.type, p.color);
                        if (def == null) continue;
                        float top = p.position.y() + def.halfExtents.y();
                        if (top > bestY) {
                            bestY = top;
                            stackOnIndex = i;
                            float placeHalf = 0.25f;
                            if (heldIdx >= 0 && heldIdx < pieceDefs.size())
                                placeHalf = pieceDefs.get(heldIdx).halfExtents.y();
                            ghostPos.x(p.position.x())
                                    .y(top + placeHalf)
                                    .z(p.position.z());
                            stacked = true;
                        }
                    }
                    if (!stacked) {
                        float hy = 0.25f;
                        if (isPlacing && heldIdx >= 0 && heldIdx < pieceDefs.size())
                            hy = pieceDefs.get(heldIdx).halfExtents.y();
                        ghostPos.y(Terrain.getTerrainHeight(forest, ghostPos.x(), ghostPos.z()) + hy);
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
                                       || placingType == Piece.PieceType.LogVert
                                       || placingType == Piece.PieceType.NotchedLog
                                       || placingType == Piece.PieceType.CornerLog;
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
                if (IsKeyPressed(KEY_PAGE_UP) || IsKeyPressed(KEY_KP_ADD)) placeHeightOffset[0] += GRID_SIZE;
                if (IsKeyPressed(KEY_PAGE_DOWN) || IsKeyPressed(KEY_KP_SUBTRACT)) placeHeightOffset[0] -= GRID_SIZE;
                if (IsKeyPressed(KEY_HOME)) placeHeightOffset[0] = 0f;

                boolean placePressed = IsMouseButtonPressed(MOUSE_BUTTON_LEFT)
                    || (hasController && IsGamepadButtonPressed(0, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
                if (haveGhostPos && placePressed) {
                    Piece.PlacedPiece pp = new Piece.PlacedPiece();
                    pp.type = placingType;
                    pp.position = Helpers.newVector3(ghostPos.x(), ghostPos.y(), ghostPos.z());
                    pp.rotationY = placingRotation; pp.rotationX = placingRotX; pp.rotationZ = placingRotZ;
                    pp.color = placingColor; pp.length = placingLength;
                    Piece.PieceDef placedDef = Piece.findDef(pieceDefs, placingType, placingColor);
                    if (placedDef != null && placedDef.isDynamic) {
                        pp.dynamic = true;
                        pp.grounded = false;
                        pp.velY = 0f;
                    }
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


            // ================= SIMPLE PHYSICS =================
            // Boulders fall; teeter boards rock when mass is off-center.
            if (!menuOpen) {
                final float GRAVITY = 18f;
                for (int i = 0; i < placedPieces.size(); i++) {
                    Piece.PlacedPiece p = placedPieces.get(i);
                    Piece.PieceDef def = Piece.findDef(pieceDefs, p.type, p.color);
                    if (def == null) continue;

                    if (p.dynamic && p.type == Piece.PieceType.Boulder) {
                        float ground = Terrain.getTerrainHeight(forest, p.position.x(), p.position.z());
                        float half = def.halfExtents.y();
                        // Rest on top of nearest static piece if higher than ground
                        float support = ground + half;
                        for (int j = 0; j < placedPieces.size(); j++) {
                            if (j == i) continue;
                            Piece.PlacedPiece o = placedPieces.get(j);
                            if (o.dynamic && o.type == Piece.PieceType.Boulder) continue;
                            Piece.PieceDef od = Piece.findDef(pieceDefs, o.type, o.color);
                            if (od == null) continue;
                            float dx = p.position.x() - o.position.x();
                            float dz = p.position.z() - o.position.z();
                            float rad = def.halfExtents.x() + Math.max(od.halfExtents.x(), od.halfExtents.z());
                            if (dx * dx + dz * dz > rad * rad) continue;
                            float top = o.position.y() + od.halfExtents.y() + half;
                            if (top > support) support = top;
                        }
                        if (!p.grounded) {
                            p.velY -= GRAVITY * dt;
                            p.position.y(p.position.y() + p.velY * dt);
                        }
                        if (p.position.y() <= support) {
                            p.position.y(support);
                            p.velY = 0f;
                            p.grounded = true;
                        } else {
                            p.grounded = false;
                        }
                    }

                    if (p.type == Piece.PieceType.TeeterTotter) {
                        // Torque from nearby dynamic pieces left/right of fulcrum
                        float torque = 0f;
                        for (Piece.PlacedPiece o : placedPieces) {
                            if (o == p || !o.dynamic) continue;
                            float dx = o.position.x() - p.position.x();
                            float dz = o.position.z() - p.position.z();
                            // project along local beam X after rotationY
                            double ry = Math.toRadians(p.rotationY);
                            float localX = (float) (dx * Math.cos(ry) + dz * Math.sin(ry));
                            if (Math.abs(localX) > def.halfExtents.x() + 0.5f) continue;
                            float dy = o.position.y() - p.position.y();
                            if (dy < -0.5f || dy > 1.2f) continue;
                            torque += localX * 0.35f; // mass proxy
                        }
                        // Spring back toward level
                        p.angVel += (torque - p.rotationX * 2.5f) * dt;
                        p.angVel *= (1f - 3f * dt); // damping
                        p.rotationX += p.angVel * dt * 60f;
                        if (p.rotationX > 18f) { p.rotationX = 18f; p.angVel = 0f; }
                        if (p.rotationX < -18f) { p.rotationX = -18f; p.angVel = 0f; }
                    }
                }
            }

            // ================= RENDER =================
            BeginDrawing();
            int si = Math.max(0, Math.min(4, settings.skyboxIndex));
            // sky tint for nether handled below
            if (forest != null && forest.nether)
                ClearBackground(Helpers.newColor(45, 12, 10, 255));
            else if (forest != null && forest.caveWorld)
                ClearBackground(Helpers.newColor(8, 8, 12, 255));
            else if (forest != null && forest.indoor)
                ClearBackground(Helpers.newColor(25, 28, 32, 255));
            else
                ClearBackground(skyColors[si]);

            BeginMode3D(camera);
            if (forest != null && forest.darkForestFog && !forest.magnetix) {
                DrawCylinder(Helpers.newVector3(player.position.x(), player.position.y() - 5f, player.position.z()),
                        55f, 55f, 30f, 12, Helpers.newColor(20, 28, 22, 55));
            }

            Terrain.drawForestTerrain(forest, camera._position(), settings.treeDrawDistance, worldTime);
			Weather.draw(camera._position());
			
            if (settings.thirdPerson) Player.draw(player);
            for (Piece.PlacedPiece p : placedPieces) Piece.drawPlacedPiece(p, pieceDefs);
            if (Survival.enabled) Survival.drawWorld(camera);
            if (isPlacing && haveGhostPos) {
                Piece.PieceDef def = Piece.findDef(pieceDefs, placingType, placingColor);
                Color ghostCol = ghostStacked
                    ? Fade(Helpers.newColor(80, 220, 100, 255), 0.65f)
                    : Fade(SKYBLUE, 0.55f);
                if (def != null) {
                    DrawModelEx(def.model, ghostPos, Helpers.newVector3(0, 1, 0),
                                placingRotation, Helpers.newVector3(1, 1, 1), ghostCol);
                }
                // Soft placement cue (always): thin ground ring at snap cell
                DrawCircle3D(ghostPos, GRID_SIZE * 0.45f, Helpers.newVector3(1, 0, 0), 90,
                             Helpers.newColor(80, 255, 120, 160));
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
			
			DrawText("WEATHER: " + Weather.name(), 12, 106, 14, UI.white());
            // Biome name — top center (Minetest-style location label)
            {
                String bname = Terrain.biomeNameAt(forest, player.position.x(), player.position.z());
                int bw = MeasureText(bname, 22);
                int bx = GetScreenWidth() / 2 - bw / 2;
                DrawRectangle(bx - 12, 8, bw + 24, 28, Helpers.newColor(10, 12, 16, 180));
                DrawText(bname, bx, 12, 22, UI.phosphor());
            }
			
            // Hotbar + inventory (custom raylib UI replacing rlImGui)
            if (gameMode.isSurvival() && Survival.enabled) {
                Survival.drawHud(); // includes survival hotbar + inv
            } else {
                // Creative / builder HUD
                DrawText("MODE  CREATIVE", 18, 12, 12, UI.phosphor());
                Hotbar.drawHotbar(hotbar, pieceDefs);
                if (inventory.isOpen && !pauseMenu.open) {
                    Hotbar.updateFullInventory(inventory, hotbar, pieceDefs);
                }
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
                        boolean ok = mapSystem.saveCurrent();
                        saveStatusMsg = ok ? ("Saved maps/" + m.name.replace(' ', '_') + ".map") : "Save FAILED";
                        saveStatusTimer = 3.0f;
                    }
                } else if (act == UI.PauseAction.NewMap) {
                    // Same as title New World — no UnloadModel mid-session
                    placedPieces.clear();
                    selectedIndex = -1;
                    int seed = (int) (System.currentTimeMillis() ^ System.nanoTime());
                    forest = Terrain.generateForestTerrain(seed);
                    mapSystem.createNewMap(String.format("World %d", seed & 0xFFFF), MapSystem.MapType.OUTDOOR);
                    MapSystem.Map m = mapSystem.getCurrentMap();
                    if (m != null) m.pieces.clear();
                    player = Player.init(forest);
                    pauseMenu.open = false;
                    pauseMenu.showOptionsPanel = false;
                    DisableCursor();
                } else if (act == UI.PauseAction.ExitToTitle) {
                    MapSystem.Map m = mapSystem.getCurrentMap();
                    if (m != null) {
                        m.pieces = new ArrayList<>(placedPieces);
                        mapSystem.saveCurrent();
                    }
                    pauseMenu.open = false;
                    pauseMenu.showOptionsPanel = false;
                    inventory.isOpen = false;
                    appState = AppState.Title;
                    titleMenu.showSettings = false;
                    Survival.disable();
                        gameMode = GameMode.CREATIVE;
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

            if (appState == AppState.Playing && forest != null && player != null) {
                String hint = Terrain.nearestPortalHint(forest, player.position.x(), player.position.z(), 14f);
                if (hint != null) {
                    DrawText("PIPE: " + hint, 12, GetScreenHeight() - 52, 18,
                             Helpers.newColor(80, 255, 120, 255));
                }
                if (Terrain.warpCooldown > 0f) {
                    DrawText(String.format("warp lock %.1fs", Terrain.warpCooldown),
                             12, GetScreenHeight() - 30, 16, Helpers.newColor(255, 180, 80, 255));
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

        // Do not UnloadModel/UnloadTexture on exit — JavaCPP + raylib on Windows
        // often hits STATUS_HEAP_CORRUPTION (0xC0000374). Process exit reclaims VRAM.
        try {
            CloseWindow();
        } catch (Throwable t) {
            System.err.println("CloseWindow: " + t.getMessage());
        }
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


    private static void registerDoorPortals(Terrain.ForestTerrain forest,
                                            List<Piece.PlacedPiece> pieces, boolean netherWorld) {
        if (forest == null || pieces == null) return;
        int i = 0;
        for (Piece.PlacedPiece pp : pieces) {
            if (pp == null || pp.type != Piece.PieceType.Door) continue;
            float dx = pp.position.x(), dy = pp.position.y(), dz = pp.position.z();
            toyz.builder.terrain.ZonePortal.Target tgt = netherWorld
                    ? toyz.builder.terrain.ZonePortal.Target.OVERWORLD
                    : toyz.builder.terrain.ZonePortal.Target.INDOOR;
            Terrain.addZonePortal(forest, dx, dy, dz, tgt,
                    (int)(dx * 31 + dz * 17 + i), netherWorld ? "Nether Door" : "Door");
            i++;
        }
        System.out.println("[Zone] door portals registered=" + i);
    }

    private static void buildIndoorRoom(List<Piece.PlacedPiece> out, Terrain.ForestTerrain forest) {
        float g = Terrain.getTerrainHeight(forest, 0, 0);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                Piece.PlacedPiece fl = new Piece.PlacedPiece();
                fl.type = Piece.PieceType.PlankWide;
                fl.color = Piece.PieceColor.Oak;
                fl.position = Helpers.newVector3(x, g, z);
                out.add(fl);
            }
        }
        for (int y = 0; y < 3; y++) {
            for (int i = -4; i <= 4; i++) {
                for (int[] side : new int[][]{{i, -4}, {i, 4}, {-4, i}, {4, i}}) {
                    Piece.PlacedPiece w = new Piece.PlacedPiece();
                    w.type = Piece.PieceType.BlockWood;
                    w.color = Piece.PieceColor.Pine;
                    w.position = Helpers.newVector3(side[0], g + 0.5f + y, side[1]);
                    out.add(w);
                }
            }
        }
        Piece.PlacedPiece door = new Piece.PlacedPiece();
        door.type = Piece.PieceType.Door;
        door.color = Piece.PieceColor.Walnut;
        door.position = Helpers.newVector3(0, g + 0.9f, 4.2f);
        out.add(door);
        Terrain.addZonePortal(forest, 0, g + 0.9f, 4.2f,
                toyz.builder.terrain.ZonePortal.Target.OVERWORLD, forest.seed, "Exit");
    }
}
