package com.toyzbuilder.engine;

/**
 * Simple material: optional diffuse texture + tint color.
 * Binds into the active shader's conventional uniform names.
 */
public final class Material {

    public final String name;
    public Texture diffuse;       // may be null → flat tint only
    public float r = 1f, g = 1f, b = 1f, a = 1f;
    public boolean unlit = false;

    public Material(String name) {
        this.name = name;
    }

    public Material tint(float r, float g, float b, float a) {
        this.r = r; this.g = g; this.b = b; this.a = a;
        return this;
    }

    public Material texture(Texture t) {
        this.diffuse = t;
        return this;
    }

    public static Material solid(String name, float r, float g, float b) {
        return new Material(name).tint(r, g, b, 1f);
    }
}
