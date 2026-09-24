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
                        // type pos.xyz rotY rotX rotZ color length
                        v = rest.split("\\s+");
                        Piece.PlacedPiece p = new Piece.PlacedPiece();
                        p.type = Piece.PieceType.values()[Integer.parseInt(v[0])];
                        p.position = Helpers.newVector3(Float.parseFloat(v[1]),
                            Float.parseFloat(v[2]), Float.parseFloat(v[3]));
                        p.rotationY = Float.parseFloat(v[4]);
                        p.rotationX = Float.parseFloat(v[5]);
                        p.rotationZ = Float.parseFloat(v[6]);
                        p.color = Piece.PieceColor.values()[Integer.parseInt(v[7])];
                        p.length = Piece.PieceLength.values()[Integer.parseInt(v[8])];
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
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(filename)))) {
            out.println("# Toyz Builder Map File");
            out.println("NAME " + map.name);
            out.println("TYPE " + map.type.ordinal());
            out.println("SPAWN " + map.spawnPoint.x() + " " + map.spawnPoint.y() + " " + map.spawnPoint.z());
            out.println("SKYBOX " + map.skybox);
            out.println("AMBIENT_COLOR " + (map.ambientColor.r() & 0xFF) + " " + (map.ambientColor.g() & 0xFF)
                        + " " + (map.ambientColor.b() & 0xFF) + " " + (map.ambientColor.a() & 0xFF));
            out.println("AMBIENT_INTENSITY " + map.ambientIntensity);
            out.println("HAS_CEILING " + (map.hasCeiling ? 1 : 0));
            out.println("CEILING_HEIGHT " + map.ceilingHeight);
            for (Piece.PlacedPiece p : map.pieces) {
                out.println("PIECE " + p.type.ordinal() + " "
                    + p.position.x() + " " + p.position.y() + " " + p.position.z() + " "
                    + p.rotationY + " " + p.rotationX + " " + p.rotationZ + " "
                    + p.color.ordinal() + " " + p.length.ordinal());
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void createNewMap(String name, MapType type) {
        Map map = new Map();
        map.name = name;
        map.type = type;
        map.spawnPoint = Helpers.newVector3(0, 1, 0);
        
        // Fallback check for skybox file existence
        String targetSkybox = "day1";
        File skyboxFile = new File("assets/skyboxes/" + targetSkybox + ".jpg");
        if (!skyboxFile.exists()) {
            System.err.println("Warning: Target skybox '" + targetSkybox + ".jpg' not found. Falling back to day1.");
            map.skybox = "day1";
        } else {
            map.skybox = targetSkybox;
        }

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

        currentMapIndex = 0;
    }
}