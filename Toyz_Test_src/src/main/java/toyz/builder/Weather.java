package toyz.builder;

import com.raylib.Helpers;

import static com.raylib.Raylib.*;
import static com.raylib.Colors.*;

/**
 * Weather system — clear / rain / snow with 3D particles that follow the
 * camera. Rain renders as fast vertical streaks, snow as slow swaying flakes.
 * Weather auto-cycles every 30–90 s, or press M in-game to cycle manually.
 */
public final class Weather {

    public enum Type { CLEAR, RAIN, SNOW }

    private static final int MAX_PARTICLES = 700;
    private static final float AREA = 30f;     // half-extent of the particle box around the camera
    private static final float TOP = 24f;       // spawn height above the camera
    private static final float BOTTOM = -6f;    // recycle height below the camera

    private static final float[] px = new float[MAX_PARTICLES];
    private static final float[] py = new float[MAX_PARTICLES];
    private static final float[] pz = new float[MAX_PARTICLES];
    private static final float[] swayPhase = new float[MAX_PARTICLES];
    private static final float[] speedMul = new float[MAX_PARTICLES];

    private static Type current = Type.CLEAR;
    private static float intensity;            // 0..1, fades weather in and out
    private static float autoTimer = 20f;
    private static boolean auto = true;
    private static boolean initialized;

    private Weather() {}

    public static Type type() { return current; }

    public static String name() {
        switch (current) {
            case RAIN: return "RAIN";
            case SNOW: return "SNOW";
            default:   return "CLEAR";
        }
    }

    public static boolean isAuto() { return auto; }
    public static void setAuto(boolean a) { auto = a; }

    public static void set(Type t) {
        current = t;
        autoTimer = 30f + (float) (Math.random() * 60f);
    }

    /** Manual cycle: CLEAR -> RAIN -> SNOW -> CLEAR. Disables auto-cycling. */
    public static void cycle() {
        auto = false;
        switch (current) {
            case CLEAR: set(Type.RAIN); break;
            case RAIN:  set(Type.SNOW); break;
            default:    set(Type.CLEAR); break;
        }
    }

    private static void spawn(int i, Vector3 cam) {
        px[i] = cam.x() + (float) (Math.random() * 2 - 1) * AREA;
        pz[i] = cam.z() + (float) (Math.random() * 2 - 1) * AREA;
        py[i] = cam.y() + (float) Math.random() * TOP;
        swayPhase[i] = (float) (Math.random() * Math.PI * 2);
        speedMul[i] = 0.7f + (float) Math.random() * 0.6f;
    }

    public static void update(float dt, Vector3 cam) {
        // Auto-cycle: clear 40-90 s, precipitation 25-60 s
        if (auto) {
            autoTimer -= dt;
            if (autoTimer <= 0f) {
                if (current == Type.CLEAR) {
                    // Snow in cold biomes, rain in warm ones — pick by chance
                    set(Math.random() < 0.45f ? Type.SNOW : Type.RAIN);
                    autoTimer = 25f + (float) (Math.random() * 35f);
                } else {
                    set(Type.CLEAR);
                    autoTimer = 40f + (float) (Math.random() * 50f);
                }
            }
        }

        float target = (current == Type.CLEAR) ? 0f : 1f;
        if (intensity < target) intensity = Math.min(target, intensity + dt * 0.5f);
        else if (intensity > target) intensity = Math.max(target, intensity - dt * 0.4f);

        if (intensity <= 0.001f) { initialized = false; return; }
        if (!initialized) {
            for (int i = 0; i < MAX_PARTICLES; i++) spawn(i, cam);
            initialized = true;
        }

        float fall = (current == Type.RAIN) ? 32f : 4.5f;
        float areaSq = (AREA + 4f) * (AREA + 4f);
        boolean snow = (current == Type.SNOW);

        for (int i = 0; i < MAX_PARTICLES; i++) {
            py[i] -= fall * speedMul[i] * dt;
            if (snow) {
                swayPhase[i] += dt * 1.5f;
                px[i] += Math.sin(swayPhase[i]) * dt * 1.2f;
                pz[i] += Math.cos(swayPhase[i] * 0.7f) * dt * 1.2f;
            } else {
                px[i] -= dt * 2.5f; // slight wind drift for rain
            }
            // Recycle: below the camera, or drifted too far horizontally
            float dx = px[i] - cam.x(), dz = pz[i] - cam.z();
            if (py[i] < cam.y() + BOTTOM || dx * dx + dz * dz > areaSq) {
                px[i] = cam.x() + (float) (Math.random() * 2 - 1) * AREA;
                pz[i] = cam.z() + (float) (Math.random() * 2 - 1) * AREA;
                py[i] = cam.y() + TOP * (0.6f + (float) Math.random() * 0.4f);
            }
        }
    }

    private static final Vector3 TMP = Helpers.newVector3(0, 0, 0);

    /** Draw inside BeginMode3D, after the terrain. */
    public static void draw(Vector3 cam) {
        if (intensity <= 0.01f) return;

        if (current == Type.RAIN) {
            Color c = Fade(Helpers.newColor(120, 160, 210, 255), 0.45f * intensity);
            for (int i = 0; i < MAX_PARTICLES; i++) {
                TMP.x(px[i]).y(py[i]).z(pz[i]);
                DrawLine3D(TMP, Helpers.newVector3(px[i] + 0.08f, py[i] - 0.65f, pz[i]), c);
            }
        } else { // SNOW
            Color c = Fade(WHITE, 0.85f * intensity);
            for (int i = 0; i < MAX_PARTICLES; i++) {
                TMP.x(px[i]).y(py[i]).z(pz[i]);
                DrawCube(TMP, 0.07f, 0.07f, 0.07f, c);
            }
        }
    }

    /** Sky darkening factor 0..1 — multiply toward storm gray when raining. */
    public static float storminess() {
        return (current == Type.CLEAR) ? 0f : intensity;
    }
}