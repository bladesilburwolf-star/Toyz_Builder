package com.toyzbuilder.engine;

import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GLB / glTF / OBJ via Assimp → one or more pos+normal meshes.
 * Missing files fall back to a unit cube so the editor still works.
 */
public final class Model {

    private static final Map<String, Model> CACHE = new HashMap<>();
    private static Model UNIT_CUBE;

    public final String path;
    public final List<Mesh> meshes = new ArrayList<>();
    /** Axis-aligned size estimate for placement (full extents). */
    public float sizeX = 1f, sizeY = 1f, sizeZ = 1f;
    public boolean fallback;

    private Model(String path) {
        this.path = path;
    }

    public static Model unitCube() {
        if (UNIT_CUBE == null) {
            UNIT_CUBE = new Model("builtin:cube");
            UNIT_CUBE.fallback = true;
            UNIT_CUBE.meshes.add(buildUnitCubeMesh());
            UNIT_CUBE.sizeX = UNIT_CUBE.sizeY = UNIT_CUBE.sizeZ = 1f;
        }
        return UNIT_CUBE;
    }

    public static Model load(String relativeOrAbsolute) {
        if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
            return unitCube();
        }
        String key = relativeOrAbsolute.replace('\\', '/');
        Model cached = CACHE.get(key);
        if (cached != null) return cached;

        Path found = resolve(key);
        if (found == null) {
            System.err.println("[Model] missing: " + key + " → unit cube");
            CACHE.put(key, unitCube());
            return unitCube();
        }

        int flags = Assimp.aiProcess_Triangulate
                | Assimp.aiProcess_JoinIdenticalVertices
                | Assimp.aiProcess_GenSmoothNormals
                | Assimp.aiProcess_LimitBoneWeights
                | Assimp.aiProcess_ImproveCacheLocality;

        AIScene scene = Assimp.aiImportFile(found.toString(), flags);
        if (scene == null) {
            System.err.println("[Model] Assimp failed: " + found + " — " + Assimp.aiGetErrorString());
            CACHE.put(key, unitCube());
            return unitCube();
        }

        try {
            Model model = new Model(found.toString());
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

            int meshCount = scene.mNumMeshes();
            for (int mi = 0; mi < meshCount; mi++) {
                AIMesh aim = AIMesh.create(scene.mMeshes().get(mi));
                int vCount = aim.mNumVertices();
                // pos3 + normal3 + uv2
                float[] verts = new float[vCount * 8];
                AIVector3D.Buffer positions = aim.mVertices();
                AIVector3D.Buffer normals = aim.mNormals();
                AIVector3D.Buffer uvs = aim.mTextureCoords(0);
                for (int i = 0; i < vCount; i++) {
                    AIVector3D p = positions.get(i);
                    float px = p.x(), py = p.y(), pz = p.z();
                    verts[i * 8] = px;
                    verts[i * 8 + 1] = py;
                    verts[i * 8 + 2] = pz;
                    if (normals != null) {
                        AIVector3D n = normals.get(i);
                        verts[i * 8 + 3] = n.x();
                        verts[i * 8 + 4] = n.y();
                        verts[i * 8 + 5] = n.z();
                    } else {
                        verts[i * 8 + 3] = 0;
                        verts[i * 8 + 4] = 1;
                        verts[i * 8 + 5] = 0;
                    }
                    if (uvs != null) {
                        AIVector3D uv = uvs.get(i);
                        verts[i * 8 + 6] = uv.x();
                        verts[i * 8 + 7] = uv.y();
                    } else {
                        // Stable planar fallback for GLBs with no UV channel.
                        verts[i * 8 + 6] = px;
                        verts[i * 8 + 7] = pz;
                    }
                    minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
                }

                int faceCount = aim.mNumFaces();
                IntBuffer idxBuf = MemoryUtil.memAllocInt(faceCount * 3);
                AIFace.Buffer faces = aim.mFaces();
                for (int f = 0; f < faceCount; f++) {
                    AIFace face = faces.get(f);
                    if (face.mNumIndices() < 3) continue;
                    idxBuf.put(face.mIndices().get(0));
                    idxBuf.put(face.mIndices().get(1));
                    idxBuf.put(face.mIndices().get(2));
                }
                idxBuf.flip();
                int[] indices = new int[idxBuf.remaining()];
                idxBuf.get(indices);
                MemoryUtil.memFree(idxBuf);

                model.meshes.add(Mesh.withNormalsUv(verts, indices));
            }

            if (model.meshes.isEmpty()) {
                System.err.println("[Model] no meshes in " + found);
                CACHE.put(key, unitCube());
                return unitCube();
            }

            model.sizeX = Math.max(0.01f, maxX - minX);
            model.sizeY = Math.max(0.01f, maxY - minY);
            model.sizeZ = Math.max(0.01f, maxZ - minZ);
            System.out.println("[Model] loaded " + found.getFileName()
                    + " meshes=" + model.meshes.size()
                    + " size=" + String.format("%.2f x %.2f x %.2f", model.sizeX, model.sizeY, model.sizeZ));
            CACHE.put(key, model);
            return model;
        } finally {
            Assimp.aiReleaseImport(scene);
        }
    }

    private static Path resolve(String relative) {
        String[] roots = {
                relative,
                "assets/" + relative,
                "assets/models/" + relative,
                "models/" + relative,
                "../assets/models/" + relative,
                "../Raylib/assets/models/" + relative
        };
        for (String r : roots) {
            Path p = Paths.get(r).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) return p;
        }
        // strip leading models/
        if (relative.startsWith("models/")) {
            return resolve(relative.substring(7));
        }
        return null;
    }

    private static Mesh buildUnitCubeMesh() {
        // 24 verts with face normals (pos+normal interleaved) — 6 faces * 4 verts
        float[] v = {
                // +Z
                -0.5f,-0.5f, 0.5f,  0,0,1,   0.5f,-0.5f, 0.5f,  0,0,1,   0.5f, 0.5f, 0.5f,  0,0,1,  -0.5f, 0.5f, 0.5f,  0,0,1,
                // -Z
                 0.5f,-0.5f,-0.5f,  0,0,-1, -0.5f,-0.5f,-0.5f,  0,0,-1, -0.5f, 0.5f,-0.5f,  0,0,-1,  0.5f, 0.5f,-0.5f,  0,0,-1,
                // +X
                 0.5f,-0.5f, 0.5f,  1,0,0,   0.5f,-0.5f,-0.5f,  1,0,0,   0.5f, 0.5f,-0.5f,  1,0,0,   0.5f, 0.5f, 0.5f,  1,0,0,
                // -X
                -0.5f,-0.5f,-0.5f, -1,0,0,  -0.5f,-0.5f, 0.5f, -1,0,0,  -0.5f, 0.5f, 0.5f, -1,0,0,  -0.5f, 0.5f,-0.5f, -1,0,0,
                // +Y
                -0.5f, 0.5f, 0.5f,  0,1,0,   0.5f, 0.5f, 0.5f,  0,1,0,   0.5f, 0.5f,-0.5f,  0,1,0,  -0.5f, 0.5f,-0.5f,  0,1,0,
                // -Y
                -0.5f,-0.5f,-0.5f,  0,-1,0,  0.5f,-0.5f,-0.5f,  0,-1,0,  0.5f,-0.5f, 0.5f,  0,-1,0, -0.5f,-0.5f, 0.5f,  0,-1,0,
        };
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            idx[f * 6] = b; idx[f * 6 + 1] = b + 1; idx[f * 6 + 2] = b + 2;
            idx[f * 6 + 3] = b; idx[f * 6 + 4] = b + 2; idx[f * 6 + 5] = b + 3;
        }
        return Mesh.withNormals(v, idx);
    }
}
