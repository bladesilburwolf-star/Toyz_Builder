package toyz.builder;

import com.raylib.Helpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic procedural architecture for the Temples and Forts update.
 * Lincoln Logs and Magnetix use separate construction grammars, but both
 * return ordinary PlacedPiece data so generated structures remain editable.
 */
public final class StructureGenerator {
    private StructureGenerator() {}

    public static final class Result {
        public final List<Piece.PlacedPiece> pieces = new ArrayList<>();
        public final List<Mob.Spawner> spawners = new ArrayList<>();
        public final List<WorldMap.Marker> markers = new ArrayList<>();
    }

    private static void mark(Result r, String name, WorldMap.MarkerKind kind, float x, float z) {
        r.markers.add(new WorldMap.Marker(name, kind, x, z));
    }

    private static float hash(int a, int b, int seed) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL
                + seed * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (float)((h & 0x7fffffffL) / (double)0x80000000L);
    }

    private static Piece.PlacedPiece piece(Piece.PieceType type, float x, float y, float z,
                                           float rot, Piece.PieceColor color) {
        Piece.PlacedPiece p = new Piece.PlacedPiece();
        p.type = type;
        p.position = Helpers.newVector3(x, y, z);
        p.rotationY = rot;
        p.color = color;
        p.length = Piece.PieceLength.MED;
        return p;
    }

    /** Wall from regular blocks (GLB-backed oakblock/rockblock/sandblock) — not OBJ primitives. */
    private static void addBlockWall(List<Piece.PlacedPiece> out, float cx, float cz, float ground,
                                     int half, int height, Piece.PieceType block, Piece.PieceColor color) {
        float step = 1.0f;
        for (int y = 0; y < height; y++) {
            float yy = ground + 0.5f + y * step;
            for (int x = -half; x <= half; x++) {
                out.add(piece(block, cx + x * step, yy, cz - half * step, 0, color));
                out.add(piece(block, cx + x * step, yy, cz + half * step, 0, color));
            }
            for (int z = -half + 1; z < half; z++) {
                out.add(piece(block, cx - half * step, yy, cz + z * step, 0, color));
                out.add(piece(block, cx + half * step, yy, cz + z * step, 0, color));
            }
        }
    }

    private static void addLogWall(List<Piece.PlacedPiece> out, float cx, float cz, float ground,
                                   int half, int height, Piece.PieceColor wood) {
        // Prefer vertical log GLBs for corner posts; walls use wood blocks
        addBlockWall(out, cx, cz, ground, half, height, Piece.PieceType.BlockWood, wood);
    }

    private static Piece.PieceType wallBlockForKind(String kind) {
        if ("temple".equals(kind)) return Piece.PieceType.BlockStone;
        if ("desert".equals(kind) || "sand".equals(kind)) return Piece.PieceType.BlockSand;
        return Piece.PieceType.BlockWood;
    }

    private static void addFort(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(4, Math.round(5 * scale));
        int height = Math.max(3, Math.round(3 * scale));
        // Regular wood blocks (oakblock.glb) for walls
        addBlockWall(r.pieces, cx, cz, ground, half, height, Piece.PieceType.BlockWood, Piece.PieceColor.Oak);

        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                float tx = cx + sx * half;
                float tz = cz + sz * half;
                for (int y = 0; y < height + 2; y++) {
                    r.pieces.add(piece(Piece.PieceType.LogVert, tx,
                            ground + 0.5f + y, tz, 0, Piece.PieceColor.Pine));
                }
                r.pieces.add(piece(Piece.PieceType.BlockWood, tx,
                        ground + height + 2.0f, tz, 0, Piece.PieceColor.Oak));
            }
        }

        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.9f,
                cz - half - 0.08f, 0, Piece.PieceColor.Walnut));
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.6f,
                cz - half - 0.12f, 0, Piece.PieceColor.Pine));
        r.pieces.add(piece(Piece.PieceType.FabricFlag, cx,
                ground + height + 2.5f, cz, 0,
                seed % 2 == 0 ? Piece.PieceColor.Red : Piece.PieceColor.Blue));

        addSpawner(r, cx, cz, ground, Mob.Kind.TONBERRY, 0, 0, 7f, 2);
        addSpawner(r, cx, cz, ground, Mob.Kind.IMP, half + 3f, 0, 9f, 2);
        addSpawner(r, cx, cz, ground, Mob.Kind.NEEDLEKIN, -half - 3f, 0, 10f, 2);
    }


    /**
     * Large tomb / dungeon entrance — vertical concrete rectangle, stone ramp roof,
     * black door (warp to Dungeon world). Fewer than trees: sparse placement.
     */

    /** Climate-aware town: a few houses + plaza marker. */
    private static void addTown(Result r, float cx, float cz, float ground, float scale, int seed, int biome) {
        boolean desert = biome == Terrain.BIOME_DESERT || biome == Terrain.BIOME_BEACH
                || biome == Terrain.BIOME_BADLANDS || biome == Terrain.BIOME_MESA;
        boolean snow = biome == Terrain.BIOME_SNOW || biome == Terrain.BIOME_TAIGA
                || biome == Terrain.BIOME_ALPINE || biome == Terrain.BIOME_FROZEN_LAKE;
        boolean swamp = biome == Terrain.BIOME_SWAMP || biome == Terrain.BIOME_MANGROVE;
        Piece.PieceType wall = desert ? Piece.PieceType.BlockSand
                : snow ? Piece.PieceType.BlockStone
                : swamp ? Piece.PieceType.BlockWood
                : Piece.PieceType.BlockWood;
        Piece.PieceColor wood = snow ? Piece.PieceColor.Pine
                : desert ? Piece.PieceColor.Oak
                : swamp ? Piece.PieceColor.Walnut
                : Piece.PieceColor.Oak;
        String townName = desert ? "Desert Hamlet" : snow ? "Snow Town" : swamp ? "Swamp Village" : "Village";
        int houses = 3 + (int)(hash(seed, 3, seed) * 3);
        for (int i = 0; i < houses; i++) {
            float ang = hash(i, 11, seed) * 6.2831853f;
            float dist = 6f + hash(i, 13, seed) * 10f * scale;
            float hx = cx + (float) Math.cos(ang) * dist;
            float hz = cz + (float) Math.sin(ang) * dist;
            int half = Math.max(2, Math.round(2 * scale));
            int h = Math.max(2, Math.round(2.5f * scale));
            addBlockWall(r.pieces, hx, hz, ground, half, h, wall, wood);
            r.pieces.add(piece(Piece.PieceType.Roof, hx, ground + h + 0.5f, hz, 0,
                    desert ? Piece.PieceColor.Yellow : Piece.PieceColor.Red));
            if (hash(i, 17, seed) > 0.45f) {
                r.pieces.add(piece(Piece.PieceType.Door, hx, ground + 0.9f, hz - half - 0.1f, 0, wood));
            }
        }
        // plaza center marker
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.2f, cz, 0, Piece.PieceColor.Pine));
        mark(r, townName, WorldMap.MarkerKind.TOWN, cx, cz);
    }

    /** Small shrine — pillar + roof peak, not a full tomb. */
    private static void addShrine(Result r, float cx, float cz, float ground, float scale, int seed) {
        int h = Math.max(3, Math.round(4 * scale));
        for (int y = 0; y < h; y++) {
            r.pieces.add(piece(Piece.PieceType.BlockStone, cx - 1, ground + 0.5f + y, cz, 0, Piece.PieceColor.Natural));
            r.pieces.add(piece(Piece.PieceType.BlockStone, cx + 1, ground + 0.5f + y, cz, 0, Piece.PieceColor.Natural));
            r.pieces.add(piece(Piece.PieceType.BlockStone, cx, ground + 0.5f + y, cz - 1, 0, Piece.PieceColor.Natural));
            r.pieces.add(piece(Piece.PieceType.BlockStone, cx, ground + 0.5f + y, cz + 1, 0, Piece.PieceColor.Natural));
        }
        r.pieces.add(piece(Piece.PieceType.RoofPeak, cx, ground + h + 0.6f, cz, 0, Piece.PieceColor.Natural));
        r.pieces.add(piece(Piece.PieceType.FabricFlag, cx, ground + h + 1.4f, cz, 0, Piece.PieceColor.White));
        mark(r, "Shrine", WorldMap.MarkerKind.SHRINE, cx, cz);
    }

    private static void addDungeonTomb(Result r, float cx, float cz, float ground, float scale, int seed) {
        // Tall box footprint ~5x5, height ~8-10 blocks
        int half = Math.max(2, Math.round(2.2f * scale)); // wall half-extent in blocks
        int height = Math.max(7, Math.round(8 * scale));
        Piece.PieceType wall = Piece.PieceType.BlockConcrete;
        // Prefer concrete; fallback path if missing uses BlockStone via piece types that exist
        // BlockConcrete is in enum from material expansion
        try {
            // walls — solid vertical rectangle (no windows)
            for (int y = 0; y < height; y++) {
                float yy = ground + 0.5f + y;
                for (int x = -half; x <= half; x++) {
                    r.pieces.add(piece(wall, cx + x, yy, cz - half, 0, Piece.PieceColor.Natural));
                    r.pieces.add(piece(wall, cx + x, yy, cz + half, 0, Piece.PieceColor.Natural));
                }
                for (int z = -half + 1; z < half; z++) {
                    r.pieces.add(piece(wall, cx - half, yy, cz + z, 0, Piece.PieceColor.Natural));
                    r.pieces.add(piece(wall, cx + half, yy, cz + z, 0, Piece.PieceColor.Natural));
                }
            }
        } catch (Throwable t) {
            wall = Piece.PieceType.BlockStone;
            addBlockWall(r.pieces, cx, cz, ground, half, height, wall, Piece.PieceColor.Natural);
        }
        // Floor
        for (int x = -half + 1; x < half; x++) {
            for (int z = -half + 1; z < half; z++) {
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x, ground + 0.15f, cz + z, 0, Piece.PieceColor.Natural));
            }
        }
        // Stone ramp roof — stepped pyramid / ramp slopes on four sides toward peak
        float roofBase = ground + height + 0.15f;
        int layers = half + 2;
        for (int layer = 0; layer < layers; layer++) {
            int rHalf = half + 1 - layer;
            if (rHalf < 0) break;
            float yy = roofBase + layer * 0.55f;
            for (int x = -rHalf; x <= rHalf; x++) {
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x, yy, cz - rHalf, 0, Piece.PieceColor.Natural));
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x, yy, cz + rHalf, 0, Piece.PieceColor.Natural));
            }
            for (int z = -rHalf + 1; z < rHalf; z++) {
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx - rHalf, yy, cz + z, 0, Piece.PieceColor.Natural));
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx + rHalf, yy, cz + z, 0, Piece.PieceColor.Natural));
            }
            // ramp pieces on front slope for silhouette
            if (layer < layers - 1) {
                r.pieces.add(piece(Piece.PieceType.Roof, cx, yy + 0.2f, cz - rHalf - 0.4f, 0, Piece.PieceColor.Natural));
                r.pieces.add(piece(Piece.PieceType.Roof, cx, yy + 0.2f, cz + rHalf + 0.4f, 180, Piece.PieceColor.Natural));
            }
        }
        // Cap
        r.pieces.add(piece(Piece.PieceType.RoofPeak, cx, roofBase + layers * 0.55f + 0.3f, cz, 0, Piece.PieceColor.Natural));
        // BLACK door on south face — portal trigger registered later
        float doorZ = cz - half - 0.15f;
        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.95f, doorZ, 0, Piece.PieceColor.Black));
        // Small marker sign
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.85f, doorZ - 0.05f, 0, Piece.PieceColor.Black));
        mark(r, "Tomb", WorldMap.MarkerKind.TOMB, cx, cz);
    }

    private static void addTemple(Result r, float cx, float cz, float ground, float scale, int seed) {
        // Stone-block shell (rockblock/concrete GLB) — desert temples use sand blocks via addDesertTemple
        int radius = Math.max(3, Math.round(3 * scale));
        int height = Math.max(3, Math.round(4 * scale));
        Piece.PieceColor[] woods = {
            Piece.PieceColor.Cedar, Piece.PieceColor.Oak, Piece.PieceColor.Mahogany
        };

        // Stone block pillars + floor (rockblock.glb)
        for (int x : new int[]{-radius, radius}) {
            for (int z : new int[]{-radius, radius}) {
                for (int y = 0; y < height; y++) {
                    r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x,
                            ground + 0.5f + y, cz + z, 0, Piece.PieceColor.Natural));
                }
            }
        }
        addBlockWall(r.pieces, cx, cz, ground, radius, height - 1, Piece.PieceType.BlockStone, Piece.PieceColor.Natural);

        for (int x = -radius; x <= radius; x++) {
            r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x,
                    ground + height + 0.1f, cz, 0, Piece.PieceColor.Natural));
            r.pieces.add(piece(Piece.PieceType.Roof, cx + x,
                    ground + height + 0.65f, cz - 0.7f, 0, Piece.PieceColor.Natural));
            r.pieces.add(piece(Piece.PieceType.Roof, cx + x,
                    ground + height + 1.1f, cz + 0.7f, 180, Piece.PieceColor.Natural));
        }

        r.pieces.add(piece(Piece.PieceType.RoofPeak, cx,
                ground + height + 1.35f, cz, 0, Piece.PieceColor.Natural));
        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.9f,
                cz - radius - 0.1f, 0, Piece.PieceColor.Walnut));
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.7f,
                cz - radius - 0.15f, 0, Piece.PieceColor.Pine));
        r.pieces.add(piece(Piece.PieceType.FabricFlag, cx,
                ground + height + 2.0f, cz, 0,
                seed % 3 == 0 ? Piece.PieceColor.Purple : Piece.PieceColor.White));

        addSpawner(r, cx, cz, ground, Mob.Kind.AHRIMAN, 0, 0, 11f, 2);
        mark(r, "Temple", WorldMap.MarkerKind.TEMPLE, cx, cz);
    }

    private static void addMagnetixTemple(Result r, float cx, float cz, float ground,
                                          float scale, int seed) {
        int ring = Math.max(3, Math.round(4 * scale));
        int levels = Math.max(2, Math.round(3 * scale));
        Piece.PieceColor[] colors = {
            Piece.PieceColor.Red, Piece.PieceColor.Orange, Piece.PieceColor.Yellow,
            Piece.PieceColor.Green, Piece.PieceColor.Blue, Piece.PieceColor.Purple
        };

        for (int level = 0; level < levels; level++) {
            float y = ground + 0.5f + level * 1.15f;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3.0;
                float x = cx + (float)Math.cos(a) * ring;
                float z = cz + (float)Math.sin(a) * ring;
                r.pieces.add(piece(Piece.PieceType.MagnetixRodVert, x, y, z,
                        0, colors[(i + level) % colors.length]));
            }
        }

        r.pieces.add(piece(Piece.PieceType.MagnetixHex, cx,
                ground + levels * 1.15f + 0.7f, cz, 0, Piece.PieceColor.Purple));
        r.pieces.add(piece(Piece.PieceType.MagnetixBall, cx,
                ground + 1.1f, cz, 0, Piece.PieceColor.White));
        r.pieces.add(piece(Piece.PieceType.MagnetixFlag, cx,
                ground + levels * 1.15f + 1.5f, cz, 0,
                colors[Math.floorMod(seed, colors.length)]));

        addSpawner(r, cx, cz, ground, Mob.Kind.ICE_WISP, 0, 0, 8f, 3);
        addSpawner(r, cx, cz, ground, Mob.Kind.FIRE_SPRITE, ring + 2f, 0, 9f, 2);
    }

    private static void addMagnetixFort(Result r, float cx, float cz, float ground,
                                        float scale, int seed) {
        int half = Math.max(3, Math.round(4 * scale));
        Piece.PieceColor[] colors = {
            Piece.PieceColor.Red, Piece.PieceColor.Blue,
            Piece.PieceColor.Yellow, Piece.PieceColor.Green
        };

        for (int side = -half; side <= half; side++) {
            r.pieces.add(piece(Piece.PieceType.MagnetixRod, cx + side,
                    ground + 0.55f, cz - half, 0,
                    colors[Math.floorMod(side + seed, colors.length)]));
            r.pieces.add(piece(Piece.PieceType.MagnetixRod, cx + side,
                    ground + 0.55f, cz + half, 0,
                    colors[Math.floorMod(side + seed + 1, colors.length)]));
            r.pieces.add(piece(Piece.PieceType.MagnetixBall, cx + side,
                    ground + 0.55f, cz - half, 0, Piece.PieceColor.Steel));
            r.pieces.add(piece(Piece.PieceType.MagnetixBall, cx + side,
                    ground + 0.55f, cz + half, 0, Piece.PieceColor.Iron));
        }

        for (int y = 0; y < 3; y++) {
            r.pieces.add(piece(Piece.PieceType.MagnetixRodVert, cx - half,
                    ground + 0.5f + y, cz, 0, Piece.PieceColor.Titanium));
            r.pieces.add(piece(Piece.PieceType.MagnetixRodVert, cx + half,
                    ground + 0.5f + y, cz, 0, Piece.PieceColor.Titanium));
        }

        addSpawner(r, cx, cz, ground, Mob.Kind.NEEDLEKIN, 0, 0, 8f, 2);
        addSpawner(r, cx, cz, ground, Mob.Kind.IMP, half + 3f, half + 3f, 9f, 2);
    }

    private static void addBridge(Result r, float x, float z, float ground,
                                  float scale, boolean magnetix, int seed) {
        int span = Math.max(5, Math.round(8 * scale));

        if (!magnetix) {
            for (int i = -span; i <= span; i++) {
                r.pieces.add(piece(Piece.PieceType.PlankWide, x,
                        ground + 0.55f, z + i, 90, Piece.PieceColor.Oak));
                if (Math.floorMod(i, 2) == 0) {
                    r.pieces.add(piece(Piece.PieceType.LogVert, x - 1.1f,
                            ground + 0.75f, z + i, 0, Piece.PieceColor.Pine));
                    r.pieces.add(piece(Piece.PieceType.LogVert, x + 1.1f,
                            ground + 0.75f, z + i, 0, Piece.PieceColor.Pine));
                }
            }
            for (int i = -span; i <= span; i++) {
                r.pieces.add(piece(Piece.PieceType.StraightLog, x - 1.15f,
                        ground + 1.2f, z + i, 90, Piece.PieceColor.Cedar));
                r.pieces.add(piece(Piece.PieceType.StraightLog, x + 1.15f,
                        ground + 1.2f, z + i, 90, Piece.PieceColor.Cedar));
            }
        } else {
            Piece.PieceColor[] colors = {
                Piece.PieceColor.Red, Piece.PieceColor.Orange, Piece.PieceColor.Yellow,
                Piece.PieceColor.Green, Piece.PieceColor.Blue, Piece.PieceColor.Purple
            };
            for (int i = -span; i <= span; i++) {
                Piece.PieceColor c = colors[Math.floorMod(i + seed, colors.length)];
                r.pieces.add(piece(Piece.PieceType.MagnetixRod, x,
                        ground + 0.55f, z + i, 0, c));
                r.pieces.add(piece(Piece.PieceType.MagnetixBall, x,
                        ground + 0.55f, z + i, 0, Piece.PieceColor.Steel));
            }
            r.pieces.add(piece(Piece.PieceType.MagnetixRod, x - 1.2f,
                    ground + 1.0f, z, 0, Piece.PieceColor.Titanium));
            r.pieces.add(piece(Piece.PieceType.MagnetixRod, x + 1.2f,
                    ground + 1.0f, z, 0, Piece.PieceColor.Titanium));
        }
    }

    private static void addSpawner(Result r, float cx, float cz, float ground,
                                   Mob.Kind kind, float dx, float dz,
                                   float interval, int maxAlive) {
        Mob.Spawner s = new Mob.Spawner();
        s.pos = Helpers.newVector3(cx + dx, ground + 0.5f, cz + dz);
        s.kind = kind;
        s.interval = interval;
        s.maxAlive = maxAlive;
        s.spawnRadius = 5f;
        r.spawners.add(s);
    }


    private static void addNetherFort(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(3, Math.round(4 * scale));
        int height = Math.max(4, Math.round(5 * scale));
        addBlockWall(r.pieces, cx, cz, ground, half, height, Piece.PieceType.BlockStone, Piece.PieceColor.Natural);
        // crimson accents
        for (int y = 0; y < height; y += 2) {
            r.pieces.add(piece(Piece.PieceType.BlockMetal, cx + half, ground + 0.5f + y, cz, 0, Piece.PieceColor.Red));
            r.pieces.add(piece(Piece.PieceType.BlockMetal, cx - half, ground + 0.5f + y, cz, 0, Piece.PieceColor.Red));
        }
        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.9f, cz - half - 0.05f, 0, Piece.PieceColor.Natural)); // black-tinted nether door
        r.pieces.add(piece(Piece.PieceType.FabricFlag, cx, ground + height + 1.5f, cz, 0, Piece.PieceColor.Red));
        addSpawner(r, cx, cz, ground, Mob.Kind.FIRE_SPRITE, 0, 0, 7f, 3);
        addSpawner(r, cx, cz, ground, Mob.Kind.IMP, half + 2f, 0, 9f, 2);
    }

    private static void addSwampHut(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(2, Math.round(2.5f * scale));
        int height = Math.max(2, Math.round(2.5f * scale));
        float stilts = 1.2f;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                for (int y = 0; y < 2; y++) {
                    r.pieces.add(piece(Piece.PieceType.LogVert, cx + sx * half,
                            ground + 0.5f + y, cz + sz * half, 0, Piece.PieceColor.Walnut));
                }
            }
        }
        float floorY = ground + stilts;
        addBlockWall(r.pieces, cx, cz, floorY, half, height, Piece.PieceType.BlockWood, Piece.PieceColor.Pine);
        for (int x = -half + 1; x < half; x++) {
            for (int z = -half + 1; z < half; z++) {
                r.pieces.add(piece(Piece.PieceType.PlankWide, cx + x, floorY + 0.05f, cz + z, 0, Piece.PieceColor.Oak));
            }
        }
        r.pieces.add(piece(Piece.PieceType.Door, cx, floorY + 0.9f, cz - half - 0.05f, 0, Piece.PieceColor.Walnut));
        r.pieces.add(piece(Piece.PieceType.Sign, cx, floorY + 1.6f, cz - half - 0.1f, 0, Piece.PieceColor.Pine));
        addSpawner(r, cx, cz, ground, Mob.Kind.IMP, 0, 0, 7f, 2);
    }

    private static void addTundraHut(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(2, Math.round(3f * scale));
        int height = Math.max(2, Math.round(2.5f * scale));
        addBlockWall(r.pieces, cx, cz, ground, half, height, Piece.PieceType.BlockStone, Piece.PieceColor.Natural);
        for (int x = -half; x <= half; x++) {
            r.pieces.add(piece(Piece.PieceType.BlockWood, cx + x, ground + height + 0.5f, cz, 0, Piece.PieceColor.Pine));
        }
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                r.pieces.add(piece(Piece.PieceType.LogVert, cx + sx * half,
                        ground + height + 1.0f, cz + sz * half, 0, Piece.PieceColor.Pine));
            }
        }
        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.9f, cz - half - 0.05f, 0, Piece.PieceColor.Walnut));
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.6f, cz - half - 0.1f, 0, Piece.PieceColor.Pine));
        r.pieces.add(piece(Piece.PieceType.FabricFlag, cx, ground + height + 2.2f, cz, 0, Piece.PieceColor.White));
        addSpawner(r, cx, cz, ground, Mob.Kind.ICE_WISP, 0, 0, 8f, 2);
        addSpawner(r, cx, cz, ground, Mob.Kind.IMP, half + 2f, 0, 9f, 1);
    }

    private static void addDesertTemple(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(3, Math.round(4 * scale));
        int height = Math.max(3, Math.round(4 * scale));
        addBlockWall(r.pieces, cx, cz, ground, half, height, Piece.PieceType.BlockSand, Piece.PieceColor.Yellow);
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                float tx = cx + sx * (half - 1);
                float tz = cz + sz * (half - 1);
                for (int y = 0; y < height; y++) {
                    r.pieces.add(piece(Piece.PieceType.BlockStone, tx, ground + 0.5f + y, tz, 0, Piece.PieceColor.Natural));
                }
            }
        }
        for (int x = -half + 1; x < half; x++) {
            for (int z = -half + 1; z < half; z++) {
                r.pieces.add(piece(Piece.PieceType.BlockStone, cx + x, ground + height + 0.5f, cz + z, 0, Piece.PieceColor.Natural));
            }
        }
        r.pieces.add(piece(Piece.PieceType.Door, cx, ground + 0.9f, cz - half - 0.05f, 0, Piece.PieceColor.Walnut));
        r.pieces.add(piece(Piece.PieceType.Sign, cx, ground + 1.7f, cz - half - 0.1f, 0, Piece.PieceColor.Pine));
        addSpawner(r, cx, cz, ground, Mob.Kind.NEEDLEKIN, 0, 0, 8f, 2);
        addSpawner(r, cx, cz, ground, Mob.Kind.AHRIMAN, half + 2f, 0, 11f, 1);
    }

    public static Result generateFromSites(Terrain.ForestTerrain terrain,
                                           MapSystem.StructureSettings cfg) {
        Result r = new Result();
        if (terrain == null || cfg == null || !cfg.enabled) return r;
        if (terrain.v2 != null && terrain.v2.structureSites != null
                && !terrain.v2.structureSites.isEmpty()) {
            int n = 0;
            for (toyz.builder.terrain.StructureSite site : terrain.v2.structureSites) {
                if (n >= cfg.maxStructures) break;
                if (site.reserved) continue;
                float ground = site.y > 0.1f ? site.y : Terrain.getTerrainHeight(terrain, site.x, site.z);
                if (ground < terrain.waterLevel + 0.8f) continue;
                float sc = Math.max(0.7f, Math.min(1.6f, cfg.globalScale * (0.9f + site.terrainScore * 0.3f)));
                String kind = site.kind != null ? site.kind : "fort";
                toyz.builder.terrain.BiomeId b = site.biome;
                boolean desert = b == toyz.builder.terrain.BiomeId.DESERT
                        || b == toyz.builder.terrain.BiomeId.BADLANDS
                        || b == toyz.builder.terrain.BiomeId.MESA
                        || b == toyz.builder.terrain.BiomeId.SAVANNA;
                boolean swamp = b == toyz.builder.terrain.BiomeId.SWAMP
                        || b == toyz.builder.terrain.BiomeId.MANGROVE;
                boolean tundra = b == toyz.builder.terrain.BiomeId.SNOW
                        || b == toyz.builder.terrain.BiomeId.TAIGA
                        || b == toyz.builder.terrain.BiomeId.ALPINE
                        || b == toyz.builder.terrain.BiomeId.FROZEN_LAKE;
                boolean nether = b != null && b.isNether();
                if (nether) {
                    addNetherFort(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if (swamp) {
                    addSwampHut(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if (tundra) {
                    addTundraHut(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if ("town".equals(kind) || "village".equals(kind)) {
                    addTown(r, site.x, site.z, ground, sc, terrain.seed + n, Terrain.biomeAt(terrain, site.x, site.z));
                } else if ("shrine".equals(kind)) {
                    addShrine(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if ("tomb".equals(kind) || "dungeon".equals(kind)) {
                    addDungeonTomb(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if ("temple".equals(kind) || desert) {
                    if (desert) addDesertTemple(r, site.x, site.z, ground, sc, terrain.seed + n);
                    else addTemple(r, site.x, site.z, ground, sc, terrain.seed + n);
                } else if ("bridge".equals(kind)) {
                    addBridge(r, site.x, site.z, ground, sc, false, terrain.seed + n);
                } else {
                    addFort(r, site.x, site.z, ground, sc, terrain.seed + n);
                }
                site.reserved = true;
                n++;
            }
            if (n > 0) {
                System.out.println("[Structures] placed " + n + " from V3 sites (block/GLB)");
                scatterDungeonTombs(r, terrain, terrain.seed);
                return r;
            }
        }
        return generate(terrain, cfg);
    }


    /** Place a few tomb entrances across the map — density much lower than trees. */
    private static void scatterDungeonTombs(Result r, Terrain.ForestTerrain terrain, int seed) {
        if (terrain == null) return;
        boolean dungeonMap = terrain.dungeonWorld || terrain.worldType == Terrain.WorldType.DUNGEON;
        // Overworld: ~3-5 tombs; dungeon dimension: denser cluster of tombs
        int count = dungeonMap ? 8 + (Math.abs(seed) % 5) : 3 + (Math.abs(seed) % 3);
        float area = Math.max(80f, terrain.size * 0.35f);
        if (dungeonMap) area = Math.max(60f, terrain.size * 0.4f);
        for (int i = 0; i < count; i++) {
            float ang = hash(i, 101, seed) * 6.2831853f;
            float dist = 25f + hash(i, 103, seed) * area;
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            // Keep one near origin on dungeon maps so player sees a tomb immediately
            if (dungeonMap && i == 0) { x = 18f; z = 12f; }
            float ground = Terrain.getTerrainHeight(terrain, x, z);
            if (!dungeonMap && ground < terrain.waterLevel + 1.2f) continue;
            float sc = dungeonMap ? 1.1f + hash(i, 107, seed) * 0.4f : 0.95f + hash(i, 107, seed) * 0.35f;
            addDungeonTomb(r, x, z, ground, sc, seed + i * 17);
        }
        System.out.println("[Structure] dungeon tombs scattered count≈" + count
                + " dungeonMap=" + dungeonMap);
    }

    public static Result generate(Terrain.ForestTerrain terrain,
                                  MapSystem.StructureSettings cfg) {
        Result r = new Result();
        if (terrain == null || cfg == null || !cfg.enabled || !terrain.structuresEnabled) return r;

        int seed = terrain.seed;
        float scale = Math.max(0.65f, Math.min(2.0f, cfg.globalScale));
        int made = 0;

        for (int i = 0; i < cfg.maxStructures && made < cfg.maxStructures; i++) {
            float angle = hash(i, 17, seed) * 6.2831853f;
            float dist = cfg.minSpacing + hash(i, 29, seed) * 115f;
            float x = (float)Math.cos(angle) * dist;
            float z = (float)Math.sin(angle) * dist;
            float ground = Terrain.getTerrainHeight(terrain, x, z);
            if (ground < terrain.waterLevel + 0.8f) continue;

            float localScale = scale * (
                cfg.houseScaleMin
                + hash(i, 41, seed) * (cfg.houseScaleMax - cfg.houseScaleMin)
            );
            float roll = hash(i, 53, seed);
            boolean magnetix = hash(i, 67, seed) < cfg.magnetixChance;

            if (roll < 0.18f) {
                if (magnetix) addMagnetixFort(r, x, z, ground, localScale, seed + i);
                else addFort(r, x, z, ground, localScale, seed + i);
                mark(r, "Fort", WorldMap.MarkerKind.FORT, x, z);
            } else if (roll < 0.36f) {
                if (magnetix) addMagnetixTemple(r, x, z, ground, localScale, seed + i);
                else addTemple(r, x, z, ground, localScale, seed + i);
            } else if (roll < 0.52f) {
                int biome = Terrain.biomeAt(terrain, x, z);
                addTown(r, x, z, ground, localScale, seed + i, biome);
            } else if (roll < 0.66f) {
                addShrine(r, x, z, ground, localScale, seed + i);
            } else if (roll < 0.78f) {
                addDungeonTomb(r, x, z, ground, localScale, seed + i);
            } else if (roll < 0.90f) {
                addBridge(r, x, z, ground, localScale, magnetix, seed + i);
                mark(r, "Bridge", WorldMap.MarkerKind.BRIDGE, x, z);
            } else {
                addTemple(r, x, z, ground, Math.max(0.7f, localScale * 0.8f), seed + i);
            }
            made++;
        }
        scatterDungeonTombs(r, terrain, seed);
        return r;
    }
}
