package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Final Fantasy–inspired enemy mobs (not Minecraft zombies/creepers).
 * Simple chase AI, HP bars, contact damage. Spawners drip new mobs over time.
 */
public final class Mob {

    public enum Kind {
        IMP,        // small red melee
        NEEDLEKIN,  // cactus-like, short range spikes
        AHRIMAN,    // floating eye, slow hover
        FIRE_SPRITE,// orange orb, burns on contact
        ICE_WISP,   // cyan float, slows player briefly (damage only for now)
        TONBERRY    // slow, high damage if it reaches you
    }

    public static class Entity {
        public Kind kind;
        public Vector3 pos = Helpers.newVector3(0, 0, 0);
        public float hp, maxHp;
        public float speed;
        public float radius;
        public float attackCd;
        public float attackDmg;
        public float anim;
        public boolean alive = true;
        public Color body;
        public Color accent;
    }

    public static class Spawner {
        public Vector3 pos = Helpers.newVector3(0, 0, 0);
        public Kind kind = Kind.IMP;
        public float interval = 8f;
        public float timer = 2f;
        public int maxAlive = 3;
        public float spawnRadius = 6f;
        public boolean active = true;
    }

    private Mob() {}

    public static Entity make(Kind k, float x, float y, float z) {
        Entity e = new Entity();
        e.kind = k;
        e.pos = Helpers.newVector3(x, y, z);
        e.anim = (float) (Math.random() * 6.28);
        switch (k) {
            case IMP:
                e.maxHp = 28; e.speed = 3.2f; e.radius = 0.35f; e.attackDmg = 6;
                e.body = Helpers.newColor(200, 60, 50, 255); e.accent = Helpers.newColor(255, 180, 40, 255);
                break;
            case NEEDLEKIN:
                e.maxHp = 40; e.speed = 2.0f; e.radius = 0.45f; e.attackDmg = 8;
                e.body = Helpers.newColor(40, 140, 60, 255); e.accent = Helpers.newColor(180, 220, 80, 255);
                break;
            case AHRIMAN:
                e.maxHp = 35; e.speed = 2.4f; e.radius = 0.50f; e.attackDmg = 7;
                e.body = Helpers.newColor(90, 50, 140, 255); e.accent = Helpers.newColor(255, 80, 80, 255);
                break;
            case FIRE_SPRITE:
                e.maxHp = 22; e.speed = 3.8f; e.radius = 0.30f; e.attackDmg = 9;
                e.body = Helpers.newColor(255, 120, 30, 255); e.accent = Helpers.newColor(255, 220, 80, 255);
                break;
            case ICE_WISP:
                e.maxHp = 24; e.speed = 2.8f; e.radius = 0.32f; e.attackDmg = 5;
                e.body = Helpers.newColor(140, 210, 255, 255); e.accent = Helpers.newColor(220, 245, 255, 255);
                break;
            case TONBERRY:
                e.maxHp = 70; e.speed = 1.15f; e.radius = 0.42f; e.attackDmg = 22;
                e.body = Helpers.newColor(40, 120, 70, 255); e.accent = Helpers.newColor(255, 230, 80, 255);
                break;
        }
        e.hp = e.maxHp;
        return e;
    }

    public static void updateAll(List<Entity> mobs, List<Spawner> spawners, Player player,
                                 Terrain.ForestTerrain forest, float dt, Random rng) {
        // Spawners
        for (Spawner s : spawners) {
            if (!s.active) continue;
            int aliveNear = 0;
            for (Entity m : mobs) {
                if (!m.alive) continue;
                float dx = m.pos.x() - s.pos.x(), dz = m.pos.z() - s.pos.z();
                if (dx * dx + dz * dz < 25f * 25f) aliveNear++;
            }
            s.timer -= dt;
            if (s.timer <= 0f && aliveNear < s.maxAlive) {
                s.timer = s.interval * (0.7f + rng.nextFloat() * 0.6f);
                float ang = rng.nextFloat() * 6.2832f;
                float r = 1.5f + rng.nextFloat() * s.spawnRadius;
                float sx = s.pos.x() + (float) Math.cos(ang) * r;
                float sz = s.pos.z() + (float) Math.sin(ang) * r;
                float sy = Terrain.getTerrainHeight(forest, sx, sz) + 0.5f;
                mobs.add(make(s.kind, sx, sy, sz));
            }
        }

        // AI
        for (Entity m : mobs) {
            if (!m.alive) continue;
            m.anim += dt * 4f;
            m.attackCd = Math.max(0f, m.attackCd - dt);

            float px = player.position.x(), pz = player.position.z();
            float dx = px - m.pos.x(), dz = pz - m.pos.z();
            float dist = (float) Math.sqrt(dx * dx + dz * dz);

            // hover types float a bit
            float ground = Terrain.getTerrainHeight(forest, m.pos.x(), m.pos.z());
            float targetY = ground + m.radius;
            if (m.kind == Kind.AHRIMAN || m.kind == Kind.FIRE_SPRITE || m.kind == Kind.ICE_WISP) {
                targetY = ground + 1.1f + 0.25f * (float) Math.sin(m.anim);
            }
            m.pos.y(m.pos.y() + (targetY - m.pos.y()) * Math.min(1f, 4f * dt));

            if (dist > 0.15f && dist < 28f) {
                float inv = 1f / dist;
                m.pos.x(m.pos.x() + dx * inv * m.speed * dt);
                m.pos.z(m.pos.z() + dz * inv * m.speed * dt);
            }

            // contact damage
            if (dist < m.radius + player.radius + 0.15f && m.attackCd <= 0f) {
                Survival.damagePlayer(m.attackDmg);
                m.attackCd = (m.kind == Kind.TONBERRY) ? 1.6f : 0.85f;
            }
        }

        // prune dead
        mobs.removeIf(m -> !m.alive);
    }

    public static void drawAll(List<Entity> mobs, Camera3D cam) {
        for (Entity m : mobs) {
            if (!m.alive) continue;
            drawOne(m, cam);
        }
    }

    private static void drawHpBar(Entity m) {
        if (m.hp >= m.maxHp) return;
        float w = 0.8f;
        float y = m.pos.y() + m.radius + 0.55f;
        DrawCube(Helpers.newVector3(m.pos.x(), y, m.pos.z()), w, 0.06f, 0.06f, Helpers.newColor(40, 40, 40, 220));
        float ratio = Math.max(0f, m.hp / m.maxHp);
        DrawCube(Helpers.newVector3(m.pos.x() - w * 0.5f * (1f - ratio), y, m.pos.z()),
                w * ratio, 0.07f, 0.07f, Helpers.newColor(220, 50, 50, 255));
    }

    private static void drawOne(Entity m, Camera3D cam) {
        Vector3 p = m.pos;
        float bob = 0.12f * (float) Math.sin(m.anim);
        Vector3 drawPos = Helpers.newVector3(p.x(), p.y() + bob, p.z());

        // Prefer GLB model
        Model mdl = AssetBank.modelForMob(m.kind);
        if (mdl != null && mdl.meshCount() > 0) {
            float scale = AssetBank.MODEL_SCALE;
            if (m.kind == Kind.TONBERRY) scale *= 1.25f;
            if (m.kind == Kind.AHRIMAN) scale *= 1.15f;
            if (m.kind == Kind.NEEDLEKIN) scale *= 1.2f;
            if (m.kind == Kind.IMP) scale *= 0.85f;
            Color tint = WHITE;
            if (m.kind == Kind.FIRE_SPRITE) tint = Helpers.newColor(255, 200, 160, 255);
            if (m.kind == Kind.ICE_WISP) tint = Helpers.newColor(200, 230, 255, 255);
            // face roughly toward movement is future work — fixed yaw for now
            AssetBank.drawModel(mdl, drawPos, m.anim * 20f % 360f, scale, tint);
            drawHpBar(m);
            return;
        }

        // Billboard fallback
        Texture sprite = AssetBank.forMob(m.kind);
        if (sprite != null && sprite.id() != 0 && cam != null) {
            float size = m.radius * 3.2f;
            if (m.kind == Kind.TONBERRY) size = m.radius * 3.8f;
            if (m.kind == Kind.AHRIMAN) size = m.radius * 3.5f;
            Color tint = WHITE;
            if (m.kind == Kind.FIRE_SPRITE) tint = Helpers.newColor(255, 180, 120, 255);
            if (m.kind == Kind.ICE_WISP) tint = Helpers.newColor(180, 220, 255, 255);
            AssetBank.drawBillboard(cam, sprite, drawPos, size, tint);
            drawHpBar(m);
            return;
        }
        switch (m.kind) {
            case IMP: {
                DrawSphere(p, m.radius, m.body);
                DrawSphere(Helpers.newVector3(p.x(), p.y() + m.radius * 0.7f, p.z()), m.radius * 0.45f, m.accent);
                // horns
                DrawCylinder(Helpers.newVector3(p.x() - 0.12f, p.y() + m.radius * 0.9f, p.z()),
                        0.04f, 0.01f, 0.25f, 6, m.accent);
                DrawCylinder(Helpers.newVector3(p.x() + 0.12f, p.y() + m.radius * 0.9f, p.z()),
                        0.04f, 0.01f, 0.25f, 6, m.accent);
                break;
            }
            case NEEDLEKIN: {
                DrawCylinder(Helpers.newVector3(p.x(), p.y() - m.radius, p.z()), m.radius * 0.7f, m.radius * 0.5f, m.radius * 2f, 8, m.body);
                for (int i = 0; i < 6; i++) {
                    float a = i * 1.047f + m.anim * 0.2f;
                    float nx = p.x() + (float) Math.cos(a) * m.radius * 0.9f;
                    float nz = p.z() + (float) Math.sin(a) * m.radius * 0.9f;
                    DrawCylinder(Helpers.newVector3(nx, p.y(), nz), 0.03f, 0.01f, 0.35f, 4, m.accent);
                }
                break;
            }
            case AHRIMAN: {
                DrawSphere(p, m.radius, m.body);
                DrawSphere(Helpers.newVector3(p.x(), p.y(), p.z() + m.radius * 0.55f), m.radius * 0.35f, WHITE);
                DrawSphere(Helpers.newVector3(p.x(), p.y(), p.z() + m.radius * 0.75f), m.radius * 0.15f, m.accent);
                // wing flaps (flat boxes)
                float flap = 0.3f * (float) Math.sin(m.anim * 3);
                DrawCube(Helpers.newVector3(p.x() - m.radius * 1.1f, p.y() + flap, p.z()), 0.5f, 0.08f, 0.35f, m.body);
                DrawCube(Helpers.newVector3(p.x() + m.radius * 1.1f, p.y() - flap, p.z()), 0.5f, 0.08f, 0.35f, m.body);
                break;
            }
            case FIRE_SPRITE: {
                float pulse = 0.85f + 0.15f * (float) Math.sin(m.anim * 6);
                DrawSphere(p, m.radius * pulse, m.body);
                DrawSphere(p, m.radius * 0.5f, m.accent);
                break;
            }
            case ICE_WISP: {
                DrawSphere(p, m.radius, Fade(m.body, 0.85f));
                DrawSphereWires(p, m.radius * 1.15f, 8, 8, m.accent);
                break;
            }
            case TONBERRY: {
                DrawCylinder(Helpers.newVector3(p.x(), p.y() - m.radius, p.z()), m.radius * 0.55f, m.radius * 0.65f, m.radius * 1.6f, 8, m.body);
                DrawSphere(Helpers.newVector3(p.x(), p.y() + m.radius * 0.85f, p.z()), m.radius * 0.4f, m.body);
                // lantern
                DrawSphere(Helpers.newVector3(p.x() + 0.35f, p.y() + 0.1f, p.z()), 0.12f, m.accent);
                break;
            }
        }
        // HP bar
        if (m.hp < m.maxHp) {
            float w = 0.8f;
            float y = p.y() + m.radius + 0.45f;
            DrawCube(Helpers.newVector3(p.x(), y, p.z()), w, 0.06f, 0.06f, Helpers.newColor(40, 40, 40, 220));
            float ratio = Math.max(0f, m.hp / m.maxHp);
            DrawCube(Helpers.newVector3(p.x() - w * 0.5f * (1f - ratio), y, p.z()), w * ratio, 0.07f, 0.07f,
                    Helpers.newColor(220, 50, 50, 255));
        }
    }

    /** Placeholder — combat uses proximity attackNearest in Survival. */
    public static int pick(List<Entity> mobs, float px, float py, float pz, float maxDist) {
        int best = -1;
        float bestD = maxDist;
        for (int i = 0; i < mobs.size(); i++) {
            Entity m = mobs.get(i);
            if (!m.alive) continue;
            float dx = m.pos.x() - px, dy = m.pos.y() - py, dz = m.pos.z() - pz;
            float d = (float) Math.sqrt(dx*dx+dy*dy+dz*dz);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    public static void seedSpawnersNearPlayer(List<Spawner> spawners, Terrain.ForestTerrain forest, float px, float pz) {
        spawners.clear();
        Kind[] kinds = Kind.values();
        float[][] offsets = { {18, 8}, {-22, 12}, {10, -25}, {-15, -18}, {30, -5} };
        for (int i = 0; i < offsets.length; i++) {
            Spawner s = new Spawner();
            s.pos.x(px + offsets[i][0]);
            s.pos.z(pz + offsets[i][1]);
            s.pos.y(Terrain.getTerrainHeight(forest, s.pos.x(), s.pos.z()));
            s.kind = kinds[i % kinds.length];
            s.interval = 7f + i * 1.5f;
            s.maxAlive = 2 + (i % 2);
            spawners.add(s);
        }
    }

    public static List<Spawner> defaultSurvivalSpawners(Terrain.ForestTerrain forest) {
        List<Spawner> list = new ArrayList<>();
        seedSpawnersNearPlayer(list, forest, 0, 0);
        return list;
    }
}
