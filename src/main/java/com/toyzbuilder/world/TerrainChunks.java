package com.toyzbuilder.world;

import com.toyzbuilder.engine.AssetBank;
import com.toyzbuilder.engine.Mesh;
import com.toyzbuilder.engine.Renderer;

/**
 * Minetest-style heightfield chunks: split the mainland mesh into
 * CELL×CELL patches and only draw those near the camera.
 * Full heightfield stays in WorldGenerator.Result for collision sampling.
 */
public final class TerrainChunks {

    /** Quads per chunk side (verts = CELL+1). */
    public static final int CELL = 16;

    private Mesh[] meshes = new Mesh[0];
    private float[] cx = new float[0];
    private float[] cy = new float[0];
    private float[] cz = new float[0];
    private float[] rad = new float[0];
    private int count;
    private int totalTris;
    private int drawnLastFrame;

    public int chunkCount() { return count; }
    public int totalTris() { return totalTris; }
    public int drawnLastFrame() { return drawnLastFrame; }

    public void cleanup() {
        for (Mesh m : meshes) {
            if (m != null) m.cleanup();
        }
        meshes = new Mesh[0];
        count = 0;
        totalTris = 0;
    }

    public void build(WorldGenerator.Result r) {
        cleanup();
        if (r == null) return;
        int n = r.settings.resolution;
        int cells = n - 1;
        int chunksX = (cells + CELL - 1) / CELL;
        int chunksZ = (cells + CELL - 1) / CELL;
        count = chunksX * chunksZ;
        meshes = new Mesh[count];
        cx = new float[count];
        cy = new float[count];
        cz = new float[count];
        rad = new float[count];
        totalTris = 0;
        int idx = 0;
        for (int cz0 = 0; cz0 < chunksZ; cz0++) {
            for (int cx0 = 0; cx0 < chunksX; cx0++) {
                int ix0 = cx0 * CELL;
                int iz0 = cz0 * CELL;
                int ix1 = Math.min(ix0 + CELL, cells);
                int iz1 = Math.min(iz0 + CELL, cells);
                Mesh mesh = buildChunk(r, ix0, iz0, ix1, iz1);
                meshes[idx] = mesh;
                totalTris += mesh.indexCount() / 3;
                // center + radius for distance cull
                float half = r.size * 0.5f;
                float sp = r.settings.spacing;
                float minX = ix0 * sp - half, maxX = ix1 * sp - half;
                float minZ = iz0 * sp - half, maxZ = iz1 * sp - half;
                cx[idx] = (minX + maxX) * 0.5f;
                cz[idx] = (minZ + maxZ) * 0.5f;
                // average height
                float hy = 0;
                int hc = 0;
                for (int iz = iz0; iz <= iz1; iz++) {
                    for (int ix = ix0; ix <= ix1; ix++) {
                        hy += r.heights[iz * n + ix];
                        hc++;
                    }
                }
                cy[idx] = hc > 0 ? hy / hc : 0;
                float dx = maxX - minX, dz = maxZ - minZ;
                rad[idx] = 0.5f * (float) Math.sqrt(dx * dx + dz * dz) + 8f;
                idx++;
            }
        }
        System.out.println("[TerrainChunks] " + count + " chunks, " + totalTris
                + " tris total (draw only near camera)");
    }

    private static Mesh buildChunk(WorldGenerator.Result r, int ix0, int iz0, int ix1, int iz1) {
        int n = r.settings.resolution;
        int vx = ix1 - ix0 + 1;
        int vz = iz1 - iz0 + 1;
        float half = r.size * 0.5f;
        float sp = r.settings.spacing;
        float uvScale = 0.08f;
        float[] data = new float[vx * vz * 12];
        for (int lz = 0; lz < vz; lz++) {
            for (int lx = 0; lx < vx; lx++) {
                int ix = ix0 + lx;
                int iz = iz0 + lz;
                int gi = iz * n + ix;
                float x = ix * sp - half;
                float z = iz * sp - half;
                float y = r.heights[gi];
                float hL = r.heights[iz * n + Math.max(ix - 1, 0)];
                float hR = r.heights[iz * n + Math.min(ix + 1, n - 1)];
                float hD = r.heights[Math.max(iz - 1, 0) * n + ix];
                float hU = r.heights[Math.min(iz + 1, n - 1) * n + ix];
                float nx = (hL - hR) / (2f * sp);
                float nz = (hD - hU) / (2f * sp);
                float ny = 1f;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;
                float[] w = WorldGenerator.biomeBlendPublic(r.biomes[gi]);
                int o = (lz * vx + lx) * 12;
                data[o] = x; data[o + 1] = y; data[o + 2] = z;
                data[o + 3] = nx; data[o + 4] = ny; data[o + 5] = nz;
                data[o + 6] = x * uvScale;
                data[o + 7] = z * uvScale;
                data[o + 8] = w[0]; data[o + 9] = w[1];
                data[o + 10] = w[2]; data[o + 11] = w[3];
            }
        }
        int quads = (vx - 1) * (vz - 1);
        int[] indices = new int[quads * 6];
        int t = 0;
        for (int lz = 0; lz < vz - 1; lz++) {
            for (int lx = 0; lx < vx - 1; lx++) {
                int tl = lz * vx + lx;
                int tr = tl + 1;
                int bl = tl + vx;
                int br = bl + 1;
                indices[t++] = tl; indices[t++] = bl; indices[t++] = tr;
                indices[t++] = tr; indices[t++] = bl; indices[t++] = br;
            }
        }
        return new Mesh(data, indices, true);
    }

    /** Draw only chunks within maxDist of the camera (sphere test). */
    public void render(Renderer renderer, AssetBank assets,
                       float camX, float camY, float camZ, float maxDist) {
        drawnLastFrame = 0;
        float maxD2 = maxDist * maxDist;
        for (int i = 0; i < count; i++) {
            float dx = cx[i] - camX;
            float dy = cy[i] - camY;
            float dz = cz[i] - camZ;
            float d2 = dx * dx + dy * dy + dz * dz;
            float lim = maxDist + rad[i];
            if (d2 > lim * lim) continue;
            // cheap frustum proxy: skip if fully behind camera far beyond
            if (!renderer.visible(cx[i], cy[i], cz[i], rad[i], maxDist + rad[i])) continue;
            renderer.renderTerrain(meshes[i], assets);
            drawnLastFrame++;
        }
    }
}
