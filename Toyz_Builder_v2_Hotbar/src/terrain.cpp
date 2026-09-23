#include "terrain.h"
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

    static float HeightAt(float x, float z, float size, float heightScale, unsigned int seed) {
        const float nx = x / size;
        const float nz = z / size;
        const float broad = FractalNoise(nx * 3.0f + 20.0f, nz * 3.0f - 17.0f, seed);
        const float detail = FractalNoise(nx * 15.0f - 7.0f, nz * 15.0f + 11.0f, seed ^ 0x9e3779b9u);
        float h = (broad * 0.82f + detail * 0.18f) * heightScale;

        // Flatten the outer edge slightly so the playable forest has a natural border.
        float edge = std::max(std::abs(nx), std::abs(nz));
        float flatten = std::clamp((edge - 0.42f) / 0.58f, 0.0f, 1.0f);
        h *= (1.0f - flatten * 0.35f);
        return h - heightScale * 0.42f;
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
        // Use noise to determine biome type
        float nx = x / size;
        float nz = z / size;
        float biomeNoise = FractalNoise(nx * 0.1f, nz * 0.1f, seed + 12345u);
        
        if (biomeNoise < 0.25f) {
            // Forest biome - dark green
            return Color{50, 100, 50, 255};
        } else if (biomeNoise < 0.5f) {
            // Meadow biome - light green
            return Color{100, 150, 80, 255};
        } else if (biomeNoise < 0.75f) {
            // Mountain biome - brown/gray
            return Color{120, 100, 80, 255};
        } else {
            // Sandy biome - light brown
            return Color{180, 160, 100, 255};
        }
    }
}

float GetTerrainHeight(const ForestTerrain& forest, float x, float z) {
    return HeightAt(x, z, forest.size, forest.heightScale, 0xC0FFEEu);
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
    forest.size = 180.0f;
    forest.cellSize = 2.0f;
    forest.heightScale = 9.0f;
    
    // Create biomes for different terrain types
    forest.biomes = {
        {-90.0f, -90.0f, 90.0f, 90.0f, Color{72, 108, 55, 255}, Color{100, 75, 50, 255}, 1.0f, 60},   // Forest center
        {0.0f, -90.0f, 90.0f, 90.0f, Color{100, 150, 80, 255}, Color{100, 75, 50, 255}, 0.8f, 40},  // Meadow right
        {-90.0f, 0.0f, 90.0f, 90.0f, Color{180, 160, 100, 255}, Color{150, 120, 80, 255}, 0.6f, 20},  // Desert top
        {-90.0f, -90.0f, 90.0f, 90.0f, Color{120, 100, 80, 255}, Color{100, 80, 60, 255}, 1.5f, 80}   // Mountains left
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
            mesh.texcoords[i * 2 + 0] = static_cast<float>(x) / cells * 12.0f;
            mesh.texcoords[i * 2 + 1] = static_cast<float>(z) / cells * 12.0f;
            mesh.normals[i * 3 + 0] = 0.0f;
            mesh.normals[i * 3 + 1] = 1.0f;
            mesh.normals[i * 3 + 2] = 0.0f;
            
            // Vertex color based on biome
            Color biomeColor = GetBiomeColor(worldX, worldZ, forest.size, seed);
            mesh.colors[i * 4 + 0] = biomeColor.r;
            mesh.colors[i * 4 + 1] = biomeColor.g;
            mesh.colors[i * 4 + 2] = biomeColor.b;
            mesh.colors[i * 4 + 3] = biomeColor.a;
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
    forest.terrainModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = WHITE; // Use vertex colors

    Mesh trunkMesh = GenMeshCylinder(0.18f, 2.0f, 16);
    forest.trunkModel = LoadModelFromMesh(trunkMesh);
    forest.trunkModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{92, 60, 35, 255};

    Mesh canopyMesh = GenMeshSphere(1.25f, 16, 16);
    forest.canopyModel = LoadModelFromMesh(canopyMesh);
    forest.canopyModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{43, 93, 45, 255};
    
    // Create pine tree model (taller, thinner)
    Mesh pineTrunkMesh = GenMeshCylinder(0.12f, 2.5f, 12);
    forest.pineModel = LoadModelFromMesh(pineTrunkMesh);
    forest.pineModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{80, 50, 30, 255};
    
    // Create deciduous tree model (wider canopy)
    Mesh deciduousCanopyMesh = GenMeshSphere(1.5f, 12, 12);
    forest.deciduousModel = LoadModelFromMesh(deciduousCanopyMesh);
    forest.deciduousModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{60, 120, 50, 255};
    
    // Enhanced tree models with better detail
    Mesh trunkMesh2 = GenMeshCylinder(0.18f, 2.0f, 16);
    forest.trunkModel = LoadModelFromMesh(trunkMesh2);
    forest.trunkModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{92, 60, 35, 255};
    
    // Add bark texture variation using vertex colors
    for (int i = 0; i < forest.trunkModel.meshCount; i++) {
        forest.trunkModel.materials[i].maps[MATERIAL_MAP_DIFFUSE].color = Color{82, 50, 25, 255};
    }

    // Enhanced forest distribution with biome-based tree types
    // Clear a small starting area around the origin
    const float spacing = 3.8f;
    
    // First pass: regular trees based on biome
    for (float z = -forest.size * 0.46f; z <= forest.size * 0.46f; z += spacing) {
        for (float x = -forest.size * 0.46f; x <= forest.size * 0.46f; x += spacing) {
            float jitterX = (Hash2D(static_cast<int>(x * 10), static_cast<int>(z * 10), seed) - 0.5f) * 2.4f;
            float jitterZ = (Hash2D(static_cast<int>(z * 10), static_cast<int>(x * 10), seed + 77u) - 0.5f) * 2.4f;
            float px = x + jitterX;
            float pz = z + jitterZ;
            if (px * px + pz * pz < 12.0f * 12.0f) continue;

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
            
            // Determine tree type based on biome
            float treeType = Hash2D(static_cast<int>(x * 5), static_cast<int>(z * 5), seed + 123u);
            
            // Different tree types with varying scales
            if (treeType < 0.2f) {
                // Small bushes
                tree.scale = 0.3f + Hash2D(static_cast<int>(x * 7), static_cast<int>(z * 7), seed + 17u) * 0.2f;
                tree.biomeType = 0;
            } else if (treeType < 0.6f) {
                // Medium trees
                tree.scale = 0.75f + Hash2D(static_cast<int>(x * 7), static_cast<int>(z * 7), seed + 17u) * 0.5f;
                tree.biomeType = 0;
            } else {
                // Large trees
                tree.scale = 1.0f + Hash2D(static_cast<int>(x * 7), static_cast<int>(z * 7), seed + 17u) * 0.5f;
                tree.biomeType = 0;
            }
            
            tree.rotation = Hash2D(static_cast<int>(x * 13), static_cast<int>(z * 13), seed + 29u) * 360.0f;
            forest.trees.push_back(tree);
        }
    }
    
    // Add some larger landmark trees for visual interest
    for (int i = 0; i < 5; i++) {
        float angle = Hash2D(i, seed + 456u) * 360.0f;
        float distance = 40.0f + Hash2D(i, seed + 789u) * 40.0f;
        float px = cosf(angle * DEG2RAD) * distance;
        float pz = sinf(angle * DEG2RAD) * distance;
        
        ForestTree landmark;
        landmark.position = {px, HeightAt(px, pz, forest.size, forest.heightScale, seed), pz};
        landmark.scale = 1.5f + Hash2D(i, seed + 111u) * 0.5f;
        landmark.rotation = Hash2D(i, seed + 222u) * 360.0f;
        landmark.biomeType = 1; // Pine
        forest.trees.push_back(landmark);
    }

    return forest;
}

void DrawForestTerrain(const ForestTerrain& forest) {
    // Draw terrain with vertex shading based on height
    DrawModel(forest.terrainModel, {0, 0, 0}, 1.0f, WHITE);

    for (const ForestTree& tree : forest.trees) {
        float s = tree.scale;
        
        // Different tree types based on biome
        if (tree.biomeType == 1) {
            // Pine tree
            DrawModelEx(forest.pineModel, {tree.position.x, tree.position.y + 0.9f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s, s * 1.2f, s}, Color{80, 50, 30, 255});
            
            // Pine canopy (multiple layers)
            Color pineGreen = Color{30, 80, 40, 200};
            DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 2.0f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 1.2f, s * 0.8f, s * 1.2f}, pineGreen);
            DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 2.5f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 1.0f, s * 0.6f, s * 1.0f}, Fade(pineGreen, 0.7f));
            DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 3.0f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 0.8f, s * 0.4f, s * 0.8f}, Fade(pineGreen, 0.5f));
        } else if (tree.biomeType == 2) {
            // Deciduous tree
            DrawModelEx(forest.trunkModel, {tree.position.x, tree.position.y + 0.9f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 1.2f, s * 1.5f, s * 1.2f}, Color{92, 60, 35, 255});
            
            // Wide deciduous canopy
            Color deciduousGreen = Color{60, 120, 50, 200};
            DrawModelEx(forest.deciduousModel, {tree.position.x, tree.position.y + 2.0f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 2.0f, s * 1.5f, s * 2.0f}, deciduousGreen);
        } else {
            // Default tree (forest)
            // Draw trunk with height-based shading
            Color trunkColor = Color{
                (unsigned char)(92 * (0.8f + tree.scale * 0.2f)),
                (unsigned char)(60 * (0.8f + tree.scale * 0.2f)),
                (unsigned char)(35 * (0.8f + tree.scale * 0.2f)),
                255
            };
            
            DrawModelEx(forest.trunkModel, {tree.position.x, tree.position.y + 0.9f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s, s * 1.2f, s}, trunkColor);
            
            // Draw canopy with variation
            Color canopyColor = Color{
                (unsigned char)(43 * (0.7f + tree.scale * 0.3f)),
                (unsigned char)(93 * (0.7f + tree.scale * 0.3f)),
                (unsigned char)(45 * (0.7f + tree.scale * 0.3f)),
                200
            };
            
            DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 2.0f * s, tree.position.z},
                        {0, 1, 0}, tree.rotation, {s * 1.5f, s * 1.2f, s * 1.5f}, canopyColor);
            
            // Add a second canopy layer for larger trees
            if (s > 0.8f) {
                DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 2.5f * s, tree.position.z},
                            {0, 1, 0}, tree.rotation, {s * 1.2f, s * 0.8f, s * 1.2f}, Fade(canopyColor, 0.8f));
            }
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
