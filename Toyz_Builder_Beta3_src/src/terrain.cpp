#include "terrain.h"
#include "raymath.h"
#include <cmath>
#include <cstdint>
#include <algorithm>

namespace {
    static float Hash2D(int x, int z, unsigned int seed) {
        uint32_t h = static_cast<uint32_t>(x) * 374761393u;
        h ^= static_cast<uint32_t>(z) * 668265263u;
        h ^= seed * 1442695041u;
        h = (h ^ (h >> 13)) * 1274126177u;
        h ^= h >> 16;
        return static_cast<float>(h & 0x00ffffffu) / 16777215.0f;
    }

    static float Smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    static float ValueNoise(float x, float z, unsigned int seed) {
        int x0 = static_cast<int>(std::floor(x));
        int z0 = static_cast<int>(std::floor(z));
        float fx = Smooth(x - static_cast<float>(x0));
        float fz = Smooth(z - static_cast<float>(z0));

        float a = Hash2D(x0,     z0,     seed);
        float b = Hash2D(x0 + 1, z0,     seed);
        float c = Hash2D(x0,     z0 + 1, seed);
        float d = Hash2D(x0 + 1, z0 + 1, seed);
        float ab = a + (b - a) * fx;
        float cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    static float FractalNoise(float x, float z, unsigned int seed) {
        float value = 0.0f;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float totalAmplitude = 0.0f;
        for (int octave = 0; octave < 5; ++octave) {
            value += ValueNoise(x * frequency, z * frequency, seed + octave * 1013u) * amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        return value / totalAmplitude;
    }

    // Water sits at this fraction of heightScale (negative = below the
    // "sea level" datum used elsewhere). Shared between HeightAt's river
    // carving and GenerateForestTerrain's water plane so they always agree.
    constexpr float kWaterLevelFrac = -0.30f;

    // One meandering river running roughly along Z, offset in X by a smooth
    // noise curve so it isn't a straight line. Returns 1.0 at the river's
    // center, fading to 0.0 a few widths out; 0 means "no river here".
    static float RiverInfluence(float nx, float nz, unsigned int seed) {
        float meander = (FractalNoise(nz * 3.0f + 50.0f, 5.0f, seed + 777u) - 0.5f) * 0.5f;
        float width = 0.018f + 0.014f * FractalNoise(nz * 4.0f + 90.0f, 8.0f, seed + 888u);
        float dist = std::abs(nx - meander);
        return 1.0f - std::clamp((dist - width) / (width * 2.5f + 0.001f), 0.0f, 1.0f);
    }

    static float SmoothStepF(float e0, float e1, float x) {
        float t = std::clamp((x - e0) / (e1 - e0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    // Rare flat "special" patches sprinkled across the map via a slow
    // secondary noise channel, independent of the main biome bands so they
    // can appear inside any of them (a magma crack through the mountains, a
    // swamp pooling at the forest's edge, etc).
    static void GetSpecialPatch(float nx, float nz, unsigned int seed, bool& isMagma, bool& isSwamp, float& strength) {
        float special = FractalNoise(nx * 0.22f + 77.0f, nz * 0.22f - 33.0f, seed + 54321u);
        isMagma = special > 0.90f;
        isSwamp = special < 0.10f;
        strength = 0.0f;
        if (isMagma) strength = SmoothStepF(0.90f, 0.97f, special);
        else if (isSwamp) strength = SmoothStepF(0.10f, 0.03f, special);
    }

    // Deterministic cave-mouth candidate for the ~42-unit world cell (cx,cz)
    // lies in. Most cells have none; the ones that do must roll a low hash
    // AND land in mountainous terrain, so cave mouths read as "found in the
    // mountains" rather than scattered randomly across meadows.
    struct CaveCandidate { bool valid = false; Vector3 center{0,0,0}; float radius = 3.2f; };
    constexpr float kCaveCellSize = 42.0f;

    static CaveCandidate CaveCellAt(int cx, int cz, float size, unsigned int seed) {
        CaveCandidate result;
        float roll = Hash2D(cx, cz, seed + 0xCAFEu);
        if (roll > 0.07f) return result; // ~7% of cells

        float jx = (Hash2D(cx * 3 + 1, cz * 3 + 1, seed + 0xCAFEu) - 0.5f) * kCaveCellSize * 0.6f;
        float jz = (Hash2D(cx * 3 + 2, cz * 3 + 2, seed + 0xCAFEu) - 0.5f) * kCaveCellSize * 0.6f;
        float centerX = (cx + 0.5f) * kCaveCellSize + jx;
        float centerZ = (cz + 0.5f) * kCaveCellSize + jz;

        float nx = centerX / size, nz = centerZ / size;
        float biomeN = FractalNoise(nx * 0.35f, nz * 0.35f, seed + 12345u);
        if (biomeN > 0.28f) return result; // mountains / rocky highlands only
        if (centerX * centerX + centerZ * centerZ < 25.0f * 25.0f) return result; // stay out of spawn
        float edge = std::max(std::abs(nx), std::abs(nz));
        if (edge > 0.42f) return result; // stay inside the playable border

        result.valid = true;
        result.center = { centerX, 0.0f, centerZ };
        result.radius = 3.0f + Hash2D(cx * 5, cz * 5, seed + 0xBEEFu) * 1.5f;
        return result;
    }

    static CaveCandidate CaveCellAtWorld(float x, float z, float size, unsigned int seed) {
        int cx = static_cast<int>(std::floor(x / kCaveCellSize));
        int cz = static_cast<int>(std::floor(z / kCaveCellSize));
        return CaveCellAt(cx, cz, size, seed);
    }

    // Height WITHOUT river/cave carving - the "solid rock" a cave digs into,
    // and the input the river-blend and cave-blend both work from.
    static float BaseHeightNoCarve(float x, float z, float size, float heightScale, unsigned int seed) {
        const float nx = x / size;
        const float nz = z / size;
        const float broad = FractalNoise(nx * 2.4f + 20.0f, nz * 2.4f - 17.0f, seed);
        const float detail = FractalNoise(nx * 12.0f - 7.0f, nz * 12.0f + 11.0f, seed ^ 0x9e3779b9u);
        const float ridges = FractalNoise(nx * 6.0f + 1.0f, nz * 6.0f - 3.0f, seed + 333u);

        // Biome-driven height scale (mountains taller, deserts flatter)
        float biomeN = FractalNoise(nx * 0.35f, nz * 0.35f, seed + 12345u);
        float localScale = heightScale;
        bool isMountain = biomeN < 0.20f;
        if (isMountain) localScale *= 2.3f;             // mountains - much taller peaks now
        else if (biomeN < 0.40f) localScale *= 1.15f;   // hills / forest
        else if (biomeN < 0.60f) localScale *= 0.95f;   // meadow
        else if (biomeN < 0.80f) localScale *= 0.70f;   // plains / desert
        else localScale *= 1.6f;                        // rocky highlands

        // Mountains lean harder on the ridge noise for jagged peaks/ravines
        // instead of just being a taller version of the same rolling hills.
        float ridgeWeight = isMountain ? 0.38f : 0.10f;
        float h = (broad * (0.90f - ridgeWeight) + detail * 0.18f + ridges * ridgeWeight) * localScale;

        // Flatten the outer edge slightly so the playable forest has a natural border.
        float edge = std::max(std::abs(nx), std::abs(nz));
        float flatten = std::clamp((edge - 0.40f) / 0.60f, 0.0f, 1.0f);
        h *= (1.0f - flatten * 0.40f);
        h = h - heightScale * 0.38f;

        // Magma plains / swamp: flatten toward a target elevation so they
        // read as distinct flat land features rather than textured hills.
        bool isMagma, isSwamp; float patchStrength;
        GetSpecialPatch(nx, nz, seed, isMagma, isSwamp, patchStrength);
        if (isMagma) {
            float target = heightScale * -0.10f; // flat, sits just above the waterline
            h = Lerp(h, target, patchStrength);
        } else if (isSwamp) {
            float target = heightScale * kWaterLevelFrac + heightScale * 0.04f; // marshy, just above water
            h = Lerp(h, target, patchStrength);
        }

        return h;
    }

    static float HeightAt(float x, float z, float size, float heightScale, unsigned int seed) {
        float h = BaseHeightNoCarve(x, z, size, heightScale, seed);
        const float nx = x / size;
        const float nz = z / size;

        // Single meandering river cut across the map. RiverInfluence is 1.0
        // dead-center and fades out over a few widths; where it's > 0 we pull
        // the terrain down toward the water level so the channel is a real
        // carved valley, not just a paint-on blue stripe.
        float river = RiverInfluence(nx, nz, seed);
        if (river > 0.0f) {
            float waterLevel = heightScale * kWaterLevelFrac;
            float bed = waterLevel - heightScale * 0.05f; // slight riverbed dip below the waterline
            h = h * (1.0f - river) + bed * river;
        }

        // Cave mouths: a shallow bowl carved into the mountainside at each
        // selected cell, floored relative to that spot's own base height so
        // it reads as "dug into the rock" rather than a flat pit.
        CaveCandidate cave = CaveCellAtWorld(x, z, size, seed);
        if (cave.valid) {
            float dx = x - cave.center.x, dz = z - cave.center.z;
            float dist = std::sqrt(dx * dx + dz * dz);
            if (dist < cave.radius) {
                float floorY = BaseHeightNoCarve(cave.center.x, cave.center.z, size, heightScale, seed) - heightScale * 0.35f;
                float t = 1.0f - dist / cave.radius;
                h = Lerp(h, floorY, t * t);
            }
        }

        return h;
    }

    static float HeightAtBiome(float x, float z, float size, float heightScale, unsigned int seed, float biomeScale = 1.0f) {
        const float nx = x / size;
        const float nz = z / size;
        const float broad = FractalNoise(nx * 3.0f + 20.0f, nz * 3.0f - 17.0f, seed);
        const float detail = FractalNoise(nx * 15.0f - 7.0f, nz * 15.0f + 11.0f, seed ^ 0x9e3779b9u);
        float h = (broad * 0.82f + detail * 0.18f) * heightScale * biomeScale;

        // Flatten the outer edge slightly so the playable forest has a natural border.
        float edge = std::max(std::abs(nx), std::abs(nz));
        float flatten = std::clamp((edge - 0.42f) / 0.58f, 0.0f, 1.0f);
        h *= (1.0f - flatten * 0.35f);
        return h - heightScale * 0.42f;
    }

    static Color GetBiomeColor(float x, float z, float size, unsigned int seed) {
        float nx = x / size;
        float nz = z / size;
        float biomeNoise = FractalNoise(nx * 0.35f, nz * 0.35f, seed + 12345u);
        float moisture = FractalNoise(nx * 0.5f + 9.0f, nz * 0.5f - 4.0f, seed + 555u);

        Color base;
        // Distinct biomes for a Minecraft-scale walk (still flat vertex colors)
        if (biomeNoise < 0.18f) {
            base = Color{110, 115, 120, 255}; // mountain rock
        } else if (biomeNoise < 0.35f) {
            base = (moisture > 0.5f) ? Color{48, 105, 48, 255} : Color{70, 120, 55, 255}; // dense / open forest
        } else if (biomeNoise < 0.52f) {
            base = Color{95, 145, 65, 255}; // meadow
        } else if (biomeNoise < 0.68f) {
            base = Color{175, 155, 95, 255}; // dry grass / savannah
        } else if (biomeNoise < 0.82f) {
            base = Color{195, 175, 120, 255}; // sand / desert
        } else {
            base = Color{200, 205, 210, 255}; // highland / light stone
        }

        bool isMagma, isSwamp; float patchStrength;
        GetSpecialPatch(nx, nz, seed, isMagma, isSwamp, patchStrength);
        if (isMagma && patchStrength > 0.0f) {
            // Dark cracked basalt, with a fraction of the ground glowing
            // orange-red like exposed magma veins instead of a flat color.
            float crack = FractalNoise(nx * 30.0f + 4.0f, nz * 30.0f - 6.0f, seed + 0x5EED1u);
            Color basalt = Color{40, 36, 34, 255};
            Color glow = Color{230, 90, 25, 255};
            Color magmaGround = (crack > 0.78f) ? glow : basalt;
            base.r = (unsigned char)Lerp((float)base.r, (float)magmaGround.r, patchStrength);
            base.g = (unsigned char)Lerp((float)base.g, (float)magmaGround.g, patchStrength);
            base.b = (unsigned char)Lerp((float)base.b, (float)magmaGround.b, patchStrength);
        } else if (isSwamp && patchStrength > 0.0f) {
            Color murk = Color{62, 68, 42, 255};
            base.r = (unsigned char)Lerp((float)base.r, (float)murk.r, patchStrength);
            base.g = (unsigned char)Lerp((float)base.g, (float)murk.g, patchStrength);
            base.b = (unsigned char)Lerp((float)base.b, (float)murk.b, patchStrength);
        }
        return base;
    }
}

float GetTerrainHeight(const ForestTerrain& forest, float x, float z) {
    return HeightAt(x, z, forest.size, forest.heightScale, forest.seed);
}

float GetBiomeHeightScale(const ForestTerrain& forest, float x, float z) {
    // Check which biome this position is in
    for (const auto& biome : forest.biomes) {
        if (x >= biome.startX && x <= biome.startX + biome.width &&
            z >= biome.startZ && z <= biome.startZ + biome.depth) {
            return biome.heightScale;
        }
    }
    return 1.0f; // Default
}

Color GetBiomeGrassColor(const ForestTerrain& forest, float x, float z) {
    // Check which biome this position is in
    for (const auto& biome : forest.biomes) {
        if (x >= biome.startX && x <= biome.startX + biome.width &&
            z >= biome.startZ && z <= biome.startZ + biome.depth) {
            return biome.grassColor;
        }
    }
    return Color{72, 108, 55, 255}; // Default forest green
}

ForestTerrain GenerateForestTerrain(unsigned int seed) {
    ForestTerrain forest;
    forest.seed = seed;
    // Larger playable area (Minecraft-ish walk distance) without exploding poly count:
    // 320 / 2.5 ≈ 128 cells → ~16k verts, fine for older GPUs.
    forest.size = 320.0f;
    forest.cellSize = 2.5f;
    forest.heightScale = 14.0f;
    forest.waterLevel = forest.heightScale * kWaterLevelFrac;

    // Quadrant-ish biomes used for tree density (colors still come from noise)
    const float h = forest.size * 0.5f;
    forest.biomes = {
        {-h, -h, h, h, Color{55, 110, 50, 255}, Color{100, 75, 50, 255}, 1.2f, 65},  // SW forest
        {0, -h, h, h, Color{100, 150, 80, 255}, Color{100, 75, 50, 255}, 0.9f, 40},  // SE meadow
        {-h, 0, h, h, Color{185, 165, 105, 255}, Color{150, 120, 80, 255}, 0.65f, 15}, // NW dry
        {0, 0, h, h, Color{120, 110, 100, 255}, Color{100, 80, 60, 255}, 1.5f, 50}   // NE hills
    };

    const int cells = static_cast<int>(forest.size / forest.cellSize);
    const int vertsPerSide = cells + 1;
    const int vertexCount = vertsPerSide * vertsPerSide;
    const int triangleCount = cells * cells * 2;

    Mesh mesh{};
    mesh.vertexCount = vertexCount;
    mesh.triangleCount = triangleCount;
    mesh.vertices = static_cast<float*>(MemAlloc(vertexCount * 3 * sizeof(float)));
    mesh.normals = static_cast<float*>(MemAlloc(vertexCount * 3 * sizeof(float)));
    mesh.texcoords = static_cast<float*>(MemAlloc(vertexCount * 2 * sizeof(float)));
    mesh.colors = static_cast<unsigned char*>(MemAlloc(vertexCount * 4 * sizeof(unsigned char)));
    mesh.indices = static_cast<unsigned short*>(MemAlloc(triangleCount * 3 * sizeof(unsigned short)));

    for (int z = 0; z < vertsPerSide; ++z) {
        for (int x = 0; x < vertsPerSide; ++x) {
            int i = z * vertsPerSide + x;
            float worldX = -forest.size * 0.5f + x * forest.cellSize;
            float worldZ = -forest.size * 0.5f + z * forest.cellSize;
            float y = HeightAt(worldX, worldZ, forest.size, forest.heightScale, seed);

            mesh.vertices[i * 3 + 0] = worldX;
            mesh.vertices[i * 3 + 1] = y;
            mesh.vertices[i * 3 + 2] = worldZ;
            // Tile UVs so grass texture repeats across the world
            mesh.texcoords[i * 2 + 0] = static_cast<float>(x) / cells * 24.0f;
            mesh.texcoords[i * 2 + 1] = static_cast<float>(z) / cells * 24.0f;

            // Approximate normal from neighbouring heights (cheap vertex shading)
            float eps = forest.cellSize;
            float hL = HeightAt(worldX - eps, worldZ, forest.size, forest.heightScale, seed);
            float hR = HeightAt(worldX + eps, worldZ, forest.size, forest.heightScale, seed);
            float hD = HeightAt(worldX, worldZ - eps, forest.size, forest.heightScale, seed);
            float hU = HeightAt(worldX, worldZ + eps, forest.size, forest.heightScale, seed);
            Vector3 n = { hL - hR, 2.0f * eps, hD - hU };
            float len = sqrtf(n.x * n.x + n.y * n.y + n.z * n.z);
            if (len > 0.0001f) { n.x /= len; n.y /= len; n.z /= len; }
            mesh.normals[i * 3 + 0] = n.x;
            mesh.normals[i * 3 + 1] = n.y;
            mesh.normals[i * 3 + 2] = n.z;

            // Vertex color based on biome + slight height tint for shading
            Color biomeColor = GetBiomeColor(worldX, worldZ, forest.size, seed);
            float riverAmt = RiverInfluence(worldX / forest.size, worldZ / forest.size, seed);
            if (riverAmt > 0.0f) {
                Color riverbed = Color{90, 80, 65, 255}; // wet sand/mud under the water plane
                biomeColor.r = (unsigned char)Lerp((float)biomeColor.r, (float)riverbed.r, riverAmt);
                biomeColor.g = (unsigned char)Lerp((float)biomeColor.g, (float)riverbed.g, riverAmt);
                biomeColor.b = (unsigned char)Lerp((float)biomeColor.b, (float)riverbed.b, riverAmt);
            }
            float heightFactor = Clamp((y + forest.heightScale * 0.4f) / (forest.heightScale * 1.2f), 0.0f, 1.0f);
            // Darken valleys, lighten peaks a bit
            float shade = 0.75f + heightFactor * 0.35f;
            mesh.colors[i * 4 + 0] = (unsigned char)Clamp(biomeColor.r * shade, 0.0f, 255.0f);
            mesh.colors[i * 4 + 1] = (unsigned char)Clamp(biomeColor.g * shade, 0.0f, 255.0f);
            mesh.colors[i * 4 + 2] = (unsigned char)Clamp(biomeColor.b * shade, 0.0f, 255.0f);
            mesh.colors[i * 4 + 3] = 255;
        }
    }

    int index = 0;
    for (int z = 0; z < cells; ++z) {
        for (int x = 0; x < cells; ++x) {
            unsigned short a = static_cast<unsigned short>(z * vertsPerSide + x);
            unsigned short b = static_cast<unsigned short>(z * vertsPerSide + x + 1);
            unsigned short c = static_cast<unsigned short>((z + 1) * vertsPerSide + x);
            unsigned short d = static_cast<unsigned short>((z + 1) * vertsPerSide + x + 1);
            mesh.indices[index++] = a; mesh.indices[index++] = c; mesh.indices[index++] = b;
            mesh.indices[index++] = b; mesh.indices[index++] = c; mesh.indices[index++] = d;
        }
    }

    UploadMesh(&mesh, false);
    forest.terrainModel = LoadModelFromMesh(mesh);

    // Try to load grass texture (works when assets/ is next to the exe)
    Texture2D grassTex = LoadTexture("assets/textures/grass1.png");
    if (grassTex.id != 0) {
        SetTextureFilter(grassTex, TEXTURE_FILTER_BILINEAR);
        SetTextureWrap(grassTex, TEXTURE_WRAP_REPEAT);
        SetMaterialTexture(&forest.terrainModel.materials[0], MATERIAL_MAP_DIFFUSE, grassTex);
        // Keep vertex colors as a tint over the texture
        forest.terrainModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = WHITE;
    } else {
        // Fallback: solid vertex-colored terrain (no asset folder)
        forest.terrainModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = WHITE;
    }

    // Low-poly trunks (8 sides) — old GPUs hate high segment counts
    Mesh trunkMesh = GenMeshCylinder(0.20f, 2.2f, 8);
    forest.trunkModel = LoadModelFromMesh(trunkMesh);
    Texture2D barkTex = LoadTexture("assets/textures/bark1.png");
    if (barkTex.id != 0) {
        SetTextureFilter(barkTex, TEXTURE_FILTER_BILINEAR);
        SetTextureWrap(barkTex, TEXTURE_WRAP_REPEAT);
        SetMaterialTexture(&forest.trunkModel.materials[0], MATERIAL_MAP_DIFFUSE, barkTex);
        forest.trunkModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = WHITE;
    } else {
        forest.trunkModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{92, 60, 35, 255};
    }

    // Canopy blob — 8x8 sphere is enough for organic look at distance
    Mesh canopyMesh = GenMeshSphere(1.15f, 8, 8);
    forest.canopyModel = LoadModelFromMesh(canopyMesh);
    forest.canopyModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{55, 120, 50, 255};

    Mesh pineTrunkMesh = GenMeshCylinder(0.14f, 2.8f, 8);
    forest.pineModel = LoadModelFromMesh(pineTrunkMesh);
    if (barkTex.id != 0) {
        SetMaterialTexture(&forest.pineModel.materials[0], MATERIAL_MAP_DIFFUSE, barkTex);
        forest.pineModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = WHITE;
    } else {
        forest.pineModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 50, 30, 255};
    }

    Mesh deciduousCanopyMesh = GenMeshSphere(1.35f, 8, 8);
    forest.deciduousModel = LoadModelFromMesh(deciduousCanopyMesh);
    forest.deciduousModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{70, 130, 55, 255};

    // Enhanced forest distribution with biome-based tree types
    // Clear a small starting area around the origin
    // Wider spacing keeps draw calls reasonable on older GPUs (HD 6450 class)
    const float spacing = 7.0f;

    for (float z = -forest.size * 0.46f; z <= forest.size * 0.46f; z += spacing) {
        for (float x = -forest.size * 0.46f; x <= forest.size * 0.46f; x += spacing) {
            float jitterX = (Hash2D(static_cast<int>(x * 10), static_cast<int>(z * 10), seed) - 0.5f) * 3.0f;
            float jitterZ = (Hash2D(static_cast<int>(z * 10), static_cast<int>(x * 10), seed + 77u) - 0.5f) * 3.0f;
            float px = x + jitterX;
            float pz = z + jitterZ;
            // Clear spawn bowl around origin
            if (px * px + pz * pz < 18.0f * 18.0f) continue;
            // Don't plant trees in the middle of the river
            if (RiverInfluence(px / forest.size, pz / forest.size, seed) > 0.4f) continue;

            // Check biome density
            bool inBiome = false;
            for (const auto& biome : forest.biomes) {
                if (px >= biome.startX && px <= biome.startX + biome.width &&
                    pz >= biome.startZ && pz <= biome.startZ + biome.depth) {
                    float density = Hash2D(static_cast<int>(x * 3), static_cast<int>(z * 3), seed + 991u);
                    if (density < biome.treeDensity / 100.0f) continue;
                    inBiome = true;
                    break;
                }
            }
            if (!inBiome) continue;

            ForestTree tree;
            tree.position = {px, HeightAt(px, pz, forest.size, forest.heightScale, seed), pz};

            float treeType = Hash2D(static_cast<int>(x * 5), static_cast<int>(z * 5), seed + 123u);
            float sizeRoll = Hash2D(static_cast<int>(x * 7), static_cast<int>(z * 7), seed + 17u);
            tree.colorVariant = (int)(Hash2D(static_cast<int>(x * 11), static_cast<int>(z * 11), seed + 55u) * 4.0f) % 4;
            tree.rotation = Hash2D(static_cast<int>(x * 13), static_cast<int>(z * 13), seed + 29u) * 360.0f;

            // Mix of shapes so the forest isn't one repeated silhouette
            if (treeType < 0.12f) {
                tree.biomeType = 3; // bush
                tree.scale = 0.35f + sizeRoll * 0.25f;
            } else if (treeType < 0.32f) {
                tree.biomeType = 1; // pine
                tree.scale = 0.85f + sizeRoll * 0.7f;
            } else if (treeType < 0.48f) {
                tree.biomeType = 2; // autumn / pink accents
                tree.scale = 0.7f + sizeRoll * 0.6f;
            } else {
                tree.biomeType = 0; // broadleaf
                tree.scale = 0.65f + sizeRoll * 0.75f;
            }

            forest.trees.push_back(tree);
        }
    }

    // Landmark pines on the ridges
    for (int i = 0; i < 8; i++) {
        float angle = Hash2D(i, 0, seed + 456u) * 360.0f;
        float distance = 35.0f + Hash2D(i, 1, seed + 789u) * 50.0f;
        float px = cosf(angle * DEG2RAD) * distance;
        float pz = sinf(angle * DEG2RAD) * distance;

        ForestTree landmark;
        landmark.position = {px, HeightAt(px, pz, forest.size, forest.heightScale, seed), pz};
        landmark.scale = 1.4f + Hash2D(i, 2, seed + 111u) * 0.7f;
        landmark.rotation = Hash2D(i, 3, seed + 222u) * 360.0f;
        landmark.biomeType = 1;
        landmark.colorVariant = i % 4;
        forest.trees.push_back(landmark);
    }

    // Scan the same coarse grid CaveCellAtWorld uses so the visible cave
    // mouths (archway props) line up exactly with where HeightAt actually
    // carved a bowl into the terrain.
    {
        int halfCells = (int)std::ceil((forest.size * 0.5f) / kCaveCellSize) + 1;
        for (int cz = -halfCells; cz <= halfCells; cz++) {
            for (int cx = -halfCells; cx <= halfCells; cx++) {
                CaveCandidate cave = CaveCellAt(cx, cz, forest.size, seed);
                if (!cave.valid) continue;
                CaveMouth mouth;
                float floorY = HeightAt(cave.center.x, cave.center.z, forest.size, forest.heightScale, seed);
                mouth.entrance = { cave.center.x, floorY, cave.center.z };
                mouth.radius = cave.radius;
                mouth.rotation = Hash2D(cx * 7, cz * 7, seed + 0xFEEDu) * 360.0f;
                forest.caveMouths.push_back(mouth);
            }
        }
    }

    return forest;
}

void DrawForestTerrain(const ForestTerrain& forest, Vector3 camPos, float maxDist, Color tint) {
    DrawModel(forest.terrainModel, {0, 0, 0}, 1.0f, tint);

    // Water plane: sits at the carved river depth, so it only shows through
    // where HeightAt actually pulled the ground below it (the river channel,
    // plus any natural low spots) rather than painting a fake straight strip.
    Color waterCol = Color{
        (unsigned char)((60 * tint.r) / 255), (unsigned char)((120 * tint.g) / 255), (unsigned char)((170 * tint.b) / 255), 255
    };
    DrawCube({0, forest.waterLevel - 0.08f, 0}, forest.size, 0.16f, forest.size, Fade(waterCol, 0.65f));

    static const Color kBroad[] = {
        {48, 110, 42, 255}, {62, 130, 50, 255}, {40, 95, 38, 255}, {70, 140, 55, 255}
    };
    static const Color kAutumn[] = {
        {160, 90, 40, 255}, {180, 60, 70, 255}, {200, 140, 50, 255}, {120, 70, 100, 255}
    };
    static const Color kPine[] = {
        {28, 70, 35, 255}, {35, 85, 42, 255}, {22, 60, 30, 255}, {40, 95, 48, 255}
    };

    const float maxDistSq = maxDist * maxDist;
    const float lodFarSq = (maxDist * 0.55f) * (maxDist * 0.55f); // beyond this: 1-blob LOD

    for (const ForestTree& tree : forest.trees) {
        float dx = tree.position.x - camPos.x;
        float dz = tree.position.z - camPos.z;
        float distSq = dx * dx + dz * dz;
        if (distSq > maxDistSq) continue; // hard cull

        float s = tree.scale;
        float y0 = tree.position.y;
        Vector3 base = {tree.position.x, y0, tree.position.z};
        int cv = tree.colorVariant & 3;
        bool lod = distSq > lodFarSq;

        if (tree.biomeType == 3) {
            Color leaf = ColorTint(kBroad[cv], tint);
            DrawModelEx(forest.canopyModel,
                {base.x, y0 + 0.55f * s, base.z},
                {0, 1, 0}, tree.rotation, {s * 1.1f, s * 0.7f, s * 1.1f}, leaf);
            if (!lod) {
                DrawModelEx(forest.canopyModel,
                    {base.x + 0.35f * s, y0 + 0.45f * s, base.z - 0.2f * s},
                    {0, 1, 0}, tree.rotation + 40.0f, {s * 0.85f, s * 0.55f, s * 0.85f}, Fade(leaf, 0.9f));
            }
            continue;
        }

        if (tree.biomeType == 1) {
            Color pine = ColorTint(kPine[cv], tint);
            if (lod) {
                // Far pine: trunk + one canopy only
                DrawModelEx(forest.pineModel,
                    {base.x, y0 + 1.2f * s, base.z},
                    {0, 1, 0}, tree.rotation, {s * 0.7f, s * 1.35f, s * 0.7f}, ColorTint(Color{75, 48, 28, 255}, tint));
                DrawModelEx(forest.canopyModel,
                    {base.x, y0 + 2.5f * s, base.z},
                    {0, 1, 0}, tree.rotation, {s * 1.1f, s * 0.9f, s * 1.1f}, pine);
            } else {
                DrawModelEx(forest.pineModel,
                    {base.x, y0 + 1.2f * s, base.z},
                    {0, 1, 0}, tree.rotation, {s * 0.7f, s * 1.35f, s * 0.7f}, ColorTint(Color{75, 48, 28, 255}, tint));
                float layers[3] = {1.6f, 2.5f, 3.3f};
                float widths[3] = {1.3f, 1.0f, 0.6f};
                for (int i = 0; i < 3; i++) {
                    DrawModelEx(forest.canopyModel,
                        {base.x, y0 + layers[i] * s, base.z},
                        {0, 1, 0}, tree.rotation + i * 15.0f,
                        {s * widths[i], s * 0.55f, s * widths[i]}, pine);
                }
            }
            continue;
        }

        Color trunkCol = ColorTint(Color{88, 58, 32, 255}, tint);
        Color leaf = ColorTint((tree.biomeType == 2) ? kAutumn[cv] : kBroad[cv], tint);
        if (lod) {
            DrawModelEx(forest.trunkModel,
                {base.x, y0 + 1.0f * s, base.z},
                {0, 1, 0}, tree.rotation, {s * 0.85f, s * 1.15f, s * 0.85f}, trunkCol);
            DrawModelEx(forest.deciduousModel,
                {base.x, y0 + 2.15f * s, base.z},
                {0, 1, 0}, tree.rotation, {s * 1.55f, s * 1.25f, s * 1.55f}, leaf);
        } else {
            DrawModelEx(forest.trunkModel,
                {base.x, y0 + 1.0f * s, base.z},
                {0, 1, 0}, tree.rotation, {s * 0.85f, s * 1.15f, s * 0.85f}, trunkCol);
            DrawModelEx(forest.deciduousModel,
                {base.x, y0 + 2.15f * s, base.z},
                {0, 1, 0}, tree.rotation, {s * 1.55f, s * 1.25f, s * 1.55f}, leaf);
            DrawModelEx(forest.canopyModel,
                {base.x + 0.7f * s, y0 + 1.9f * s, base.z + 0.15f * s},
                {0, 1, 0}, tree.rotation + 25.0f, {s * 1.05f, s * 0.9f, s * 1.05f}, Fade(leaf, 0.92f));
            DrawModelEx(forest.canopyModel,
                {base.x - 0.65f * s, y0 + 2.0f * s, base.z - 0.25f * s},
                {0, 1, 0}, tree.rotation - 35.0f, {s * 0.95f, s * 0.85f, s * 0.95f}, Fade(leaf, 0.88f));
        }
    }
}

void DrawCaveMouths(const ForestTerrain& forest, Vector3 camPos, float maxDist, Color tint) {
    const float maxDistSq = maxDist * maxDist;
    Color rock = ColorTint(Color{60, 55, 52, 255}, tint);
    Color rockDark = ColorTint(Color{30, 27, 25, 255}, tint);
    for (const CaveMouth& cave : forest.caveMouths) {
        float dx = cave.entrance.x - camPos.x, dz = cave.entrance.z - camPos.z;
        if (dx * dx + dz * dz > maxDistSq) continue;

        // A dark pit at the carved floor, framed by a rough archway of rock
        // chunks so it reads as "opening into the mountain" from a distance.
        DrawCylinder(cave.entrance, cave.radius * 0.75f, cave.radius * 0.55f, 0.15f, 10, rockDark);
        for (int i = 0; i < 6; i++) {
            float a = (cave.rotation + i * 60.0f) * DEG2RAD;
            float r = cave.radius * 0.95f;
            Vector3 p = { cave.entrance.x + cosf(a) * r, cave.entrance.y + 0.6f, cave.entrance.z + sinf(a) * r };
            DrawCylinderEx(p, {p.x, p.y + 1.2f + (i % 2) * 0.4f, p.z}, 0.5f, 0.35f, 6, rock);
        }
    }
}

void UnloadForestTerrain(ForestTerrain& forest) {
    UnloadModel(forest.terrainModel);
    UnloadModel(forest.trunkModel);
    UnloadModel(forest.canopyModel);
    UnloadModel(forest.pineModel);
    UnloadModel(forest.deciduousModel);
    forest.trees.clear();
    forest.biomes.clear();
    forest.caveMouths.clear();
}


void ResolveTreeCollision(const ForestTerrain& forest, Vector3& position, float radius) {
    // Trees are represented by simple vertical cylinders for gameplay collision.
    // This intentionally matches the procedural visual trunks closely enough while
    // keeping movement inexpensive even with hundreds of trees.
    for (const ForestTree& tree : forest.trees) {
        const float trunkRadius = 0.18f * tree.scale;
        const float combined = radius + trunkRadius;
        float dx = position.x - tree.position.x;
        float dz = position.z - tree.position.z;
        float distSq = dx * dx + dz * dz;
        if (distSq >= combined * combined || distSq < 0.000001f) {
            if (distSq < 0.000001f) {
                dx = 1.0f; dz = 0.0f; distSq = 1.0f;
            } else {
                continue;
            }
        }
        float dist = std::sqrt(distSq);
        float push = combined - dist;
        position.x += (dx / dist) * push;
        position.z += (dz / dist) * push;
    }
}
