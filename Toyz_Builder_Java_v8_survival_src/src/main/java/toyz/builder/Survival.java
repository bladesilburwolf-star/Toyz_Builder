package toyz.builder;

import com.raylib.Helpers;
import com.raylib.Raylib.Camera3D;
import com.raylib.Raylib.Color;
import com.raylib.Raylib.Rectangle;
import com.raylib.Raylib.Texture;
import com.raylib.Raylib.Vector3;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Survival / Light World mode state.
 * Creative mode does not use this (GameMode.CREATIVE keeps builder inventory only).
 */
public final class Survival {

    /** True only while GameMode.SURVIVAL is active. */
    public static boolean enabled = false;

    public static float playerHp = 100f;
    public static float playerMaxHp = 100f;
    public static Crafting.Bag bag = Crafting.starterBag();
    public static SurvivalInv.State inv = new SurvivalInv.State();
    public static List<Mob.Entity> mobs = new ArrayList<>();
    public static List<Mob.Spawner> spawners = new ArrayList<>();
    public static boolean showCraft = false;
    public static int craftSelected = 0;
    public static float attackCooldown = 0f;
    public static float mineCooldown = 0f;
    public static float toastTimer = 0f;
    public static String toast = "";
    public static final Random rng = new Random();

    private Survival() {}

    public static void disable() {
        enabled = false;
        showCraft = false;
        inv.isOpen = false;
        mobs.clear();
        spawners.clear();
    }

    public static void reset(Terrain.ForestTerrain forest) {
        enabled = true;
        playerHp = playerMaxHp;
        bag = Crafting.starterBag();
        inv = SurvivalInv.fromBag(bag);
        showCraft = false;
        inv.isOpen = false;
        attackCooldown = 0f;
        toastTimer = 0f;
        toast = "";
        mobs.clear();
        spawners.clear();
        // seed a few spawners around origin
        if (forest != null) {
            Mob.Kind[] kinds = Mob.Kind.values();
            for (int i = 0; i < 5; i++) {
                float x = (rng.nextFloat() - 0.5f) * 80f;
                float z = (rng.nextFloat() - 0.5f) * 80f;
                float y = Terrain.getTerrainHeight(forest, x, z) + 0.5f;
                Mob.Spawner sp = new Mob.Spawner();
                sp.pos = Helpers.newVector3(x, y, z);
                sp.kind = kinds[rng.nextInt(kinds.length)];
                sp.interval = 7f + rng.nextFloat() * 5f;
                spawners.add(sp);
            }
        }
        toast("SURVIVAL  //  Light World");
    }

    public static void toast(String msg) {
        toast = msg;
        toastTimer = 2.5f;
    }

    public static void damagePlayer(float dmg) {
        if (!enabled) return;
        playerHp -= dmg;
        if (playerHp < 0f) playerHp = 0f;
        toast("Hit! HP " + (int) playerHp);
    }

    public static void update(float dt, Vector3 playerPos, Terrain.ForestTerrain forest) {
        // compat wrapper — prefer update(Player,...)
        if (!enabled) return;
        if (toastTimer > 0f) toastTimer -= dt;
        if (attackCooldown > 0f) attackCooldown -= dt;
        if (mineCooldown > 0f) mineCooldown -= dt;
        Crafting.Item held = SurvivalInv.heldItem(inv);
        if (held != null && Crafting.kindOf(held) == Crafting.Kind.WEAPON) {
            bag.equippedWeapon = held;
            bag.weaponDamage = Crafting.damageOf(held);
        }
    }

    public static void update(Player player, Terrain.ForestTerrain forest, float dt) {
        if (!enabled) return;
        if (toastTimer > 0f) toastTimer -= dt;
        if (attackCooldown > 0f) attackCooldown -= dt;
        if (mineCooldown > 0f) mineCooldown -= dt;
        Crafting.Item held = SurvivalInv.heldItem(inv);
        if (held != null && Crafting.kindOf(held) == Crafting.Kind.WEAPON) {
            bag.equippedWeapon = held;
            bag.weaponDamage = Crafting.damageOf(held);
        }
        Mob.updateAll(mobs, spawners, player, forest, dt, rng);
    }


    /**
     * Mine / destroy world objects: trees and placed pieces in front of player.
     * Drops materials into survival inventory.
     */
    public static void tryMine(Player player, Terrain.ForestTerrain forest,
                               java.util.List<Piece.PlacedPiece> placed,
                               java.util.List<Piece.PieceDef> defs, float yawDeg) {
        if (!enabled || mineCooldown > 0f || player == null) return;
        float yaw = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float px = player.position.x(), py = player.position.y() + player.eyeHeight * 0.5f, pz = player.position.z();
        float reach = 3.2f;
        float reach2 = reach * reach;

        // 1) Trees
        if (forest != null && forest.trees != null) {
            Terrain.ForestTree bestTree = null;
            float best = reach2;
            int bestIdx = -1;
            for (int i = 0; i < forest.trees.size(); i++) {
                Terrain.ForestTree tr = forest.trees.get(i);
                if (tr == null || tr.position == null) continue;
                float dx = tr.position.x() - px, dz = tr.position.z() - pz;
                float d2 = dx * dx + dz * dz;
                if (d2 > best) continue;
                float dot = dx * fx + dz * fz;
                if (dot < 0.15f) continue;
                best = d2;
                bestTree = tr;
                bestIdx = i;
            }
            if (bestTree != null) {
                mineCooldown = 0.45f;
                forest.trees.remove(bestIdx);
                int wood = 2 + (bestTree.biomeType == 7 ? 3 : 0); // redwood yields more
                bag.add(Crafting.Item.WOOD, wood);
                bag.add(Crafting.Item.STICK, 1);
                SurvivalInv.addToBag(inv, Crafting.Item.WOOD, wood);
                SurvivalInv.addToBag(inv, Crafting.Item.STICK, 1);
                toast("Chopped tree +" + wood + " Wood");
                return;
            }
        }

        // 2) Placed / structure pieces
        if (placed != null) {
            Piece.PlacedPiece best = null;
            float bestD = reach2;
            int bestIdx = -1;
            for (int i = 0; i < placed.size(); i++) {
                Piece.PlacedPiece pp = placed.get(i);
                if (pp == null || pp.position == null) continue;
                float dx = pp.position.x() - px;
                float dy = pp.position.y() - py;
                float dz = pp.position.z() - pz;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > bestD) continue;
                float dot = dx * fx + dz * fz;
                if (dot < 0.1f) continue;
                bestD = d2;
                best = pp;
                bestIdx = i;
            }
            if (best != null) {
                mineCooldown = 0.4f;
                placed.remove(bestIdx);
                Crafting.Item drop = dropForPiece(best.type);
                int n = 1;
                bag.add(drop, n);
                SurvivalInv.addToBag(inv, drop, n);
                toast("Mined " + drop.name + " +" + n);
            }
        }
    }

    private static Crafting.Item dropForPiece(Piece.PieceType type) {
        if (type == null) return Crafting.Item.STONE;
        switch (type) {
            case StraightLog: case LogVert: case NotchedLog: case CornerLog:
            case HalfLog: case LogStub: case Plank: case PlankWide: case BlockWood:
            case FloorPlank: case Roof: case RoofPeak: case Door: case Sign:
                return Crafting.Item.WOOD;
            case BlockMetal: case MetalCage: case BlockIron: case BlockCopper:
            case BlockTitanium: case BlockMagnecite:
                return Crafting.Item.IRON_ORE;
            case BlockStone: case BlockConcrete: case BlockSand: case BlockDirt:
            case BlockGrass: case BlockSnow:
                return Crafting.Item.STONE;
            default:
                return Crafting.Item.STONE;
        }
    }

    public static void tryAttack(Vector3 playerPos, float yawDeg) {
        if (!enabled || attackCooldown > 0f) return;
        attackCooldown = 0.35f;
        float yaw = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float reach = 2.4f;
        float dmg = SurvivalInv.heldDamage(inv);
        Mob.Entity target = null;
        float best = reach * reach;
        for (Mob.Entity m : mobs) {
            if (!m.alive) continue;
            float dx = m.pos.x() - playerPos.x();
            float dz = m.pos.z() - playerPos.z();
            float dist2 = dx * dx + dz * dz;
            if (dist2 > best) continue;
            // must be roughly in front
            float dot = dx * fx + dz * fz;
            if (dot < 0.2f) continue;
            best = dist2;
            target = m;
        }
        if (target != null) {
            target.hp -= dmg;
            toast(target.kind.name() + " −" + (int) dmg + " HP");
            if (target.hp <= 0f) {
                target.alive = false;
                // loot
                bag.add(Crafting.Item.STICK, 1);
                SurvivalInv.addToBag(inv, Crafting.Item.STICK, 1);
                toast("Loot +Stick");
            }
        }
    }

    public static void drawWorld(Camera3D camera) {
        if (!enabled) return;
        Mob.drawAll(mobs, camera);
        for (Mob.Spawner sp : spawners) {
            if (AssetBank.modelChest != null && AssetBank.modelChest.meshCount() > 0) {
                AssetBank.drawModel(AssetBank.modelChest, sp.pos, 0f, 0.45f, WHITE);
            } else {
                DrawCube(sp.pos, 0.6f, 1.2f, 0.6f, Helpers.newColor(80, 40, 100, 220));
                DrawCubeWires(sp.pos, 0.6f, 1.2f, 0.6f, Helpers.newColor(180, 80, 255, 255));
            }
        }
    }

    private static void drawHeart(float x, float y, float s, Color c) {
        DrawCircle((int) (x + s * 0.30f), (int) (y + s * 0.32f), s * 0.28f, c);
        DrawCircle((int) (x + s * 0.70f), (int) (y + s * 0.32f), s * 0.28f, c);
        DrawTriangle(
            Helpers.newVector2(x + s * 0.08f, y + s * 0.40f),
            Helpers.newVector2(x + s * 0.92f, y + s * 0.40f),
            Helpers.newVector2(x + s * 0.50f, y + s * 0.95f),
            c);
    }

    public static void drawHud() {
        if (!enabled) return;
        int sw = GetScreenWidth();

        DrawRectangle(12, 10, 300, 86, Helpers.newColor(12, 14, 18, 210));
        DrawRectangleLinesEx(Helpers.newRectangle(12, 10, 300, 86), 1.5f, UI.phosphorDim());
        DrawText("MODE  SURVIVAL", 18, 12, 12, UI.phosphor());

        int hearts = 8;
        float per = playerMaxHp / hearts;
        int filled = (int) Math.ceil(Math.max(0f, playerHp) / per);
        for (int i = 0; i < hearts; i++) {
            Color c = (i < filled)
                ? Helpers.newColor(80, 255, 120, 255)
                : Helpers.newColor(40, 55, 45, 255);
            drawHeart(18 + i * 22, 28, 18f, c);
        }
        float ratio = Math.max(0f, Math.min(1f, playerHp / playerMaxHp));
        DrawRectangle(18, 50, 204, 8, Helpers.newColor(30, 35, 40, 255));
        Color barCol = ratio > 0.3f
            ? Helpers.newColor(80, 255, 120, 255)
            : Helpers.newColor(255, 70, 70, 255);
        DrawRectangle(18, 50, (int) (204 * ratio), 8, barCol);
        DrawText((int) playerHp + " / " + (int) playerMaxHp, 228, 46, 14, UI.phosphor());

        Crafting.Item held = SurvivalInv.heldItem(inv);
        String wpn = held != null ? held.name : "Fists";
        DrawText(wpn + "  (" + (int) SurvivalInv.heldDamage(inv) + " dmg)", 18, 64, 14, UI.phosphor());
        DrawText("Mobs " + mobs.size() + "  |  C craft  |  LMB attack", 18, 78, 12, UI.phosphorDim());

        SurvivalInv.drawHotbar(inv);

        if (toastTimer > 0f && !toast.isEmpty()) {
            int tw = MeasureText(toast, 18);
            DrawRectangle(sw / 2 - tw / 2 - 10, 100, tw + 20, 28, Helpers.newColor(0, 0, 0, 180));
            DrawText(toast, sw / 2 - tw / 2, 106, 18, Helpers.newColor(255, 220, 120, 255));
        }

        if (inv.isOpen) SurvivalInv.drawInventory(inv);
        if (showCraft) drawCraftPanel();
    }

    private static void drawCraftPanel() {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        float pw = 420, ph = 480;
        Rectangle panel = Helpers.newRectangle((sw - pw) * 0.5f, (sh - ph) * 0.5f, pw, ph);
        UI.drawSteelPanel(panel, "CRAFTING  //  SURVIVAL");

        float y = panel.y() + 40;
        DrawText("Materials", (int) panel.x() + 16, (int) y, 16, UI.phosphor());
        y += 22;
        Crafting.Item[] mats = { Crafting.Item.WOOD, Crafting.Item.STONE, Crafting.Item.IRON_ORE, Crafting.Item.STICK };
        for (Crafting.Item it : mats) {
            DrawText(it.name + ": " + bag.get(it), (int) panel.x() + 20, (int) y, 14, WHITE);
            y += 18;
        }
        y += 8;
        DrawText("Recipes  [Up/Down]  Enter craft  C close", (int) panel.x() + 16, (int) y, 14, UI.phosphorDim());
        y += 22;

        List<Crafting.Recipe> list = Crafting.recipes();
        if (craftSelected < 0) craftSelected = 0;
        if (craftSelected >= list.size()) craftSelected = list.size() - 1;

        for (int i = 0; i < list.size(); i++) {
            Crafting.Recipe r = list.get(i);
            boolean can = Crafting.canCraft(bag, r);
            boolean sel = i == craftSelected;
            Color col = sel ? UI.phosphor() : (can ? WHITE : Helpers.newColor(100, 100, 110, 255));
            Texture icon = AssetBank.forCraftResult(r.result);
            String tag = r.result.modelPlaceholder ? " [GLB]" : "";
            if (icon != null && icon.id() != 0) {
                DrawTextureEx(icon, Helpers.newVector2(panel.x() + 18, y - 2), 0f, 0.9f, WHITE);
                DrawText((sel ? "> " : "  ") + r.name + tag, (int) panel.x() + 48, (int) y, 15, col);
            } else {
                SurvivalInv.drawItemIcon(r.result, panel.x() + 18, y - 2, 18);
                DrawText((sel ? "> " : "  ") + r.name + tag, (int) panel.x() + 48, (int) y, 15, col);
            }
            y += 22;
        }
    }

    public static void handleCraftInput() {
        if (!showCraft) return;
        List<Crafting.Recipe> list = Crafting.recipes();
        if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) craftSelected = Math.max(0, craftSelected - 1);
        if (IsKeyPressed(KEY_DOWN) || IsKeyPressed(KEY_S)) craftSelected = Math.min(list.size() - 1, craftSelected + 1);
        if (IsKeyPressed(KEY_ENTER)) {
            Crafting.Recipe r = list.get(craftSelected);
            if (Crafting.craft(bag, r)) {
                SurvivalInv.addToBag(inv, r.result, r.resultQty);
                toast("Crafted " + r.name);
            } else {
                toast("Missing materials");
            }
        }
    }
}
