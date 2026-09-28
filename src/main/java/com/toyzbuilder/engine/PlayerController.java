package com.toyzbuilder.engine;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class PlayerController {

    private final FirstPersonCamera camera;
    private Collision collision;
    private double lastMouseX = Double.NaN, lastMouseY;
    private final Vector3f playerPos = new Vector3f(0, 10, 0);
    private float eyeHeight = 1.7f;
    private float bodyWidth = 0.6f;
    private float bodyHeight = 1.8f;
    private float moveSpeed = 12f;
    private float mouseSens = 0.08f;
    private boolean thirdPerson = false;
    private float camDistance = 6.0f;
    private boolean flying = false;
    private boolean editorMode = false;
    private int prevCKey = GLFW.GLFW_RELEASE;
    private int prevF5Key = GLFW.GLFW_RELEASE;
    private boolean sneaking = false;
    private float velocityY = 0f;
    private boolean grounded = false;
    private static final float GRAVITY = -28f;
    private static final float JUMP_V = 9.5f;

    public PlayerController(FirstPersonCamera camera) {
        this.camera = camera;
    }

    public void setCollision(Collision collision) {
        this.collision = collision;
    }

    public void setEditorMode(boolean on) {
        this.editorMode = on;
    }

    public void setPlayerPos(float x, float y, float z) {
        playerPos.set(x, y, z);
        velocityY = 0f;
        placeCamera();
    }

    public Vector3f getPlayerPos() { return playerPos; }
    public boolean isThirdPerson() { return thirdPerson; }
    public boolean isFlying() { return flying; }
    public boolean isGrounded() { return grounded; }
    public float getEyeHeight() { return eyeHeight; }
    public float getMouseSens() { return mouseSens; }
    public void setMouseSens(float s) { mouseSens = s; }
    public float getMoveSpeed() { return moveSpeed; }
    public void setMoveSpeed(float s) { moveSpeed = s; }

    public void resyncMouse() {
        lastMouseX = Double.NaN;
        lastMouseY = 0;
    }

    public void resyncMouseIfNeeded() {
        resyncMouse();
    }

    public void handleMouse(long window) {
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        if (Double.isNaN(lastMouseX)) {
            lastMouseX = mx[0];
            lastMouseY = my[0];
            return;
        }
        float dx = (float) (mx[0] - lastMouseX);
        float dy = (float) (my[0] - lastMouseY);
        lastMouseX = mx[0];
        lastMouseY = my[0];
        camera.addYawPitch(dx * mouseSens, -dy * mouseSens);
    }

    public void handleMovement(long window, float dt) {
        int cNow = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C);
        if (cNow == GLFW.GLFW_PRESS && prevCKey == GLFW.GLFW_RELEASE) {
            thirdPerson = !thirdPerson;
        }
        prevCKey = cNow;
        int f5 = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F5);
        if (f5 == GLFW.GLFW_PRESS && prevF5Key == GLFW.GLFW_RELEASE) {
            thirdPerson = !thirdPerson;
        }
        prevF5Key = f5;

        Vector3f front = camera.getFront();
        Vector3f flat = new Vector3f(front.x, 0, front.z);
        if (flat.lengthSquared() > 1e-6f) flat.normalize();
        Vector3f right = new Vector3f(flat).cross(0, 1, 0).normalize();

        flying = editorMode || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F) == GLFW.GLFW_PRESS;

        float speed = moveSpeed;
        // Minecraft-style: Ctrl sprint, Shift sneak (walk only), Shift descends while flying
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
        sneaking = !flying && shift && !ctrl;
        if (flying) {
            // creative fly uses base speed; boost with Ctrl
            if (ctrl) speed *= 2.2f;
        } else if (ctrl) {
            speed *= 2.2f;
            sneaking = false;
        } else if (sneaking) {
            speed *= 0.35f;
        }

        Vector3f wish = new Vector3f();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) wish.add(flat);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) wish.sub(flat);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) wish.add(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) wish.sub(right);

        if (flying) {
            velocityY = 0f;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) wish.y += 1;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS) wish.y -= 1;
            if (wish.lengthSquared() > 1e-6f) {
                wish.normalize().mul(speed * dt);
                playerPos.add(wish);
            }
            grounded = false;
        } else {
            float dx = 0, dz = 0;
            if (wish.lengthSquared() > 1e-6f) {
                wish.normalize().mul(speed * dt);
                dx = wish.x;
                dz = wish.z;
            }

            // Jump
            if (grounded && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
                velocityY = JUMP_V;
                grounded = false;
            }

            velocityY += GRAVITY * dt;
            float dy = velocityY * dt;

            if (collision != null) {
                Collision.AABB body = playerBody();
                grounded = collision.moveBody(body, dx, dy, dz, 0.45f);
                playerPos.x = (body.minX + body.maxX) * 0.5f;
                playerPos.y = body.minY;
                playerPos.z = (body.minZ + body.maxZ) * 0.5f;
                if (grounded && velocityY < 0f) velocityY = 0f;
                // ceiling damp
                if (!grounded && velocityY > 0f) {
                    Collision.AABB probe = playerBody();
                    probe.minY += 0.05f;
                    probe.maxY += 0.05f;
                    // if still intersecting after move, velocity already handled inside moveBody
                }
            } else {
                playerPos.x += dx;
                playerPos.z += dz;
                playerPos.y += dy;
            }
        }

        placeCamera();
    }

    private Collision.AABB playerBody() {
        float hx = bodyWidth * 0.5f;
        return new Collision.AABB(
                playerPos.x - hx, playerPos.x + hx,
                playerPos.y, playerPos.y + bodyHeight,
                playerPos.z - hx, playerPos.z + hx);
    }

    private void placeCamera() {
        if (!thirdPerson || editorMode) {
            camera.setPosition(playerPos.x, playerPos.y + eyeHeight, playerPos.z);
            return;
        }
        Vector3f front = camera.getFront();
        float targetY = playerPos.y + eyeHeight;
        float cx = playerPos.x - front.x * camDistance;
        float cy = targetY - front.y * camDistance;
        float cz = playerPos.z - front.z * camDistance;

        if (collision != null) {
            float minY = collision.groundHeight(cx, cz) + 0.4f;
            if (cy < minY) cy = minY;
        }
        camera.setPosition(cx, cy, cz);
    }
}
