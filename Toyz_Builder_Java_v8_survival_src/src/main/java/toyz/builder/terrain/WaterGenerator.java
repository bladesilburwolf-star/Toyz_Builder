package toyz.builder.terrain;

/**
 * Oceans, lakes, rivers that carve channels into the height field.
 * River centerlines stored for later bridge crossing candidates.
 */
public final class WaterGenerator {

    public static final class RiverPath {
        public float[] xs;
        public float[] zs;
        public float width;
    }

    public final java.util.List<RiverPath> rivers = new java.util.ArrayList<>();
    public float oceanLevel;

    private WaterGenerator() {}

    public static WaterGenerator build(WorldConfig cfg) {
        WaterGenerator w = new WaterGenerator();
        w.oceanLevel = cfg.heightScale * -0.28f;
        if (cfg.type == WorldConfig.WorldType.FLAT) {
            w.oceanLevel = -0.5f;
            return w;
        }
        // Deterministic meandering rivers across the map
        int count = 2 + (Math.abs(cfg.seed) % 3);
        for (int r = 0; r < count; r++) {
            RiverPath path = new RiverPath();
            int n = 48;
            path.xs = new float[n];
            path.zs = new float[n];
            path.width = 3.5f + Noise.hash2D(r, 3, cfg.seed) * 4f;
            float z0 = -cfg.size * 0.45f;
            float z1 = cfg.size * 0.45f;
            float xBase = (Noise.hash2D(r * 17, 9, cfg.seed) - 0.5f) * cfg.size * 0.7f;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                float z = z0 + (z1 - z0) * t;
                float meander = (Noise.fractal(z * 0.02f + r * 3f, r * 1.7f, cfg.seed + 777 + r, 3) - 0.5f)
                        * cfg.size * 0.22f;
                path.xs[i] = xBase + meander;
                path.zs[i] = z;
            }
            w.rivers.add(path);
        }
        return w;
    }

    /** Distance to nearest river polyline (world units). */
    public float riverDistance(float x, float z) {
        float best = 1e9f;
        for (RiverPath r : rivers) {
            for (int i = 0; i < r.xs.length - 1; i++) {
                float d = distToSegment(x, z, r.xs[i], r.zs[i], r.xs[i + 1], r.zs[i + 1]);
                if (d < best) best = d;
            }
        }
        return best;
    }

    public float riverWidthAt(float x, float z) {
        float best = 1e9f;
        float w = 4f;
        for (RiverPath r : rivers) {
            for (int i = 0; i < r.xs.length - 1; i++) {
                float d = distToSegment(x, z, r.xs[i], r.zs[i], r.xs[i + 1], r.zs[i + 1]);
                if (d < best) {
                    best = d;
                    w = r.width;
                }
            }
        }
        return w;
    }

    /** Carve river bed into height; set water flags on sample. */
    public void apply(TerrainSample s, WorldConfig cfg) {
        float rd = riverDistance(s.x, s.z);
        float rw = riverWidthAt(s.x, s.z);
        s.riverProximity = clamp01(1f - rd / (rw * 3f));
        s.waterDistance = rd;

        if (rd < rw) {
            // carve channel
            float carve = (1f - rd / rw);
            carve = carve * carve;
            float bed = oceanLevel - cfg.heightScale * 0.04f - carve * cfg.heightScale * 0.08f;
            if (s.height > bed) s.height = bed;
            s.riverChannel = rd < rw * 0.55f;
            s.inWater = s.height <= oceanLevel + 0.4f || s.riverChannel;
        }

        if (s.height <= oceanLevel) {
            s.inWater = true;
            s.waterProximity = 1f;
        } else {
            float shore = Math.max(0f, 1f - (s.height - oceanLevel) / (cfg.heightScale * 0.15f));
            s.waterProximity = Math.max(s.waterProximity, shore * 0.85f);
            s.waterProximity = Math.max(s.waterProximity, s.riverProximity);
        }
    }

    private static float distToSegment(float px, float pz, float ax, float az, float bx, float bz) {
        float dx = bx - ax, dz = bz - az;
        float len2 = dx * dx + dz * dz;
        if (len2 < 1e-8f) {
            float ex = px - ax, ez = pz - az;
            return (float) Math.sqrt(ex * ex + ez * ez);
        }
        float t = ((px - ax) * dx + (pz - az) * dz) / len2;
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        float qx = ax + t * dx, qz = az + t * dz;
        float ex = px - qx, ez = pz - qz;
        return (float) Math.sqrt(ex * ex + ez * ez);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
