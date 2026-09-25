package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads placeholder sprites/textures from the merged assets pack
 * (enemies, boss, hud hearts, inventory icons, world textures).
 */
public final class AssetBank {

    private static final Map<String, Texture> cache = new HashMap<>();
    private static boolean loaded = false;

    // Survival HUD
    public static Texture heartFull, heartEmpty;
    public static Texture[] enemyHp = new Texture[9]; // 1..8

    // Mob billboards (FF-ish stand-ins from the pack)
    public static Texture mobImp, mobNeedle, mobAhriman, mobFire, mobIce, mobTonberry;
    public static Texture bossEye, bossCactus, bossGhost, bossKnight;

    // Crafting / items
    public static Texture iconWoodSword, iconMetalSword, iconPlasmaSword;
    public static Texture iconBow, iconBomb, iconLantern, iconKey;

    // World extras
    public static Texture texTorch, texBoulder, texTree, texJar;

    private AssetBank() {}

    private static Texture load(String path) {
        if (cache.containsKey(path)) return cache.get(path);
        File f = new File(path);
        if (!f.isFile()) {
            // try alternate separators / cwd relative
            f = new File(path.replace('\\', '/'));
            if (!f.isFile()) {
                cache.put(path, null);
                return null;
            }
        }
        Texture t = LoadTexture(path);
        if (t == null || t.id() == 0) {
            cache.put(path, null);
            return null;
        }
        SetTextureFilter(t, TEXTURE_FILTER_POINT); // crisp pixel art
        cache.put(path, t);
        return t;
    }

    /** Prefer first path that exists. */
    private static Texture loadFirst(String... paths) {
        for (String p : paths) {
            Texture t = load(p);
            if (t != null) return t;
        }
        return null;
    }

    public static void loadAll() {
        if (loaded) return;
        loaded = true;

        heartFull = loadFirst("assets/hud/1HEART.png", "assets/player/HEART.png");
        heartEmpty = loadFirst("assets/hud/1HP.png", "assets/hud/1-1.png");
        for (int i = 1; i <= 8; i++) {
            enemyHp[i] = loadFirst("assets/hud/ENEMY" + i + "HP.png", "assets/hud/" + i + "HP.png");
        }

        // Map FF kinds → familiar pack sprites
        mobImp = loadFirst("assets/enemies/LEAFY-1-0.png", "assets/enemies/HELLOL.png", "assets/enemies/ENEMY.png");
        mobNeedle = loadFirst("assets/boss/cactus_boss.png", "assets/enemies/NEONIBUP.png");
        mobAhriman = loadFirst("assets/boss/eye_boss.png", "assets/enemies/GHOSTHARDL.png", "assets/boss/ghost_boss.png");
        mobFire = loadFirst("assets/enemies/CHROMAC-1.png", "assets/boss/jelly_boss.png");
        mobIce = loadFirst("assets/enemies/BOOEY-1-0.png", "assets/enemies/GHOSTHARDR.png");
        mobTonberry = loadFirst("assets/enemies/KNIGHTL-1.png", "assets/boss/knight_boss.png", "assets/enemies/KNIGHTR-1.png");

        bossEye = loadFirst("assets/boss/eye_boss.png");
        bossCactus = loadFirst("assets/boss/cactus_boss.png");
        bossGhost = loadFirst("assets/boss/ghost_boss.png");
        bossKnight = loadFirst("assets/boss/knight_boss.png");

        iconWoodSword = loadFirst("assets/inventory/WOODSWORD-1.png", "assets/inventory/sword_straight.png");
        iconMetalSword = loadFirst("assets/inventory/METALSWORD.png", "assets/inventory/sword2_straight.png");
        iconPlasmaSword = loadFirst("assets/inventory/PLASMASWORD.png", "assets/inventory/sword3_straight.png");
        iconBow = loadFirst("assets/inventory/bow.png", "assets/inventory/BOW UP.png");
        iconBomb = loadFirst("assets/inventory/bomb.png", "assets/inventory/BOMBS.png");
        iconLantern = loadFirst("assets/inventory/LANTERN-1.png", "assets/items/lantern.png");
        iconKey = loadFirst("assets/inventory/SMALL KEY.png", "assets/items/skey.png");

        texTorch = loadFirst("assets/obstacles/torch1.png");
        texBoulder = loadFirst("assets/obstacles/bigboulder.png", "assets/obstacles/smboulder.png");
        texTree = loadFirst("assets/obstacles/tree1.png");
        texJar = loadFirst("assets/obstacles/jar1.png");

        int ok = 0;
        for (Texture t : cache.values()) if (t != null) ok++;
        System.out.println("[AssetBank] loaded " + ok + " textures from assets pack");
    }

    public static void unloadAll() {
        for (Texture t : cache.values()) {
            if (t != null && t.id() != 0) UnloadTexture(t);
        }
        cache.clear();
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

    /** Draw a world-facing sprite billboard (pixel art). */
    public static void drawBillboard(Camera3D cam, Texture tex, Vector3 pos, float size, Color tint) {
        if (tex == null || tex.id() == 0) return;
        // Manual billboard facing camera XZ
        Vector3 camPos = cam._position();
        float dx = camPos.x() - pos.x();
        float dz = camPos.z() - pos.z();
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        // Use DrawBillboard if available
        try {
            DrawBillboard(cam, tex, pos, size, tint);
        } catch (Throwable t) {
            // fallback cube
            DrawCube(pos, size * 0.5f, size, 0.05f, tint);
        }
    }
}
