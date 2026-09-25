package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Survival mode state: HP, bag, mobs, spawners, day threat scaling.
 * Building mode remains available; survival adds combat + crafting HUD.
 */
public final class Survival {

    public static boolean enabled = false;
    public static float playerHp = 100f;
    public static float playerMaxHp = 100f;
    public static float invuln = 0f;
    public static Crafting.Bag bag = Crafting.starterBag();
    public static List<Mob.Entity> mobs = new ArrayList<>();
    public static List<Mob.Spawner> spawners = new ArrayList<>();
    public static boolean showCraft = false;
    public static int craftSelected = 0;
    public static String toast = "";
    public static float toastTimer = 0f;
    public static Random rng = new Random();

    private Survival() {}

    public static void reset(Terrain.ForestTerrain forest) {
        enabled = true;
        playerHp = playerMaxHp;
        invuln = 0f;
        bag = Crafting.starterBag();
        mobs.clear();
        spawners = Mob.defaultSurvivalSpawners(forest);
        showCraft = false;
        toast("Survival mode — LMB attack, C craft, Ctrl+L load maps");
    }

    public static void disable() {
        enabled = false;
        mobs.clear();
        spawners.clear();
        showCraft = false;
    }

    public static void toast(String msg) {
        toast = msg;
        toastTimer = 3.5f;
    }

    public static void damagePlayer(float dmg) {
        if (!enabled || invuln > 0f) return;
        playerHp -= dmg;
        invuln = 0.6f;
        if (playerHp < 0) playerHp = 0;
        toast("Hit! HP " + (int) playerHp);
    }

    public static void update(Player player, Terrain.ForestTerrain forest, float dt) {
        if (!enabled) return;
        invuln = Math.max(0f, invuln - dt);
        if (toastTimer > 0f) toastTimer -= dt;

        Mob.updateAll(mobs, spawners, player, forest, dt, rng);

        // passive regen very slow when not in combat
        if (invuln <= 0f && playerHp < playerMaxHp) {
            playerHp = Math.min(playerMaxHp, playerHp + 1.5f * dt);
        }

        // attack (building placement uses LMB when a piece is held — call attackNearest from main if needed)
    }

    public static void attackNearest(Player player) {
        float best = 3.2f;
        Mob.Entity target = null;
        for (Mob.Entity m : mobs) {
            if (!m.alive) continue;
            float dx = m.pos.x() - player.position.x();
            float dy = m.pos.y() - player.position.y();
            float dz = m.pos.z() - player.position.z();
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < best) { best = d; target = m; }
        }
        if (target != null) {
            target.hp -= bag.weaponDamage;
            toast(target.kind.name() + " −" + (int) bag.weaponDamage + " HP");
            if (target.hp <= 0) {
                target.alive = false;
                // loot
                bag.add(Crafting.Item.WOOD, 1 + rng.nextInt(2));
                if (rng.nextFloat() < 0.35f) bag.add(Crafting.Item.STONE, 1);
                if (rng.nextFloat() < 0.12f) bag.add(Crafting.Item.IRON_ORE, 1);
                toast("Defeated " + target.kind.name() + " — loot gained");
            }
        }
    }

    public static void attackRay(float px, float py, float pz) {
        int idx = Mob.pick(mobs, px, py, pz, 3.5f);
        if (idx < 0) return;
        Mob.Entity target = mobs.get(idx);
        target.hp -= bag.weaponDamage;
        toast(target.kind.name() + " −" + (int) bag.weaponDamage);
        if (target.hp <= 0) {
            target.alive = false;
            bag.add(Crafting.Item.WOOD, 1 + rng.nextInt(2));
            if (rng.nextFloat() < 0.35f) bag.add(Crafting.Item.STONE, 1);
            if (rng.nextFloat() < 0.12f) bag.add(Crafting.Item.IRON_ORE, 1);
            toast("Defeated " + target.kind.name());
        }
    }

    public static void drawWorld(Camera3D camera) {
        if (!enabled) return;
        Mob.drawAll(mobs, camera);
        // spawner markers (small pillars)
        for (Mob.Spawner s : spawners) {
            if (!s.active) continue;
            DrawCylinder(Helpers.newVector3(s.pos.x(), s.pos.y(), s.pos.z()),
                    0.2f, 0.25f, 1.2f, 6, Helpers.newColor(120, 40, 160, 200));
            DrawSphere(Helpers.newVector3(s.pos.x(), s.pos.y() + 1.3f, s.pos.z()), 0.18f,
                    Helpers.newColor(200, 80, 255, 220));
        }
    }

    public static void drawHud() {
        if (!enabled) return;
        int sw = GetScreenWidth();
        // Heart-style HP (pixel pack) with bar fallback
        int hearts = 8;
        float per = playerMaxHp / hearts;
        int filled = (int) Math.ceil(playerHp / per);
        if (AssetBank.heartFull != null && AssetBank.heartFull.id() != 0) {
            for (int i = 0; i < hearts; i++) {
                Texture ht = (i < filled) ? AssetBank.heartFull :
                        (AssetBank.heartEmpty != null ? AssetBank.heartEmpty : AssetBank.heartFull);
                if (ht == null) continue;
                float alpha = (i < filled) ? 1f : 0.35f;
                DrawTextureEx(ht, Helpers.newVector2(18 + i * 28, 16), 0f, 2.0f,
                        Helpers.newColor(255, 255, 255, (int) (255 * alpha)));
            }
            DrawText((int) playerHp + "/" + (int) playerMaxHp, 18 + hearts * 28 + 8, 22, 16, WHITE);
        } else {
            DrawRectangle(20, 20, 220, 22, Helpers.newColor(20, 20, 28, 200));
            float ratio = playerHp / playerMaxHp;
            DrawRectangle(22, 22, (int) (216 * ratio), 18,
                    ratio > 0.3f ? Helpers.newColor(80, 200, 100, 255) : Helpers.newColor(220, 60, 60, 255));
            DrawText("HP " + (int) playerHp + "/" + (int) playerMaxHp, 28, 24, 16, WHITE);
        }
        // Weapon
        String wpn = bag.equippedWeapon != null ? bag.equippedWeapon.name : "Fists";
        DrawText("Weapon: " + wpn + "  (" + (int) bag.weaponDamage + " dmg)", 20, 48, 16, UI.phosphor());
        DrawText("Mobs: " + mobs.size() + "   C craft | LMB attack", 20, 68, 14, UI.phosphorDim());

        if (toastTimer > 0f && !toast.isEmpty()) {
            int tw = MeasureText(toast, 18);
            DrawRectangle(sw / 2 - tw / 2 - 10, 90, tw + 20, 28, Helpers.newColor(0, 0, 0, 180));
            DrawText(toast, sw / 2 - tw / 2, 96, 18, Helpers.newColor(255, 220, 120, 255));
        }

        if (showCraft) drawCraftPanel();
    }

    private static void drawCraftPanel() {
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        float pw = 420, ph = 460;
        Rectangle panel = Helpers.newRectangle((sw - pw) * 0.5f, (sh - ph) * 0.5f, pw, ph);
        UI.drawSteelPanel(panel, "CRAFTING  //  SURVIVAL");

        // bag summary
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
            if (icon != null && icon.id() != 0) {
                DrawTextureEx(icon, Helpers.newVector2(panel.x() + 18, y - 2), 0f, 0.9f, WHITE);
                DrawText((sel ? "> " : "  ") + r.name, (int) panel.x() + 48, (int) y, 15, col);
            } else {
                DrawText((sel ? "> " : "  ") + r.name, (int) panel.x() + 20, (int) y, 15, col);
            }
            y += 22;
        }
    }

    public static void handleCraftInput() {
        if (!enabled || !showCraft) return;
        List<Crafting.Recipe> list = Crafting.recipes();
        if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) craftSelected = Math.max(0, craftSelected - 1);
        if (IsKeyPressed(KEY_DOWN) || IsKeyPressed(KEY_S)) craftSelected = Math.min(list.size() - 1, craftSelected + 1);
        if (IsKeyPressed(KEY_ENTER) || IsKeyPressed(KEY_SPACE)) {
            Crafting.Recipe r = list.get(craftSelected);
            if (Crafting.craft(bag, r)) toast("Crafted " + r.name);
            else toast("Missing materials");
        }
    }
}
