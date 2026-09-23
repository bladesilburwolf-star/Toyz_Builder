#pragma once

#include "raylib.h"
#include <vector>

struct ForestTree {
    Vector3 position;
    float scale;
    float rotation;
    int biomeType = 0; // 0 = forest, 1 = pine, 2 = deciduous
};

struct Biome {
    float startX, startZ;
    float width, depth;
    Color grassColor;
    Color dirtColor;
    float heightScale;
    int treeDensity; // 0-100
};

struct ForestTerrain {
    Model terrainModel{};
    Model trunkModel{};
    Model canopyModel{};
    Model pineModel{};
    Model deciduousModel{};
    std::vector<ForestTree> trees;
    std::vector<Biome> biomes;
    float size = 180.0f;
    float cellSize = 2.0f;
    float heightScale = 8.0f;
};

ForestTerrain GenerateForestTerrain(unsigned int seed = 0xC0FFEEu);
void DrawForestTerrain(const ForestTerrain& forest);
void UnloadForestTerrain(ForestTerrain& forest);
float GetTerrainHeight(const ForestTerrain& forest, float x, float z);
// Keeps a spherical player/controller avatar from entering procedural tree trunks.
void ResolveTreeCollision(const ForestTerrain& forest, Vector3& position, float radius);
float GetBiomeHeightScale(const ForestTerrain& forest, float x, float z);
Color GetBiomeGrassColor(const ForestTerrain& forest, float x, float z);
