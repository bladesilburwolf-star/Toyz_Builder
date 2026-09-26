package toyz.builder.terrain;

import com.raylib.Raylib.Mesh;
import com.raylib.Raylib.Model;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.ShortPointer;

import static com.raylib.Raylib.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Continuous water meshes — no DrawCube tiles.
 * Rivers: ribbon along WaterGenerator paths.
 * Ocean/lakes: height-field quads only where surface is submerged.
 */
public final class WaterMeshBuilder {
    private WaterMeshBuilder() {}

    public static final class WaterMeshes {
        public Model ocean;
        public Model rivers;
        public Model frozen;
        public float surfaceY;
    }

    public static WaterMeshes build(TerrainGenerator.WorldData world) {
        WaterMeshes out = new WaterMeshes();
        if (world == null || world.config == null) return out;
        WorldConfig cfg = world.config;
        out.surfaceY = world.oceanLevel + 0.05f;

        // --- Ocean / lake continuous surface ---
        out.ocean = buildFloodMesh(world, out.surfaceY, false);
        out.frozen = buildFloodMesh(world, out.surfaceY, true);

        // --- River ribbons ---
        if (world.water != null && !world.water.rivers.isEmpty()) {
            out.rivers = buildRiverRibbons(world, out.surfaceY);
        }
        return out;
    }

    private static Model buildFloodMesh(TerrainGenerator.WorldData world, float surfaceY, boolean frozenOnly) {
        WorldConfig cfg = world.config;
        float spacing = Math.max(6f, cfg.meshSpacing * 1.5f);
        float half = cfg.size * 0.5f;
        int res = Math.max(8, (int) Math.ceil(cfg.size / spacing) + 1);
        if (res > 96) {
            res = 96;
            spacing = cfg.size / (res - 1);
        }

        // First pass: which cells are wet
        boolean[] wet = new boolean[res * res];
        int wetCount = 0;
        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                float x = -half + ix * spacing;
                float z = -half + iz * spacing;
                float h = world.heightOnly(x, z);
                boolean under = h < world.oceanLevel - 0.02f;
                if (!under) continue;
                TerrainSample s = world.sample(x, z);
                boolean frozen = s.temperature < 0.28f;
                if (frozenOnly && !frozen) continue;
                if (!frozenOnly && frozen) continue;
                wet[iz * res + ix] = true;
                wetCount++;
            }
        }
        if (wetCount < 3) return null;

        // Quads between wet neighbors — collect unique verts
        List<float[]> verts = new ArrayList<>();
        List<int[]> tris = new ArrayList<>();
        int[] vertIndex = new int[res * res];
        java.util.Arrays.fill(vertIndex, -1);

        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                if (!wet[iz * res + ix]) continue;
                vertIndex[iz * res + ix] = verts.size();
                float x = -half + ix * spacing;
                float z = -half + iz * spacing;
                verts.add(new float[]{x, surfaceY, z, x * 0.08f, z * 0.08f});
            }
        }
        for (int iz = 0; iz < res - 1; iz++) {
            for (int ix = 0; ix < res - 1; ix++) {
                int i0 = vertIndex[iz * res + ix];
                int i1 = vertIndex[iz * res + ix + 1];
                int i2 = vertIndex[(iz + 1) * res + ix];
                int i3 = vertIndex[(iz + 1) * res + ix + 1];
                // need at least 3 corners wet
                int n = 0;
                if (i0 >= 0) n++;
                if (i1 >= 0) n++;
                if (i2 >= 0) n++;
                if (i3 >= 0) n++;
                if (n < 3) continue;
                if (i0 >= 0 && i2 >= 0 && i1 >= 0)
                    tris.add(new int[]{i0, i2, i1});
                if (i1 >= 0 && i2 >= 0 && i3 >= 0)
                    tris.add(new int[]{i1, i2, i3});
            }
        }
        if (tris.isEmpty()) return null;
        return meshFrom(verts, tris);
    }

    private static Model buildRiverRibbons(TerrainGenerator.WorldData world, float surfaceY) {
        List<float[]> verts = new ArrayList<>();
        List<int[]> tris = new ArrayList<>();
        float uvScale = 0.12f;

        for (WaterGenerator.RiverPath river : world.water.rivers) {
            if (river.xs == null || river.xs.length < 2) continue;
            float halfW = Math.max(2f, river.width * 0.5f) * world.config.waterStrength();
            int base = verts.size();
            int n = river.xs.length;
            for (int i = 0; i < n; i++) {
                float x = river.xs[i], z = river.zs[i];
                // tangent
                float tx, tz;
                if (i == 0) {
                    tx = river.xs[1] - x;
                    tz = river.zs[1] - z;
                } else if (i == n - 1) {
                    tx = x - river.xs[i - 1];
                    tz = z - river.zs[i - 1];
                } else {
                    tx = river.xs[i + 1] - river.xs[i - 1];
                    tz = river.zs[i + 1] - river.zs[i - 1];
                }
                float len = (float) Math.sqrt(tx * tx + tz * tz);
                if (len < 1e-4f) { tx = 1; tz = 0; len = 1; }
                tx /= len;
                tz /= len;
                float px = -tz, pz = tx; // perpendicular
                float u = i * uvScale;
                // left / right bank verts
                verts.add(new float[]{x + px * halfW, surfaceY, z + pz * halfW, u, 0f});
                verts.add(new float[]{x - px * halfW, surfaceY, z - pz * halfW, u, 1f});
            }
            for (int i = 0; i < n - 1; i++) {
                int i0 = base + i * 2;
                int i1 = i0 + 1;
                int i2 = i0 + 2;
                int i3 = i0 + 3;
                tris.add(new int[]{i0, i2, i1});
                tris.add(new int[]{i1, i2, i3});
            }
        }
        if (tris.isEmpty()) return null;
        return meshFrom(verts, tris);
    }

    /** vert = {x,y,z,u,v} */
    private static Model meshFrom(List<float[]> verts, List<int[]> tris) {
        int vc = verts.size();
        int tc = tris.size();
        Mesh mesh = new Mesh();
        mesh.vertexCount(vc);
        mesh.triangleCount(tc);
        FloatPointer v = new FloatPointer(vc * 3L);
        FloatPointer n = new FloatPointer(vc * 3L);
        FloatPointer uv = new FloatPointer(vc * 2L);
        ShortPointer idx = new ShortPointer(tc * 3L);
        for (int i = 0; i < vc; i++) {
            float[] p = verts.get(i);
            v.put(i * 3L, p[0]);
            v.put(i * 3L + 1, p[1]);
            v.put(i * 3L + 2, p[2]);
            n.put(i * 3L, 0f);
            n.put(i * 3L + 1, 1f);
            n.put(i * 3L + 2, 0f);
            uv.put(i * 2L, p[3]);
            uv.put(i * 2L + 1, p[4]);
        }
        int t = 0;
        for (int[] tri : tris) {
            idx.put(t++, (short) tri[0]);
            idx.put(t++, (short) tri[1]);
            idx.put(t++, (short) tri[2]);
        }
        mesh.vertices(v);
        mesh.normals(n);
        mesh.texcoords(uv);
        mesh.indices(idx);
        UploadMesh(mesh, false);
        return LoadModelFromMesh(mesh);
    }
}
