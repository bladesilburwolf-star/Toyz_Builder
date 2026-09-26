package toyz.builder;

import com.raylib.Helpers;
import org.bytedeco.javacpp.FloatPointer;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Piece catalog v5 — Lincoln Logs (wood variants, roofs, doors, windows, signs, flags)
 * and expanded Magnetix (metal balls, rainbow rods H/V, polygons). Grok task.
 */
public final class Piece {

    public enum PieceType {
        // Lincoln Logs
        StraightLog, LogVert, NotchedLog, CornerLog,
        HalfLog, LogStub, Plank, PlankWide,
        Roof, RoofPeak, Window, Door, Sign, FabricFlag,
        // Magnetix
        MagnetixBall, MagnetixRod, MagnetixRodVert,
        MagnetixTriangle, MagnetixSquare, MagnetixHex, MagnetixFlag,
        // Tech / Lolo
        TechLight, TechEngine, TechPulley,
        LoloBlock, LoloPlayer,
        // Misc playground
        Ramp, TeeterTotter, Boulder, Slide,
        BlockWood, BlockStone, BlockMetal, BlockSand, BlockGlass,
        BlockIce, BlockSnow, BlockCopper, BlockIron, BlockTitanium,
        BlockCrystal, BlockDiamond, BlockQuartz, BlockConcrete,
        BlockMagma, BlockDirt, BlockGrass, BlockMagnecite,
        MetalCage, FloorPlank,
        // Erector Set (append only — ordinals for map save)
        ErectorBarH, ErectorBarV, ErectorBracket, ErectorLightBulb,
        ErectorPulley, ErectorSlide, ErectorSwitch, ErectorCircuitBoard
    }

    public enum PieceColor {
        Natural, Red, Green, Blue, Yellow, Purple, Orange, Pink, White,
        // wood stains / metal finishes (shown via name + tint)
        Pine, Oak, Cedar, Cherry, Walnut, Birch, Mahogany, Teak,
        Steel, Iron, Titanium, Black
    }

    public enum PieceLength { SHT, MED, LRG }
    public enum PieceOrient { HORIZ, VERT, ANGLE }

    /** Mega-Block scale: fat cylinders fill ~1.0 world unit diameter on 0.5 stud grid. */
    public static final float LOG_RADIUS = 0.42f;
    public static final float LOG_LENGTH = 1.0f; // 2 studs on 0.5 grid
    public static final float STUB_HEIGHT = 0.40f; // cut-tree stump
    public static final float HALF_LOG_LEN = 1.0f;

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
        public Vector3 halfExtents = Helpers.newVector3(0.42f, 0.42f, 0.5f);
        public PieceColor defaultColor = PieceColor.Natural;
        public PieceLength length = PieceLength.MED;
        public PieceOrient orient = PieceOrient.HORIZ;
        public boolean isMagnetic = false;
        public boolean hasLight = false;
        public boolean isDynamic = false; // participates in simple gravity
        public int category = 0; // 1 Lincoln, 2 Magnetix, 3 Tech, 4 Lolo, 5 Misc
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
        // Simple physics (boulders / teeter)
        public boolean dynamic = false;
        public float velY = 0f;
        public float angVel = 0f; // teeter beam
        public boolean grounded = true;
    }

    private Piece() {}

    private static Texture texBark, texBark2, texMetal, texMetal2, texMetal3;
    private static Texture texIce, texSnow, texCopper, texCrystal;
    private static Texture texPlanks, texStone, texGlass, texRoof, texRoof2;
    private static Texture texGrass, texDirt, texSand, texMagma, texRock, texHouse;

    private static Texture tryLoadTex(String path) {
        Texture t = LoadTexture(path);
        if (t != null && t.id() != 0) {
            SetTextureFilter(t, TEXTURE_FILTER_BILINEAR);
            SetTextureWrap(t, TEXTURE_WRAP_REPEAT);
            return t;
        }
        return null;
    }

    /** Try several relative paths (flat + folder layout). */
    private static Texture tryLoadTexAny(String... paths) {
        for (String path : paths) {
            Texture t = tryLoadTex(path);
            if (t != null) return t;
        }
        return null;
    }

    /** Prefer authored GLB over procedural mesh when present. */
    private static Model tryLoadModel(String path) {
        try {
            java.io.File f = new java.io.File(path);
            if (!f.isFile()) return null;
            Model m = LoadModel(path);
            if (m != null && m.meshCount() > 0) {
                System.out.println("[Piece] GLB: " + path + " meshes=" + m.meshCount());
                return m;
            }
        } catch (Throwable t) {
            System.err.println("[Piece] LoadModel fail " + path + ": " + t.getMessage());
        }
        return null;
    }

    private static Model tryLoadModelAny(String... paths) {
        for (String path : paths) {
            Model m = tryLoadModel(path);
            if (m != null) return m;
        }
        return null;
    }

    private static String woodKey(PieceColor c) {
        if (c == null) return "oak";
        switch (c) {
            case Pine: return "pine";
            case Oak: return "oak";
            case Cedar: return "cedar";
            case Cherry: return "cherry";
            case Walnut: return "walnut";
            case Birch: return "birch";
            case Mahogany: return "mahogany";
            case Teak: return "teak";
            default: return "oak";
        }
    }

    private static void applyDiffuse(Model model, Texture tex, Color tint) {
        if (model == null || model.materialCount() < 1) return;
        if (tex != null && tex.id() != 0) {
            SetMaterialTexture(model.materials().position(0), MATERIAL_MAP_DIFFUSE, tex);
        }
        model.materials().position(0).maps().position(MATERIAL_MAP_DIFFUSE).color(tint);
    }

    /** Rotate mesh 90° around X so a Y-up cylinder lies along +Z (true horizontal log). */
    private static void bakeCylinderHorizontal(Mesh mesh) {
        if (mesh == null || mesh.isNull() || mesh.vertices() == null) return;
        int vc = mesh.vertexCount();
        FloatPointer v = mesh.vertices();
        FloatPointer n = mesh.normals();
        for (int i = 0; i < vc; i++) {
            float x = v.get(i * 3);
            float y = v.get(i * 3 + 1);
            float z = v.get(i * 3 + 2);
            // Rx(90°): (x,y,z) -> (x, -z, y)
            v.put(i * 3, x);
            v.put(i * 3 + 1, -z);
            v.put(i * 3 + 2, y);
            if (n != null) {
                float nx = n.get(i * 3);
                float ny = n.get(i * 3 + 1);
                float nz = n.get(i * 3 + 2);
                n.put(i * 3, nx);
                n.put(i * 3 + 1, -nz);
                n.put(i * 3 + 2, ny);
            }
        }
    }

    /** Keep only +X half of a Y-up cylinder → flat face + rounded back (building half-log). */
    private static void bakeHalfCylinder(Mesh mesh) {
        if (mesh == null || mesh.isNull() || mesh.vertices() == null) return;
        int vc = mesh.vertexCount();
        FloatPointer v = mesh.vertices();
        FloatPointer n = mesh.normals();
        for (int i = 0; i < vc; i++) {
            float x = v.get(i * 3);
            if (x < 0f) {
                v.put(i * 3, 0f);
                if (n != null) {
                    n.put(i * 3, -1f);
                    n.put(i * 3 + 1, 0f);
                    n.put(i * 3 + 2, 0f);
                }
            }
        }
    }

    private static Model makeLogModel(float length, Texture bark, Color tint, boolean horizontal) {
        Mesh mesh = GenMeshCylinder(LOG_RADIUS, length, 14);
        if (horizontal) bakeCylinderHorizontal(mesh);
        Model model = LoadModelFromMesh(mesh);
        applyDiffuse(model, bark != null ? bark : texBark, tint);
        return model;
    }

    private static Model makeHalfLogModel(float length, Texture bark, Color tint, boolean horizontal) {
        Mesh mesh = GenMeshCylinder(LOG_RADIUS, length, 16);
        bakeHalfCylinder(mesh);
        if (horizontal) bakeCylinderHorizontal(mesh);
        Model model = LoadModelFromMesh(mesh);
        applyDiffuse(model, bark != null ? bark : texBark, tint);
        return model;
    }

    private static Model makeStubModel(Texture bark, Color tint) {
        Mesh mesh = GenMeshCylinder(LOG_RADIUS * 1.05f, STUB_HEIGHT, 14);
        Model model = LoadModelFromMesh(mesh);
        applyDiffuse(model, bark != null ? bark : texBark, tint);
        return model;
    }

    private static Model makePlankModel(float sx, float sy, float sz, Texture tex, Color tint) {
        Model model = LoadModelFromMesh(GenMeshCube(sx, sy, sz));
        applyDiffuse(model, tex != null ? tex : texPlanks, tint);
        return model;
    }

    private static Model makeLogModel(float length, Texture bark, Color tint) {
        return makeLogModel(length, bark, tint, false);
    }

    private static Model makeCubeModel(float sx, float sy, float sz, Texture tex, Color tint) {
        Model model = LoadModelFromMesh(GenMeshCube(sx, sy, sz));
        applyDiffuse(model, tex, tint);
        return model;
    }

    private static Model makeBallModel(Texture tex, Color tint) {
        Model model = LoadModelFromMesh(GenMeshSphere(0.22f, 12, 12));
        applyDiffuse(model, tex, tint);
        return model;
    }

    private static Model makeRodModel(float len, Texture tex, Color tint, boolean horizontal) {
        Mesh mesh = GenMeshCylinder(0.08f, len, 10);
        if (horizontal) bakeCylinderHorizontal(mesh);
        Model model = LoadModelFromMesh(mesh);
        applyDiffuse(model, tex, tint);
        return model;
    }

    private static Model makeRodModel(float len, Texture tex, Color tint) {
        return makeRodModel(len, tex, tint, false);
    }

    private static Model makePanelModel(Texture tex, Color tint) {
        Model model = LoadModelFromMesh(GenMeshCube(1.0f, 0.08f, 0.6f));
        applyDiffuse(model, tex, tint);
        return model;
    }

    private static Model makeRoofPeakModel(Texture tex, Color tint) {
        // wedge-ish: use cube scaled thin + angled via rotation on place
        Model model = LoadModelFromMesh(GenMeshCube(1.2f, 0.15f, 0.7f));
        applyDiffuse(model, tex, tint);
        return model;
    }

    private static Model makeHexPanel(Texture tex, Color tint) {
        Model model = LoadModelFromMesh(GenMeshCylinder(0.45f, 0.08f, 6));
        applyDiffuse(model, tex, tint);
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
            case Pine: return Helpers.newColor(200, 185, 140, 255);
            case Oak: return Helpers.newColor(180, 140, 90, 255);
            case Cedar: return Helpers.newColor(160, 100, 70, 255);
            case Cherry: return Helpers.newColor(150, 80, 70, 255);
            case Walnut: return Helpers.newColor(90, 60, 40, 255);
            case Birch: return Helpers.newColor(230, 220, 200, 255);
            case Mahogany: return Helpers.newColor(120, 50, 40, 255);
            case Teak: return Helpers.newColor(170, 130, 70, 255);
            case Steel: return Helpers.newColor(180, 185, 190, 255);
            case Black: return Helpers.newColor(18, 18, 22, 255);
            case Iron: return Helpers.newColor(110, 110, 115, 255);
            case Titanium: return Helpers.newColor(200, 205, 210, 255);
            default: return Helpers.newColor(220, 200, 170, 255);
        }
    }

    private static void addSnapsEndsZ(PieceDef d, float half) {
        d.snapPoints.add(new SnapPoint(0, 0, half, 0, 0, 1));
        d.snapPoints.add(new SnapPoint(0, 0, -half, 0, 0, -1));
    }

    private static void addSnapsEndsY(PieceDef d, float half) {
        d.snapPoints.add(new SnapPoint(0, half, 0, 0, 1, 0));
        d.snapPoints.add(new SnapPoint(0, -half, 0, 0, -1, 0));
    }

    private static void addSnapsEndsX(PieceDef d, float half) {
        d.snapPoints.add(new SnapPoint(half, 0, 0, 1, 0, 0));
        d.snapPoints.add(new SnapPoint(-half, 0, 0, -1, 0, 0));
    }

    public static List<PieceDef> loadPieceDefs() {
        // 512px pack under assets/textures/<category>/ (+ legacy flat paths)
        if (texBark == null)   texBark   = tryLoadTexAny(
            "assets/textures/trees/bark1.png", "assets/textures/bark1.png",
            "assets/textures/wood/oakplank.jpg", "assets/textures/wood/oaklog.jpg");
        if (texBark2 == null)  texBark2  = tryLoadTexAny(
            "assets/textures/trees/bark2.png", "assets/textures/bark2.png",
            "assets/textures/wood/pineplank.jpg", "assets/textures/wood/pinelog.jpg");
        if (texMetal == null)  texMetal  = tryLoadTexAny(
            "assets/textures/metals/steel.jpg", "assets/textures/metals/metal1.png",
            "assets/textures/metal1.png");
        if (texMetal2 == null) texMetal2 = tryLoadTexAny(
            "assets/textures/metals/ironbars.png", "assets/textures/metals/ironbars.jpg",
            "assets/textures/metals/metal2.png", "assets/textures/metals/copper.jpg");
        if (texMetal3 == null) texMetal3 = tryLoadTexAny(
            "assets/textures/metals/titanium.jpg", "assets/textures/metals/castiron.jpg",
            "assets/textures/metals/metal3.png");
        if (texPlanks == null) texPlanks = tryLoadTexAny(
            "assets/textures/wood/oakplank.jpg", "assets/textures/wood/pineplank.jpg",
            "assets/textures/wood/planks1.png", "assets/textures/planks1.png",
            "assets/textures/house/housewall1.png");
        if (texStone == null)  texStone  = tryLoadTexAny(
            "assets/textures/stone/stone1.png", "assets/textures/stone1.png",
            "assets/textures/stone/concrete.jpg", "assets/textures/rocks/rock1.png");
        if (texGlass == null)  texGlass  = tryLoadTexAny(
            "assets/textures/glass/glass1.png", "assets/textures/glass1.png",
            "assets/textures/glass/glass2.png");
        if (texRoof == null)   texRoof   = tryLoadTexAny(
            "assets/textures/house/roof2.png", "assets/textures/roof2.png",
            "assets/textures/house/rooff1.png");
        if (texRoof2 == null)  texRoof2  = tryLoadTexAny(
            "assets/textures/house/roof3.png", "assets/textures/roof3.png");
        if (texGrass == null)  texGrass  = tryLoadTexAny(
            "assets/textures/grass/grass1.png", "assets/textures/grass1.png");
        if (texDirt == null)   texDirt   = tryLoadTexAny(
            "assets/textures/dirt/dirt1.png", "assets/textures/dirt1.png");
        if (texSand == null)   texSand   = tryLoadTexAny(
            "assets/textures/sand/sand1.png", "assets/textures/sand1.png");
        if (texMagma == null)  texMagma  = tryLoadTexAny(
            "assets/textures/magma/magma1.png", "assets/textures/magma1.png");
        if (texIce == null)    texIce    = tryLoadTexAny(
            "assets/textures/ice/ice.jpg", "assets/textures/ice/ice.png");
        if (texSnow == null)   texSnow   = tryLoadTexAny(
            "assets/textures/snow/snow.png", "assets/textures/snow/snow.jpg");
        if (texCopper == null) texCopper = tryLoadTexAny(
            "assets/textures/metals/copper.jpg");
        if (texCrystal == null) texCrystal = tryLoadTexAny(
            "assets/textures/rocks/crystal.png", "assets/textures/rocks/quartz.jpg",
            "assets/textures/rocks/diamond.png");
        if (texRock == null)   texRock   = tryLoadTexAny(
            "assets/textures/rocks/rock1.png", "assets/textures/rock1.png",
            "assets/textures/rocks/rock2.png");
        if (texHouse == null)  texHouse  = tryLoadTexAny(
            "assets/textures/house/housewall1.png", "assets/textures/housewall1.png");
        System.out.println("[Piece] textures bark=" + (texBark!=null) + " metal=" + (texMetal!=null)
            + " planks=" + (texPlanks!=null) + " stone=" + (texStone!=null) + " grass=" + (texGrass!=null));

        List<PieceDef> defs = new ArrayList<>();
        PieceDef d;

        // ========== LINCOLN LOGS — 8 wood variants (H + sample V) ==========
        PieceColor[] woods = {
            PieceColor.Pine, PieceColor.Oak, PieceColor.Cedar, PieceColor.Cherry,
            PieceColor.Walnut, PieceColor.Birch, PieceColor.Mahogany, PieceColor.Teak
        };
        String[] woodNames = {
            "Pine", "Oak", "Cedar", "Cherry", "Walnut", "Birch", "Mahogany", "Teak"
        };

        for (int i = 0; i < woods.length; i++) {
            Color tint = getColorFromEnum(woods[i]);
            Texture bark = (i % 2 == 0) ? texBark : (texBark2 != null ? texBark2 : texBark);

            // Horizontal log
            d = new PieceDef();
            d.type = PieceType.StraightLog;
            d.name = woodNames[i] + " Log H";
            d.defaultColor = woods[i];
            d.orient = PieceOrient.HORIZ;
            d.category = 1;
            d.model = tryLoadModelAny(
                "assets/models/" + woodKey(woods[i]) + "logh.glb",
                "assets/models/" + woodKey(woods[i]) + "logblock.glb");
            if (d.model == null) d.model = makeLogModel(LOG_LENGTH, bark, tint, true); // length along Z
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS, LOG_RADIUS, LOG_LENGTH * 0.5f);
            addSnapsEndsZ(d, LOG_LENGTH * 0.5f);
            defs.add(d);

            // Vertical log (post)
            d = new PieceDef();
            d.type = PieceType.LogVert;
            d.name = woodNames[i] + " Log V";
            d.defaultColor = woods[i];
            d.orient = PieceOrient.VERT;
            d.category = 1;
            d.model = tryLoadModelAny("assets/models/" + woodKey(woods[i]) + "logv.glb");
            if (d.model == null) d.model = makeLogModel(LOG_LENGTH, bark, tint, false);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS, LOG_LENGTH * 0.5f, LOG_RADIUS);
            addSnapsEndsY(d, LOG_LENGTH * 0.5f);
            defs.add(d);
        }

        // Notched + corner (oak default)
        d = new PieceDef();
        d.type = PieceType.NotchedLog; d.name = "Notched Log";
        d.category = 1; d.defaultColor = PieceColor.Oak;
        d.orient = PieceOrient.HORIZ;
        d.model = makeLogModel(LOG_LENGTH, texBark, getColorFromEnum(PieceColor.Oak), true);
        d.halfExtents = Helpers.newVector3(LOG_RADIUS, LOG_RADIUS, LOG_LENGTH * 0.5f);
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
        addSnapsEndsZ(d, LOG_LENGTH * 0.5f);
        d.snapPoints.add(new SnapPoint(LOG_RADIUS, 0, 0, 1, 0, 0));
        d.snapPoints.add(new SnapPoint(-LOG_RADIUS, 0, 0, -1, 0, 0));
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.CornerLog; d.name = "Corner Log";
        d.category = 1; d.defaultColor = PieceColor.Cedar;
        d.orient = PieceOrient.HORIZ;
        d.model = makeLogModel(LOG_LENGTH, texBark2 != null ? texBark2 : texBark, getColorFromEnum(PieceColor.Cedar), true);
        d.halfExtents = Helpers.newVector3(LOG_RADIUS, LOG_RADIUS, LOG_LENGTH * 0.5f);
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Cedar), 128);
        addSnapsEndsZ(d, 0.5f);
        addSnapsEndsX(d, 0.5f);
        defs.add(d);

        // Roofs
        d = new PieceDef();
        // ---- Half-logs (semi) — flat face + round back, good wall/floor rails ----
        PieceColor[] halfWoods = { PieceColor.Oak, PieceColor.Pine, PieceColor.Walnut };
        String[] halfNames = { "Oak", "Pine", "Walnut" };
        for (int i = 0; i < halfWoods.length; i++) {
            Color tint = getColorFromEnum(halfWoods[i]);
            Texture bark = (i % 2 == 0) ? texBark : (texBark2 != null ? texBark2 : texBark);
            d = new PieceDef();
            d.type = PieceType.HalfLog;
            d.name = halfNames[i] + " Half-Log H";
            d.defaultColor = halfWoods[i];
            d.orient = PieceOrient.HORIZ;
            d.category = 1;
            d.model = makeHalfLogModel(HALF_LOG_LEN, bark, tint, true);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS * 0.5f, LOG_RADIUS, HALF_LOG_LEN * 0.5f);
            addSnapsEndsZ(d, HALF_LOG_LEN * 0.5f);
            d.snapPoints.add(new SnapPoint(0, LOG_RADIUS, 0, 0, 1, 0));
            defs.add(d);

            d = new PieceDef();
            d.type = PieceType.HalfLog;
            d.name = halfNames[i] + " Half-Log V";
            d.defaultColor = halfWoods[i];
            d.orient = PieceOrient.VERT;
            d.category = 1;
            d.model = makeHalfLogModel(HALF_LOG_LEN, bark, tint, false);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS * 0.5f, HALF_LOG_LEN * 0.5f, LOG_RADIUS);
            addSnapsEndsY(d, HALF_LOG_LEN * 0.5f);
            defs.add(d);
        }

        // ---- Log stubs / cut-tree stumps (short vertical) ----
        PieceColor[] stubWoods = { PieceColor.Oak, PieceColor.Cedar, PieceColor.Birch, PieceColor.Mahogany };
        String[] stubNames = { "Oak", "Cedar", "Birch", "Mahogany" };
        for (int i = 0; i < stubWoods.length; i++) {
            Color tint = getColorFromEnum(stubWoods[i]);
            Texture bark = (i % 2 == 0) ? texBark : (texBark2 != null ? texBark2 : texBark);
            d = new PieceDef();
            d.type = PieceType.LogStub;
            d.name = stubNames[i] + " Stump";
            d.defaultColor = stubWoods[i];
            d.orient = PieceOrient.VERT;
            d.category = 1;
            d.model = makeStubModel(bark, tint);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS * 1.05f, STUB_HEIGHT * 0.5f, LOG_RADIUS * 1.05f);
            addSnapsEndsY(d, STUB_HEIGHT * 0.5f);
            defs.add(d);
        }

        // ---- Planks (wall / floor boards — door-thickness language) ----
        // Standard plank: door width, shorter height — wall filler
        d = new PieceDef();
        d.type = PieceType.Plank; d.name = "Plank Wall";
        d.category = 1; d.defaultColor = PieceColor.Walnut;
        d.model = makePlankModel(0.7f, 1.0f, 0.10f, texPlanks, getColorFromEnum(PieceColor.Walnut));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Walnut), 128);
        d.halfExtents = Helpers.newVector3(0.35f, 0.5f, 0.05f);
        addSnapsEndsX(d, 0.35f);
        addSnapsEndsY(d, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Plank; d.name = "Plank Pine";
        d.category = 1; d.defaultColor = PieceColor.Pine;
        d.model = makePlankModel(0.7f, 1.0f, 0.10f, texPlanks, getColorFromEnum(PieceColor.Pine));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Pine), 128);
        d.halfExtents = Helpers.newVector3(0.35f, 0.5f, 0.05f);
        addSnapsEndsX(d, 0.35f);
        addSnapsEndsY(d, 0.5f);
        defs.add(d);

        // Wide floor/roof plank (horizontal board)
        d = new PieceDef();
        d.type = PieceType.PlankWide; d.name = "Plank Floor";
        d.category = 1; d.defaultColor = PieceColor.Oak;
        d.orient = PieceOrient.HORIZ;
        d.model = makePlankModel(1.0f, 0.10f, 0.7f, texPlanks, getColorFromEnum(PieceColor.Oak));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.05f, 0.35f);
        addSnapsEndsX(d, 0.5f);
        addSnapsEndsZ(d, 0.35f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.PlankWide; d.name = "Plank Floor Wide";
        d.category = 1; d.defaultColor = PieceColor.Teak;
        d.orient = PieceOrient.HORIZ;
        d.model = makePlankModel(1.5f, 0.10f, 1.0f, texPlanks, getColorFromEnum(PieceColor.Teak));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Teak), 128);
        d.halfExtents = Helpers.newVector3(0.75f, 0.05f, 0.5f);
        addSnapsEndsX(d, 0.75f);
        addSnapsEndsZ(d, 0.5f);
        defs.add(d);

        // Half-height wall plank (window sill / rail height)
        d = new PieceDef();
        d.type = PieceType.Plank; d.name = "Plank Rail";
        d.category = 1; d.defaultColor = PieceColor.Cedar;
        d.model = makePlankModel(0.7f, 0.5f, 0.10f, texPlanks, getColorFromEnum(PieceColor.Cedar));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Cedar), 128);
        d.halfExtents = Helpers.newVector3(0.35f, 0.25f, 0.05f);
        addSnapsEndsX(d, 0.35f);
        addSnapsEndsY(d, 0.25f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Roof; d.name = "Roof Panel";
        d.category = 1; d.defaultColor = PieceColor.Red;
        d.model = makeCubeModel(1.2f, 0.1f, 0.8f, texRoof, getColorFromEnum(PieceColor.Red));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Red), 128);
        d.halfExtents = Helpers.newVector3(0.6f, 0.08f, 0.4f);
        addSnapsEndsX(d, 0.6f);
        addSnapsEndsZ(d, 0.4f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Roof; d.name = "Roof Green";
        d.category = 1; d.defaultColor = PieceColor.Green;
        d.model = makeCubeModel(1.2f, 0.1f, 0.8f, texRoof2 != null ? texRoof2 : texRoof,
            getColorFromEnum(PieceColor.Green));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Green), 128);
        d.halfExtents = Helpers.newVector3(0.6f, 0.08f, 0.4f);
        addSnapsEndsX(d, 0.6f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.RoofPeak; d.name = "Roof Peak";
        d.category = 1; d.defaultColor = PieceColor.Orange;
        d.model = makeRoofPeakModel(texRoof, getColorFromEnum(PieceColor.Orange));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Orange), 128);
        d.halfExtents = Helpers.newVector3(0.6f, 0.1f, 0.35f);
        addSnapsEndsX(d, 0.6f);
        defs.add(d);

        // Window + Door
        d = new PieceDef();
        d.type = PieceType.Window; d.name = "Window";
        d.category = 1;
        d.model = tryLoadModelAny("assets/models/window1.glb");
        if (d.model == null)
            d.model = makeCubeModel(0.9f, 0.9f, 0.12f, texGlass, Helpers.newColor(180, 220, 255, 200));
        d.icon = renderIcon(d.model, Helpers.newColor(180, 220, 255, 255), 128);
        d.halfExtents = Helpers.newVector3(0.45f, 0.45f, 0.08f);
        addSnapsEndsX(d, 0.45f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Door; d.name = "Door";
        d.category = 1; d.defaultColor = PieceColor.Walnut;
        d.model = tryLoadModelAny("assets/models/door1.glb");
        if (d.model == null)
            d.model = makeCubeModel(0.7f, 1.4f, 0.1f, texPlanks, getColorFromEnum(PieceColor.Walnut));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Walnut), 128);
        d.halfExtents = Helpers.newVector3(0.35f, 0.7f, 0.08f);
        addSnapsEndsY(d, 0.7f);
        defs.add(d);

        // Sign + fabric flag
        d = new PieceDef();
        d.type = PieceType.Sign; d.name = "Sign Board";
        d.category = 1; d.defaultColor = PieceColor.Pine;
        d.model = tryLoadModelAny("assets/models/sign1.glb");
        if (d.model == null)
            d.model = makeCubeModel(0.8f, 0.5f, 0.06f, texPlanks, getColorFromEnum(PieceColor.Pine));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Pine), 128);
        d.halfExtents = Helpers.newVector3(0.4f, 0.25f, 0.05f);
        d.snapPoints.add(new SnapPoint(0, -0.25f, 0, 0, -1, 0));
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.FabricFlag; d.name = "Fabric Flag";
        d.category = 1; d.defaultColor = PieceColor.Red;
        d.model = makePanelModel(null, getColorFromEnum(PieceColor.Red));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Red), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.05f, 0.3f);
        d.snapPoints.add(new SnapPoint(-0.5f, 0, 0, -1, 0, 0));
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.FabricFlag; d.name = "Fabric Flag Blue";
        d.category = 1; d.defaultColor = PieceColor.Blue;
        d.model = makePanelModel(null, getColorFromEnum(PieceColor.Blue));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Blue), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.05f, 0.3f);
        d.snapPoints.add(new SnapPoint(-0.5f, 0, 0, -1, 0, 0));
        defs.add(d);

        // ========== MAGNETIX ==========
        // Metal balls
        Object[][] balls = {
            { PieceColor.Steel, "Steel Ball", texMetal },
            { PieceColor.Iron, "Iron Ball", texMetal2 != null ? texMetal2 : texMetal },
            { PieceColor.Titanium, "Titanium Ball", texMetal3 != null ? texMetal3 : texMetal },
            { PieceColor.White, "Light Ball", texMetal }
        };
        for (Object[] b : balls) {
            PieceColor col = (PieceColor) b[0];
            String nm = (String) b[1];
            Texture tex = (Texture) b[2];
            Color tint = getColorFromEnum(col);
            if (col == PieceColor.White) tint = Helpers.newColor(255, 250, 200, 255);
            d = new PieceDef();
            d.type = PieceType.MagnetixBall;
            d.name = nm;
            d.defaultColor = col;
            d.isMagnetic = true;
            d.hasLight = (col == PieceColor.White);
            d.category = 2;
            // Prefer magnetix GLB balls when present
            if (col == PieceColor.Steel || col == PieceColor.Iron)
                d.model = tryLoadModelAny("assets/models/ironball.glb");
            else if (col == PieceColor.White)
                d.model = tryLoadModelAny("assets/models/lightball.glb", "assets/models/glassball.glb");
            else
                d.model = tryLoadModelAny("assets/models/glassball.glb");
            if (d.model == null) d.model = makeBallModel(tex, tint);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(0.25f, 0.25f, 0.25f);
            d.snapPoints.add(new SnapPoint(0, 0.22f, 0, 0, 1, 0));
            d.snapPoints.add(new SnapPoint(0, -0.22f, 0, 0, -1, 0));
            d.snapPoints.add(new SnapPoint(0.22f, 0, 0, 1, 0, 0));
            d.snapPoints.add(new SnapPoint(-0.22f, 0, 0, -1, 0, 0));
            d.snapPoints.add(new SnapPoint(0, 0, 0.22f, 0, 0, 1));
            d.snapPoints.add(new SnapPoint(0, 0, -0.22f, 0, 0, -1));
            defs.add(d);
        }

        // Rainbow rods H + V
        PieceColor[] rodColors = {
            PieceColor.Red, PieceColor.Orange, PieceColor.Yellow, PieceColor.Green,
            PieceColor.Blue, PieceColor.Purple, PieceColor.Pink, PieceColor.White
        };
        for (PieceColor col : rodColors) {
            Color tint = getColorFromEnum(col);
            // Horizontal
            d = new PieceDef();
            d.type = PieceType.MagnetixRod;
            d.name = "MAG Rod " + col.name() + " H";
            d.defaultColor = col;
            d.isMagnetic = true;
            d.orient = PieceOrient.HORIZ;
            d.category = 2;
            d.model = makeRodModel(2.0f, texMetal, tint, true); // along Z; yaw via rotationY
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(0.1f, 0.1f, 1.0f);
            addSnapsEndsZ(d, 1.0f);
            defs.add(d);

            // Vertical
            d = new PieceDef();
            d.type = PieceType.MagnetixRodVert;
            d.name = "MAG Rod " + col.name() + " V";
            d.defaultColor = col;
            d.isMagnetic = true;
            d.orient = PieceOrient.VERT;
            d.category = 2;
            d.model = makeRodModel(2.0f, texMetal, tint);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(0.1f, 1.0f, 0.1f);
            addSnapsEndsY(d, 1.0f);
            defs.add(d);
        }

        // Polygons
        d = new PieceDef();
        d.type = PieceType.MagnetixTriangle; d.name = "MAG Triangle";
        d.isMagnetic = true; d.defaultColor = PieceColor.Green; d.category = 2;
        d.model = makePanelModel(texMetal, getColorFromEnum(PieceColor.Green));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Green), 128);
        addSnapsEndsX(d, 0.5f);
        d.snapPoints.add(new SnapPoint(0, 0, 0.3f, 0, 0, 1));
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.MagnetixSquare; d.name = "MAG Square";
        d.isMagnetic = true; d.defaultColor = PieceColor.Yellow; d.category = 2;
        d.model = makePanelModel(texMetal, getColorFromEnum(PieceColor.Yellow));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Yellow), 128);
        addSnapsEndsX(d, 0.5f);
        addSnapsEndsZ(d, 0.3f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.MagnetixHex; d.name = "MAG Hexagon";
        d.isMagnetic = true; d.defaultColor = PieceColor.Purple; d.category = 2;
        d.model = makeHexPanel(texMetal, getColorFromEnum(PieceColor.Purple));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Purple), 128);
        d.halfExtents = Helpers.newVector3(0.45f, 0.06f, 0.45f);
        for (int i = 0; i < 6; i++) {
            float ang = (float) (i * Math.PI / 3.0);
            float cx = (float) Math.cos(ang) * 0.4f;
            float cz = (float) Math.sin(ang) * 0.4f;
            d.snapPoints.add(new SnapPoint(cx, 0, cz, cx, 0, cz));
        }
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.MagnetixFlag; d.name = "MAG Flag";
        d.isMagnetic = true; d.defaultColor = PieceColor.Blue; d.category = 2;
        d.model = makePanelModel(texMetal, getColorFromEnum(PieceColor.Blue));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Blue), 128);
        d.snapPoints.add(new SnapPoint(0, 0, 0, 0, 1, 0));
        defs.add(d);

        // ========== TECH ==========
        d = new PieceDef();
        d.type = PieceType.TechLight; d.name = "TECH Light";
        d.hasLight = true; d.category = 3;
        d.model = makeBallModel(texMetal, Helpers.newColor(255, 60, 40, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(255, 60, 40, 255), 128);
        d.snapPoints.add(new SnapPoint(0, 0, 0, 0, 1, 0));
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.TechEngine; d.name = "TECH Engine";
        d.category = 3; d.defaultColor = PieceColor.Iron;
        d.model = makeCubeModel(0.8f, 0.6f, 0.8f, texMetal2 != null ? texMetal2 : texMetal,
            getColorFromEnum(PieceColor.Iron));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Iron), 128);
        d.halfExtents = Helpers.newVector3(0.4f, 0.3f, 0.4f);
        addSnapsEndsY(d, 0.3f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.TechPulley; d.name = "TECH Pulley";
        d.category = 3;
        d.model = makeBallModel(texMetal, Helpers.newColor(200, 180, 80, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(200, 180, 80, 255), 128);
        d.snapPoints.add(new SnapPoint(0, 0.22f, 0, 0, 1, 0));
        d.snapPoints.add(new SnapPoint(0, -0.22f, 0, 0, -1, 0));
        defs.add(d);

        // ========== LOLO ==========
        d = new PieceDef();
        d.type = PieceType.LoloBlock; d.name = "Lolo Block";
        d.category = 4;
        d.model = makeCubeModel(1f, 1f, 1f, texPlanks, BEIGE);
        d.icon = renderIcon(d.model, BEIGE, 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.LoloPlayer; d.name = "VRMan";
        d.category = 4;
        d.model = tryLoadModelAny("assets/models/VRMan.glb");
        if (d.model == null)
            d.model = makeCubeModel(0.8f, 1f, 0.8f, texPlanks, PINK);
        d.icon = renderIcon(d.model, PINK, 128);
        d.halfExtents = Helpers.newVector3(0.4f, 0.5f, 0.4f);
        defs.add(d);

        // ========== MISC playground ==========
        // Ramp (wedge via scaled cube + rotation hint — mesh is a long low block)
        d = new PieceDef();
        d.type = PieceType.Ramp; d.name = "Ramp";
        d.category = 5; d.defaultColor = PieceColor.Oak;
        d.model = tryLoadModelAny(
            "assets/models/oakrampmedium.glb", "assets/models/oakramplong.glb", "assets/models/oakrampsmall.glb");
        if (d.model == null)
            d.model = makeCubeModel(1.5f, 0.35f, 1.0f, texPlanks, getColorFromEnum(PieceColor.Oak));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
        d.halfExtents = Helpers.newVector3(0.75f, 0.175f, 0.5f);
        d.orient = PieceOrient.ANGLE;
        addSnapsEndsX(d, 0.75f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Ramp; d.name = "Ramp Stone";
        d.category = 5; d.defaultColor = PieceColor.Natural;
        d.model = tryLoadModelAny(
            "assets/models/stonerampmedium.glb", "assets/models/stoneramplong.glb", "assets/models/stonerampsmall.glb");
        if (d.model == null)
            d.model = makeCubeModel(1.5f, 0.35f, 1.0f, texStone, Helpers.newColor(140, 140, 145, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(140, 140, 145, 255), 128);
        d.halfExtents = Helpers.newVector3(0.75f, 0.175f, 0.5f);
        d.orient = PieceOrient.ANGLE;
        defs.add(d);

        // Teeter-totter beam
        d = new PieceDef();
        d.type = PieceType.TeeterTotter; d.name = "Teeter Totter";
        d.category = 5; d.isDynamic = true; d.defaultColor = PieceColor.Pine;
        d.model = makeCubeModel(2.4f, 0.12f, 0.4f, texPlanks, getColorFromEnum(PieceColor.Pine));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Pine), 128);
        d.halfExtents = Helpers.newVector3(1.2f, 0.06f, 0.2f);
        d.orient = PieceOrient.HORIZ;
        defs.add(d);

        // Boulders (dynamic spheres)
        String[] boulderNames = { "Boulder Small", "Boulder", "Boulder Large" };
        float[] boulderR = { 0.35f, 0.55f, 0.80f };
        for (int i = 0; i < boulderNames.length; i++) {
            d = new PieceDef();
            d.type = PieceType.Boulder;
            d.name = boulderNames[i];
            d.category = 5;
            d.isDynamic = true;
            d.defaultColor = PieceColor.Natural;
            float r = boulderR[i];
            String[] bPaths = {
                "assets/models/bouldersmall.glb",
                "assets/models/bouldermedium.glb",
                "assets/models/boulderlarge.glb"
            };
            d.model = tryLoadModelAny(bPaths[i]);
            if (d.model == null)
                d.model = makeBallModel(texRock != null ? texRock : (texStone != null ? texStone : texMetal),
                    Helpers.newColor(110, 105, 100, 255));
            // scale via halfExtents only — draw uses unit sphere; scale in draw later
            d.icon = renderIcon(d.model, Helpers.newColor(110, 105, 100, 255), 128);
            d.halfExtents = Helpers.newVector3(r, r, r);
            defs.add(d);
        }

        // Slide (long shallow board)
        d = new PieceDef();
        d.type = PieceType.Slide; d.name = "Slide";
        d.category = 5; d.defaultColor = PieceColor.Red;
        d.model = tryLoadModelAny("assets/models/slidered.glb");
        if (d.model == null)
            d.model = makeCubeModel(2.0f, 0.10f, 0.7f, texMetal, getColorFromEnum(PieceColor.Red));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Red), 128);
        d.halfExtents = Helpers.newVector3(1.0f, 0.05f, 0.35f);
        d.orient = PieceOrient.ANGLE;
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.Slide; d.name = "Slide Blue";
        d.category = 5; d.defaultColor = PieceColor.Blue;
        d.model = makeCubeModel(2.0f, 0.10f, 0.7f, texMetal, getColorFromEnum(PieceColor.Blue));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Blue), 128);
        d.halfExtents = Helpers.newVector3(1.0f, 0.05f, 0.35f);
        defs.add(d);

        // Regular material blocks (1x1x1 stud-ish)
        Object[][] blocks = {
            { PieceType.BlockWood,  "Block Wood",  PieceColor.Oak,  "texPlanks" },
            { PieceType.BlockStone, "Block Stone", PieceColor.Natural, "texStone" },
            { PieceType.BlockMetal, "Block Metal", PieceColor.Steel, "texMetal" },
            { PieceType.BlockSand,  "Block Sand",  PieceColor.Yellow, "texStone" },
            { PieceType.BlockGlass, "Block Glass", PieceColor.White, "texGlass" },
        };
        // manual blocks without reflection
        d = new PieceDef();
        d.type = PieceType.BlockWood; d.name = "Block Wood";
        d.category = 5; d.defaultColor = PieceColor.Oak;
        d.model = tryLoadModelAny("assets/models/oakblock.glb", "assets/models/pineblock.glb", "assets/models/oaklogblock.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 1f, 1f, texPlanks, getColorFromEnum(PieceColor.Oak));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        addSnapsEndsY(d, 0.5f); addSnapsEndsX(d, 0.5f); addSnapsEndsZ(d, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.BlockStone; d.name = "Block Stone";
        d.category = 5;
        d.model = tryLoadModelAny("assets/models/rockblock.glb", "assets/models/concreteblock.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 1f, 1f, texStone, Helpers.newColor(130, 130, 135, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(130, 130, 135, 255), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        addSnapsEndsY(d, 0.5f); addSnapsEndsX(d, 0.5f); addSnapsEndsZ(d, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.BlockMetal; d.name = "Block Metal";
        d.category = 5; d.defaultColor = PieceColor.Steel;
        d.model = tryLoadModelAny("assets/models/steelblock.glb", "assets/models/ironblock.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 1f, 1f, texMetal, getColorFromEnum(PieceColor.Steel));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Steel), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        addSnapsEndsY(d, 0.5f); addSnapsEndsX(d, 0.5f); addSnapsEndsZ(d, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.BlockSand; d.name = "Block Sand";
        d.category = 5; d.defaultColor = PieceColor.Yellow;
        d.model = tryLoadModelAny("assets/models/sandblock.glb", "assets/models/dirtblock.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 1f, 1f, texSand != null ? texSand : texStone, Helpers.newColor(210, 190, 140, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(210, 190, 140, 255), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        addSnapsEndsY(d, 0.5f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.BlockGlass; d.name = "Block Glass";
        d.category = 5; d.defaultColor = PieceColor.White;
        d.model = tryLoadModelAny("assets/models/glassblock.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 1f, 1f, texGlass != null ? texGlass : texMetal,
                Helpers.newColor(180, 220, 230, 180));
        d.icon = renderIcon(d.model, Helpers.newColor(180, 220, 230, 255), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
        addSnapsEndsY(d, 0.5f);
        defs.add(d);

        System.out.println("[Piece] Loaded " + defs.size() + " piece definitions");

        // ---- New material blocks (repo asset expansion) ----
        String[][] matBlocks = {
            {"BlockIce", "Block Ice", "assets/models/iceblock.glb", "ice"},
            {"BlockSnow", "Block Snow", "assets/models/snowblock.glb", "snow"},
            {"BlockCopper", "Block Copper", "assets/models/copperblock.glb", "copper"},
            {"BlockIron", "Block Iron", "assets/models/ironblock.glb", "iron"},
            {"BlockTitanium", "Block Titanium", "assets/models/titaniumblock.glb", "titanium"},
            {"BlockCrystal", "Block Crystal", "assets/models/crystalblock.glb", "crystal"},
            {"BlockDiamond", "Block Diamond", "assets/models/diamondblock.glb", "diamond"},
            {"BlockQuartz", "Block Quartz", "assets/models/quartzblock.glb", "quartz"},
            {"BlockConcrete", "Block Concrete", "assets/models/concreteblock.glb", "concrete"},
            {"BlockMagma", "Block Magma", "assets/models/magmablock.glb", "magma"},
            {"BlockDirt", "Block Dirt", "assets/models/dirtblock.glb", "dirt"},
            {"BlockGrass", "Block Grass", "assets/models/grassblock.glb", "grass"},
            {"BlockMagnecite", "Block Magnecite", "assets/models/magneciteblock.glb", "magnecite"},
        };
        for (String[] mb : matBlocks) {
            d = new PieceDef();
            try {
                d.type = PieceType.valueOf(mb[0]);
            } catch (Exception ex) { continue; }
            d.name = mb[1];
            d.category = 5;
            d.model = tryLoadModelAny(mb[2]);
            Texture fallbackTex = texStone;
            Color fallbackCol = Helpers.newColor(160, 160, 165, 255);
            if ("ice".equals(mb[3])) { fallbackTex = texIce != null ? texIce : texGlass; fallbackCol = Helpers.newColor(180, 220, 240, 220); }
            else if ("snow".equals(mb[3])) { fallbackTex = texSnow != null ? texSnow : texStone; fallbackCol = Helpers.newColor(240, 245, 250, 255); }
            else if ("copper".equals(mb[3])) { fallbackTex = texCopper != null ? texCopper : texMetal; fallbackCol = Helpers.newColor(180, 110, 70, 255); }
            else if ("iron".equals(mb[3])) { fallbackTex = texMetal2 != null ? texMetal2 : texMetal; fallbackCol = Helpers.newColor(140, 140, 145, 255); }
            else if ("titanium".equals(mb[3])) { fallbackTex = texMetal3 != null ? texMetal3 : texMetal; fallbackCol = Helpers.newColor(200, 205, 210, 255); }
            else if ("crystal".equals(mb[3]) || "diamond".equals(mb[3]) || "quartz".equals(mb[3])) {
                fallbackTex = texCrystal != null ? texCrystal : texGlass; fallbackCol = Helpers.newColor(160, 220, 255, 255);
            }
            else if ("magma".equals(mb[3])) { fallbackTex = texMagma != null ? texMagma : texStone; fallbackCol = Helpers.newColor(220, 80, 30, 255); }
            else if ("dirt".equals(mb[3])) { fallbackTex = texDirt != null ? texDirt : texStone; fallbackCol = Helpers.newColor(110, 80, 50, 255); }
            else if ("grass".equals(mb[3])) { fallbackTex = texGrass != null ? texGrass : texStone; fallbackCol = Helpers.newColor(70, 140, 55, 255); }
            else if ("magnecite".equals(mb[3])) { fallbackTex = texMetal; fallbackCol = Helpers.newColor(90, 100, 120, 255); }
            else if ("concrete".equals(mb[3])) { fallbackTex = texStone; fallbackCol = Helpers.newColor(150, 150, 148, 255); }
            if (d.model == null)
                d.model = makeCubeModel(1f, 1f, 1f, fallbackTex, fallbackCol);
            d.icon = renderIcon(d.model, fallbackCol, 128);
            d.halfExtents = Helpers.newVector3(0.5f, 0.5f, 0.5f);
            addSnapsEndsY(d, 0.5f); addSnapsEndsX(d, 0.5f); addSnapsEndsZ(d, 0.5f);
            defs.add(d);
        }

        // Metal cages + floors
        d = new PieceDef();
        d.type = PieceType.MetalCage; d.name = "Metal Cage";
        d.category = 5; d.defaultColor = PieceColor.Steel;
        d.model = tryLoadModelAny("assets/models/metalcage.glb", "assets/models/metalstonecage.glb");
        if (d.model == null)
            d.model = makeCubeModel(1.2f, 1.2f, 1.2f, texMetal, getColorFromEnum(PieceColor.Steel));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Steel), 128);
        d.halfExtents = Helpers.newVector3(0.6f, 0.6f, 0.6f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.FloorPlank; d.name = "Floor Oak";
        d.category = 1;
        d.model = tryLoadModelAny("assets/models/oakfloor.glb", "assets/models/pinefloor.glb");
        if (d.model == null)
            d.model = makeCubeModel(1f, 0.12f, 1f, texPlanks, getColorFromEnum(PieceColor.Oak));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
        d.halfExtents = Helpers.newVector3(0.5f, 0.06f, 0.5f);
        addSnapsEndsY(d, 0.06f);
        defs.add(d);

        // Extra wood logs: bamboo, dark oak, redwood
        String[][] extraLogs = {
            {"Bamboo H", "assets/models/bamboo_h.glb", "assets/models/bamboo_v.glb"},
            {"Dark Oak H", "assets/models/darkoaklog_h.glb", "assets/models/darkoaklog_v.glb"},
            {"Redwood H", "assets/models/redwoodlog_h.glb", "assets/models/redwoodlog_v.glb"},
        };
        for (String[] el : extraLogs) {
            d = new PieceDef();
            d.type = PieceType.StraightLog; d.name = el[0];
            d.category = 1;
            d.model = tryLoadModelAny(el[1]);
            if (d.model == null)
                d.model = makeLogModel(2.0f, texBark, getColorFromEnum(PieceColor.Oak), true);
            d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS, LOG_RADIUS, 1.0f);
            d.length = PieceLength.MED;
            defs.add(d);
            d = new PieceDef();
            d.type = PieceType.LogVert; d.name = el[0].replace(" H", " V");
            d.category = 1;
            d.model = tryLoadModelAny(el[2]);
            if (d.model == null)
                d.model = makeLogModel(2.0f, texBark, getColorFromEnum(PieceColor.Oak), false);
            d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Oak), 128);
            d.halfExtents = Helpers.newVector3(LOG_RADIUS, 1.0f, LOG_RADIUS);
            defs.add(d);
        }

        System.out.println("[Piece] defs=" + defs.size() + " (incl. material expansion)");

        // ---- Erector Set (metal bars, brackets, devices) ----
        // category 6; rainbow metal finishes
        PieceColor[] erectorMetals = {
            PieceColor.Steel, PieceColor.Iron, PieceColor.Titanium,
            PieceColor.Red, PieceColor.Blue, PieceColor.Yellow, PieceColor.Green
        };
        String[] metalNames = { "Steel", "Iron", "Titanium", "Red", "Blue", "Yellow", "Green" };
        for (int mi = 0; mi < erectorMetals.length; mi++) {
            PieceColor mc = erectorMetals[mi];
            String metal = metalNames[mi];
            Color tint = getColorFromEnum(mc);

            d = new PieceDef();
            d.type = PieceType.ErectorBarH; d.name = "Erector Bar H (" + metal + ")";
            d.category = 6; d.defaultColor = mc; d.orient = PieceOrient.HORIZ;
            d.model = makeCubeModel(1.6f, 0.08f, 0.12f, texMetal, tint);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(0.8f, 0.04f, 0.06f);
            addSnapsEndsX(d, 0.8f);
            defs.add(d);

            d = new PieceDef();
            d.type = PieceType.ErectorBarV; d.name = "Erector Bar V (" + metal + ")";
            d.category = 6; d.defaultColor = mc; d.orient = PieceOrient.VERT;
            d.model = makeCubeModel(0.12f, 1.6f, 0.08f, texMetal, tint);
            d.icon = renderIcon(d.model, tint, 128);
            d.halfExtents = Helpers.newVector3(0.06f, 0.8f, 0.04f);
            addSnapsEndsY(d, 0.8f);
            defs.add(d);
        }

        d = new PieceDef();
        d.type = PieceType.ErectorBracket; d.name = "Erector Bracket";
        d.category = 6; d.defaultColor = PieceColor.Steel;
        d.model = makeCubeModel(0.5f, 0.12f, 0.5f, texMetal, getColorFromEnum(PieceColor.Steel));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Steel), 128);
        d.halfExtents = Helpers.newVector3(0.25f, 0.06f, 0.25f);
        addSnapsEndsY(d, 0.06f); addSnapsEndsX(d, 0.25f); addSnapsEndsZ(d, 0.25f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.ErectorLightBulb; d.name = "Erector Light Bulb";
        d.category = 6; d.defaultColor = PieceColor.Yellow; d.hasLight = true;
        d.model = makeCubeModel(0.35f, 0.45f, 0.35f, texMetal, Helpers.newColor(255, 240, 160, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(255, 240, 160, 255), 128);
        d.halfExtents = Helpers.newVector3(0.18f, 0.22f, 0.18f);
        addSnapsEndsY(d, 0.22f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.ErectorPulley; d.name = "Erector Pulley";
        d.category = 6; d.defaultColor = PieceColor.Iron; d.isDynamic = true;
        d.model = makeCubeModel(0.5f, 0.2f, 0.5f, texMetal, getColorFromEnum(PieceColor.Iron));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Iron), 128);
        d.halfExtents = Helpers.newVector3(0.25f, 0.1f, 0.25f);
        addSnapsEndsY(d, 0.1f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.ErectorSlide; d.name = "Erector Slide";
        d.category = 6; d.defaultColor = PieceColor.Steel; d.orient = PieceOrient.ANGLE;
        d.model = makeCubeModel(2.0f, 0.1f, 0.6f, texMetal, getColorFromEnum(PieceColor.Steel));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Steel), 128);
        d.halfExtents = Helpers.newVector3(1.0f, 0.05f, 0.3f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.ErectorSwitch; d.name = "Erector Switch";
        d.category = 6; d.defaultColor = PieceColor.Red;
        d.model = makeCubeModel(0.3f, 0.25f, 0.2f, texMetal, getColorFromEnum(PieceColor.Red));
        d.icon = renderIcon(d.model, getColorFromEnum(PieceColor.Red), 128);
        d.halfExtents = Helpers.newVector3(0.15f, 0.12f, 0.1f);
        addSnapsEndsY(d, 0.12f);
        defs.add(d);

        d = new PieceDef();
        d.type = PieceType.ErectorCircuitBoard; d.name = "Erector Circuit Board";
        d.category = 6; d.defaultColor = PieceColor.Green;
        d.model = makeCubeModel(0.8f, 0.06f, 0.5f, texMetal, Helpers.newColor(40, 120, 55, 255));
        d.icon = renderIcon(d.model, Helpers.newColor(40, 120, 55, 255), 128);
        d.halfExtents = Helpers.newVector3(0.4f, 0.03f, 0.25f);
        addSnapsEndsY(d, 0.03f);
        defs.add(d);

        System.out.println("[Piece] Erector set added — total defs=" + defs.size());

        return defs;
    }

    public static void unloadPieceDefs(List<PieceDef> defs) {
        if (defs == null) return;
        for (PieceDef d : defs) {
            if (d == null) continue;
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
        unloadTex(texBark); texBark = null;
        unloadTex(texBark2); texBark2 = null;
        unloadTex(texMetal); texMetal = null;
        unloadTex(texMetal2); texMetal2 = null;
        unloadTex(texMetal3); texMetal3 = null;
        unloadTex(texPlanks); texPlanks = null;
        unloadTex(texStone); texStone = null;
        unloadTex(texGlass); texGlass = null;
        unloadTex(texRoof); texRoof = null;
        unloadTex(texRoof2); texRoof2 = null;
    }

    private static void unloadTex(Texture t) {
        if (t == null || t.isNull()) return;
        try { UnloadTexture(t); } catch (Throwable ignored) {}
    }

    public static void drawPlacedPiece(PlacedPiece piece, List<PieceDef> defs) {
        PieceDef def = findDef(defs, piece.type, piece.color);
        if (def == null) {
            // fallback: first matching type
            for (PieceDef x : defs) {
                if (x.type == piece.type) { def = x; break; }
            }
            if (def == null) return;
        }

        Color tint = getColorFromEnum(piece.color);
        if (piece.color == PieceColor.Natural) tint = WHITE;

        // Wood pieces use baked wood tint on model — keep WHITE unless selected
        boolean wood = def.category == 1 && piece.type != PieceType.FabricFlag
                && piece.type != PieceType.Roof && piece.type != PieceType.RoofPeak
                && piece.type != PieceType.Window
                && piece.type != PieceType.Plank && piece.type != PieceType.PlankWide;
        if (wood) tint = WHITE;

        if (piece.selected) tint = YELLOW;
        else if (piece.snapHighlight) tint = RED;

        float heightFactor = clamp(piece.position.y() / 12f, 0f, 1f);
        float m = 0.85f + heightFactor * 0.15f;
        int tr = tint.r() & 0xFF, tg = tint.g() & 0xFF, tb = tint.b() & 0xFF, ta = tint.a() & 0xFF;
        Color shaded = Helpers.newColor((int) (tr * m), (int) (tg * m), (int) (tb * m), ta);

        // Models are pre-baked: VERT = Y-up, HORIZ = length along Z. Only yaw in world.
        DrawModelEx(def.model, piece.position,
                   Helpers.newVector3(0, 1, 0), piece.rotationY,
                   Helpers.newVector3(1, 1, 1), shaded);

        // Magnetic rod end caps
        if (def.isMagnetic && (def.type == PieceType.MagnetixRod || def.type == PieceType.MagnetixRodVert)) {
            float ry = (float) Math.toRadians(piece.rotationY);
            Vector3 a, b;
            if (def.orient == PieceOrient.VERT) {
                a = Vector3Add(piece.position, Helpers.newVector3(0, 1f, 0));
                b = Vector3Add(piece.position, Helpers.newVector3(0, -1f, 0));
            } else {
                // HORIZ rod length along local Z after bake
                a = Vector3Add(piece.position,
                    Vector3RotateByAxisAngle(Helpers.newVector3(0, 0, -1f), Helpers.newVector3(0, 1, 0), ry));
                b = Vector3Add(piece.position,
                    Vector3RotateByAxisAngle(Helpers.newVector3(0, 0, 1f), Helpers.newVector3(0, 1, 0), ry));
            }
            DrawSphere(a, 0.11f, WHITE);
            DrawSphere(b, 0.11f, WHITE);
            if (piece.snapHighlight) {
                DrawSphereWires(a, 0.18f, 8, 8, YELLOW);
                DrawSphereWires(b, 0.18f, 8, 8, YELLOW);
            }
        }

        // Soft light pulse for light pieces
        if (def.hasLight) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(GetTime() * 4.0);
            DrawSphere(piece.position, 0.35f, Fade(Helpers.newColor(255, 220, 120, 255), 0.25f * pulse));
        }
    }

    public static BoundingBox getPieceWorldBounds(PlacedPiece piece, PieceDef def) {
        float r = Math.max(def.halfExtents.x(),
                  Math.max(def.halfExtents.y(), def.halfExtents.z()));
        return Helpers.newBoundingBox(
            Helpers.newVector3(piece.position.x() - r, piece.position.y() - r, piece.position.z() - r),
            Helpers.newVector3(piece.position.x() + r, piece.position.y() + r, piece.position.z() + r));
    }

    /** Match type; for colored variants also match defaultColor when multiple defs share a type. */
    public static PieceDef findDef(List<PieceDef> defs, PieceType type, PieceColor color) {
        PieceDef typeOnly = null;
        for (PieceDef d : defs) {
            if (d.type != type) continue;
            if (d.defaultColor == color) return d;
            if (typeOnly == null) typeOnly = d;
        }
        return typeOnly;
    }

    static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }
}
