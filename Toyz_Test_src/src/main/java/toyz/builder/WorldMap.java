package toyz.builder;

import com.raylib.Helpers;

import java.util.ArrayList;
import java.util.List;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

/**
 * Elder Scrolls–style navigation: compass mini-map + full world map + fast travel.
 * Markers are filled when structures generate; click a marker on the full map to warp.
 */
public final class WorldMap {
    private WorldMap() {}

    public enum MarkerKind {
        PLAYER, FORT, TOWN, TOMB, SHRINE, DUNGEON, OBELISK, TEMPLE, BRIDGE, OTHER
    }

    public static final class Marker {
        public String name;
        public MarkerKind kind;
        public float x, z;
        public boolean discovered = true; // all known for now (explore fog later)
        public boolean fastTravel = true;

        public Marker(String name, MarkerKind kind, float x, float z) {
            this.name = name;
            this.kind = kind;
            this.x = x;
            this.z = z;
        }
    }

    public static final class State {
        public boolean fullMapOpen = false;
        public final List<Marker> markers = new ArrayList<>();
        /** World units shown on full map radius. */
        public float mapRadius = 400f;
        public float panX = 0f, panZ = 0f;
        public String status = "";
        public float statusTimer = 0f;
    }

    public static void clear(State s) {
        if (s == null) return;
        s.markers.clear();
        s.panX = 0f;
        s.panZ = 0f;
        s.status = "";
    }

    public static void addMarker(State s, String name, MarkerKind kind, float x, float z) {
        if (s == null) return;
        // de-dupe near same spot
        for (Marker m : s.markers) {
            float dx = m.x - x, dz = m.z - z;
            if (dx * dx + dz * dz < 36f && m.kind == kind) {
                m.name = name;
                return;
            }
        }
        s.markers.add(new Marker(name, kind, x, z));
    }

    public static void registerFromResult(State s, StructureGenerator.Result r) {
        if (s == null || r == null || r.markers == null) return;
        for (Marker m : r.markers) addMarker(s, m.name, m.kind, m.x, m.z);
    }

    public static void registerObelisks(State s, Terrain.ForestTerrain forest) {
        if (s == null || forest == null || forest.obelisks == null) return;
        int i = 0;
        for (toyz.builder.terrain.RavineGenerator.PortalObelisk o : forest.obelisks) {
            addMarker(s, "Obelisk " + (++i), MarkerKind.OBELISK, o.x, o.z);
        }
    }

    private static Color kindColor(MarkerKind k) {
        switch (k) {
            case FORT: return Helpers.newColor(200, 160, 80, 255);
            case TOWN: return Helpers.newColor(120, 200, 120, 255);
            case TOMB: case DUNGEON: return Helpers.newColor(160, 100, 200, 255);
            case SHRINE: return Helpers.newColor(220, 220, 100, 255);
            case OBELISK: return Helpers.newColor(180, 100, 255, 255);
            case TEMPLE: return Helpers.newColor(100, 180, 220, 255);
            case BRIDGE: return Helpers.newColor(140, 140, 140, 255);
            case PLAYER: return Helpers.newColor(80, 255, 120, 255);
            default: return Helpers.newColor(200, 200, 200, 255);
        }
    }

    private static String kindGlyph(MarkerKind k) {
        switch (k) {
            case FORT: return "F";
            case TOWN: return "T";
            case TOMB: return "M"; // tomb
            case DUNGEON: return "D";
            case SHRINE: return "S";
            case OBELISK: return "O";
            case TEMPLE: return "P";
            case BRIDGE: return "B";
            default: return "•";
        }
    }

    /** Top-right compass mini-map. */
    public static void drawMinimap(State s, Terrain.ForestTerrain forest, Player player, float yawDeg) {
        if (s == null || forest == null || player == null) return;
        int sw = GetScreenWidth();
        float size = 148f;
        float pad = 12f;
        float mx = sw - size - pad;
        float my = pad + 28f;
        Rectangle panel = Helpers.newRectangle(mx, my, size, size);
        DrawRectangleRec(panel, Helpers.newColor(12, 14, 18, 200));
        DrawRectangleLinesEx(panel, 2f, UI.phosphor());

        float worldR = 120f; // world units radius on mini-map
        float cx = mx + size * 0.5f;
        float cy = my + size * 0.5f;
        float px = player.position.x();
        float pz = player.position.z();

        // grid
        DrawCircleLines((int) cx, (int) cy, size * 0.42f, Helpers.newColor(40, 80, 50, 180));
        DrawCircleLines((int) cx, (int) cy, size * 0.22f, Helpers.newColor(40, 80, 50, 100));

        for (Marker m : s.markers) {
            if (!m.discovered) continue;
            float dx = m.x - px;
            float dz = m.z - pz;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > worldR * 1.15f) continue;
            float sx = cx + (dx / worldR) * (size * 0.42f);
            float sy = cy + (dz / worldR) * (size * 0.42f);
            Color col = kindColor(m.kind);
            DrawCircle((int) sx, (int) sy, 3.5f, col);
        }

        // player + facing
        DrawCircle((int) cx, (int) cy, 4f, UI.phosphor());
        float rad = (float) Math.toRadians(yawDeg);
        float fx = cx + (float) Math.sin(rad) * 14f;
        float fy = cy - (float) Math.cos(rad) * 14f;
        DrawLineEx(Helpers.newVector2(cx, cy), Helpers.newVector2(fx, fy), 2f, UI.phosphor());

        DrawText("MAP [M]", (int) mx + 6, (int) (my + size - 16), 12, UI.phosphorDim());
        DrawText("N", (int) cx - 4, (int) my + 4, 12, UI.phosphorDim());
    }

    /**
     * Full map overlay. Returns true if a fast-travel was requested (sets outX/outZ).
     */
    public static boolean drawFullMap(State s, Terrain.ForestTerrain forest, Player player,
                                      float[] outXZ) {
        if (s == null || !s.fullMapOpen || forest == null || player == null) return false;
        int sw = GetScreenWidth(), sh = GetScreenHeight();
        DrawRectangle(0, 0, sw, sh, Helpers.newColor(0, 0, 0, 180));

        float panelW = Math.min(720, sw - 80f);
        float panelH = Math.min(520, sh - 80f);
        float px0 = (sw - panelW) * 0.5f;
        float py0 = (sh - panelH) * 0.5f;
        Rectangle panel = Helpers.newRectangle(px0, py0, panelW, panelH);
        UI.drawSteelPanel(panel, "WORLD MAP");

        float mapPad = 36f;
        float mapX = px0 + mapPad;
        float mapY = py0 + 48f;
        float mapW = panelW - mapPad * 2;
        float mapH = panelH - 100f;
        DrawRectangle((int) mapX, (int) mapY, (int) mapW, (int) mapH, Helpers.newColor(18, 22, 28, 255));
        DrawRectangleLinesEx(Helpers.newRectangle(mapX, mapY, mapW, mapH), 1.5f, UI.phosphorDim());

        float radius = s.mapRadius;
        float centerX = player.position.x() + s.panX;
        float centerZ = player.position.z() + s.panZ;
        float midX = mapX + mapW * 0.5f;
        float midY = mapY + mapH * 0.5f;
        float scale = (Math.min(mapW, mapH) * 0.45f) / radius;

        // cardinal
        DrawText("N", (int) midX - 4, (int) mapY + 6, 14, UI.phosphor());
        DrawText("S", (int) midX - 4, (int) (mapY + mapH - 18), 14, UI.phosphorDim());
        DrawText("W", (int) mapX + 8, (int) midY - 6, 14, UI.phosphorDim());
        DrawText("E", (int) (mapX + mapW - 16), (int) midY - 6, 14, UI.phosphorDim());

        Vector2 mouse = GetMousePosition();
        Marker hover = null;
        float bestD = 14f;

        for (Marker m : s.markers) {
            if (!m.discovered) continue;
            float sx = midX + (m.x - centerX) * scale;
            float sy = midY + (m.z - centerZ) * scale;
            if (sx < mapX + 4 || sx > mapX + mapW - 4 || sy < mapY + 4 || sy > mapY + mapH - 4)
                continue;
            Color col = kindColor(m.kind);
            DrawCircle((int) sx, (int) sy, 6f, col);
            DrawText(kindGlyph(m.kind), (int) sx - 4, (int) sy - 5, 12, BLACK);
            float ddx = mouse.x() - sx, ddy = mouse.y() - sy;
            float dd = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (dd < bestD) {
                bestD = dd;
                hover = m;
            }
        }

        // player
        float psx = midX + (player.position.x() - centerX) * scale;
        float psy = midY + (player.position.z() - centerZ) * scale;
        DrawCircle((int) psx, (int) psy, 5f, UI.phosphor());
        DrawText("You", (int) psx + 8, (int) psy - 6, 12, UI.phosphor());

        // legend + status
        float ly = py0 + panelH - 44;
        DrawText("F Fort  T Town  M Tomb  S Shrine  D Dungeon  O Obelisk  |  Click marker = Fast Travel  |  M/Esc close",
                (int) mapX, (int) ly, 13, UI.phosphorDim());
        if (hover != null) {
            DrawText(hover.name + "  (" + (int) hover.x + ", " + (int) hover.z + ")",
                    (int) mapX, (int) (ly - 18), 14, UI.phosphor());
        }
        if (s.statusTimer > 0f && s.status != null && !s.status.isEmpty()) {
            DrawText(s.status, (int) mapX, (int) (mapY + 8), 14, UI.phosphor());
        }

        // zoom
        float wheel = GetMouseWheelMove();
        if (wheel != 0f) {
            s.mapRadius = Math.max(80f, Math.min(1200f, s.mapRadius - wheel * 40f));
        }

        boolean travel = false;
        if (hover != null && hover.fastTravel && IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            outXZ[0] = hover.x;
            outXZ[1] = hover.z;
            travel = true;
            s.status = "Fast travel → " + hover.name;
            s.statusTimer = 2.5f;
            s.fullMapOpen = false;
        }
        return travel;
    }

    public static void update(State s, float dt) {
        if (s == null) return;
        if (s.statusTimer > 0f) s.statusTimer -= dt;
    }
}
