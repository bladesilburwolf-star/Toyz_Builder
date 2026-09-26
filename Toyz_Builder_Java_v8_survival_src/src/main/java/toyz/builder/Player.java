package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Player v5 — Minecraft-Steve-style character controller.
 *
 *  - position is the FEET (bottom-center) of a proper AABB body
 *    (0.6 wide x 1.8 tall), not a floating sphere.
 *  - Full collision against every placed piece / generated structure piece,
 *    resolved axis-by-axis (X, then Z, then Y) like Minecraft.
 *  - Rotated pieces are handled by expanding their extents by the yaw
 *    rotation (OBB -> conservative AABB).
 *  - Auto step-up over half-height edges (0.6 blocks).
 *  - Gravity / jumping tuned to Minecraft feel (~1.25 block jump apex).
 *  - Sprint (double-tap W or LEFT_CTRL), crouch (LEFT_SHIFT),
 *    swimming in water bodies, and creative flight (double-tap SPACE).
 *  - Terrain height is still the floor; tree trunks still push you out.
 *
 * Hook-up in ToyzBuilder.java — replace the old update call with:
 *
 *   Player.update(player, forest, placedPieces, pieceDefs, dt,
 *                 hasController, 0, camYaw, !Survival.showCraft && !mapLoader.open);
 */
public final class Player {

    // ---- body ----
    public Vector3 position;      // feet, bottom-center of AABB
    public Vector3 velocity = Helpers.newVector3(0, 0, 0);
    public float yaw;
    public float radius = 0.55f;  // kept for legacy spawn code / callers
    public float width = 0.6f;    // full body width (x/z)
    public float height = 1.8f;  // full body height (y)
    public float eyeHeight = 1.62f; // camera sits here above feet
    public boolean grounded = true;

    // ---- state ----
    public boolean inWater = false;
    public boolean sprinting = false;
    public boolean crouching = false;
    public boolean flying = false;

    // ---- tuning (Minecraft-ish) ----
    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_SPEED = 5.6f;
    private static final float CROUCH_SPEED = 1.6f;
    private static final float SWIM_SPEED = 2.6f;
    private static final float FLY_SPEED = 9.5f;
    private static final float GRAVITY = 28f;
    private static final float WATER_GRAVITY = 6f;
    private static final float JUMP_VELOCITY = 8.35f;   // ~1.25 block apex
    private static final float SWIM_UP_VELOCITY = 4.0f;
    private static final float TERMINAL_VELOCITY = -55f;
    private static final float STEP_HEIGHT = 0.6f;
    private static final float EPSILON = 0.001f;

    // double-tap timers
    private float lastForwardTap = -1f;
    private float lastSpaceTap = -1f;

    private Player() {}

    // =====================================================================
    // Init
    // =====================================================================

    public static Player init(Terrain.ForestTerrain forest) {
        Player p = new Player();
        p.position = Helpers.newVector3(0, 0, 0);
        p.position.y(Terrain.getTerrainHeight(forest, 0, 0));
        p.velocity.x(0).y(0).z(0);
        p.yaw = 0;
        p.grounded = true;
        return p;
    }

    /** Convenience: drop feet onto ground at a given x/z. */
    public void placeOnGround(Terrain.ForestTerrain forest, float x, float z) {
        position.x(x).z(z);
        position.y(Terrain.getTerrainHeight(forest, x, z));
        velocity.y(0);
    }

    // =====================================================================
    // Update — legacy signature (no piece collision). Use the piece-aware
    // overload below for real physics.
    // =====================================================================

    public static void update(Player player, Terrain.ForestTerrain forest, float dt,
                              boolean hasController, int gamepadId, float cameraYaw,
                              boolean allowJump) {
        update(player, forest, null, null, dt, hasController, gamepadId, cameraYaw, allowJump);
    }

    // =====================================================================
    // Update — full physics against terrain + placed pieces/structures
    // =====================================================================

    public static void update(Player player, Terrain.ForestTerrain forest,
                              List<Piece.PlacedPiece> pieces, List<Piece.PieceDef> defs,
                              float dt, boolean hasController, int gamepadId,
                              float cameraYaw, boolean allowJump) {
        float now = (float) GetTime();

        // ---------- input ----------
        float mx = (IsKeyDown(KEY_D) ? 1f : 0f) - (IsKeyDown(KEY_A) ? 1f : 0f);
        float my = (IsKeyDown(KEY_W) ? 1f : 0f) - (IsKeyDown(KEY_S) ? 1f : 0f);

        if (hasController) {
            mx += applyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_X), 0.15f);
            my -= applyDeadzone(GetGamepadAxisMovement(gamepadId, GAMEPAD_AXIS_LEFT_Y), 0.15f);
        }
        float mlen = (float) Math.sqrt(mx * mx + my * my);
        if (mlen > 1f) { mx /= mlen; my /= mlen; }
        boolean moving = mlen > 0.01f;

        // sprint: hold ctrl, or double-tap forward
        boolean wantSprint = IsKeyDown(KEY_LEFT_CONTROL) || IsKeyDown(KEY_RIGHT_CONTROL)
                || (hasController && IsGamepadButtonDown(gamepadId, GAMEPAD_BUTTON_LEFT_TRIGGER_1));
        if (my > 0.5f && IsKeyPressed(KEY_W)) {
            if (player.lastForwardTap > 0 && now - player.lastForwardTap < 0.3f) player.sprinting = true;
            player.lastForwardTap = now;
        }
        if (wantSprint && moving) player.sprinting = true;
        if (!moving || my <= 0.1f || IsKeyDown(KEY_S)) player.sprinting = false;

        player.crouching = IsKeyDown(KEY_LEFT_SHIFT);
        if (player.crouching) player.sprinting = false;

        // fly toggle: double-tap space
        boolean jumpPressed = IsKeyPressed(KEY_SPACE)
            || (hasController && IsGamepadButtonPressed(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN));
        if (jumpPressed) {
            if (player.lastSpaceTap > 0 && now - player.lastSpaceTap < 0.3f) {
                player.flying = !player.flying;
                player.velocity.y(0);
            }
            player.lastSpaceTap = now;
        }
        if (player.flying && (player.crouching || player.inWater)) player.flying = false;

        // ---------- water check ----------
        player.inWater = isInWater(player, forest);

        // ---------- horizontal wish velocity ----------
        double yaw = Math.toRadians(cameraYaw);
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = (float) Math.cos(yaw), rz = -(float) Math.sin(yaw);
        float wx = rx * mx + fx * my;
        float wz = rz * mx + fz * my;

        float speed = WALK_SPEED;
        if (player.sprinting) speed = SPRINT_SPEED;
        if (player.crouching) speed = CROUCH_SPEED;
        if (player.inWater) speed = SWIM_SPEED;
        if (player.flying) speed = FLY_SPEED;

        if (moving) {
            float len = (float) Math.sqrt(wx * wx + wz * wz);
            wx = wx / len * speed;
            wz = wz / len * speed;
            player.yaw = (float) Math.toDegrees(Math.atan2(wx, wz));
        } else {
            wx = 0; wz = 0;
        }

        // ---------- vertical ----------
        if (player.flying) {
            float vy = 0;
            if (IsKeyDown(KEY_SPACE)) vy += FLY_SPEED * 0.8f;
            if (IsKeyDown(KEY_LEFT_SHIFT)) vy -= FLY_SPEED * 0.8f;
            if (hasController) {
                if (IsGamepadButtonDown(gamepadId, GAMEPAD_BUTTON_RIGHT_TRIGGER_2)) vy += FLY_SPEED * 0.8f;
                if (IsGamepadButtonDown(gamepadId, GAMEPAD_BUTTON_LEFT_TRIGGER_2)) vy -= FLY_SPEED * 0.8f;
            }
            player.velocity.y(vy);
        } else if (player.inWater) {
            player.velocity.y(player.velocity.y() - WATER_GRAVITY * dt);
            if (player.velocity.y() < -4f) player.velocity.y(-4f);
            if (allowJump && (IsKeyDown(KEY_SPACE) ||
                    (hasController && IsGamepadButtonDown(gamepadId, GAMEPAD_BUTTON_RIGHT_FACE_DOWN)))) {
                player.velocity.y(SWIM_UP_VELOCITY);
            }
        } else {
            player.velocity.y(player.velocity.y() - GRAVITY * dt);
            if (player.velocity.y() < TERMINAL_VELOCITY) player.velocity.y(TERMINAL_VELOCITY);
            if (allowJump && jumpPressed && player.grounded) {
                player.velocity.y(JUMP_VELOCITY);
                player.grounded = false;
            }
        }

        // ---------- collision boxes from placed pieces ----------
        List<CollisionBox> boxes = new ArrayList<>();
        if (pieces != null && defs != null) {
            for (Piece.PlacedPiece p : pieces) {
                if (p == null) continue;
                Piece.PieceDef def = Piece.findDef(defs, p.type, p.color);
                if (def == null) {
                    for (Piece.PieceDef d : defs) { if (d.type == p.type) { def = d; break; } }
                }
                if (def == null) continue;
                boxes.add(boxFromPiece(p, def));
            }
        }

        float halfW = player.width * 0.5f;
        float bodyH = player.crouching ? player.height * 0.65f : player.height;

        // Store horizontal velocity so collision + fall damage read it too
        player.velocity.x(wx);
        player.velocity.z(wz);

        // ---------- integrate + resolve, axis by axis ----------
        player.grounded = false;

        // X
        moveAxisX(player, boxes, halfW, bodyH, player.velocity.x() * dt);
        // Z
        moveAxisZ(player, boxes, halfW, bodyH, player.velocity.z() * dt);
        // Y (gravity / landing / head bump)
        moveAxisY(player, boxes, halfW, bodyH, player.velocity.y() * dt);

        // Terrain is still the ultimate floor.
        float terrainY = Terrain.getTerrainHeight(forest, player.position.x(), player.position.z());
        if (player.position.y() <= terrainY) {
            player.position.y(terrainY);
            if (player.velocity.y() < 0) player.velocity.y(0);
            player.grounded = true;
        }
        // Safety net if terrain drops faster than one frame (slopes).
        float corrected = Terrain.getTerrainHeight(forest, player.position.x(), player.position.z());
        if (player.position.y() < corrected) player.position.y(corrected);

        // Trees remain solid — slide around trunks.
        Terrain.resolveTreeCollision(forest, player.position, player.radius);
        corrected = Terrain.getTerrainHeight(forest, player.position.x(), player.position.z());
        if (player.position.y() < corrected) {
            player.position.y(corrected);
            player.grounded = true;
            if (player.velocity.y() < 0) player.velocity.y(0);
        }

        // World bounds
        float half = forest.size * 0.5f - 1.5f;
        player.position.x(Terrain.clamp(player.position.x(), -half, half))
                    .z(Terrain.clamp(player.position.z(), -half, half));
    }

    // =====================================================================
    // Collision core
    // =====================================================================

    private static final class CollisionBox {
        float minX, maxX, minY, maxY, minZ, maxZ;
    }

    /**
     * Conservative AABB for a placed piece. Yaw rotation can grow the box
     * slightly (OBB projection), which is fine for a builder game and far
     * cheaper/more robust than true OBB tests.
     */
    private static CollisionBox boxFromPiece(Piece.PlacedPiece p, Piece.PieceDef def) {
        float hx = def.halfExtents.x(), hy = def.halfExtents.y(), hz = def.halfExtents.z();
        if (p.rotationX != 0f || p.rotationZ != 0f) {
            // tilted pieces (ramps, teeters): pad a bit so they stay solid
            float m = Math.max(hx, Math.max(hy, hz));
            hx = m * 0.85f; hy = m * 0.85f; hz = m * 0.85f;
        } else {
            double ry = Math.toRadians(p.rotationY);
            float c = (float) Math.abs(Math.cos(ry)), s = (float) Math.abs(Math.sin(ry));
            float ex = hx * c + hz * s;
            float ez = hx * s + hz * c;
            hx = ex; hz = ez;
        }
        CollisionBox b = new CollisionBox();
        b.minX = p.position.x() - hx; b.maxX = p.position.x() + hx;
        b.minY = p.position.y() - hy; b.maxY = p.position.y() + hy;
        b.minZ = p.position.z() - hz; b.maxZ = p.position.z() + hz;
        return b;
    }

    private static boolean overlaps(CollisionBox b, float px, float py, float pz,
                                    float halfW, float bodyH) {
        return px + halfW > b.minX + EPSILON && px - halfW < b.maxX - EPSILON
            && py + bodyH > b.minY + EPSILON && py < b.maxY - EPSILON
            && pz + halfW > b.minZ + EPSILON && pz - halfW < b.maxZ - EPSILON;
    }

    private static void moveAxisX(Player player, List<CollisionBox> boxes,
                                 float halfW, float bodyH, float dx) {
        if (dx == 0) return;
        float px = player.position.x();
        player.position.x(px + dx);
        for (CollisionBox b : boxes) {
            if (!overlaps(b, player.position.x(), player.position.y(), player.position.z(), halfW, bodyH))
                continue;
            // blocked — try step-up (walk onto low ledges like Steve)
            if (stepUp(player, boxes, halfW, bodyH, b.maxY)) return;
            // no step: clamp against the wall
            if (dx > 0) player.position.x(b.minX - halfW - EPSILON);
            else        player.position.x(b.maxX + halfW + EPSILON);
            player.velocity.x(0);
        }
    }

    private static void moveAxisZ(Player player, List<CollisionBox> boxes,
                                 float halfW, float bodyH, float dz) {
        if (dz == 0) return;
        float pz = player.position.z();
        player.position.z(pz + dz);
        for (CollisionBox b : boxes) {
            if (!overlaps(b, player.position.x(), player.position.y(), player.position.z(), halfW, bodyH))
                continue;
            if (stepUp(player, boxes, halfW, bodyH, b.maxY)) return;
            if (dz > 0) player.position.z(b.minZ - halfW - EPSILON);
            else        player.position.z(b.maxZ + halfW + EPSILON);
            player.velocity.z(0);
        }
    }

    private static void moveAxisY(Player player, List<CollisionBox> boxes,
                                  float halfW, float bodyH, float dy) {
        if (dy == 0) return;
        float py = player.position.y();
        player.position.y(py + dy);
        for (CollisionBox b : boxes) {
            if (!overlaps(b, player.position.x(), player.position.y(), player.position.z(), halfW, bodyH))
                continue;
            if (dy > 0) { // head bump
                player.position.y(b.minY - bodyH - EPSILON);
            } else {      // landed on top of piece
                player.position.y(b.maxY + EPSILON);
                player.grounded = true;
            }
            player.velocity.y(0);
        }
    }

    /**
     * Try to lift the player smoothly onto a ledge (max STEP_HEIGHT).
     * Returns true if the step succeeded (caller stops resolving this box).
     */
    private static boolean stepUp(Player player, List<CollisionBox> boxes,
                                  float halfW, float bodyH, float ledgeY) {
        float rise = ledgeY - player.position.y();
        if (rise <= 0f || rise > STEP_HEIGHT) return false;
        float candidateY = ledgeY + EPSILON;
        // would the body fit standing on the ledge?
        for (CollisionBox o : boxes) {
            if (overlaps(o, player.position.x(), candidateY, player.position.z(), halfW, bodyH))
                return false;
        }
        player.position.y(candidateY);
        player.grounded = true;
        return true;
    }

    // =====================================================================
    // Water
    // =====================================================================

    private static boolean isInWater(Player player, Terrain.ForestTerrain forest) {
        float px = player.position.x(), py = player.position.y(), pz = player.position.z();
        if (py < forest.waterLevel + 0.2f) return true; // below ocean/river surface
        if (forest.waterBodies == null) return false;
        for (Terrain.WaterBody w : forest.waterBodies) {
            if (w == null) continue;
            float dx = px - w.position.x(), dz = pz - w.position.z();
            if (dx * dx / (w.radiusX * w.radiusX) + dz * dz / (w.radiusZ * w.radiusZ) > 1f)
                continue;
            if (py + player.height * 0.5f > w.position.y() + 0.4f) continue; // too high above surface
            return true;
        }
        return false;
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    public static void draw(Player player) {
        Vector3 body = Vector3Add(player.position,
                Helpers.newVector3(0, player.height * 0.5f, 0));

        if (AssetBank.modelVRMan != null && AssetBank.modelVRMan.meshCount() > 0) {
            AssetBank.drawModel(AssetBank.modelVRMan, body, player.yaw,
                    AssetBank.VRMAN_SCALE, WHITE);
            return;
        }

        // Fallback Steve-ish blocky body
        float h = player.height;
        DrawCube(body, player.width * 0.9f, h * 0.6f, 0.28f, Helpers.newColor(0, 160, 200, 255)); // torso+legs
        // Head
        Vector3 head = Vector3Add(player.position, Helpers.newVector3(0, h * 0.86f, 0));
        DrawCube(head, 0.45f, 0.45f, 0.45f, Helpers.newColor(233, 190, 150, 255));
        DrawCubeWires(head, 0.45f, 0.45f, 0.45f, Helpers.newColor(25, 65, 115, 255));
        // Arms
        float yawRad = (float) Math.toRadians(player.yaw);
        float rx = (float) Math.cos(yawRad), rz = -(float) Math.sin(yawRad);
        Vector3 armL = Vector3Add(body, Vector3Scale(Helpers.newVector3(rx, 0, rz), -0.34f));
        Vector3 armR = Vector3Add(body, Vector3Scale(Helpers.newVector3(rx, 0, rz), 0.34f));
        DrawCube(armL, 0.16f, h * 0.42f, 0.16f, Helpers.newColor(52, 132, 220, 255));
        DrawCube(armR, 0.16f, h * 0.42f, 0.16f, Helpers.newColor(52, 132, 220, 255));
        // Eyes
        float fx = (float) Math.sin(yawRad), fz = (float) Math.cos(yawRad);
        Vector3 face = Vector3Add(head, Vector3Scale(Helpers.newVector3(fx, 0, fz), 0.23f));
        Vector3 eyeL = Vector3Add(face, Vector3Scale(Helpers.newVector3(rx, 0, rz), -0.09f));
        Vector3 eyeR = Vector3Add(face, Vector3Scale(Helpers.newVector3(rx, 0, rz), 0.09f));
        DrawCube(eyeL, 0.08f, 0.08f, 0.04f, WHITE);
        DrawCube(eyeR, 0.08f, 0.08f, 0.04f, WHITE);
    }

    // =====================================================================
    // Misc
    // =====================================================================

    private static float applyDeadzone(float v, float deadzone) {
        if (Math.abs(v) <= deadzone) return 0f;
        float sign = v < 0 ? -1f : 1f;
        return sign * (Math.abs(v) - deadzone) / (1f - deadzone);
    }

    /** Eye position (camera anchor) — feet + eyeHeight. */
    public Vector3 eyePosition() {
        return Vector3Add(position, Helpers.newVector3(0, eyeHeight, 0));
    }
}