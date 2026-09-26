package toyz.builder.terrain;

/**
 * Large-scale coherent landforms (not raw noise→height).
 * Wavelengths are in WORLD UNITS so huge Phase B maps still have hills/mountains
 * near the player — never divide feature noise by cfg.size.
 */
public final class LandformGenerator {
    private LandformGenerator() {}

    /** Continent blob size (~units). Independent of world extent. */
    private static final float CONT_SCALE = 420f;
    /** Mountain range wavelength */
    private static final float RANGE_SCALE = 180f;
    /** Rolling hills */
    private static final float HILL_SCALE = 64f;
    /** Plateau / mesa / valley */
    private static final float MACRO_SCALE = 110f;
    private static final float DETAIL_SCALE = 18f;

    public static float baseHeight(float x, float z, WorldConfig cfg) {
        if (cfg.type == WorldConfig.WorldType.FLAT) {
            return Noise.fractal(x * 0.05f, z * 0.05f, cfg.seed + 1, 2) * cfg.heightScale * 0.15f;
        }

        int seed = cfg.seed;
        float hs = Math.max(12f, cfg.heightScale);

        // World-unit normalized coords (NOT / size)
        float cx = x / CONT_SCALE;
        float cz = z / CONT_SCALE;
        float rx = x / RANGE_SCALE;
        float rz = z / RANGE_SCALE;
        float hx = x / HILL_SCALE;
        float hz = z / HILL_SCALE;
        float mx = x / MACRO_SCALE;
        float mz = z / MACRO_SCALE;
        float dx = x / DETAIL_SCALE;
        float dz = z / DETAIL_SCALE;

        // Continentalness: land vs low shelf (still varies within a few hundred units)
        float cont = Noise.fractal(cx * 1.15f + 2f, cz * 1.15f - 1.5f, seed + 11, 5);
        cont = smoothstep(0.22f, 0.58f, cont);

        // Mountain ranges — ridged, strong
        float rangeAxis = rx * 0.85f + rz * 0.35f;
        float range = Noise.ridged(rangeAxis * 2.8f + 4f, rz * 1.4f - rx * 0.5f, seed + 333);
        range = (float) Math.pow(Math.max(0f, range), 1.35f);

        // Foothills / rolling hills
        float hills = Noise.fractal(hx * 1.1f - 3f, hz * 1.1f + 2f, seed + 90, 4);

        // Secondary hill band (breaks up flat mid-elevation)
        float hills2 = Noise.fractal(hx * 2.3f + 5f, hz * 2.3f - 4f, seed + 191, 3);

        // Plateaus
        float plate = Noise.fractal(mx * 1.2f + 8f, mz * 1.2f - 6f, seed + 1707, 3);
        float plateau = 0f;
        if (plate > 0.58f) {
            plateau = smoothstep(0.58f, 0.78f, plate) * 0.65f;
        }

        // Mesas / terraces
        float mesa = Noise.fractal(mx * 1.6f + 1f, mz * 1.6f + 9f, seed + 404, 3);
        float mesaH = 0f;
        if (mesa > 0.68f) {
            mesaH = (float) Math.floor(smoothstep(0.68f, 0.9f, mesa) * 5f) / 5f * 0.45f;
        }

        // Valleys — deeper cuts
        float valley = Noise.fractal(mx * 1.4f - 10f, mz * 1.4f + 4f, seed + 505, 3);
        float valleyCut = smoothstep(0.5f, 0.82f, valley) * 0.38f;

        // Fine detail
        float detail = Noise.fractal(dx, dz, seed ^ 0x9e3779b9, 3) * 0.12f;

        float h = 0.12f; // base rise so land isn't ocean-flat mid-continent
        h += cont * 0.28f;
        h += range * cont * 0.85f;   // mountains
        h += hills * cont * 0.38f;   // primary hills
        h += hills2 * cont * 0.18f;  // secondary
        h += plateau * cont;
        h += mesaH * cont;
        h -= valleyCut * (0.55f + 0.45f * cont);
        h += detail * (0.35f + 0.65f * cont);

        // Ocean / shelf only where continentalness is truly low
        if (cont < 0.12f) {
            h = -0.28f + hills * 0.06f + detail * 0.04f;
        } else if (cont < 0.35f) {
            h = lerp(-0.12f, h, (cont - 0.12f) / 0.23f);
        }

        if (cfg.type == WorldConfig.WorldType.AMPLIFIED) {
            h *= 1.45f;
            h += range * 0.28f;
            h += hills * 0.12f;
        } else {
            // NORMAL — ensure readable relief
            h *= 1.12f;
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
