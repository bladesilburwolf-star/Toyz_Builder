package com.toyzbuilder.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniforms = new HashMap<>();

    /** Renderer stamps this with the frame id after uploading per-frame uniforms (camera, fog...). */
    int frameStamp = -1;

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vertexId = compileShader(vertexSrc, GL20.GL_VERTEX_SHADER);
        int fragmentId = compileShader(fragmentSrc, GL20.GL_FRAGMENT_SHADER);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexId);
        GL20.glAttachShader(programId, fragmentId);
        // Bind common attribute names before link (required for GLSL 150 without layout())
        GL20.glBindAttribLocation(programId, 0, "aPos");
        GL20.glBindAttribLocation(programId, 1, "aColor");
        GL20.glBindAttribLocation(programId, 1, "aNormal"); // meshes share loc1 for normal
        GL20.glBindAttribLocation(programId, 1, "aUV");     // image shader uses loc1 for UV
        GL20.glBindAttribLocation(programId, 2, "aUV");
        GL20.glBindAttribLocation(programId, 3, "aBlend");
        GL20.glLinkProgram(programId);

        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader linking failed: " + GL20.glGetProgramInfoLog(programId));
        }

        GL20.glDeleteShader(vertexId);
        GL20.glDeleteShader(fragmentId);
    }

    private int compileShader(String src, int type) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);

        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader compile failed: " + GL20.glGetShaderInfoLog(id));
        }

        return id;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getId() {
        return programId;
    }

    // ---- cached uniform access (glGetUniformLocation is a driver round-trip; never call per draw) ----

    public int loc(String name) {
        Integer c = uniforms.get(name);
        if (c == null) {
            c = GL20.glGetUniformLocation(programId, name);
            uniforms.put(name, c);
        }
        return c;
    }

    /** Program must be bound. Location -1 (unused uniform) is silently ignored by GL. */
    public void set1i(String n, int v) { GL20.glUniform1i(loc(n), v); }
    public void set1f(String n, float v) { GL20.glUniform1f(loc(n), v); }
    public void set3f(String n, float a, float b, float c) { GL20.glUniform3f(loc(n), a, b, c); }
    public void set4f(String n, float a, float b, float c, float d) { GL20.glUniform4f(loc(n), a, b, c, d); }
    public void setMat4(String n, float[] m) { GL20.glUniformMatrix4fv(loc(n), false, m); }
    public void setMat3(String n, float[] m) { GL20.glUniformMatrix3fv(loc(n), false, m); }
}
