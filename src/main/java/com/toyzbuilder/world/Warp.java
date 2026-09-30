package com.toyzbuilder.world;

/**
 * In-world teleport pad. When the player walks inside the radius
 * (and within triggerHalfHeight vertically), they are teleported to the target.
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
    /** Vertical half-extent of trigger (default 2.5 for flat pads; tall for chasms). */
    public final float triggerHalfHeight;

    public Warp(float x, float y, float z, float radius,
                float tx, float ty, float tz,
                String label, float r, float g, float b) {
        this(x, y, z, radius, tx, ty, tz, label, r, g, b, 2.5f);
    }

    public Warp(float x, float y, float z, float radius,
                float tx, float ty, float tz,
                String label, float r, float g, float b,
                float triggerHalfHeight) {
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.tx = tx; this.ty = ty; this.tz = tz;
        this.label = label;
        this.r = r; this.g = g; this.b = b;
        this.triggerHalfHeight = triggerHalfHeight;
    }
}
