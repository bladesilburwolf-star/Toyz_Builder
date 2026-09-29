package com.toyzbuilder.world;

/** Landmark placed by StructureGenerator — also a map / compass marker. */
public final class StructureSite {
    public enum Type {
        TOMB("Graveyard"),
        DUNGEON("Dungeon"),
        TOWN("Town"),
        FORT("Fort"),
        SHRINE("Shrine");

        public final String label;
        Type(String l) { this.label = l; }
    }

    public final Type type;
    public final String name;
    public final float x, y, z;
    public final float yaw;
    public final int seed;

    public StructureSite(Type type, String name, float x, float y, float z, float yaw, int seed) {
        this.type = type;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.seed = seed;
    }
}
