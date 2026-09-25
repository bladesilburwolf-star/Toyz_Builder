package toyz.builder.terrain;

/**
 * Candidate location for forts, temples, villages, bridges, ruins.
 */
public final class StructureSite {
    public float x, y, z;
    public float rotationY;
    public float footprintWidth = 12f;
    public float footprintDepth = 12f;
    public float terrainScore;
    public boolean reserved;
    public String kind = "generic"; // fort, temple, village, bridge, ruin
    public BiomeId biome;
}
