package com.toyzbuilder.world;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapFile {

    public static float[] playerStart;

    private static final Pattern THING = Pattern.compile(
            "^(\\S+)\\s+(-?[\\d.]+)\\s+(-?[\\d.]+)\\s+(-?[\\d.]+)\\s+(SOLID|NOSOLID)"
                    + "(?:\\s+YAW\\s+(-?[\\d.]+))?"
                    + "(?:\\s+AREA\\s+([\\d.]+))?(?:\\s+\"([^\"]*)\")?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private MapFile() {}

    public static void save(File f, List<Entity> ents, float px, float py, float pz) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        try (PrintWriter w = new PrintWriter(new FileWriter(f))) {
            w.println("# TOYZ WORLDGEN MAP v2");
            w.printf("PLAYER %.2f %.2f %.2f%n", px, py, pz);
            w.println("BEGIN_THINGS");
            for (Entity e : ents) {
                String safeText = e.text == null ? "" : e.text.replace("\"", "'");
                w.printf("%s %.2f %.2f %.2f %s YAW %.0f AREA %.1f \"%s\"%n",
                        e.category, e.x, e.y, e.z,
                        e.solid ? "SOLID" : "NOSOLID",
                        e.yaw,
                        e.areaRadius, safeText);
            }
            w.println("END_THINGS");
        }
    }

    public static List<Entity> load(File f) throws IOException {
        List<Entity> out = new ArrayList<>();
        playerStart = null;
        boolean inThings = false;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (t.equalsIgnoreCase("BEGIN_THINGS")) { inThings = true; continue; }
                if (t.equalsIgnoreCase("END_THINGS")) { inThings = false; continue; }
                if (t.toUpperCase().startsWith("PLAYER ")) {
                    String[] p = t.split("\\s+");
                    if (p.length >= 4) {
                        try {
                            playerStart = new float[]{
                                    Float.parseFloat(p[1]),
                                    Float.parseFloat(p[2]),
                                    Float.parseFloat(p[3])};
                        } catch (NumberFormatException ignored) {}
                    }
                    continue;
                }
                if (!inThings) continue;
                Matcher m = THING.matcher(t);
                if (m.find()) {
                    Entity e = new Entity(m.group(1),
                            Float.parseFloat(m.group(2)),
                            Float.parseFloat(m.group(3)),
                            Float.parseFloat(m.group(4)),
                            m.group(5).equalsIgnoreCase("SOLID"));
                    if (m.group(6) != null) e.yaw = Float.parseFloat(m.group(6));
                    if (m.group(7) != null) e.areaRadius = Float.parseFloat(m.group(7));
                    if (m.group(8) != null) e.text = m.group(8);
                    out.add(e);
                }
            }
        }
        return out;
    }
}
