package com.toyzbuilder.world;

public class Entity {

    public final String category;
    public float x, y, z;
    /** Yaw in degrees (0, 90, 180, 270 typical for snap rotate). */
    public float yaw;
    public boolean solid;
    public float areaRadius = 2.5f;
    public String text = "";

    public Entity(String category, float x, float y, float z, boolean solid) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.z = z;
        this.solid = solid;
        this.yaw = 0f;
        if (category != null) {
            String u = category.toUpperCase();
            if (u.equals("LIGHT")) areaRadius = 5.0f;
            else if (u.equals("TORCH")) areaRadius = 3.0f;
            else if (!u.equals("ENEMY") && !u.equals("BOSS")) areaRadius = 0f;
        }
    }

    public Entity copy() {
        Entity e = new Entity(category, x, y, z, solid);
        e.areaRadius = areaRadius;
        e.text = text;
        e.yaw = yaw;
        return e;
    }
}
