package com.toyzbuilder.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sky Islands as a separate vertical zone (same pattern as Depths).
 * Surface launch pads (cloud/quartz pillars) warp up; island pads return down.
 */
public final class SkyIslandsFeatures {

    public static final float SKY_Y = 160f;
    public static final float SKY_X = 0f;
    public static final float SKY_Z = 200f; // offset so Depths and Sky don't overlap in XZ
    public static final float ARENA = 70f;

    public static final class Pad {
        public final float x, z, surfaceY;
        public final float skyX, skyY, skyZ;
        public Pad(float x, float z, float surfaceY, float skyX, float skyY, float skyZ) {
            this.x = x; this.z = z; this.surfaceY = surfaceY;
            this.skyX = skyX; this.skyY = skyY; this.skyZ = skyZ;
        }
    }

    private final List<Pad> pads = new ArrayList<>();
    private final List<StructureGenerator.Part> parts = new ArrayList<>();
    private final List<Warp> warps = new ArrayList<>();
    private boolean enabled;

    public List<Pad> getPads() { return pads; }
    public List<StructureGenerator.Part> getParts() { return parts; }
    public List<Warp> getWarps() { return warps; }
    public boolean isEnabled() { return enabled; }

    public void generate(WorldGenerator.Result terrain, int seed, boolean enabled) {
        cleanup();
        this.enabled = enabled;
        if (terrain == null || !enabled) {
            System.out.println("[SkyIslands] disabled");
            return;
        }

        Random rng = new Random(seed ^ 0x534B5949L); // "SKYI"
        placeSurfacePads(terrain, rng);
        buildIslandArena(rng);
        linkWarps();

        System.out.println("[SkyIslands] pads=" + pads.size()
                + " parts=" + parts.size()
                + " warps=" + warps.size());
    }

    public void cleanup() {
        pads.clear();
        parts.clear();
        warps.clear();
        enabled = false;
    }

    private void placeSurfacePads(WorldGenerator.Result terrain, Random rng) {
        float half = terrain.size * 0.5f;
        float water = terrain.settings.waterLevel;
        int want = 3 + rng.nextInt(2);
        int tries = 0;
        while (pads.size() < want && tries < 80) {
            tries++;
            float x = -half + 40 + rng.nextFloat() * (2 * half - 80);
            float z = -half + 40 + rng.nextFloat() * (2 * half - 80);
            float y = WorldGenerator.sampleHeight(terrain, x, z);
            if (y < water + 6f) continue;
            WorldGenerator.Biome b = WorldGenerator.sampleBiome(terrain, x, z);
            if (b == WorldGenerator.Biome.OCEAN || b == WorldGenerator.Biome.CHASM) continue;
            boolean near = false;
            for (Pad p : pads) {
                float dx = p.x - x, dz = p.z - z;
                if (dx * dx + dz * dz < 45f * 45f) { near = true; break; }
            }
            if (near) continue;

            float ang = rng.nextFloat() * (float) Math.PI * 2f;
            float dist = 10f + rng.nextFloat() * 20f;
            float sx = SKY_X + (float) Math.cos(ang) * dist;
            float sz = SKY_Z + (float) Math.sin(ang) * dist;

            pads.add(new Pad(x, z, y, sx, SKY_Y + 1.5f, sz));
            // Surface launch pillar (visible marker)
            buildLaunchPillar(x, y, z);
        }
    }

    private void buildLaunchPillar(float x, float y, float z) {
        // White/gold sky pillar on surface
        part(x, y + 3.5f, z, 1.2f, 7f, 1.2f, 0, 0.9f, 0.92f, 1f, true, "QUARTZ");
        part(x, y + 0.6f, z, 2.4f, 1.2f, 2.4f, 0, 0.85f, 0.88f, 0.95f, true, "QUARTZ");
        deco(x, y + 8f, z, 0.5f, 6f, 0.5f, 0, 0.95f, 0.95f, 0.7f);
        deco(x, y + 0.25f, z, 3f, 0.12f, 3f, 0, 0.8f, 0.9f, 1f);
    }

    private void buildIslandArena(Random rng) {
        float y = SKY_Y;
        float half = ARENA * 0.5f;
        // Main floating island top
        part(SKY_X, y, SKY_Z, ARENA, 2.5f, ARENA, 0, 0.45f, 0.55f, 0.35f, true, "GRASS");
        // Underside rock
        part(SKY_X, y - 4f, SKY_Z, ARENA * 0.9f, 6f, ARENA * 0.9f, 0, 0.4f, 0.38f, 0.35f, true, "ROCK");
        // Tapered underside
        part(SKY_X, y - 9f, SKY_Z, ARENA * 0.55f, 5f, ARENA * 0.55f, 0, 0.35f, 0.32f, 0.3f, true, "ROCK");
        part(SKY_X, y - 13f, SKY_Z, ARENA * 0.3f, 4f, ARENA * 0.3f, 0, 0.3f, 0.28f, 0.26f, true, "ROCK");

        // Edge lip
        part(SKY_X, y + 1.5f, SKY_Z - half, ARENA, 1.2f, 1.5f, 0, 0.5f, 0.48f, 0.42f, true, "STONE");
        part(SKY_X, y + 1.5f, SKY_Z + half, ARENA, 1.2f, 1.5f, 0, 0.5f, 0.48f, 0.42f, true, "STONE");
        part(SKY_X - half, y + 1.5f, SKY_Z, 1.5f, 1.2f, ARENA, 0, 0.5f, 0.48f, 0.42f, true, "STONE");
        part(SKY_X + half, y + 1.5f, SKY_Z, 1.5f, 1.2f, ARENA, 0, 0.5f, 0.48f, 0.42f, true, "STONE");

        // Small satellite islands
        for (int i = 0; i < 5; i++) {
            float ang = i * (float) Math.PI * 2f / 5f + rng.nextFloat() * 0.3f;
            float dist = half + 12f + rng.nextFloat() * 10f;
            float ix = SKY_X + (float) Math.cos(ang) * dist;
            float iz = SKY_Z + (float) Math.sin(ang) * dist;
            float size = 8f + rng.nextFloat() * 6f;
            part(ix, y - 2f + rng.nextFloat() * 4f, iz, size, 2f, size, 0,
                    0.42f, 0.52f, 0.32f, true, "GRASS");
            part(ix, y - 5f, iz, size * 0.7f, 4f, size * 0.7f, 0,
                    0.35f, 0.32f, 0.3f, true, "ROCK");
        }

        // Return pillars on main island (one per pad)
        for (Pad p : pads) {
            part(p.skyX, y + 4f, p.skyZ, 1.3f, 8f, 1.3f, 0, 0.9f, 0.95f, 1f, true, "QUARTZ");
            deco(p.skyX, y + 10f, p.skyZ, 0.5f, 8f, 0.5f, 0, 0.95f, 0.9f, 0.6f);
            deco(p.skyX, y + 0.4f, p.skyZ, 3f, 0.12f, 3f, 0, 0.7f, 0.9f, 1f);
        }
        if (pads.isEmpty()) {
            part(SKY_X, y + 4f, SKY_Z, 1.3f, 8f, 1.3f, 0, 0.9f, 0.95f, 1f, true, "QUARTZ");
        }
    }

    private void linkWarps() {
        for (Pad p : pads) {
            // Surface → Sky
            warps.add(new Warp(p.x, p.surfaceY + 1f, p.z, 2.6f,
                    p.skyX, p.skyY, p.skyZ,
                    "Ascend to Sky Islands", 0.7f, 0.9f, 1f, 3.5f));
            // Sky → Surface
            warps.add(new Warp(p.skyX, p.skyY, p.skyZ, 2.8f,
                    p.x, p.surfaceY + 2.5f, p.z,
                    "Return to Surface", 0.95f, 0.85f, 0.4f, 3.5f));
        }
    }

    private void part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b, boolean solid, String tex) {
        parts.add(new StructureGenerator.Part(x, y, z, sx, sy, sz, yaw, r, g, b, solid, tex, false));
    }

    private void deco(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b) {
        parts.add(new StructureGenerator.Part(x, y, z, sx, sy, sz, yaw, r, g, b, false, null, false));
    }
}
