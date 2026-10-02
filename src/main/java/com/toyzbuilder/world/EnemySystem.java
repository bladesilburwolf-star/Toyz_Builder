package com.toyzbuilder.world;

import com.toyzbuilder.engine.Model;
import com.toyzbuilder.engine.PlayerController;
import com.toyzbuilder.engine.PrimitiveMeshes;
import com.toyzbuilder.engine.Renderer;
import com.toyzbuilder.engine.Survival;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Mobs + iron-cage spawners. Uses Morrowind test-dummy GLBs when present
 * under assets/models/ or models/ (unit cube fallback).
 */
public final class EnemySystem {

    public enum MobType {
        // legacy cubes (still valid spawn pool)
        SLIME(0.35f, 0.85f, 0.35f, 8f, 0.9f, 3.2f, null, 0.9f),
        SKELETON(0.85f, 0.85f, 0.75f, 12f, 1.6f, 2.8f, null, 0.55f),
        BAT(0.45f, 0.35f, 0.55f, 6f, 0.7f, 4.5f, null, 0.5f),
        // Morrowind test dummies
        RAT(0.55f, 0.45f, 0.35f, 6f, 0.55f, 3.8f, "rat1.glb", 0.7f),
        WORM(0.45f, 0.55f, 0.35f, 10f, 0.7f, 2.6f, "worm1.glb", 1.1f),
        ZOMBIE(0.35f, 0.45f, 0.30f, 16f, 1.7f, 2.2f, "zombie1.glb", 1.0f),
        GHOST(0.70f, 0.85f, 0.95f, 12f, 1.6f, 3.0f, "ghost1.glb", 1.0f),
        GOLEM(0.50f, 0.48f, 0.45f, 28f, 2.2f, 1.6f, "golem1.glb", 1.4f);

        public final float r, g, b, hp, height, speed;
        /** Relative path under assets/models/ — null = colored cube. */
        public final String modelPath;
        /** Uniform scale applied to the GLB. */
        public final float modelScale;

        MobType(float r, float g, float b, float hp, float height, float speed,
                String modelPath, float modelScale) {
            this.r = r; this.g = g; this.b = b;
            this.hp = hp; this.height = height; this.speed = speed;
            this.modelPath = modelPath;
            this.modelScale = modelScale;
        }

        /** Weighted dungeon spawn pick (favors Morrowind dummies). */
        public static MobType randomDungeon(Random rng) {
            float u = rng.nextFloat();
            if (u < 0.22f) return RAT;
            if (u < 0.40f) return WORM;
            if (u < 0.58f) return ZOMBIE;
            if (u < 0.72f) return GHOST;
            if (u < 0.82f) return GOLEM;
            if (u < 0.90f) return SLIME;
            if (u < 0.96f) return SKELETON;
            return BAT;
        }
    }

    public static final class Spawner {
        public final float x, y, z;
        public final MobType type;
        public final int maxAlive;
        public int alive;
        public float cooldown;
        public Spawner(float x, float y, float z, MobType type, int maxAlive) {
            this.x = x; this.y = y; this.z = z;
            this.type = type; this.maxAlive = maxAlive;
        }
    }

    public static final class Mob {
        public float x, y, z, yaw;
        public float hp;
        public final MobType type;
        public final Spawner home;
        public float hitCooldown;
        public Mob(float x, float y, float z, MobType type, Spawner home) {
            this.x = x; this.y = y; this.z = z;
            this.type = type; this.home = home;
            this.hp = type.hp;
        }
    }

    private final List<Spawner> spawners = new ArrayList<>();
    private final List<Mob> mobs = new ArrayList<>();
    private final Random rng = new Random();

    public void clear() {
        spawners.clear();
        mobs.clear();
    }

    public void addSpawner(float x, float y, float z, MobType type, int maxAlive) {
        spawners.add(new Spawner(x, y, z, type, maxAlive));
    }

    public List<Mob> getMobs() { return mobs; }
    public List<Spawner> getSpawners() { return spawners; }

    public void update(float dt, PlayerController player, Survival survival,
                       WorldGenerator.Result terrain) {
        float px = player.getPlayerPos().x;
        float py = player.getPlayerPos().y;
        float pz = player.getPlayerPos().z;

        for (Spawner s : spawners) {
            s.cooldown -= dt;
            if (s.alive >= s.maxAlive) continue;
            if (s.cooldown > 0) continue;
            float dx = s.x - px, dz = s.z - pz;
            if (dx * dx + dz * dz > 80f * 80f) continue;
            float mx = s.x + (rng.nextFloat() - 0.5f) * 2.5f;
            float mz = s.z + (rng.nextFloat() - 0.5f) * 2.5f;
            float my = s.y + 0.2f;
            if (terrain != null) {
                my = Math.max(my, WorldGenerator.sampleHeight(terrain, mx, mz) + 0.1f);
            }
            Mob m = new Mob(mx, my, mz, s.type, s);
            mobs.add(m);
            s.alive++;
            s.cooldown = 4f + rng.nextFloat() * 4f;
        }

        Iterator<Mob> it = mobs.iterator();
        while (it.hasNext()) {
            Mob m = it.next();
            m.hitCooldown -= dt;
            float dx = px - m.x, dz = pz - m.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.15f && dist < 28f) {
                float inv = 1f / dist;
                float sp = m.type.speed * dt;
                m.x += dx * inv * sp;
                m.z += dz * inv * sp;
                m.yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            }
            if (terrain != null && py > -50f) {
                float gh = WorldGenerator.sampleHeight(terrain, m.x, m.z);
                if (m.type == MobType.BAT || m.type == MobType.GHOST) {
                    m.y = gh + 2.2f + (float) Math.sin(m.x * 0.3f + m.z) * 0.4f;
                } else {
                    m.y = gh + 0.05f;
                }
            }
            if (survival != null && survival.isSurvival() && dist < 1.45f && m.hitCooldown <= 0) {
                float dmg = switch (m.type) {
                    case GOLEM -> 3f;
                    case ZOMBIE, SKELETON -> 2f;
                    default -> 1f;
                };
                survival.damage(dmg);
                m.hitCooldown = 1.0f;
            }
            if (m.hp <= 0) {
                if (m.home != null) m.home.alive = Math.max(0, m.home.alive - 1);
                it.remove();
            }
        }
    }

    public boolean tryPlayerAttack(float px, float py, float pz, float yawDeg) {
        float rad = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.cos(rad);
        float fz = (float) Math.sin(rad);
        for (Mob m : mobs) {
            float dx = m.x - px, dz = m.z - pz;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > 3.4f) continue;
            float dot = (dx * fx + dz * fz) / Math.max(0.01f, dist);
            if (dot < 0.35f) continue;
            m.hp -= 4f;
            m.x += fx * 0.6f;
            m.z += fz * 0.6f;
            return true;
        }
        return false;
    }

    public void render(Renderer renderer) {
        var cube = PrimitiveMeshes.uvCube();
        for (Mob m : mobs) {
            if (m.type.modelPath != null) {
                Model mdl = Model.load(m.type.modelPath);
                float s = m.type.modelScale;
                float h = m.type.height;
                // Tint-only; Morrowind test dummies may lack game textures
                renderer.renderModel(
                        mdl,
                        m.x, m.y + h * 0.15f, m.z,
                        s, s, s, m.yaw,
                        m.type.r, m.type.g, m.type.b, 1f);
            } else {
                float h = m.type.height;
                float w = m.type == MobType.SLIME ? 0.9f : 0.55f;
                renderer.renderMesh(cube, m.x, m.y + h * 0.5f, m.z,
                        w, h, w, m.type.r, m.type.g, m.type.b, 1f);
            }
        }
    }
}
