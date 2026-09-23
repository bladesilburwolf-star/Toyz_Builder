#pragma once
#include "raylib.h"
#include "raymath.h"
#include "terrain.h"

struct Player {
    Vector3 position{0.0f, 0.0f, 0.0f};
    Vector3 velocity{0.0f, 0.0f, 0.0f};
    float yaw = 0.0f;
    float radius = 0.55f;
    float eyeHeight = 0.48f;
    bool grounded = true;
};

void InitPlayer(Player& player, const ForestTerrain& forest);
void UpdatePlayer(Player& player, const ForestTerrain& forest, float dt,
                  bool hasController, int gamepadId, float cameraYaw, bool allowJump = true);
void DrawPlayer(const Player& player);
