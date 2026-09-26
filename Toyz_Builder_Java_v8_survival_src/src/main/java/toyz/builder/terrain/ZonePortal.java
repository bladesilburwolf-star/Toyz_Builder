package toyz.builder.terrain;

/**
 * Loading-zone trigger. Warp pipes + doors + obelisks + cave mouths.
 */
public final class ZonePortal {
    public enum Target {
        OVERWORLD,
        NETHER,
        INDOOR,
        CAVE,
        FOREST,
        DESERT,
        CORAL,
        SKY,
        INDUSTRIAL
    }

    public float x, y, z;
    public Target target = Target.INDOOR;
    public float radius = 2.2f;
    public boolean active = true;
    public String name = "";
    public int salt;
    /** Pipe color for drawing (Mario-style). */
    public int pipeColor = 0; // 0 green 1 yellow 2 red 3 blue 4 silver 5 white 6 black

    public ZonePortal() {}

    public ZonePortal(float x, float y, float z, Target t, int salt) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.target = t;
        this.salt = salt;
    }

    public static Target fromPipeColor(int c) {
        switch (c & 7) {
            case 0: return Target.FOREST;
            case 1: return Target.DESERT;
            case 2: return Target.NETHER;
            case 3: return Target.CORAL;
            case 4: return Target.CAVE;
            case 5: return Target.SKY;
            case 6: return Target.INDUSTRIAL;
            default: return Target.FOREST;
        }
    }

    public static String pipeName(int c) {
        switch (c & 7) {
            case 0: return "Green Pipe → Forest";
            case 1: return "Yellow Pipe → Desert";
            case 2: return "Red Pipe → Nether";
            case 3: return "Blue Pipe → Coral";
            case 4: return "Silver Pipe → Cave";
            case 5: return "White Pipe → Sky";
            case 6: return "Black Pipe → Industrial";
            default: return "Warp Pipe";
        }
    }
}
