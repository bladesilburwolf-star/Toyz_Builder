package toyz.builder.terrain;

/**
 * Climate fields → biome id with soft transitions (no hard borders).
 */
public final class BiomeGenerator {
    private BiomeGenerator() {}

    public static void fillClimate(TerrainSample s, WorldConfig cfg) {
        float size = cfg.size;
        float nx = s.x / size;
        float nz = s.z / size;
        int seed = cfg.seed;

        s.continentalness = Noise.fractal(nx * 1.1f + 3f, nz * 1.1f - 2f, seed + 11, 4);
        s.temperature = Noise.fractal(nx * 0.9f + 31f, nz * 0.9f - 12f, seed + 100, 4);
        // latitude-ish: colder toward +Z edge
        s.temperature = clamp01(s.temperature * 0.75f + (1f - Math.abs(nz)) * 0.25f);
        s.moisture = Noise.fractal(nx * 0.8f - 5f, nz * 0.8f + 9f, seed + 555, 4);
        s.erosion = Noise.fractal(nx * 2.2f + 17f, nz * 2.2f - 9f, seed + 222, 3);
    }

    public static BiomeId pick(TerrainSample s) {
        if (s.inWater && s.temperature < 0.28f) return BiomeId.FROZEN_LAKE;
        if (s.inWater) return BiomeId.OCEAN;
        if (s.waterProximity > 0.55f && s.height < 2.5f) {
            if (s.moisture > 0.55f) return BiomeId.MANGROVE;
            return BiomeId.BEACH;
        }

        float t = s.temperature;
        float m = s.moisture;
        float e = s.height;
        float volc = s.continentalness; // reused nuance

        // High elevation
        if (e > 14f) {
            if (t < 0.35f) return BiomeId.SNOW;
            if (m < 0.35f) return BiomeId.ALPINE;
            return BiomeId.HIGHLANDS;
        }
        if (e > 10f && t < 0.4f) return BiomeId.TAIGA;

        // Hot dry
        if (t > 0.72f && m < 0.28f) {
            if (e > 6f && s.erosion > 0.55f) return BiomeId.MESA;
            if (s.erosion > 0.6f) return BiomeId.BADLANDS;
            return BiomeId.DESERT;
        }
        if (t > 0.68f && m < 0.45f) return BiomeId.SAVANNA;

        // Volcanic pockets
        if (t > 0.6f && m < 0.4f && volc > 0.78f && e > 5f) return BiomeId.VOLCANIC;

        // Wet low
        if (m > 0.7f && e < 4f) return BiomeId.SWAMP;
        if (t > 0.65f && m > 0.6f) return BiomeId.JUNGLE;

        // Temperate
        if (m > 0.55f && t > 0.45f && t < 0.65f) {
            if (m > 0.7f) return BiomeId.BIRCH;
            return BiomeId.FOREST;
        }
        if (m < 0.4f && t > 0.4f) return BiomeId.DRY_FOREST;
        if (m > 0.5f && t > 0.5f) return BiomeId.FLOWER_MEADOW;
        if (e > 5f) return BiomeId.HIGHLANDS;
        return BiomeId.MEADOW;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
