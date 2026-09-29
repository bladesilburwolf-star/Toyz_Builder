package com.toyzbuilder.world;

import com.toyzbuilder.engine.Mesh;
import com.toyzbuilder.engine.PrimitiveMeshes;
import com.toyzbuilder.world.WorldGenerator.Biome;
import com.toyzbuilder.world.WorldGenerator.Result;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Sparse procedural landmarks: tombs, dungeons, towns, forts, shrines.
 * All structural geometry (floors, walls, ceilings, pillars) is SOLID and
 * fed into Collision via rebuildFromStructures(); doorways stay open.
 * Dungeons are branching room trees with warp pads (entrance <-> depths).
 * HD 6450: shared unit-cube instances only.
 */
public final class StructureGenerator {

    public static final class Part {
        public float x, y, z, sx, sy, sz, yaw;
        public float r, g, b;
        /** Solid parts get AABB collision; decorative parts do not. */
        public boolean solid;
        /**
         * Optional texture key for AssetBank: PLANKS, STONE, ROCK, CONCRETE, BARK, IRON, …
         * null = flat color only.
         */
        public String texKey;
        /** Thin wall pieces: disable back-face cull so both sides show texture. */
        public boolean twoSided;

        public Part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                    float r, float g, float b, boolean solid) {
            this(x, y, z, sx, sy, sz, yaw, r, g, b, solid, null, false);
        }

        public Part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                    float r, float g, float b, boolean solid, String texKey, boolean twoSided) {
            this.x = x; this.y = y; this.z = z;
            this.sx = sx; this.sy = sy; this.sz = sz; this.yaw = yaw;
            this.r = r; this.g = g; this.b = b;
            this.solid = solid;
            this.texKey = texKey;
            this.twoSided = twoSided;
        }

        /** Legacy constructor: solid by default. */
        public Part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                    float r, float g, float b) {
            this(x, y, z, sx, sy, sz, yaw, r, g, b, true, null, false);
        }
    }

    private final List<StructureSite> sites = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private final List<Warp> warps = new ArrayList<>();
    private final Map<String, float[]> dungeonEntrances = new HashMap<>();

    public List<StructureSite> getSites() { return sites; }
    public List<Part> getParts() { return parts; }
    public List<Warp> getWarps() { return warps; }

    public void generate(Result terrain, int seed) {
        sites.clear();
        parts.clear();
        warps.clear();
        dungeonEntrances.clear();
        if (terrain == null) return;

        Random rng = new Random(seed ^ 0x53545255L); // "STRU"
        float half = terrain.size * 0.5f;
        float water = terrain.settings.waterLevel + 1.0f;
        // Keep the largest (dungeon) footprint and its relief samples inside
        // the generated terrain patch.
        float margin = 52f;

        float area = terrain.size * terrain.size;
        // Slightly more landmarks on larger maps
        int target = Math.max(5, Math.min(22, (int) (area / 14000f)));

        int attempts = target * 50;
        int placed = 0;
        for (int a = 0; a < attempts && placed < target; a++) {
            float x = -half + margin + rng.nextFloat() * (terrain.size - margin * 2);
            float z = -half + margin + rng.nextFloat() * (terrain.size - margin * 2);
            float y = WorldGenerator.sampleHeight(terrain, x, z);
            if (y < water) continue;

            Biome bio = WorldGenerator.sampleBiome(terrain, x, z);
            StructureSite.Type type = pickType(rng, bio);
            // Check the whole build footprint. A center-only slope test lets
            // large plazas and dungeon rooms cut through hillsides.
            float footprint = switch (type) {
                case TOWN -> 22f;
                case DUNGEON -> 48f;
                case FORT -> 14f;
                case TOMB -> 10f;
                case SHRINE -> 6f;
            };
            float maxRelief = switch (type) {
                case TOWN -> 2.5f;
                case DUNGEON -> 4f;
                default -> 3f;
            };
            if (!hasBuildableFootprint(terrain, x, z, footprint, maxRelief)) continue;

            boolean near = false;
            for (StructureSite s : sites) {
                float dx = s.x - x, dz = s.z - z;
                // larger separation for big dungeons / towns
                if (dx * dx + dz * dz < 75f * 75f) { near = true; break; }
            }
            if (near) continue;

            float yaw = rng.nextInt(4) * 90f;
            float padY = y + 0.15f;
            String name = (type == StructureSite.Type.TOMB ? "Graveyard" : type.label) + " " + (placed + 1);
            StructureSite site = new StructureSite(type, name, x, padY, z, yaw, rng.nextInt());
            sites.add(site);
            buildStructure(site, rng);
            placed++;
        }

        // Town wells warp to the nearest dungeon entrance pad.
        for (StructureSite town : sites) {
            if (town.type != StructureSite.Type.TOWN) continue;
            StructureSite best = null;
            float bestD = Float.MAX_VALUE;
            for (StructureSite d : sites) {
                if (d.type != StructureSite.Type.DUNGEON) continue;
                float dx = d.x - town.x, dz = d.z - town.z;
                float dist = dx * dx + dz * dz;
                if (dist < bestD) { bestD = dist; best = d; }
            }
            if (best == null) continue;
            float[] entry = dungeonEntrances.get(best.name);
            if (entry == null) continue;
            warps.add(new Warp(town.x, town.y + 0.8f, town.z, 2.4f,
                    entry[0], entry[1] + 1.2f, entry[2],
                    "Well -> " + best.name, 0.2f, 0.9f, 1f));
            // glowing well water
            part(town.x, town.y + 1.05f, town.z, 1.2f, 0.1f, 1.2f, town.yaw,
                    0.2f, 0.9f, 1f, false, null, false);
        }

        int solidCount = 0;
        for (Part p : parts) if (p.solid) solidCount++;
        System.out.println("[StructureGen] sites=" + sites.size()
                + " parts=" + parts.size()
                + " solid=" + solidCount
                + " warps=" + warps.size());
    }

    private static boolean hasBuildableFootprint(Result terrain, float x, float z,
                                                  float radius, float maxRelief) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        // A five by five grid catches broad slopes and local ridges while
        // remaining cheap during landmark placement.
        for (int iz = -2; iz <= 2; iz++) {
            for (int ix = -2; ix <= 2; ix++) {
                float h = WorldGenerator.sampleHeight(terrain,
                        x + radius * ix * 0.5f, z + radius * iz * 0.5f);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min <= maxRelief;
    }

    private static StructureSite.Type pickType(Random rng, Biome bio) {
        float r = rng.nextFloat();
        return switch (bio) {
            case DESERT, SAVANNA, BADLANDS -> r < 0.45f ? StructureSite.Type.TOMB
                    : r < 0.7f ? StructureSite.Type.FORT : StructureSite.Type.SHRINE;
            case SNOW, TAIGA, ALPINE -> r < 0.4f ? StructureSite.Type.FORT
                    : r < 0.75f ? StructureSite.Type.DUNGEON : StructureSite.Type.TOWN;
            case SWAMP, JUNGLE -> r < 0.5f ? StructureSite.Type.SHRINE
                    : r < 0.8f ? StructureSite.Type.TOWN : StructureSite.Type.DUNGEON;
            case VOLCANIC -> r < 0.6f ? StructureSite.Type.DUNGEON : StructureSite.Type.FORT;
            default -> r < 0.25f ? StructureSite.Type.TOWN
                    : r < 0.45f ? StructureSite.Type.TOMB
                    : r < 0.65f ? StructureSite.Type.DUNGEON
                    : r < 0.85f ? StructureSite.Type.FORT : StructureSite.Type.SHRINE;
        };
    }

    private void buildStructure(StructureSite site, Random rng) {
        switch (site.type) {
            case TOMB -> buildTomb(site);
            case DUNGEON -> buildDungeon(site, rng);
            case TOWN -> buildTown(site, rng);
            case FORT -> buildFort(site);
            case SHRINE -> buildShrine(site);
        }
    }

    private void part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b, boolean solid) {
        parts.add(new Part(x, y, z, sx, sy, sz, yaw, r, g, b, solid, null, false));
    }

    private void part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b, boolean solid, String texKey, boolean twoSided) {
        parts.add(new Part(x, y, z, sx, sy, sz, yaw, r, g, b, solid, texKey, twoSided));
    }

    /** Structural part — solid by default. */
    private void part(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b) {
        part(x, y, z, sx, sy, sz, yaw, r, g, b, true);
    }

    /** Textured solid part. */
    private void texPart(float x, float y, float z, float sx, float sy, float sz, float yaw,
                         float r, float g, float b, String texKey, boolean twoSided) {
        part(x, y, z, sx, sy, sz, yaw, r, g, b, true, texKey, twoSided);
    }

    /** Decorative part — rendered, no collision. */
    private void deco(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b) {
        part(x, y, z, sx, sy, sz, yaw, r, g, b, false, null, false);
    }

    private void deco(float x, float y, float z, float sx, float sy, float sz, float yaw,
                      float r, float g, float b, String texKey) {
        part(x, y, z, sx, sy, sz, yaw, r, g, b, false, texKey, false);
    }

    // ============================ TOMB ============================

    /**
     * Graveyard: iron-bar fence, several small headstones / mini-tombs,
     * and one larger enterable mausoleum with a door warp.
     */
    private void buildTomb(StructureSite s) {
        float yard = 22f;
        float half = yard * 0.5f;
        float barH = 2.2f;
        float barT = 0.12f;
        float post = 0.22f;
        float ironR = 0.35f, ironG = 0.35f, ironB = 0.38f;
        float stoneR = 0.7f, stoneG = 0.7f, stoneB = 0.68f;

        // ground
        texPart(s.x, s.y + 0.08f, s.z, yard, 0.2f, yard, s.yaw,
                0.45f, 0.42f, 0.38f, "DIRT", false);

        // ---- iron bar fence (thin two-sided posts + rails) with south gate ----
        // north / west / east solid runs
        buildIronFenceRun(s, 0, -half, yard, true);   // north along X
        buildIronFenceRun(s, -half, 0, yard, false);  // west along Z
        buildIronFenceRun(s, half, 0, yard, false);   // east along Z
        // south with gate gap in the middle
        float gateW = 3.2f;
        float wing = (yard - gateW) * 0.5f;
        float wingOff = (gateW + wing) * 0.5f;
        // left/right south wings in local space
        float[] sL = localToWorld(s, -wingOff, half);
        float[] sR = localToWorld(s, wingOff, half);
        // approximate south wings as short fence runs
        buildIronFenceSegment(sL[0], s.y, sL[1], wing, true, s.yaw);
        buildIronFenceSegment(sR[0], s.y, sR[1], wing, true, s.yaw);
        // gate posts
        float[] gL = localToWorld(s, -gateW * 0.5f, half);
        float[] gR = localToWorld(s, gateW * 0.5f, half);
        texPart(gL[0], s.y + barH * 0.5f, gL[1], post, barH, post, s.yaw, ironR, ironG, ironB, "IRON_BARS", true);
        texPart(gR[0], s.y + barH * 0.5f, gR[1], post, barH, post, s.yaw, ironR, ironG, ironB, "IRON_BARS", true);
        // gate cyan pad just outside
        float[] gatePad = localToWorld(s, 0, half + 1.2f);
        deco(gatePad[0], s.y + 0.25f, gatePad[1], 2.2f, 0.08f, 2.2f, s.yaw, 0.2f, 0.9f, 1f);

        // ---- small headstones / mini tombs scattered inside ----
        // fixed offsets so they stay deterministic per site seed without extra Random
        float[][] graves = {
                {-6f, -5f}, {-2f, -6f}, {3f, -5.5f}, {6f, -4f},
                {-7f, 0f}, {-5f, 3f}, {5f, 2f}, {7f, -1f},
                {-3f, 5f}, {2f, 6f}
        };
        for (int i = 0; i < graves.length; i++) {
            float[] p = localToWorld(s, graves[i][0], graves[i][1]);
            if ((i % 3) == 0) {
                // mini stone tomb (solid box)
                texPart(p[0], s.y + 0.55f, p[1], 1.6f, 1.0f, 1.0f, s.yaw,
                        stoneR, stoneG, stoneB, "STONE", false);
                texPart(p[0], s.y + 1.2f, p[1], 1.8f, 0.25f, 1.2f, s.yaw,
                        0.55f, 0.55f, 0.52f, "ROCK", false);
            } else {
                // headstone slab (thin two-sided)
                texPart(p[0], s.y + 0.9f, p[1], 0.9f, 1.5f, 0.2f, s.yaw,
                        stoneR * 0.95f, stoneG * 0.95f, stoneB, "STONE", true);
                // base
                texPart(p[0], s.y + 0.2f, p[1], 1.1f, 0.25f, 0.5f, s.yaw,
                        0.5f, 0.5f, 0.48f, "CONCRETE", false);
            }
        }

        // ---- large mausoleum at back (north) of yard ----
        float[] m = localToWorld(s, 0, -5.5f);
        float mx = m[0], mz = m[1];
        float mw = 7f, md = 6f, mh = 3.6f;
        // floor
        texPart(mx, s.y + 0.2f, mz, mw, 0.3f, md, s.yaw, 0.6f, 0.6f, 0.58f, "CONCRETE", false);
        // walls N E W
        texPart(mx, s.y + mh * 0.5f + 0.2f, mz - md * 0.5f, mw, mh, 0.4f, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        texPart(mx + mw * 0.5f, s.y + mh * 0.5f + 0.2f, mz, 0.4f, mh, md, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        texPart(mx - mw * 0.5f, s.y + mh * 0.5f + 0.2f, mz, 0.4f, mh, md, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        // south door gap
        float doorW = 1.8f;
        float seg = (mw - doorW) * 0.5f;
        float segOff = (doorW + seg) * 0.5f;
        float[] fL = localToWorldAt(mx, mz, s.yaw, -segOff, md * 0.5f);
        float[] fR = localToWorldAt(mx, mz, s.yaw, segOff, md * 0.5f);
        texPart(fL[0], s.y + mh * 0.5f + 0.2f, fL[1], seg, mh, 0.4f, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        texPart(fR[0], s.y + mh * 0.5f + 0.2f, fR[1], seg, mh, 0.4f, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        // lintel
        float[] fC = localToWorldAt(mx, mz, s.yaw, 0, md * 0.5f);
        texPart(fC[0], s.y + mh - 0.2f, fC[1], doorW, 0.5f, 0.4f, s.yaw, stoneR, stoneG, stoneB, "STONE", true);
        // ramp roof
        texPart(mx, s.y + mh + 0.5f, mz, mw + 0.8f, 0.5f, md + 0.8f, s.yaw, 0.55f, 0.55f, 0.52f, "ROCK", false);
        // dark door slab (decorative)
        deco(fC[0], s.y + 1.6f, fC[1], doorW * 0.9f, 2.6f, 0.15f, s.yaw, 0.08f, 0.08f, 0.1f);

        // door warps into / out of mausoleum
        float[] inside = localToWorldAt(mx, mz, s.yaw, 0, 0);
        float[] outside = localToWorldAt(mx, mz, s.yaw, 0, md * 0.5f + 1.3f);
        float feet = s.y + 0.45f;
        warps.add(new Warp(outside[0], feet, outside[1], 1.5f,
                inside[0], feet, inside[1], "Enter mausoleum", 0.2f, 0.9f, 1f));
        warps.add(new Warp(inside[0], feet, inside[1], 1.3f,
                outside[0], feet, outside[1], "Exit mausoleum", 1f, 0.55f, 0.15f));
        deco(outside[0], s.y + 0.28f, outside[1], 1.3f, 0.08f, 1.3f, s.yaw, 0.2f, 0.9f, 1f);
        deco(inside[0], s.y + 0.28f, inside[1], 1.1f, 0.08f, 1.1f, s.yaw, 1f, 0.55f, 0.15f);
    }

    /** Full fence side centered on site, along X (alongX) or Z. */
    private void buildIronFenceRun(StructureSite s, float lx, float lz, float length, boolean alongX) {
        float[] c = localToWorld(s, lx, lz);
        buildIronFenceSegment(c[0], s.y, c[1], length, alongX, s.yaw);
    }

    private void buildIronFenceSegment(float cx, float cy, float cz, float length,
                                       boolean alongX, float yaw) {
        float barH = 2.2f;
        float barT = 0.1f;
        float post = 0.2f;
        float ironR = 0.32f, ironG = 0.32f, ironB = 0.35f;
        // continuous thin rail (looks like bars from distance; HD 6450-friendly)
        if (alongX) {
            texPart(cx, cy + barH * 0.55f, cz, length, barH * 0.9f, barT, yaw,
                    ironR, ironG, ironB, "IRON_BARS", true);
            // top rail thicker
            texPart(cx, cy + barH, cz, length, 0.12f, barT * 1.4f, yaw,
                    ironR * 1.1f, ironG * 1.1f, ironB, "IRON", true);
            // posts at ends
            texPart(cx - length * 0.5f, cy + barH * 0.5f, cz, post, barH, post, yaw,
                    ironR, ironG, ironB, "IRON", true);
            texPart(cx + length * 0.5f, cy + barH * 0.5f, cz, post, barH, post, yaw,
                    ironR, ironG, ironB, "IRON", true);
        } else {
            texPart(cx, cy + barH * 0.55f, cz, barT, barH * 0.9f, length, yaw,
                    ironR, ironG, ironB, "IRON_BARS", true);
            texPart(cx, cy + barH, cz, barT * 1.4f, 0.12f, length, yaw,
                    ironR * 1.1f, ironG * 1.1f, ironB, "IRON", true);
            texPart(cx, cy + barH * 0.5f, cz - length * 0.5f, post, barH, post, yaw,
                    ironR, ironG, ironB, "IRON", true);
            texPart(cx, cy + barH * 0.5f, cz + length * 0.5f, post, barH, post, yaw,
                    ironR, ironG, ironB, "IRON", true);
        }
    }

    // ========================== DUNGEON ===========================

    // Daggerfall-ish scale: larger cells, more rooms on a bigger grid
    private static final float DG_CELL = 20f;
    private static final float DG_WALL_T = 0.65f;
    private static final float DG_WALL_H = 5.5f;
    private static final float DG_DOOR_W = 4.5f;
    private static final float DG_DOOR_H = 3.2f;
    private static final int DG_GRID = 5; // 5x5 cell grid (was 3x3)


    private void buildDungeon(StructureSite s, Random rng) {
        float floorR = 0.35f, floorG = 0.35f, floorB = 0.38f;
        float wallR = 0.42f, wallG = 0.4f, wallB = 0.45f;
        float ceilR = 0.28f, ceilG = 0.28f, ceilB = 0.31f;
        float baseY = s.y;
        float floorTop = baseY + 0.4f;
        float wallCY = floorTop + DG_WALL_H * 0.5f;
        float ceilCY = floorTop + DG_WALL_H + 0.25f;
        float edge = DG_CELL * 0.5f - DG_WALL_T * 0.5f;

        // ---- 1. branching room tree on DG_GRID x DG_GRID (Daggerfall-scale) ----
        int G = DG_GRID;
        int mid = G / 2;
        Set<Long> used = new HashSet<>();
        List<int[]> rooms = new ArrayList<>();   // {gx, gz, depth}
        List<int[]> links = new ArrayList<>();   // {ax, az, bx, bz}
        used.add(gridKey(mid, mid));
        rooms.add(new int[]{mid, mid, 0});
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{mid, mid, 0});
        int maxRooms = 8 + rng.nextInt(5);         // 8-12 rooms
        int maxDepth = 3 + rng.nextInt(3);         // depth 3-5
        while (rooms.size() < maxRooms && !queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[2] >= maxDepth) continue;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int i = dirs.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int[] t = dirs[i]; dirs[i] = dirs[j]; dirs[j] = t;
            }
            int branches = 1 + rng.nextInt(3);
            for (int i = 0; i < dirs.length && branches > 0 && rooms.size() < maxRooms; i++) {
                int nx = cur[0] + dirs[i][0];
                int nz = cur[1] + dirs[i][1];
                if (nx < 0 || nx >= G || nz < 0 || nz >= G) continue;
                if (used.add(gridKey(nx, nz))) {
                    rooms.add(new int[]{nx, nz, cur[2] + 1});
                    links.add(new int[]{cur[0], cur[1], nx, nz});
                    queue.add(new int[]{nx, nz, cur[2] + 1});
                    branches--;
                }
            }
        }

        // link lookup: dir 0=N(-z) 1=S(+z) 2=W(-x) 3=E(+x)
        boolean[][][] conn = new boolean[G][G][4];
        for (int[] l : links) {
            if (l[3] == l[1] + 1) { conn[l[0]][l[1]][1] = true; conn[l[2]][l[3]][0] = true; }
            else if (l[3] == l[1] - 1) { conn[l[0]][l[1]][0] = true; conn[l[2]][l[3]][1] = true; }
            if (l[2] == l[0] + 1) { conn[l[0]][l[1]][3] = true; conn[l[2]][l[3]][2] = true; }
            else if (l[2] == l[0] - 1) { conn[l[0]][l[1]][2] = true; conn[l[2]][l[3]][3] = true; }
        }

        int[] deepest = rooms.get(0);
        for (int[] r : rooms) if (r[2] > deepest[2]) deepest = r;

        // ---- 2. rooms: solid floor, ceiling, walls with door gaps ----
        for (int[] r : rooms) {
            float bx = (r[0] - mid) * DG_CELL;
            float bz = (r[1] - mid) * DG_CELL;
            float[] c = localToWorld(s, bx, bz);
            texPart(c[0], baseY + 0.2f, c[1], DG_CELL, 0.4f, DG_CELL, s.yaw,
                    floorR, floorG, floorB, "CONCRETE", false);
            texPart(c[0], ceilCY, c[1], DG_CELL, 0.5f, DG_CELL, s.yaw,
                    ceilR, ceilG, ceilB, "ROCK", false);
            for (int d = 0; d < 4; d++) {
                boolean door = (r[0] == 1 && r[1] == 1 && d == 1) || conn[r[0]][r[1]][d];
                boolean alongX = (d == 0 || d == 1);
                float off = (d == 0 || d == 2) ? -edge : edge;
                float wx = alongX ? bx : bx + off;
                float wz = alongX ? bz + off : bz;
                roomWall(s, wx, wz, wallCY, alongX, door, wallR, wallG, wallB);
            }
        }

        // ---- 3. corridors: floor, side walls, ceiling ----
        for (int[] l : links) {
            float ax = (l[0] - mid) * DG_CELL, az = (l[1] - mid) * DG_CELL;
            float bx2 = (l[2] - mid) * DG_CELL, bz2 = (l[3] - mid) * DG_CELL;
            float mx = (ax + bx2) * 0.5f, mz = (az + bz2) * 0.5f;
            boolean xLink = bx2 != ax;
            float wSpan = DG_DOOR_W + DG_WALL_T * 2f;
            float side = DG_DOOR_W * 0.5f + DG_WALL_T * 0.5f;
            if (xLink) {
                float[] c = localToWorld(s, mx, mz);
                texPart(c[0], baseY + 0.2f, c[1], DG_CELL, 0.4f, wSpan, s.yaw, floorR, floorG, floorB, "CONCRETE", false);
                texPart(c[0], ceilCY, c[1], DG_CELL, 0.5f, wSpan, s.yaw, ceilR, ceilG, ceilB, "ROCK", false);
                float[] wN = localToWorld(s, mx, mz - side);
                float[] wS = localToWorld(s, mx, mz + side);
                texPart(wN[0], wallCY, wN[1], DG_CELL, DG_WALL_H, DG_WALL_T, s.yaw, wallR, wallG, wallB, "STONE", true);
                texPart(wS[0], wallCY, wS[1], DG_CELL, DG_WALL_H, DG_WALL_T, s.yaw, wallR, wallG, wallB, "STONE", true);
            } else {
                float[] c = localToWorld(s, mx, mz);
                texPart(c[0], baseY + 0.2f, c[1], wSpan, 0.4f, DG_CELL, s.yaw, floorR, floorG, floorB, "CONCRETE", false);
                texPart(c[0], ceilCY, c[1], wSpan, 0.5f, DG_CELL, s.yaw, ceilR, ceilG, ceilB, "ROCK", false);
                float[] wW = localToWorld(s, mx - side, mz);
                float[] wE = localToWorld(s, mx + side, mz);
                texPart(wW[0], wallCY, wW[1], DG_WALL_T, DG_WALL_H, DG_CELL, s.yaw, wallR, wallG, wallB, "STONE", true);
                texPart(wE[0], wallCY, wE[1], DG_WALL_T, DG_WALL_H, DG_CELL, s.yaw, wallR, wallG, wallB, "STONE", true);
            }
        }

        // ---- 4. entrance corridor (open end, south of the center room) ----
        float corLen = 10f;
        float eMidZ = DG_CELL * 0.5f + corLen * 0.5f;
        float eSide = DG_DOOR_W * 0.5f + DG_WALL_T * 0.5f;
        float eSpan = DG_DOOR_W + DG_WALL_T * 2f;
        float[] ec = localToWorld(s, 0, eMidZ);
        texPart(ec[0], baseY + 0.2f, ec[1], eSpan, 0.4f, corLen, s.yaw, floorR, floorG, floorB, "CONCRETE", false);
        texPart(ec[0], ceilCY, ec[1], eSpan, 0.5f, corLen, s.yaw, ceilR, ceilG, ceilB, "ROCK", false);
        float[] eW = localToWorld(s, -eSide, eMidZ);
        float[] eE = localToWorld(s, eSide, eMidZ);
        texPart(eW[0], wallCY, eW[1], DG_WALL_T, DG_WALL_H, corLen, s.yaw, wallR, wallG, wallB, "STONE", true);
        texPart(eE[0], wallCY, eE[1], DG_WALL_T, DG_WALL_H, corLen, s.yaw, wallR, wallG, wallB, "STONE", true);

        // ---- 5. warp pads + treasure ----
        float padZ = DG_CELL * 0.5f + corLen + 3f;
        float[] pad = localToWorld(s, 0, padZ);
        deco(pad[0], baseY + 0.45f, pad[1], 4f, 0.12f, 4f, s.yaw, 0.2f, 0.9f, 1f);
        dungeonEntrances.put(s.name, new float[]{pad[0], baseY, pad[1]});

        float[] dw = localToWorld(s, (deepest[0] - mid) * DG_CELL, (deepest[1] - mid) * DG_CELL);
        deco(dw[0], baseY + 0.55f, dw[1], 4f, 0.12f, 4f, s.yaw, 1f, 0.6f, 0.15f);
        // treasure chest marker in the deepest room
        deco(dw[0], floorTop + 0.55f, dw[1] + 2f, 1.1f, 1.0f, 1.1f, s.yaw, 0.85f, 0.65f, 0.15f);

        float[] rt = localToWorld(s, 0, DG_CELL * 0.5f + 3f); // inside the entry corridor
        warps.add(new Warp(pad[0], baseY + 0.5f, pad[1], 2.6f,
                dw[0], floorTop + 0.1f, dw[1],
                s.name + " Depths", 0.2f, 0.9f, 1f));
        warps.add(new Warp(dw[0], baseY + 0.6f, dw[1], 2.6f,
                rt[0], floorTop + 0.1f, rt[1],
                "Exit " + s.name, 1f, 0.6f, 0.15f));
    }

    /** One room wall, optionally with a door gap (two segments + lintel). Textured STONE, two-sided. */
    private void roomWall(StructureSite s, float lx, float lz, float wallCY,
                          boolean alongX, boolean door, float r, float g, float b) {
        float[] c = localToWorld(s, lx, lz);
        String tex = "STONE";
        if (!door) {
            if (alongX) texPart(c[0], wallCY, c[1], DG_CELL, DG_WALL_H, DG_WALL_T, s.yaw, r, g, b, tex, true);
            else texPart(c[0], wallCY, c[1], DG_WALL_T, DG_WALL_H, DG_CELL, s.yaw, r, g, b, tex, true);
            return;
        }
        float segLen = (DG_CELL - DG_DOOR_W) * 0.5f;
        float segOff = (DG_DOOR_W + segLen) * 0.5f;
        float lintelH = DG_WALL_H - DG_DOOR_H;
        float lintelCY = 0.4f + DG_DOOR_H + lintelH * 0.5f;
        if (alongX) {
            float[] cA = localToWorld(s, lx - segOff, lz);
            float[] cB = localToWorld(s, lx + segOff, lz);
            texPart(cA[0], wallCY, cA[1], segLen, DG_WALL_H, DG_WALL_T, s.yaw, r, g, b, tex, true);
            texPart(cB[0], wallCY, cB[1], segLen, DG_WALL_H, DG_WALL_T, s.yaw, r, g, b, tex, true);
            texPart(c[0], s.y + lintelCY, c[1], DG_DOOR_W, lintelH, DG_WALL_T, s.yaw, r, g, b, tex, true);
        } else {
            float[] cA = localToWorld(s, lx, lz - segOff);
            float[] cB = localToWorld(s, lx, lz + segOff);
            texPart(cA[0], wallCY, cA[1], DG_WALL_T, DG_WALL_H, segLen, s.yaw, r, g, b, tex, true);
            texPart(cB[0], wallCY, cB[1], DG_WALL_T, DG_WALL_H, segLen, s.yaw, r, g, b, tex, true);
            texPart(c[0], s.y + lintelCY, c[1], DG_WALL_T, lintelH, DG_DOOR_W, s.yaw, r, g, b, tex, true);
        }
    }

    /** Local (site-relative) offset -> world, for 90-degree site yaw. */
    private static float[] localToWorld(StructureSite s, float lx, float lz) {
        int q = Math.round(s.yaw / 90f) & 3;
        float x, z;
        switch (q) {
            case 1 -> { x = -lz; z = lx; }
            case 2 -> { x = -lx; z = -lz; }
            case 3 -> { x = lz; z = -lx; }
            default -> { x = lx; z = lz; }
        }
        return new float[]{s.x + x, s.z + z};
    }

    private static long gridKey(int gx, int gz) { return gx * 4L + gz; }

    // ============================ TOWN ============================

    private void buildTown(StructureSite s, Random rng) {
        // Plaza + road grid + variety of houses (cottage / house / shop / tavern)
        float woodR = 0.55f, woodG = 0.42f, woodB = 0.28f;
        float stoneR = 0.55f, stoneG = 0.52f, stoneB = 0.48f;

        // large plaza floor
        texPart(s.x, s.y + 0.12f, s.z, 36f, 0.28f, 36f, s.yaw, 0.5f, 0.48f, 0.44f, "CONCRETE", false);

        // cross roads
        texPart(s.x, s.y + 0.18f, s.z, 36f, 0.12f, 4.5f, s.yaw, 0.4f, 0.38f, 0.35f, "ROCK", false);
        texPart(s.x, s.y + 0.18f, s.z, 4.5f, 0.12f, 36f, s.yaw, 0.4f, 0.38f, 0.35f, "ROCK", false);

        // central well / fountain
        texPart(s.x, s.y + 0.7f, s.z, 3.2f, 1.1f, 3.2f, s.yaw, stoneR, stoneG, stoneB, "STONE", false);
        deco(s.x, s.y + 1.35f, s.z, 2.2f, 0.15f, 2.2f, s.yaw, 0.25f, 0.55f, 0.85f);

        // House lots on a ring — 8–12 buildings
        // layout: positions in local XZ relative to town center
        float[][] lots = {
            // outer ring
            {-12f, -12f}, {0f, -14f}, {12f, -12f},
            {-14f, 0f},               {14f, 0f},
            {-12f, 12f},  {0f, 14f},  {12f, 12f},
            // inner near plaza
            {-7f, -7f}, {7f, -7f}, {-7f, 7f}, {7f, 7f},
        };
        int houseCount = 8 + rng.nextInt(5); // 8-12
        for (int i = 0; i < houseCount && i < lots.length; i++) {
            float lx = lots[i][0] + (rng.nextFloat() - 0.5f) * 1.5f;
            float lz = lots[i][1] + (rng.nextFloat() - 0.5f) * 1.5f;
            float[] w = localToWorld(s, lx, lz);
            // face toward plaza roughly
            float yaw = s.yaw;
            if (Math.abs(lx) > Math.abs(lz)) {
                yaw = s.yaw + (lx > 0 ? 270f : 90f);
            } else {
                yaw = s.yaw + (lz > 0 ? 180f : 0f);
            }
            yaw = (Math.round(yaw / 90f) & 3) * 90f;

            int style = rng.nextInt(4); // 0 cottage 1 house 2 shop 3 tavern
            float size;
            String wallTex;
            float wr, wg, wb;
            switch (style) {
                case 0 -> { // cottage
                    size = 5.5f + rng.nextFloat() * 1.5f;
                    wallTex = "PLANKS";
                    wr = woodR; wg = woodG; wb = woodB;
                }
                case 2 -> { // shop (stone front)
                    size = 7f + rng.nextFloat() * 1.5f;
                    wallTex = "STONE";
                    wr = stoneR; wg = stoneG; wb = stoneB;
                }
                case 3 -> { // tavern (larger wood)
                    size = 8.5f + rng.nextFloat() * 1.5f;
                    wallTex = "PLANKS";
                    wr = woodR * 0.9f; wg = woodG * 0.85f; wb = woodB;
                }
                default -> { // house
                    size = 6.5f + rng.nextFloat() * 2f;
                    wallTex = "PLANKS";
                    wr = woodR; wg = woodG; wb = woodB;
                }
            }
            buildHouse(w[0], s.y, w[1], yaw, size, rng, wallTex, wr, wg, wb, style);
        }

        // market stalls near center (decorative)
        for (int i = 0; i < 3; i++) {
            float ang = i * 2.1f;
            float lx = (float) Math.cos(ang) * 5.5f;
            float lz = (float) Math.sin(ang) * 5.5f;
            float[] w = localToWorld(s, lx, lz);
            deco(w[0], s.y + 1.0f, w[1], 2.2f, 1.8f, 1.4f, s.yaw + i * 40f, 0.5f, 0.35f, 0.2f, "PLANKS");
        }
    }

    private void buildHouse(float hx, float hy, float hz, float yaw, float size, Random rng) {
        buildHouse(hx, hy, hz, yaw, size, rng, "PLANKS", 0.55f, 0.42f, 0.28f, 1);
    }

    private void buildHouse(float hx, float hy, float hz, float yaw, float size, Random rng,
                            String wallTex, float woodR, float woodG, float woodB, int style) {
        float wallT = 0.4f;
        float wallH = style == 3 ? 4.2f : (style == 0 ? 3.2f : 3.6f);
        float half = size * 0.5f;
        float doorW = style == 2 ? 3.2f : 2.4f;

        // floor pad
        texPart(hx, hy + 0.12f, hz, size + 0.4f, 0.28f, size + 0.4f, yaw, 0.45f, 0.4f, 0.35f, "CONCRETE", false);

        // roof slab
        float roofY = hy + wallH + 0.35f;
        texPart(hx, roofY, hz, size + 0.8f, 0.45f, size + 0.8f, yaw, 0.35f, 0.22f, 0.15f, "BARK", false);

        // optional second story block for taverns
        if (style == 3) {
            texPart(hx, hy + wallH * 0.55f, hz, size * 0.85f, wallH * 0.5f, size * 0.85f, yaw,
                    woodR * 0.95f, woodG * 0.95f, woodB, wallTex, false);
        }

        float wallCY = hy + wallH * 0.5f + 0.15f;

        // back / left / right walls
        float[] back = localToWorldAt(hx, hz, yaw, 0, -half);
        float[] left = localToWorldAt(hx, hz, yaw, -half, 0);
        float[] right = localToWorldAt(hx, hz, yaw, half, 0);
        texPart(back[0], wallCY, back[1], size, wallH, wallT, yaw, woodR, woodG, woodB, wallTex, true);
        texPart(left[0], wallCY, left[1], wallT, wallH, size, yaw, woodR, woodG, woodB, wallTex, true);
        texPart(right[0], wallCY, right[1], wallT, wallH, size, yaw, woodR, woodG, woodB, wallTex, true);

        // front wall with door gap
        float segLen = (size - doorW) * 0.5f;
        float segOff = (doorW + segLen) * 0.5f;
        float[] fL = localToWorldAt(hx, hz, yaw, -segOff, half);
        float[] fR = localToWorldAt(hx, hz, yaw, segOff, half);
        texPart(fL[0], wallCY, fL[1], segLen, wallH, wallT, yaw, woodR, woodG, woodB, wallTex, true);
        texPart(fR[0], wallCY, fR[1], segLen, wallH, wallT, yaw, woodR, woodG, woodB, wallTex, true);
        float lintelH = 0.55f;
        float lintelCY = hy + wallH - lintelH * 0.5f + 0.15f;
        float[] fC = localToWorldAt(hx, hz, yaw, 0, half);
        texPart(fC[0], lintelCY, fC[1], doorW, lintelH, wallT, yaw, woodR * 0.9f, woodG * 0.9f, woodB, wallTex, true);

        // door slab decorative
        deco(fC[0], hy + 1.4f, fC[1], doorW * 0.9f, 2.5f, 0.12f, yaw, 0.25f, 0.15f, 0.08f, "BARK");

        // chimney on houses / taverns
        if (style == 1 || style == 3) {
            float[] ch = localToWorldAt(hx, hz, yaw, half * 0.55f, -half * 0.55f);
            texPart(ch[0], hy + wallH + 1.0f, ch[1], 0.7f, 1.8f, 0.7f, yaw, 0.4f, 0.38f, 0.36f, "STONE", false);
        }

        // shop awning
        if (style == 2) {
            float[] aw = localToWorldAt(hx, hz, yaw, 0, half + 0.6f);
            deco(aw[0], hy + 2.4f, aw[1], size * 0.7f, 0.15f, 1.2f, yaw, 0.6f, 0.15f, 0.15f);
        }

        // interior / exterior door warps
        float[] inside = localToWorldAt(hx, hz, yaw, 0, 0);
        float[] outside = localToWorldAt(hx, hz, yaw, 0, half + 1.4f);
        float feetY = hy + 0.4f;
        warps.add(new Warp(outside[0], feetY, outside[1], 1.5f,
                inside[0], feetY, inside[1],
                "Enter house", 0.2f, 0.9f, 1f));
        warps.add(new Warp(inside[0], feetY, inside[1], 1.3f,
                outside[0], feetY, outside[1],
                "Exit house", 1f, 0.6f, 0.15f));
        deco(outside[0], hy + 0.35f, outside[1], 1.2f, 0.08f, 1.2f, yaw, 0.2f, 0.9f, 1f);
        deco(inside[0], hy + 0.35f, inside[1], 1.0f, 0.08f, 1.0f, yaw, 1f, 0.55f, 0.15f);
    }

    private static float[] localToWorldAt(float ox, float oz, float yawDeg, float lx, float lz) {
        int q = Math.round(yawDeg / 90f) & 3;
        float x, z;
        switch (q) {
            case 1 -> { x = -lz; z = lx; }
            case 2 -> { x = -lx; z = -lz; }
            case 3 -> { x = lz; z = -lx; }
            default -> { x = lx; z = lz; }
        }
        return new float[]{ox + x, oz + z};
    }

    // ============================= FORT ============================

    private void buildFort(StructureSite s) {
        float r = 0.72f, g = 0.7f, b = 0.66f;
        float wallH = 5f;
        float wallT = 0.55f;
        // courtyard
        texPart(s.x, s.y + 0.15f, s.z, 20, 0.3f, 20, s.yaw, 0.55f, 0.52f, 0.48f, "CONCRETE", false);
        // walls N / W / E — thin two-sided stone
        texPart(s.x, s.y + wallH * 0.5f, s.z - 9.5f, 20, wallH, wallT, s.yaw, r, g, b, "STONE", true);
        texPart(s.x - 9.5f, s.y + wallH * 0.5f, s.z, wallT, wallH, 18, s.yaw, r, g, b, "STONE", true);
        texPart(s.x + 9.5f, s.y + wallH * 0.5f, s.z, wallT, wallH, 18, s.yaw, r, g, b, "STONE", true);
        // south gate gap
        float segLen = (20f - 4f) * 0.5f;
        texPart(s.x - 6f, s.y + wallH * 0.5f, s.z + 9.5f, segLen, wallH, wallT, s.yaw, r, g, b, "STONE", true);
        texPart(s.x + 6f, s.y + wallH * 0.5f, s.z + 9.5f, segLen, wallH, wallT, s.yaw, r, g, b, "STONE", true);
        texPart(s.x, s.y + 4.2f, s.z + 9.5f, 4f, 1.6f, wallT, s.yaw, r, g, b, "STONE", true);
        // corner towers
        for (float ox : new float[]{-9f, 9f}) {
            for (float oz : new float[]{-9f, 9f}) {
                texPart(s.x + ox, s.y + 4f, s.z + oz, 3f, 7f, 3f, s.yaw, r * 0.95f, g * 0.95f, b, "ROCK", false);
            }
        }
        // keep
        texPart(s.x, s.y + 4f, s.z, 6f, 7f, 6f, s.yaw, 0.65f, 0.62f, 0.58f, "STONE", false);
        deco(s.x, s.y + 2f, s.z + 9.6f, 3f, 3.5f, 0.35f, s.yaw, 0.15f, 0.12f, 0.1f);
        // gate cyan pad for future warps / visual
        deco(s.x, s.y + 0.35f, s.z + 11f, 2.5f, 0.1f, 2.5f, s.yaw, 0.2f, 0.9f, 1f);
    }

    // ============================ SHRINE ===========================

    private void buildShrine(StructureSite s) {
        texPart(s.x, s.y + 0.2f, s.z, 6, 0.35f, 6, s.yaw, 0.75f, 0.73f, 0.7f, "STONE", false);
        for (float ox : new float[]{-2f, 2f}) {
            for (float oz : new float[]{-2f, 2f}) {
                texPart(s.x + ox, s.y + 2.2f, s.z + oz, 0.6f, 4f, 0.6f, s.yaw, 0.8f, 0.8f, 0.78f, "STONE", true);
            }
        }
        texPart(s.x, s.y + 4.5f, s.z, 7, 0.4f, 7, s.yaw, 0.65f, 0.6f, 0.5f, "ROCK", false);
        deco(s.x, s.y + 0.9f, s.z, 1.8f, 1.2f, 1.8f, s.yaw, 0.45f, 0.35f, 0.55f);
    }

    /** Shared unit cube for all structure parts. */
    public static Mesh unitCube() {
        return PrimitiveMeshes.uvCube();
    }
}
