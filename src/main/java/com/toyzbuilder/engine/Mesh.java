package com.toyzbuilder.engine;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Mesh layouts:
 * - terrain: pos3 + normal3 + uv2 + blend4 (12)
 * - normals: pos3 + normal3 (6)
 * - plain:   pos3 (3)
 */
public class Mesh {

    public enum Layout { PLAIN, NORMALS, NORMALS_UV, TERRAIN }

    private final int vao, vbo, ebo, indexCount;
    private final Layout layout;

    public Mesh(float[] vertices, int[] indices) {
        this(vertices, indices, false);
    }

    public Mesh(float[] vertices, int[] indices, boolean terrain) {
        this(vertices, indices, terrain ? Layout.TERRAIN : Layout.PLAIN);
    }

    public Mesh(float[] vertices, int[] indices, Layout layout) {
        this.layout = layout;
        this.indexCount = indices.length;

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        FloatBuffer vBuf = MemoryUtil.memAllocFloat(vertices.length);
        vBuf.put(vertices).flip();
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vBuf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(vBuf);

        IntBuffer iBuf = MemoryUtil.memAllocInt(indices.length);
        iBuf.put(indices).flip();
        ebo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, iBuf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(iBuf);

        if (layout == Layout.TERRAIN) {
            int stride = 12 * Float.BYTES;
            attrib(0, 3, stride, 0);
            attrib(1, 3, stride, 3L * Float.BYTES);
            attrib(2, 2, stride, 6L * Float.BYTES);
            attrib(3, 4, stride, 8L * Float.BYTES);
        } else if (layout == Layout.NORMALS) {
            int stride = 6 * Float.BYTES;
            attrib(0, 3, stride, 0);
            attrib(1, 3, stride, 3L * Float.BYTES);
        } else if (layout == Layout.NORMALS_UV) {
            int stride = 8 * Float.BYTES;
            attrib(0, 3, stride, 0);
            attrib(1, 3, stride, 3L * Float.BYTES);
            attrib(2, 2, stride, 6L * Float.BYTES);
        } else {
            attrib(0, 3, 3 * Float.BYTES, 0);
        }

        GL30.glBindVertexArray(0);
    }

    private static void attrib(int index, int size, int stride, long offset) {
        GL20.glVertexAttribPointer(index, size, GL20.GL_FLOAT, false, stride, offset);
        GL20.glEnableVertexAttribArray(index);
    }

    /** pos3 + normal3 interleaved */
    public static Mesh withNormals(float[] vertices, int[] indices) {
        return new Mesh(vertices, indices, Layout.NORMALS);
    }

    public static Mesh withNormalsUv(float[] vertices, int[] indices) {
        return new Mesh(vertices, indices, Layout.NORMALS_UV);
    }

    public void render() {
        GL30.glBindVertexArray(vao);
        GL20.glDrawElements(GL20.GL_TRIANGLES, indexCount, GL20.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }

    public void cleanup() {
        GL15.glDeleteBuffers(vbo);
        GL15.glDeleteBuffers(ebo);
        GL30.glDeleteVertexArrays(vao);
    }

    public boolean isTerrain() { return layout == Layout.TERRAIN; }
    public Layout getLayout() { return layout; }
    public int indexCount() { return indexCount; }
}
