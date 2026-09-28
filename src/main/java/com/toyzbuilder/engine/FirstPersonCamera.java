package com.toyzbuilder.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class FirstPersonCamera {

    private final Vector3f position = new Vector3f(0, 10, 0);
    private float yaw = -90f;   // degrees
    private float pitch = 0f;

    public Matrix4f getViewMatrix() {
        Vector3f front = getFront();
        Vector3f center = new Vector3f(position).add(front);
        return new Matrix4f().lookAt(position, center, new Vector3f(0, 1, 0));
    }

    public Vector3f getFront() {
        float yr = (float) Math.toRadians(yaw);
        float pr = (float) Math.toRadians(pitch);
        float cosP = (float) Math.cos(pr);
        return new Vector3f(
                (float) Math.cos(yr) * cosP,
                (float) Math.sin(pr),
                (float) Math.sin(yr) * cosP
        ).normalize();
    }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public void addYawPitch(float dyaw, float dpitch) {
        yaw += dyaw;
        pitch += dpitch;
        if (pitch > 89f) pitch = 89f;
        if (pitch < -89f) pitch = -89f;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public void move(Vector3f delta) {
        position.add(delta);
    }

    public float getX() { return position.x; }
    public float getY() { return position.y; }
    public float getZ() { return position.z; }
    public Vector3f getPosition() { return position; }
}
