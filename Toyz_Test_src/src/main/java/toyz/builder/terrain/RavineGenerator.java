package toyz.builder.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Large deterministic ravines — 3D cuts via height carving + wall markers.
 * Not sinkhole cubes. Seed salts: RAVI / CAVE / ORES.
 */
public final class RavineGenerator {

    public static final int SALT_RAVI = 0x52415649;
    public static final int SALT_CAVE = 0x43415645;
    public static final int SALT_ORES = 0x4F524553;

    public static final class RavinePath {
        public float[] xs, zs;
        public float width;
        public float depth;
        public boolean enormous;
    }

    public static final class OreNode {
        public float x, y, z;
        public UndergroundMaterial mat;
        public float scale;
    }

    public static final class PortalObelisk {
        public float x, y, z;
        public int variant; // 0 single, 1 triple gate, 2 ruined, 3 shrine
        public boolean active = true;
    }

    private RavineGenerator() {}

    public static List<RavinePath> generateRavines(WorldConfig cfg, int count) {
        List<RavinePath> out = new ArrayList<>();
        if (cfg == null || cfg.nether) return out;
        int seed = cfg.seed + SALT_RAVI;
        int n = Math.max(2, count);
        for (int i = 0; i < n; i++) {
            RavinePath r = new RavinePath();
            r.enormous = Noise.hash2D(i, 9, seed) > 0.88f;
            r.width = r.enormous ? 14f + Noise.hash2D(i, 1, seed) * 12f
                    : 5f + Noise.hash2D(i, 1, seed) * 8f;
            r.depth = r.enormous ? 14f + Noise.hash2D(i, 2, seed) * 10f
                    : 6f + Noise.hash2D(i, 2, seed) * 8f;
            int segs = r.enormous ? 28 : 14 + (int)(Noise.hash2D(i, 3, seed) * 10);
            r.xs = new float[segs];
            r.zs = new float[segs];
            float ang = Noise.hash2D(i, 4, seed) * 6.2831853f;
            // Cap placement span so huge Phase B worlds still get local ravines
            float span = Math.min(cfg.size * 0.7f, 900f);
            float x = (Noise.hash2D(i, 5, seed) - 0.5f) * span;
            float z = (Noise.hash2D(i, 6, seed) - 0.5f) * span;
            float step = r.enormous ? 9f : 7f;
            for (int s = 0; s < segs; s++) {
                r.xs[s] = x;
                r.zs[s] = z;
                ang += (Noise.hash2D(i * 31 + s, 7, seed) - 0.5f) * 0.9f;
                x += (float) Math.cos(ang) * step;
                z += (float) Math.sin(ang) * step;
            }
            out.add(r);
        }
        System.out.println("[Ravine] paths=" + out.size());
        return out;
    }

    /** Carve surface height along ravine centerlines. */
    public static float carveHeight(float x, float z, float baseH, List<RavinePath> ravines) {
        if (ravines == null || ravines.isEmpty()) return baseH;
        float best = 0f;
        for (RavinePath r : ravines) {
            if (r.xs == null) continue;
            for (int i = 0; i < r.xs.length; i++) {
                float dx = x - r.xs[i], dz = z - r.zs[i];
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                float half = r.width * 0.5f;
                if (d >= half) continue;
                // U-shaped carve
                float t = 1f - d / half;
                t = t * t * (3f - 2f * t);
                float carve = r.depth * t;
                if (carve > best) best = carve;
            }
        }
        return baseH - best;
    }

    public static List<OreNode> scatterOres(WorldConfig cfg, List<RavinePath> ravines, int max) {
        List<OreNode> out = new ArrayList<>();
        if (cfg == null) return out;
        int seed = cfg.seed + SALT_ORES;
        int n = Math.min(max, 80);
        for (int i = 0; i < n; i++) {
            OreNode o = new OreNode();
            if (ravines != null && !ravines.isEmpty() && Noise.hash2D(i, 0, seed) > 0.35f) {
                RavinePath r = ravines.get(i % ravines.size());
                int si = (int)(Noise.hash2D(i, 1, seed) * (r.xs.length - 1));
                float ang = Noise.hash2D(i, 2, seed) * 6.28f;
                float rad = r.width * (0.2f + Noise.hash2D(i, 3, seed) * 0.4f);
                o.x = r.xs[si] + (float) Math.cos(ang) * rad;
                o.z = r.zs[si] + (float) Math.sin(ang) * rad;
                o.y = -r.depth * (0.3f + Noise.hash2D(i, 4, seed) * 0.5f); // relative; world y set later
            } else {
                o.x = (Noise.hash2D(i, 5, seed) - 0.5f) * cfg.size * 0.75f;
                o.z = (Noise.hash2D(i, 6, seed) - 0.5f) * cfg.size * 0.75f;
                o.y = -(2f + Noise.hash2D(i, 7, seed) * cfg.maxDepth * 0.6f);
            }
            float roll = Noise.hash2D(i, 8, seed);
            if (roll < 0.22f) o.mat = UndergroundMaterial.COAL;
            else if (roll < 0.40f) o.mat = UndergroundMaterial.IRON;
            else if (roll < 0.52f) o.mat = UndergroundMaterial.COPPER;
            else if (roll < 0.62f) o.mat = UndergroundMaterial.GOLD;
            else if (roll < 0.72f) o.mat = UndergroundMaterial.REDSTONE;
            else if (roll < 0.80f) o.mat = UndergroundMaterial.DIAMOND;
            else if (roll < 0.88f) o.mat = UndergroundMaterial.OBSIDIAN;
            else if (roll < 0.94f) o.mat = UndergroundMaterial.CRYSTAL;
            else o.mat = UndergroundMaterial.DEEPSLATE;
            o.scale = 0.5f + Noise.hash2D(i, 9, seed) * 1.2f;
            out.add(o);
        }
        return out;
    }

    public static List<PortalObelisk> placeObelisks(WorldConfig cfg, int count) {
        List<PortalObelisk> out = new ArrayList<>();
        if (cfg == null || cfg.nether) return out;
        int seed = cfg.seed + 0x4F42454C; // OBEL
        for (int i = 0; i < count; i++) {
            PortalObelisk p = new PortalObelisk();
            p.x = (Noise.hash2D(i, 1, seed) - 0.5f) * cfg.size * 0.65f;
            p.z = (Noise.hash2D(i, 2, seed) - 0.5f) * cfg.size * 0.65f;
            // keep away from origin spawn a bit
            if (p.x * p.x + p.z * p.z < 35f * 35f) {
                p.x += 50f;
                p.z += 40f;
            }
            p.variant = (int)(Noise.hash2D(i, 3, seed) * 4f) % 4;
            p.active = true;
            out.add(p);
        }
        System.out.println("[Obelisk] portals=" + out.size());
        return out;
    }
}
