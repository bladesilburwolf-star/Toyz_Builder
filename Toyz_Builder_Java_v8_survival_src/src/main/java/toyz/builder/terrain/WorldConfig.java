package toyz.builder.terrain;

/**
 * World generation config — Terrain V3 mapgen presets (Minetest-inspired, Toyz interpretation).
 */
public final class WorldConfig {

    public enum MapgenPreset {
        V2, V3, V4, V5, V6, V7, FLAT, AMPLIFIED
    }

    public enum WorldType { FLAT, NORMAL, AMPLIFIED }

    public int seed = 0xC0FFEE;
    public WorldType type = WorldType.NORMAL;
    public MapgenPreset preset = MapgenPreset.V6;
    public boolean structures = true;
    public boolean skyIslands = false;
    /** Nether dimension (red sky, lava, 3 biomes). */
    public boolean nether = false;
    public float size = 400f;
    public float sampleSpacing = 4f;
    public float heightScale = 16f;
    public float meshSpacing = 4f;
    public float maxDepth = 24f;

    public WorldConfig() {}

    public WorldConfig(int seed, WorldType type, boolean structures) {
        this.seed = seed;
        this.type = type == null ? WorldType.NORMAL : type;
        this.structures = structures;
        syncPresetFromType();
    }

    public void syncPresetFromType() {
        if (type == WorldType.FLAT) {
            preset = MapgenPreset.FLAT;
            heightScale = 2f;
        } else if (type == WorldType.AMPLIFIED) {
            preset = MapgenPreset.AMPLIFIED;
            heightScale = 28f;
        }
        applyPresetTuning();
    }

    public void applyPresetTuning() {
        switch (preset) {
            case V2: heightScale = 10f; maxDepth = 8f; break;
            case V3: heightScale = 14f; maxDepth = 12f; break;
            case V4: heightScale = 16f; maxDepth = 16f; break;
            case V5: heightScale = 16f; maxDepth = 18f; break;
            case V6: heightScale = 18f; maxDepth = 22f; break;
            case V7: heightScale = 20f; maxDepth = 28f; break;
            case FLAT: heightScale = 2f; maxDepth = 4f; skyIslands = false; break;

            case AMPLIFIED: heightScale = 32f; maxDepth = 30f; break;
        }
        if (type == WorldType.AMPLIFIED && preset != MapgenPreset.FLAT)
            heightScale = Math.max(heightScale, 28f);
    }

    public static WorldConfig fromLegacy(int seed, toyz.builder.Terrain.WorldType wt, boolean structures) {
        WorldType t = WorldType.NORMAL;
        if (wt == toyz.builder.Terrain.WorldType.FLAT) t = WorldType.FLAT;
        else if (wt == toyz.builder.Terrain.WorldType.AMPLIFIED) t = WorldType.AMPLIFIED;
        WorldConfig c = new WorldConfig(seed, t, structures);
        c.preset = (t == WorldType.FLAT) ? MapgenPreset.FLAT
                : (t == WorldType.AMPLIFIED) ? MapgenPreset.AMPLIFIED : MapgenPreset.V6;
        if (wt == toyz.builder.Terrain.WorldType.NETHER) {
            c.nether = true;
            c.preset = MapgenPreset.V6;
        }
        c.applyPresetTuning();
        if (c.nether) c.applyNether();
        return c;
    }

    public float minSpacingLike() { return size * 0.12f; }

    public float landformStrength() {
        switch (preset) {
            case V2: return 0.55f;
            case V3: return 0.75f;
            case V4: return 0.9f;
            case V5: return 0.85f;
            case V6: return 1.0f;
            case V7: return 1.15f;
            case FLAT: return 0.08f;
            case AMPLIFIED: return 1.4f;
            default: return 1f;
        }
    }

    public void applyNether() {
        if (!nether) return;
        heightScale = 22f;
        maxDepth = 16f;
        skyIslands = false;
        // lava-like water level sits higher relative to floor
    }

    public float waterStrength() {
        switch (preset) {
            case V4: case V7: return 1.25f;
            case V6: return 1.1f;
            case FLAT: return 0.3f;
            default: return 1f;
        }
    }
}
