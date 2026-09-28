package com.toyzbuilder.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sparse biome trees for the overworld (not builder pieces).
 * Engine-independent positions; LWJGL only draws them.
 */
public final class TreeField {

    public enum Kind {
        OAK, PINE, BIRCH, SNOW
    }

    public static final class Instance {
        public final float x, y, z;
        public final float scale;
        public final float yaw;
        public final Kind kind;

        public Instance(float x, float y, float z, float scale, float yaw, Kind kind) {
            this.x = x; this.y = y; this.z = z;
            this.scale = scale; this.yaw = yaw; this.kind = kind;
        }
    }

    private final List<Instance> trees = new ArrayList<>();

    public List<Instance> getTrees() { return trees; }

    public void generate(WorldGenerator.Result terrain, int seed) {
        trees.clear();
        if (terrain == null) return;
        Random rng = new Random(seed ^ 0x7EE5L);
        float half = terrain.size * 0.5f;
        float step = 14f; // spacing — raise to reduce density / cost
        float water = terrain.settings.waterLevel + 0.8f;

        for (float z = -half + step; z < half - step; z += step) {
            for (float x = -half + step; x < half - step; x += step) {
                // jitter so grid is not obvious
                float jx = x + (rng.nextFloat() - 0.5f) * step * 0.7f;
                float jz = z + (rng.nextFloat() - 0.5f) * step * 0.7f;
                WorldGenerator.Biome bio = WorldGenerator.sampleBiome(terrain, jx, jz);
                Kind kind = kindFor(bio, rng);
                if (kind == null) continue;
                float h = WorldGenerator.sampleHeight(terrain, jx, jz);
                if (h < water) continue;
                // slope reject: sample neighbors
                float hx = WorldGenerator.sampleHeight(terrain, jx + 2f, jz);
                float hz = WorldGenerator.sampleHeight(terrain, jx, jz + 2f);
                if (Math.abs(hx - h) > 2.2f || Math.abs(hz - h) > 2.2f) continue;

                float scale = 0.85f + rng.nextFloat() * 0.55f;
                if (kind == Kind.PINE) scale *= 1.15f;
                float yaw = rng.nextFloat() * 360f;
                trees.add(new Instance(jx, h, jz, scale, yaw, kind));
            }
        }
        System.out.println("[TreeField] placed " + trees.size() + " trees (step=" + step + ")");
    }

    private static Kind kindFor(WorldGenerator.Biome bio, Random rng) {
        return switch (bio) {
            case FOREST -> rng.nextFloat() < 0.85f ? (rng.nextFloat() < 0.3f ? Kind.BIRCH : Kind.OAK) : null;
            case JUNGLE -> rng.nextFloat() < 0.9f ? Kind.OAK : null;
            case TAIGA -> rng.nextFloat() < 0.8f ? Kind.PINE : null;
            case SNOW, ALPINE -> rng.nextFloat() < 0.45f ? Kind.SNOW : null;
            case MEADOW, HIGHLANDS -> rng.nextFloat() < 0.18f ? Kind.OAK : null;
            case SWAMP -> rng.nextFloat() < 0.35f ? Kind.OAK : null;
            default -> null; // desert, ocean, beach, volcanic, etc.
        };
    }
}
