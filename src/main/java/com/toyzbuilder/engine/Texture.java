package com.toyzbuilder.engine;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Loads PNG/JPG via STB into an OpenGL texture. */
public final class Texture {

    private final int id;
    private final int width, height;
    private final String path;
    private boolean mipmapped;

    private Texture(int id, int width, int height, String path) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.path = path;
    }

    public int getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String pathSafe() { return path == null ? "" : path; }

    public void bind(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public void cleanup() {
        GL11.glDeleteTextures(id);
    }

    /** Try several relative roots so running from project or build/ still finds assets. */
    public static Texture load(String relativePath) {
        Path found = resolve(relativePath);
        if (found == null) {
            System.err.println("[Texture] MISSING: " + relativePath);
            return createFallback(0.5f, 0.5f, 0.5f);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer pixels = STBImage.stbi_load(found.toString(), w, h, comp, 4);
            if (pixels == null) {
                System.err.println("[Texture] STB fail: " + found + " — " + STBImage.stbi_failure_reason());
                return createFallback(1f, 0f, 1f);
            }
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w.get(0), h.get(0), 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            STBImage.stbi_image_free(pixels);
            Texture loaded = new Texture(tex, w.get(0), h.get(0), found.toString());
            loaded.mipmapped = true;
            System.out.println("[Texture] loaded " + found + " (" + w.get(0) + "x" + h.get(0) + ")");
            return loaded;
        }
    }

    /**
     * Apply the Options texture filter. mode: 0 nearest, 1 bilinear+mip, 2 trilinear.
     * aniso > 1 needs GL_EXT_texture_filter_anisotropic (HD 6450 has it, but it costs bandwidth).
     * Fallback 1x1 textures are left alone.
     */
    public void setFilter(int mode, int aniso) {
        if (!mipmapped) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        int min, mag;
        switch (mode) {
            case 0 -> { min = GL11.GL_NEAREST_MIPMAP_NEAREST; mag = GL11.GL_NEAREST; }
            case 2 -> { min = GL11.GL_LINEAR_MIPMAP_LINEAR; mag = GL11.GL_LINEAR; }
            default -> { min = GL11.GL_LINEAR_MIPMAP_NEAREST; mag = GL11.GL_LINEAR; }
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        if (org.lwjgl.opengl.GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    Math.max(1f, Math.min(aniso, max)));
        }
    }

    private static Path resolve(String relative) {
        String[] roots = {
            "",
            "assets/",
            "../assets/",
            "ToyzBuilderWorldGen/assets/",
            "./"
        };
        for (String root : roots) {
            Path p = Paths.get(root + relative).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) return p;
            // also try without "assets/" prefix if relative already has it
            p = Paths.get(root, relative).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) return p;
        }
        // walk common texture locations
        Path base = Paths.get("assets").toAbsolutePath();
        if (Files.isDirectory(base)) {
            Path p = base.resolve(relative.replace("assets/", "")).normalize();
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static Texture createFallback(float r, float g, float b) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        ByteBuffer px = MemoryUtil.memAlloc(4);
        px.put((byte) (r * 255)).put((byte) (g * 255)).put((byte) (b * 255)).put((byte) 255).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        MemoryUtil.memFree(px);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return new Texture(tex, 1, 1, "fallback");
    }
}
