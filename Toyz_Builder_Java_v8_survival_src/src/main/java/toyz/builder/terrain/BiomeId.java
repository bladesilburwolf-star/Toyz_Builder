package toyz.builder.terrain;

/** Overworld + water + Nether biomes. */
public enum BiomeId {
    OCEAN,
    BEACH,
    CORAL_REEF,
    FROZEN_LAKE,
    SNOW,
    TAIGA,
    ALPINE,
    FOREST,
    BIRCH,
    DRY_FOREST,
    MEADOW,
    FLOWER_MEADOW,
    HIGHLANDS,
    JUNGLE,
    MANGROVE,
    SWAMP,
    SAVANNA,
    DESERT,
    BADLANDS,
    MESA,
    VOLCANIC,
    // Nether (dimension-only)
    NETHER_WASTES,
    NETHER_CRIMSON,
    NETHER_BASALT;

    public String displayName() {
        switch (this) {
            case FROZEN_LAKE: return "Frozen Lake";
            case FLOWER_MEADOW: return "Flower Meadow";
            case DRY_FOREST: return "Dry Forest";
            case CORAL_REEF: return "Coral Reef";
            case NETHER_WASTES: return "Nether Wastes";
            case NETHER_CRIMSON: return "Crimson Forest";
            case NETHER_BASALT: return "Basalt Deltas";
            default: return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
        }
    }

    public boolean isNether() {
        return this == NETHER_WASTES || this == NETHER_CRIMSON || this == NETHER_BASALT;
    }

    public boolean isWaterBody() {
        return this == OCEAN || this == CORAL_REEF || this == FROZEN_LAKE;
    }
}
