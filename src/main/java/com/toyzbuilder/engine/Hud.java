package com.toyzbuilder.engine;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 2D overlay for HD 6450-class GPUs.
 * Text is drawn as 5x7 pixel glyphs using the same rect path that already works
 * (no STB easy_font — that path was invisible on some drivers).
 */
public final class Hud {

    private ShaderProgram shader;
    private ShaderProgram imageShader;
    private int vao, vbo;
    private int imageVao, imageVbo;
    private static final int BATCH_BYTES = 262144;
    private static final int VERT_BYTES = 12;              // float x, float y, ubyte r,g,b,a
    private static final int MAX_VERTS = BATCH_BYTES / VERT_BYTES;
    private final ByteBuffer batch = MemoryUtil.memAlloc(BATCH_BYTES);
    private int batchVerts = 0;
    private final Matrix4f ortho = new Matrix4f();
    private final float[] mat16 = new float[16];

    /** cell pixel size for bitmap glyphs */
    private float glyphScale = 2.0f;

    public void init() {
        // GLSL 150 is safer on older Catalyst / HD 6000 series than 330 wording
        String vs = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec4 aColor;
            uniform mat4 projection;
            out vec4 vColor;
            void main() {
                vColor = aColor;
                gl_Position = projection * vec4(aPos, 0.0, 1.0);
            }
            """;
        String fs = """
            #version 330 core
            in vec4 vColor;
            uniform vec4 uTint;
            out vec4 FragColor;
            void main() {
                FragColor = vColor * uTint;
            }
            """;
        shader = new ShaderProgram(vs, fs);

        String ivs = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;
            uniform mat4 projection;
            out vec2 vUV;
            void main() {
                vUV = aUV;
                gl_Position = projection * vec4(aPos, 0.0, 1.0);
            }
            """;
        String ifs = """
            #version 330 core
            in vec2 vUV;
            uniform sampler2D uTex;
            uniform vec4 uTint;
            out vec4 FragColor;
            void main() {
                FragColor = texture(uTex, vUV) * uTint;
            }
            """;
        imageShader = new ShaderProgram(ivs, ifs);

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 262144, GL15.GL_DYNAMIC_DRAW);
        int stride = VERT_BYTES;
        GL20.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 4, GL20.GL_UNSIGNED_BYTE, true, stride, 8);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);

        imageVao = GL30.glGenVertexArrays();
        imageVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(imageVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, imageVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 16, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL20.GL_FLOAT, false, 16, 8);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        Glyphs.init();
    }

    public void begin(int width, int height) {
        shader.bind();
        ortho.setOrtho2D(0, width, height, 0);
        shader.setMat4("projection", ortho.get(mat16));
        shader.set4f("uTint", 1f, 1f, 1f, 1f);   // colour now lives in the vertices (batched)
        batch.clear();
        batchVerts = 0;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    }

    /** Draw text with 5x7 rect glyphs (visible on HD 6450). */
    public void text(float x, float y, String s, float r, float g, float b, float a) {
        if (s == null || s.isEmpty()) return;
        float px = x;
        float cell = glyphScale;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ' ') {
                px += cell * 6f;
                continue;
            }
            byte[] rows = Glyphs.get(ch);
            if (rows != null) {
                for (int row = 0; row < 7; row++) {
                    int bits = rows[row] & 0xFF;
                    int col = 0;
                    while (col < 5) {
                        if ((bits & (1 << (4 - col))) == 0) { col++; continue; }
                        int start = col;
                        while (col < 5 && (bits & (1 << (4 - col))) != 0) col++;
                        rect(px + start * cell, y + row * cell, (col - start) * cell, cell, r, g, b, a);
                    }
                }
            }
            px += cell * 6f;
        }
    }

    public void setGlyphScale(float s) { glyphScale = Math.max(1f, s); }

    public void image(Texture texture, float x, float y, float w, float h,
                      float r, float g, float b, float a) {
        if (texture == null) return;
        flush(); // keep draw order: everything queued so far goes underneath the image
        float[] v = {
                x, y, 0f, 1f,
                x + w, y, 1f, 1f,
                x + w, y + h, 1f, 0f,
                x, y, 0f, 1f,
                x + w, y + h, 1f, 0f,
                x, y + h, 0f, 0f
        };
        imageShader.bind();
        imageShader.setMat4("projection", ortho.get(mat16));
        imageShader.set4f("uTint", r, g, b, a);
        imageShader.set1i("uTex", 0);
        texture.bind(0);
        GL30.glBindVertexArray(imageVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, imageVbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, v);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        imageShader.unbind();
        // restore color shader binding for subsequent rect/text
        shader.bind();
        shader.setMat4("projection", ortho.get(mat16));
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
    }

    /** Queue one coloured quad. Nothing is drawn until flush()/end() (one draw call per batch). */
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (batchVerts + 6 > MAX_VERTS) flush();
        byte br = c8(r), bg = c8(g), bb = c8(b), ba = c8(a);
        vert(x, y, br, bg, bb, ba);
        vert(x + w, y, br, bg, bb, ba);
        vert(x + w, y + h, br, bg, bb, ba);
        vert(x, y, br, bg, bb, ba);
        vert(x + w, y + h, br, bg, bb, ba);
        vert(x, y + h, br, bg, bb, ba);
        batchVerts += 6;
    }

    private void vert(float x, float y, byte r, byte g, byte b, byte a) {
        batch.putFloat(x).putFloat(y).put(r).put(g).put(b).put(a);
    }

    private static byte c8(float v) {
        int i = (int) (v * 255f + 0.5f);
        return (byte) (i < 0 ? 0 : Math.min(i, 255));
    }

    /** Upload + draw everything queued. Color shader + VAO must be bound (begin() guarantees it). */
    private void flush() {
        if (batchVerts == 0) return;
        batch.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, batch);
        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, batchVerts);
        batch.clear();
        batchVerts = 0;
    }

    public void end() {
        flush();
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        shader.unbind();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public void cleanup() {
        MemoryUtil.memFree(batch);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(imageVbo);
        GL30.glDeleteVertexArrays(imageVao);
    }

    /** Tiny 5x7 glyphs — each byte is one row, bits 4..0 = pixels left→right. */
    static final class Glyphs {
        private static final Map<Character, byte[]> MAP = new HashMap<>();

        static void init() {
            // digits
            put('0', 0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E);
            put('1', 0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E);
            put('2', 0x0E, 0x11, 0x01, 0x06, 0x08, 0x10, 0x1F);
            put('3', 0x0E, 0x11, 0x01, 0x06, 0x01, 0x11, 0x0E);
            put('4', 0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02);
            put('5', 0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E);
            put('6', 0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E);
            put('7', 0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08);
            put('8', 0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E);
            put('9', 0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C);
            // A-Z
            put('A', 0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11);
            put('B', 0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E);
            put('C', 0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E);
            put('D', 0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E);
            put('E', 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F);
            put('F', 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10);
            put('G', 0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F);
            put('H', 0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11);
            put('I', 0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E);
            put('J', 0x01, 0x01, 0x01, 0x01, 0x11, 0x11, 0x0E);
            put('K', 0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11);
            put('L', 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F);
            put('M', 0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11);
            put('N', 0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11);
            put('O', 0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E);
            put('P', 0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10);
            put('Q', 0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D);
            put('R', 0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11);
            put('S', 0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E);
            put('T', 0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04);
            put('U', 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E);
            put('V', 0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04);
            put('W', 0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11);
            put('X', 0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11);
            put('Y', 0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04);
            put('Z', 0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F);
            // symbols
            put('-', 0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00);
            put('+', 0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00);
            put('=', 0x00, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00);
            put('.', 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C);
            put(',', 0x00, 0x00, 0x00, 0x00, 0x04, 0x08, 0x10);
            put(':', 0x00, 0x0C, 0x0C, 0x00, 0x0C, 0x0C, 0x00);
            put('/', 0x01, 0x02, 0x04, 0x04, 0x08, 0x10, 0x10);
            put('\\', 0x10, 0x10, 0x08, 0x04, 0x04, 0x02, 0x01);
            put('(', 0x04, 0x08, 0x10, 0x10, 0x10, 0x08, 0x04);
            put(')', 0x08, 0x04, 0x02, 0x02, 0x02, 0x04, 0x08);
            put('[', 0x0E, 0x08, 0x08, 0x08, 0x08, 0x08, 0x0E);
            put(']', 0x0E, 0x02, 0x02, 0x02, 0x02, 0x02, 0x0E);
            put('_', 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1F);
            put('!', 0x04, 0x04, 0x04, 0x04, 0x04, 0x00, 0x04);
            put('?', 0x0E, 0x11, 0x01, 0x06, 0x04, 0x00, 0x04);
            put('%', 0x19, 0x1A, 0x02, 0x04, 0x08, 0x13, 0x03);
            put('\'', 0x04, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00);
            put('"', 0x0A, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00);
            put('<', 0x02, 0x04, 0x08, 0x10, 0x08, 0x04, 0x02);
            put('>', 0x08, 0x04, 0x02, 0x01, 0x02, 0x04, 0x08);
            put('|', 0x04, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04);
        }

        private static void put(char c, int r0, int r1, int r2, int r3, int r4, int r5, int r6) {
            byte[] rows = new byte[]{(byte) r0, (byte) r1, (byte) r2, (byte) r3, (byte) r4, (byte) r5, (byte) r6};
            MAP.put(c, rows);
            MAP.put(Character.toLowerCase(c), rows);
        }

        static byte[] get(char c) {
            byte[] g = MAP.get(c);
            if (g != null) return g;
            return MAP.get(Character.toUpperCase(c));
        }
    }
}
