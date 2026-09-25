package toyz.builder.terrain;

/**
 * Biome-aware decoration rules for GLB placement (trees, rocks, boulders).
 * Does not place pieces itself — returns density / variant hints for Terrain.java.
 */
public final class TerrainDecorator {

    public static final class Hint {
        public float treeDensity;   // 0–1
        public float rockDensity;
        public float bushDensity;
        public String preferredWood = "oak"; // maps to *logh.glb / *logv.glb
        public boolean volcanic;
        public boolean snowy;
    }

    private TerrainDecorator() {}

    public static Hint forBiome(BiomeId b) {
        Hint h = new Hint();
        if (b == null) b = BiomeId.MEADOW;
        switch (b) {
            case JUNGLE:
                h.treeDensity = 0.9f; h.bushDensity = 0.7f; h.preferredWood = "mahogany"; break;
            case FOREST:
                h.treeDensity = 0.75f; h.preferredWood = "oak"; break;
            case BIRCH:
                h.treeDensity = 0.7f; h.preferredWood = "birch"; break;
            case TAIGA:
                h.treeDensity = 0.65f; h.preferredWood = "pine"; h.snowy = true; break;
            case SNOW: case ALPINE:
                h.treeDensity = 0.25f; h.rockDensity = 0.4f; h.preferredWood = "pine"; h.snowy = true; break;
            case DESERT: case BADLANDS: case MESA:
                h.treeDensity = 0.05f; h.rockDensity = 0.35f; h.preferredWood = "cedar"; break;
            case SAVANNA:
                h.treeDensity = 0.2f; h.preferredWood = "teak"; break;
            case SWAMP: case MANGROVE:
                h.treeDensity = 0.45f; h.bushDensity = 0.6f; h.preferredWood = "walnut"; break;
            case VOLCANIC:
                h.treeDensity = 0.05f; h.rockDensity = 0.55f; h.volcanic = true; break;
            case FLOWER_MEADOW: case MEADOW:
                h.treeDensity = 0.15f; h.bushDensity = 0.5f; h.preferredWood = "oak"; break;
            case HIGHLANDS: case DRY_FOREST:
                h.treeDensity = 0.35f; h.rockDensity = 0.25f; h.preferredWood = "cedar"; break;
            case BEACH:
                h.treeDensity = 0.1f; h.preferredWood = "palm"; break;
            default:
                h.treeDensity = 0.3f; h.preferredWood = "oak"; break;
        }
        return h;
    }
}
