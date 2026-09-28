package com.toyzbuilder.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lightweight surface decoration layer. These are world features, not builder pieces.
 * Positions are deterministic and terrain-aware; rendering remains in the LWJGL backend.
 */
public final class LandscapeFeatures {

    public enum Kind { BOULDER_S, BOULDER_M, BOULDER_L, CRYSTAL, QUARTZ, MAGMA }

    public static final class Instance {
        public final float x, y, z, scale, yaw;
        public final Kind kind;
        public Instance(float x, float y, float z, float scale, float yaw, Kind kind) {
            this.x = x; this.y = y; this.z = z; this.scale = scale; this.yaw = yaw; this.kind = kind;
        }
    }

    private final List<Instance> features = new ArrayList<>();
    public List<Instance> getFeatures() { return features; }

    public void generate(WorldGenerator.Result terrain, int seed) {
        features.clear();
        if (terrain == null) return;

        Random rng = new Random(seed ^ 0x4C414E44L);
        float half = terrain.size * 0.5f;
        float step = 18f;
        float water = terrain.settings.waterLevel + 0.5f;

        for (float z = -half + step; z < half - step; z += step) {
            for (float x = -half + step; x < half - step; x += step) {
                float jx = x + (rng.nextFloat() - .5f) * step * .8f;
                float jz = z + (rng.nextFloat() - .5f) * step * .8f;
                float y = WorldGenerator.sampleHeight(terrain, jx, jz);
                if (y < water) continue;

                WorldGenerator.Biome b = WorldGenerator.sampleBiome(terrain, jx, jz);
                float slope = Math.abs(WorldGenerator.sampleHeight(terrain, jx + 2, jz) - y)
                        + Math.abs(WorldGenerator.sampleHeight(terrain, jx, jz + 2) - y);

                float chance;
                Kind kind;
                switch (b) {
                    case ALPINE, HIGHLANDS, BADLANDS -> {
                        chance = slope < 3.5f ? .28f : .12f;
                        kind = rng.nextFloat() < .72f ? Kind.BOULDER_M : Kind.BOULDER_L;
                    }
                    case VOLCANIC -> {
                        chance = .30f;
                        kind = rng.nextFloat() < .55f ? Kind.MAGMA : Kind.BOULDER_S;
                    }
                    case DESERT, SAVANNA -> {
                        chance = .10f;
                        kind = rng.nextFloat() < .8f ? Kind.BOULDER_S : Kind.BOULDER_M;
                    }
                    case SNOW, TAIGA -> {
                        chance = .12f;
                        kind = Kind.BOULDER_S;
                    }
                    case MEADOW, FOREST, JUNGLE, SWAMP -> {
                        chance = .055f;
                        kind = Kind.BOULDER_S;
                    }
                    case BEACH -> {
                        chance = .025f;
                        kind = Kind.BOULDER_S;
                    }
                    default -> {
                        chance = .0f;
                        kind = Kind.BOULDER_S;
                    }
                }

                if (rng.nextFloat() > chance) continue;

                // Rare mineral outcrops make highland/volcanic terrain less visually uniform.
                if ((b == WorldGenerator.Biome.ALPINE || b == WorldGenerator.Biome.HIGHLANDS)
                        && rng.nextFloat() < .08f) {
                    kind = rng.nextBoolean() ? Kind.CRYSTAL : Kind.QUARTZ;
                }

                float scale = .65f + rng.nextFloat() * .75f;
                if (kind == Kind.BOULDER_L) scale *= 1.25f;
                features.add(new Instance(jx, y, jz, scale, rng.nextFloat() * 360f, kind));
            }
        }
        System.out.println("[LandscapeFeatures] placed " + features.size() + " surface features");
    }
}
