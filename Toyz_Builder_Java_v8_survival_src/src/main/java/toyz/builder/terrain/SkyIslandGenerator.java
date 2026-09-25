package toyz.builder.terrain;

import com.raylib.Raylib.Model;
import static com.raylib.Raylib.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating continuous terrain islands above the main world (real meshes, not cubes).
 */
public final class SkyIslandGenerator {

    public static final class Island {
        public float cx, cz;
        public float baseY;
        public float radius;
        public Model mesh;
        public int seedOffset;
    }

    private SkyIslandGenerator() {}

    public static List<Island> generate(WorldConfig cfg) {
        List<Island> out = new ArrayList<>();
        if (cfg == null || !cfg.skyIslands) return out;
        if (cfg.preset == WorldConfig.MapgenPreset.FLAT) return out;

        int count = 3 + (Math.abs(cfg.seed) % 3);
        for (int i = 0; i < count; i++) {
            Island isl = new Island();
            float ang = Noise.hash2D(i * 13, 7, cfg.seed) * 6.2831853f;
            float dist = cfg.size * (0.15f + Noise.hash2D(i, 19, cfg.seed) * 0.28f);
            isl.cx = (float) Math.cos(ang) * dist;
            isl.cz = (float) Math.sin(ang) * dist;
            isl.baseY = 55f + Noise.hash2D(i, 3, cfg.seed) * 40f + cfg.heightScale * 0.5f;
            isl.radius = 18f + Noise.hash2D(i, 5, cfg.seed) * 22f;
            isl.seedOffset = 5000 + i * 173;
            isl.mesh = buildIslandMesh(cfg, isl);
            out.add(isl);
            System.out.println("[SkyIsland] r=" + isl.radius + " at (" + isl.cx + "," + isl.baseY + "," + isl.cz + ")");
        }
        return out;
    }

    /** Height on island local xz relative to center. */
    public static float islandHeight(WorldConfig cfg, Island isl, float x, float z) {
        float dx = x - isl.cx, dz = z - isl.cz;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist > isl.radius) return Float.NaN;
        float edge = 1f - dist / isl.radius;
        edge = edge * edge * (3f - 2f * edge);
        float nx = dx / cfg.size, nz = dz / cfg.size;
        float h = Noise.fractal(nx * 8f + isl.seedOffset, nz * 8f, cfg.seed + isl.seedOffset, 4);
        return isl.baseY + h * cfg.heightScale * 0.35f * edge;
    }

    private static Model buildIslandMesh(WorldConfig cfg, Island isl) {
        // Build a mini WorldData-like height via local sampling into mesh
        float spacing = 3.5f;
        int res = Math.max(8, (int) Math.ceil(isl.radius * 2f / spacing) + 1);
        if (res > 48) res = 48;
        spacing = (isl.radius * 2f) / (res - 1);

        // Reuse WaterMeshBuilder-style assembly
        java.util.List<float[]> verts = new java.util.ArrayList<>();
        java.util.List<int[]> tris = new java.util.ArrayList<>();
        int[] idxMap = new int[res * res];
        java.util.Arrays.fill(idxMap, -1);

        for (int iz = 0; iz < res; iz++) {
            for (int ix = 0; ix < res; ix++) {
                float x = isl.cx - isl.radius + ix * spacing;
                float z = isl.cz - isl.radius + iz * spacing;
                float y = islandHeight(cfg, isl, x, z);
                if (Float.isNaN(y)) continue;
                idxMap[iz * res + ix] = verts.size();
                verts.add(new float[]{x, y, z, x * 0.1f, z * 0.1f});
            }
        }
        for (int iz = 0; iz < res - 1; iz++) {
            for (int ix = 0; ix < res - 1; ix++) {
                int i0 = idxMap[iz * res + ix];
                int i1 = idxMap[iz * res + ix + 1];
                int i2 = idxMap[(iz + 1) * res + ix];
                int i3 = idxMap[(iz + 1) * res + ix + 1];
                if (i0 >= 0 && i2 >= 0 && i1 >= 0) tris.add(new int[]{i0, i2, i1});
                if (i1 >= 0 && i2 >= 0 && i3 >= 0) tris.add(new int[]{i1, i2, i3});
            }
        }
        if (tris.isEmpty()) return null;
        return buildMesh(verts, tris);
    }

    private static Model buildMesh(java.util.List<float[]> verts, java.util.List<int[]> tris) {
        // Smooth normals approx up
        int vc = verts.size();
        int tc = tris.size();
        com.raylib.Raylib.Mesh mesh = new com.raylib.Raylib.Mesh();
        mesh.vertexCount(vc);
        mesh.triangleCount(tc);
        org.bytedeco.javacpp.FloatPointer v = new org.bytedeco.javacpp.FloatPointer(vc * 3L);
        org.bytedeco.javacpp.FloatPointer n = new org.bytedeco.javacpp.FloatPointer(vc * 3L);
        org.bytedeco.javacpp.FloatPointer uv = new org.bytedeco.javacpp.FloatPointer(vc * 2L);
        org.bytedeco.javacpp.ShortPointer indices = new org.bytedeco.javacpp.ShortPointer(tc * 3L);
        for (int i = 0; i < vc; i++) {
            float[] p = verts.get(i);
            v.put(i * 3L, p[0]); v.put(i * 3L + 1, p[1]); v.put(i * 3L + 2, p[2]);
            n.put(i * 3L, 0f); n.put(i * 3L + 1, 1f); n.put(i * 3L + 2, 0f);
            uv.put(i * 2L, p[3]); uv.put(i * 2L + 1, p[4]);
        }
        int t = 0;
        for (int[] tri : tris) {
            indices.put(t++, (short) tri[0]);
            indices.put(t++, (short) tri[1]);
            indices.put(t++, (short) tri[2]);
        }
        mesh.vertices(v); mesh.normals(n); mesh.texcoords(uv); mesh.indices(indices);
        UploadMesh(mesh, false);
        return LoadModelFromMesh(mesh);
    }
}
