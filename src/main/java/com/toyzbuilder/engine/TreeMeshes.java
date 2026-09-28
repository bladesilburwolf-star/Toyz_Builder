package com.toyzbuilder.engine;

/**
 * Shared procedural tree meshes with UVs (pos3 + normal3 + uv2 = 8 floats).
 * Trunk uses bark texture; canopy uses grass/leaf tint via shader color.
 */
public final class TreeMeshes {

    public final Mesh trunk;
    public final Mesh canopy;

    public TreeMeshes() {
        trunk = buildCylinder(6, 1f, 0.12f, 0.10f);
        canopy = buildCanopy();
    }

    /** Cylinder along +Y from 0..height, radius bottom/top. */
    private static Mesh buildCylinder(int segments, float height, float rBot, float rTop) {
        int rings = 2;
        int vCount = (segments + 1) * rings;
        float[] v = new float[vCount * 8];
        int vi = 0;
        for (int y = 0; y < rings; y++) {
            float fy = y;
            float py = fy * height;
            float radius = rBot + (rTop - rBot) * fy;
            for (int i = 0; i <= segments; i++) {
                float a = (float) (i * Math.PI * 2.0 / segments);
                float cx = (float) Math.cos(a);
                float cz = (float) Math.sin(a);
                v[vi++] = cx * radius;
                v[vi++] = py;
                v[vi++] = cz * radius;
                v[vi++] = cx;
                v[vi++] = 0f;
                v[vi++] = cz;
                v[vi++] = (float) i / segments;
                v[vi++] = fy;
            }
        }
        int[] idx = new int[segments * 6];
        int ii = 0;
        for (int i = 0; i < segments; i++) {
            int a = i;
            int b = i + 1;
            int c = i + (segments + 1);
            int d = i + 1 + (segments + 1);
            idx[ii++] = a; idx[ii++] = c; idx[ii++] = b;
            idx[ii++] = b; idx[ii++] = c; idx[ii++] = d;
        }
        return Mesh.withNormalsUv(v, idx);
    }

    /** Layered cones / spheres as leaf blob (local space, sits above y=0.55). */
    private static Mesh buildCanopy() {
        // three stacked flattened spheres approximated as UV spheres low poly
        int seg = 8, rings = 5;
        float[][] layers = {
                {0.55f, 0.55f},
                {0.95f, 0.70f},
                {1.35f, 0.45f}
        };
        int per = (rings + 1) * (seg + 1);
        float[] v = new float[layers.length * per * 8];
        int[] idx = new int[layers.length * rings * seg * 6];
        int vi = 0, ii = 0, vBase = 0;
        for (float[] layer : layers) {
            float cy = layer[0];
            float rad = layer[1];
            for (int r = 0; r <= rings; r++) {
                float phi = (float) (Math.PI * r / rings);
                float y = cy + (float) Math.cos(phi) * rad * 0.55f;
                float ringR = (float) Math.sin(phi) * rad;
                for (int s = 0; s <= seg; s++) {
                    float th = (float) (s * Math.PI * 2 / seg);
                    float x = (float) Math.cos(th) * ringR;
                    float z = (float) Math.sin(th) * ringR;
                    float nx = x, ny = y - cy, nz = z;
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 1e-5f) { nx /= len; ny /= len; nz /= len; }
                    v[vi++] = x; v[vi++] = y; v[vi++] = z;
                    v[vi++] = nx; v[vi++] = ny; v[vi++] = nz;
                    v[vi++] = (float) s / seg;
                    v[vi++] = (float) r / rings;
                }
            }
            for (int r = 0; r < rings; r++) {
                for (int s = 0; s < seg; s++) {
                    int a = vBase + r * (seg + 1) + s;
                    int b = a + seg + 1;
                    idx[ii++] = a; idx[ii++] = b; idx[ii++] = a + 1;
                    idx[ii++] = a + 1; idx[ii++] = b; idx[ii++] = b + 1;
                }
            }
            vBase += per;
        }
        // trim arrays
        float[] vv = new float[vi];
        System.arraycopy(v, 0, vv, 0, vi);
        int[] ii2 = new int[ii];
        System.arraycopy(idx, 0, ii2, 0, ii);
        return Mesh.withNormalsUv(vv, ii2);
    }

    public void cleanup() {
        trunk.cleanup();
        canopy.cleanup();
    }
}
