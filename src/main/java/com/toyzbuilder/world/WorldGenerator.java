package com.toyzbuilder.world;

/**
 * Multi-biome continuous heightfield for the LWJGL exploration prototype.
 * Tuned for performance: single dense mesh, deterministic seed, no piece spam.
 *
 * Biomes (Toyz-style palette):
 * Ocean, Beach, Meadow, Forest, Jungle, Savanna, Desert, Badlands,
 * Taiga, Snow, Alpine, Swamp, Highlands, Volcanic.
 */
public final class WorldGenerator {

    public enum Biome {
        OCEAN(0.10f, 0.25f, 0.55f),
        BEACH(0.76f, 0.70f, 0.45f),
        MEADOW(0.30f, 0.62f, 0.22f),
        FOREST(0.15f, 0.42f, 0.14f),
        JUNGLE(0.08f, 0.48f, 0.12f),
        SAVANNA(0.62f, 0.55f, 0.22f),
        DESERT(0.86f, 0.78f, 0.42f),
        BADLANDS(0.72f, 0.38f, 0.22f),
        TAIGA(0.22f, 0.40f, 0.32f),
        SNOW(0.88f, 0.92f, 0.96f),
        ALPINE(0.55f, 0.58f, 0.62f),
        SWAMP(0.22f, 0.35f, 0.18f),
        HIGHLANDS(0.40f, 0.48f, 0.28f),
        VOLCANIC(0.28f, 0.18f, 0.16f),
        // expanded variety
        TUNDRA(0.55f, 0.58f, 0.50f),
        REDWOOD(0.18f, 0.32f, 0.14f),
        DARK_FOREST(0.10f, 0.22f, 0.12f),
        BAMBOO(0.25f, 0.55f, 0.20f),
        PRAIRIE(0.55f, 0.62f, 0.28f),
        MARSH(0.28f, 0.40f, 0.28f),
        CRAG(0.48f, 0.45f, 0.42f),
        MUSHROOM(0.45f, 0.28f, 0.40f),
        FROZEN_FOREST(0.16f, 0.28f, 0.22f),
        GLACIER(0.72f, 0.86f, 0.94f),
        MANGROVE(0.12f, 0.38f, 0.20f),
        FLOODPLAIN(0.34f, 0.55f, 0.20f),
        WILLOW_WETLAND(0.20f, 0.40f, 0.18f),
        CEDAR_HIGHLANDS(0.18f, 0.34f, 0.20f),
        PALM_COAST(0.30f, 0.62f, 0.24f),
        ASHLANDS(0.24f, 0.22f, 0.20f),
        CRYSTAL_FIELDS(0.42f, 0.62f, 0.72f),
        OBSIDIAN_WASTES(0.10f, 0.08f, 0.10f),
        SALT_FLATS(0.78f, 0.76f, 0.68f),
        OASIS(0.30f, 0.58f, 0.20f),
        STEPPE(0.48f, 0.54f, 0.25f);

        public final float r, g, b;
        Biome(float r, float g, float b) { this.r = r; this.g = g; this.b = b; }
    }

    public static final class Settings {
        public int seed = 0xC0FFEE;
        /** Grid resolution (verts per side). Default 97 ≈ 18k tris (was 257 ≈ 131k). */
        public int resolution = 97;
        /** Logical playable world width/depth. Terrain is streamed into this space. */
        public float worldSize = WorldBounds.MINETEST_WORLD_SIZE;
        /** Radius around the player to keep actively generated/renderable. */
        public float streamRadius = 768f;
        /** World units between verts. total size = (resolution-1) * spacing. */
        public float spacing = 3.5f;
        public float heightScale = 28f;
        public float waterLevel = 4.5f;
    }

    public static final class Result {
        public final Settings settings;
        public final float[] heights;   // resolution^2
        public final Biome[] biomes;    // resolution^2
        public final float size;        // currently generated terrain patch extent
        public final float worldSize;  // logical world extent
        public Result(Settings s, float[] h, Biome[] b) {
            this.settings = s;
            this.heights = h;
            this.biomes = b;
            this.size = (s.resolution - 1) * s.spacing;
            this.worldSize = s.worldSize;
        }
    }

    private final Settings cfg;
    private final int seed;

    public WorldGenerator(Settings cfg) {
        this.cfg = cfg;
        this.seed = cfg.seed;
    }

    public Result generate() {
        int n = cfg.resolution;
        float[] h = new float[n * n];
        Biome[] bio = new Biome[n * n];
        float half = (n - 1) * cfg.spacing * 0.5f;

        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                float x = ix * cfg.spacing - half;
                float z = iz * cfg.spacing - half;
                int i = iz * n + ix;

                float cont = fbm(x * 0.0022f, z * 0.0022f, 4, 2.0f, 0.5f);
                float range = fbm(x * 0.006f + 30, z * 0.006f + 30, 3, 2.1f, 0.5f);
                float hill = fbm(x * 0.018f + 90, z * 0.018f + 90, 3, 2.0f, 0.45f);
                float detail = fbm(x * 0.05f, z * 0.05f, 2, 2.0f, 0.4f);

                // Continent mask → ocean vs land
                float land = smoothstep(0.32f, 0.58f, cont);
                float elev = land * (0.25f + range * 0.45f + hill * 0.22f + detail * 0.08f);
                // Alpine spikes
                elev += land * range * range * 0.35f;
                float height = elev * cfg.heightScale;

                // Simple river carve
                float river = Math.abs(fbm(x * 0.004f + 200, z * 0.004f, 2, 2.0f, 0.5f) - 0.5f);
                if (land > 0.4f && river < 0.04f) {
                    height = Math.min(height, cfg.waterLevel - 0.4f + river * 8f);
                }

                float temp = fbm(x * 0.0035f + 11, z * 0.0035f + 11, 3, 2.0f, 0.5f);
                // Cooler at high elev
                temp -= elev * 0.45f;
                float moist = fbm(x * 0.004f + 77, z * 0.004f + 77, 3, 2.0f, 0.5f);

                bio[i] = pickBiome(land, elev, temp, moist, height);
                if (bio[i] == Biome.OCEAN || bio[i] == Biome.BEACH) {
                    height = Math.min(height, cfg.waterLevel + (bio[i] == Biome.BEACH ? 0.6f : -0.5f));
                }
                if (bio[i] == Biome.SWAMP) {
                    height = Math.min(height, cfg.waterLevel + 1.2f);
                }
                h[i] = height;
            }
        }
        return new Result(cfg, h, bio);
    }

    public static float sampleHeight(Result r, float wx, float wz) {
        if (r == null) return 0f;
        if (!WorldBounds.contains(r.worldSize, wx, wz)) return 0f;
        int n = r.settings.resolution;
        float half = r.size * 0.5f;
        float fx = (wx + half) / r.settings.spacing;
        float fz = (wz + half) / r.settings.spacing;
        int x0 = clamp((int) Math.floor(fx), 0, n - 2);
        int z0 = clamp((int) Math.floor(fz), 0, n - 2);
        float tx = fx - x0, tz = fz - z0;
        float h00 = r.heights[z0 * n + x0];
        float h10 = r.heights[z0 * n + x0 + 1];
        float h01 = r.heights[(z0 + 1) * n + x0];
        float h11 = r.heights[(z0 + 1) * n + x0 + 1];
        float hx0 = h00 + (h10 - h00) * tx;
        float hx1 = h01 + (h11 - h01) * tx;
        return hx0 + (hx1 - hx0) * tz;
    }

    public static Biome sampleBiome(Result r, float wx, float wz) {
        if (r == null) return Biome.MEADOW;
        if (!WorldBounds.contains(r.worldSize, wx, wz)) return Biome.OCEAN;
        int n = r.settings.resolution;
        float half = r.size * 0.5f;
        int ix = clamp(Math.round((wx + half) / r.settings.spacing), 0, n - 1);
        int iz = clamp(Math.round((wz + half) / r.settings.spacing), 0, n - 1);
        return r.biomes[iz * n + ix];
    }

    /**
     * Interleaved terrain mesh: pos3 + normal3 + uv2 + blend4 (grass,sand,snow,rock).
     * Textures come from AssetBank (same folders as main Toyz).
     */
    public static float[] buildVertexData(Result r) {
        int n = r.settings.resolution;
        float half = r.size * 0.5f;
        float sp = r.settings.spacing;
        float[] data = new float[n * n * 12];
        float uvScale = 0.08f; // tiling density

        for (int iz = 0; iz < n; iz++) {
            for (int ix = 0; ix < n; ix++) {
                int i = iz * n + ix;
                float x = ix * sp - half;
                float z = iz * sp - half;
                float y = r.heights[i];

                float hL = r.heights[iz * n + Math.max(ix - 1, 0)];
                float hR = r.heights[iz * n + Math.min(ix + 1, n - 1)];
                float hD = r.heights[Math.max(iz - 1, 0) * n + ix];
                float hU = r.heights[Math.min(iz + 1, n - 1) * n + ix];
                float nx = (hL - hR) / (2f * sp);
                float nz = (hD - hU) / (2f * sp);
                float ny = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;

                float[] w = biomeBlend(r.biomes[i]);
                int o = i * 12;
                data[o] = x; data[o + 1] = y; data[o + 2] = z;
                data[o + 3] = nx; data[o + 4] = ny; data[o + 5] = nz;
                data[o + 6] = x * uvScale;
                data[o + 7] = z * uvScale;
                data[o + 8] = w[0]; data[o + 9] = w[1];
                data[o + 10] = w[2]; data[o + 11] = w[3];
            }
        }
        return data;
    }

    /** grass, sand, snow, rock weights — sum ~1 */
    /** Public for TerrainChunks mesh build. */
    public static float[] biomeBlendPublic(Biome b) { return biomeBlend(b); }

    private static float[] biomeBlend(Biome b) {
        switch (b) {
            case OCEAN:
            case BEACH:      return new float[]{0.05f, 0.85f, 0.00f, 0.10f};
            case DESERT:
            case SAVANNA:
            case BADLANDS:
            case PRAIRIE:    return new float[]{0.10f, 0.70f, 0.00f, 0.20f};
            case SNOW:
            case TAIGA:
            case TUNDRA:     return new float[]{0.15f, 0.00f, 0.75f, 0.10f};
            case ALPINE:
            case CRAG:       return new float[]{0.05f, 0.00f, 0.45f, 0.50f};
            case VOLCANIC:   return new float[]{0.00f, 0.10f, 0.00f, 0.90f};
            case HIGHLANDS:  return new float[]{0.35f, 0.05f, 0.10f, 0.50f};
            case SWAMP:
            case MARSH:      return new float[]{0.55f, 0.25f, 0.00f, 0.20f};
            case FOREST:
            case JUNGLE:
            case REDWOOD:
            case BAMBOO:     return new float[]{0.80f, 0.05f, 0.00f, 0.15f};
            case DARK_FOREST:return new float[]{0.55f, 0.05f, 0.05f, 0.35f};
            case MUSHROOM:   return new float[]{0.40f, 0.15f, 0.05f, 0.40f};
            case FROZEN_FOREST:
            case GLACIER:    return new float[]{0.05f, 0.00f, 0.80f, 0.15f};
            case MANGROVE:
            case FLOODPLAIN:
            case WILLOW_WETLAND: return new float[]{0.65f, 0.20f, 0.00f, 0.15f};
            case CEDAR_HIGHLANDS: return new float[]{0.35f, 0.00f, 0.10f, 0.55f};
            case PALM_COAST:
            case OASIS:      return new float[]{0.65f, 0.25f, 0.00f, 0.10f};
            case ASHLANDS:
            case OBSIDIAN_WASTES: return new float[]{0.00f, 0.05f, 0.00f, 0.95f};
            case CRYSTAL_FIELDS: return new float[]{0.15f, 0.00f, 0.20f, 0.65f};
            case SALT_FLATS: return new float[]{0.05f, 0.85f, 0.00f, 0.10f};
            case STEPPE:     return new float[]{0.55f, 0.25f, 0.00f, 0.20f};
            case MEADOW:
            default:         return new float[]{0.75f, 0.10f, 0.00f, 0.15f};
        }
    }

    public static int[] buildIndices(Result r) {
        int n = r.settings.resolution;
        int quads = (n - 1) * (n - 1);
        int[] idx = new int[quads * 6];
        int t = 0;
        for (int z = 0; z < n - 1; z++) {
            for (int x = 0; x < n - 1; x++) {
                int tl = z * n + x;
                int tr = tl + 1;
                int bl = tl + n;
                int br = bl + 1;
                idx[t++] = tl; idx[t++] = bl; idx[t++] = tr;
                idx[t++] = tr; idx[t++] = bl; idx[t++] = br;
            }
        }
        return idx;
    }

    private Biome pickBiome(float land, float elev, float temp, float moist, float height) {
        if (land < 0.25f) return Biome.OCEAN;
        if (land < 0.38f) return Biome.BEACH;
        // high peaks
        if (elev > 0.78f) return temp < 0.35f ? Biome.SNOW : Biome.CRAG;
        if (elev > 0.68f) return temp < 0.4f ? Biome.SNOW : Biome.ALPINE;
        // cold band
        if (temp < 0.12f && elev > 0.62f) return Biome.GLACIER;
        if (temp < 0.16f && moist > 0.55f) return Biome.FROZEN_FOREST;
        if (temp < 0.22f) return moist > 0.4f ? Biome.TUNDRA : Biome.SNOW;
        if (temp < 0.32f) return moist > 0.45f ? Biome.TAIGA : Biome.TUNDRA;
        // wet lowlands and tropical coasts
        if (moist > 0.84f && temp > 0.65f && elev < 0.28f) return Biome.MANGROVE;
        if (moist > 0.80f && temp > 0.55f && elev < 0.4f) return Biome.BAMBOO;
        if (moist > 0.72f && temp > 0.55f) return Biome.JUNGLE;
        if (moist > 0.68f && elev < 0.32f) return temp > 0.45f ? Biome.MARSH : Biome.SWAMP;
        if (moist > 0.62f && elev < 0.35f) return Biome.SWAMP;
        if (moist > 0.62f && elev < 0.42f && temp > 0.48f) return Biome.FLOODPLAIN;
        if (moist > 0.72f && elev < 0.38f && temp > 0.38f) return Biome.WILLOW_WETLAND;
        // arid and special drylands
        if (moist < 0.12f && temp > 0.62f && elev < 0.34f) return Biome.SALT_FLATS;
        if (moist < 0.18f && temp > 0.65f && elev < 0.40f && height < cfg.waterLevel + 18f) return Biome.OASIS;
        if (moist < 0.22f && temp > 0.6f) return elev > 0.48f ? Biome.BADLANDS : Biome.DESERT;
        if (moist < 0.30f && temp > 0.55f) return elev > 0.45f ? Biome.BADLANDS : Biome.DESERT;
        if (moist < 0.40f && temp > 0.45f) return elev > 0.42f ? Biome.STEPPE : Biome.SAVANNA;
        // special forests
        if (moist > 0.58f && temp > 0.35f && temp < 0.55f && elev > 0.35f && elev < 0.55f)
            return Biome.REDWOOD;
        if (moist > 0.55f && temp < 0.48f && elev < 0.45f)
            return Biome.DARK_FOREST;
        if (moist > 0.5f && temp > 0.4f && elev < 0.35f && height < cfg.waterLevel + 6f)
            return Biome.MUSHROOM;
        if (elev > 0.72f && moist > 0.45f) return Biome.CEDAR_HIGHLANDS;
        if (elev > 0.50f) return Biome.HIGHLANDS;
        if (temp > 0.72f && moist < 0.28f && elev > 0.40f) return Biome.ASHLANDS;
        if (temp > 0.68f && moist < 0.30f && elev > 0.55f) return Biome.OBSIDIAN_WASTES;
        if (elev > 0.52f && moist > 0.55f && temp > 0.45f) return Biome.CRYSTAL_FIELDS;
        if (temp > 0.65f && moist > 0.45f && elev < 0.28f) return Biome.PALM_COAST;
        if (moist > 0.50f) return Biome.FOREST;
        if (temp > 0.7f && moist < 0.35f) return Biome.VOLCANIC;
        if (moist < 0.48f && temp > 0.4f) return Biome.PRAIRIE;
        return Biome.MEADOW;
    }

    // ---- noise ----
    private float fbm(float x, float z, int oct, float lacunarity, float gain) {
        float sum = 0, amp = 0.5f, freq = 1f, norm = 0;
        for (int i = 0; i < oct; i++) {
            sum += amp * valueNoise(x * freq, z * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private float valueNoise(float x, float z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        float fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float v00 = hash(x0, z0), v10 = hash(x0 + 1, z0);
        float v01 = hash(x0, z0 + 1), v11 = hash(x0 + 1, z0 + 1);
        float a = v00 + (v10 - v00) * fx;
        float b = v01 + (v11 - v01) * fx;
        return a + (b - a) * fz;
    }

    private float hash(int x, int z) {
        long n = x * 374761393L + z * 668265263L + seed * 1274126177L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n ^= (n >> 16);
        return ((n & 0x7fffffffL) / (float) 0x7fffffffL);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(1, v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(hi, v);
    }
}
