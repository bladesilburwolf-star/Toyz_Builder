#include "player.h"
#include <cmath>

namespace {
static float ApplyDeadzone(float v, float deadzone) {
    if (std::fabs(v) <= deadzone) return 0.0f;
    float sign = v < 0.0f ? -1.0f : 1.0f;
    return sign * (std::fabs(v) - deadzone) / (1.0f - deadzone);
}
}

void InitPlayer(Player& player, const ForestTerrain& forest) {
    player.position = {0.0f, GetTerrainHeight(forest, 0.0f, 0.0f) + player.radius, 0.0f};
    player.velocity = {0, 0, 0};
    player.yaw = 0.0f;
    player.grounded = true;
}

void UpdatePlayer(Player& player, const ForestTerrain& forest, float dt,
                  bool hasController, int gamepadId, float cameraYaw, bool allowJump) {
    Vector2 move{0, 0};
    if (hasController) {
        move.x = ApplyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_X), 0.15f);
        move.y = ApplyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_Y), 0.15f);
    } else {
        move.x = (IsKeyDown(KEY_D) ? 1.0f : 0.0f) - (IsKeyDown(KEY_A) ? 1.0f : 0.0f);
        move.y = (IsKeyDown(KEY_W) ? 1.0f : 0.0f) - (IsKeyDown(KEY_S) ? 1.0f : 0.0f);
    }

    if (Vector2Length(move) > 1.0f) move = Vector2Normalize(move);

    float yaw = cameraYaw * DEG2RAD;
    Vector3 forward = { std::sin(yaw), 0.0f, std::cos(yaw) };
    Vector3 right = { std::cos(yaw), 0.0f, -std::sin(yaw) };
    Vector3 wish = Vector3Add(Vector3Scale(right, move.x), Vector3Scale(forward, move.y));

    const float speed = 7.0f;
    if (Vector3Length(wish) > 0.001f) {
        wish = Vector3Normalize(wish);
        player.position.x += wish.x * speed * dt;
        player.position.z += wish.z * speed * dt;
        player.yaw = std::atan2(wish.x, wish.z) * RAD2DEG;
    }

    float ground = GetTerrainHeight(forest, player.position.x, player.position.z) + player.radius;
    player.velocity.y -= 22.0f * dt;
    player.position.y += player.velocity.y * dt;
    if (player.position.y <= ground) {
        player.position.y = ground;
        player.velocity.y = 0.0f;
        player.grounded = true;
    } else {
        player.grounded = false;
    }

    bool jump = hasController
        ? IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN)
        : IsKeyPressed(KEY_SPACE);
    if (allowJump && jump && player.grounded) {
        player.velocity.y = 8.0f;
        player.grounded = false;
    }

    const float half = forest.size * 0.5f - 1.5f;
    player.position.x = Clamp(player.position.x, -half, half);
    player.position.z = Clamp(player.position.z, -half, half);

    // Trees are solid props in the world. Resolve after terrain/bounds so the
    // builder can naturally slide around trunks instead of becoming embedded.
    ResolveTreeCollision(forest, player.position, player.radius);
    float correctedGround = GetTerrainHeight(forest, player.position.x, player.position.z) + player.radius;
    if (player.position.y < correctedGround) player.position.y = correctedGround;
}

void DrawPlayer(const Player& player) {
    // Small blue spherical builder mascot: deliberately procedural so no external asset is required.
    Vector3 body = player.position;
    DrawSphere(body, player.radius, Color{52, 132, 220, 255});
    DrawSphereWires(body, player.radius, 12, 8, Color{25, 65, 115, 255});

    float yaw = player.yaw * DEG2RAD;
    Vector3 forward = { std::sin(yaw), 0.0f, std::cos(yaw) };
    Vector3 right = { std::cos(yaw), 0.0f, -std::sin(yaw) };

    // Friendly face, kept as simple geometry so the mascot scales cleanly with the prototype.
    Vector3 faceCenter = Vector3Add(body, Vector3Scale(forward, player.radius * 0.88f));
    faceCenter.y += player.radius * 0.12f;
    Vector3 leftEye = Vector3Add(faceCenter, Vector3Scale(right, -player.radius * 0.28f));
    Vector3 rightEye = Vector3Add(faceCenter, Vector3Scale(right, player.radius * 0.28f));
    DrawSphere(leftEye, player.radius * 0.11f, WHITE);
    DrawSphere(rightEye, player.radius * 0.11f, WHITE);
    DrawSphere(Vector3Add(leftEye, Vector3Scale(forward, player.radius * 0.06f)), player.radius * 0.045f, BLACK);
    DrawSphere(Vector3Add(rightEye, Vector3Scale(forward, player.radius * 0.06f)), player.radius * 0.045f, BLACK);

    // Tiny builder boots give the ball a readable connection to the ground.
    Vector3 footBase = {body.x, body.y - player.radius * 0.82f, body.z};
    DrawSphere(Vector3Add(footBase, Vector3Scale(right, -player.radius * 0.30f)), player.radius * 0.22f, Color{40, 55, 75, 255});
    DrawSphere(Vector3Add(footBase, Vector3Scale(right,  player.radius * 0.30f)), player.radius * 0.22f, Color{40, 55, 75, 255});
}
