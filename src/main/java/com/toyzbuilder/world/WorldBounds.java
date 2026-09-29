package com.toyzbuilder.world;

/** Global logical bounds for the procedural Toyz Builder world. */
public final class WorldBounds {
    private WorldBounds() {}

    /** Minetest-scale horizontal world extent: 62 km by 62 km. */
    public static final float MINETEST_WORLD_SIZE = 62000.0f;
    public static final float HALF_WORLD = MINETEST_WORLD_SIZE * 0.5f;

    public static boolean contains(float worldSize, float x, float z) {
        float half = worldSize * 0.5f;
        return x >= -half && x <= half && z >= -half && z <= half;
    }

    public static boolean contains(float x, float z) {
        return contains(MINETEST_WORLD_SIZE, x, z);
    }

    public static float clampX(float x) {
        return Math.max(-HALF_WORLD, Math.min(HALF_WORLD, x));
    }

    public static float clampZ(float z) {
        return Math.max(-HALF_WORLD, Math.min(HALF_WORLD, z));
    }
}
