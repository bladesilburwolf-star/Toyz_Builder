#pragma once

#include "raylib.h"
#include <vector>

struct ForestTree {
    Vector3 position;
    float scale;
    float rotation;
};

struct ForestTerrain {
    Model terrainModel{};
    Model trunkModel{};
    Model canopyModel{};
    std::vector<ForestTree> trees;
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
