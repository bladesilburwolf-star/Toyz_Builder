package toyz.builder;

/**
 * Official split: Creative (builder) vs Survival (Light World).
 * Keys, HUD, inventory, and placement all branch on this.
 */
public enum GameMode {
    /** Free build: piece hotbar, inventory, place/rotate/stack. No combat. */
    CREATIVE,
    /** Light World: item/weapon hotbar, survival inventory, HP, mobs, craft. No free piece spam. */
    SURVIVAL;

    public boolean isCreative() { return this == CREATIVE; }
    public boolean isSurvival() { return this == SURVIVAL; }
}
