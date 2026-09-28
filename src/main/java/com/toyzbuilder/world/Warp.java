package com.toyzbuilder.world;

/**
 * In-world teleport pad. When the player walks inside the radius
 * (and within 2.5m vertically), they are teleported to the target.
 * World enforces a cooldown so pads do not ping-pong.
 */
public final class Warp {
    /** Pad center (feet level). */
    public final float x, y, z;
    /** Trigger radius on XZ. */
    public final float radius;
    /** Destination (feet level). */
    public final float tx, ty, tz;
    public final String label;
    /** Pad glow color. */
    public final float r, g, b;

    public Warp(float x, float y, float z, float radius,
                float tx, float ty, float tz,
                String label, float r, float g, float b) {
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.label = label;
        this.r = r; this.g = g; this.b = b;
    }
}
