package com.toyzbuilder.engine;

import com.toyzbuilder.world.Entity;
import com.toyzbuilder.world.MapFile;
import com.toyzbuilder.world.World;
import com.toyzbuilder.world.WorldGenerator;
import com.toyzbuilder.world.TreeField;
import com.toyzbuilder.world.StructureGenerator;
import com.toyzbuilder.world.StructureSite;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

public class Window {

    private long window;
    private final GameSettings settings = GameSettings.get();
    private int appliedVersion = -1;
    private long nextFrameNs;
    private int refreshRate = 60;
    private Input input;
    private World world;
    private Renderer renderer;
    private AssetBank assets;
    private TreeField treeField;
    private TreeMeshes treeMeshes;
    private com.toyzbuilder.world.LandscapeFeatures landscapeFeatures;
    private StructureGenerator structureGen;
    private WorldMapUI worldMap;
    private Hud hud;
    private Editor editor;
    private BuilderUI builderUI;
    private MainMenu mainMenu;
    private boolean inMenu = true;
    private Mesh terrainMesh;
    private Mesh waterMesh;
    private Mesh playerMesh;
    private WorldGenerator.Result terrain;
    private long lastTime;
    private int frameCount;
    private float fpsTimer;
    private int fps;
    private float timeSec;
    private final Map<Integer, Integer> prevKeys = new HashMap<>();
    private static final File MAP_FILE = new File("assets/maps/editor_map.txt");

    public void run() {
        init();
        loop();
        if (terrainMesh != null) terrainMesh.cleanup();
        if (waterMesh != null) waterMesh.cleanup();
        if (playerMesh != null) playerMesh.cleanup();
        if (hud != null) hud.cleanup();
        if (renderer != null) renderer.cleanup();
        if (assets != null) assets.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    private void init() {
        settings.load();   // options.txt (defaults are HD 6450-safe)
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW init failed");

        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(1280, 720, "Toyz Explore — Textured Biomes", 0, 0);
        if (window == 0) throw new RuntimeException("No window");

        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(settings.vsync ? 1 : 0);
        var vm = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (vm != null) refreshRate = vm.refreshRate();
        System.out.println("[GL] " + GL11.glGetString(GL11.GL_RENDERER) + " | GL " + GL11.glGetString(GL11.GL_VERSION));
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        assets = new AssetBank();
        assets.loadAll();
        assets.applyFiltering(settings.texFilter, settings.aniso());
        treeMeshes = new TreeMeshes();
        treeField = new TreeField();
        landscapeFeatures = new com.toyzbuilder.world.LandscapeFeatures();
        structureGen = new StructureGenerator();
        worldMap = new WorldMapUI();

        WorldGenerator.Settings cfg = new WorldGenerator.Settings();
        cfg.seed = (int) (System.currentTimeMillis() & 0x7fffffff);
        cfg.resolution = 257;
        cfg.spacing = 2.5f;
        cfg.heightScale = 32f;
        cfg.waterLevel = 5.0f;

        long t0 = System.nanoTime();
        terrain = new WorldGenerator(cfg).generate();
        terrainMesh = new Mesh(
                WorldGenerator.buildVertexData(terrain),
                WorldGenerator.buildIndices(terrain),
                true);
        waterMesh = buildWaterMesh(terrain);
        landscapeFeatures.generate(terrain, cfg.seed);
        structureGen.generate(terrain, cfg.seed);
        playerMesh = buildPlayerMesh();
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.println("[Explore] seed=" + cfg.seed
                + " size=" + terrain.size
                + " tris=" + (terrainMesh.indexCount() / 3)
                + " gen+upload=" + String.format("%.1f", ms) + "ms"
                + " | textures from assets/textures/");

        treeField.generate(terrain, cfg.seed);
        world = new World(terrain);
        worldMap.setMarkers(structureGen.getSites(), terrain.size);
        world.setStructures(structureGen.getParts(), structureGen.getWarps());
        float spawnGround = WorldGenerator.sampleHeight(terrain, 0, 0);
        world.getController().setPlayerPos(0, Math.max(spawnGround, cfg.waterLevel), 0);

        editor = new Editor();
        builderUI = new BuilderUI();
        editor.setTerrain(terrain);
        mainMenu = new MainMenu();
        mainMenu.seed = cfg.seed;

        // Mouse clicks → editor (LMB place, RMB pick) — only meaningful in EDIT mode
        // mouse buttons polled via Input each frame (MC-style)

        input = new Input(window);
        // Title menu starts with free cursor
        input.setCursorDisabled(false);
        inMenu = true;
        renderer = new Renderer();
        renderer.init();
        hud = new Hud();
        hud.init();
        applySettings();
        lastTime = System.nanoTime();
    }

    /** Push GameSettings into live systems, then persist. Runs whenever the settings version changes. */
    private void applySettings() {
        appliedVersion = settings.version();
        GLFW.glfwSwapInterval(settings.vsync ? 1 : 0);
        if (world != null) world.getController().setMouseSens(settings.mouseSens());
        if (assets != null) assets.applyFiltering(settings.texFilter, settings.aniso());
        settings.save();
    }

    /**
     * Software frame cap. Skipped when vsync already paces at or below the cap.
     * Sleeps for the bulk of the frame, spins the last ~3 ms (Windows sleep is coarse).
     */
    private void limitFrame(boolean menu) {
        int cap = settings.fpsLimit();
        if (menu && (cap == 0 || cap > 60)) cap = 60;   // never spin a menu at 1000 fps
        if (cap <= 0 || (settings.vsync && cap >= refreshRate - 1)) { nextFrameNs = 0; return; }
        long frameNs = 1_000_000_000L / cap;
        long now = System.nanoTime();
        if (nextFrameNs == 0 || now - nextFrameNs > frameNs * 4) nextFrameNs = now;  // fell behind: resync
        nextFrameNs += frameNs;
        long remain = nextFrameNs - System.nanoTime();
        if (remain > 4_000_000L) LockSupport.parkNanos(remain - 3_000_000L);
        while (System.nanoTime() < nextFrameNs) Thread.onSpinWait();
    }

    /** Terrain-aware water surface: only low cells receive water instead of one global plane. */
    private Mesh buildWaterMesh(WorldGenerator.Result r) {
        int n = r.settings.resolution;
        float sp = r.settings.spacing;
        float half = r.size * 0.5f;
        float water = r.settings.waterLevel;
        java.util.ArrayList<Float> verts = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> idx = new java.util.ArrayList<>();
        int vi = 0;

        for (int z = 0; z < n - 1; z++) {
            for (int x = 0; x < n - 1; x++) {
                int i = z * n + x;
                float h00 = r.heights[i], h10 = r.heights[i + 1];
                float h01 = r.heights[i + n], h11 = r.heights[i + n + 1];
                if (Math.min(Math.min(h00, h10), Math.min(h01, h11)) > water + 0.15f) continue;

                float x0 = x * sp - half, x1 = x0 + sp;
                float z0 = z * sp - half, z1 = z0 + sp;
                float y = water + 0.015f;
                float[] q = { x0,y,z0, x1,y,z0, x1,y,z1, x0,y,z1 };
                for (float v : q) verts.add(v);
                idx.add(vi); idx.add(vi+1); idx.add(vi+2);
                idx.add(vi); idx.add(vi+2); idx.add(vi+3);
                vi += 4;
            }
        }
        float[] va = new float[verts.size()];
        int[] ia = new int[idx.size()];
        for (int i=0;i<va.length;i++) va[i]=verts.get(i);
        for (int i=0;i<ia.length;i++) ia[i]=idx.get(i);
        return new Mesh(va, ia, false);
    }

    /** Unit cube (centered on origin, ±0.5) used for the player + entities. */
    private Mesh buildPlayerMesh() {
        float s = 0.5f;
        float[] v = {
                // front (z-)
                -s, -s, -s,   s, -s, -s,   s,  s, -s,
                -s, -s, -s,   s,  s, -s,  -s,  s, -s,
                // back (z+)
                 s, -s,  s,  -s, -s,  s,  -s,  s,  s,
                 s, -s,  s,  -s,  s,  s,   s,  s,  s,
                // top
                -s,  s, -s,   s,  s, -s,   s,  s,  s,
                -s,  s, -s,   s,  s,  s,  -s,  s,  s,
                // bottom
                -s, -s,  s,   s, -s,  s,   s, -s, -s,
                -s, -s,  s,   s, -s, -s,  -s, -s, -s,
                // right (x+)
                 s, -s, -s,   s, -s,  s,   s,  s,  s,
                 s, -s, -s,   s,  s,  s,   s,  s, -s,
                // left (x-)
                -s, -s,  s,  -s, -s, -s,  -s,  s, -s,
                -s, -s,  s,  -s,  s, -s,  -s,  s,  s,
        };
        int[] idx = new int[v.length / 3];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        return new Mesh(v, idx, false);
    }

    /** Edge-triggered key check via Input abstraction. */
    private boolean keyPressed(int key) {
        return input != null && input.pressed(key);
    }

    private void loop() {
        while (!GLFW.glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - lastTime) / 1e9f);
            lastTime = now;
            timeSec += dt;

            input.beginFrame();
            if (settings.version() != appliedVersion) applySettings();

            // ---- MAIN MENU ----
            if (inMenu) {
                MainMenu.Action act = MainMenu.Action.NONE;
                if (keyPressed(GLFW.GLFW_KEY_UP) || keyPressed(GLFW.GLFW_KEY_W))
                    act = mainMenu.handleKey(0, false, false, true, false, false, false);
                if (keyPressed(GLFW.GLFW_KEY_DOWN) || keyPressed(GLFW.GLFW_KEY_S))
                    act = mainMenu.handleKey(0, false, false, false, true, false, false);
                if (keyPressed(GLFW.GLFW_KEY_LEFT) || keyPressed(GLFW.GLFW_KEY_A))
                    act = mainMenu.handleKey(0, true, false, false, false, false, false);
                if (keyPressed(GLFW.GLFW_KEY_RIGHT) || keyPressed(GLFW.GLFW_KEY_D))
                    act = mainMenu.handleKey(0, false, true, false, false, false, false);
                if (keyPressed(GLFW.GLFW_KEY_ENTER) || keyPressed(GLFW.GLFW_KEY_SPACE))
                    act = mainMenu.handleKey(0, false, false, false, false, true, false);
                if (keyPressed(GLFW.GLFW_KEY_ESCAPE))
                    act = mainMenu.handleKey(0, false, false, false, false, false, true);
                if (input.mousePressed(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT))
                    act = mainMenu.handleClick((float) input.mouseX(), (float) input.mouseY(), 1280, 720);

                if (act == MainMenu.Action.QUIT) {
                    GLFW.glfwSetWindowShouldClose(window, true);
                } else if (act == MainMenu.Action.PLAY_NEW) {
                    mainMenu.applyTo(terrain.settings);
                    regenerateFromSettings(terrain.settings);
                    enterPlay();
                } else if (act == MainMenu.Action.PLAY_CONTINUE) {
                    enterPlay();
                    if (MAP_FILE.isFile()) {
                        editor.load(MAP_FILE);
                        if (MapFile.playerStart != null) {
                            float[] ps = MapFile.playerStart;
                            world.getController().setPlayerPos(ps[0], ps[1], ps[2]);
                        }
                    }
                }

                GL11.glClearColor(0.02f, 0.03f, 0.04f, 1f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                hud.begin(1280, 720);
                mainMenu.draw(hud, 1280, 720);
                hud.end();
                GLFW.glfwSwapBuffers(window);
                GLFW.glfwPollEvents();
                limitFrame(true);
                continue;
            }

            // ESC layers: close inventory → leave edit → main menu
            if (keyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                if (worldMap.isFullMapOpen()) {
                    worldMap.setFullMapOpen(false);
                    if (input != null) {
                        input.setCursorDisabled(true);
                        world.getController().resyncMouseIfNeeded();
                    }
                } else if (editor.isActive() && builderUI.isInventoryOpen()) {
                    builderUI.setInventoryOpen(false);
                    if (input != null) {
                        input.setCursorDisabled(true);
                        world.getController().resyncMouseIfNeeded();
                    }
                } else if (editor.isActive()) {
                    editor.setActive(false);
                    world.getController().setEditorMode(false);
                    builderUI.setInventoryOpen(false);
                    if (input != null) {
                        input.setCursorDisabled(true);
                        input.resyncMouse();
                    }
                    world.getController().resyncMouseIfNeeded();
                } else {
                    // back to title menu
                    inMenu = true;
                    mainMenu.setScreen(MainMenu.Screen.TITLE);
                    if (input != null) input.setCursorDisabled(false);
                }
            }
            if (keyPressed(GLFW.GLFW_KEY_R) && !editor.isActive()) {
                regenerate();
            }

            // ---- editor keys ----
            if (keyPressed(GLFW.GLFW_KEY_TAB)) {
                editor.setActive(!editor.isActive());
                world.getController().setEditorMode(editor.isActive());
                if (!editor.isActive()) builderUI.setInventoryOpen(false);
                if (input != null) input.resyncMouse();
                world.getController().resyncMouseIfNeeded();
            }
            // E = inventory (only meaningful in edit mode)
            if (keyPressed(GLFW.GLFW_KEY_E)) {
                if (editor.isActive()) {
                    builderUI.toggleInventory();
                    if (input != null) {
                        // free cursor to click inventory; lock again when closed
                        input.setCursorDisabled(!builderUI.isInventoryOpen());
                        world.getController().resyncMouseIfNeeded();
                    }
                }
            }
            if (keyPressed(GLFW.GLFW_KEY_F3)) {
                world.getCollision().toggleDebugDraw();
            }

            // Minecraft-style mouse in edit mode
            if (editor.isActive() && input != null) {
                if (input.mousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                    if (worldMap.isFullMapOpen()) {
                        StructureSite warp = worldMap.handleClick((float) input.mouseX(), (float) input.mouseY());
                        if (warp != null) {
                            float gy = warp.y + 1.5f;
                            world.getController().setPlayerPos(warp.x, gy, warp.z + 6f);
                            worldMap.setFullMapOpen(false);
                            if (input != null) {
                                input.setCursorDisabled(true);
                                world.getController().resyncMouseIfNeeded();
                            }
                        }
                    } else if (builderUI.isInventoryOpen()) {
                        // screen-space click on inventory grid
                        builderUI.handleClick(editor, (float) input.mouseX(), (float) input.mouseY());
                    } else {
                        editor.mouseLeft();
                    }
                }
                if (input.mousePressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && !builderUI.isInventoryOpen()) {
                    editor.mouseRight();
                }
                if (input.mousePressed(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && !builderUI.isInventoryOpen()) {
                    editor.mouseMiddle();
                }
                int scroll = input.scrollSteps();
                if (scroll != 0) {
                    editor.cyclePiece(-scroll);
                }
            }

            if (editor.isActive()) {
                if (keyPressed(GLFW.GLFW_KEY_1)) builderUI.selectHotbar(editor, 0);
                if (keyPressed(GLFW.GLFW_KEY_2)) builderUI.selectHotbar(editor, 1);
                if (keyPressed(GLFW.GLFW_KEY_3)) builderUI.selectHotbar(editor, 2);
                if (keyPressed(GLFW.GLFW_KEY_4)) builderUI.selectHotbar(editor, 3);
                if (keyPressed(GLFW.GLFW_KEY_5)) builderUI.selectHotbar(editor, 4);
                if (keyPressed(GLFW.GLFW_KEY_6)) builderUI.selectHotbar(editor, 5);
                if (keyPressed(GLFW.GLFW_KEY_7)) builderUI.selectHotbar(editor, 6);
                if (keyPressed(GLFW.GLFW_KEY_8)) builderUI.selectHotbar(editor, 7);
                if (keyPressed(GLFW.GLFW_KEY_9)) builderUI.selectHotbar(editor, 8);
                if (keyPressed(GLFW.GLFW_KEY_0)) builderUI.selectHotbar(editor, 9);
                if (keyPressed(GLFW.GLFW_KEY_LEFT_BRACKET)) editor.cycleGroup(-1);
                if (keyPressed(GLFW.GLFW_KEY_RIGHT_BRACKET)) editor.cycleGroup(1);
                if (keyPressed(GLFW.GLFW_KEY_COMMA)) editor.cyclePiece(-1);
                if (keyPressed(GLFW.GLFW_KEY_PERIOD)) editor.cyclePiece(1);
                if (keyPressed(GLFW.GLFW_KEY_R)) editor.rotatePlace(1);
                if (keyPressed(GLFW.GLFW_KEY_MINUS)) editor.cycleSnap();
                if (keyPressed(GLFW.GLFW_KEY_EQUAL)) editor.cycleSnap();
                if (keyPressed(GLFW.GLFW_KEY_X) || keyPressed(GLFW.GLFW_KEY_DELETE) || keyPressed(GLFW.GLFW_KEY_Q)) editor.deleteSelected();
                if (keyPressed(GLFW.GLFW_KEY_G)) editor.duplicateSelected();
                if (keyPressed(GLFW.GLFW_KEY_F6)) {
                    var pp = world.getController().getPlayerPos();
                    editor.save(MAP_FILE, pp.x, pp.y, pp.z);
                }
                if (keyPressed(GLFW.GLFW_KEY_F9)) {
                    editor.load(MAP_FILE);
                    if (MapFile.playerStart != null) {
                        float[] ps = MapFile.playerStart;
                        world.getController().setPlayerPos(ps[0], ps[1], ps[2]);
                    }
                }
            }

            world.update(window, dt);
            // Keep solid AABBs in sync with placed entities
            world.getCollision().rebuildFromEntities(editor.getEntities());
            if (editor.isActive()) editor.update(world.getCamera());

            PlayerController pc = world.getController();
            FirstPersonCamera cam = world.getCamera();
            float waterLevel = terrain.settings.waterLevel;

            WorldGenerator.Biome under = WorldGenerator.sampleBiome(
                    terrain, cam.getX(), cam.getZ());
            boolean underwater = cam.getY() < waterLevel
                    && WorldGenerator.sampleHeight(terrain, cam.getX(), cam.getZ()) < waterLevel;

            float sr = 0.48f, sg = 0.64f, sb = 0.92f;
            if (under == WorldGenerator.Biome.DESERT || under == WorldGenerator.Biome.SAVANNA) {
                sr = 0.58f; sg = 0.72f; sb = 0.90f;
            } else if (under == WorldGenerator.Biome.SNOW || under == WorldGenerator.Biome.TAIGA) {
                sr = 0.55f; sg = 0.60f; sb = 0.72f;
            } else if (under == WorldGenerator.Biome.VOLCANIC) {
                sr = 0.22f; sg = 0.18f; sb = 0.20f;
            }
            if (underwater) { sr = 0.03f; sg = 0.12f; sb = 0.25f; }
            GL11.glClearColor(sr, sg, sb, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            float aspect = 1280f / 720f;
            // fog fades into the sky colour we just cleared with
            renderer.beginFrame(cam, aspect, settings, sr, sg, sb, underwater);
            renderer.renderTerrain(terrainMesh, assets);
            renderer.renderTrees(treeField, treeMeshes, assets);
            renderer.renderLandscapeFeatures(landscapeFeatures, assets);
            // structure parts — textured when texKey set; two-sided walls keep both faces
            Mesh cube = StructureGenerator.unitCube();
            for (StructureGenerator.Part pt : structureGen.getParts()) {
                Texture tex = (pt.texKey != null) ? assets.forKey(pt.texKey) : null;
                if (tex != null) {
                    renderer.renderTextured(cube, pt.x, pt.y, pt.z, pt.sx, pt.sy, pt.sz, pt.yaw,
                            tex, pt.r, pt.g, pt.b, 1f, pt.twoSided);
                } else {
                    renderer.renderMesh(cube, pt.x, pt.y, pt.z, pt.sx, pt.sy, pt.sz, pt.yaw,
                            pt.r, pt.g, pt.b, 1f);
                }
            }
            if (pc.isThirdPerson() && !editor.isActive()) {
                // mannequin: unit cube scaled to 0.6 x 1.7 x 0.6, feet on the ground
                renderer.renderMesh(playerMesh,
                        pc.getPlayerPos().x,
                        pc.getPlayerPos().y + 0.85f,
                        pc.getPlayerPos().z,
                        0.6f, 1.7f, 0.6f,
                        0.85f, 0.76f, 0.60f, 1f);
            }

            // ---- entities (pieces) — textured cubes for blocks; GLB for organic props ----
            for (Entity e : editor.getEntities()) {
                drawPiece(e.category, e.x, e.y, e.z, e.yaw,
                        e == editor.getSelected(), 1f);
            }

            // ---- placement marker ----
            if (editor.isActive() && editor.markerValid && !builderUI.isInventoryOpen()) {
                PieceCatalog.Def d = editor.currentPiece();
                float pulse = 0.55f + 0.35f * (float) Math.sin(timeSec * 6.0);
                drawPiece(d.id, editor.markerX, editor.markerY, editor.markerZ,
                        editor.getPlaceYaw(), false, pulse);
            }

            if (world.getCollision().isDebugDraw()) {
                float[] lines = world.getCollision().buildDebugLines();
                renderer.renderDebugLines(lines, 0.2f, 1f, 0.3f, 1f);
            }

            renderer.renderWater(waterMesh, assets, timeSec);

            // ---- HUD overlay ----
            // map / compass player pose
            var mapCam = world.getCamera();
            var pp = world.getController().getPlayerPos();
            worldMap.setPlayer(pp.x, pp.z, mapCam.getYaw());

            hud.begin(1280, 720);
            hud.setGlyphScale(2.0f);
            if (!inMenu) worldMap.draw(hud, 1280, 720);
            if (editor.isActive()) {
                builderUI.draw(hud, editor, 1280, 720, assets);
                drawEditorHud();
            } else if (settings.hudInfo == 1) {
                hud.text(10, 10, "FPS " + fps, 1, 1, 1, 1);
            } else if (settings.hudInfo == 2) {
                hud.text(10, 10, "Toyz Explore   FPS " + fps, 1, 1, 1, 1);
                hud.text(10, 26, "Biome: " + under.name()
                        + "   XYZ: " + String.format("%.0f, %.1f, %.0f",
                                pc.getPlayerPos().x, pc.getPlayerPos().y, pc.getPlayerPos().z),
                        1, 1, 1, 1);
                hud.text(10, 42, (pc.isThirdPerson() ? "[3rd Person]" : "[1st Person]")
                        + "  " + (pc.isFlying() ? "[FLYING]" : "[WALK]")
                        + (underwater ? "  [UNDERWATER]" : ""),
                        underwater ? 0.4f : 1f, underwater ? 0.8f : 1f, 1f, 1f);
                hud.text(10, 58, "TAB edit  E inv  WASD  Space jump  Ctrl sprint  Shift sneak  F fly  F5/C cam  F3 debug  R regen", 0.8f, 0.8f, 0.8f, 1f);
            }
            // Crosshair always in first-person (build + play)
            if (!pc.isThirdPerson()) {
                hud.rect(640 - 10, 360 - 1, 20, 2, 1, 1, 1, 0.9f);
                hud.rect(640 - 1, 360 - 10, 2, 20, 1, 1, 1, 0.9f);
            }

            // health bar, bottom center (placeholder in play mode)
            if (!editor.isActive()) {
                hud.rect(1280 / 2f - 102, 700, 204, 16, 0.05f, 0.05f, 0.05f, 0.8f);
                hud.rect(1280 / 2f - 100, 702, 200, 12, 0.75f, 0.15f, 0.15f, 1f);
            }

            if (underwater && settings.transparency) {   // full-screen alpha quad: fill-rate heavy, opt-in
                hud.rect(0, 0, 1280, 720, 0.05f, 0.2f, 0.45f, 0.25f);
            }
            hud.end();

            frameCount++;
            fpsTimer += dt;
            if (fpsTimer >= 1f) {
                fps = frameCount;
                frameCount = 0;
                fpsTimer = 0f;
                String title = editor.isActive()
                        ? String.format("Toyz Explore | EDITOR | %d entities | %s",
                                editor.getEntities().size(), editor.getStatus())
                        : String.format("Toyz Explore | FPS %d | %s | (%.0f, %.0f) | R=regen | ESC=quit",
                                fps, under.name(), cam.getX(), cam.getZ());
                GLFW.glfwSetWindowTitle(window, title);
            }

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            limitFrame(false);
        }
    }

    /** Left panel: category palette. Right panel: selected entity inspector. */
        private void drawEditorHud() {
        // inventory drawn by BuilderUI; status strip only
        hud.rect(8, 720 - 26, 1280 - 16, 20, 0.02f, 0.03f, 0.05f, 0.85f);
        hud.text(14, 720 - 22, editor.getStatus(), 0.4f, 1f, 0.5f, 1f);
        if (settings.hudInfo > 0) {
            hud.text(10, 10, "BUILD MODE   FPS " + fps
                    + "   pieces: " + editor.getEntities().size(), 1f, 0.9f, 0.3f, 1f);
        }
        if (settings.hudInfo == 2) {
            hud.text(10, 26, "TAB edit  E inv  LMB place  RMB break  MMB pick  Scroll  R rotate  Ctrl sprint  Shift sneak  Q/X del  F6/F9",
                    0.75f, 0.85f, 0.75f, 1f);
        }
    }


    /** Prefer textured UV-cube for block-like pieces; GLB for boulders/doors/actors. */
    private void drawPiece(String id, float x, float feetY, float z, float yaw,
                           boolean selected, float alpha) {
        PieceCatalog.Def d = PieceCatalog.get(id);
        // distance + frustum cull (bounding sphere of the piece box)
        float rad = 0.5f * (float) Math.sqrt(d.sx * d.sx + d.sy * d.sy + d.sz * d.sz);
        if (!selected && !renderer.visible(x, feetY + d.sy * 0.5f, z, rad, settings.renderDistance())) return;
        float rr = selected ? 1f : d.r;
        float gg = selected ? 0.95f : d.g;
        float bb = selected ? 0.2f : d.b;
        float y = feetY + d.sy * 0.5f;
        boolean useGlb = wantsGlb(d);
        if (useGlb && d.modelPath != null) {
            Model mdl = Model.load(d.modelPath);
            if (mdl != null && !mdl.fallback) {
                float sx = d.sx / Math.max(0.01f, mdl.sizeX);
                float sy = d.sy / Math.max(0.01f, mdl.sizeY);
                float sz = d.sz / Math.max(0.01f, mdl.sizeZ);
                Texture modelTex = assets.forPiece(d.id);
                renderer.renderTexturedModel(mdl, x, y, z,
                        sx, sy, sz, yaw, modelTex, rr, gg, bb, alpha);
                return;
            }
        }
        Texture tex = assets.forPiece(d.id);
        // mild tint so selection still reads; texture carries the surface
        float tr = selected ? 1.15f : 1f;
        renderer.renderTextured(PrimitiveMeshes.uvCube(),
                x, y, z, d.sx, d.sy, d.sz, yaw, tex,
                Math.min(1f, rr * tr), Math.min(1f, gg * tr), Math.min(1f, bb * tr), alpha);
    }

    private static boolean wantsGlb(PieceCatalog.Def d) {
        String u = d.id.toUpperCase();
        return u.contains("BOULDER") || u.contains("DOOR") || u.contains("WINDOW")
                || u.contains("CHEST") || u.contains("SLIDE") || u.contains("RAFT")
                || u.contains("ENEMY") || u.contains("BOSS") || u.contains("BALL")
                || u.contains("LOG");
    }


    private void enterPlay() {
        inMenu = false;
        if (input != null) {
            input.setCursorDisabled(true);
            input.resyncMouse();
        }
        world.getController().resyncMouseIfNeeded();
        editor.setActive(false);
        world.getController().setEditorMode(false);
        builderUI.setInventoryOpen(false);
    }

    private void regenerateFromSettings(WorldGenerator.Settings cfg) {
        long t0 = System.nanoTime();
        terrain = new WorldGenerator(cfg).generate();
        if (terrainMesh != null) terrainMesh.cleanup();
        if (waterMesh != null) waterMesh.cleanup();
        terrainMesh = new Mesh(
                WorldGenerator.buildVertexData(terrain),
                WorldGenerator.buildIndices(terrain),
                true);
        waterMesh = buildWaterMesh(terrain);
        landscapeFeatures.generate(terrain, cfg.seed);
        structureGen.generate(terrain, cfg.seed);
        treeField.generate(terrain, cfg.seed);
        worldMap.setMarkers(structureGen.getSites(), terrain.size);
        world.setTerrain(terrain);
        world.setStructures(structureGen.getParts(), structureGen.getWarps());
        editor.setTerrain(terrain);
        float spawnGround = WorldGenerator.sampleHeight(terrain, 0, 0);
        world.getController().setPlayerPos(0, Math.max(spawnGround, cfg.waterLevel), 0);
        System.out.println("[Explore] menu gen seed=" + cfg.seed
                + " size=" + terrain.size
                + " in " + String.format("%.1f", (System.nanoTime() - t0) / 1e6) + "ms");
    }

    private void regenerate() {
        WorldGenerator.Settings cfg = terrain.settings;
        cfg.seed = (int) (System.currentTimeMillis() & 0x7fffffff);
        long t0 = System.nanoTime();
        terrain = new WorldGenerator(cfg).generate();
        if (terrainMesh != null) terrainMesh.cleanup();
        terrainMesh = new Mesh(
                WorldGenerator.buildVertexData(terrain),
                WorldGenerator.buildIndices(terrain),
                true);
        world.setTerrain(terrain);
        editor.setTerrain(terrain);
        if (waterMesh != null) waterMesh.cleanup();
        waterMesh = buildWaterMesh(terrain);
        treeField.generate(terrain, cfg.seed);
        landscapeFeatures.generate(terrain, cfg.seed);
        structureGen.generate(terrain, cfg.seed);
        worldMap.setMarkers(structureGen.getSites(), terrain.size);
        world.setStructures(structureGen.getParts(), structureGen.getWarps());
        float spawnGround = WorldGenerator.sampleHeight(terrain, 0, 0);
        world.getController().setPlayerPos(0, Math.max(spawnGround, cfg.waterLevel), 0);
        System.out.println("[Explore] regen seed=" + cfg.seed
                + " in " + String.format("%.1f", (System.nanoTime() - t0) / 1e6) + "ms");
    }
}
