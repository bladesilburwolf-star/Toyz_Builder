package toyz.builder;

import com.raylib.Helpers;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.ShortPointer;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/** Port of terrain.h / terrain.cpp — procedural forest with biomes and a river. */
public final class Terrain {

    public static class ForestTree {
        public Vector3 position;
        public float scale;
        public float rotation;
        public int biomeType;    // 0 broadleaf, 1 pine, 2 autumn, 3 bush
        public int colorVariant; // 0..3
    }

    public static class Biome {
        public float startX, startZ, width, depth;
        public Color grassColor, dirtColor;
        public float heightScale;
        public int treeDensity; // 0-100
        public Biome(float sx, float sz, float w, float d, Color g, Color dirt, float hs, int td) {
            startX = sx; startZ = sz; width = w; depth = d;
            grassColor = g; dirtColor = dirt; heightScale = hs; treeDensity = td;
        }
    }

    public static class ForestTerrain {
        public Model terrainModel, trunkModel, canopyModel, pineModel, deciduousModel;
        public List<ForestTree> trees = new ArrayList<>();
        public List<Biome> biomes = new ArrayList<>();
        public float size = 320f;
        public float cellSize = 2.5f;
        public float heightScale = 14f;
        public float waterLevel = 0f;
        public int seed = 0xC0FFEE;
    }

    private static final float WATER_LEVEL_FRAC = -0.30f;

    private Terrain() {}

    // ---- noise (exact port of the C++ hash / value / fractal noise) ----

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
        float width = 0.018f + 0.014f * fractalNoise(nz * 4f + 90f, 8f, seed + 888);
        float dist = Math.abs(nx - meander);
        float t = clamp((dist - width) / (width * 2.5f + 0.001f), 0f, 1f);
        return 1f - t;
    }

    private static float heightAt(float x, float z, float size, float heightScale, int seed) {
        float nx = x / size, nz = z / size;
        float broad = fractalNoise(nx * 2.4f + 20f, nz * 2.4f - 17f, seed);
        float detail = fractalNoise(nx * 12f - 7f, nz * 12f + 11f, seed ^ 0x9e3779b9);
        float ridges = fractalNoise(nx * 6f + 1f, nz * 6f - 3f, seed + 333);

        float biomeN = fractalNoise(nx * 0.35f, nz * 0.35f, seed + 12345);
        float localScale = heightScale;
        boolean isMountain = biomeN < 0.20f;
        if (isMountain) localScale *= 2.3f;
        else if (biomeN < 0.40f) localScale *= 1.15f;
        else if (biomeN < 0.60f) localScale *= 0.95f;
        else if (biomeN < 0.80f) localScale *= 0.70f;
        else localScale *= 1.6f;

        float ridgeWeight = isMountain ? 0.38f : 0.10f;
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
        return h;
    }

    private static Color getBiomeColor(float x, float z, float size, int seed) {
        float nx = x / size, nz = z / size;
        float biomeNoise = fractalNoise(nx * 0.35f, nz * 0.35f, seed + 12345);
        float moisture = fractalNoise(nx * 0.5f + 9f, nz * 0.5f - 4f, seed + 555);

        if (biomeNoise < 0.18f) return Helpers.newColor(110, 115, 120, 255);
        if (biomeNoise < 0.35f) return (moisture > 0.5f)
            ? Helpers.newColor(48, 105, 48, 255)
            : Helpers.newColor(70, 120, 55, 255);
        if (biomeNoise < 0.52f) return Helpers.newColor(95, 145, 65, 255);
        if (biomeNoise < 0.68f) return Helpers.newColor(175, 155, 95, 255);
        if (biomeNoise < 0.82f) return Helpers.newColor(195, 175, 120, 255);
        return Helpers.newColor(200, 205, 210, 255);
    }

    public static float getTerrainHeight(ForestTerrain forest, float x, float z) {
        return heightAt(x, z, forest.size, forest.heightScale, forest.seed);
    }

    public static float getBiomeHeightScale(ForestTerrain forest, float x, float z) {
        for (Biome b : forest.biomes) {
            if (x >= b.startX && x <= b.startX + b.width && z >= b.startZ && z <= b.startZ + b.depth) {
                return b.heightScale;
            }
        }
        return 1f;
    }

    public static Color getBiomeGrassColor(ForestTerrain forest, float x, float z) {
        for (Biome b : forest.biomes) {
            if (x >= b.startX && x <= b.startX + b.width && z >= b.startZ && z <= b.startZ + b.depth) {
                return b.grassColor;
            }
        }
        return Helpers.newColor(72, 108, 55, 255);
    }

    // ---- generation ----

    public static ForestTerrain generateForestTerrain(int seed) {
        ForestTerrain forest = new ForestTerrain();
        forest.seed = seed;
        forest.size = 320f;
        forest.cellSize = 2.5f;
        forest.heightScale = 14f;
        forest.waterLevel = forest.heightScale * WATER_LEVEL_FRAC;

        float h = forest.size * 0.5f;
        forest.biomes.add(new Biome(-h, -h, h, h, Helpers.newColor(55, 110, 50, 255),
                                    Helpers.newColor(100, 75, 50, 255), 1.2f, 65));   // SW forest
        forest.biomes.add(new Biome(0, -h, h, h, Helpers.newColor(100, 150, 80, 255),
                                    Helpers.newColor(100, 75, 50, 255), 0.9f, 40));  // SE meadow
        forest.biomes.add(new Biome(-h, 0, h, h, Helpers.newColor(185, 165, 105, 255),
                                    Helpers.newColor(150, 120, 80, 255), 0.65f, 15)); // NW dry
        forest.biomes.add(new Biome(0, 0, h, h, Helpers.newColor(120, 110, 100, 255),
                                    Helpers.newColor(100, 80, 60, 255), 1.5f, 50));   // NE hills

        int cells = (int) (forest.size / forest.cellSize);
        int vertsPerSide = cells + 1;
        int vertexCount = vertsPerSide * vertsPerSide;
        int triangleCount = cells * cells * 2;

        // Custom mesh: JavaCPP pointers (bundled with jaylib, replaces C MemAlloc).
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

                // Approximate normal from neighbouring heights
                float eps = forest.cellSize;
                float hL = heightAt(worldX - eps, worldZ, forest.size, forest.heightScale, seed);
                float hR = heightAt(worldX + eps, worldZ, forest.size, forest.heightScale, seed);
                float hD = heightAt(worldX, worldZ - eps, forest.size, forest.heightScale, seed);
                float hU = heightAt(worldX, worldZ + eps, forest.size, forest.heightScale, seed);
                float nx = hL - hR, ny = 2f * eps, nz = hD - hU;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
                normals.put(i * 3, nx).put(i * 3 + 1, ny).put(i * 3 + 2, nz);

                // Biome color + river bed + height shading
                Color biomeColor = getBiomeColor(worldX, worldZ, forest.size, seed);
                float riverAmt = riverInfluence(worldX / forest.size, worldZ / forest.size, seed);
                int br = biomeColor.r() & 0xFF, bg = biomeColor.g() & 0xFF, bb = biomeColor.b() & 0xFF;
                if (riverAmt > 0f) {
                    br = lerpi(br, 90, riverAmt);
                    bg = lerpi(bg, 80, riverAmt);
                    bb = lerpi(bb, 65, riverAmt);
                }
                float heightFactor = clamp((y + forest.heightScale * 0.4f) / (forest.heightScale * 1.2f), 0f, 1f);
                float shade = 0.75f + heightFactor * 0.35f;
                colors.put(i * 4, (byte) clamp(br * shade, 0f, 255f));
                colors.put(i * 4 + 1, (byte) clamp(bg * shade, 0f, 255f));
                colors.put(i * 4 + 2, (byte) clamp(bb * shade, 0f, 255f));
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

        // Grass texture (optional — vertex colors still look fine without it)
        Texture grassTex = LoadTexture("assets/textures/grass1.png");
        if (grassTex != null && grassTex.id() != 0) {
            SetTextureFilter(grassTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(grassTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.terrainModel.materials().position(0), MATERIAL_MAP_DIFFUSE, grassTex);
        }
        forest.terrainModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);

        // Trunks / canopies
        forest.trunkModel = LoadModelFromMesh(GenMeshCylinder(0.20f, 2.2f, 8));
        Texture barkTex = LoadTexture("assets/textures/bark1.png");
        boolean haveBark = barkTex != null && barkTex.id() != 0;
        if (haveBark) {
            SetTextureFilter(barkTex, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(barkTex, TEXTURE_WRAP_REPEAT);
            SetMaterialTexture(forest.trunkModel.materials().position(0), MATERIAL_MAP_DIFFUSE, barkTex);
            forest.trunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.trunkModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(92, 60, 35, 255));
        }

        forest.canopyModel = LoadModelFromMesh(GenMeshSphere(1.15f, 8, 8));
        forest.canopyModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(55, 120, 50, 255));

        forest.pineModel = LoadModelFromMesh(GenMeshCylinder(0.14f, 2.8f, 8));
        if (haveBark) {
            SetMaterialTexture(forest.pineModel.materials().position(0), MATERIAL_MAP_DIFFUSE, barkTex);
            forest.pineModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(WHITE);
        } else {
            forest.pineModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
                .color(Helpers.newColor(80, 50, 30, 255));
        }

        forest.deciduousModel = LoadModelFromMesh(GenMeshSphere(1.35f, 8, 8));
        forest.deciduousModel.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(70, 130, 55, 255));

        // Trees
        final float spacing = 7.0f;
        for (float zPos = -forest.size * 0.46f; zPos <= forest.size * 0.46f; zPos += spacing) {
            for (float xPos = -forest.size * 0.46f; xPos <= forest.size * 0.46f; xPos += spacing) {
                float jitterX = (hash2D((int) (xPos * 10), (int) (zPos * 10), seed) - 0.5f) * 3f;
                float jitterZ = (hash2D((int) (zPos * 10), (int) (xPos * 10), seed + 77) - 0.5f) * 3f;
                float px = xPos + jitterX;
                float pz = zPos + jitterZ;
                if (px * px + pz * pz < 18f * 18f) continue; // spawn bowl
                if (riverInfluence(px / forest.size, pz / forest.size, seed) > 0.4f) continue;

                boolean inBiome = false;
                for (Biome b : forest.biomes) {
                    if (px >= b.startX && px <= b.startX + b.width && pz >= b.startZ && pz <= b.startZ + b.depth) {
                        float density = hash2D((int) (xPos * 3), (int) (zPos * 3), seed + 991);
                        if (density < b.treeDensity / 100f) continue;
                        inBiome = true;
                        break;
                    }
                }
                if (!inBiome) continue;

                ForestTree tree = new ForestTree();
                tree.position = Helpers.newVector3(px,
                    heightAt(px, pz, forest.size, forest.heightScale, seed), pz);

                float treeType = hash2D((int) (xPos * 5), (int) (zPos * 5), seed + 123);
                float sizeRoll = hash2D((int) (xPos * 7), (int) (zPos * 7), seed + 17);
                tree.colorVariant = (int) (hash2D((int) (xPos * 11), (int) (zPos * 11), seed + 55) * 4f) % 4;
                tree.rotation = hash2D((int) (xPos * 13), (int) (zPos * 13), seed + 29) * 360f;

                if (treeType < 0.12f) { tree.biomeType = 3; tree.scale = 0.35f + sizeRoll * 0.25f; }
                else if (treeType < 0.32f) { tree.biomeType = 1; tree.scale = 0.85f + sizeRoll * 0.7f; }
                else if (treeType < 0.48f) { tree.biomeType = 2; tree.scale = 0.7f + sizeRoll * 0.6f; }
                else { tree.biomeType = 0; tree.scale = 0.65f + sizeRoll * 0.75f; }

                forest.trees.add(tree);
            }
        }

        // Landmark pines on the ridges
        for (int i = 0; i < 8; i++) {
            float angle = hash2D(i, 0, seed + 456) * 360f;
            float distance = 35f + hash2D(i, 1, seed + 789) * 50f;
            float px = (float) (Math.cos(Math.toRadians(angle)) * distance);
            float pz = (float) (Math.sin(Math.toRadians(angle)) * distance);
            ForestTree landmark = new ForestTree();
            landmark.position = Helpers.newVector3(px,
                heightAt(px, pz, forest.size, forest.heightScale, seed), pz);
            landmark.scale = 1.4f + hash2D(i, 2, seed + 111) * 0.7f;
            landmark.rotation = hash2D(i, 3, seed + 222) * 360f;
            landmark.biomeType = 1;
            landmark.colorVariant = i % 4;
            forest.trees.add(landmark);
        }

        return forest;
    }

    // ---- drawing ----

    private static final Color[] K_BROAD = {
        Helpers.newColor(48, 110, 42, 255), Helpers.newColor(62, 130, 50, 255),
        Helpers.newColor(40, 95, 38, 255), Helpers.newColor(70, 140, 55, 255)
    };
    private static final Color[] K_AUTUMN = {
        Helpers.newColor(160, 90, 40, 255), Helpers.newColor(180, 60, 70, 255),
        Helpers.newColor(200, 140, 50, 255), Helpers.newColor(120, 70, 100, 255)
    };
    private static final Color[] K_PINE = {
        Helpers.newColor(28, 70, 35, 255), Helpers.newColor(35, 85, 42, 255),
        Helpers.newColor(22, 60, 30, 255), Helpers.newColor(40, 95, 48, 255)
    };

    public static void drawForestTerrain(ForestTerrain forest, Vector3 camPos, float maxDist) {
        DrawModel(forest.terrainModel, Helpers.newVector3(0, 0, 0), 1f, WHITE);

        // Water plane at the carved river level
        DrawCube(Helpers.newVector3(0, forest.waterLevel - 0.08f, 0), forest.size, 0.16f, forest.size,
                 Fade(Helpers.newColor(60, 120, 170, 255), 0.65f));

        float maxDistSq = maxDist * maxDist;
        float lodFarSq = (maxDist * 0.55f) * (maxDist * 0.55f);

        Color trunkTint = Helpers.newColor(75, 48, 28, 255);

        for (ForestTree tree : forest.trees) {
            float dx = tree.position.x() - camPos.x();
            float dz = tree.position.z() - camPos.z();
            float distSq = dx * dx + dz * dz;
            if (distSq > maxDistSq) continue;

            float s = tree.scale;
            float y0 = tree.position.y();
            int cv = tree.colorVariant & 3;
            boolean lod = distSq > lodFarSq;
            float tx = tree.position.x(), tz = tree.position.z();

            if (tree.biomeType == 3) { // bush
                Color leaf = K_BROAD[cv];
                DrawModelEx(forest.canopyModel, Helpers.newVector3(tx, y0 + 0.55f * s, tz),
                           Helpers.newVector3(0, 1, 0), tree.rotation,
                           Helpers.newVector3(s * 1.1f, s * 0.7f, s * 1.1f), leaf);
                if (!lod) {
                    DrawModelEx(forest.canopyModel, Helpers.newVector3(tx + 0.35f * s, y0 + 0.45f * s, tz - 0.2f * s),
                               Helpers.newVector3(0, 1, 0), tree.rotation + 40f,
                               Helpers.newVector3(s * 0.85f, s * 0.55f, s * 0.85f), Fade(leaf, 0.9f));
                }
                continue;
            }

            if (tree.biomeType == 1) { // pine
                Color pine = K_PINE[cv];
                DrawModelEx(forest.pineModel, Helpers.newVector3(tx, y0 + 1.2f * s, tz),
                           Helpers.newVector3(0, 1, 0), tree.rotation,
                           Helpers.newVector3(s * 0.7f, s * 1.35f, s * 0.7f), trunkTint);
                if (lod) {
                    DrawModelEx(forest.canopyModel, Helpers.newVector3(tx, y0 + 2.5f * s, tz),
                               Helpers.newVector3(0, 1, 0), tree.rotation,
                               Helpers.newVector3(s * 1.1f, s * 0.9f, s * 1.1f), pine);
                } else {
                    float[] layers = {1.6f, 2.5f, 3.3f};
                    float[] widths = {1.3f, 1.0f, 0.6f};
                    for (int i = 0; i < 3; i++) {
                        DrawModelEx(forest.canopyModel, Helpers.newVector3(tx, y0 + layers[i] * s, tz),
                                   Helpers.newVector3(0, 1, 0), tree.rotation + i * 15f,
                                   Helpers.newVector3(s * widths[i], s * 0.55f, s * widths[i]), pine);
                    }
                }
                continue;
            }

            // broadleaf / autumn
            Color leaf = (tree.biomeType == 2) ? K_AUTUMN[cv] : K_BROAD[cv];
            DrawModelEx(forest.trunkModel, Helpers.newVector3(tx, y0 + 1.0f * s, tz),
                       Helpers.newVector3(0, 1, 0), tree.rotation,
                       Helpers.newVector3(s * 0.85f, s * 1.15f, s * 0.85f), Helpers.newColor(88, 58, 32, 255));
            DrawModelEx(forest.deciduousModel, Helpers.newVector3(tx, y0 + 2.15f * s, tz),
                       Helpers.newVector3(0, 1, 0), tree.rotation,
                       Helpers.newVector3(s * 1.55f, s * 1.25f, s * 1.55f), leaf);
            if (!lod) {
                DrawModelEx(forest.canopyModel, Helpers.newVector3(tx + 0.7f * s, y0 + 1.9f * s, tz + 0.15f * s),
                           Helpers.newVector3(0, 1, 0), tree.rotation + 25f,
                           Helpers.newVector3(s * 1.05f, s * 0.9f, s * 1.05f), Fade(leaf, 0.92f));
                DrawModelEx(forest.canopyModel, Helpers.newVector3(tx - 0.65f * s, y0 + 2.0f * s, tz - 0.25f * s),
                           Helpers.newVector3(0, 1, 0), tree.rotation - 35f,
                           Helpers.newVector3(s * 0.95f, s * 0.85f, s * 0.95f), Fade(leaf, 0.88f));
            }
        }
    }

    public static void unloadForestTerrain(ForestTerrain forest) {
        UnloadModel(forest.terrainModel);
        UnloadModel(forest.trunkModel);
        UnloadModel(forest.canopyModel);
        UnloadModel(forest.pineModel);
        UnloadModel(forest.deciduousModel);
        forest.trees.clear();
        forest.biomes.clear();
    }

    /** Cylinder collision against tree trunks — keeps the player out of trees. */
    public static Vector3 resolveTreeCollision(ForestTerrain forest, Vector3 position, float radius) {
        for (ForestTree tree : forest.trees) {
            float trunkRadius = 0.18f * tree.scale;
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

    private static int lerpi(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }
}