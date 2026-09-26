package toyz.builder.terrain;

/**
 * Large-scale coherent landforms (not raw noise→height).
 * Continental mask + mountain ranges + plateaus + mesas + valleys.
 */
public final class LandformGenerator {
    private LandformGenerator() {}

    public static float baseHeight(float x, float z, WorldConfig cfg) {
        if (cfg.type == WorldConfig.WorldType.FLAT) {
            return Noise.fractal(x * 0.05f, z * 0.05f, cfg.seed + 1, 2) * cfg.heightScale * 0.15f;
        }

        float size = cfg.size;
        float nx = x / size;
        float nz = z / size;
        int seed = cfg.seed;
        float hs = cfg.heightScale;

        // Continentalness: large blobs of land vs ocean shelf
        float cont = Noise.fractal(nx * 1.2f + 2f, nz * 1.2f - 1.5f, seed + 11, 5);
        cont = smoothstep(0.28f, 0.62f, cont);

        // Mountain range: elongated ridged band
        float rangeAxis = nx * 0.7f + nz * 0.3f;
        float range = Noise.ridged(rangeAxis * 3.5f + 4f, nz * 1.2f - nx * 0.4f, seed + 333);
        range = (float) Math.pow(range, 1.6f);

        // Foothills / hills
        float hills = Noise.fractal(nx * 4.5f - 3f, nz * 4.5f + 2f, seed + 90, 4);

        // Plateaus: flattened highs
        float plate = Noise.fractal(nx * 2.0f + 8f, nz * 2.0f - 6f, seed + 1707, 3);
        float plateau = 0f;
        if (plate > 0.62f) {
            plateau = smoothstep(0.62f, 0.78f, plate) * 0.55f;
        }

        // Mesas / badlands terraces (hot dry regions use later in biome)
        float mesa = Noise.fractal(nx * 5f + 1f, nz * 5f + 9f, seed + 404, 3);
        float mesaH = 0f;
        if (mesa > 0.7f) {
            mesaH = (float) Math.floor(smoothstep(0.7f, 0.9f, mesa) * 4f) / 4f * 0.35f;
        }

        // Valleys: subtract along secondary axis
        float valley = Noise.fractal(nx * 3.2f - 10f, nz * 3.2f + 4f, seed + 505, 3);
        float valleyCut = smoothstep(0.55f, 0.85f, valley) * 0.25f;

        // Detail
        float detail = Noise.fractal(nx * 14f, nz * 14f, seed ^ 0x9e3779b9, 3) * 0.08f;

        float h = 0f;
        h += cont * 0.35f;
        h += range * cont * 0.55f;
        h += hills * cont * 0.22f;
        h += plateau * cont;
        h += mesaH * cont;
        h -= valleyCut * cont;
        h += detail * cont;

        // Ocean shelf outside continent
        if (cont < 0.15f) {
            h = -0.35f + hills * 0.05f;
        } else if (cont < 0.4f) {
            h = lerp(-0.2f, h, (cont - 0.15f) / 0.25f);
        }

        if (cfg.type == WorldConfig.WorldType.AMPLIFIED) {
            h *= 1.35f;
            h += range * 0.2f;
        }

        return h * hs;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
