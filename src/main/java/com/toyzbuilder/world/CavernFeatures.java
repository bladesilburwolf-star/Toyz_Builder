package com.toyzbuilder.world;

import com.toyzbuilder.engine.Mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zelda ToTK style Depths linked from surface chasms.
 * Chasm pits are carved by WorldGenerator; this builds the underground arena,
 * solid collision, and warps (pit → Depths, quartz pillar → surface).
 */
public final class CavernFeatures {

    public static final float DEPTHS_Y = -140f;
    public static final float DEPTHS_X = 0f;
    public static final float DEPTHS_Z = 0f;
    public static final float ARENA = 90f;

    /** Empty — tube meshes removed. Kept for Renderer.renderCaverns. */
    public static final class Cavern {
        public final float x, y, z, length, radius, yaw;
        public final Mesh mesh;
        public Cavern(float x, float y, float z, float length, float radius, float yaw, Mesh mesh) {
            this.x = x; this.y = y; this.z = z;
            this.length = length; this.radius = radius; this.yaw = yaw; this.mesh = mesh;
        }
    }

    public static final class Chasm {
        public final float x, z, surfaceY, pitFloorY, radius;
        public final float depthsX, depthsY, depthsZ;
        public Chasm(float x, float z, float surfaceY, float pitFloorY, float radius,
                     float depthsX, float depthsY, float depthsZ) {
            this.x = x; this.z = z;
            this.surfaceY = surfaceY; this.pitFloorY = pitFloorY; this.radius = radius;
            this.depthsX = depthsX; this.depthsY = depthsY; this.depthsZ = depthsZ;
        }
    }

    private final List<Cavern> caverns = new ArrayList<>();
    private final List<Chasm> chasms = new ArrayList<>();
    private final List<StructureGenerator.Part> parts = new ArrayList<>();
    private final List<Warp> warps = new ArrayList<>();

    public List<Cavern> getCaverns() { return caverns; }
    public List<Chasm> getChasms() { return chasms; }
    public List<StructureGenerator.Part> getParts() { return parts; }
    public List<Warp> getWarps() { return warps; }

    public void generate(WorldGenerator.Result terrain, int seed) {
        cleanup();
        if (terrain == null) return;

        Random rng = new Random(seed ^ 0x44455054L);
        // Prefer explicit sites from terrain carve
        if (terrain.chasms != null && !terrain.chasms.isEmpty()) {
            for (WorldGenerator.ChasmSite s : terrain.chasms) {
                float ang = rng.nextFloat() * (float) Math.PI * 2f;
                float dist = 12f + rng.nextFloat() * 25f;
                float dx = DEPTHS_X + (float) Math.cos(ang) * dist;
                float dz = DEPTHS_Z + (float) Math.sin(ang) * dist;
                chasms.add(new Chasm(s.x, s.z, s.rimY, s.floorY, s.radius,
                        dx, DEPTHS_Y + 1.2f, dz));
            }
        } else {
            detectChasmsFromBiome(terrain, rng);
        }
        buildDepthsArena(rng);
        linkWarps();

        System.out.println("[Depths] chasms=" + chasms.size()
                + " parts=" + parts.size()
                + " warps=" + warps.size());
    }

    public void cleanup() {
        for (Cavern c : caverns) {
            if (c.mesh != null) c.mesh.cleanup();
        }
        caverns.clear();
        chasms.clear();
        parts.clear();
        warps.clear();
    }

    private void detectChasmsFromBiome(WorldGenerator.Result terrain, Random rng) {
        int n = terrain.settings.resolution;
        float sp = terrain.settings.spacing;
        float half = terrain.size * 0.5f;
        boolean[] visited = new boolean[n * n];
        for (int iz = 1; iz < n - 1; iz++) {
            for (int ix = 1; ix < n - 1; ix++) {
                int i = iz * n + ix;
                if (visited[i] || terrain.biomes[i] != WorldGenerator.Biome.CHASM) continue;
                float sx = 0, sz = 0; int count = 0;
                float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
                ArrayList<int[]> stack = new ArrayList<>();
                stack.add(new int[]{ix, iz});
                visited[i] = true;
                while (!stack.isEmpty()) {
                    int[] c = stack.remove(stack.size() - 1);
                    int ci = c[1] * n + c[0];
                    float wx = c[0] * sp - half, wz = c[1] * sp - half;
                    float h = terrain.heights[ci];
                    sx += wx; sz += wz; count++;
                    minH = Math.min(minH, h); maxH = Math.max(maxH, h);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], nz = c[1] + d[1];
                        if (nx < 0 || nz < 0 || nx >= n || nz >= n) continue;
                        int ni = nz * n + nx;
                        if (visited[ni] || terrain.biomes[ni] != WorldGenerator.Biome.CHASM) continue;
                        visited[ni] = true;
                        stack.add(new int[]{nx, nz});
                    }
                }
                if (count < 6) continue;
                float cx = sx / count, cz = sz / count;
                float radius = Math.min(16f, 6f + (float) Math.sqrt(count) * 0.4f);
                float ang = rng.nextFloat() * (float) Math.PI * 2f;
                float dist = 12f + rng.nextFloat() * 25f;
                chasms.add(new Chasm(cx, cz, maxH, minH, radius,
                        DEPTHS_X + (float) Math.cos(ang) * dist,
                        DEPTHS_Y + 1.2f,
                        DEPTHS_Z + (float) Math.sin(ang) * dist));
            }
        }
    }

    private void buildDepthsArena(Random rng) {
        float y = DEPTHS_Y;
        float half = ARENA * 0.5f;
        // Dark rock floor / ceiling / walls — distinct from surface biomes
        part(DEPTHS_X, y, DEPTHS_Z, ARENA, 2.0f, ARENA, 0, 0.08f, 0.04f, 0.10f, true, "ROCK");
        part(DEPTHS_X, y + 24f, DEPTHS_Z, ARENA + 6f, 2.0f, ARENA + 6f, 0, 0.05f, 0.02f, 0.07f, true, "ROCK");
        float wallH = 22f, wallT = 3f;
        part(DEPTHS_X, y + wallH * 0.5f, DEPTHS_Z - half, ARENA + 2, wallH, wallT, 0, 0.1f, 0.05f, 0.12f, true, "ROCK");
        part(DEPTHS_X, y + wallH * 0.5f, DEPTHS_Z + half, ARENA + 2, wallH, wallT, 0, 0.1f, 0.05f, 0.12f, true, "ROCK");
        part(DEPTHS_X - half, y + wallH * 0.5f, DEPTHS_Z, wallT, wallH, ARENA + 2, 0, 0.1f, 0.05f, 0.12f, true, "ROCK");
        part(DEPTHS_X + half, y + wallH * 0.5f, DEPTHS_Z, wallT, wallH, ARENA + 2, 0, 0.1f, 0.05f, 0.12f, true, "ROCK");
        for (int i = 0; i < 14; i++) {
            float px = DEPTHS_X + (rng.nextFloat() - 0.5f) * (ARENA - 20);
            float pz = DEPTHS_Z + (rng.nextFloat() - 0.5f) * (ARENA - 20);
            float ph = 4f + rng.nextFloat() * 10f;
            float pr = 1.2f + rng.nextFloat() * 1.8f;
            part(px, y + ph * 0.5f + 0.6f, pz, pr, ph, pr, rng.nextFloat() * 360f,
                    0.12f, 0.05f, 0.14f, true, "ROCK");
        }
        for (Chasm c : chasms) buildLightPillar(c.depthsX, y, c.depthsZ);
        if (chasms.isEmpty()) buildLightPillar(DEPTHS_X, y, DEPTHS_Z);
    }

    private void buildLightPillar(float x, float y, float z) {
        part(x, y + 5f, z, 1.4f, 10f, 1.4f, 0, 0.85f, 0.9f, 1f, true, "QUARTZ");
        part(x, y + 1.2f, z, 2.6f, 2.2f, 2.6f, 45f, 0.75f, 0.85f, 0.95f, true, "QUARTZ");
        deco(x, y + 14f, z, 0.7f, 16f, 0.7f, 0, 0.7f, 0.95f, 1f);
        deco(x, y + 0.4f, z, 3.5f, 0.15f, 3.5f, 0, 0.5f, 0.9f, 1f);
    }

    private void linkWarps() {
        for (Chasm c : chasms) {
            // Trigger once player is deep in the shaft (not at the rim)
            float deepY = c.pitFloorY + (c.surfaceY - c.pitFloorY) * 0.25f;
            float halfH = Math.max(10f, (c.surfaceY - c.pitFloorY) * 0.45f);
            warps.add(new Warp(c.x, deepY, c.z, Math.max(5f, c.radius * 0.5f),
                    c.depthsX, c.depthsY, c.depthsZ,
                    "Enter Depths", 0.55f, 0.15f, 0.7f, halfH));
            // Return beside the pit so you do not immediately fall back in
            float rx = c.x + c.radius + 4f;
            float rz = c.z;
            warps.add(new Warp(c.depthsX, c.depthsY, c.depthsZ, 2.8f,
                    rx, c.surfaceY + 2.5f, rz,
                    "Return to Surface", 0.6f, 0.9f, 1f, 3.5f));
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
