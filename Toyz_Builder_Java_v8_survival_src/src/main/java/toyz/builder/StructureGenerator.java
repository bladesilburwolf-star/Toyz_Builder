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

    private static void addLogWall(List<Piece.PlacedPiece> out, float cx, float cz, float ground,
                                   int half, int height, Piece.PieceColor wood) {
        for (int y = 0; y < height; y++) {
            float yy = ground + 0.5f + y;
            for (int x = -half; x <= half; x++) {
                out.add(piece(Piece.PieceType.StraightLog, cx + x, yy, cz - half, 0, wood));
                out.add(piece(Piece.PieceType.StraightLog, cx + x, yy, cz + half, 0, wood));
            }
            for (int z = -half + 1; z < half; z++) {
                out.add(piece(Piece.PieceType.StraightLog, cx - half, yy, cz + z, 90, wood));
                out.add(piece(Piece.PieceType.StraightLog, cx + half, yy, cz + z, 90, wood));
            }
        }
    }

    private static void addFort(Result r, float cx, float cz, float ground, float scale, int seed) {
        int half = Math.max(4, Math.round(5 * scale));
        int height = Math.max(3, Math.round(3 * scale));
        addLogWall(r.pieces, cx, cz, ground, half, height, Piece.PieceColor.Oak);

        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                float tx = cx + sx * half;
                float tz = cz + sz * half;
                for (int y = 0; y < height + 2; y++) {
                    r.pieces.add(piece(Piece.PieceType.LogVert, tx,
                            ground + 0.5f + y, tz, 0, Piece.PieceColor.Pine));
                }
                r.pieces.add(piece(Piece.PieceType.RoofPeak, tx,
                        ground + height + 2.0f, tz, 0, Piece.PieceColor.Natural));
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

    private static void addTemple(Result r, float cx, float cz, float ground, float scale, int seed) {
        int radius = Math.max(3, Math.round(3 * scale));
        int height = Math.max(3, Math.round(4 * scale));
        Piece.PieceColor[] woods = {
            Piece.PieceColor.Cedar, Piece.PieceColor.Oak, Piece.PieceColor.Mahogany
        };

        for (int x : new int[]{-radius, radius}) {
            for (int z : new int[]{-radius, radius}) {
                for (int y = 0; y < height; y++) {
                    r.pieces.add(piece(Piece.PieceType.LogVert, cx + x,
                            ground + 0.5f + y, cz + z, 0,
                            woods[Math.floorMod(seed + x + z, woods.length)]));
                }
            }
        }

        for (int x = -radius; x <= radius; x++) {
            r.pieces.add(piece(Piece.PieceType.PlankWide, cx + x,
                    ground + height + 0.1f, cz, 0, Piece.PieceColor.Cedar));
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

            if (roll < cfg.fortChance) {
                if (magnetix) addMagnetixFort(r, x, z, ground, localScale, seed + i);
                else addFort(r, x, z, ground, localScale, seed + i);
            } else if (roll < cfg.fortChance + cfg.templeChance) {
                if (magnetix) addMagnetixTemple(r, x, z, ground, localScale, seed + i);
                else addTemple(r, x, z, ground, localScale, seed + i);
            } else if (roll < cfg.fortChance + cfg.templeChance + cfg.bridgeChance) {
                addBridge(r, x, z, ground, localScale, magnetix, seed + i);
            } else {
                addTemple(r, x, z, ground, Math.max(0.7f, localScale * 0.8f), seed + i);
            }
            made++;
        }
        return r;
    }
}
