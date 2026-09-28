package com.toyzbuilder.engine;

import com.toyzbuilder.world.StructureSite;
import com.toyzbuilder.world.WorldGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Compass (always-on strip) + minimap + full map (M).
 * Click a marker on the full map to warp.
 */
public final class WorldMapUI {

    private boolean fullMapOpen = false;
    private final List<StructureSite> markers = new ArrayList<>();
    private float worldSize = 512f;
    private float playerX, playerZ, playerYaw;

    // full-map layout cache
    private float mapX, mapY, mapW, mapH;
    private int hovered = -1;

    public boolean isFullMapOpen() { return fullMapOpen; }
    public void toggleFullMap() { fullMapOpen = !fullMapOpen; }
    public void setFullMapOpen(boolean on) { fullMapOpen = on; }

    public void setMarkers(List<StructureSite> sites, float worldSize) {
        markers.clear();
        if (sites != null) markers.addAll(sites);
        this.worldSize = Math.max(1f, worldSize);
    }

    public void setPlayer(float x, float z, float yawDeg) {
        playerX = x;
        playerZ = z;
        playerYaw = yawDeg;
    }

    public void draw(Hud hud, int sw, int sh) {
        drawCompass(hud, sw, sh);
        drawMinimap(hud, sw, sh);
        if (fullMapOpen) drawFullMap(hud, sw, sh);
    }

    private void drawCompass(Hud hud, int sw, int sh) {
        // top-center strip
        float cw = 220, ch = 28;
        float cx = (sw - cw) * 0.5f;
        float cy = 8;
        hud.rect(cx, cy, cw, ch, 0.04f, 0.05f, 0.04f, 1f);
        String facing = cardinal(playerYaw);
        hud.text(cx + 12, cy + 8, "COMPASS  " + facing, 0.35f, 1f, 0.45f, 1f);
        // nearest marker name
        StructureSite near = nearest();
        if (near != null) {
            float dist = dist(near);
            String dir = bearing(near);
            hud.text(cx + 110, cy + 8, near.type.label + " " + dir + " " + (int) dist + "m",
                    0.25f, 0.7f, 0.35f, 1f);
        }
    }

    private void drawMinimap(Hud hud, int sw, int sh) {
        float size = 140;
        float mx = sw - size - 16;
        float my = 16;
        hud.rect(mx - 4, my - 4, size + 8, size + 8, 0.03f, 0.04f, 0.03f, 1f);
        hud.rect(mx, my, size, size, 0.06f, 0.08f, 0.06f, 1f);

        float half = worldSize * 0.5f;
        for (StructureSite s : markers) {
            float u = (s.x + half) / worldSize;
            float v = (s.z + half) / worldSize;
            float px = mx + u * size;
            float py = my + v * size;
            float[] c = colorFor(s.type);
            hud.rect(px - 2, py - 2, 4, 4, c[0], c[1], c[2], 1f);
        }
        // player
        float pu = (playerX + half) / worldSize;
        float pv = (playerZ + half) / worldSize;
        float ppx = mx + pu * size;
        float ppy = my + pv * size;
        hud.rect(ppx - 3, ppy - 3, 6, 6, 0.35f, 1f, 0.45f, 1f);
        hud.text(mx, my + size + 6, "M MAP", 0.3f, 0.8f, 0.4f, 1f);
    }

    private void drawFullMap(Hud hud, int sw, int sh) {
        mapW = Math.min(sw - 80, 640);
        mapH = Math.min(sh - 100, 480);
        mapX = (sw - mapW) * 0.5f;
        mapY = (sh - mapH) * 0.5f;

        hud.rect(0, 0, sw, sh, 0.0f, 0.0f, 0.0f, 0.55f); // dim — alpha ok for overlay
        // solid panel under map
        hud.rect(mapX - 8, mapY - 36, mapW + 16, mapH + 60, 0.04f, 0.05f, 0.04f, 1f);
        hud.text(mapX, mapY - 28, "WORLD MAP  -  click marker to warp  -  M/ESC close",
                0.35f, 1f, 0.45f, 1f);
        hud.rect(mapX, mapY, mapW, mapH, 0.07f, 0.09f, 0.07f, 1f);

        float half = worldSize * 0.5f;
        hovered = -1;
        for (int i = 0; i < markers.size(); i++) {
            StructureSite s = markers.get(i);
            float u = (s.x + half) / worldSize;
            float v = (s.z + half) / worldSize;
            float px = mapX + u * mapW;
            float py = mapY + v * mapH;
            float[] c = colorFor(s.type);
            float sz = 8;
            hud.rect(px - sz * 0.5f, py - sz * 0.5f, sz, sz, c[0], c[1], c[2], 1f);
            hud.text(px + 6, py - 4, s.name, 0.3f, 0.9f, 0.4f, 1f);
        }
        // player
        float pu = (playerX + half) / worldSize;
        float pv = (playerZ + half) / worldSize;
        hud.rect(mapX + pu * mapW - 4, mapY + pv * mapH - 4, 8, 8, 0.35f, 1f, 0.45f, 1f);

        // legend
        float lx = mapX;
        float ly = mapY + mapH + 8;
        hud.text(lx, ly, "TOMB  DUNGEON  TOWN  FORT  SHRINE", 0.25f, 0.7f, 0.35f, 1f);
    }

    /**
     * Click full map. Returns warp target or null.
     */
    public StructureSite handleClick(float mx, float my) {
        if (!fullMapOpen) return null;
        float half = worldSize * 0.5f;
        StructureSite best = null;
        float bestD = 18f * 18f;
        for (StructureSite s : markers) {
            float u = (s.x + half) / worldSize;
            float v = (s.z + half) / worldSize;
            float px = mapX + u * mapW;
            float py = mapY + v * mapH;
            float dx = px - mx, dy = py - my;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private StructureSite nearest() {
        StructureSite best = null;
        float bestD = Float.MAX_VALUE;
        for (StructureSite s : markers) {
            float d = dist(s);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private float dist(StructureSite s) {
        float dx = s.x - playerX, dz = s.z - playerZ;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private String bearing(StructureSite s) {
        float dx = s.x - playerX, dz = s.z - playerZ;
        float ang = (float) Math.toDegrees(Math.atan2(dx, dz));
        float rel = ang - playerYaw;
        while (rel < -180) rel += 360;
        while (rel > 180) rel -= 360;
        if (rel > -22.5f && rel <= 22.5f) return "N";
        if (rel > 22.5f && rel <= 67.5f) return "NE";
        if (rel > 67.5f && rel <= 112.5f) return "E";
        if (rel > 112.5f && rel <= 157.5f) return "SE";
        if (rel > 157.5f || rel <= -157.5f) return "S";
        if (rel > -157.5f && rel <= -112.5f) return "SW";
        if (rel > -112.5f && rel <= -67.5f) return "W";
        return "NW";
    }

    private static String cardinal(float yaw) {
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        if (y >= 337.5f || y < 22.5f) return "N";
        if (y < 67.5f) return "NE";
        if (y < 112.5f) return "E";
        if (y < 157.5f) return "SE";
        if (y < 202.5f) return "S";
        if (y < 247.5f) return "SW";
        if (y < 292.5f) return "W";
        return "NW";
    }

    private static float[] colorFor(StructureSite.Type t) {
        return switch (t) {
            case TOMB -> new float[]{0.7f, 0.7f, 0.65f};
            case DUNGEON -> new float[]{0.5f, 0.25f, 0.55f};
            case TOWN -> new float[]{0.3f, 0.75f, 0.35f};
            case FORT -> new float[]{0.75f, 0.45f, 0.2f};
            case SHRINE -> new float[]{0.35f, 0.55f, 0.9f};
        };
    }
}
