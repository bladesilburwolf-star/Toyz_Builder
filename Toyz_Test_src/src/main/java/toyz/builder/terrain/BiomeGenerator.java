package toyz.builder.terrain;

public final class BiomeGenerator {
    private BiomeGenerator() {}

    public static void fillClimate(TerrainSample s, WorldConfig cfg) {
        float nx = s.x / cfg.size, nz = s.z / cfg.size;
        if (cfg.nether) {
            s.temperature = 0.85f + 0.15f * Noise.fractal(nx * 2.2f, nz * 2.2f, cfg.seed + 11, 3);
            s.moisture = 0.15f + 0.4f * Noise.fractal(nx * 1.8f + 4f, nz * 1.8f, cfg.seed + 22, 3);
            s.continentalness = Noise.fractal(nx * 1.2f - 2f, nz * 1.2f + 3f, cfg.seed + 33, 2);
            s.erosion = Noise.fractal(nx * 3f, nz * 3f, cfg.seed + 44, 2);
            return;
        }
        s.temperature = Noise.fractal(nx * 1.4f + 1.1f, nz * 1.4f - 0.7f, cfg.seed + 101, 4);
        s.moisture = Noise.fractal(nx * 1.5f - 2.3f, nz * 1.5f + 0.9f, cfg.seed + 202, 4);
        s.continentalness = Noise.fractal(nx * 0.9f, nz * 0.9f, cfg.seed + 303, 3);
        s.erosion = Noise.fractal(nx * 2.4f + 5f, nz * 2.4f - 3f, cfg.seed + 404, 3);
    }

    public static BiomeId pick(TerrainSample s, WorldConfig cfg) {
        if (cfg != null && cfg.nether) return pickNether(s);
        if (cfg != null && cfg.forceTheme != null) {
            switch (cfg.forceTheme) {
                case "FOREST":
                    if (s.inWater) return BiomeId.OCEAN;
                    return s.moisture > 0.55f ? BiomeId.FOREST : BiomeId.BIRCH;
                case "DESERT":
                    if (s.inWater) return BiomeId.OCEAN;
                    return s.erosion > 0.5f ? BiomeId.BADLANDS : BiomeId.DESERT;
                case "CORAL":
                    if (s.inWater || s.waterProximity > 0.3f) return BiomeId.CORAL_REEF;
                    return BiomeId.BEACH;
                case "SKY":
                    if (s.height > 8f) return BiomeId.ALPINE;
                    return BiomeId.HIGHLANDS;
                case "INDUSTRIAL":
                    return BiomeId.BADLANDS; // gray rock later tinted
                default: break;
            }
        }

        if (s.inWater && s.temperature < 0.28f) return BiomeId.FROZEN_LAKE;
        // Warm shallow water → coral reef
        if (s.inWater) {
            if (s.temperature > 0.52f && s.waterProximity > 0.25f
                    && s.moisture > 0.30f && s.slope < 0.55f) {
                return BiomeId.CORAL_REEF;
            }
            return BiomeId.OCEAN;
        }
        if (s.waterProximity > 0.55f && s.height < 2.5f) {
            if (s.moisture > 0.55f) return BiomeId.MANGROVE;
            return BiomeId.BEACH;
        }

        float t = s.temperature;
        float m = s.moisture;
        float e = s.height;
        float volc = s.continentalness;

        if (e > 12f) {
            if (t < 0.40f) return BiomeId.SNOW;
            if (m < 0.38f) return BiomeId.ALPINE;
            return BiomeId.HIGHLANDS;
        }
        if (t < 0.22f) return BiomeId.SNOW;
        if (e > 8f && t < 0.42f) return BiomeId.TAIGA;

        if (t > 0.62f && m < 0.35f) {
            if (e > 7f && s.erosion > 0.5f) return BiomeId.MESA;
            if (s.erosion > 0.55f) return BiomeId.BADLANDS;
            return BiomeId.DESERT;
        }
        if (t > 0.58f && m < 0.42f) return BiomeId.SAVANNA;
        if (t > 0.6f && m < 0.4f && volc > 0.78f && e > 5f) return BiomeId.VOLCANIC;

        if (m > 0.58f && e < 5.5f && t > 0.35f && t < 0.72f) return BiomeId.SWAMP;
        // Bamboo: warm + very wet lowlands
        if (t > 0.58f && m > 0.72f && e < 6f) return BiomeId.BAMBOO;
        // Jungle: hot + wet
        if (t > 0.62f && m > 0.58f) return BiomeId.JUNGLE;
        // Redwood: mild cool + moist mid elevation
        if (t > 0.32f && t < 0.55f && m > 0.55f && e > 4f && e < 11f
                && s.continentalness > 0.45f) return BiomeId.REDWOOD;
        // Dark forest: cool-mild + high moisture, low light (noise)
        if (t > 0.28f && t < 0.58f && m > 0.62f && e < 9f
                && Noise.fractal(s.x * 0.01f, s.z * 0.01f, 909, 2) > 0.55f)
            return BiomeId.DARK_FOREST;
        // Evergreen denser than taiga
        if (t < 0.38f && m > 0.45f && e > 3f) {
            if (m > 0.58f) return BiomeId.EVERGREEN;
            return BiomeId.TAIGA;
        }

        if (m > 0.55f && t > 0.45f && t < 0.65f) {
            if (m > 0.7f) return BiomeId.BIRCH;
            return BiomeId.FOREST;
        }
        if (m < 0.4f && t > 0.4f) return BiomeId.DRY_FOREST;
        if (m > 0.5f && t > 0.5f) return BiomeId.FLOWER_MEADOW;
        if (e > 5f) return BiomeId.HIGHLANDS;
        return BiomeId.MEADOW;
    }

    /** Compatibility when only sample is passed. */
    public static BiomeId pick(TerrainSample s) {
        return pick(s, null);
    }

    private static BiomeId pickNether(TerrainSample s) {
        float t = s.temperature;
        float m = s.moisture;
        float e = s.height;
        // Three biomes: wastes (default), crimson (moist), basalt (high/eroded)
        if (e > 12f || s.erosion > 0.62f) return BiomeId.NETHER_BASALT;
        if (m > 0.42f) return BiomeId.NETHER_CRIMSON;
        return BiomeId.NETHER_WASTES;
    }
}
