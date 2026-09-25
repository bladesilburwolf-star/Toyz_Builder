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
    // V5: 20-biome foundation. IDs remain stable so other systems can query them.
    public static final int BIOME_SNOW = 0, BIOME_TAIGA = 1, BIOME_FOREST = 2, BIOME_MEADOW = 3;
    public static final int BIOME_AUTUMN = 4, BIOME_JUNGLE = 5, BIOME_SAVANNA = 6, BIOME_DESERT = 7;
    public static final int BIOME_SWAMP = 8, BIOME_VOLCANO = 9, BIOME_BIRCH = 10, BIOME_ALPINE = 11;
    public static final int BIOME_BADLANDS = 12, BIOME_MESA = 13, BIOME_MANGROVE = 14, BIOME_BEACH = 15;
    public static final int BIOME_HIGHLANDS = 16, BIOME_FLOWER_MEADOW = 17, BIOME_DRY_FOREST = 18;
    public static final int BIOME_FROZEN_LAKE = 19;

    public static final String[] BIOME_NAMES = {
        "Snowy Peaks", "Taiga", "Temperate Forest", "Meadow", "Autumn Woods",
        "Jungle", "Savanna", "Desert", "Swamp", "Volcanic",
        "Birch Grove", "Alpine Meadow", "Badlands", "Mesa", "Mangrove",
        "Beach", "Rocky Highlands", "Flower Meadow", "Dry Forest", "Frozen Lake"
    };

    private static final int[][] BIOME_GRASS = {
        /*Snow*/{235,240,248}, /*Taiga*/{55,90,65}, /*Forest*/{48,115,45}, /*Meadow*/{95,155,70},
        /*Autumn*/{165,110,45}, /*Jungle*/{28,125,52}, /*Savanna*/{195,175,85},
        /*Desert*/{228,200,130}, /*Swamp*/{55,80,48}, /*Volcano*/{70,55,50},
        /*Birch*/{95,145,70}, /*Alpine*/{120,155,100}, /*Badlands*/{180,95,50},
        /*Mesa*/{210,120,55}, /*Mangrove*/{48,105,68}, /*Beach*/{235,215,160},
        /*Highlands*/{95,100,95}, /*Flower*/{110,175,90}, /*DryForest*/{145,135,65},
        /*Frozen*/{185,210,220}
    };

    private static final float[] BIOME_HEIGHT_MOD = {
        1.35f,1.10f,1.00f,0.80f,0.95f,1.05f,0.85f,0.70f,0.55f,1.60f,
        0.95f,1.35f,1.15f,1.25f,0.62f,0.35f,1.45f,0.78f,0.92f,0.28f
    };

    private static final int[] BIOME_DENSITY = {
        5,55,70,12,45,85,10,3,40,0,65,18,4,3,70,2,12,38,30,8
    };

    // 0 oak, 1 spruce, 2 autumn oak, 3 bush, 4 acacia, 5 cactus, 6 jungle.
    private static final int[][] BIOME_TREES = {
        {1},{1},{0,2},{0,3},{2},{6,0},{4},{5},{3,0},{},
        {0,3},{1,0},{3},{4},{3,0},{},{1,0},{0,3},{4,0},{1}
    };

    private static final int[] BIOME_VEG_DENSITY = {
        2,15,55,90,30,70,45,2,65,0,70,75,4,4,80,5,12,100,35,2
    };
    private static final int[] BIOME_FLOWER_CHANCE = {
        0,5,25,55,20,35,15,0,30,0,30,70,0,0,45,5,5,80,10,0
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

    /** V5 open-world landform decoration. Not architecture. */
    public static class LandFeature {
        public Vector3 position;
        public float scaleX, scaleY, scaleZ;
        public float rotation;
        // 0 boulder, 1 mesa pillar, 2 sand pit, 3 snow drift, 4 basalt column, 5 cliff rock.
        public int kind;
        public int variant;
    }

    /** Local water surface generated from terrain, rather than one giant water plane. */
    public static class WaterBody {
        public Vector3 position;
        public float radiusX, radiusZ;
        public boolean frozen;
        public boolean river;
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

    public enum WorldType { FLAT, NORMAL, AMPLIFIED }

    public static class WorldConfig {
        public WorldType type = WorldType.NORMAL;
        public boolean structures = true;
        public float terrainScale = 1.0f;
        public int seed;
        public WorldConfig() {}
        public WorldConfig(int seed, WorldType type, boolean structures) { this.seed = seed; this.type = type; this.structures = structures; }
    }

    public static class ForestTerrain {
        public Model terrainModel;
        /** Real texture layers by biome (sand/rock/dirt/snow/grass). */
        public Model terrainGrass, terrainSand, terrainRock, terrainDirt, terrainSnow;
        // tree part models (trunk = semi-rounded cylinder; leaves = leaf blocks)
        public Model trunkModel, trunkFatModel;
        public Model leafBlockModel, leafBlockSmallModel;
        public Model spruceBlockModel;
        public Model cactusBlockModel;
        public Model acaciaTrunkModel;
        public Model rockBlockModel;   // GenMeshCube for outcrops / cave mouths
        public Model magmaBlockModel;  // flat-ish cube for lava surface
        public Model featureBlockModel; // V5 landforms
        /** Authored GLB logs (preferred over GenMesh cylinders). */
        public Model logVertOak, logVertPine, logVertBirch, logVertJungle;
        public Model logHorizOak, logHorizPine, logHorizBirch;
        public Model boulderSmall, boulderMed, boulderLarge;
        public List<ForestTree> trees = new ArrayList<>();
        public List<Vegetation> vegetation = new ArrayList<>();
        public List<MagmaPool> magmaPools = new ArrayList<>();
        public List<CaveFeature> caves = new ArrayList<>();
        public List<RockOutcrop> rocks = new ArrayList<>();
        public List<LandFeature> landFeatures = new ArrayList<>();
        public List<WaterBody> waterBodies = new ArrayList<>();
        public List<Biome> biomes = new ArrayList<>();
        public float size = 400f;
        public float cellSize = 4.0f;
        public float heightScale = 16f;
        public float waterLevel = 0f;
        public int seed = 0xC0FFEE;
        public WorldType worldType = WorldType.NORMAL;
        public boolean structuresEnabled = true;
        /** Terrain V2/V3 continuous world data (height, biomes, rivers, structure sites). */
        public toyz.builder.terrain.TerrainGenerator.WorldData v2;
        public Model waterOceanMesh, waterRiverMesh, waterFrozenMesh;
        public java.util.List<toyz.builder.terrain.SkyIslandGenerator.Island> skyIslands =
            new java.util.ArrayList<>();
    }

    /** Active V2 during mesh generation so heightAt() samples continuous landforms. */
    private static toyz.builder.terrain.TerrainGenerator.WorldData activeV2;
    /** Set from title menu before generateForestTerrain. */
    public static int pendingMapgenPreset = 4; // V6
    public static boolean pendingSkyIslands = false;


    private static final float WATER_LEVEL_FRAC = -0.30f;

    private Terrain() {}

    private static Model tryLoadGlb(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.isFile()) return null;
            Model m = LoadModel(path);
            if (m != null && m.meshCount() > 0) {
                System.out.println("[Terrain] GLB tree/prop: " + path);
                return m;
            }
        } catch (Throwable t) {
            System.err.println("[Terrain] GLB fail " + path + ": " + t.getMessage());
        }
        return null;
    }

    private static Model trunkForBiome(ForestTerrain f, int biomeType) {
        if (biomeType == 1 && f.logVertPine != null) return f.logVertPine;
        if (biomeType == 6 && f.logVertJungle != null) return f.logVertJungle;
        if ((biomeType == 0 || biomeType == 2) && f.logVertOak != null) return f.logVertOak;
        if (biomeType == 4 && f.logVertBirch != null) return f.logVertBirch;
        if (f.logVertOak != null) return f.logVertOak;
        return (biomeType == 6 && f.trunkFatModel != null) ? f.trunkFatModel : f.trunkModel;
    }

    private static int terrainMaterialForBiomes(int ba, int bb, int bc, int bd) {
        int[] votes = new int[5];
        votes[biomeToMat(ba)]++;
        votes[biomeToMat(bb)]++;
        votes[biomeToMat(bc)]++;
        votes[biomeToMat(bd)]++;
        int best = 0, bestV = votes[0];
        for (int i = 1; i < 5; i++) if (votes[i] > bestV) { bestV = votes[i]; best = i; }
        return best;
    }

    private static int biomeToMat(int biome) {
        if (biome == BIOME_DESERT || biome == BIOME_BEACH || biome == BIOME_SAVANNA) return 1;
        if (biome == BIOME_HIGHLANDS || biome == BIOME_ALPINE || biome == BIOME_BADLANDS
                || biome == BIOME_MESA || biome == BIOME_VOLCANO) return 2;
        if (biome == BIOME_SWAMP || biome == BIOME_MANGROVE || biome == BIOME_DRY_FOREST) return 3;
        if (biome == BIOME_SNOW || biome == BIOME_FROZEN_LAKE) return 4;
        return 0;
    }

    private static Texture loadTerrainTex(String primary, String fallback) {
        Texture t = LoadTexture(primary);
        if (t == null || t.id() == 0) t = LoadTexture(fallback);
        if (t != null && t.id() != 0) {
            SetTextureFilter(t, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(t, TEXTURE_WRAP_REPEAT);
            System.out.println("[Terrain] texture: " + primary);
            return t;
        }
        return null;
    }

    private static void assignTerrainTexture(Model model, Texture tex) {
        if (model == null || model.meshCount() < 1) return;
        if (tex != null && tex.id() != 0) {
            SetMaterialTexture(model.materials().position(0), MATERIAL_MAP_DIFFUSE, tex);
        }
        model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
    }

    private static Model buildTerrainLayer(FloatPointer vertices, FloatPointer normals,
            FloatPointer texcoords, BytePointer colors, int vertexCount,
            java.util.List<short[]> tris) {
        if (tris == null || tris.isEmpty()) return null;
        int triCount = tris.size();
        Mesh mesh = new Mesh().vertexCount(vertexCount).triangleCount(triCount);
        FloatPointer v = new FloatPointer(vertexCount * 3L);
        FloatPointer n = new FloatPointer(vertexCount * 3L);
        FloatPointer uv = new FloatPointer(vertexCount * 2L);
        BytePointer col = new BytePointer(vertexCount * 4L);
        for (long i = 0; i < vertexCount * 3L; i++) {
            v.put(i, vertices.get(i));
            n.put(i, normals.get(i));
        }
        for (long i = 0; i < vertexCount * 2L; i++) uv.put(i, texcoords.get(i));
        for (long i = 0; i < vertexCount * 4L; i++) col.put(i, colors.get(i));
        ShortPointer idx = new ShortPointer(triCount * 3L);
        long t = 0;
        for (short[] tri : tris) {
            idx.put(t++, tri[0]);
            idx.put(t++, tri[1]);
            idx.put(t++, tri[2]);
        }
        mesh.vertices(v);
        mesh.normals(n);
        mesh.texcoords(uv);
        mesh.colors(col);
        mesh.indices(idx);
        UploadMesh(mesh, false);
        return LoadModelFromMesh(mesh);
    }

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
        // Broad climate bands are intentionally fuzzy. Height is handled separately
        // so biome borders do not create artificial cliffs.
        if (volcNoise > 0.88f) return BIOME_VOLCANO;
        if (temp < 0.18f) return moist > 0.48f ? BIOME_TAIGA : BIOME_SNOW;
        if (temp < 0.32f) {
            if (moist > 0.68f) return BIOME_FROZEN_LAKE;
            if (moist > 0.45f) return BIOME_TAIGA;
            return BIOME_ALPINE;
        }
        if (temp < 0.50f) {
            if (moist > 0.82f) return BIOME_MANGROVE;
            if (moist > 0.67f) return BIOME_SWAMP;
            if (moist > 0.54f) return BIOME_BIRCH;
            if (moist > 0.38f) return BIOME_FOREST;
            if (moist > 0.20f) return BIOME_FLOWER_MEADOW;
            return BIOME_MEADOW;
        }
        if (temp < 0.70f) {
            if (moist < 0.16f) return BIOME_BADLANDS;
            if (moist < 0.29f) return BIOME_MESA;
            if (moist < 0.43f) return BIOME_SAVANNA;
            if (moist < 0.58f) return BIOME_DRY_FOREST;
            return BIOME_JUNGLE;
        }
        if (moist < 0.18f) return BIOME_DESERT;
        if (moist < 0.34f) return BIOME_MESA;
        if (moist < 0.52f) return BIOME_SAVANNA;
        return BIOME_JUNGLE;
    }

    public static int biomeAt(ForestTerrain forest, float x, float z) {
        float nx = x / forest.size, nz = z / forest.size;
        float temp = temperatureAt(x, z, forest.size, forest.seed);
        float moist = moistureAt(x, z, forest.size, forest.seed);
        float volc = fractalNoise(nx * 1.6f + 61f, nz * 1.6f - 22f, forest.seed + 404);
        int b = biomeFromClimate(temp, moist, volc);
        float h = heightAt(x, z, forest.size, forest.heightScale, forest.seed);

        // Elevation adds secondary alpine/highland regions; shorelines get their own biome.
        if (h <= forest.waterLevel + forest.heightScale * 0.10f) {
            if (temp < 0.28f) return BIOME_FROZEN_LAKE;
            if (b != BIOME_SWAMP && b != BIOME_MANGROVE) return BIOME_BEACH;
        }
        if (h > forest.heightScale * 1.05f && b != BIOME_VOLCANO) {
            return temp < 0.32f ? BIOME_SNOW : BIOME_HIGHLANDS;
        }
        return b;
    }

    private static int biomeIdToLegacy(toyz.builder.terrain.BiomeId b) {
        if (b == null) return BIOME_MEADOW;
        switch (b) {
            case SNOW: return BIOME_SNOW;
            case TAIGA: return BIOME_TAIGA;
            case FOREST: return BIOME_FOREST;
            case MEADOW: return BIOME_MEADOW;
            case FLOWER_MEADOW: return BIOME_FLOWER_MEADOW;
            case JUNGLE: return BIOME_JUNGLE;
            case SAVANNA: return BIOME_SAVANNA;
            case DESERT: return BIOME_DESERT;
            case SWAMP: return BIOME_SWAMP;
            case VOLCANIC: return BIOME_VOLCANO;
            case BIRCH: return BIOME_BIRCH;
            case ALPINE: return BIOME_ALPINE;
            case BADLANDS: return BIOME_BADLANDS;
            case MESA: return BIOME_MESA;
            case MANGROVE: return BIOME_MANGROVE;
            case BEACH: case OCEAN: return BIOME_BEACH;
            case HIGHLANDS: return BIOME_HIGHLANDS;
            case DRY_FOREST: return BIOME_DRY_FOREST;
            case FROZEN_LAKE: return BIOME_FROZEN_LAKE;
            default: return BIOME_MEADOW;
        }
    }

    public static String biomeNameAt(ForestTerrain forest, float x, float z) {
        if (forest != null && forest.v2 != null) {
            try {
                return toyz.builder.terrain.TerrainGenerator.getBiome(forest.v2, x, z).displayName();
            } catch (Throwable ignored) {}
        }
        int b = biomeAt(forest, x, z);
        if (b < 0 || b >= BIOME_NAMES.length) return "Unknown";
        return BIOME_NAMES[b];
    }

    // ---- height ----

    private static WorldType activeWorldType = WorldType.NORMAL;

    private static float heightAt(float x, float z, float size, float heightScale, int seed) {
        if (activeV2 != null)
            return toyz.builder.terrain.TerrainGenerator.getHeight(activeV2, x, z);
        float nx = x / size, nz = z / size;
        if (activeWorldType == WorldType.FLAT) {
            float detailFlat = fractalNoise(nx * 10f + 7f, nz * 10f - 3f, seed + 901);
            return 1.8f + detailFlat * 0.18f;
        }
        float broad = fractalNoise(nx * 2.4f + 20f, nz * 2.4f - 17f, seed);
        float detail = fractalNoise(nx * 12f - 7f, nz * 12f + 11f, seed ^ 0x9e3779b9);
        float ridges = fractalNoise(nx * 6f + 1f, nz * 6f - 3f, seed + 333);

        float temp = temperatureAt(x, z, size, seed);
        float moist = moistureAt(x, z, size, seed);
        float volc = fractalNoise(nx * 1.6f + 61f, nz * 1.6f - 22f, seed + 404);
        int biome = biomeFromClimate(temp, moist, volc);

        float localScale = heightScale * BIOME_HEIGHT_MOD[biome];
        boolean isMountain = (biome == BIOME_SNOW || biome == BIOME_VOLCANO
                || biome == BIOME_ALPINE || biome == BIOME_HIGHLANDS);

        float ridgeWeight = isMountain ? 0.58f : 0.12f;
        float h = (broad * (0.90f - ridgeWeight) + detail * 0.18f + ridges * ridgeWeight) * localScale;

        // V5 landform pass: independent macro features create mountains, plateaus,
        // eroded mesas and gentler lowlands without replacing the base noise.
        float landform = fractalNoise(nx * 3.1f + 73f, nz * 3.1f - 41f, seed + 1707);
        if (activeWorldType == WorldType.AMPLIFIED) {
            broad = 0.5f + (broad - 0.5f) * 1.8f;
            ridges = 0.5f + (ridges - 0.5f) * 1.7f;
            landform = 0.5f + (landform - 0.5f) * 1.5f;
        }
        if (biome == BIOME_ALPINE || biome == BIOME_HIGHLANDS || biome == BIOME_SNOW) {
            float ridge = 1f - Math.abs(landform * 2f - 1f);
            h += ridge * heightScale * 0.85f;
        } else if (biome == BIOME_MESA || biome == BIOME_BADLANDS) {
            float step = heightScale * 0.32f;
            float terraces = (float)Math.floor(h / Math.max(1f, step)) * step;
            h = h * 0.55f + terraces * 0.45f + landform * heightScale * 0.18f;
        } else if (biome == BIOME_BEACH || biome == BIOME_FROZEN_LAKE) {
            h = Math.min(h, heightScale * 0.04f + detail * heightScale * 0.08f);
        }

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
        if (forest != null && forest.v2 != null)
            return toyz.builder.terrain.TerrainGenerator.getHeight(forest.v2, x, z);
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
        return generateForestTerrain(seed, WorldType.NORMAL, true);
    }

    public static ForestTerrain generateForestTerrain(int seed, WorldType worldType, boolean structuresEnabled) {
        ForestTerrain forest = new ForestTerrain();
        forest.seed = seed;
        forest.worldType = worldType == null ? WorldType.NORMAL : worldType;
        forest.structuresEnabled = structuresEnabled;
        activeWorldType = forest.worldType;
        forest.size = 640f;
        forest.cellSize = 4.0f;
        forest.heightScale = 16f;
        forest.waterLevel = forest.heightScale * WATER_LEVEL_FRAC;

        // ---- Terrain V2 (continuous landforms / biomes / rivers / structure sites) ----
        try {
            toyz.builder.terrain.WorldConfig v2cfg =
                toyz.builder.terrain.WorldConfig.fromLegacy(seed, forest.worldType, structuresEnabled);
            v2cfg.size = forest.size;
            v2cfg.heightScale = forest.heightScale;
            v2cfg.sampleSpacing = forest.cellSize;
            v2cfg.meshSpacing = forest.cellSize;
            // V3 defaults: V6 mapgen, sky islands off until menu exposes toggle
            int pi = Math.max(0, Math.min(5, pendingMapgenPreset));
            v2cfg.preset = toyz.builder.terrain.WorldConfig.MapgenPreset.values()[pi];
            v2cfg.skyIslands = pendingSkyIslands;
            v2cfg.applyPresetTuning();
            activeV2 = toyz.builder.terrain.TerrainGenerator.generate(v2cfg);
            forest.v2 = activeV2;
            forest.waterLevel = activeV2.oceanLevel;
            if (activeV2.waterMeshes != null) {
                forest.waterOceanMesh = activeV2.waterMeshes.ocean;
                forest.waterRiverMesh = activeV2.waterMeshes.rivers;
                forest.waterFrozenMesh = activeV2.waterMeshes.frozen;
            }
            forest.skyIslands = activeV2.skyIslands != null ? activeV2.skyIslands
                    : new java.util.ArrayList<>();
        } catch (Throwable t) {
            System.err.println("[Terrain] V2 init failed, legacy height: " + t.getMessage());
            t.printStackTrace();
            forest.v2 = null;
            activeV2 = null;
        }

        int cells = (int) (forest.size / forest.cellSize);
        cells = Math.min(cells, 160);
        forest.cellSize = forest.size / cells;
        int vertsPerSide = cells + 1;
        int vertexCount = vertsPerSide * vertsPerSide;
        int triangleCount = cells * cells * 2;

        Mesh mesh = new Mesh().vertexCount(vertexCount).triangleCount(triangleCount);
        FloatPointer vertices = new FloatPointer(vertexCount * 3);
        FloatPointer normals = new FloatPointer(vertexCount * 3);
        FloatPointer texcoords = new FloatPointer(vertexCount * 2);
        BytePointer colors = new BytePointer(vertexCount * 4);
        int[] biomeIds = new int[vertexCount]; // for material segmentation
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
                // Segment terrain by biome — V3 BiomeId is authority when present
                int biome = biomeFromClimate(temp, moist, volc);
                if (forest.v2 != null) {
                    try {
                        biome = biomeIdToLegacy(toyz.builder.terrain.TerrainGenerator.getBiome(forest.v2, worldX, worldZ));
                    } catch (Throwable ignored) {}
                }
                if (y > forest.heightScale * 1.05f && biome != BIOME_VOLCANO)
                    biome = temp < 0.32f ? BIOME_SNOW : BIOME_HIGHLANDS;
                boolean nearWater = y <= forest.waterLevel + forest.heightScale * 0.18f;
                if (nearWater && temp > 0.28f && biome != BIOME_SWAMP && biome != BIOME_MANGROVE
                        && biome != BIOME_DESERT && biome != BIOME_BADLANDS)
                    biome = BIOME_BEACH;

                float riverAmt = riverInfluence(nxs, nzs, seed);
                if (forest.v2 != null && forest.v2.water != null) {
                    float rd = forest.v2.water.riverDistance(worldX, worldZ);
                    float rw = forest.v2.water.riverWidthAt(worldX, worldZ);
                    if (rd < rw * 2.5f) riverAmt = Math.max(riverAmt, 1f - rd / (rw * 2.5f));
                    if (rd < rw * 1.8f && y <= forest.waterLevel + forest.heightScale * 0.2f)
                        biome = BIOME_BEACH;
                }
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
                biomeIds[i] = biome;
            }
        }

        // ---- Multi-material terrain: sand / rock / dirt / snow / grass (real textures) ----
        final int MAT_GRASS = 0, MAT_SAND = 1, MAT_ROCK = 2, MAT_DIRT = 3, MAT_SNOW = 4;
        java.util.List<short[]>[] matTris = new java.util.ArrayList[5];
        for (int m = 0; m < 5; m++) matTris[m] = new java.util.ArrayList<>();

        long index = 0;
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                short a = (short) (z * vertsPerSide + x);
                short b = (short) (z * vertsPerSide + x + 1);
                short cIdx = (short) ((z + 1) * vertsPerSide + x);
                short d = (short) ((z + 1) * vertsPerSide + x + 1);
                // Full mesh still gets all tris (compat / fallback)
                indices.put(index++, a); indices.put(index++, cIdx); indices.put(index++, b);
                indices.put(index++, b); indices.put(index++, cIdx); indices.put(index++, d);
                // Classify triangle pair by majority biome → material
                int mat = terrainMaterialForBiomes(biomeIds[a & 0xFFFF], biomeIds[b & 0xFFFF],
                        biomeIds[cIdx & 0xFFFF], biomeIds[d & 0xFFFF]);
                matTris[mat].add(new short[]{a, cIdx, b});
                matTris[mat].add(new short[]{b, cIdx, d});
            }
        }

        mesh.vertices(vertices);
        mesh.normals(normals);
        mesh.texcoords(texcoords);
        mesh.colors(colors);
        mesh.indices(indices);
        UploadMesh(mesh, false);
        forest.terrainModel = LoadModelFromMesh(mesh);

        Texture grassTex = loadTerrainTex("assets/textures/grass/grass1.png", "assets/textures/grass1.png");
        Texture sandTex = loadTerrainTex("assets/textures/sand/sand1.png", "assets/textures/sand/sand2.png");
        Texture rockTexT = loadTerrainTex("assets/textures/rocks/rock1.png", "assets/textures/stone/stone1.png");
        Texture dirtTex = loadTerrainTex("assets/textures/dirt/dirt1.png", "assets/textures/rocks/rock3.png");
        Texture snowTex = loadTerrainTex("assets/textures/stone/stone2.png", "assets/textures/stone/concrete.jpg");
        Texture concreteTex = loadTerrainTex("assets/textures/stone/concrete.jpg", "assets/textures/stone/stone1.png");

        forest.terrainGrass = buildTerrainLayer(vertices, normals, texcoords, colors, vertexCount, matTris[MAT_GRASS]);
        forest.terrainSand  = buildTerrainLayer(vertices, normals, texcoords, colors, vertexCount, matTris[MAT_SAND]);
        forest.terrainRock  = buildTerrainLayer(vertices, normals, texcoords, colors, vertexCount, matTris[MAT_ROCK]);
        forest.terrainDirt  = buildTerrainLayer(vertices, normals, texcoords, colors, vertexCount, matTris[MAT_DIRT]);
        forest.terrainSnow  = buildTerrainLayer(vertices, normals, texcoords, colors, vertexCount, matTris[MAT_SNOW]);

        assignTerrainTexture(forest.terrainGrass, grassTex);
        assignTerrainTexture(forest.terrainSand, sandTex != null ? sandTex : grassTex);
        // Rock prefers rock; mesa/badlands also use concrete blend via rock layer
        assignTerrainTexture(forest.terrainRock, rockTexT != null ? rockTexT : (concreteTex != null ? concreteTex : grassTex));
        assignTerrainTexture(forest.terrainDirt, dirtTex != null ? dirtTex : grassTex);
        assignTerrainTexture(forest.terrainSnow, snowTex != null ? snowTex : grassTex);
        // Legacy single model: grass texture + vertex colors (fallback if layers empty)
        assignTerrainTexture(forest.terrainModel, grassTex);
        System.out.println("[Terrain] materials grass=" + matTris[MAT_GRASS].size()
                + " sand=" + matTris[MAT_SAND].size()
                + " rock=" + matTris[MAT_ROCK].size()
                + " dirt=" + matTris[MAT_DIRT].size()
                + " snow=" + matTris[MAT_SNOW].size());

        Texture barkTex = LoadTexture("assets/textures/trees/bark1.png"); if (barkTex == null || barkTex.id()==0) barkTex = LoadTexture("assets/textures/bark1.png");
        boolean haveBark = barkTex != null && barkTex.id() != 0;
        if (haveBark) {
            SetTextureFilter(barkTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(barkTex, TEXTURE_WRAP_REPEAT);
        }

        // ---- TREE PART MODELS (prefer fixed GLB logs over GenMesh cylinders) ----
        forest.logVertOak = tryLoadGlb("assets/models/oaklogv.glb");
        forest.logVertPine = tryLoadGlb("assets/models/pinelogv.glb");
        forest.logVertBirch = tryLoadGlb("assets/models/birchlogv.glb");
        forest.logVertJungle = tryLoadGlb("assets/models/mahoganylogv.glb");
        forest.logHorizOak = tryLoadGlb("assets/models/oaklogh.glb");
        forest.logHorizPine = tryLoadGlb("assets/models/pinelogh.glb");
        forest.logHorizBirch = tryLoadGlb("assets/models/birchlogh.glb");
        forest.boulderSmall = tryLoadGlb("assets/models/bouldersmall.glb");
        forest.boulderMed = tryLoadGlb("assets/models/bouldermedium.glb");
        forest.boulderLarge = tryLoadGlb("assets/models/boulderlarge.glb");

        forest.trunkModel = forest.logVertOak != null ? forest.logVertOak
                : LoadModelFromMesh(GenMeshCylinder(0.32f, 1.0f, 8));
        forest.trunkFatModel = forest.logVertJungle != null ? forest.logVertJungle
                : (forest.logVertOak != null ? forest.logVertOak
                : LoadModelFromMesh(GenMeshCylinder(0.42f, 1.0f, 8)));
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
        forest.featureBlockModel = LoadModelFromMesh(GenMeshCube(1.0f, 1.0f, 1.0f));
        Texture rockTex = LoadTexture("assets/textures/rocks/rock1.png"); if (rockTex == null || rockTex.id()==0) rockTex = LoadTexture("assets/textures/rock1.png");
        if (rockTex == null || rockTex.id() == 0)
            rockTex = LoadTexture("assets/textures/stone/stone1.png"); if (rockTex == null || rockTex.id()==0) rockTex = LoadTexture("assets/textures/stone1.png");
        if (rockTex != null && rockTex.id() != 0) {
            SetTextureFilter(rockTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(rockTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.rockBlockModel.materials().position(0), MATERIAL_MAP_DIFFUSE, rockTex);
            forest.rockBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.rockBlockModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(110, 105, 100, 255));
        }
        Texture magmaTex = LoadTexture("assets/textures/magma/magma1.png"); if (magmaTex == null || magmaTex.id()==0) magmaTex = LoadTexture("assets/textures/magma1.png");
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
                int biome = biomeAt(forest, px, pz);
                float groundY = heightAt(px, pz, forest.size, forest.heightScale, seed);

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
                int biome = biomeAt(forest, px, pz);
                float groundY = heightAt(px, pz, forest.size, forest.heightScale, seed);
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

        // ---- V5 land features: natural geometry only, no buildings ----
        final float featureSpacing = 19f;
        for (float zPos = -caveArea; zPos <= caveArea; zPos += featureSpacing) {
            for (float xPos = -caveArea; xPos <= caveArea; xPos += featureSpacing) {
                float jx = (hash2D((int)(xPos * 7), (int)(zPos * 7), seed + 3001) - 0.5f) * 12f;
                float jz = (hash2D((int)(zPos * 7), (int)(xPos * 7), seed + 3101) - 0.5f) * 12f;
                float px = xPos + jx, pz = zPos + jz;
                if (px * px + pz * pz < 24f * 24f) continue;
                float gy = heightAt(px, pz, forest.size, forest.heightScale, seed);
                if (gy < forest.waterLevel + 0.25f) continue;

                int biome = biomeAt(forest, px, pz);
                float roll = hash2D((int)(xPos * 5), (int)(zPos * 5), seed + 3201);
                int kind = -1;

                if ((biome == BIOME_BADLANDS || biome == BIOME_MESA) && roll < 0.42f) kind = 1;
                else if ((biome == BIOME_DESERT || biome == BIOME_BEACH)) {
                    if (roll < 0.30f) kind = 2;
                    else if (roll < 0.43f) kind = 0;
                } else if (biome == BIOME_SNOW || biome == BIOME_TAIGA || biome == BIOME_ALPINE
                        || biome == BIOME_FROZEN_LAKE) {
                    if (roll < 0.34f) kind = 3;
                    else if (roll < 0.46f) kind = 0;
                } else if (biome == BIOME_VOLCANO && roll < 0.30f) kind = 4;
                else if ((biome == BIOME_HIGHLANDS || biome == BIOME_SAVANNA) && roll < 0.22f) kind = 0;

                if (kind >= 0) {
                    LandFeature feature = new LandFeature();
                    feature.position = Helpers.newVector3(px, gy, pz);
                    feature.rotation = hash2D((int)(xPos * 11), (int)(zPos * 11), seed + 3301) * 360f;
                    feature.variant = (int)(hash2D((int)(xPos * 13), (int)(zPos * 13), seed + 3401) * 4f) & 3;
                    feature.kind = kind;
                    float sr = hash2D((int)(xPos * 17), (int)(zPos * 17), seed + 3501);
                    if (kind == 1) {
                        feature.scaleX = 2.0f + sr * 4.0f;
                        feature.scaleZ = 2.0f + sr * 3.0f;
                        feature.scaleY = 1.2f + sr * 4.5f;
                    } else if (kind == 2) {
                        feature.scaleX = 2.0f + sr * 3.5f;
                        feature.scaleZ = 1.5f + sr * 3.0f;
                        feature.scaleY = 0.08f;
                    } else if (kind == 3) {
                        feature.scaleX = 1.5f + sr * 3.0f;
                        feature.scaleZ = 1.2f + sr * 2.5f;
                        feature.scaleY = 0.25f + sr * 0.65f;
                    } else {
                        feature.scaleX = 0.8f + sr * 1.8f;
                        feature.scaleZ = 0.8f + sr * 1.8f;
                        feature.scaleY = 1.0f + sr * 3.5f;
                    }
                    forest.landFeatures.add(feature);
                }
            }
        }

                // ---- Water surfaces (V2 rivers/ocean + flood fill where terrain is submerged) ----
        forest.waterBodies.clear();
        final float surfaceY = forest.waterLevel + 0.06f;
        // Dense samples where height is below water surface → visible lakes/ocean pockets
        final float waterSpacing = 10f;
        for (float zPos = -caveArea; zPos <= caveArea; zPos += waterSpacing) {
            for (float xPos = -caveArea; xPos <= caveArea; xPos += waterSpacing) {
                float gy = heightAt(xPos, zPos, forest.size, forest.heightScale, seed);
                if (gy > forest.waterLevel - 0.05f) continue;
                WaterBody water = new WaterBody();
                water.position = Helpers.newVector3(xPos, surfaceY, zPos);
                water.radiusX = waterSpacing * 0.65f;
                water.radiusZ = waterSpacing * 0.65f;
                water.frozen = temperatureAt(xPos, zPos, forest.size, seed) < 0.28f;
                water.river = false;
                forest.waterBodies.add(water);
            }
        }
        // V2 river polylines → continuous water ribbon
        if (forest.v2 != null && forest.v2.water != null) {
            for (toyz.builder.terrain.WaterGenerator.RiverPath river : forest.v2.water.rivers) {
                for (int i = 0; i < river.xs.length; i++) {
                    float px = river.xs[i];
                    float pz = river.zs[i];
                    WaterBody water = new WaterBody();
                    water.position = Helpers.newVector3(px, surfaceY, pz);
                    water.radiusX = Math.max(2.5f, river.width * 0.55f);
                    water.radiusZ = Math.max(3.5f, river.width * 0.85f);
                    water.frozen = temperatureAt(px, pz, forest.size, seed) < 0.25f;
                    water.river = true;
                    forest.waterBodies.add(water);
                }
                // scatter a few horizontal log decorations on banks
                if (forest.logHorizOak != null || forest.logHorizPine != null) {
                    for (int i = 3; i < river.xs.length - 3; i += 7) {
                        float px = river.xs[i] + river.width * 1.2f;
                        float pz = river.zs[i];
                        float gy = heightAt(px, pz, forest.size, forest.heightScale, seed);
                        if (gy < forest.waterLevel + 0.2f) continue;
                        // store as tiny land feature via trees list? use LandFeature if available
                        // draw later in drawForest via water bank pass
                    }
                }
            }
        } else {
            // Legacy river ribbon fallback
            for (float zPos = -caveArea; zPos <= caveArea; zPos += 12f) {
                float nzr = zPos / forest.size;
                float ri = riverInfluence(0f, nzr, seed);
                if (ri < 0.15f) continue;
                float meander = (fractalNoise(nzr * 3f + 50f, 5f, seed + 777) - 0.5f) * forest.size * 0.25f;
                float px = meander;
                WaterBody water = new WaterBody();
                water.position = Helpers.newVector3(px, surfaceY, zPos);
                water.radiusX = 2.5f + ri * 4.0f;
                water.radiusZ = 10f;
                water.frozen = temperatureAt(px, zPos, forest.size, seed) < 0.25f;
                water.river = true;
                forest.waterBodies.add(water);
            }
        }
        System.out.println("[Terrain] waterBodies=" + forest.waterBodies.size()
                + " waterLevel=" + forest.waterLevel);

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

        activeV2 = null; // height queries use forest.v2 via getTerrainHeight
        if (forest.v2 != null) {
            System.out.println("[Terrain] V2 active biomes/sites bridged; structureSites="
                + forest.v2.structureSites.size() + " bridges=" + forest.v2.bridgeSites.size());
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
        drawTrunk(f, pos, scale, blocks, fat, 0);
    }

    private static void drawTrunk(ForestTerrain f, Vector3 pos, float scale, int blocks, boolean fat, int biomeType) {
        Model m = trunkForBiome(f, fat ? 6 : biomeType);
        // GLB logs are full-height pieces — stack fewer, scale to segment height
        boolean glb = (m == f.logVertOak || m == f.logVertPine || m == f.logVertBirch || m == f.logVertJungle);
        if (glb) {
            float seg = scale * 1.05f;
            int n = Math.max(1, blocks);
            for (int i = 0; i < n; i++) {
                DrawModelEx(m,
                    Helpers.newVector3(pos.x(), pos.y() + (i + 0.5f) * seg, pos.z()),
                    Helpers.newVector3(0, 1, 0), 0f,
                    Helpers.newVector3(seg, seg, seg), WHITE);
            }
        } else {
            for (int i = 0; i < blocks; i++) {
                DrawModel(m, Helpers.newVector3(pos.x(), pos.y() + (i + 0.5f) * scale, pos.z()),
                         scale, WHITE);
            }
        }
    }

    /** Fallen / horizontal log decoration using *logh.glb. */
    private static void drawHorizLog(ForestTerrain f, float x, float y, float z, float scale, float yaw) {
        Model m = f.logHorizOak != null ? f.logHorizOak
                : (f.logHorizPine != null ? f.logHorizPine : f.logHorizBirch);
        if (m == null) return;
        DrawModelEx(m, Helpers.newVector3(x, y + scale * 0.35f, z),
                Helpers.newVector3(0, 1, 0), yaw,
                Helpers.newVector3(scale, scale, scale), WHITE);
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
        drawTrunk(f, t.position, s, trunkBlocks, false, t.biomeType);
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
        // Draw biome material layers (sand/rock/dirt/snow/grass textures)
        boolean anyLayer = false;
        if (forest.terrainSand != null) { DrawModel(forest.terrainSand, Helpers.newVector3(0, 0, 0), 1f, WHITE); anyLayer = true; }
        if (forest.terrainRock != null) { DrawModel(forest.terrainRock, Helpers.newVector3(0, 0, 0), 1f, WHITE); anyLayer = true; }
        if (forest.terrainDirt != null) { DrawModel(forest.terrainDirt, Helpers.newVector3(0, 0, 0), 1f, WHITE); anyLayer = true; }
        if (forest.terrainSnow != null) { DrawModel(forest.terrainSnow, Helpers.newVector3(0, 0, 0), 1f, WHITE); anyLayer = true; }
        if (forest.terrainGrass != null) { DrawModel(forest.terrainGrass, Helpers.newVector3(0, 0, 0), 1f, WHITE); anyLayer = true; }
        if (!anyLayer && forest.terrainModel != null)
            DrawModel(forest.terrainModel, Helpers.newVector3(0, 0, 0), 1f, WHITE);


        float maxDistSq = maxDist * maxDist;
        float lodFarSq = (maxDist * 0.55f) * (maxDist * 0.55f);
        float propDistSq = (maxDist * 0.85f) * (maxDist * 0.85f);

        // V3: continuous water meshes (no tile cubes)
        Color oceanCol = Helpers.newColor(45, 110, 170, 200);
        Color riverCol = Helpers.newColor(40, 130, 190, 210);
        Color frozenCol = Helpers.newColor(200, 225, 235, 230);
        if (forest.waterOceanMesh != null && forest.waterOceanMesh.meshCount() > 0) {
            DrawModel(forest.waterOceanMesh, Helpers.newVector3(0, 0, 0), 1f, oceanCol);
        }
        if (forest.waterRiverMesh != null && forest.waterRiverMesh.meshCount() > 0) {
            DrawModel(forest.waterRiverMesh, Helpers.newVector3(0, 0, 0), 1f, riverCol);
        }
        if (forest.waterFrozenMesh != null && forest.waterFrozenMesh.meshCount() > 0) {
            DrawModel(forest.waterFrozenMesh, Helpers.newVector3(0, 0, 0), 1f, frozenCol);
        }
        // Fallback only if V3 meshes missing
        if (forest.waterOceanMesh == null && forest.waterRiverMesh == null
                && forest.waterBodies != null) {
            for (WaterBody water : forest.waterBodies) {
                float dx = water.position.x() - camPos.x();
                float dz = water.position.z() - camPos.z();
                float reach = Math.max(water.radiusX, water.radiusZ);
                if (dx * dx + dz * dz > (maxDist + reach) * (maxDist + reach)) continue;
                water.position.y(forest.waterLevel + 0.05f);
                Color wc = water.frozen ? frozenCol : (water.river ? riverCol : oceanCol);
                DrawCube(water.position, water.radiusX * 2f, 0.08f, water.radiusZ * 2f, Fade(wc, 0.75f));
            }
        }

        // Sky islands (continuous meshes)
        if (forest.skyIslands != null) {
            Color islCol = Helpers.newColor(90, 140, 70, 255);
            for (toyz.builder.terrain.SkyIslandGenerator.Island isl : forest.skyIslands) {
                if (isl == null || isl.mesh == null || isl.mesh.meshCount() < 1) continue;
                float dx = isl.cx - camPos.x(), dz = isl.cz - camPos.z();
                if (dx * dx + dz * dz > (maxDist + isl.radius) * (maxDist + isl.radius)) continue;
                DrawModel(isl.mesh, Helpers.newVector3(0, 0, 0), 1f, islCol);
            }
        }

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

        // V5 natural landform decorations.
        if (forest.landFeatures != null && forest.featureBlockModel != null) {
            for (LandFeature feature : forest.landFeatures) {
                float dx = feature.position.x() - camPos.x();
                float dz = feature.position.z() - camPos.z();
                if (dx * dx + dz * dz > propDistSq) continue;
                Color fc;
                switch (feature.kind) {
                    case 1: fc = Helpers.newColor(155 + feature.variant * 12, 92 + feature.variant * 8, 48, 255); break;
                    case 2: fc = Helpers.newColor(218, 195, 132, 255); break;
                    case 3: fc = Helpers.newColor(235, 242, 245, 255); break;
                    case 4: fc = Helpers.newColor(72, 68, 65, 255); break;
                    default: fc = K_ROCK[feature.variant % K_ROCK.length];
                }
                DrawModelEx(forest.featureBlockModel, feature.position,
                    Helpers.newVector3(0, 1, 0), feature.rotation,
                    Helpers.newVector3(feature.scaleX, feature.scaleY, feature.scaleZ), fc);
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
            // occasional fallen GLB log beside tree
            if ((tree.colorVariant % 5) == 0 && (forest.logHorizOak != null || forest.logHorizPine != null)) {
                float ang = tree.rotation + 35f;
                float lx = tree.position.x() + (float)Math.cos(ang * 0.017453f) * tree.scale * 1.4f;
                float lz = tree.position.z() + (float)Math.sin(ang * 0.017453f) * tree.scale * 1.4f;
                drawHorizLog(forest, lx, tree.position.y(), lz, tree.scale * 0.85f, ang);
            }

        }

        drawVegetation(forest, camPos, time);
    }

    public static void unloadForestTerrain(ForestTerrain forest) {
        if (forest == null) return;
        // Guard each model — JavaCPP/raylib double-free → STATUS_HEAP_CORRUPTION on Windows
        unloadModelSafe(forest.terrainModel); forest.terrainModel = null;
        unloadModelSafe(forest.terrainGrass); forest.terrainGrass = null;
        unloadModelSafe(forest.terrainSand); forest.terrainSand = null;
        unloadModelSafe(forest.terrainRock); forest.terrainRock = null;
        unloadModelSafe(forest.terrainDirt); forest.terrainDirt = null;
        unloadModelSafe(forest.terrainSnow); forest.terrainSnow = null;
        unloadModelSafe(forest.trunkModel); forest.trunkModel = null;
        unloadModelSafe(forest.trunkFatModel); forest.trunkFatModel = null;
        unloadModelSafe(forest.leafBlockModel); forest.leafBlockModel = null;
        unloadModelSafe(forest.leafBlockSmallModel); forest.leafBlockSmallModel = null;
        unloadModelSafe(forest.spruceBlockModel); forest.spruceBlockModel = null;
        unloadModelSafe(forest.cactusBlockModel); forest.cactusBlockModel = null;
        unloadModelSafe(forest.acaciaTrunkModel); forest.acaciaTrunkModel = null;
        unloadModelSafe(forest.rockBlockModel); forest.rockBlockModel = null;
        unloadModelSafe(forest.magmaBlockModel); forest.magmaBlockModel = null;
        unloadModelSafe(forest.featureBlockModel); forest.featureBlockModel = null;
        if (forest.trees != null) forest.trees.clear();
        if (forest.vegetation != null) forest.vegetation.clear();
        if (forest.magmaPools != null) forest.magmaPools.clear();
        if (forest.caves != null) forest.caves.clear();
        if (forest.rocks != null) forest.rocks.clear();
        if (forest.landFeatures != null) forest.landFeatures.clear();
        if (forest.waterBodies != null) forest.waterBodies.clear();
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