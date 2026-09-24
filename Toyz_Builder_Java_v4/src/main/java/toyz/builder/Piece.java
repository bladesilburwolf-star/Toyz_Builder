package toyz.builder;

// jaylib 6.x: functions + struct types live in com.raylib.Raylib,
// color constants in com.raylib.Colors, struct factories in com.raylib.Helpers.
import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/** Port of piece.h / piece.cpp — piece types, definitions, models, rendering. */
public final class Piece {

    public enum PieceType {
        StraightLog, NotchedLog, CornerLog, Roof, Window,
        MagnetixBall, MagnetixRod, MagnetixTriangle, MagnetixSquare, MagnetixFlag,
        TechLight, TechEngine, TechPulley,
        LoloBlock, LoloPlayer
    }

    public enum PieceColor {
        Natural, Red, Green, Blue, Yellow, Purple, Orange, Pink, White
    }

    public enum PieceLength { SHT, MED, LRG }
    public enum PieceOrient { HORIZ, VERT, ANGLE }

    public static class SnapPoint {
        public final Vector3 position;
        public final Vector3 normal;
        public SnapPoint(float px, float py, float pz, float nx, float ny, float nz) {
            position = Helpers.newVector3(px, py, pz);
            normal = Helpers.newVector3(nx, ny, nz);
        }
    }

    public static class PieceDef {
        public PieceType type;
        public String name;
        public Model model;
        public RenderTexture icon;
        public List<SnapPoint> snapPoints = new ArrayList<>();
        public float rotationStepDeg = 90f;
        public Vector3 halfExtents = Helpers.newVector3(0.3f, 0.3f, 0.6f);
        public PieceColor defaultColor = PieceColor.Natural;
        public PieceLength length = PieceLength.MED;
        public PieceOrient orient = PieceOrient.HORIZ;
        public boolean isMagnetic = false;
        public boolean hasLight = false;
    }

    public static class PlacedPiece {
        public PieceType type;
        public Vector3 position = Helpers.newVector3(0, 0, 0);
        public float rotationY;
        public float rotationX;
        public float rotationZ;
        public boolean selected = false;
        public boolean snapHighlight = false;
        public PieceColor color = PieceColor.Natural;
        public PieceLength length = PieceLength.MED;
    }

    private Piece() {}

    // ---- shared textures (loaded once, unloaded with the defs) ----
    private static Texture texBark, texMetal, texPlanks, texStone, texGlass;

    private static Texture tryLoadTex(String path) {
        Texture t = LoadTexture(path);
        if (t != null && t.id() != 0) {
            SetTextureFilter(t, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(t, TEXTURE_WRAP_REPEAT);
            return t;
        }
        return null;
    }

    private static void applyDiffuse(Model model, Texture tex, Color tint) {
        if (model == null || model.materialCount() < 1) return;
        if (tex != null && tex.id() != 0) {
            SetMaterialTexture(model.materials().position(0), MATERIAL_MAP_DIFFUSE, tex);
        }
        model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(tint);
    }

    private static Model makeLogModel(float length) {
        Model model = LoadModelFromMesh(GenMeshCylinder(0.25f, length, 12));
        applyDiffuse(model, texBark, WHITE);
        return model;
    }
    private static Model makeBallModel() {
        Model model = LoadModelFromMesh(GenMeshSphere(0.22f, 12, 12));
        applyDiffuse(model, texMetal, WHITE);
        return model;
    }
    private static Model makeRodModel(float len) {
        Model model = LoadModelFromMesh(GenMeshCylinder(0.08f, len, 10));
        applyDiffuse(model, texMetal, WHITE);
        return model;
    }
    private static Model makePanelModel() {
        Model model = LoadModelFromMesh(GenMeshCube(1.0f, 0.08f, 0.6f));
        applyDiffuse(model, texMetal, WHITE);
        return model;
    }
    private static Model makeLoloBlockModel() {
        Model model = LoadModelFromMesh(GenMeshCube(1.0f, 1.0f, 1.0f));
        applyDiffuse(model, texPlanks, WHITE);
        return model;
    }

    private static RenderTexture renderIcon(Model model, Color tint, int size) {
        RenderTexture target = LoadRenderTexture(size, size);
        Camera3D cam = Helpers.newCamera(
            Helpers.newVector3(2f, 2f, 2f), Helpers.newVector3(0, 0, 0),
            Helpers.newVector3(0, 1f, 0), 35f, CAMERA_PERSPECTIVE);
        BeginTextureMode(target);
        ClearBackground(BLANK);
        BeginMode3D(cam);
        DrawModel(model, Helpers.newVector3(0, 0, 0), 1f, tint);
        EndMode3D();
        EndTextureMode();
        return target;
    }

    public static Color getColorFromEnum(PieceColor c) {
        switch (c) {
            case Red: return RED;
            case Green: return GREEN;
            case Blue: return BLUE;
            case Yellow: return YELLOW;
            case Purple: return PURPLE;
            case Orange: return ORANGE;
            case Pink: return PINK;
            case White: return WHITE;
            default: return Helpers.newColor(220, 200, 170, 255); // natural wood
        }
    }

    public static List<PieceDef> loadPieceDefs() {
        if (texBark == null)   texBark   = tryLoadTex("assets/textures/bark1.png");
        if (texMetal == null)  texMetal  = tryLoadTex("assets/textures/metal1.png");
        if (texPlanks == null) texPlanks = tryLoadTex("assets/textures/planks1.png");
        if (texStone == null)  texStone  = tryLoadTex("assets/textures/stone1.png");
        if (texGlass == null)  texGlass  = tryLoadTex("assets/textures/glass1.png");

        List<PieceDef> defs = new ArrayList<>();

        // --- Lincoln Logs ---
        PieceDef d;
        d = new PieceDef(); d.type = PieceType.StraightLog; d.name = "Straight Log";
        d.model = makeLogModel(1.0f); d.icon = renderIcon(d.model, WHITE, 128);
        d.snapPoints.add(new SnapPoint(0, 0, 0.5f, 0, 0, 1));
        d.snapPoints.add(new SnapPoint(0, 0, -0.5f, 0, 0, -1));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.NotchedLog; d.name = "Notched Log";
        d.model = makeLogModel(1.0f);
        d.icon = renderIcon(d.model, Helpers.newColor(230, 210, 180, 255), 128);
        d.snapPoints.add(new SnapPoint(0, 0, 0.5f, 0, 0, 1));
        d.snapPoints.add(new SnapPoint(0, 0, -0.5f, 0, 0, -1));
        d.snapPoints.add(new SnapPoint(0.5f, 0, 0, 1, 0, 0));
        defs.add(d);

        // --- Magnetix ---
        d = new PieceDef(); d.type = PieceType.MagnetixBall; d.name = "MAG Ball";
        d.model = makeBallModel(); d.icon = renderIcon(d.model, LIGHTGRAY, 128);
        d.isMagnetic = true; d.halfExtents = Helpers.newVector3(0.25f, 0.25f, 0.25f);
        d.snapPoints.add(new SnapPoint(0, 0, 0, 0, 1, 0));
        d.snapPoints.add(new SnapPoint(0.22f, 0, 0, 1, 0, 0));
        d.snapPoints.add(new SnapPoint(-0.22f, 0, 0, -1, 0, 0));
        d.snapPoints.add(new SnapPoint(0, 0, 0.22f, 0, 0, 1));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.MagnetixRod; d.name = "MAG Rod RED";
        d.model = makeRodModel(2.0f); d.icon = renderIcon(d.model, RED, 128);
        d.isMagnetic = true; d.defaultColor = PieceColor.Red; d.length = PieceLength.MED;
        d.snapPoints.add(new SnapPoint(-1, 0, 0, -1, 0, 0));
        d.snapPoints.add(new SnapPoint(1, 0, 0, 1, 0, 0));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(255, 80, 80, 255));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.MagnetixRod; d.name = "MAG Rod BLUE";
        d.model = makeRodModel(2.0f); d.icon = renderIcon(d.model, BLUE, 128);
        d.isMagnetic = true; d.defaultColor = PieceColor.Blue;
        d.snapPoints.add(new SnapPoint(-1, 0, 0, -1, 0, 0));
        d.snapPoints.add(new SnapPoint(1, 0, 0, 1, 0, 0));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(80, 120, 255, 255));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.MagnetixTriangle; d.name = "Xtreme Triangle";
        d.model = makePanelModel(); d.icon = renderIcon(d.model, GREEN, 128);
        d.isMagnetic = true; d.defaultColor = PieceColor.Green;
        d.snapPoints.add(new SnapPoint(-0.5f, 0, 0, -1, 0, 0));
        d.snapPoints.add(new SnapPoint(0.5f, 0, 0, 1, 0, 0));
        d.snapPoints.add(new SnapPoint(0, 0, 0.3f, 0, 0, 1));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(80, 220, 100, 255));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.MagnetixSquare; d.name = "Xtreme Square";
        d.model = makePanelModel(); d.icon = renderIcon(d.model, YELLOW, 128);
        d.isMagnetic = true; d.defaultColor = PieceColor.Yellow;
        d.snapPoints.add(new SnapPoint(-0.5f, 0, 0, -1, 0, 0));
        d.snapPoints.add(new SnapPoint(0.5f, 0, 0, 1, 0, 0));
        d.snapPoints.add(new SnapPoint(0, 0, 0.5f, 0, 0, 1));
        d.snapPoints.add(new SnapPoint(0, 0, -0.5f, 0, 0, -1));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(255, 230, 80, 255));
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.MagnetixFlag; d.name = "Castle Flag";
        d.model = makePanelModel(); d.icon = renderIcon(d.model, SKYBLUE, 128);
        d.snapPoints.add(new SnapPoint(0, 0, 0, 0, 1, 0));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(100, 180, 255, 255));
        defs.add(d);

        // --- TECH ---
        d = new PieceDef(); d.type = PieceType.TechLight; d.name = "TECH Light (Flashing)";
        d.model = makeBallModel(); d.icon = renderIcon(d.model, RED, 128);
        d.hasLight = true;
        d.snapPoints.add(new SnapPoint(0, 0, 0, 0, 1, 0));
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(255, 60, 40, 255));
        defs.add(d);

        // --- Lolo ---
        d = new PieceDef(); d.type = PieceType.LoloBlock; d.name = "Lolo Block (Pushable)";
        d.model = makeLoloBlockModel(); d.icon = renderIcon(d.model, BEIGE, 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        defs.add(d);

        d = new PieceDef(); d.type = PieceType.LoloPlayer; d.name = "Lolo Mascot";
        d.model = makeLoloBlockModel(); d.icon = renderIcon(d.model, PINK, 128);
        d.halfExtents = Helpers.newVector3(0.4f, 0.5f, 0.4f);
        d.model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE)
            .color(Helpers.newColor(255, 180, 200, 255));
        defs.add(d);

        return defs;
    }

    public static void unloadPieceDefs(List<PieceDef> defs) {
        if (defs == null) return;
        for (PieceDef d : defs) {
            if (d == null) continue;
            // Unload models first; shared textures are freed once below
            if (d.model != null && !d.model.isNull()) {
                try { UnloadModel(d.model); } catch (Throwable ignored) {}
                d.model = null;
            }
            if (d.icon != null && !d.icon.isNull()) {
                try { UnloadRenderTexture(d.icon); } catch (Throwable ignored) {}
                d.icon = null;
            }
        }
        defs.clear();
        // Shared textures — unload once only
        unloadTex(texBark);   texBark = null;
        unloadTex(texMetal);  texMetal = null;
        unloadTex(texPlanks); texPlanks = null;
        unloadTex(texStone);  texStone = null;
        unloadTex(texGlass);  texGlass = null;
    }

    private static void unloadTex(Texture t) {
        if (t == null || t.isNull()) return;
        try { UnloadTexture(t); } catch (Throwable ignored) {}
    }

    public static void drawPlacedPiece(PlacedPiece piece, List<PieceDef> defs) {
        PieceDef def = findDef(defs, piece.type, piece.color);
        if (def == null) return;

        Color tint = WHITE;
        if (piece.type == PieceType.StraightLog || piece.type == PieceType.NotchedLog) {
            tint = WHITE;
        } else if (piece.type == PieceType.MagnetixRod || piece.type == PieceType.MagnetixTriangle
                || piece.type == PieceType.MagnetixSquare || piece.type == PieceType.MagnetixFlag
                || piece.type == PieceType.TechLight) {
            tint = getColorFromEnum(piece.color);
            if (piece.color == PieceColor.Natural) tint = WHITE;
        } else if (piece.type == PieceType.MagnetixBall) {
            tint = LIGHTGRAY;
        }

        if (piece.selected) tint = YELLOW;
        else if (piece.snapHighlight) tint = RED;

        float heightFactor = clamp(piece.position.y() / 12f, 0f, 1f);
        float m = 0.85f + heightFactor * 0.15f;
        int tr = tint.r() & 0xFF, tg = tint.g() & 0xFF, tb = tint.b() & 0xFF, ta = tint.a() & 0xFF;
        Color shaded = Helpers.newColor((int) (tr * m), (int) (tg * m), (int) (tb * m), ta);

        DrawModelEx(def.model, piece.position,
                   Helpers.newVector3(0, 1, 0), piece.rotationY,
                   Helpers.newVector3(1, 1, 1), shaded);
        if (Math.abs(piece.rotationX) > 0.01f) {
            DrawModelEx(def.model, piece.position,
                       Helpers.newVector3(1, 0, 0), piece.rotationX,
                       Helpers.newVector3(1, 1, 1), Fade(shaded, 0.35f));
        }

        if (def.isMagnetic && def.type == PieceType.MagnetixRod) {
            float ry = (float) Math.toRadians(piece.rotationY);
            Vector3 end1 = Vector3Add(piece.position,
                Vector3RotateByAxisAngle(Helpers.newVector3(-1, 0, 0), Helpers.newVector3(0, 1, 0), ry));
            Vector3 end2 = Vector3Add(piece.position,
                Vector3RotateByAxisAngle(Helpers.newVector3(1, 0, 0), Helpers.newVector3(0, 1, 0), ry));
            DrawSphere(end1, 0.11f, WHITE);
            DrawSphere(end2, 0.11f, WHITE);
            if (piece.snapHighlight) {
                DrawSphereWires(end1, 0.18f, 8, 8, YELLOW);
                DrawSphereWires(end2, 0.18f, 8, 8, YELLOW);
            }
        }
    }

    public static BoundingBox getPieceWorldBounds(PlacedPiece piece, PieceDef def) {
        float r = Math.max(def.halfExtents.x(),
                  Math.max(def.halfExtents.y(), def.halfExtents.z()));
        return Helpers.newBoundingBox(
            Helpers.newVector3(piece.position.x() - r, piece.position.y() - r, piece.position.z() - r),
            Helpers.newVector3(piece.position.x() + r, piece.position.y() + r, piece.position.z() + r));
    }

    /** Resolves the def for a placed piece; rods must also match their color. */
    public static PieceDef findDef(List<PieceDef> defs, PieceType type, PieceColor color) {
        for (PieceDef d : defs) {
            if (d.type == type) {
                if (type == PieceType.MagnetixRod) {
                    if (d.defaultColor == color) return d;
                } else {
                    return d;
                }
            }
        }
        return null;
    }

    static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }
}