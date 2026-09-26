package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Textures + GLB models for Survival / Light World.
 * Models live under assets/models/*.glb (Serif Sprite To Mesh / Blender exports).
 */
public final class AssetBank {

    private static final Map<String, Texture> texCache = new HashMap<>();
    private static final Map<String, Model> modelCache = new HashMap<>();
    private static boolean loaded = false;

    // Survival HUD
    public static Texture heartFull, heartEmpty;
    public static Texture[] enemyHp = new Texture[9];

    // Mob billboards (fallback if GLB missing)
    public static Texture mobImp, mobNeedle, mobAhriman, mobFire, mobIce, mobTonberry;
    public static Texture bossEye, bossCactus, bossGhost, bossKnight;

    // Crafting icons
    public static Texture iconWoodSword, iconMetalSword, iconPlasmaSword;
    public static Texture iconBow, iconBomb, iconLantern, iconKey;

    // World extras
    public static Texture texTorch, texBoulder, texTree, texJar;

    // GLB models
    public static Model modelVRMan;
    public static Model modelImp;       // leafy / slime stand-in
    public static Model modelNeedle;    // boss_cactus / spiney
    public static Model modelAhriman;   // boss_eyeballs / bat
    public static Model modelFire;      // skull1 / pacman accent
    public static Model modelIce;       // Booey / ghost
    public static Model modelTonberry;  // boss_knight / guard1
    public static Model modelDoor;
    public static Model modelSlime;
    public static Model modelGhost;
    public static Model modelBat;
    public static Model modelPede;
    public static Model modelNib;
    public static Model modelChest;
    public static Model modelSword1, modelSword2, modelSword3;
    public static Model modelAxeWood, modelAxeStone, modelAxeIron, modelAxeSteel;
    public static Model modelPickWood, modelPickStone, modelPickIron, modelPickSteel;
    public static Model modelRaft;
    public static Model modelPanel;


    /** Default world scale applied when drawing GLBs (Blender units → game). */
    public static final float MODEL_SCALE = 0.28f;   // enemy GLBs (was 0.55 — too large)
    public static final float VRMAN_SCALE = 0.38f;  // player GLB (was 0.65)

    private AssetBank() {}

    private static Texture loadTex(String path) {
        if (texCache.containsKey(path)) return texCache.get(path);
        File f = new File(path);
        if (!f.isFile()) {
            texCache.put(path, null);
            return null;
        }
        Texture t = LoadTexture(path);
        if (t == null || t.id() == 0) {
            texCache.put(path, null);
            return null;
        }
        SetTextureFilter(t, TEXTURE_FILTER_POINT);
        texCache.put(path, t);
        return t;
    }

    private static Texture loadTexFirst(String... paths) {
        for (String p : paths) {
            Texture t = loadTex(p);
            if (t != null) return t;
        }
        return null;
    }

    /**
     * Load a GLB/GLTF via raylib. Returns null if missing or unloadable.
     * raylib 4+ / Jaylib 6 support embedded GLB.
     */
    private static Model loadModel(String path) {
        if (modelCache.containsKey(path)) return modelCache.get(path);
        File f = new File(path);
        if (!f.isFile()) {
            System.out.println("[AssetBank] model missing: " + path);
            modelCache.put(path, null);
            return null;
        }
        try {
            Model m = LoadModel(path);
            if (m == null || m.meshCount() < 1) {
                System.out.println("[AssetBank] model empty/fail: " + path);
                modelCache.put(path, null);
                return null;
            }
            modelCache.put(path, m);
            System.out.println("[AssetBank] GLB ok: " + path + " meshes=" + m.meshCount());
            return m;
        } catch (Throwable t) {
            System.err.println("[AssetBank] LoadModel exception " + path + ": " + t.getMessage());
            modelCache.put(path, null);
            return null;
        }
    }

    private static Model loadModelFirst(String... paths) {
        for (String p : paths) {
            Model m = loadModel(p);
            if (m != null) return m;
        }
        return null;
    }

    public static void loadAll() {
        if (loaded) return;
        loaded = true;

        heartFull = loadTexFirst("assets/hud/1HEART.png", "assets/player/HEART.png");
        heartEmpty = loadTexFirst("assets/hud/1HP.png", "assets/hud/1-1.png");
        for (int i = 1; i <= 8; i++) {
            enemyHp[i] = loadTexFirst("assets/hud/ENEMY" + i + "HP.png", "assets/hud/" + i + "HP.png");
        }

        mobImp = loadTexFirst("assets/enemies/LEAFY-1-0.png", "assets/enemies/HELLOL.png", "assets/enemies/ENEMY.png");
        mobNeedle = loadTexFirst("assets/boss/cactus_boss.png", "assets/enemies/NEONIBUP.png");
        mobAhriman = loadTexFirst("assets/boss/eye_boss.png", "assets/enemies/GHOSTHARDL.png", "assets/boss/ghost_boss.png");
        mobFire = loadTexFirst("assets/enemies/CHROMAC-1.png", "assets/boss/jelly_boss.png");
        mobIce = loadTexFirst("assets/enemies/BOOEY-1-0.png", "assets/enemies/GHOSTHARDR.png");
        mobTonberry = loadTexFirst("assets/enemies/KNIGHTL-1.png", "assets/boss/knight_boss.png");

        bossEye = loadTexFirst("assets/boss/eye_boss.png");
        bossCactus = loadTexFirst("assets/boss/cactus_boss.png");
        bossGhost = loadTexFirst("assets/boss/ghost_boss.png");
        bossKnight = loadTexFirst("assets/boss/knight_boss.png");

        iconWoodSword = loadTexFirst("assets/inventory/WOODSWORD-1.png", "assets/inventory/sword_straight.png");
        iconMetalSword = loadTexFirst("assets/inventory/METALSWORD.png", "assets/inventory/sword2_straight.png");
        iconPlasmaSword = loadTexFirst("assets/inventory/PLASMASWORD.png", "assets/inventory/sword3_straight.png");
        iconBow = loadTexFirst("assets/inventory/bow.png", "assets/inventory/BOW UP.png");
        iconBomb = loadTexFirst("assets/inventory/bomb.png", "assets/inventory/BOMBS.png");
        iconLantern = loadTexFirst("assets/inventory/LANTERN-1.png", "assets/items/lantern.png");
        iconKey = loadTexFirst("assets/inventory/SMALL KEY.png", "assets/items/skey.png");

        texTorch = loadTexFirst("assets/obstacles/torch1.png");
        texBoulder = loadTexFirst("assets/obstacles/bigboulder.png", "assets/obstacles/smboulder.png");
        texTree = loadTexFirst("assets/obstacles/tree1.png");
        texJar = loadTexFirst("assets/obstacles/jar1.png");

        // ---- GLB models (assets/models) ----
        modelVRMan = loadModelFirst("assets/models/VRMan.glb");
        modelImp = loadModelFirst("assets/models/slime1.glb", "assets/models/nib.glb", "assets/models/pacman.glb");
        modelNeedle = loadModelFirst("assets/models/boss_cactus.glb", "assets/models/spiney.glb");
        modelAhriman = loadModelFirst("assets/models/boss_eyeballs.glb", "assets/models/bat.glb");
        modelFire = loadModelFirst("assets/models/skull1.glb", "assets/models/boss_twinskull.glb");
        modelIce = loadModelFirst("assets/models/Booey.glb", "assets/models/ghost.glb", "assets/models/boss_ghost.glb");
        modelTonberry = loadModelFirst("assets/models/boss_knight.glb", "assets/models/guard1.glb");
        modelDoor = loadModelFirst("assets/models/door1.glb");
        modelSlime = loadModelFirst("assets/models/slime1.glb");
        modelGhost = loadModelFirst("assets/models/ghost.glb", "assets/models/boss_ghost.glb");
        modelBat = loadModelFirst("assets/models/bat.glb");
        modelPede = loadModelFirst("assets/models/Pede.glb", "assets/models/boss_pede.glb");
        modelNib = loadModelFirst("assets/models/nib.glb");
        modelChest = loadModelFirst("assets/models/chestclosed.glb");
        modelSword1 = loadModelFirst("assets/models/sword1.glb");
        modelSword2 = loadModelFirst("assets/models/sword2.glb");
        modelSword3 = loadModelFirst("assets/models/sword3.glb");
        modelRaft = loadModelFirst("assets/models/raft.glb");
        modelPanel = loadModelFirst("assets/models/panelblock.glb");


        int texN = 0, modN = 0;
        for (Texture t : texCache.values()) if (t != null) texN++;
        for (Model m : modelCache.values()) if (m != null) modN++;
        System.out.println("[AssetBank] textures=" + texN + "  glb models=" + modN);
    }

    public static void unloadAll() {
        for (Texture t : texCache.values()) {
            if (t != null && t.id() != 0) UnloadTexture(t);
        }
        texCache.clear();
        for (Model m : modelCache.values()) {
            if (m != null) {
                try { UnloadModel(m); } catch (Throwable ignored) {}
            }
        }
        modelCache.clear();
        loaded = false;
    }

    public static Texture forMob(Mob.Kind k) {
        switch (k) {
            case IMP: return mobImp;
            case NEEDLEKIN: return mobNeedle;
            case AHRIMAN: return mobAhriman;
            case FIRE_SPRITE: return mobFire;
            case ICE_WISP: return mobIce;
            case TONBERRY: return mobTonberry;
            default: return mobImp;
        }
    }

    public static Model modelForMob(Mob.Kind k) {
        switch (k) {
            case IMP: return modelImp;
            case NEEDLEKIN: return modelNeedle;
            case AHRIMAN: return modelAhriman;
            case FIRE_SPRITE: return modelFire;
            case ICE_WISP: return modelIce;
            case TONBERRY: return modelTonberry;
            default: return modelImp;
        }
    }

    public static Texture forCraftResult(Crafting.Item item) {
        if (item == null) return null;
        switch (item) {
            case WOODEN_SWORD: return iconWoodSword;
            case STONE_SWORD: return iconMetalSword;
            case IRON_SWORD: return iconPlasmaSword;
            case TORCH: return texTorch != null ? texTorch : iconLantern;
            default: return null;
        }
    }

    /** Draw GLB at position, yaw degrees, uniform scale. */
    public static void drawModel(Model model, Vector3 pos, float yawDeg, float scale, Color tint) {
        if (model == null || model.meshCount() < 1) return;
        DrawModelEx(model, pos,
                Helpers.newVector3(0, 1, 0), yawDeg,
                Helpers.newVector3(scale, scale, scale),
                tint);
    }

    public static void drawBillboard(Camera3D cam, Texture tex, Vector3 pos, float size, Color tint) {
        if (tex == null || tex.id() == 0) return;
        try {
            DrawBillboard(cam, tex, pos, size, tint);
        } catch (Throwable t) {
            DrawCube(pos, size * 0.5f, size, 0.05f, tint);
        }
    }
}
