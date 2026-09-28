package com.toyzbuilder.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder piece definitions (from Raylib Toyz Piece set, engine-independent).
 * GLB paths are under assets/models/ — missing files use unit cube fallback.
 */
public final class PieceCatalog {

    public enum Group {
        WOOD("Wood / Logs"),
        BLOCKS("Blocks"),
        BUILD("Doors / Ramps"),
        MAGNETIX("Magnetix"),
        MISC("Misc / Props"),
        ACTORS("Actors");

        public final String label;
        Group(String label) { this.label = label; }
    }

    public static final class Def {
        public final String id;
        public final String label;
        public final Group group;
        public final float sx, sy, sz;
        public final boolean solid;
        public final String modelPath; // nullable
        public final float r, g, b;

        public Def(String id, String label, Group group,
                   float sx, float sy, float sz, boolean solid,
                   String modelPath, float r, float g, float b) {
            this.id = id; this.label = label; this.group = group;
            this.sx = sx; this.sy = sy; this.sz = sz; this.solid = solid;
            this.modelPath = modelPath;
            this.r = r; this.g = g; this.b = b;
        }
    }

    private static final List<Def> ALL = new ArrayList<>();
    private static final List<Def>[] BY_GROUP;

    static {
        // ---- Wood / logs ----
        add("OAK_LOG_H", "Oak Log H", Group.WOOD, 2f, 0.5f, 0.5f, true, "oaklogh.glb", 0.55f, 0.38f, 0.20f);
        add("OAK_LOG_V", "Oak Log V", Group.WOOD, 0.5f, 2f, 0.5f, true, "oaklogv.glb", 0.55f, 0.38f, 0.20f);
        add("PINE_LOG_H", "Pine Log H", Group.WOOD, 2f, 0.5f, 0.5f, true, "pinelogh.glb", 0.50f, 0.36f, 0.22f);
        add("PINE_LOG_V", "Pine Log V", Group.WOOD, 0.5f, 2f, 0.5f, true, "pinelogv.glb", 0.50f, 0.36f, 0.22f);
        add("BIRCH_LOG_H", "Birch Log H", Group.WOOD, 2f, 0.5f, 0.5f, true, "birchlogh.glb", 0.85f, 0.82f, 0.75f);
        add("BIRCH_LOG_V", "Birch Log V", Group.WOOD, 0.5f, 2f, 0.5f, true, "birchlogv.glb", 0.85f, 0.82f, 0.75f);
        add("CEDAR_LOG_H", "Cedar Log H", Group.WOOD, 2f, 0.5f, 0.5f, true, "cedarlogh.glb", 0.45f, 0.28f, 0.16f);
        add("CEDAR_LOG_V", "Cedar Log V", Group.WOOD, 0.5f, 2f, 0.5f, true, "cedarlogv.glb", 0.45f, 0.28f, 0.16f);
        add("CHERRY_LOG_H", "Cherry Log H", Group.WOOD, 2f, 0.5f, 0.5f, true, "cherrylogh.glb", 0.55f, 0.30f, 0.28f);
        add("CHERRY_LOG_V", "Cherry Log V", Group.WOOD, 0.5f, 2f, 0.5f, true, "cherrylogv.glb", 0.55f, 0.30f, 0.28f);
        add("REDWOOD_LOG_H", "Redwood Log H", Group.WOOD, 2.4f, 0.6f, 0.6f, true, "redwoodlog_h.glb", 0.50f, 0.22f, 0.15f);
        add("REDWOOD_LOG_V", "Redwood Log V", Group.WOOD, 0.6f, 2.4f, 0.6f, true, "redwoodlog_v.glb", 0.50f, 0.22f, 0.15f);
        add("DARKOAK_LOG_H", "Dark Oak H", Group.WOOD, 2f, 0.5f, 0.5f, true, "darkoaklog_h.glb", 0.28f, 0.18f, 0.12f);
        add("DARKOAK_LOG_V", "Dark Oak V", Group.WOOD, 0.5f, 2f, 0.5f, true, "darkoaklog_v.glb", 0.28f, 0.18f, 0.12f);
        add("BAMBOO_V", "Bamboo V", Group.WOOD, 0.3f, 2f, 0.3f, true, "bamboo_v.glb", 0.45f, 0.65f, 0.25f);
        add("BAMBOO_H", "Bamboo H", Group.WOOD, 2f, 0.3f, 0.3f, true, "bamboo_h.glb", 0.45f, 0.65f, 0.25f);
        add("PLANK_OAK", "Oak Plank", Group.WOOD, 1f, 0.15f, 1f, true, "oakfloor.glb", 0.62f, 0.48f, 0.28f);
        add("PLANK_PINE", "Pine Plank", Group.WOOD, 1f, 0.15f, 1f, true, "pinefloor.glb", 0.58f, 0.45f, 0.28f);

        // ---- Blocks ----
        add("BLOCK_OAK", "Oak Block", Group.BLOCKS, 1f, 1f, 1f, true, "oakblock.glb", 0.55f, 0.40f, 0.22f);
        add("BLOCK_PINE", "Pine Block", Group.BLOCKS, 1f, 1f, 1f, true, "pineblock.glb", 0.50f, 0.38f, 0.22f);
        add("BLOCK_BIRCH", "Birch Block", Group.BLOCKS, 1f, 1f, 1f, true, "birchblock.glb", 0.80f, 0.78f, 0.70f);
        add("BLOCK_STONE", "Stone Block", Group.BLOCKS, 1f, 1f, 1f, true, "rockblock.glb", 0.55f, 0.55f, 0.58f);
        add("BLOCK_CONCRETE", "Concrete", Group.BLOCKS, 1f, 1f, 1f, true, "concreteblock.glb", 0.60f, 0.60f, 0.62f);
        add("BLOCK_SAND", "Sand Block", Group.BLOCKS, 1f, 1f, 1f, true, "sandblock.glb", 0.86f, 0.78f, 0.45f);
        add("BLOCK_DIRT", "Dirt Block", Group.BLOCKS, 1f, 1f, 1f, true, "dirtblock.glb", 0.45f, 0.32f, 0.18f);
        add("BLOCK_GRASS", "Grass Block", Group.BLOCKS, 1f, 1f, 1f, true, "grassblock.glb", 0.30f, 0.55f, 0.22f);
        add("BLOCK_SNOW", "Snow Block", Group.BLOCKS, 1f, 1f, 1f, true, "snowblock.glb", 0.92f, 0.94f, 0.96f);
        add("BLOCK_ICE", "Ice Block", Group.BLOCKS, 1f, 1f, 1f, true, "iceblock.glb", 0.70f, 0.88f, 0.95f);
        add("BLOCK_GLASS", "Glass Block", Group.BLOCKS, 1f, 1f, 1f, true, "glassblock.glb", 0.70f, 0.85f, 0.95f);
        add("BLOCK_IRON", "Iron Block", Group.BLOCKS, 1f, 1f, 1f, true, "ironblock.glb", 0.75f, 0.75f, 0.78f);
        add("BLOCK_STEEL", "Steel Block", Group.BLOCKS, 1f, 1f, 1f, true, "steelblock.glb", 0.55f, 0.58f, 0.62f);
        add("BLOCK_COPPER", "Copper Block", Group.BLOCKS, 1f, 1f, 1f, true, "copperblock.glb", 0.72f, 0.45f, 0.28f);
        add("BLOCK_TITANIUM", "Titanium", Group.BLOCKS, 1f, 1f, 1f, true, "titaniumblock.glb", 0.65f, 0.70f, 0.75f);
        add("BLOCK_CRYSTAL", "Crystal", Group.BLOCKS, 1f, 1f, 1f, true, "crystalblock.glb", 0.55f, 0.85f, 0.95f);
        add("BLOCK_DIAMOND", "Diamond", Group.BLOCKS, 1f, 1f, 1f, true, "diamondblock.glb", 0.55f, 0.90f, 0.95f);
        add("BLOCK_QUARTZ", "Quartz", Group.BLOCKS, 1f, 1f, 1f, true, "quartzblock.glb", 0.90f, 0.88f, 0.85f);
        add("BLOCK_MAGMA", "Magma", Group.BLOCKS, 1f, 1f, 1f, true, "magmablock.glb", 0.85f, 0.35f, 0.12f);
        add("BLOCK_MAGNECITE", "Magnecite", Group.BLOCKS, 1f, 1f, 1f, true, "magneciteblock.glb", 0.40f, 0.25f, 0.55f);

        // ---- Build ----
        add("DOOR", "Door", Group.BUILD, 1.2f, 2.2f, 0.3f, true, "door1.glb", 0.55f, 0.35f, 0.18f);
        add("WINDOW", "Window", Group.BUILD, 1.0f, 1.0f, 0.2f, true, "window1.glb", 0.70f, 0.85f, 0.95f);
        add("SIGN", "Sign", Group.BUILD, 0.8f, 1.2f, 0.15f, true, "sign1.glb", 0.55f, 0.40f, 0.22f);
        add("RAMP_OAK_S", "Oak Ramp S", Group.BUILD, 1f, 0.5f, 1f, true, "oakrampsmall.glb", 0.55f, 0.40f, 0.22f);
        add("RAMP_OAK_M", "Oak Ramp M", Group.BUILD, 1.5f, 0.75f, 1f, true, "oakrampmedium.glb", 0.55f, 0.40f, 0.22f);
        add("RAMP_OAK_L", "Oak Ramp L", Group.BUILD, 2f, 1f, 1f, true, "oakramplong.glb", 0.55f, 0.40f, 0.22f);
        add("RAMP_STONE_S", "Stone Ramp S", Group.BUILD, 1f, 0.5f, 1f, true, "stonerampsmall.glb", 0.55f, 0.55f, 0.58f);
        add("RAMP_STONE_M", "Stone Ramp M", Group.BUILD, 1.5f, 0.75f, 1f, true, "stonerampmedium.glb", 0.55f, 0.55f, 0.58f);
        add("RAMP_STONE_L", "Stone Ramp L", Group.BUILD, 2f, 1f, 1f, true, "stoneramplong.glb", 0.55f, 0.55f, 0.58f);
        add("PANEL", "Panel", Group.BUILD, 1f, 1f, 0.15f, true, "panelblock.glb", 0.50f, 0.48f, 0.45f);
        add("IRON_BAR", "Iron Bars", Group.BUILD, 1f, 1.5f, 0.15f, true, null, 0.55f, 0.55f, 0.60f);
        add("METAL_CAGE", "Metal Cage", Group.BUILD, 1.2f, 1.2f, 1.2f, true, "metalcage.glb", 0.50f, 0.52f, 0.55f);

        // ---- Magnetix ----
        add("MAG_BALL", "Magnetix Ball", Group.MAGNETIX, 0.6f, 0.6f, 0.6f, true, "ironball.glb", 0.30f, 0.55f, 0.95f);
        add("MAG_ROD_H", "Magnetix Rod H", Group.MAGNETIX, 2f, 0.25f, 0.25f, true, null, 0.85f, 0.20f, 0.20f);
        add("MAG_ROD_V", "Magnetix Rod V", Group.MAGNETIX, 0.25f, 2f, 0.25f, true, null, 0.85f, 0.20f, 0.20f);
        add("GLASS_BALL", "Glass Ball", Group.MAGNETIX, 0.6f, 0.6f, 0.6f, true, "glassball.glb", 0.70f, 0.90f, 0.95f);
        add("LIGHT_BALL", "Light Ball", Group.MAGNETIX, 0.5f, 0.5f, 0.5f, false, "lightball.glb", 1.0f, 0.95f, 0.55f);

        // ---- Misc ----
        add("BOULDER_S", "Boulder S", Group.MISC, 1.0f, 0.9f, 1.0f, true, "bouldersmall.glb", 0.50f, 0.50f, 0.52f);
        add("BOULDER_M", "Boulder M", Group.MISC, 1.4f, 1.2f, 1.4f, true, "bouldermedium.glb", 0.48f, 0.48f, 0.50f);
        add("BOULDER_L", "Boulder L", Group.MISC, 2.0f, 1.6f, 2.0f, true, "boulderlarge.glb", 0.45f, 0.45f, 0.48f);
        add("SLIDE", "Slide", Group.MISC, 2f, 1.5f, 1f, true, "slidered.glb", 0.90f, 0.25f, 0.20f);
        add("CHEST", "Chest", Group.MISC, 0.9f, 0.7f, 0.7f, true, "chestclosed.glb", 0.85f, 0.65f, 0.15f);
        add("RAFT", "Raft", Group.MISC, 2f, 0.3f, 1.2f, true, "raft.glb", 0.55f, 0.40f, 0.22f);

        // ---- Actors (editor markers) ----
        add("SPAWN", "Spawn", Group.ACTORS, 0.8f, 0.2f, 0.8f, false, null, 1.0f, 1.0f, 0.2f);
        add("ENEMY", "Enemy", Group.ACTORS, 0.8f, 1.4f, 0.8f, true, "slime1.glb", 1.0f, 0.31f, 0.31f);
        add("BOSS", "Boss", Group.ACTORS, 1.6f, 2.6f, 1.6f, true, "boss_ghost.glb", 0.70f, 0.16f, 0.16f);
        add("TORCH", "Torch", Group.ACTORS, 0.25f, 1.2f, 0.25f, false, null, 1.0f, 0.63f, 0.16f);
        add("LIGHT", "Light", Group.ACTORS, 0.3f, 0.3f, 0.3f, false, "lightball.glb", 1.0f, 0.90f, 0.40f);

        BY_GROUP = new List[Group.values().length];
        for (int i = 0; i < BY_GROUP.length; i++) BY_GROUP[i] = new ArrayList<>();
        for (Def d : ALL) BY_GROUP[d.group.ordinal()].add(d);
    }

    private static void add(String id, String label, Group g,
                            float sx, float sy, float sz, boolean solid,
                            String model, float r, float gcol, float b) {
        ALL.add(new Def(id, label, g, sx, sy, sz, solid, model, r, gcol, b));
    }

    public static List<Def> all() { return ALL; }

    public static List<Def> byGroup(Group g) { return BY_GROUP[g.ordinal()]; }

    public static Def get(String id) {
        if (id == null) return ALL.get(0);
        for (Def d : ALL) if (d.id.equalsIgnoreCase(id)) return d;
        // legacy editor names
        for (Def d : ALL) if (d.label.equalsIgnoreCase(id)) return d;
        return ALL.get(0);
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equalsIgnoreCase(id)) return i;
        return 0;
    }
}
