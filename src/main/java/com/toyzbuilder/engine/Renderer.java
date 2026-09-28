package com.toyzbuilder.engine;

import org.joml.FrustumIntersection;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * OpenGL 3.3 core renderer, written for a Radeon HD 6450 class GPU (fill-rate / bandwidth bound).
 *
 * Per-frame contract: call {@link #beginFrame} once, then the render* methods, then Hud.
 * Everything here reads {@link GameSettings}:
 *  - transparency OFF  -> water is opaque, no alpha blending anywhere (default)
 *  - fog               -> linear fog, doubles as pop-in cover for the render distance
 *  - render distance   -> far plane + distance culling of trees / boulders / pieces
 *  - terrainBlend FAST -> terrain shader skips texture layers whose weight is ~0
 *  - modelCull         -> back-face culling on tree + GLB + piece meshes
 * Shadows / reflections: not implemented. If you add them, gate on settings.shadows / .reflections.
 *
 * CPU-side savings vs the first LWJGL port: uniform locations cached, camera/fog uniforms uploaded
 * once per program per frame, program + texture + cull-state redundancy filtered, normal matrix
 * computed on the CPU (was inverse() per vertex), frustum culling.
 */
public class Renderer {

    private static final float NEAR = 0.15f;
    private static final float LIGHT_X = 0.4f, LIGHT_Y = -1f, LIGHT_Z = 0.3f;

    private ShaderProgram terrainFast, terrainFancy, waterShader, flatShader, modelShader, texMeshShader;

    // ---- per-frame state ----
    private GameSettings gs = GameSettings.get();
    private int frameId = 0;
    private ShaderProgram current;
    private Texture lastTex;
    private boolean cullOn = true;
    private boolean underwater;

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f projView = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final float[] projArr = new float[16];
    private final float[] viewArr = new float[16];
    private float camX, camY, camZ;
    private float fogR, fogG, fogB, fogStart = 1e8f, fogEnd = 1.1e8f;
    private float farPlane = 900f;

    // ---- scratch ----
    private final Matrix4f tmpModel = new Matrix4f();
    private final Matrix3f tmpNormal = new Matrix3f();
    private final float[] modelArr = new float[16];
    private final float[] normArr = new float[9];

    private int debugVao, debugVbo;

    // ------------------------------------------------------------------ shader sources

    private static final String FOG_FS_DECL = """
            uniform vec3 fogColor;
            uniform float fogStart;
            uniform float fogEnd;
            vec3 applyFog(vec3 c, float d) {
                float f = clamp((fogEnd - d) / (fogEnd - fogStart), 0.0, 1.0);
                return mix(fogColor, c, f);
            }
            """;

    private static String terrainFS(boolean fast) {
        String albedo = fast
                ? """
                  // FAST: only fetch layers that contribute. textureGrad keeps mip selection correct
                  // even though the fetches sit inside branches.
                  vec2 dx = dFdx(vUV);
                  vec2 dy = dFdy(vUV);
                  vec3 albedo = vec3(0.0);
                  if (vBlend.x > 0.01) albedo += textureGrad(texGrass, vUV, dx, dy).rgb * vBlend.x;
                  if (vBlend.y > 0.01) albedo += textureGrad(texSand,  vUV, dx, dy).rgb * vBlend.y;
                  if (vBlend.z > 0.01) albedo += textureGrad(texSnow,  vUV, dx, dy).rgb * vBlend.z;
                  if (vBlend.w > 0.01) albedo += textureGrad(texRock,  vUV, dx, dy).rgb * vBlend.w;
                  """
                : """
                  vec3 g = texture(texGrass, vUV).rgb;
                  vec3 s = texture(texSand, vUV).rgb;
                  vec3 sn = texture(texSnow, vUV).rgb;
                  vec3 r = texture(texRock, vUV).rgb;
                  vec3 albedo = g * vBlend.x + s * vBlend.y + sn * vBlend.z + r * vBlend.w;
                  """;
        return """
            #version 330 core
            in vec3 vNormal;
            in vec2 vUV;
            in vec4 vBlend;
            in float vHeight;
            in float vFog;
            out vec4 FragColor;
            uniform sampler2D texGrass;
            uniform sampler2D texSand;
            uniform sampler2D texSnow;
            uniform sampler2D texRock;
            uniform vec3 lightDir;
            """ + FOG_FS_DECL + """
            void main() {
                vec3 n = normalize(vNormal);
                float ndl = max(dot(n, normalize(-lightDir)), 0.0);
                float light = 0.32 + 0.68 * ndl;
            """ + albedo + """
                albedo *= 0.90 + 0.10 * clamp(vHeight / 30.0, 0.0, 1.0);
                FragColor = vec4(applyFog(albedo * light, vFog), 1.0);
            }
            """;
    }

    public void init() {
        String terrainVS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUV;
            layout(location = 3) in vec4 aBlend;
            uniform mat4 projection;
            uniform mat4 view;
            out vec3 vNormal;
            out vec2 vUV;
            out vec4 vBlend;
            out float vHeight;
            out float vFog;
            void main() {
                vNormal = aNormal;
                vUV = aUV;
                vBlend = aBlend;
                vHeight = aPos.y;
                gl_Position = projection * view * vec4(aPos, 1.0);
                vFog = gl_Position.w;
            }
            """;

        String waterVS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 projection;
            uniform mat4 view;
            out vec2 vUV;
            out float vFog;
            void main() {
                vUV = aPos.xz * 0.05;
                gl_Position = projection * view * vec4(aPos, 1.0);
                vFog = gl_Position.w;
            }
            """;
        String waterFS = """
            #version 330 core
            in vec2 vUV;
            in float vFog;
            out vec4 FragColor;
            uniform sampler2D texWater;
            uniform float time;
            uniform float uAlpha;
            """ + FOG_FS_DECL + """
            void main() {
                vec2 uv = vUV + vec2(time * 0.02, time * 0.015);
                vec3 c = texture(texWater, uv).rgb;
                c = c * 0.75 + vec3(0.05, 0.15, 0.25);
                FragColor = vec4(applyFog(c, vFog), uAlpha);
            }
            """;

        String flatVS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            void main() {
                gl_Position = projection * view * model * vec4(aPos, 1.0);
            }
            """;
        String flatFS = """
            #version 330 core
            uniform vec4 uColor;
            out vec4 FragColor;
            void main() {
                FragColor = uColor;
            }
            """;

        String modelVS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            uniform mat3 normalMat;
            out vec3 vNormal;
            out float vFog;
            void main() {
                gl_Position = projection * view * model * vec4(aPos, 1.0);
                vNormal = normalMat * aNormal;
                vFog = gl_Position.w;
            }
            """;
        String modelFS = """
            #version 330 core
            in vec3 vNormal;
            in float vFog;
            out vec4 FragColor;
            uniform vec4 uColor;
            uniform vec3 lightDir;
            """ + FOG_FS_DECL + """
            void main() {
                vec3 n = normalize(vNormal);
                float ndl = max(dot(n, normalize(-lightDir)), 0.0);
                float light = 0.35 + 0.65 * ndl;
                FragColor = vec4(applyFog(uColor.rgb * light, vFog), uColor.a);
            }
            """;

        String texVS = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUV;
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            uniform mat3 normalMat;
            out vec3 vNormal;
            out vec2 vUV;
            out float vFog;
            void main() {
                vUV = aUV;
                vNormal = normalMat * aNormal;
                gl_Position = projection * view * model * vec4(aPos, 1.0);
                vFog = gl_Position.w;
            }
            """;
        String texFS = """
            #version 330 core
            in vec3 vNormal;
            in vec2 vUV;
            in float vFog;
            out vec4 FragColor;
            uniform sampler2D uTex;
            uniform vec4 uColor;
            uniform vec3 lightDir;
            uniform float uUseTex;
            """ + FOG_FS_DECL + """
            void main() {
                vec3 n = normalize(vNormal);
                float ndl = max(dot(n, normalize(-lightDir)), 0.0);
                float light = 0.35 + 0.65 * ndl;
                vec3 base = uColor.rgb;
                if (uUseTex > 0.5) {
                    base *= texture(uTex, vUV).rgb;
                }
                FragColor = vec4(applyFog(base * light, vFog), uColor.a);
            }
            """;

        terrainFast = new ShaderProgram(terrainVS, terrainFS(true));
        terrainFancy = new ShaderProgram(terrainVS, terrainFS(false));
        waterShader = new ShaderProgram(waterVS, waterFS);
        flatShader = new ShaderProgram(flatVS, flatFS);
        modelShader = new ShaderProgram(modelVS, modelFS);
        texMeshShader = new ShaderProgram(texVS, texFS);

        // sampler bindings never change: set once
        for (ShaderProgram t : new ShaderProgram[] { terrainFast, terrainFancy }) {
            t.bind();
            t.set1i("texGrass", 0);
            t.set1i("texSand", 1);
            t.set1i("texSnow", 2);
            t.set1i("texRock", 3);
        }
        waterShader.bind();
        waterShader.set1i("texWater", 0);
        texMeshShader.bind();
        texMeshShader.set1i("uTex", 0);
        texMeshShader.unbind();

        debugVao = GL30.glGenVertexArrays();
        debugVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(debugVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, debugVbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    // ------------------------------------------------------------------ frame setup

    /**
     * @param fogR/G/B  fog colour; pass the sky clear colour so terrain fades into it
     */
    public void beginFrame(FirstPersonCamera camera, float aspect, GameSettings settings,
                           float fogR, float fogG, float fogB, boolean underwater) {
        this.gs = settings;
        this.underwater = underwater;
        frameId++;
        current = null;
        lastTex = null;
        GL11.glEnable(GL11.GL_CULL_FACE);   // Hud.end() leaves it on; keep our cache honest
        cullOn = true;

        float dist = gs.renderDistance();
        farPlane = gs.renderDistanceIsMax() ? 1000f : dist * 1.15f + 8f;

        proj.identity().perspective((float) Math.toRadians(gs.fov), aspect, NEAR, farPlane);
        view.set(camera.getViewMatrix());
        projView.set(proj).mul(view);
        frustum.set(projView);
        proj.get(projArr);
        view.get(viewArr);
        camX = camera.getX();
        camY = camera.getY();
        camZ = camera.getZ();

        this.fogR = fogR; this.fogG = fogG; this.fogB = fogB;
        if (gs.fog) {
            fogEnd = gs.renderDistanceIsMax() ? 900f : dist * 1.05f;
            fogStart = fogEnd * 0.55f;
            if (underwater) { fogStart = 2f; fogEnd = Math.min(fogEnd, 48f); }
        } else {
            fogStart = 1e8f;       // effectively no fog, no branch needed in the shader
            fogEnd = 1.1e8f;
        }
    }

    public float getFarPlane() { return farPlane; }

    /** Distance + frustum test. maxDist is measured from the camera to the sphere centre. */
    public boolean visible(float x, float y, float z, float radius, float maxDist) {
        float dx = x - camX, dy = y - camY, dz = z - camZ;
        float lim = maxDist + radius;
        if (dx * dx + dy * dy + dz * dz > lim * lim) return false;
        return frustum.testSphere(x, y, z, radius);
    }

    // ------------------------------------------------------------------ state helpers

    private void use(ShaderProgram sp) {
        if (current != sp) {
            sp.bind();
            current = sp;
        }
        if (sp.frameStamp != frameId) {
            sp.frameStamp = frameId;
            sp.setMat4("projection", projArr);
            sp.setMat4("view", viewArr);
            sp.set3f("lightDir", LIGHT_X, LIGHT_Y, LIGHT_Z);
            sp.set3f("fogColor", fogR, fogG, fogB);
            sp.set1f("fogStart", fogStart);
            sp.set1f("fogEnd", fogEnd);
        }
    }

    private void cull(boolean on) {
        if (on == cullOn) return;
        if (on) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
        cullOn = on;
    }

    private void bindTex0(Texture t) {
        if (t == lastTex) return;
        t.bind(0);
        lastTex = t;
    }

    private void setModelMatrix(ShaderProgram sp, float x, float y, float z,
                                float sx, float sy, float sz, float yawDeg) {
        tmpModel.translation(x, y, z);
        if (yawDeg != 0f) tmpModel.rotateY((float) Math.toRadians(yawDeg));
        tmpModel.scale(sx, sy, sz);
        tmpModel.get(modelArr);
        sp.setMat4("model", modelArr);
        tmpModel.normal(tmpNormal);
        tmpNormal.get(normArr);
        sp.setMat3("normalMat", normArr);
    }

    // ------------------------------------------------------------------ world

    public void renderTerrain(Mesh mesh, AssetBank assets) {
        if (mesh == null) return;
        ShaderProgram sp = gs.terrainBlend == 0 ? terrainFast : terrainFancy;
        use(sp);
        cull(true);
        assets.get(AssetBank.Slot.GRASS).bind(0);
        assets.get(AssetBank.Slot.SAND).bind(1);
        assets.get(AssetBank.Slot.SNOW).bind(2);
        assets.get(AssetBank.Slot.ROCK).bind(3);
        lastTex = null; // units 0-3 were touched directly
        mesh.render();
    }

    public void renderWater(Mesh mesh, AssetBank assets, float time) {
        if (mesh == null) return;
        boolean blend = gs.transparency;
        // Opaque water seen from below would be a solid lid; the sky-coloured clear reads better.
        if (!blend && underwater) return;

        use(waterShader);
        cull(false);
        assets.get(AssetBank.Slot.WATER).bind(0);
        lastTex = null;
        waterShader.set1f("time", time);
        waterShader.set1f("uAlpha", blend ? 0.55f : 1f);

        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
        }
        mesh.render();
        if (blend) {
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    public void renderTrees(com.toyzbuilder.world.TreeField field, TreeMeshes meshes, AssetBank assets) {
        if (field == null || meshes == null || gs.trees == 0) return;

        final boolean fancy = gs.trees == 2;
        final float dist = gs.renderDistance();
        final float maxD = fancy ? dist : dist * 0.8f;
        // FAST: beyond this range only the leafy canopy is drawn (halves draw calls for far trees)
        final float trunkD = fancy ? Float.MAX_VALUE : maxD * 0.4f;
        final float trunkD2 = trunkD * trunkD;
        final int stride = gs.treeStride();
        final var trees = field.getTrees();
        final int n = trees.size();

        Texture barkOak = assets.get(AssetBank.Slot.BARK);
        Texture barkPine = assets.get(AssetBank.Slot.BARK_PINE);
        Texture barkBirch = assets.get(AssetBank.Slot.BARK_BIRCH);
        Texture leaves = assets.get(AssetBank.Slot.LEAVES);

        // ---- pass A: trunks ----
        use(texMeshShader);
        cull(gs.modelCull);
        texMeshShader.set1f("uUseTex", 1f);
        for (int i = 0; i < n; i += stride) {
            var t = trees.get(i);
            float scale = t.scale;
            float trunkH = trunkHeight(t.kind, scale);
            float r = 2.5f * scale + trunkH * 0.5f;
            if (!visible(t.x, t.y + trunkH * 0.5f, t.z, r, maxD)) continue;
            float dx = t.x - camX, dz = t.z - camZ;
            if (dx * dx + dz * dz > trunkD2) continue;
            Texture bark = switch (t.kind) {
                case PINE, SNOW -> barkPine;
                case BIRCH -> barkBirch;
                default -> barkOak;
            };
            float trunkR = 1.0f * scale;
            drawTex(meshes.trunk, t.x, t.y, t.z, trunkR, trunkH, trunkR, t.yaw,
                    bark, 0.85f, 0.75f, 0.60f, 1f);
        }

        // ---- pass B: canopies (single texture -> no rebinds) ----
        for (int i = 0; i < n; i += stride) {
            var t = trees.get(i);
            float scale = t.scale;
            float trunkH = trunkHeight(t.kind, scale);
            float canopyS = canopyScale(t.kind, scale);
            float cy = t.y + trunkH * 0.55f;
            float r = 1.8f * canopyS;
            if (!visible(t.x, cy + canopyS * 0.6f, t.z, r, maxD)) continue;
            float lr = 0.22f, lg = 0.48f, lb = 0.18f;
            switch (t.kind) {
                case PINE -> { lr = 0.12f; lg = 0.38f; lb = 0.18f; }
                case BIRCH -> { lr = 0.35f; lg = 0.55f; lb = 0.25f; }
                case SNOW -> { lr = 0.85f; lg = 0.90f; lb = 0.92f; }
                default -> { }
            }
            drawTex(meshes.canopy, t.x, cy, t.z, canopyS, canopyS, canopyS, t.yaw,
                    leaves, lr, lg, lb, 1f);
        }
    }

    private static float trunkHeight(com.toyzbuilder.world.TreeField.Kind k, float scale) {
        return switch (k) {
            case PINE -> 2.2f * scale;
            case SNOW -> 1.9f * scale;
            default -> 1.6f * scale;
        };
    }

    private static float canopyScale(com.toyzbuilder.world.TreeField.Kind k, float scale) {
        return k == com.toyzbuilder.world.TreeField.Kind.PINE ? 0.85f * scale : scale;
    }

    public void renderLandscapeFeatures(com.toyzbuilder.world.LandscapeFeatures field, AssetBank assets) {
        if (field == null || !gs.landscape) return;
        final float maxD = gs.renderDistance() * 0.6f;
        for (com.toyzbuilder.world.LandscapeFeatures.Instance f : field.getFeatures()) {
            boolean large = f.kind == com.toyzbuilder.world.LandscapeFeatures.Kind.BOULDER_L;
            float rad = f.scale * (large ? 2.4f : 1.4f) + 0.5f;
            if (!visible(f.x, f.y + f.scale * 0.5f, f.z, rad, maxD)) continue;

            String path;
            Texture tex;
            switch (f.kind) {
                case BOULDER_L -> { path = "boulderlarge.glb"; tex = assets.get(AssetBank.Slot.ROCK); }
                case BOULDER_M -> { path = "bouldermedium.glb"; tex = assets.get(AssetBank.Slot.ROCK); }
                case BOULDER_S -> { path = "bouldersmall.glb"; tex = assets.get(AssetBank.Slot.ROCK); }
                case CRYSTAL -> { path = "crystalblock.glb"; tex = assets.get(AssetBank.Slot.CRYSTAL); }
                case QUARTZ -> { path = "quartzblock.glb"; tex = assets.get(AssetBank.Slot.QUARTZ); }
                case MAGMA -> { path = "magmablock.glb"; tex = assets.get(AssetBank.Slot.MAGMA); }
                default -> { path = "bouldersmall.glb"; tex = assets.get(AssetBank.Slot.ROCK); }
            }
            Model model = Model.load(path);
            if (model == null) continue;
            float k = large ? 2.0f : 1.0f;
            float sx = k * f.scale / Math.max(.01f, model.sizeX);
            float sz = k * f.scale / Math.max(.01f, model.sizeZ);
            float sy = f.scale / Math.max(.01f, model.sizeY);
            if (f.kind == com.toyzbuilder.world.LandscapeFeatures.Kind.CRYSTAL
                    || f.kind == com.toyzbuilder.world.LandscapeFeatures.Kind.QUARTZ
                    || f.kind == com.toyzbuilder.world.LandscapeFeatures.Kind.MAGMA) {
                sy *= 0.8f;
            }
            renderTexturedModel(model, f.x, f.y + f.scale * .15f, f.z, sx, sy, sz, f.yaw,
                    tex, 1f, 1f, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ generic draws

    /** Flat-colour mesh (mannequin). Double-sided like the original. */
    public void renderMesh(Mesh mesh, float x, float y, float z,
                           float sx, float sy, float sz,
                           float r, float g, float b, float a) {
        renderMesh(mesh, x, y, z, sx, sy, sz, 0f, r, g, b, a);
    }

    public void renderMesh(Mesh mesh, float x, float y, float z,
                           float sx, float sy, float sz, float yawDeg,
                           float r, float g, float b, float a) {
        if (mesh == null) return;
        use(flatShader);
        cull(false);
        tmpModel.translation(x, y, z)
                .rotateY((float) Math.toRadians(yawDeg))
                .scale(sx, sy, sz)
                .get(modelArr);
        flatShader.setMat4("model", modelArr);
        flatShader.set4f("uColor", r, g, b, a);
        mesh.render();
    }

    /** Debug AABB edges: pos-only float array (pairs of points), drawn as GL_LINES. */
    public void renderDebugLines(float[] lines, float r, float g, float b, float a) {
        if (lines == null || lines.length < 6) return;
        use(flatShader);
        tmpModel.identity().get(modelArr);
        flatShader.setMat4("model", modelArr);
        flatShader.set4f("uColor", r, g, b, a);
        GL30.glBindVertexArray(debugVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, debugVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, lines, GL15.GL_STREAM_DRAW);
        GL11.glDrawArrays(GL11.GL_LINES, 0, lines.length / 3);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void renderModel(Model model, float x, float y, float z,
                            float sx, float sy, float sz, float yawDeg,
                            float r, float g, float b, float a) {
        if (model == null) return;
        use(modelShader);
        cull(gs.modelCull);
        setModelMatrix(modelShader, x, y, z, sx, sy, sz, yawDeg);
        modelShader.set4f("uColor", r, g, b, a);
        for (Mesh mesh : model.meshes) mesh.render();
    }

    public void renderTexturedModel(Model model, float x, float y, float z,
                                    float sx, float sy, float sz, float yawDeg,
                                    Texture tex, float r, float g, float b, float a) {
        if (model == null || tex == null) return;
        use(texMeshShader);
        cull(gs.modelCull);
        texMeshShader.set1f("uUseTex", 1f);
        setModelMatrix(texMeshShader, x, y, z, sx, sy, sz, yawDeg);
        texMeshShader.set4f("uColor", r, g, b, a);
        bindTex0(tex);
        boolean blend = a < 0.999f && gs.transparency;
        if (blend) beginBlend();
        for (Mesh mesh : model.meshes) mesh.render();
        if (blend) endBlend();
    }

    public void renderTextured(Mesh mesh, float x, float y, float z,
                               float sx, float sy, float sz, float yawDeg,
                               Texture tex, float r, float g, float b, float a) {
        renderTextured(mesh, x, y, z, sx, sy, sz, yawDeg, tex, r, g, b, a, false);
    }

    public void renderTextured(Mesh mesh, float x, float y, float z,
                               float sx, float sy, float sz, float yawDeg,
                               Texture tex, float r, float g, float b, float a,
                               boolean twoSided) {
        if (mesh == null) return;
        use(texMeshShader);
        // two-sided walls: show texture from both faces (thin house walls)
        cull(twoSided ? false : gs.modelCull);
        texMeshShader.set1f("uUseTex", tex != null ? 1f : 0f);
        drawTex(mesh, x, y, z, sx, sy, sz, yawDeg, tex, r, g, b, a);
    }

    /** Shared inner draw: texMeshShader already in use, uUseTex already set. */
    private void drawTex(Mesh mesh, float x, float y, float z,
                         float sx, float sy, float sz, float yawDeg,
                         Texture tex, float r, float g, float b, float a) {
        setModelMatrix(texMeshShader, x, y, z, sx, sy, sz, yawDeg);
        texMeshShader.set4f("uColor", r, g, b, a);
        if (tex != null) bindTex0(tex);
        boolean blend = a < 0.999f && gs.transparency;
        if (blend) beginBlend();
        mesh.render();
        if (blend) endBlend();
    }

    private static void beginBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public void cleanup() {
        if (debugVbo != 0) GL15.glDeleteBuffers(debugVbo);
        if (debugVao != 0) GL30.glDeleteVertexArrays(debugVao);
    }
}
