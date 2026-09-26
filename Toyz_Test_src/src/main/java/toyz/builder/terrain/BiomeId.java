package toyz.builder.terrain;

/** Overworld + water + Nether + expanded forests. */
public enum BiomeId {
    OCEAN,
    BEACH,
    CORAL_REEF,
    FROZEN_LAKE,
    SNOW,
    TAIGA,          // evergreen / spruce
    EVERGREEN,      // denser conifer stands
    ALPINE,
    FOREST,
    BIRCH,
    DRY_FOREST,
    DARK_FOREST,    // foggy canopy
    REDWOOD,
    BAMBOO,
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
    NETHER_WASTES,
    NETHER_CRIMSON,
    NETHER_BASALT;

    public String displayName() {
        switch (this) {
            case FROZEN_LAKE: return "Frozen Lake";
            case FLOWER_MEADOW: return "Flower Meadow";
            case DRY_FOREST: return "Dry Forest";
            case DARK_FOREST: return "Dark Forest";
            case CORAL_REEF: return "Coral Reef";
            case EVERGREEN: return "Evergreen";
            case REDWOOD: return "Redwood";
            case BAMBOO: return "Bamboo Forest";
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

    public boolean isForest() {
        return this == FOREST || this == BIRCH || this == TAIGA || this == EVERGREEN
                || this == DARK_FOREST || this == REDWOOD || this == BAMBOO
                || this == JUNGLE || this == DRY_FOREST;
    }
}
