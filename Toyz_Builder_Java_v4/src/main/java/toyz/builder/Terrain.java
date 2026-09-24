package toyz.builder;

import com.raylib.Helpers;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.ShortPointer;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Terrain v4 — 10-biome world + caves, magma pools, rock outcrops,
 * richer tree tints. Hybrid Minecraft-style trees
 * (semi-rounded cylinder trunks + blocky leaf-block canopies) and a
 * decorative vegetation layer (grass tufts + flowers) with no collision.
 */
public final class Terrain {

    // ---- biome ids ----
    public static final int BIOME_SNOW    = 0;
    public static final int BIOME_TAIGA   = 1;
    public static final int BIOME_FOREST  = 2;
    public static final int BIOME_MEADOW  = 3;
    public static final int BIOME_AUTUMN  = 4;
    public static final int BIOME_JUNGLE  = 5;
    public static final int BIOME_SAVANNA = 6;
    public static final int BIOME_DESERT  = 7;
    public static final int BIOME_SWAMP   = 8;
    public static final int BIOME_VOLCANO = 9;

    public static final String[] BIOME_NAMES = {
        "Snowy Peaks", "Taiga", "Temperate Forest", "Meadow", "Autumn Woods",
        "Jungle", "Savanna", "Desert", "Swamp", "Volcanic"
    };

    private static final int[][] BIOME_GRASS = {
        {240, 245, 250}, { 62,  95,  72}, { 55, 120,  50}, {120, 165,  80},
        {170, 120,  50}, { 35, 140,  60}, {175, 160,  80}, {220, 195, 135},
        { 70,  95,  55}, { 60,  50,  48}
    };
    private static final float[] BIOME_HEIGHT_MOD = {
        1.35f, 1.10f, 1.00f, 0.80f, 0.95f, 1.05f, 0.85f, 0.70f, 0.55f, 1.60f
    };
    private static final int[] BIOME_DENSITY = {
        5, 55, 70, 12, 45, 85, 10, 3, 40, 0
    };
    // tree kinds per biome: 0 oak, 1 spruce, 2 autumn oak, 3 bush, 4 acacia, 5 cactus, 6 jungle
    private static final int[][] BIOME_TREES = {
        {1},       // snow: spruce
        {1},       // taiga: spruce
        {0, 2},    // forest: oak / autumn oak
        {0, 3},    // meadow: oak / bush
        {2},       // autumn woods
        {6, 0},    // jungle: jungle tree / oak
        {4},       // savanna: acacia
        {5},       // desert: cactus
        {3, 0},    // swamp: bush / oak
        {}         // volcanic: barren
    };
    // vegetation density per biome (0-100) — grass + flowers
    private static final int[] BIOME_VEG_DENSITY = {
        2, 15, 55, 90, 30, 70, 45, 2, 65, 0
    };
    // flower chance within vegetation per biome (0-100)
    private static final int[] BIOME_FLOWER_CHANCE = {
        0, 5, 25, 55, 20, 35, 15, 0, 30, 0
    };

    public static class ForestTree {
        public Vector3 position;
        public float scale;
        public float rotation;
        public int biomeType;    // 0 oak, 1 spruce, 2 autumn oak, 3 bush, 4 acacia, 5 cactus, 6 jungle
        public int colorVariant; // 0..3
        public int biome;
    }

    /** Decorative vegetation — grass tufts and flowers. No collision. */
    public static class Vegetation {
        public Vector3 position;
        public int kind;         // 0 grass tuft, 1 flower
        public int variant;      // grass: 0..3 color, flower: 0 red 1 yellow 2 blue 3 white
        public float rotation;
        public float swayPhase;
    }

    /** Magma / lava pool surface (volcanic + rare hot spots). */
    public static class MagmaPool {
        public Vector3 position; // center at pool surface
        public float radius;
        public float pulsePhase;
    }

    /** Cave mouth / sinkhole marker — rock ring around a depression. */
    public static class CaveFeature {
        public Vector3 position; // ground at mouth
        public float radius;
        public float depth;
        public int variant; // rock tint
    }

    /** Decorative rock outcrop cluster (uses rock/stone textures). */
    public static class RockOutcrop {
        public Vector3 position;
        public float scale;
        public float rotation;
        public int variant; // 0..4 rock palette
        public int blocks;  // 1..4 stacked cubes
    }

    /** Legacy rectangle biome — kept for compatibility. */
    public static class Biome {
        public float startX, startZ, width, depth;
        public Color grassColor, dirtColor;
        public float heightScale;
        public int treeDensity;
        public Biome(float sx, float sz, float w, float d, Color g, Color dirt, float hs, int td) {
            startX = sx; startZ = sz; width = w; depth = d;
            grassColor = g; dirtColor = dirt; heightScale = hs; treeDensity = td;
        }
    }

    public static class ForestTerrain {
        public Model terrainModel;
        // tree part models (trunk = semi-rounded cylinder; leaves = leaf blocks)
        public Model trunkModel, trunkFatModel;
        public Model leafBlockModel, leafBlockSmallModel;
        public Model spruceBlockModel;
        public Model cactusBlockModel;
        public Model acaciaTrunkModel;
        public Model rockBlockModel;   // GenMeshCube for outcrops / cave mouths
        public Model magmaBlockModel;  // flat-ish cube for lava surface
        public List<ForestTree> trees = new ArrayList<>();
        public List<Vegetation> vegetation = new ArrayList<>();
        public List<MagmaPool> magmaPools = new ArrayList<>();
        public List<CaveFeature> caves = new ArrayList<>();
        public List<RockOutcrop> rocks = new ArrayList<>();
        public List<Biome> biomes = new ArrayList<>();
        public float size = 400f;
        public float cellSize = 4.0f;
        public float heightScale = 16f;
        public float waterLevel = 0f;
        public int seed = 0xC0FFEE;
    }

    private static final float WATER_LEVEL_FRAC = -0.30f;

    private Terrain() {}

    // ---- noise ----

    private static float hash2D(int x, int z, int seed) {
        long h = (x * 374761393L);
        h ^= (z * 668265263L);
        h ^= (seed * 1442695041L);
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0x00FFFFFFL) / 16777215.0f;
    }

    private static float smooth(float t) { return t * t * (3f - 2f * t); }

    private static float valueNoise(float x, float z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = smooth(x - x0);
        float fz = smooth(z - z0);
        float a = hash2D(x0, z0, seed);
        float b = hash2D(x0 + 1, z0, seed);
        float c = hash2D(x0, z0 + 1, seed);
        float d = hash2D(x0 + 1, z0 + 1, seed);
        float ab = a + (b - a) * fx;
        float cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    private static float fractalNoise(float x, float z, int seed) {
        float value = 0f, amplitude = 1f, frequency = 1f, totalAmplitude = 0f;
        for (int octave = 0; octave < 5; octave++) {
            value += valueNoise(x * frequency, z * frequency, seed + octave * 1013) * amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5f;
            frequency *= 2f;
        }
        return value / totalAmplitude;
    }

    private static float riverInfluence(float nx, float nz, int seed) {
        float meander = (fractalNoise(nz * 3f + 50f, 5f, seed + 777) - 0.5f) * 0.5f;
        float width = 0.014f + 0.010f * fractalNoise(nz * 4f + 90f, 8f, seed + 888);
        float dist = Math.abs(nx - meander);
        float t = clamp((dist - width) / (width * 2.5f + 0.001f), 0f, 1f);
        return 1f - t;
    }

    // ---- climate / biome selection ----

    private static float temperatureAt(float x, float z, float size, int seed) {
        float nx = x / size, nz = z / size;
        float lat = 1f - Math.min(1f, Math.abs(nz) * 1.6f);
        float t = fractalNoise(nx * 0.9f + 31f, nz * 0.9f - 12f, seed + 100);
        return clamp(t * 0.72f + lat * 0.38f, 0f, 1f);
    }

    private static float moistureAt(float x, float z, float size, int seed) {
        float nx = x / size, nz = z / size;
        return fractalNoise(nx * 0.8f - 5f, nz * 0.8f + 9f, seed + 555);
    }

    private static int biomeFromClimate(float temp, float moist, float volcNoise) {
        if (volcNoise > 0.86f) return BIOME_VOLCANO;
        if (temp < 0.25f) return moist > 0.40f ? BIOME_TAIGA : BIOME_SNOW;
        if (temp < 0.50f) {
            if (moist < 0.35f) return BIOME_MEADOW;
            if (moist < 0.62f) return BIOME_FOREST;
            return BIOME_SWAMP;
        }
        if (temp < 0.75f) {
            if (moist < 0.30f) return BIOME_DESERT;
            if (moist < 0.55f) return BIOME_SAVANNA;
            return BIOME_JUNGLE;
        }
        if (moist < 0.28f) return BIOME_DESERT;
        if (moist < 0.58f) return BIOME_SAVANNA;
        return BIOME_JUNGLE;
    }

    public static int biomeAt(ForestTerrain forest, float x, float z) {
        float nx = x / forest.size, nz = z / forest.size;
        float temp = temperatureAt(x, z, forest.size, forest.seed);
        float moist = moistureAt(x, z, forest.size, forest.seed);
        float volc = fractalNoise(nx * 1.6f + 61f, nz * 1.6f - 22f, forest.seed + 404);
        int b = biomeFromClimate(temp, moist, volc);
        float h = heightAt(x, z, forest.size, forest.heightScale, forest.seed);
        if (h > forest.heightScale * 0.85f && b != BIOME_VOLCANO) return BIOME_SNOW;
        return b;
    }

    public static String biomeNameAt(ForestTerrain forest, float x, float z) {
        return BIOME_NAMES[biomeAt(forest, x, z)];
    }

    // ---- height ----

    private static float heightAt(float x, float z, float size, float heightScale, int seed) {
        float nx = x / size, nz = z / size;
        float broad = fractalNoise(nx * 2.4f + 20f, nz * 2.4f - 17f, seed);
        float detail = fractalNoise(nx * 12f - 7f, nz * 12f + 11f, seed ^ 0x9e3779b9);
        float ridges = fractalNoise(nx * 6f + 1f, nz * 6f - 3f, seed + 333);

        float temp = temperatureAt(x, z, size, seed);
        float moist = moistureAt(x, z, size, seed);
        float volc = fractalNoise(nx * 1.6f + 61f, nz * 1.6f - 22f, seed + 404);
        int biome = biomeFromClimate(temp, moist, volc);

        float localScale = heightScale * BIOME_HEIGHT_MOD[biome];
        boolean isMountain = (biome == BIOME_SNOW || biome == BIOME_VOLCANO);

        float ridgeWeight = isMountain ? 0.40f : 0.10f;
        float h = (broad * (0.90f - ridgeWeight) + detail * 0.18f + ridges * ridgeWeight) * localScale;

        float edge = Math.max(Math.abs(nx), Math.abs(nz));
        float flatten = clamp((edge - 0.40f) / 0.60f, 0f, 1f);
        h *= (1f - flatten * 0.40f);
        h = h - heightScale * 0.38f;

        float river = riverInfluence(nx, nz, seed);
        if (river > 0f) {
            float waterLevel = heightScale * WATER_LEVEL_FRAC;
            float bed = waterLevel - heightScale * 0.05f;
            h = h * (1f - river) + bed * river;
        }

        // Cave sinkholes — rare deep wells (surface depression; true 3D caves later)
        float caveN = fractalNoise(nx * 9f + 40f, nz * 9f - 15f, seed + 909);
        if (caveN > 0.87f) {
            float strength = (caveN - 0.87f) / 0.13f;
            strength = strength * strength;
            h -= strength * heightScale * 0.65f;
        }

        // Magma craters in volcanic climate — flatten into a hot pool bowl
        if (biome == BIOME_VOLCANO) {
            float crater = fractalNoise(nx * 5.5f + 2f, nz * 5.5f + 8f, seed + 606);
            if (crater > 0.72f) {
                float s = (crater - 0.72f) / 0.28f;
                float poolY = heightScale * (-0.18f);
                h = h * (1f - s) + (poolY - s * heightScale * 0.08f) * s;
            }
        }
        return h;
    }

    private static Color biomeColor(int biome, float shade, float riverAmt) {
        int[] g = BIOME_GRASS[biome];
        int br = g[0], bg = g[1], bb = g[2];
        if (riverAmt > 0f) {
            br = (int) (br + (90 - br) * riverAmt);
            bg = (int) (bg + (80 - bg) * riverAmt);
            bb = (int) (bb + (65 - bb) * riverAmt);
        }
        return Helpers.newColor((int) (br * shade), (int) (bg * shade), (int) (bb * shade), 255);
    }

    public static float getTerrainHeight(ForestTerrain forest, float x, float z) {
        return heightAt(x, z, forest.size, forest.heightScale, forest.seed);
    }

    public static float getBiomeHeightScale(ForestTerrain forest, float x, float z) {
        return BIOME_HEIGHT_MOD[biomeAt(forest, x, z)];
    }

    public static Color getBiomeGrassColor(ForestTerrain forest, float x, float z) {
        int[] g = BIOME_GRASS[biomeAt(forest, x, z)];
        return Helpers.newColor(g[0], g[1], g[2], 255);
    }

    // ---- generation ----

    public static ForestTerrain generateForestTerrain(int seed) {
        ForestTerrain forest = new ForestTerrain();
        forest.seed = seed;
        forest.size = 400f;
        forest.cellSize = 4.0f;
        forest.heightScale = 16f;
        forest.waterLevel = forest.heightScale * WATER_LEVEL_FRAC;

        int cells = (int) (forest.size / forest.cellSize);
        cells = Math.min(cells, 104);
        forest.cellSize = forest.size / cells;
        int vertsPerSide = cells + 1;
        int vertexCount = vertsPerSide * vertsPerSide;
        int triangleCount = cells * cells * 2;

        Mesh mesh = new Mesh().vertexCount(vertexCount).triangleCount(triangleCount);
        FloatPointer vertices = new FloatPointer(vertexCount * 3);
        FloatPointer normals = new FloatPointer(vertexCount * 3);
        FloatPointer texcoords = new FloatPointer(vertexCount * 2);
        BytePointer colors = new BytePointer(vertexCount * 4);
        ShortPointer indices = new ShortPointer(triangleCount * 3);

        for (int z = 0; z < vertsPerSide; z++) {
            for (int x = 0; x < vertsPerSide; x++) {
                int i = z * vertsPerSide + x;
                float worldX = -forest.size * 0.5f + x * forest.cellSize;
                float worldZ = -forest.size * 0.5f + z * forest.cellSize;
                float y = heightAt(worldX, worldZ, forest.size, forest.heightScale, seed);

                vertices.put(i * 3, worldX).put(i * 3 + 1, y).put(i * 3 + 2, worldZ);
                texcoords.put(i * 2, (float) x / cells * 24f).put(i * 2 + 1, (float) z / cells * 24f);

                float eps = forest.cellSize;
                float hL = heightAt(worldX - eps, worldZ, forest.size, forest.heightScale, seed);
                float hR = heightAt(worldX + eps, worldZ, forest.size, forest.heightScale, seed);
                float hD = heightAt(worldX, worldZ - eps, forest.size, forest.heightScale, seed);
                float hU = heightAt(worldX, worldZ + eps, forest.size, forest.heightScale, seed);
                float nx = hL - hR, ny = 2f * eps, nz = hD - hU;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
                normals.put(i * 3, nx).put(i * 3 + 1, ny).put(i * 3 + 2, nz);

                float temp = temperatureAt(worldX, worldZ, forest.size, seed);
                float moist = moistureAt(worldX, worldZ, forest.size, seed);
                float nxs = worldX / forest.size, nzs = worldZ / forest.size;
                float volc = fractalNoise(nxs * 1.6f + 61f, nzs * 1.6f - 22f, seed + 404);
                int biome = biomeFromClimate(temp, moist, volc);
                if (y > forest.heightScale * 0.85f && biome != BIOME_VOLCANO) biome = BIOME_SNOW;

                float riverAmt = riverInfluence(nxs, nzs, seed);
                float heightFactor = clamp((y + forest.heightScale * 0.4f) / (forest.heightScale * 1.2f), 0f, 1f);
                float shade = 0.75f + heightFactor * 0.35f;
                Color c = biomeColor(biome, shade, riverAmt);
                // Cave mouths: darken rock
                float caveN = fractalNoise(nxs * 9f + 40f, nzs * 9f - 15f, seed + 909);
                if (caveN > 0.87f) {
                    float cs = (caveN - 0.87f) / 0.13f;
                    int cr = (int) ((c.r() & 0xFF) * (1f - cs * 0.55f) + 35 * cs);
                    int cg = (int) ((c.g() & 0xFF) * (1f - cs * 0.55f) + 32 * cs);
                    int cb = (int) ((c.b() & 0xFF) * (1f - cs * 0.55f) + 30 * cs);
                    c = Helpers.newColor(cr, cg, cb, 255);
                }
                // Magma pool glow on volcano flats
                if (biome == BIOME_VOLCANO) {
                    float crater = fractalNoise(nxs * 5.5f + 2f, nzs * 5.5f + 8f, seed + 606);
                    if (crater > 0.72f) {
                        float ms = (crater - 0.72f) / 0.28f;
                        int cr = (int) ((c.r() & 0xFF) * (1f - ms) + 220 * ms);
                        int cg = (int) ((c.g() & 0xFF) * (1f - ms) + 70 * ms);
                        int cb = (int) ((c.b() & 0xFF) * (1f - ms) + 25 * ms);
                        c = Helpers.newColor(cr, cg, cb, 255);
                    }
                }
                colors.put(i * 4, (byte) clamp(c.r() & 0xFF, 0f, 255f));
                colors.put(i * 4 + 1, (byte) clamp(c.g() & 0xFF, 0f, 255f));
                colors.put(i * 4 + 2, (byte) clamp(c.b() & 0xFF, 0f, 255f));
                colors.put(i * 4 + 3, (byte) 255);
            }
        }

        long index = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                short a = (short) (z * vertsPerSide + x);
                short b = (short) (z * vertsPerSide + x + 1);
                short c = (short) ((z + 1) * vertsPerSide + x);
                short d = (short) ((z + 1) * vertsPerSide + x + 1);
                indices.put(index++, a); indices.put(index++, c); indices.put(index++, b);
                indices.put(index++, b); indices.put(index++, c); indices.put(index++, d);
            }
        }

        mesh.vertices(vertices);
        mesh.normals(normals);
        mesh.texcoords(texcoords);
        mesh.colors(colors);
        mesh.indices(indices);
        UploadMesh(mesh, false);

        forest.terrainModel = LoadModelFromMesh(mesh);

        Texture grassTex = LoadTexture("assets/textures/grass1.png");
        if (grassTex != null && grassTex.id() != 0) {
            SetTextureFilter(grassTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(grassTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.terrainModel.materials().position(0), MATERIAL_MAP_DIFFUSE, grassTex);
        }
        forest.terrainModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);

        Texture barkTex = LoadTexture("assets/textures/bark1.png");
        boolean haveBark = barkTex != null && barkTex.id() != 0;
        if (haveBark) {
            SetTextureFilter(barkTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(barkTex, TEXTURE_WRAP_REPEAT);
        }

        // ---- HYBRID TREE PART MODELS ----
        // Semi-rounded trunks: low-segment cylinders look like Minecraft logs
        // with softened edges. Leaves are 1-block cubes (Minecraft-style).
        forest.trunkModel = LoadModelFromMesh(GenMeshCylinder(0.32f, 1.0f, 8)); // 1 block tall segment
        forest.trunkFatModel = LoadModelFromMesh(GenMeshCylinder(0.42f, 1.0f, 8)); // jungle/old-growth trunk
        forest.leafBlockModel = LoadModelFromMesh(GenMeshCube(1.0f, 1.0f, 1.0f));
        forest.leafBlockSmallModel = LoadModelFromMesh(GenMeshCube(0.55f, 0.55f, 0.55f));
        forest.spruceBlockModel = LoadModelFromMesh(GenMeshCone(0.55f, 1.0f, 4)); // semi-rounded spruce tier
        forest.cactusBlockModel = LoadModelFromMesh(GenMeshCube(0.55f, 1.0f, 0.55f));
        forest.acaciaTrunkModel = LoadModelFromMesh(GenMeshCylinder(0.28f, 1.0f, 6));

        if (haveBark) {
            SetMaterialTexture(forest.trunkModel.materials().position(0), MATERIAL_MAP_DIFFUSE, barkTex);
            forest.trunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
            SetMaterialTexture(forest.trunkFatModel.materials().position(0), MATERIAL_MAP_DIFFUSE, barkTex);
            forest.trunkFatModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
            SetMaterialTexture(forest.acaciaTrunkModel.materials().position(0), MATERIAL_MAP_DIFFUSE, barkTex);
            forest.acaciaTrunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.trunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(104, 78, 47, 255));
            forest.trunkFatModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(94, 70, 42, 255));
            forest.acaciaTrunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(110, 85, 50, 255));
        }
        // leaf blocks default gray; tinted per draw call
        forest.leafBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(WHITE);
        forest.leafBlockSmallModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(WHITE);
        forest.spruceBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(WHITE);
        forest.cactusBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(62, 132, 62, 255));

        // Rock + magma prop meshes (textured when assets present)
        forest.rockBlockModel = LoadModelFromMesh(GenMeshCube(1.0f, 1.0f, 1.0f));
        forest.magmaBlockModel = LoadModelFromMesh(GenMeshCube(1.0f, 0.2f, 1.0f));
        Texture rockTex = LoadTexture("assets/textures/rock1.png");
        if (rockTex == null || rockTex.id() == 0)
            rockTex = LoadTexture("assets/textures/stone1.png");
        if (rockTex != null && rockTex.id() != 0) {
            SetTextureFilter(rockTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(rockTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.rockBlockModel.materials().position(0), MATERIAL_MAP_DIFFUSE, rockTex);
            forest.rockBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.rockBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(110, 105, 100, 255));
        }
        Texture magmaTex = LoadTexture("assets/textures/magma1.png");
        if (magmaTex != null && magmaTex.id() != 0) {
            SetTextureFilter(magmaTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(magmaTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.magmaBlockModel.materials().position(0), MATERIAL_MAP_DIFFUSE, magmaTex);
            forest.magmaBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.magmaBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(255, 90, 20, 255));
        }

        // ---- trees ----
        final float spacing = 11.0f;
        float treeArea = forest.size * 0.47f;
        for (float zPos = -treeArea; zPos <= treeArea; zPos += spacing) {
            for (float xPos = -treeArea; xPos <= treeArea; xPos += spacing) {
                float jitterX = (hash2D((int) (xPos * 10), (int) (zPos * 10), seed) - 0.5f) * 4f;
                float jitterZ = (hash2D((int) (zPos * 10), (int) (xPos * 10), seed + 77) - 0.5f) * 4f;
                float px = xPos + jitterX;
                float pz = zPos + jitterZ;
                if (px * px + pz * pz < 20f * 20f) continue; // spawn bowl
                if (riverInfluence(px / forest.size, pz / forest.size, seed) > 0.4f) continue;

                float temp = temperatureAt(px, pz, forest.size, seed);
                float moist = moistureAt(px, pz, forest.size, seed);
                float nxs = px / forest.size, nzs = pz / forest.size;
                float volc = fractalNoise(nxs * 1.6f + 61f, nzs * 1.6f - 22f, seed + 404);
                int biome = biomeFromClimate(temp, moist, volc);
                float groundY = heightAt(px, pz, forest.size, forest.heightScale, seed);
                if (groundY > forest.heightScale * 0.85f && biome != BIOME_VOLCANO) biome = BIOME_SNOW;

                float density = hash2D((int) (xPos * 3), (int) (zPos * 3), seed + 991);
                if (density > BIOME_DENSITY[biome] / 100f) continue;

                int[] kinds = BIOME_TREES[biome];
                if (kinds.length == 0) continue;

                ForestTree tree = new ForestTree();
                tree.position = Helpers.newVector3(px, groundY, pz);
                tree.biome = biome;
                tree.colorVariant = (int) (hash2D((int) (xPos * 11), (int) (zPos * 11), seed + 55) * 8f) % 8;
                tree.rotation = hash2D((int) (xPos * 13), (int) (zPos * 13), seed + 29) * 360f;
                tree.biomeType = kinds[(int) (hash2D((int) (xPos * 17), (int) (zPos * 17), seed + 3) * kinds.length) % kinds.length];

                float sizeRoll = hash2D((int) (xPos * 7), (int) (zPos * 7), seed + 17);
                switch (tree.biomeType) {
                    case 1: tree.scale = 0.85f + sizeRoll * 0.8f; break;   // spruce
                    case 2: tree.scale = 0.8f + sizeRoll * 0.6f; break;    // autumn oak
                    case 3: tree.scale = 0.5f + sizeRoll * 0.35f; break;   // bush
                    case 4: tree.scale = 1.0f + sizeRoll * 0.6f; break;    // acacia
                    case 5: tree.scale = 0.9f + sizeRoll * 0.7f; break;    // cactus
                    case 6: tree.scale = 1.2f + sizeRoll * 1.0f; break;    // jungle
                    default: tree.scale = 0.85f + sizeRoll * 0.7f; break; // oak
                }
                forest.trees.add(tree);
            }
        }

        // Landmark spruces on ridges
        for (int i = 0; i < 5; i++) {
            float angle = hash2D(i, 0, seed + 456) * 360f;
            float distance = 60f + hash2D(i, 1, seed + 789) * 110f;
            float px = (float) (Math.cos(Math.toRadians(angle)) * distance);
            float pz = (float) (Math.sin(Math.toRadians(angle)) * distance);
            ForestTree landmark = new ForestTree();
            landmark.position = Helpers.newVector3(px,
                heightAt(px, pz, forest.size, forest.heightScale, seed), pz);
            landmark.scale = 1.5f + hash2D(i, 2, seed + 111) * 0.7f;
            landmark.rotation = hash2D(i, 3, seed + 222) * 360f;
            landmark.biomeType = 1;
            landmark.biome = BIOME_SNOW;
            landmark.colorVariant = i % 4;
            forest.trees.add(landmark);
        }

        // ---- vegetation (grass tufts + flowers, no collision) ----
        final float vegSpacing = 5.0f;
        float vegArea = forest.size * 0.46f;
        for (float zPos = -vegArea; zPos <= vegArea; zPos += vegSpacing) {
            for (float xPos = -vegArea; xPos <= vegArea; xPos += vegSpacing) {
                float jitterX = (hash2D((int) (xPos * 7), (int) (zPos * 7), seed + 202) - 0.5f) * vegSpacing;
                float jitterZ = (hash2D((int) (zPos * 7), (int) (xPos * 7), seed + 303) - 0.5f) * vegSpacing;
                float px = xPos + jitterX;
                float pz = zPos + jitterZ;
                if (px * px + pz * pz < 16f) continue;
                if (riverInfluence(px / forest.size, pz / forest.size, seed) > 0.35f) continue;

                float temp = temperatureAt(px, pz, forest.size, seed);
                float moist = moistureAt(px, pz, forest.size, seed);
                float nxs = px / forest.size, nzs = pz / forest.size;
                float volc = fractalNoise(nxs * 1.6f + 61f, nzs * 1.6f - 22f, seed + 404);
                int biome = biomeFromClimate(temp, moist, volc);
                float groundY = heightAt(px, pz, forest.size, forest.heightScale, seed);
                if (groundY > forest.heightScale * 0.85f && biome != BIOME_VOLCANO) biome = BIOME_SNOW;
                // no plants underwater
                if (groundY < forest.waterLevel + 0.2f) continue;

                float roll = hash2D((int) (xPos * 21), (int) (zPos * 21), seed + 444);
                if (roll > BIOME_VEG_DENSITY[biome] / 100f) continue;

                Vegetation veg = new Vegetation();
                veg.position = Helpers.newVector3(px, groundY, pz);
                float flowerRoll = hash2D((int) (xPos * 23), (int) (zPos * 23), seed + 555);
                boolean flower = flowerRoll < BIOME_FLOWER_CHANCE[biome] / 100f;
                veg.kind = flower ? 1 : 0;
                veg.variant = flower
                    ? (int) (hash2D((int) (xPos * 29), (int) (zPos * 29), seed + 666) * 4f) % 4
                    : (int) (hash2D((int) (xPos * 29), (int) (zPos * 29), seed + 666) * 4f) % 4;
                veg.rotation = hash2D((int) (xPos * 31), (int) (zPos * 31), seed + 777) * 360f;
                veg.swayPhase = hash2D((int) (xPos * 31), (int) (zPos * 31), seed + 888) * 6.283f;
                forest.vegetation.add(veg);
            }
        }

        // ---- caves (mouth markers on strong sinkholes) ----
        final float caveSpacing = 28f;
        float caveArea = forest.size * 0.45f;
        for (float zPos = -caveArea; zPos <= caveArea; zPos += caveSpacing) {
            for (float xPos = -caveArea; xPos <= caveArea; xPos += caveSpacing) {
                float jx = (hash2D((int)(xPos*3), (int)(zPos*3), seed + 1201) - 0.5f) * 10f;
                float jz = (hash2D((int)(zPos*3), (int)(xPos*3), seed + 1301) - 0.5f) * 10f;
                float px = xPos + jx, pz = zPos + jz;
                if (px*px + pz*pz < 30f*30f) continue;
                float nxs = px / forest.size, nzs = pz / forest.size;
                float caveN = fractalNoise(nxs * 9f + 40f, nzs * 9f - 15f, seed + 909);
                if (caveN < 0.90f) continue;
                float gy = heightAt(px, pz, forest.size, forest.heightScale, seed);
                CaveFeature cave = new CaveFeature();
                cave.position = Helpers.newVector3(px, gy, pz);
                cave.radius = 2.2f + (caveN - 0.90f) * 12f;
                cave.depth = 3f + (caveN - 0.90f) * 18f;
                cave.variant = (int)(hash2D((int)(xPos), (int)(zPos), seed + 1401) * 5f) % 5;
                forest.caves.add(cave);
            }
        }

        // ---- magma pools (volcano biome + hot ridges) ----
        final float magmaSpacing = 22f;
        for (float zPos = -caveArea; zPos <= caveArea; zPos += magmaSpacing) {
            for (float xPos = -caveArea; xPos <= caveArea; xPos += magmaSpacing) {
                float jx = (hash2D((int)(xPos*5), (int)(zPos*5), seed + 1501) - 0.5f) * 8f;
                float jz = (hash2D((int)(zPos*5), (int)(xPos*5), seed + 1601) - 0.5f) * 8f;
                float px = xPos + jx, pz = zPos + jz;
                float nxs = px / forest.size, nzs = pz / forest.size;
                float volc = fractalNoise(nxs * 1.6f + 61f, nzs * 1.6f - 22f, seed + 404);
                int biome = biomeFromClimate(
                    temperatureAt(px, pz, forest.size, seed),
                    moistureAt(px, pz, forest.size, seed), volc);
                float crater = fractalNoise(nxs * 5.5f + 2f, nzs * 5.5f + 8f, seed + 606);
                boolean hot = (biome == BIOME_VOLCANO && crater > 0.74f)
                           || (volc > 0.82f && crater > 0.80f);
                if (!hot) continue;
                float gy = heightAt(px, pz, forest.size, forest.heightScale, seed);
                MagmaPool pool = new MagmaPool();
                pool.position = Helpers.newVector3(px, gy + 0.05f, pz);
                pool.radius = 2.5f + (crater - 0.74f) * 10f;
                pool.pulsePhase = hash2D((int)(xPos), (int)(zPos), seed + 1701) * 6.28f;
                forest.magmaPools.add(pool);
            }
        }

        // ---- rock outcrops (desert / mountain / volcano segments) ----
        final float rockSpacing = 16f;
        for (float zPos = -caveArea; zPos <= caveArea; zPos += rockSpacing) {
            for (float xPos = -caveArea; xPos <= caveArea; xPos += rockSpacing) {
                float jx = (hash2D((int)(xPos*9), (int)(zPos*9), seed + 1801) - 0.5f) * 12f;
                float jz = (hash2D((int)(zPos*9), (int)(xPos*9), seed + 1901) - 0.5f) * 12f;
                float px = xPos + jx, pz = zPos + jz;
                if (px*px + pz*pz < 18f*18f) continue;
                float nxs = px / forest.size, nzs = pz / forest.size;
                float volc = fractalNoise(nxs * 1.6f + 61f, nzs * 1.6f - 22f, seed + 404);
                int biome = biomeFromClimate(
                    temperatureAt(px, pz, forest.size, seed),
                    moistureAt(px, pz, forest.size, seed), volc);
                // denser rocks in desert, snow peaks, volcano
                float chance = 0.08f;
                if (biome == BIOME_DESERT) chance = 0.35f;
                else if (biome == BIOME_SNOW) chance = 0.28f;
                else if (biome == BIOME_VOLCANO) chance = 0.45f;
                else if (biome == BIOME_SAVANNA) chance = 0.18f;
                if (hash2D((int)(xPos*4), (int)(zPos*4), seed + 2001) > chance) continue;
                float gy = heightAt(px, pz, forest.size, forest.heightScale, seed);
                if (gy < forest.waterLevel + 0.3f) continue;
                RockOutcrop rock = new RockOutcrop();
                rock.position = Helpers.newVector3(px, gy, pz);
                rock.scale = 0.6f + hash2D((int)(xPos), (int)(zPos), seed + 2101) * 1.4f;
                rock.rotation = hash2D((int)(xPos), (int)(zPos), seed + 2201) * 360f;
                rock.variant = (int)(hash2D((int)(xPos), (int)(zPos), seed + 2301) * 5f) % 5;
                rock.blocks = 1 + (int)(hash2D((int)(xPos), (int)(zPos), seed + 2401) * 3.5f);
                forest.rocks.add(rock);
            }
        }

        return forest;
    }

    // ---- tree drawing (hybrid: semi-rounded trunks + blocky leaf canopies) ----

    // 8 tints each — colorVariant uses & 7
    private static final Color[] K_LEAF = {
        Helpers.newColor(52, 118, 45, 255), Helpers.newColor(64, 132, 52, 255),
        Helpers.newColor(44, 105, 40, 255), Helpers.newColor(72, 142, 58, 255),
        Helpers.newColor(90, 155, 70, 255), Helpers.newColor(38, 95, 48, 255),
        Helpers.newColor(110, 168, 85, 255), Helpers.newColor(58, 125, 55, 255)
    };
    private static final Color[] K_LEAF_AUTUMN = {
        Helpers.newColor(196, 108, 42, 255), Helpers.newColor(204, 78, 62, 255),
        Helpers.newColor(214, 150, 52, 255), Helpers.newColor(150, 88, 60, 255),
        Helpers.newColor(230, 170, 55, 255), Helpers.newColor(180, 55, 45, 255),
        Helpers.newColor(200, 120, 40, 255), Helpers.newColor(160, 95, 50, 255)
    };
    private static final Color[] K_LEAF_SPRUCE = {
        Helpers.newColor(32, 84, 48, 255), Helpers.newColor(38, 95, 52, 255),
        Helpers.newColor(28, 76, 44, 255), Helpers.newColor(44, 104, 56, 255),
        Helpers.newColor(25, 70, 55, 255), Helpers.newColor(50, 110, 70, 255),
        Helpers.newColor(35, 90, 60, 255), Helpers.newColor(20, 65, 50, 255)
    };
    private static final Color[] K_LEAF_SNOW = {
        Helpers.newColor(178, 200, 192, 255), Helpers.newColor(190, 210, 205, 255),
        Helpers.newColor(170, 195, 188, 255), Helpers.newColor(200, 218, 212, 255),
        Helpers.newColor(160, 185, 200, 255), Helpers.newColor(210, 220, 215, 255),
        Helpers.newColor(185, 195, 210, 255), Helpers.newColor(175, 205, 195, 255)
    };
    private static final Color[] K_LEAF_JUNGLE = {
        Helpers.newColor(30, 118, 48, 255), Helpers.newColor(36, 132, 54, 255),
        Helpers.newColor(26, 108, 42, 255), Helpers.newColor(42, 140, 60, 255),
        Helpers.newColor(20, 100, 55, 255), Helpers.newColor(50, 150, 70, 255),
        Helpers.newColor(15, 90, 40, 255), Helpers.newColor(55, 145, 80, 255)
    };
    private static final Color[] K_LEAF_BIRCH = {
        Helpers.newColor(140, 175, 110, 255), Helpers.newColor(155, 190, 120, 255),
        Helpers.newColor(130, 165, 100, 255), Helpers.newColor(165, 200, 130, 255),
        Helpers.newColor(120, 160, 95, 255), Helpers.newColor(175, 205, 140, 255),
        Helpers.newColor(150, 180, 115, 255), Helpers.newColor(135, 170, 105, 255)
    };
    private static final Color[] K_LEAF_BLOSSOM = {
        Helpers.newColor(230, 150, 175, 255), Helpers.newColor(240, 180, 195, 255),
        Helpers.newColor(220, 130, 160, 255), Helpers.newColor(250, 200, 210, 255),
        Helpers.newColor(235, 140, 170, 255), Helpers.newColor(245, 190, 200, 255),
        Helpers.newColor(225, 160, 180, 255), Helpers.newColor(255, 210, 220, 255)
    };
    private static final Color[] K_ROCK = {
        Helpers.newColor(120, 115, 110, 255), Helpers.newColor(95, 90, 88, 255),
        Helpers.newColor(140, 130, 120, 255), Helpers.newColor(80, 78, 75, 255),
        Helpers.newColor(160, 145, 130, 255)
    };

    private static void drawTrunk(ForestTerrain f, Vector3 pos, float scale, int blocks, boolean fat) {
        Model m = fat ? f.trunkFatModel : f.trunkModel;
        for (int i = 0; i < blocks; i++) {
            DrawModel(m, Helpers.newVector3(pos.x(), pos.y() + (i + 0.5f) * scale, pos.z()),
                     scale, WHITE);
        }
    }

    /** Draws a Minecraft-style blobby canopy: cluster of leaf cubes around a center block. */
    private static void drawLeafBlob(ForestTerrain f, Vector3 center, float s, Color leaf, int variant) {
        DrawModel(f.leafBlockModel, center, s, leaf);
        // surrounding partial blocks soften the silhouette while staying blocky
        float off = 0.5f * s;
        DrawModel(f.leafBlockSmallModel,
            Helpers.newVector3(center.x() + off, center.y() - 0.1f * s, center.z()), s, leaf);
        DrawModel(f.leafBlockSmallModel,
            Helpers.newVector3(center.x() - off, center.y() - 0.1f * s, center.z()), s, leaf);
        DrawModel(f.leafBlockSmallModel,
            Helpers.newVector3(center.x(), center.y() - 0.1f * s, center.z() + off), s, leaf);
        DrawModel(f.leafBlockSmallModel,
            Helpers.newVector3(center.x(), center.y() - 0.1f * s, center.z() - off), s, leaf);
        if (variant >= 2) {
            DrawModel(f.leafBlockSmallModel,
                Helpers.newVector3(center.x() + off * 0.7f, center.y() + 0.35f * s, center.z() - off * 0.7f), s, leaf);
        }
    }

    private static void drawTree(ForestTerrain f, ForestTree t, boolean lod) {
        float s = t.scale;
        float x = t.position.x(), y0 = t.position.y(), z = t.position.z();
        int cv = t.colorVariant & 7;

        switch (t.biomeType) {
            case 1: { // spruce: trunk + stacked shrinking cone tiers
                int trunkBlocks = Math.max(2, (int) (2.5f * s));
                drawTrunk(f, t.position, s, trunkBlocks, false);
                Color leaf = (t.biome == BIOME_SNOW) ? K_LEAF_SNOW[cv] : K_LEAF_SPRUCE[cv];
                float baseY = y0 + trunkBlocks * s;
                int tiers = lod ? 3 : 4;
                for (int i = 0; i < tiers; i++) {
                    float tierS = s * (0.95f - i * 0.16f);
                    DrawModel(f.spruceBlockModel,
                        Helpers.newVector3(x, baseY + (i + 0.5f) * 0.85f * s, z), tierS, leaf);
                }
                DrawModel(f.spruceBlockModel, Helpers.newVector3(x, baseY + tiers * 0.85f * s, z),
                         s * 0.35f, leaf);
                return;
            }
            case 2: { // autumn oak
                drawTreeOak(f, t, K_LEAF_AUTUMN[cv], lod);
                return;
            }
            case 3: { // bush: short trunk + one leaf blob near the ground
                drawTrunk(f, t.position, s, 1, false);
                Color leaf = K_LEAF[cv];
                drawLeafBlob(f, Helpers.newVector3(x, y0 + 1.0f * s, z), s, leaf, cv);
                return;
            }
            case 4: { // acacia: tall trunk + flat wide canopy
                int trunkBlocks = Math.max(3, (int) (3.2f * s));
                // slight lean: draw trunk segments with a small offset ramp
                for (int i = 0; i < trunkBlocks; i++) {
                    float lean = i * 0.06f * s;
                    DrawModel(f.acaciaTrunkModel,
                        Helpers.newVector3(x + lean, y0 + (i + 0.5f) * s, z - lean * 0.5f), s, WHITE);
                }
                Color leaf = K_LEAF_AUTUMN[cv];
                float topY = y0 + trunkBlocks * s;
                drawLeafBlob(f, Helpers.newVector3(x + trunkBlocks * 0.06f * s, topY, z - trunkBlocks * 0.03f * s),
                             s * 1.1f, leaf, cv);
                if (!lod) {
                    DrawModel(f.leafBlockModel, Helpers.newVector3(x + 0.9f * s, topY - 0.1f * s, z), s * 0.8f, leaf);
                    DrawModel(f.leafBlockModel, Helpers.newVector3(x - 0.9f * s, topY - 0.1f * s, z + 0.5f * s), s * 0.8f, leaf);
                }
                return;
            }
            case 5: { // cactus: stacked rounded blocks + arm
                int blocks = Math.max(2, (int) (2.4f * s));
                for (int i = 0; i < blocks; i++) {
                    DrawModel(f.cactusBlockModel, Helpers.newVector3(x, y0 + (i + 0.5f) * s, z), s, WHITE);
                }
                if (!lod) {
                    DrawModel(f.cactusBlockModel, Helpers.newVector3(x + 0.5f * s, y0 + blocks * s * 0.6f, z), s * 0.7f, WHITE);
                    DrawModel(f.cactusBlockModel, Helpers.newVector3(x + 0.5f * s, y0 + blocks * s * 0.6f + 0.7f * s, z), s * 0.7f, WHITE);
                }
                return;
            }
            case 6: { // jungle: fat trunk, tall, big blocky canopy + vines
                int trunkBlocks = Math.max(4, (int) (4.5f * s));
                drawTrunk(f, t.position, s, trunkBlocks, true);
                Color leaf = K_LEAF_JUNGLE[cv];
                float topY = y0 + trunkBlocks * s;
                drawLeafBlob(f, Helpers.newVector3(x, topY, z), s * 1.15f, leaf, cv);
                if (!lod) {
                    drawLeafBlob(f, Helpers.newVector3(x + 0.8f * s, topY - 0.6f * s, z), s * 0.9f, leaf, cv);
                    drawLeafBlob(f, Helpers.newVector3(x - 0.7f * s, topY - 0.4f * s, z + 0.4f * s), s * 0.85f, leaf, cv);
                    // hanging vine blocks
                    for (int i = 0; i < 3; i++) {
                        DrawModel(f.leafBlockSmallModel,
                            Helpers.newVector3(x + 0.5f * s, topY - (0.5f + i) * s, z + 0.5f * s), s * 0.5f,
                            Helpers.newColor(48, 105, 45, 255));
                    }
                }
                return;
            }
            default: { // oak — green, birch, or rare blossom
                Color leaf;
                if (cv == 6) leaf = K_LEAF_BIRCH[cv];
                else if (cv == 7) leaf = K_LEAF_BLOSSOM[cv];
                else leaf = K_LEAF[cv];
                drawTreeOak(f, t, leaf, lod);
            }
        }
    }

    private static void drawTreeOak(ForestTerrain f, ForestTree t, Color leaf, boolean lod) {
        float s = t.scale;
        float x = t.position.x(), y0 = t.position.y(), z = t.position.z();
        int cv = t.colorVariant & 7;
        int trunkBlocks = Math.max(2, (int) (2.8f * s));
        drawTrunk(f, t.position, s, trunkBlocks, false);
        float baseY = y0 + trunkBlocks * s;
        // Minecraft oak shape: 2x2 leaf layer, 1x2 layer above, cap block
        float u = 0.5f * s;
        DrawModel(f.leafBlockModel, Helpers.newVector3(x + u, baseY, z), s, leaf);
        DrawModel(f.leafBlockModel, Helpers.newVector3(x - u, baseY, z), s, leaf);
        DrawModel(f.leafBlockModel, Helpers.newVector3(x, baseY, z + u), s, leaf);
        DrawModel(f.leafBlockModel, Helpers.newVector3(x, baseY, z - u), s, leaf);
        DrawModel(f.leafBlockModel, Helpers.newVector3(x, baseY + s, z), s, leaf);
        DrawModel(f.leafBlockModel, Helpers.newVector3(x, baseY + s, z + u), s, leaf);
        if (cv >= 2) DrawModel(f.leafBlockModel, Helpers.newVector3(x + u, baseY + s, z), s, leaf);
        if (!lod) {
            DrawModel(f.leafBlockModel, Helpers.newVector3(x - u, baseY + s, z - u * 0.5f), s * 0.9f, Fade(leaf, 0.92f));
            DrawModel(f.leafBlockModel, Helpers.newVector3(x + u * 0.6f, baseY - 0.4f * s, z - u), s * 0.85f, Fade(leaf, 0.88f));
        }
    }

    // ---- vegetation drawing (no collision) ----

    private static final Color[] K_TUFT = {
        Helpers.newColor(70, 140, 55, 255), Helpers.newColor(85, 155, 62, 255),
        Helpers.newColor(60, 125, 48, 255), Helpers.newColor(95, 165, 70, 255)
    };
    private static final Color[] K_FLOWER = {
        Helpers.newColor(220, 70, 70, 255),   // red
        Helpers.newColor(235, 210, 70, 255),  // yellow
        Helpers.newColor(90, 110, 230, 255),  // blue
        Helpers.newColor(240, 240, 240, 255)  // white
    };

    public static void drawVegetation(ForestTerrain forest, Vector3 camPos, float time) {
        float maxDistSq = 70f * 70f; // vegetation draws only near the player
        for (Vegetation veg : forest.vegetation) {
            float dx = veg.position.x() - camPos.x();
            float dz = veg.position.z() - camPos.z();
            if (dx * dx + dz * dz > maxDistSq) continue;

            float x = veg.position.x(), y = veg.position.y(), z = veg.position.z();
            float sway = (float) Math.sin(time * 1.6f + veg.swayPhase) * 0.06f;

            if (veg.kind == 0) {
                // grass tuft: 3 thin blades in a fan, swaying
                Color c = K_TUFT[veg.variant & 3];
                DrawCube(Helpers.newVector3(x + sway, y + 0.22f, z), 0.07f, 0.44f, 0.07f, c);
                DrawCube(Helpers.newVector3(x + 0.08f - sway, y + 0.16f, z + 0.05f), 0.06f, 0.32f, 0.06f, c);
                DrawCube(Helpers.newVector3(x - 0.07f + sway, y + 0.15f, z - 0.05f), 0.06f, 0.30f, 0.06f, c);
            } else {
                // flower: thin stem + small colored head cube
                DrawCube(Helpers.newVector3(x + sway * 0.5f, y + 0.2f, z), 0.05f, 0.4f, 0.05f,
                         Helpers.newColor(60, 130, 50, 255));
                DrawCube(Helpers.newVector3(x + sway, y + 0.44f, z), 0.16f, 0.16f, 0.16f,
                         K_FLOWER[veg.variant & 3]);
            }
        }
    }

    // ---- main terrain draw ----

    public static void drawForestTerrain(ForestTerrain forest, Vector3 camPos, float maxDist) {
        drawForestTerrain(forest, camPos, maxDist, 0f);
    }

    public static void drawForestTerrain(ForestTerrain forest, Vector3 camPos, float maxDist, float time) {
        DrawModel(forest.terrainModel, Helpers.newVector3(0, 0, 0), 1f, WHITE);

        // Water plane at the carved river level
        DrawCube(Helpers.newVector3(0, forest.waterLevel - 0.08f, 0), forest.size, 0.16f, forest.size,
                 Fade(Helpers.newColor(60, 120, 170, 255), 0.65f));

        float maxDistSq = maxDist * maxDist;
        float lodFarSq = (maxDist * 0.55f) * (maxDist * 0.55f);
        float propDistSq = (maxDist * 0.85f) * (maxDist * 0.85f);

        // Magma pools (pulsing emissive surface)
        if (forest.magmaPools != null) {
            for (MagmaPool pool : forest.magmaPools) {
                float dx = pool.position.x() - camPos.x();
                float dz = pool.position.z() - camPos.z();
                if (dx*dx + dz*dz > propDistSq) continue;
                float pulse = 0.85f + 0.15f * (float)Math.sin(time * 2.2f + pool.pulsePhase);
                float r = pool.radius;
                Color lava = Helpers.newColor(
                    (int)(255 * pulse), (int)(70 + 50 * pulse), 15, 255);
                // tiled magma blocks across the pool
                int tiles = Math.max(2, (int)(r * 1.2f));
                float step = (r * 2f) / tiles;
                for (int iz = 0; iz < tiles; iz++) {
                    for (int ix = 0; ix < tiles; ix++) {
                        float lx = pool.position.x() - r + (ix + 0.5f) * step;
                        float lz = pool.position.z() - r + (iz + 0.5f) * step;
                        float dx2 = lx - pool.position.x(), dz2 = lz - pool.position.z();
                        if (dx2*dx2 + dz2*dz2 > r*r) continue;
                        DrawModel(forest.magmaBlockModel,
                            Helpers.newVector3(lx, pool.position.y(), lz),
                            step * 0.95f, lava);
                    }
                }
            }
        }

        // Cave mouths — dark pit + rock ring
        if (forest.caves != null) {
            for (CaveFeature cave : forest.caves) {
                float dx = cave.position.x() - camPos.x();
                float dz = cave.position.z() - camPos.z();
                if (dx*dx + dz*dz > propDistSq) continue;
                // dark hole
                DrawCube(Helpers.newVector3(cave.position.x(), cave.position.y() - cave.depth * 0.35f, cave.position.z()),
                         cave.radius * 1.4f, cave.depth * 0.7f, cave.radius * 1.4f,
                         Helpers.newColor(18, 16, 14, 255));
                // rock ring
                Color rc = K_ROCK[cave.variant % K_ROCK.length];
                int stones = 6;
                for (int i = 0; i < stones; i++) {
                    float ang = i * (float)(Math.PI * 2.0 / stones) + cave.variant * 0.2f;
                    float sx = cave.position.x() + (float)Math.cos(ang) * cave.radius * 0.85f;
                    float sz = cave.position.z() + (float)Math.sin(ang) * cave.radius * 0.85f;
                    float sc = 0.7f + (i % 3) * 0.15f;
                    DrawModelEx(forest.rockBlockModel,
                        Helpers.newVector3(sx, cave.position.y() + sc * 0.35f, sz),
                        Helpers.newVector3(0, 1, 0), i * 40f,
                        Helpers.newVector3(sc, sc * 0.8f, sc), rc);
                }
            }
        }

        // Rock outcrops
        if (forest.rocks != null && forest.rockBlockModel != null) {
            for (RockOutcrop rock : forest.rocks) {
                float dx = rock.position.x() - camPos.x();
                float dz = rock.position.z() - camPos.z();
                if (dx*dx + dz*dz > propDistSq) continue;
                Color rc = K_ROCK[rock.variant % K_ROCK.length];
                for (int b = 0; b < rock.blocks; b++) {
                    float ox = (b % 2) * 0.25f * rock.scale;
                    float oz = ((b / 2) % 2) * 0.2f * rock.scale;
                    DrawModelEx(forest.rockBlockModel,
                        Helpers.newVector3(rock.position.x() + ox,
                            rock.position.y() + (b + 0.5f) * rock.scale * 0.55f,
                            rock.position.z() + oz),
                        Helpers.newVector3(0, 1, 0), rock.rotation + b * 18f,
                        Helpers.newVector3(rock.scale * (1f - b * 0.08f),
                            rock.scale * 0.55f,
                            rock.scale * (1f - b * 0.08f)), rc);
                }
            }
        }

        for (ForestTree tree : forest.trees) {
            float dx = tree.position.x() - camPos.x();
            float dz = tree.position.z() - camPos.z();
            float distSq = dx * dx + dz * dz;
            if (distSq > maxDistSq) continue;
            drawTree(forest, tree, distSq > lodFarSq);
        }

        drawVegetation(forest, camPos, time);
    }

    public static void unloadForestTerrain(ForestTerrain forest) {
        if (forest == null) return;
        // Guard each model — JavaCPP/raylib double-free → STATUS_HEAP_CORRUPTION on Windows
        unloadModelSafe(forest.terrainModel); forest.terrainModel = null;
        unloadModelSafe(forest.trunkModel); forest.trunkModel = null;
        unloadModelSafe(forest.trunkFatModel); forest.trunkFatModel = null;
        unloadModelSafe(forest.leafBlockModel); forest.leafBlockModel = null;
        unloadModelSafe(forest.leafBlockSmallModel); forest.leafBlockSmallModel = null;
        unloadModelSafe(forest.spruceBlockModel); forest.spruceBlockModel = null;
        unloadModelSafe(forest.cactusBlockModel); forest.cactusBlockModel = null;
        unloadModelSafe(forest.acaciaTrunkModel); forest.acaciaTrunkModel = null;
        unloadModelSafe(forest.rockBlockModel); forest.rockBlockModel = null;
        unloadModelSafe(forest.magmaBlockModel); forest.magmaBlockModel = null;
        if (forest.trees != null) forest.trees.clear();
        if (forest.vegetation != null) forest.vegetation.clear();
        if (forest.magmaPools != null) forest.magmaPools.clear();
        if (forest.caves != null) forest.caves.clear();
        if (forest.rocks != null) forest.rocks.clear();
        if (forest.biomes != null) forest.biomes.clear();
    }

    private static void unloadModelSafe(Model m) {
        if (m == null || m.isNull()) return;
        try {
            UnloadModel(m);
        } catch (Throwable t) {
            System.err.println("UnloadModel skipped: " + t.getMessage());
        }
    }

    /** Cylinder collision against tree trunks — vegetation has no collision. */
    public static Vector3 resolveTreeCollision(ForestTerrain forest, Vector3 position, float radius) {
        for (ForestTree tree : forest.trees) {
            float trunkRadius = 0.30f * tree.scale;
            float combined = radius + trunkRadius;
            float dx = position.x() - tree.position.x();
            float dz = position.z() - tree.position.z();
            float distSq = dx * dx + dz * dz;
            if (distSq >= combined * combined || distSq < 0.000001f) {
                if (distSq < 0.000001f) { dx = 1f; dz = 0f; distSq = 1f; }
                else continue;
            }
            float dist = (float) Math.sqrt(distSq);
            float push = combined - dist;
            position.x(position.x() + (dx / dist) * push)
                    .z(position.z() + (dz / dist) * push);
        }
        return position;
    }

    static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }
}