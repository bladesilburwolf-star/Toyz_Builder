package toyz.builder.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds structure-suitable sites and bridge crossings from terrain analysis.
 */
public final class LandmarkPlacement {

    public static List<StructureSite> findSites(TerrainGenerator.WorldData world, int maxSites) {
        List<StructureSite> out = new ArrayList<>();
        if (world == null || world.config == null || !world.config.structures) return out;

        WorldConfig cfg = world.config;
        float step = Math.max(cfg.sampleSpacing * 4f, 20f);
        List<StructureSite> candidates = new ArrayList<>();

        for (float z = -cfg.size * 0.4f; z <= cfg.size * 0.4f; z += step) {
            for (float x = -cfg.size * 0.4f; x <= cfg.size * 0.4f; x += step) {
                TerrainSample s = world.sample(x, z);
                if (s.inWater || s.riverChannel) continue;
                if (s.structureScore < 0.45f) continue;
                if (s.slope > 0.35f) continue;
                if (s.flatness < 0.55f) continue;

                StructureSite site = new StructureSite();
                site.x = x;
                site.z = z;
                site.y = s.height;
                site.terrainScore = s.structureScore;
                site.biome = s.biome;
                site.rotationY = Noise.hash2D((int) x, (int) z, cfg.seed) * 360f;
                // Kind bias by biome
                if (s.biome == BiomeId.DESERT || s.biome == BiomeId.BADLANDS || s.biome == BiomeId.MESA)
                    site.kind = "temple";
                else if (s.biome == BiomeId.FOREST || s.biome == BiomeId.TAIGA || s.biome == BiomeId.HIGHLANDS)
                    site.kind = "fort";
                else if (s.biome == BiomeId.MEADOW || s.biome == BiomeId.FLOWER_MEADOW)
                    site.kind = "village";
                else
                    site.kind = (Noise.hash2D((int) x + 3, (int) z, cfg.seed) > 0.5f) ? "fort" : "temple";
                candidates.add(site);
            }
        }

        // Greedy spacing
        candidates.sort((a, b) -> Float.compare(b.terrainScore, a.terrainScore));
        float minDist = cfg.minSpacingLike();
        for (StructureSite c : candidates) {
            if (out.size() >= maxSites) break;
            boolean ok = true;
            for (StructureSite e : out) {
                float dx = e.x - c.x, dz = e.z - c.z;
                if (dx * dx + dz * dz < minDist * minDist) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(c);
        }
        return out;
    }

    /**
     * Bridge integration point: river crossings with reasonable opposite banks.
     */
    public static List<StructureSite> findBridgeCrossings(TerrainGenerator.WorldData world, int max) {
        List<StructureSite> out = new ArrayList<>();
        if (world == null || world.water == null) return out;
        WorldConfig cfg = world.config;

        for (WaterGenerator.RiverPath river : world.water.rivers) {
            for (int i = 2; i < river.xs.length - 2; i += 3) {
                float x = river.xs[i], z = river.zs[i];
                // Bank samples perpendicular to segment
                float dx = river.xs[i + 1] - river.xs[i - 1];
                float dz = river.zs[i + 1] - river.zs[i - 1];
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                if (len < 1e-3f) continue;
                dx /= len;
                dz /= len;
                float px = -dz, pz = dx; // perpendicular

                float bank = river.width * 1.4f;
                TerrainSample a = world.sample(x + px * bank, z + pz * bank);
                TerrainSample b = world.sample(x - px * bank, z - pz * bank);
                if (a.inWater || b.inWater) continue;
                if (a.slope > 0.5f || b.slope > 0.5f) continue;
                float score = 1f - Math.abs(a.height - b.height) / Math.max(1f, cfg.heightScale);
                if (score < 0.4f) continue;

                StructureSite site = new StructureSite();
                site.x = x;
                site.z = z;
                site.y = Math.max(a.height, b.height);
                site.kind = "bridge";
                site.terrainScore = score;
                site.rotationY = (float) Math.toDegrees(Math.atan2(dx, dz)); // along river → bridge perpendicular later
                site.footprintWidth = river.width * 2f;
                site.footprintDepth = bank * 2f;
                site.biome = a.biome;
                out.add(site);
                if (out.size() >= max) return out;
            }
        }
        return out;
    }
}
