package com.toyzbuilder.engine;

/** Shared unit primitives with normals + UVs for textured piece cubes. */
public final class PrimitiveMeshes {

    private static Mesh UV_CUBE;

    public static Mesh uvCube() {
        if (UV_CUBE != null) return UV_CUBE;
        // 6 faces, 4 verts each, pos3+nrm3+uv2
        float[] v = {
                // +Z
                -0.5f,-0.5f, 0.5f, 0,0,1, 0,0,  0.5f,-0.5f, 0.5f, 0,0,1, 1,0,  0.5f, 0.5f, 0.5f, 0,0,1, 1,1, -0.5f, 0.5f, 0.5f, 0,0,1, 0,1,
                // -Z
                 0.5f,-0.5f,-0.5f, 0,0,-1, 0,0, -0.5f,-0.5f,-0.5f, 0,0,-1, 1,0, -0.5f, 0.5f,-0.5f, 0,0,-1, 1,1,  0.5f, 0.5f,-0.5f, 0,0,-1, 0,1,
                // +X
                 0.5f,-0.5f, 0.5f, 1,0,0, 0,0,  0.5f,-0.5f,-0.5f, 1,0,0, 1,0,  0.5f, 0.5f,-0.5f, 1,0,0, 1,1,  0.5f, 0.5f, 0.5f, 1,0,0, 0,1,
                // -X
                -0.5f,-0.5f,-0.5f,-1,0,0, 0,0, -0.5f,-0.5f, 0.5f,-1,0,0, 1,0, -0.5f, 0.5f, 0.5f,-1,0,0, 1,1, -0.5f, 0.5f,-0.5f,-1,0,0, 0,1,
                // +Y
                -0.5f, 0.5f, 0.5f, 0,1,0, 0,0,  0.5f, 0.5f, 0.5f, 0,1,0, 1,0,  0.5f, 0.5f,-0.5f, 0,1,0, 1,1, -0.5f, 0.5f,-0.5f, 0,1,0, 0,1,
                // -Y
                -0.5f,-0.5f,-0.5f, 0,-1,0, 0,0,  0.5f,-0.5f,-0.5f, 0,-1,0, 1,0,  0.5f,-0.5f, 0.5f, 0,-1,0, 1,1, -0.5f,-0.5f, 0.5f, 0,-1,0, 0,1,
        };
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            idx[f * 6] = b; idx[f * 6 + 1] = b + 1; idx[f * 6 + 2] = b + 2;
            idx[f * 6 + 3] = b; idx[f * 6 + 4] = b + 2; idx[f * 6 + 5] = b + 3;
        }
        UV_CUBE = Mesh.withNormalsUv(v, idx);
        return UV_CUBE;
    }
}
