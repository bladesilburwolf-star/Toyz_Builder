package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Port of map.h / map.cpp — multiple maps, text save/load, defaults. */
public final class MapSystem {

    public enum MapType { OUTDOOR, INDOOR, CAVE }

    public static class Map {
        public String name = "";
        public MapType type = MapType.OUTDOOR;
        public List<Piece.PlacedPiece> pieces = new ArrayList<>();
        public Vector3 spawnPoint = Helpers.newVector3(0, 1, 0);
        public String skybox = "day1";
        public Color ambientColor = Helpers.newColor(40, 44, 52, 255);
        public float ambientIntensity = 0.4f;
        public boolean hasCeiling = false;
        public float ceilingHeight = 0.0f;
    }

    public List<Map> maps = new ArrayList<>();
    public int currentMapIndex = -1;
    public static final String MAPS_DIR = "maps";

    public static void ensureMapsDir() {
        try { Files.createDirectories(Paths.get(MAPS_DIR)); }
        catch (IOException e) { System.err.println("[Map] mkdir failed: " + e.getMessage()); }
    }

    public static String fileNameFor(String mapName) {
        String n = (mapName == null || mapName.isEmpty()) ? "untitled" : mapName;
        n = n.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim().replace(' ', '_');
        if (n.isEmpty()) n = "untitled";
        return MAPS_DIR + "/" + n + ".map";
    }

    public static boolean isDynamicType(Piece.PieceType t) {
        return t == Piece.PieceType.Boulder || t == Piece.PieceType.TeeterTotter;
    }

    /** Procedural open-world architecture settings. Values are intentionally exposed so the
     * player/editor can later override scale, density and style without changing generators. */
    public static class StructureSettings {
        public boolean enabled = true;
        public float globalScale = 1.0f;
        public int maxStructures = 9;
        public float minSpacing = 55f;
        public float villageChance = 0.55f;
        public float houseScaleMin = 0.85f;
        public float houseScaleMax = 1.45f;
        public int wallLevels = 3;
        public boolean includeRoofs = true;
        public boolean includeDoorsWindows = true;
    }

    private static float structureHash(int x, int z, int seed) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        return (float)((h & 0x7fffffffL) / (double)0x80000000L);
    }

    private static Piece.PlacedPiece structurePiece(Piece.PieceType type, float x, float y, float z, float rot, Piece.PieceColor color) {
        Piece.PlacedPiece p = new Piece.PlacedPiece();
        p.type = type; p.position = Helpers.newVector3(x, y, z); p.rotationY = rot; p.color = color;
        p.length = Piece.PieceLength.MED;
        return p;
    }

    private static void addHouse(List<Piece.PlacedPiece> out, float cx, float cz, float ground, float scale, int seed, boolean details) {
        int halfW = Math.max(3, Math.round(4 * scale));
        int halfD = Math.max(3, Math.round(3 * scale));
        int levels = Math.max(2, Math.round(3 * scale));
        float y0 = ground + 0.5f;
        // Plank walls: perimeter is built from the new door-thickness plank pieces.
        for (int level = 0; level < levels; level++) {
            float y = y0 + level * 1.0f;
            for (int x = -halfW; x <= halfW; x++) {
                if (details && level == 0 && Math.abs(x) <= 1) continue;
                out.add(structurePiece(Piece.PieceType.Plank, cx + x * 0.7f, y, cz - halfD * 0.7f, 0, Piece.PieceColor.Walnut));
                out.add(structurePiece(Piece.PieceType.Plank, cx + x * 0.7f, y, cz + halfD * 0.7f, 0, Piece.PieceColor.Walnut));
            }
            for (int z = -halfD + 1; z < halfD; z++) {
                out.add(structurePiece(Piece.PieceType.Plank, cx - halfW * 0.7f, y, cz + z * 0.7f, 90, Piece.PieceColor.Pine));
                out.add(structurePiece(Piece.PieceType.Plank, cx + halfW * 0.7f, y, cz + z * 0.7f, 90, Piece.PieceColor.Pine));
            }
        }
        // Corner posts and top beam language makes the generated building read as a log/plank structure.
        for (int x : new int[]{-halfW, halfW}) for (int z : new int[]{-halfD, halfD}) {
            out.add(structurePiece(Piece.PieceType.LogVert, cx + x * 0.7f, y0 + levels * 0.5f, cz + z * 0.7f, 0, Piece.PieceColor.Oak));
        }
        if (details) {
            out.add(structurePiece(Piece.PieceType.Door, cx, y0 + 0.5f, cz - halfD * 0.7f - 0.08f, 0, Piece.PieceColor.Walnut));
            out.add(structurePiece(Piece.PieceType.Window, cx - halfW * 0.7f, y0 + 1.5f, cz, 90, Piece.PieceColor.White));
            out.add(structurePiece(Piece.PieceType.Window, cx + halfW * 0.7f, y0 + 1.5f, cz, 90, Piece.PieceColor.White));
        }
        if (true) {
            float roofY = y0 + levels + 0.35f;
            for (int x = -halfW; x <= halfW; x++) {
                out.add(structurePiece(Piece.PieceType.Roof, cx + x * 0.75f, roofY, cz - 0.55f, 0, Piece.PieceColor.Natural));
                out.add(structurePiece(Piece.PieceType.Roof, cx + x * 0.75f, roofY + 0.45f, cz + 0.55f, 180, Piece.PieceColor.Natural));
            }
            out.add(structurePiece(Piece.PieceType.RoofPeak, cx, roofY + 0.55f, cz, 0, Piece.PieceColor.Natural));
        }
    }

    private static void addTower(List<Piece.PlacedPiece> out, float cx, float cz, float ground, float scale) {
        int height = Math.max(4, Math.round(6 * scale));
        int radius = Math.max(2, Math.round(2 * scale));
        for (int y = 0; y < height; y++) {
            out.add(structurePiece(Piece.PieceType.Plank, cx - radius * 0.7f, ground + 0.5f + y, cz, 90, Piece.PieceColor.Oak));
            out.add(structurePiece(Piece.PieceType.Plank, cx + radius * 0.7f, ground + 0.5f + y, cz, 90, Piece.PieceColor.Oak));
            out.add(structurePiece(Piece.PieceType.Plank, cx, ground + 0.5f + y, cz - radius * 0.7f, 0, Piece.PieceColor.Pine));
            out.add(structurePiece(Piece.PieceType.Plank, cx, ground + 0.5f + y, cz + radius * 0.7f, 0, Piece.PieceColor.Pine));
        }
        float top = ground + height + 0.6f;
        out.add(structurePiece(Piece.PieceType.PlankWide, cx, top, cz, 0, Piece.PieceColor.Oak));
        out.add(structurePiece(Piece.PieceType.RoofPeak, cx, top + 0.8f, cz, 0, Piece.PieceColor.Natural));
    }

    public static List<Piece.PlacedPiece> generateStructures(Terrain.ForestTerrain terrain, StructureSettings cfg) {
        List<Piece.PlacedPiece> out = new ArrayList<>();
        if (terrain == null || cfg == null || !cfg.enabled || !terrain.structuresEnabled) return out;
        int seed = terrain.seed;
        float scale = Math.max(0.65f, Math.min(2.0f, cfg.globalScale));
        int made = 0;
        // A deterministic settlement ring keeps the spawn area readable while making each seed distinct.
        for (int i = 0; i < cfg.maxStructures && made < cfg.maxStructures; i++) {
            float angle = structureHash(i, 17, seed) * 6.2831853f;
            float dist = cfg.minSpacing + structureHash(i, 29, seed) * 115f;
            float x = (float)Math.cos(angle) * dist;
            float z = (float)Math.sin(angle) * dist;
            float ground = Terrain.getTerrainHeight(terrain, x, z);
            if (ground < terrain.waterLevel + 0.8f) continue;
            float localScale = scale * (cfg.houseScaleMin + structureHash(i, 41, seed) * (cfg.houseScaleMax - cfg.houseScaleMin));
            int kind = (int)(structureHash(i, 53, seed) * 4f);
            if (kind == 3 && structureHash(i, 61, seed) > cfg.villageChance) kind = 0;
            if (kind == 0 || kind == 1) addHouse(out, x, z, ground, localScale, seed + i, cfg.includeDoorsWindows);
            else if (kind == 2) addTower(out, x, z, ground, localScale);
            else addHouse(out, x, z, ground, localScale * 1.35f, seed + i, true);
            made++;
        }
        return out;
    }

    private static final MapSystem INSTANCE = new MapSystem();
    public static MapSystem get() { return INSTANCE; }

    public void addMap(Map map) { maps.add(map); }

    public Map getCurrentMap() {
        if (currentMapIndex >= 0 && currentMapIndex < maps.size()) {
            return maps.get(currentMapIndex);
        }
        return null;
    }

    public boolean loadMap(String filename) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            Map map = new Map();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] tok = line.trim().split("\\s+", 2);
                String key = tok[0];
                String rest = tok.length > 1 ? tok[1].trim() : "";
                String[] v;
                switch (key) {
                    case "NAME": map.name = rest; break;
                    case "TYPE": map.type = MapType.values()[Integer.parseInt(rest)]; break;
                    case "SPAWN":
                        v = rest.split("\\s+");
                        map.spawnPoint = Helpers.newVector3(Float.parseFloat(v[0]),
                            Float.parseFloat(v[1]), Float.parseFloat(v[2]));
                        break;
                    case "SKYBOX": map.skybox = rest; break;
                    case "AMBIENT_COLOR":
                        v = rest.split("\\s+");
                        map.ambientColor = Helpers.newColor(Integer.parseInt(v[0]),
                            Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3]));
                        break;
                    case "AMBIENT_INTENSITY": map.ambientIntensity = Float.parseFloat(rest); break;
                    case "HAS_CEILING": map.hasCeiling = Integer.parseInt(rest) != 0; break;
                    case "CEILING_HEIGHT": map.ceilingHeight = Float.parseFloat(rest); break;
                    case "PIECE":
                        // type pos.xyz rotY rotX rotZ color length [dynamic]
                        v = rest.split("\s+");
                        Piece.PlacedPiece p = new Piece.PlacedPiece();
                        p.type = Piece.PieceType.values()[Integer.parseInt(v[0])];
                        p.position = Helpers.newVector3(Float.parseFloat(v[1]),
                            Float.parseFloat(v[2]), Float.parseFloat(v[3]));
                        p.rotationY = Float.parseFloat(v[4]);
                        p.rotationX = Float.parseFloat(v[5]);
                        p.rotationZ = Float.parseFloat(v[6]);
                        p.color = Piece.PieceColor.values()[Integer.parseInt(v[7])];
                        p.length = Piece.PieceLength.values()[Integer.parseInt(v[8])];
                        if (v.length > 9) p.dynamic = Integer.parseInt(v[9]) != 0;
                        else p.dynamic = isDynamicType(p.type);
                        if (p.dynamic) p.grounded = false;
                        map.pieces.add(p);
                        break;
                }
            }
            maps.add(map);
            currentMapIndex = maps.size() - 1;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean saveMap(String filename) {
        if (currentMapIndex < 0 || currentMapIndex >= maps.size()) return false;
        Map map = maps.get(currentMapIndex);
        ensureMapsDir();
        if (filename == null || filename.isEmpty()) filename = fileNameFor(map.name);
        try {
            Path path = Paths.get(filename);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path))) {
                out.println("# Toyz Builder Map File v2");
                out.println("NAME " + map.name);
                out.println("TYPE " + map.type.ordinal());
                out.println("SPAWN " + map.spawnPoint.x() + " " + map.spawnPoint.y() + " " + map.spawnPoint.z());
                out.println("SKYBOX " + map.skybox);
                out.println("AMBIENT_COLOR " + (map.ambientColor.r() & 0xFF) + " " + (map.ambientColor.g() & 0xFF)
                            + " " + (map.ambientColor.b() & 0xFF) + " " + (map.ambientColor.a() & 0xFF));
                out.println("AMBIENT_INTENSITY " + map.ambientIntensity);
                out.println("HAS_CEILING " + (map.hasCeiling ? 1 : 0));
                out.println("CEILING_HEIGHT " + map.ceilingHeight);
                for (Piece.PlacedPiece pp : map.pieces) {
                    out.println("PIECE " + pp.type.ordinal() + " "
                        + pp.position.x() + " " + pp.position.y() + " " + pp.position.z() + " "
                        + pp.rotationY + " " + pp.rotationX + " " + pp.rotationZ + " "
                        + pp.color.ordinal() + " " + pp.length.ordinal() + " "
                        + (pp.dynamic || isDynamicType(pp.type) ? 1 : 0));
                }
            }
            System.out.println("[Map] saved " + filename + " (" + map.pieces.size() + " pieces)");
            return true;
        } catch (IOException e) {
            System.err.println("[Map] save failed: " + e.getMessage());
            return false;
        }
    }

    public boolean saveCurrent() {
        Map m = getCurrentMap();
        if (m == null) return false;
        return saveMap(fileNameFor(m.name));
    }

    public static List<String> listSavedMapFiles() {
        ensureMapsDir();
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(MAPS_DIR), "*.map")) {
            for (Path f : ds) out.add(f.toString());
        } catch (IOException ignored) {}
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public void createNewMap(String name, MapType type) {
        Map map = new Map();
        map.name = name;
        map.type = type;
        map.spawnPoint = Helpers.newVector3(0, 1, 0);
        map.skybox = "day1";
        map.ambientColor = Helpers.newColor(40, 44, 52, 255);
        map.ambientIntensity = 0.4f;
        map.hasCeiling = (type == MapType.INDOOR);
        map.ceilingHeight = (type == MapType.INDOOR) ? 10f : 0f;
        maps.add(map);
        currentMapIndex = maps.size() - 1;
    }

    public void switchToMap(int index) {
        if (index >= 0 && index < maps.size()) currentMapIndex = index;
    }

    public void switchToMap(String name) {
        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).name.equals(name)) { currentMapIndex = i; return; }
        }
    }

    public List<String> getMapList() {
        List<String> names = new ArrayList<>();
        for (Map m : maps) names.add(m.name);
        return names;
    }

    public int getMapCount() { return maps.size(); }

    public void createDefaultMaps() {
        // Outdoor forest map
        Map outdoor = new Map();
        outdoor.name = "Forest Map";
        outdoor.type = MapType.OUTDOOR;
        outdoor.skybox = "day1";
        {
            Piece.PlacedPiece p = new Piece.PlacedPiece();
            p.type = Piece.PieceType.StraightLog; p.position = Helpers.newVector3(0, 0.25f, 0); p.rotationY = 0;
            outdoor.pieces.add(p);
            Piece.PlacedPiece p2 = new Piece.PlacedPiece();
            p2.type = Piece.PieceType.StraightLog; p2.position = Helpers.newVector3(1, 0.25f, 0); p2.rotationY = 90;
            outdoor.pieces.add(p2);
            Piece.PlacedPiece p3 = new Piece.PlacedPiece();
            p3.type = Piece.PieceType.MagnetixBall; p3.position = Helpers.newVector3(0.5f, 0.5f, 0.5f);
            p3.color = Piece.PieceColor.White;
            outdoor.pieces.add(p3);
        }
        maps.add(outdoor);

        // Indoor map
        Map indoor = new Map();
        indoor.name = "House Interior";
        indoor.type = MapType.INDOOR;
        indoor.skybox = "overcast1";
        indoor.ambientColor = Helpers.newColor(80, 60, 40, 255);
        indoor.ambientIntensity = 0.6f;
        indoor.hasCeiling = true;
        indoor.ceilingHeight = 5f;
        {
            Piece.PlacedPiece p = new Piece.PlacedPiece();
            p.type = Piece.PieceType.StraightLog; p.position = Helpers.newVector3(0, 0.25f, 0); p.rotationY = 0;
            indoor.pieces.add(p);
            Piece.PlacedPiece p2 = new Piece.PlacedPiece();
            p2.type = Piece.PieceType.StraightLog; p2.position = Helpers.newVector3(-2, 0.25f, 0); p2.rotationY = 90;
            indoor.pieces.add(p2);
            Piece.PlacedPiece p3 = new Piece.PlacedPiece();
            p3.type = Piece.PieceType.StraightLog; p3.position = Helpers.newVector3(2, 0.25f, 0); p3.rotationY = 90;
            indoor.pieces.add(p3);
            Piece.PlacedPiece p4 = new Piece.PlacedPiece();
            p4.type = Piece.PieceType.StraightLog; p4.position = Helpers.newVector3(0, 0.25f, -2); p4.rotationY = 0;
            indoor.pieces.add(p4);
        }
        maps.add(indoor);

        // Cave map
        Map cave = new Map();
        cave.name = "Underground Cave";
        cave.type = MapType.CAVE;
        cave.skybox = "night1";
        cave.ambientColor = Helpers.newColor(20, 20, 30, 255);
        cave.ambientIntensity = 0.3f;
        cave.hasCeiling = true;
        cave.ceilingHeight = 8f;
        {
            Piece.PlacedPiece p = new Piece.PlacedPiece();
            p.type = Piece.PieceType.MagnetixBall; p.position = Helpers.newVector3(0, 0.5f, 0);
            p.color = Piece.PieceColor.White;
            cave.pieces.add(p);
            Piece.PlacedPiece p2 = new Piece.PlacedPiece();
            p2.type = Piece.PieceType.MagnetixRod; p2.position = Helpers.newVector3(1, 0.5f, 0);
            p2.color = Piece.PieceColor.Red;
            cave.pieces.add(p2);
        }
        maps.add(cave);
        maps.add(buildPhysicsDebugMap());
        currentMapIndex = 0;
    }

    public static Map buildPhysicsDebugMap() {
        Map m = new Map();
        m.name = "Physics_Debug_Room";
        m.type = MapType.INDOOR;
        m.skybox = "overcast1";
        m.ambientColor = Helpers.newColor(50, 55, 70, 255);
        m.ambientIntensity = 0.55f;
        m.hasCeiling = true;
        m.ceilingHeight = 12f;
        m.spawnPoint = Helpers.newVector3(0, 1.2f, 6);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                Piece.PlacedPiece f = new Piece.PlacedPiece();
                f.type = Piece.PieceType.BlockStone;
                f.position = Helpers.newVector3(x * 1.0f, 0.5f, z * 1.0f);
                m.pieces.add(f);
            }
        }
        for (int x = -5; x <= -3; x++) {
            for (int z = -2; z <= 2; z++) {
                Piece.PlacedPiece f = new Piece.PlacedPiece();
                f.type = Piece.PieceType.BlockWood;
                f.position = Helpers.newVector3(x * 1.0f, 2.5f, z * 1.0f);
                m.pieces.add(f);
            }
        }
        Piece.PlacedPiece ramp = new Piece.PlacedPiece();
        ramp.type = Piece.PieceType.Ramp;
        ramp.position = Helpers.newVector3(-2.0f, 1.0f, 0f);
        ramp.rotationY = 90;
        m.pieces.add(ramp);
        Piece.PlacedPiece teeter = new Piece.PlacedPiece();
        teeter.type = Piece.PieceType.TeeterTotter;
        teeter.position = Helpers.newVector3(0f, 1.2f, -2f);
        teeter.dynamic = true;
        m.pieces.add(teeter);
        Piece.PlacedPiece ful = new Piece.PlacedPiece();
        ful.type = Piece.PieceType.BlockMetal;
        ful.position = Helpers.newVector3(0f, 0.5f, -2f);
        m.pieces.add(ful);
        Piece.PlacedPiece slide = new Piece.PlacedPiece();
        slide.type = Piece.PieceType.Slide;
        slide.position = Helpers.newVector3(3.5f, 2.0f, 0f);
        slide.rotationY = -20;
        m.pieces.add(slide);
        float[][] bpos = { {-4.5f, 4.5f, 0f}, {-4.0f, 5.2f, 1f}, {2f, 3.5f, -3f}, {4f, 4.0f, 2f} };
        for (float[] bp : bpos) {
            Piece.PlacedPiece b = new Piece.PlacedPiece();
            b.type = Piece.PieceType.Boulder;
            b.position = Helpers.newVector3(bp[0], bp[1], bp[2]);
            b.dynamic = true;
            b.grounded = false;
            m.pieces.add(b);
        }
        Piece.PieceType[] mats = {
            Piece.PieceType.BlockWood, Piece.PieceType.BlockStone, Piece.PieceType.BlockMetal,
            Piece.PieceType.BlockSand, Piece.PieceType.BlockGlass
        };
        for (int i = 0; i < mats.length; i++) {
            Piece.PlacedPiece b = new Piece.PlacedPiece();
            b.type = mats[i];
            b.position = Helpers.newVector3(-2f + i * 1.1f, 0.5f, 5f);
            m.pieces.add(b);
        }
        System.out.println("[Map] Physics_Debug_Room (" + m.pieces.size() + " pieces)");
        return m;
    }
}
