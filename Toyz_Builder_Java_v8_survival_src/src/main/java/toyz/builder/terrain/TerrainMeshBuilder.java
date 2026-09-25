package toyz.builder.terrain;

import com.raylib.Helpers;
import com.raylib.Raylib.Mesh;
import com.raylib.Raylib.Model;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.ShortPointer;

import static com.raylib.Raylib.*;

/**
 * Continuous height-field mesh: real polygons, smooth normals, UVs for 512 textures.
 * Logical sample spacing ≠ required equal to mesh spacing (LOD-ready).
 */
public final class TerrainMeshBuilder {
    private TerrainMeshBuilder() {}

    public static Model build(TerrainGenerator.WorldData world) {
        WorldConfig cfg = world.config;
        float spacing = cfg.meshSpacing;
        float half = cfg.size * 0.5f;
        int res = Math.max(8, (int) Math.ceil(cfg.size / spacing) + 1);
        // Cap for performance on older GPUs
        if (res > 128) {
            res = 128;
            spacing = cfg.size / (res - 1);
        }

        int vertCount = res * res;
        int quadCount = (res - 1) * (res - 1);
        int triCount = quadCount * 2;

        float[] heights = new float[vertCount];
        float[] xs = new float[vertCount];
        float[] zs = new float[vertCount];
        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                int i = iz * res + ix;
                float x = -half + ix * spacing;
                float z = -half + iz * spacing;
                xs[i] = x;
                zs[i] = z;
                heights[i] = world.heightOnly(x, z);
            }
        }

        // Positions + normals + uvs interleaved via GenMesh + fill — use Mesh manually
        Mesh mesh = new Mesh();
        mesh.vertexCount(vertCount);
        mesh.triangleCount(triCount);

        FloatPointer verts = new FloatPointer(vertCount * 3L);
        FloatPointer norms = new FloatPointer(vertCount * 3L);
        FloatPointer uvs = new FloatPointer(vertCount * 2L);
        // indices as unsigned short
        ShortPointer indices = new ShortPointer(triCount * 3L);

        float uvScale = 1f / 16f; // tile 512 textures across ~16 world units

        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                int i = iz * res + ix;
                float x = xs[i], z = zs[i], y = heights[i];
                verts.put(i * 3L, x);
                verts.put(i * 3L + 1, y);
                verts.put(i * 3L + 2, z);
                uvs.put(i * 2L, x * uvScale);
                uvs.put(i * 2L + 1, z * uvScale);

                // Smooth normal from finite differences
                float hL = heightAt(heights, xs, zs, res, x - spacing, z, half, spacing);
                float hR = heightAt(heights, xs, zs, res, x + spacing, z, half, spacing);
                float hD = heightAt(heights, xs, zs, res, x, z - spacing, half, spacing);
                float hU = heightAt(heights, xs, zs, res, x, z + spacing, half, spacing);
                float nx = hL - hR;
                float ny = 2f * spacing;
                float nz = hD - hU;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) { nx = 0; ny = 1; nz = 0; len = 1; }
                norms.put(i * 3L, nx / len);
                norms.put(i * 3L + 1, ny / len);
                norms.put(i * 3L + 2, nz / len);
            }
        }

        int t = 0;
        for (int iz = 0; iz < res - 1; iz++) {
            for (int ix = 0; ix < res - 1; ix++) {
                int i0 = iz * res + ix;
                int i1 = i0 + 1;
                int i2 = i0 + res;
                int i3 = i2 + 1;
                // two triangles
                indices.put(t++, (short) i0);
                indices.put(t++, (short) i2);
                indices.put(t++, (short) i1);
                indices.put(t++, (short) i1);
                indices.put(t++, (short) i2);
                indices.put(t++, (short) i3);
            }
        }

        mesh.vertices(verts);
        mesh.normals(norms);
        mesh.texcoords(uvs);
        mesh.indices(indices);

        UploadMesh(mesh, false);
        Model model = LoadModelFromMesh(mesh);
        return model;
    }

    private static float heightAt(float[] heights, float[] xs, float[] zs, int res,
                                  float x, float z, float half, float spacing) {
        float fx = (x + half) / spacing;
        float fz = (z + half) / spacing;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        if (ix < 0) ix = 0;
        if (iz < 0) iz = 0;
        if (ix >= res) ix = res - 1;
        if (iz >= res) iz = res - 1;
        return heights[iz * res + ix];
    }
}
