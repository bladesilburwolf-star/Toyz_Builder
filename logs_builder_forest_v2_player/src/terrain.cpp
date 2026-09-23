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
}

float GetTerrainHeight(const ForestTerrain& forest, float x, float z) {
    return HeightAt(x, z, forest.size, forest.heightScale, 0xC0FFEEu);
}

ForestTerrain GenerateForestTerrain(unsigned int seed) {
    ForestTerrain forest;
    forest.size = 180.0f;
    forest.cellSize = 2.0f;
    forest.heightScale = 9.0f;

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
    forest.terrainModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{72, 108, 55, 255};

    Mesh trunkMesh = GenMeshCylinder(0.18f, 2.0f, 8);
    forest.trunkModel = LoadModelFromMesh(trunkMesh);
    forest.trunkModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{92, 60, 35, 255};

    Mesh canopyMesh = GenMeshSphere(1.25f, 8, 8);
    forest.canopyModel = LoadModelFromMesh(canopyMesh);
    forest.canopyModel.materials[0].maps[MATERIAL_MAP_DIFFUSE].color = Color{43, 93, 45, 255};

    // Deterministic forest distribution. Clear a small starting area around the origin.
    const float spacing = 3.8f;
    for (float z = -forest.size * 0.46f; z <= forest.size * 0.46f; z += spacing) {
        for (float x = -forest.size * 0.46f; x <= forest.size * 0.46f; x += spacing) {
            float jitterX = (Hash2D(static_cast<int>(x * 10), static_cast<int>(z * 10), seed) - 0.5f) * 2.4f;
            float jitterZ = (Hash2D(static_cast<int>(z * 10), static_cast<int>(x * 10), seed + 77u) - 0.5f) * 2.4f;
            float px = x + jitterX;
            float pz = z + jitterZ;
            if (px * px + pz * pz < 12.0f * 12.0f) continue;

            float density = Hash2D(static_cast<int>(x * 3), static_cast<int>(z * 3), seed + 991u);
            if (density < 0.32f) continue;

            ForestTree tree;
            tree.position = {px, HeightAt(px, pz, forest.size, forest.heightScale, seed), pz};
            tree.scale = 0.75f + Hash2D(static_cast<int>(x * 7), static_cast<int>(z * 7), seed + 17u) * 0.75f;
            tree.rotation = Hash2D(static_cast<int>(x * 13), static_cast<int>(z * 13), seed + 29u) * 360.0f;
            forest.trees.push_back(tree);
        }
    }

    return forest;
}

void DrawForestTerrain(const ForestTerrain& forest) {
    DrawModel(forest.terrainModel, {0, 0, 0}, 1.0f, WHITE);

    for (const ForestTree& tree : forest.trees) {
        float s = tree.scale;
        DrawModelEx(forest.trunkModel, {tree.position.x, tree.position.y + 0.9f * s, tree.position.z},
                    {0, 1, 0}, tree.rotation, {s, s, s}, WHITE);
        DrawModelEx(forest.canopyModel, {tree.position.x, tree.position.y + 2.0f * s, tree.position.z},
                    {0, 1, 0}, tree.rotation, {s, s, s}, WHITE);
    }
}

void UnloadForestTerrain(ForestTerrain& forest) {
    UnloadModel(forest.terrainModel);
    UnloadModel(forest.trunkModel);
    UnloadModel(forest.canopyModel);
    forest.trees.clear();
}
