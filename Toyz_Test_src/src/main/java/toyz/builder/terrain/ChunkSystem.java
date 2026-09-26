package toyz.builder.terrain;

import com.raylib.Helpers;
import com.raylib.Raylib.Model;
import com.raylib.Raylib.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase B chunk system — Minetest-inspired.
 * World half-extent ~3072, chunk 64, render 6–8, active 4.
 * Only nearby chunks hold meshes/trees; height stays continuous via V2 noise.
 */
public final class ChunkSystem {
    private ChunkSystem() {}

    /** Tunable settings (pause menu later). */
    public static final class Settings {
        public float worldHalfSize = 3072f;   // Phase B
        public float chunkSize = 64f;
        public int renderChunks = 6;          // 6 → ~384 units view radius
        public int activeChunks = 4;          // physics/mobs
        public int generateChunks = 6;        // emerge radius
        public float unloadHysteresis = 1.5f; // unload beyond render * this
        public float cellSize = 4f;           // mesh density inside chunk
    }

    public static Settings settings = new Settings();

    public static final class Coord {
        public final int cx, cz;
        public Coord(int cx, int cz) { this.cx = cx; this.cz = cz; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Coord)) return false;
            Coord c = (Coord) o;
            return c.cx == cx && c.cz == cz;
        }
        @Override public int hashCode() { return cx * 73856093 ^ cz * 19349663; }
        @Override public String toString() { return cx + "," + cz; }
    }

    public static Coord worldToChunk(float x, float z, float chunkSize) {
        // floor division
        int cx = (int) Math.floor(x / chunkSize);
        int cz = (int) Math.floor(z / chunkSize);
        return new Coord(cx, cz);
    }

    public static float chunkMinX(int cx, float cs) { return cx * cs; }
    public static float chunkMinZ(int cz, float cs) { return cz * cs; }

    /** One loaded terrain tile. */
    public static final class Chunk {
        public final Coord coord;
        public Model grass, sand, rock, dirt, snow;
        public Model terrainFallback;
        public final List<Object> localTrees = new ArrayList<>(); // filled by Terrain as ForestTree
        public final List<Object> localVeg = new ArrayList<>();
        public boolean meshed;
        public long lastSeenMs;

        public Chunk(Coord c) {
            this.coord = c;
            this.lastSeenMs = System.currentTimeMillis();
        }
    }

    public static final class Manager {
        public final Map<Coord, Chunk> loaded = new HashMap<>();
        public Settings cfg = settings;
        public int emergeBudgetPerFrame = 1; // soft limit meshes per frame

        public Coord playerChunk = new Coord(0, 0);

        public void clear() {
            for (Chunk c : loaded.values()) unloadMesh(c);
            loaded.clear();
        }

        private void unloadMesh(Chunk c) {
            // Models freed by engine on exit; mark null to drop refs
            c.grass = c.sand = c.rock = c.dirt = c.snow = c.terrainFallback = null;
            c.localTrees.clear();
            c.localVeg.clear();
            c.meshed = false;
        }

        public void update(float playerX, float playerZ,
                           MeshBuilder builder) {
            playerChunk = worldToChunk(playerX, playerZ, cfg.chunkSize);
            int genR = cfg.generateChunks;
            int rendR = cfg.renderChunks;
            float unloadR = rendR * cfg.unloadHysteresis;

            // Mark needed
            Set<Coord> needed = new HashSet<>();
            for (int dz = -genR; dz <= genR; dz++) {
                for (int dx = -genR; dx <= genR; dx++) {
                    if (dx * dx + dz * dz > genR * genR + 1) continue;
                    needed.add(new Coord(playerChunk.cx + dx, playerChunk.cz + dz));
                }
            }

            // Unload far
            Iterator<Map.Entry<Coord, Chunk>> it = loaded.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Coord, Chunk> e = it.next();
                int dx = e.getKey().cx - playerChunk.cx;
                int dz = e.getKey().cz - playerChunk.cz;
                if (dx * dx + dz * dz > unloadR * unloadR) {
                    unloadMesh(e.getValue());
                    it.remove();
                }
            }

            // Emerge missing (budget)
            int budget = emergeBudgetPerFrame;
            for (Coord c : needed) {
                if (budget <= 0) break;
                Chunk ch = loaded.get(c);
                if (ch == null) {
                    ch = new Chunk(c);
                    loaded.put(c, ch);
                }
                if (!ch.meshed && builder != null) {
                    builder.build(ch);
                    ch.meshed = true;
                    ch.lastSeenMs = System.currentTimeMillis();
                    budget--;
                }
            }
        }

        public boolean inRenderRange(Coord c) {
            int dx = c.cx - playerChunk.cx;
            int dz = c.cz - playerChunk.cz;
            int r = cfg.renderChunks;
            return dx * dx + dz * dz <= r * r + 1;
        }

        public int loadedCount() { return loaded.size(); }
    }

    /** Callback from Terrain to fill chunk meshes. */
    public interface MeshBuilder {
        void build(Chunk chunk);
    }
}
