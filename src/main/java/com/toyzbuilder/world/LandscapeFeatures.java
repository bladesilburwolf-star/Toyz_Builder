package com.toyzbuilder.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Surface decoration layer — rocks, flora stubs, deadwood, minerals.
 * Dense enough to read biomes; distance-culled in Renderer.
 */
public final class LandscapeFeatures {

    public enum Kind {
        BOULDER_S, BOULDER_M, BOULDER_L,
        CRYSTAL, QUARTZ, MAGMA,
        // expanded props
        CACTUS, BUSH, REED, STUMP, DEAD_LOG,
        ICE_SPIKE, DRIFTWOOD, MUSHROOM_CAP, BONE, PILLAR, SALT_CHUNK
    }

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
        float water = terrain.settings.waterLevel + 0.4f;

        // Pass A: rocks / minerals (coarser grid)
        placePass(terrain, rng, half, water, 14f, true);
        // Pass B: flora / clutter (finer grid, biome gated)
        placePass(terrain, rng, half, water, 9f, false);

        System.out.println("[LandscapeFeatures] placed " + features.size() + " surface features");
    }

    private void placePass(WorldGenerator.Result terrain, Random rng,
                           float half, float water, float step, boolean rockPass) {
        for (float z = -half + step; z < half - step; z += step) {
            for (float x = -half + step; x < half - step; x += step) {
                float jx = x + (rng.nextFloat() - 0.5f) * step * 0.75f;
                float jz = z + (rng.nextFloat() - 0.5f) * step * 0.75f;
                float y = WorldGenerator.sampleHeight(terrain, jx, jz);
                if (y < water) continue;

                WorldGenerator.Biome b = WorldGenerator.sampleBiome(terrain, jx, jz);
                float slope = Math.abs(WorldGenerator.sampleHeight(terrain, jx + 2, jz) - y)
                        + Math.abs(WorldGenerator.sampleHeight(terrain, jx, jz + 2) - y);
                // Avoid placing upright props where their base would be
                // buried by a nearby face of the terrain mesh.
                if (slope > (rockPass ? 3.5f : 2.2f)) continue;

                Kind kind;
                float chance;
                if (rockPass) {
                    kind = rockKind(b, slope, rng);
                    chance = rockChance(b, slope);
                } else {
                    kind = floraKind(b, rng);
                    if (kind == null) continue;
                    chance = floraChance(b);
                }
                if (rng.nextFloat() > chance) continue;

                float scale = scaleFor(kind, rng);
                features.add(new Instance(jx, y, jz, scale, rng.nextFloat() * 360f, kind));
            }
        }
    }

    private static float rockChance(WorldGenerator.Biome b, float slope) {
        return switch (b) {
            case ALPINE, HIGHLANDS, BADLANDS, CRAG, CEDAR_HIGHLANDS -> slope < 3.5f ? 0.34f : 0.16f;
            case VOLCANIC, ASHLANDS, OBSIDIAN_WASTES -> 0.36f;
            case CRYSTAL_FIELDS -> 0.40f;
            case DESERT, SAVANNA, PRAIRIE, STEPPE, SALT_FLATS -> 0.14f;
            case SNOW, TAIGA, TUNDRA, GLACIER, FROZEN_FOREST -> 0.16f;
            case BEACH, PALM_COAST -> 0.06f;
            default -> 0.08f;
        };
    }

    private static Kind rockKind(WorldGenerator.Biome b, float slope, Random rng) {
        // mineral overrides
        if (b == WorldGenerator.Biome.CRYSTAL_FIELDS && rng.nextFloat() < 0.55f)
            return rng.nextBoolean() ? Kind.CRYSTAL : Kind.QUARTZ;
        if ((b == WorldGenerator.Biome.ALPINE || b == WorldGenerator.Biome.HIGHLANDS
                || b == WorldGenerator.Biome.CRAG) && rng.nextFloat() < 0.12f)
            return rng.nextBoolean() ? Kind.CRYSTAL : Kind.QUARTZ;
        if (b == WorldGenerator.Biome.VOLCANIC || b == WorldGenerator.Biome.ASHLANDS) {
            if (rng.nextFloat() < 0.5f) return Kind.MAGMA;
            return Kind.BOULDER_S;
        }
        if (b == WorldGenerator.Biome.OBSIDIAN_WASTES)
            return rng.nextFloat() < 0.4f ? Kind.PILLAR : Kind.BOULDER_M;
        if (b == WorldGenerator.Biome.SALT_FLATS)
            return Kind.SALT_CHUNK;
        if (b == WorldGenerator.Biome.GLACIER && rng.nextFloat() < 0.35f)
            return Kind.ICE_SPIKE;

        float r = rng.nextFloat();
        if (slope > 3.0f || r < 0.25f) return Kind.BOULDER_S;
        if (r < 0.75f) return Kind.BOULDER_M;
        return Kind.BOULDER_L;
    }

    private static float floraChance(WorldGenerator.Biome b) {
        return switch (b) {
            case DESERT, STEPPE -> 0.22f;
            case SAVANNA, PRAIRIE -> 0.18f;
            case MEADOW, FOREST, REDWOOD, DARK_FOREST, BAMBOO, CEDAR_HIGHLANDS -> 0.28f;
            case JUNGLE, MANGROVE, WILLOW_WETLAND -> 0.30f;
            case SWAMP, MARSH, FLOODPLAIN -> 0.32f;
            case MUSHROOM -> 0.35f;
            case BEACH, PALM_COAST, OASIS -> 0.16f;
            case TAIGA, FROZEN_FOREST -> 0.14f;
            case SNOW, TUNDRA, GLACIER -> 0.06f;
            default -> 0.05f;
        };
    }

    /** null = skip this cell for flora pass */
    private static Kind floraKind(WorldGenerator.Biome b, Random rng) {
        return switch (b) {
            case DESERT, STEPPE -> rng.nextFloat() < 0.7f ? Kind.CACTUS : Kind.BONE;
            case SAVANNA, PRAIRIE -> Kind.BUSH;
            case MEADOW, FOREST, REDWOOD, BAMBOO, CEDAR_HIGHLANDS ->
                    rng.nextFloat() < 0.55f ? Kind.BUSH : (rng.nextFloat() < 0.5f ? Kind.STUMP : Kind.DEAD_LOG);
            case DARK_FOREST -> rng.nextFloat() < 0.4f ? Kind.STUMP : Kind.DEAD_LOG;
            case JUNGLE, MANGROVE, WILLOW_WETLAND ->
                    rng.nextFloat() < 0.5f ? Kind.BUSH : Kind.REED;
            case SWAMP, MARSH, FLOODPLAIN -> Kind.REED;
            case MUSHROOM -> Kind.MUSHROOM_CAP;
            case BEACH, PALM_COAST -> Kind.DRIFTWOOD;
            case OASIS -> rng.nextFloat() < 0.5f ? Kind.BUSH : Kind.CACTUS;
            case TAIGA, FROZEN_FOREST -> rng.nextFloat() < 0.5f ? Kind.STUMP : Kind.DEAD_LOG;
            case SNOW, TUNDRA, GLACIER -> Kind.ICE_SPIKE;
            case SALT_FLATS -> Kind.SALT_CHUNK;
            case OBSIDIAN_WASTES, ASHLANDS -> Kind.BONE;
            default -> null;
        };
    }

    private static float scaleFor(Kind k, Random rng) {
        float s = 0.65f + rng.nextFloat() * 0.7f;
        return switch (k) {
            case BOULDER_L -> s * 1.35f;
            case BOULDER_M -> s * 1.05f;
            case CACTUS, ICE_SPIKE, PILLAR, REED -> s * 1.2f;
            case DEAD_LOG, DRIFTWOOD -> s * 1.1f;
            case MUSHROOM_CAP -> s * 0.9f;
            case BUSH, STUMP -> s * 0.85f;
            default -> s;
        };
    }
}
