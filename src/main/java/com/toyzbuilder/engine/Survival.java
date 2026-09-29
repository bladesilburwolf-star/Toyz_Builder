package com.toyzbuilder.engine;

/**
 * Lightweight survival layer — HP, fall damage, mode gate for creative powers.
 * Expand later: hunger, mining hardness, mobs, crafting.
 */
public final class Survival {

    public enum Mode { CREATIVE, SURVIVAL }

    private Mode mode = Mode.CREATIVE;
    private float hp = 20f;
    private float maxHp = 20f;
    private float fallStartY = Float.NaN;
    private boolean wasGrounded = true;

    public Mode mode() { return mode; }
    public boolean isSurvival() { return mode == Mode.SURVIVAL; }
    public boolean isCreative() { return mode == Mode.CREATIVE; }

    public void setMode(Mode m) {
        mode = m;
        if (m == Mode.CREATIVE) {
            hp = maxHp;
            fallStartY = Float.NaN;
        }
    }

    public float hp() { return hp; }
    public float maxHp() { return maxHp; }
    public float hpRatio() { return maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / maxHp)); }

    public void heal(float amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public void damage(float amount) {
        if (mode == Mode.CREATIVE) return;
        hp = Math.max(0, hp - amount);
    }

    public boolean isDead() {
        return mode == Mode.SURVIVAL && hp <= 0f;
    }

    public void respawn(float x, float y, float z, PlayerController player) {
        hp = maxHp;
        fallStartY = Float.NaN;
        wasGrounded = true;
        player.setPlayerPos(x, y, z);
    }

    /**
     * Call each tick after physics. Tracks fall distance while airborne and
     * applies Minecraft-ish fall damage on landing (safe fall ~3 blocks).
     */
    public void updateFallDamage(PlayerController player) {
        if (mode != Mode.SURVIVAL) {
            wasGrounded = true;
            fallStartY = Float.NaN;
            return;
        }
        boolean grounded = player.isGrounded();
        float y = player.getPlayerPos().y;
        if (wasGrounded && !grounded) {
            fallStartY = y;
        } else if (!wasGrounded && grounded && !Float.isNaN(fallStartY)) {
            float dropped = fallStartY - y;
            // safe fall ~3 units; every unit beyond deals 1 HP
            if (dropped > 3.5f) {
                damage(dropped - 3.5f);
            }
            fallStartY = Float.NaN;
        }
        if (grounded) fallStartY = Float.NaN;
        wasGrounded = grounded;
    }
}
