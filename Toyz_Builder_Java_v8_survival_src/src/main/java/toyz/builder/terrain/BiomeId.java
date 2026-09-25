package toyz.builder.terrain;

/** ~20 biomes for Terrain V2. Transitions via blended climate fields. */
public enum BiomeId {
    OCEAN,
    BEACH,
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
    VOLCANIC;

    public String displayName() {
        switch (this) {
            case FROZEN_LAKE: return "Frozen Lake";
            case FLOWER_MEADOW: return "Flower Meadow";
            case DRY_FOREST: return "Dry Forest";
            default: return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
        }
    }
}
