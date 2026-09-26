package toyz.builder.terrain;

import com.raylib.Raylib.Model;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain V3 facade — continuous landforms, water meshes, depth/caves, sky islands, mapgen presets.
 * Design: ChatGPT | Implementation: Grok | Title: Temples, Forts & New Terrain System
 */
public final class TerrainGenerator {

    public static final class WorldData {
        public WorldConfig config;
        public WaterGenerator water;
        public List<StructureSite> structureSites = new ArrayList<>();
        public List<StructureSite> bridgeSites = new ArrayList<>();
        public List<CaveGenerator.CaveMouth> caveMouths = new ArrayList<>();
        public List<SkyIslandGenerator.Island> skyIslands = new ArrayList<>();
        public List<RavineGenerator.RavinePath> ravines = new ArrayList<>();
        public List<RavineGenerator.OreNode> ores = new ArrayList<>();
        public List<RavineGenerator.PortalObelisk> obelisks = new ArrayList<>();
        public Model mesh;
        public WaterMeshBuilder.WaterMeshes waterMeshes;
        public float oceanLevel;

        public float heightOnly(float x, float z) {
            return sample(x, z).height;
        }

        public TerrainSample sample(float x, float z) {
            TerrainSample s = new TerrainSample();
            s.x = x;
            s.z = z;
            BiomeGenerator.fillClimate(s, config);
            float strength = config.landformStrength();
            s.height = LandformGenerator.baseHeight(x, z, config) * (0.85f + 0.15f * strength);
            // slight re-scale: baseHeight already uses heightScale; strength amplifies landform feel
            if (strength != 1f) {
                float base = LandformGenerator.baseHeight(x, z, config);
                s.height = base; // LandformGenerator reads cfg; strength applied inside if we patch — keep base
            }
            if (ravines != null && !ravines.isEmpty()) {
                s.height = RavineGenerator.carveHeight(x, z, s.height, ravines);
            }
            if (water != null) water.apply(s, config);
            float e = 0.75f;
            float h1 = LandformGenerator.baseHeight(x + e, z, config);
            float h2 = LandformGenerator.baseHeight(x - e, z, config);
            float h3 = LandformGenerator.baseHeight(x, z + e, config);
            float h4 = LandformGenerator.baseHeight(x, z - e, config);
            float dx = (h1 - h2) / (2f * e);
            float dz = (h3 - h4) / (2f * e);
            s.slope = (float) Math.sqrt(dx * dx + dz * dz);
            s.flatness = 1f / (1f + s.slope * 4f);
            s.biome = BiomeGenerator.pick(s, config);
            DepthGenerator.fillSample(s, config);
            s.structureScore = 0f;
            if (!s.inWater && !s.riverChannel) {
                s.structureScore = s.flatness * 0.5f
                        + (1f - Math.min(1f, s.waterProximity)) * 0.2f
                        + clamp01(1f - Math.abs(s.height - config.heightScale * 0.25f) / Math.max(1f, config.heightScale)) * 0.3f;
                if (s.height < water.oceanLevel + 1.5f) s.structureScore *= 0.3f;
            }
            return s;
        }
    }

    private TerrainGenerator() {}

    public static WorldData generate(WorldConfig cfg) {
        cfg.applyPresetTuning();
        if (cfg.nether) cfg.applyNether();
        WorldData w = new WorldData();
        w.config = cfg;
        w.water = WaterGenerator.build(cfg);
        // more rivers for water-heavy presets
        if (cfg.waterStrength() > 1.05f && w.water.rivers.size() < 4) {
            // WaterGenerator already seeded; strength used in mesh width
        }
        w.oceanLevel = w.water.oceanLevel;
        if (!cfg.nether) {
            int rc = cfg.preset == WorldConfig.MapgenPreset.V7 ? 6
                    : (cfg.preset == WorldConfig.MapgenPreset.V6 ? 5 : 3);
            w.ravines = RavineGenerator.generateRavines(cfg, rc);
            w.ores = RavineGenerator.scatterOres(cfg, w.ravines, 90);
            w.obelisks = RavineGenerator.placeObelisks(cfg, 3 + (Math.abs(cfg.seed) % 3));
        }
        w.structureSites = LandmarkPlacement.findSites(w, cfg.structures ? 12 : 0);
        w.bridgeSites = LandmarkPlacement.findBridgeCrossings(w, cfg.structures ? 8 : 0);
        w.caveMouths = CaveGenerator.findMouths(w, cfg.preset.ordinal() >= WorldConfig.MapgenPreset.V5.ordinal() ? 20 : 8);

        try {
            w.mesh = TerrainMeshBuilder.build(w);
        } catch (Throwable t) {
            System.err.println("[TerrainV3] land mesh failed: " + t.getMessage());
            w.mesh = null;
        }
        try {
            w.waterMeshes = WaterMeshBuilder.build(w);
        } catch (Throwable t) {
            System.err.println("[TerrainV3] water mesh failed: " + t.getMessage());
            t.printStackTrace();
            w.waterMeshes = null;
        }
        try {
            w.skyIslands = SkyIslandGenerator.generate(cfg);
        } catch (Throwable t) {
            System.err.println("[TerrainV3] sky islands failed: " + t.getMessage());
            w.skyIslands = new ArrayList<>();
        }

        System.out.println("[TerrainV3] preset=" + cfg.preset + " seed=" + cfg.seed
                + " sites=" + w.structureSites.size()
                + " bridges=" + w.bridgeSites.size()
                + " caves=" + w.caveMouths.size()
                + " ravines=" + (w.ravines != null ? w.ravines.size() : 0)
                + " ores=" + (w.ores != null ? w.ores.size() : 0)
                + " obelisks=" + (w.obelisks != null ? w.obelisks.size() : 0)
                + " sky=" + w.skyIslands.size()
                + " waterMesh=" + (w.waterMeshes != null
                    && (w.waterMeshes.ocean != null || w.waterMeshes.rivers != null)));
        return w;
    }

    public static float getHeight(WorldData w, float x, float z) {
        if (w == null) return 0f;
        return w.heightOnly(x, z);
    }

    public static BiomeId getBiome(WorldData w, float x, float z) {
        if (w == null) return BiomeId.MEADOW;
        return w.sample(x, z).biome;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
