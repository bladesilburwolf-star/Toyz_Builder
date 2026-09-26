package toyz.builder.terrain;

import java.util.ArrayList;
import java.util.List;

/** Cave mouth / tunnel markers derived from depth field (not fake sinkhole cubes). */
public final class CaveGenerator {

    public static final class CaveMouth {
        public float x, y, z;
        public float radius;
        public float depth;
    }

    private CaveGenerator() {}

    public static List<CaveMouth> findMouths(TerrainGenerator.WorldData world, int max) {
        List<CaveMouth> out = new ArrayList<>();
        if (world == null || world.config == null) return out;
        WorldConfig cfg = world.config;
        if (cfg.preset == WorldConfig.MapgenPreset.FLAT || cfg.preset == WorldConfig.MapgenPreset.V2)
            return out;
        float step = Math.max(12f, cfg.sampleSpacing * 3f);
        for (float z = -cfg.size * 0.4f; z <= cfg.size * 0.4f; z += step) {
            for (float x = -cfg.size * 0.4f; x <= cfg.size * 0.4f; x += step) {
                TerrainSample s = world.sample(x, z);
                if (s.inWater || !s.hasCave) continue;
                if (s.slope < 0.25f) continue; // prefer hillsides for mouths
                CaveMouth m = new CaveMouth();
                m.x = x;
                m.z = z;
                m.y = s.height;
                m.radius = 1.2f + s.caveOpenness * 2.5f;
                m.depth = Math.min(s.solidDepth * 0.6f, cfg.maxDepth * 0.5f);
                out.add(m);
                if (out.size() >= max) return out;
            }
        }
        return out;
    }
}
