#pragma once

#include "raylib.h"
#include <vector>

struct ForestTree {
    Vector3 position;
    float scale;
    float rotation;
    int biomeType = 0;   // 0 = broadleaf, 1 = pine, 2 = autumn, 3 = bush
    int colorVariant = 0; // 0..3 leaf color variation
};

struct Biome {
    float startX, startZ;
    float width, depth;
    Color grassColor;
    Color dirtColor;
    float heightScale;
    int treeDensity; // 0-100
};

struct CaveMouth {
    Vector3 entrance;   // world position of the opening
    float radius = 3.2f;
    float rotation = 0.0f;
};

struct ForestTerrain {
    Model terrainModel{};
    Model trunkModel{};
    Model canopyModel{};
    Model pineModel{};
    Model deciduousModel{};
    std::vector<ForestTree> trees;
    std::vector<Biome> biomes;
    std::vector<CaveMouth> caveMouths;
    float size = 320.0f;
    float cellSize = 2.5f;
    float heightScale = 14.0f;
    float waterLevel = 0.0f; // world-space Y of the river/lake surface; set in GenerateForestTerrain
    // The seed the current mesh/trees were generated from. GetTerrainHeight
    // MUST sample using this, not a hardcoded value, or collision queries
    // describe a different terrain than the one actually on screen.
    unsigned int seed = 0xC0FFEEu;
};

ForestTerrain GenerateForestTerrain(unsigned int seed = 0xC0FFEEu);
// camPos + maxDist: distance cull / LOD (maxDist <= 0 draws everything).
// tint multiplies every color drawn (terrain + trees) - pass the day/night
// ambient tint from weather.h so the world actually darkens at night.
void DrawForestTerrain(const ForestTerrain& forest, Vector3 camPos, float maxDist = 120.0f, Color tint = WHITE);
void DrawCaveMouths(const ForestTerrain& forest, Vector3 camPos, float maxDist, Color tint = WHITE);
void UnloadForestTerrain(ForestTerrain& forest);
float GetTerrainHeight(const ForestTerrain& forest, float x, float z);
// Keeps a spherical player/controller avatar from entering procedural tree trunks.
void ResolveTreeCollision(const ForestTerrain& forest, Vector3& position, float radius);
float GetBiomeHeightScale(const ForestTerrain& forest, float x, float z);
Color GetBiomeGrassColor(const ForestTerrain& forest, float x, float z);
