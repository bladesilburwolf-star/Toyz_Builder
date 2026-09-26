package toyz.builder.terrain;

/**
 * Material identity separate from texture — render with existing rock/dirt + tint.
 */
public enum UndergroundMaterial {
    STONE,
    DEEPSLATE,
    COAL,
    IRON,
    COPPER,
    GOLD,
    REDSTONE,
    DIAMOND,
    OBSIDIAN,
    CRYSTAL,
    LAVA_ROCK;

    /** RGB multiply tint for rock/stone diffuse (0..255). */
    public int[] tint() {
        switch (this) {
            case STONE:     return new int[]{160, 160, 165};
            case DEEPSLATE: return new int[]{70, 75, 85};
            case COAL:      return new int[]{45, 45, 48};
            case IRON:      return new int[]{190, 160, 140};
            case COPPER:    return new int[]{180, 110, 70};
            case GOLD:      return new int[]{230, 200, 60};
            case REDSTONE:  return new int[]{180, 40, 40};
            case DIAMOND:   return new int[]{80, 200, 220};
            case OBSIDIAN:  return new int[]{25, 10, 40};
            case CRYSTAL:   return new int[]{160, 100, 220};
            case LAVA_ROCK: return new int[]{120, 50, 30};
            default:        return new int[]{140, 140, 140};
        }
    }
}
