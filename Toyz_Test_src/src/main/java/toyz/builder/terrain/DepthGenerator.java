package toyz.builder.terrain;

/** Deterministic depth under continuous surface — soil/rock/caves. */
public final class DepthGenerator {
    private DepthGenerator() {}

    public static float solidDepth(float x, float z, WorldConfig cfg) {
        float nx = x / cfg.size, nz = z / cfg.size;
        float base = cfg.maxDepth * (0.55f + 0.35f * Noise.fractal(nx * 2.1f + 9f, nz * 2.1f - 4f, cfg.seed + 9001, 3));
        return Math.max(2f, base);
    }

    public static float caveOpenness(float x, float y, float z, float surfaceY, WorldConfig cfg) {
        if (cfg.preset == WorldConfig.MapgenPreset.V2 || cfg.preset == WorldConfig.MapgenPreset.FLAT)
            return 0f;
        float depth = surfaceY - y;
        if (depth < 2f || depth > cfg.maxDepth) return 0f;
        float nx = x * 0.035f, nz = z * 0.035f, ny = y * 0.05f;
        float n1 = Noise.fractal(nx + 2f, nz - 1f + ny * 0.3f, cfg.seed + 9100, 3);
        float n2 = Noise.fractal(nx * 1.7f - 5f, nz * 1.7f + ny, cfg.seed + 9200, 2);
        float worm = 1f - Math.abs(n1 * 2f - 1f);
        float open = worm * 0.65f + n2 * 0.35f;
        float band = 1f - Math.abs(depth / cfg.maxDepth - 0.45f) * 1.8f;
        if (band < 0f) band = 0f;
        open *= band;
        float threshold = cfg.preset == WorldConfig.MapgenPreset.V7 ? 0.55f : 0.62f;
        return open > threshold ? (open - threshold) / (1f - threshold) : 0f;
    }

    public static void fillSample(TerrainSample s, WorldConfig cfg) {
        s.solidDepth = solidDepth(s.x, s.z, cfg);
        float midY = s.height - s.solidDepth * 0.4f;
        s.caveOpenness = caveOpenness(s.x, midY, s.z, s.height, cfg);
        s.hasCave = s.caveOpenness > 0.15f;
    }
}
