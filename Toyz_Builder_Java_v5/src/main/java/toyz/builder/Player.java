package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

/** Port of player.h / player.cpp — MCCE-style movement with gamepad support. */
public final class Player {

    public Vector3 position = Helpers.newVector3(0, 0, 0);
	public Vector3 velocity = Helpers.newVector3(0, 0, 0);
    public float yaw;
    public float radius = 0.55f;
    public float eyeHeight = 0.48f;
    public boolean grounded = true;

    private Player() {}

    public static Player init(Terrain.ForestTerrain forest) {
        Player p = new Player();
        p.position.x(0)
                 .y(Terrain.getTerrainHeight(forest, 0, 0) + p.radius)
                 .z(0);
        p.velocity.x(0).y(0).z(0);
        p.yaw = 0;
        p.grounded = true;
        return p;
    }

    private static float applyDeadzone(float v, float deadzone) {
        if (Math.abs(v) <= deadzone) return 0f;
        float sign = v < 0 ? -1f : 1f;
        return sign * (Math.abs(v) - deadzone) / (1f - deadzone);
    }

    public static void update(Player player, Terrain.ForestTerrain forest, float dt,
                              boolean hasController, int gamepadId, float cameraYaw, boolean allowJump) {
        // Keyboard and gamepad both work at once.
        float mx = (IsKeyDown(KEY_D) ? 1f : 0f) - (IsKeyDown(KEY_A) ? 1f : 0f);
        float my = (IsKeyDown(KEY_W) ? 1f : 0f) - (IsKeyDown(KEY_S) ? 1f : 0f);

        if (hasController) {
            mx += applyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_X), 0.15f);
            my += applyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_Y), 0.15f);
        }

        float mlen = (float) Math.sqrt(mx * mx + my * my);
        if (mlen > 1f) { mx /= mlen; my /= mlen; }

        double yaw = Math.toRadians(cameraYaw);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = (float) Math.cos(yaw), rz = -(float) Math.sin(yaw);
        float wx = rx * mx + fx * my;
        float wz = rz * mx + fz * my;

        final float speed = 7f;
        if (Math.sqrt(wx * wx + wz * wz) > 0.001f) {
            float len = (float) Math.sqrt(wx * wx + wz * wz);
            wx /= len; wz /= len;
            player.position.x(player.position.x() + wx * speed * dt)
                    .z(player.position.z() + wz * speed * dt);
            player.yaw = (float) Math.toDegrees(Math.atan2(wx, wz));
        }

        float ground = Terrain.getTerrainHeight(forest, player.position.x(), player.position.z()) + player.radius;
        player.velocity.y(player.velocity.y() - 22f * dt);
        player.position.y(player.position.y() + player.velocity.y() * dt);
        if (player.position.y() <= ground) {
            player.position.y(ground);
            player.velocity.y(0);
            player.grounded = true;
        } else {
            player.grounded = false;
        }

        boolean jump = IsKeyPressed(KEY_SPACE)
            || (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
        if (allowJump && jump && player.grounded) {
            player.velocity.y(8f);
            player.grounded = false;
        }

        float half = forest.size * 0.5f - 1.5f;
        player.position.x(Terrain.clamp(player.position.x(), -half, half))
                    .z(Terrain.clamp(player.position.z(), -half, half));

        // Trees are solid — resolve so the builder slides around trunks.
        Terrain.resolveTreeCollision(forest, player.position, player.radius);
        float correctedGround = Terrain.getTerrainHeight(forest, player.position.x(), player.position.z()) + player.radius;
        if (player.position.y() < correctedGround) {
            player.position.y(correctedGround);
        }
    }

    public static void draw(Player player) {
        Vector3 body = player.position;
        DrawSphere(body, player.radius, Helpers.newColor(52, 132, 220, 255));
        DrawSphereWires(body, player.radius, 12, 8, Helpers.newColor(25, 65, 115, 255));

        float yaw = (float) Math.toRadians(player.yaw);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = (float) Math.cos(yaw), rz = -(float) Math.sin(yaw);

        // Face
        Vector3 faceCenter = Vector3Add(body, Vector3Scale(Helpers.newVector3(fx, 0, fz), player.radius * 0.88f));
        faceCenter.y(faceCenter.y() + player.radius * 0.12f);
        Vector3 leftEye = Vector3Add(faceCenter, Vector3Scale(Helpers.newVector3(rx, 0, rz), -player.radius * 0.28f));
        Vector3 rightEye = Vector3Add(faceCenter, Vector3Scale(Helpers.newVector3(rx, 0, rz), player.radius * 0.28f));
        DrawSphere(leftEye, player.radius * 0.11f, WHITE);
        DrawSphere(rightEye, player.radius * 0.11f, WHITE);
        DrawSphere(Vector3Add(leftEye, Vector3Scale(Helpers.newVector3(fx, 0, fz), player.radius * 0.06f)),
                   player.radius * 0.045f, BLACK);
        DrawSphere(Vector3Add(rightEye, Vector3Scale(Helpers.newVector3(fx, 0, fz), player.radius * 0.06f)),
                   player.radius * 0.045f, BLACK);

        // Boots
        float footY = body.y() - player.radius * 0.82f;
        Vector3 footBase = Helpers.newVector3(body.x(), footY, body.z());
        DrawSphere(Vector3Add(footBase, Vector3Scale(Helpers.newVector3(rx, 0, rz), -player.radius * 0.30f)),
                   player.radius * 0.22f, Helpers.newColor(40, 55, 75, 255));
        DrawSphere(Vector3Add(footBase, Vector3Scale(Helpers.newVector3(rx, 0, rz), player.radius * 0.30f)),
                   player.radius * 0.22f, Helpers.newColor(40, 55, 75, 255));
    }
}