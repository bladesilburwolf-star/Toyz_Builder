package toyz.builder.terrain;

/** Deterministic value / fractal noise for Terrain V2. */
public final class Noise {
    private Noise() {}

    public static float hash2D(int x, int z, int seed) {
        long h = x * 374761393L + z * 668265263L;
        h ^= (seed * 1442695041L);
        h = (h ^ (h >> 13)) * 1274126177L;
        return ((h & 0x7fffffff) / (float) 0x7fffffff);
    }

    public static float valueNoise(float x, float z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        float fx = x - x0, fz = z - z0;
        float u = fx * fx * (3f - 2f * fx);
        float v = fz * fz * (3f - 2f * fz);
        float a = hash2D(x0, z0, seed);
        float b = hash2D(x0 + 1, z0, seed);
        float c = hash2D(x0, z0 + 1, seed);
        float d = hash2D(x0 + 1, z0 + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    public static float fractal(float x, float z, int seed, int octaves) {
        float sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += valueNoise(x * freq, z * freq, seed + i * 1013) * amp;
            norm += amp;
            amp *= 0.5f;
            freq *= 2f;
        }
        return sum / Math.max(1e-6f, norm);
    }

    public static float ridged(float x, float z, int seed) {
        float n = fractal(x, z, seed, 4);
        return 1f - Math.abs(n * 2f - 1f);
    }
}
