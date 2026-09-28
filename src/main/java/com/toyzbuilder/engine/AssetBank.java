package com.toyzbuilder.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * All known Toyz texture folders → GL textures.
 * Paths try cwd-relative and assets/ prefixes.
 */
public final class AssetBank {

    public enum Slot {
        GRASS, SAND, SNOW, ROCK, DIRT, WATER, STONE, CONCRETE,
        BARK, BARK_PINE, BARK_BIRCH, BARK_CEDAR, BARK_CHERRY, BARK_REDWOOD, BARK_DARKOAK,
        LEAVES, PLANKS, PLANKS_PINE, PLANKS_BIRCH,
        IRON, STEEL, COPPER, TITANIUM, GLASS, ICE,
        CRYSTAL, DIAMOND, QUARTZ, MAGMA, MAGNECITE,
        CAST_IRON, IRON_BARS, HOUSE_WALL, ROOF, LADDER, BARK_BAMBOO, PLANKS_CEDAR, PLANKS_CHERRY, PLANKS_MAHOGANY, PLANKS_TEAK, PLANKS_WALNUT, PLANKS_BAMBOO
    }

    private final Map<Slot, Texture> map = new HashMap<>();
    private Texture fallback;

    public void loadAll() {
        map.put(Slot.GRASS, first("textures/grass/grass1.png", "assets/textures/grass/grass1.png"));
        map.put(Slot.SAND, first("textures/sand/sand1.png", "textures/sand/sand2.png", "assets/textures/sand/sand1.png"));
        map.put(Slot.SNOW, first("textures/snow/snow.png", "assets/textures/snow/snow.png"));
        map.put(Slot.ROCK, first("textures/rocks/rock1.png", "textures/rocks/rock2.png", "assets/textures/rocks/rock1.png"));
        map.put(Slot.DIRT, first("textures/dirt/dirt1.png", "assets/textures/dirt/dirt1.png"));
        map.put(Slot.WATER, first("textures/water/water.jpg", "assets/textures/water/water.jpg"));
        map.put(Slot.STONE, first("textures/stone/stone1.png", "assets/textures/stone/stone1.png"));
        map.put(Slot.CONCRETE, first("textures/stone/concrete.jpg", "textures/stone/stone1.png", "assets/textures/stone/concrete.jpg"));

        map.put(Slot.BARK, first("textures/trees/oaklog.jpg", "textures/trees/bark1.png", "assets/textures/trees/oaklog.jpg"));
        map.put(Slot.BARK_PINE, first("textures/trees/pinelog.jpg", "textures/trees/oaklog.jpg", "assets/textures/trees/pinelog.jpg"));
        map.put(Slot.BARK_BIRCH, first("textures/trees/birchlog.jpg", "textures/trees/bark1.png", "assets/textures/trees/birchlog.jpg"));
        map.put(Slot.BARK_CEDAR, first("textures/trees/cedarlog.jpg", "textures/trees/oaklog.jpg", "assets/textures/trees/cedarlog.jpg"));
        map.put(Slot.BARK_CHERRY, first("textures/trees/cherrybark.jpg", "textures/trees/cherrylog.jpg", "textures/trees/oaklog.jpg"));
        map.put(Slot.BARK_REDWOOD, first("textures/trees/redwoodlog.jpg", "textures/trees/oaklog.jpg", "assets/textures/trees/redwoodlog.jpg"));
        map.put(Slot.BARK_DARKOAK, first("textures/trees/darkoaklog.jpg", "textures/trees/oaklog.jpg", "assets/textures/trees/darkoaklog.jpg"));

        map.put(Slot.LEAVES, first("textures/grass/grass1.png", "assets/textures/grass/grass1.png"));
        map.put(Slot.PLANKS, first("textures/wood/oakplank.jpg", "assets/textures/wood/oakplank.jpg"));
        map.put(Slot.PLANKS_PINE, first("textures/wood/pineplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/pineplank.jpg"));
        map.put(Slot.PLANKS_BIRCH, first("textures/wood/birchplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/birchplank.jpg"));

        map.put(Slot.IRON, first("textures/metal/iron.jpg", "textures/metal/steel.jpg", "assets/textures/metal/iron.jpg"));
        map.put(Slot.STEEL, first("textures/metal/steel.jpg", "textures/metal/iron.jpg", "assets/textures/metal/steel.jpg"));
        map.put(Slot.COPPER, first("textures/metal/copper.jpg", "textures/metal/iron.jpg", "assets/textures/metal/copper.jpg"));
        map.put(Slot.TITANIUM, first("textures/metal/titanium.jpg", "textures/metal/steel.jpg", "assets/textures/metal/titanium.jpg"));
        map.put(Slot.GLASS, first("textures/glass/glass.png", "textures/glass/glass1.png", "assets/textures/glass/glass.png"));
        map.put(Slot.ICE, first("textures/ice/ice.jpg", "assets/textures/ice/ice.jpg"));
        map.put(Slot.CRYSTAL, first("textures/rocks/crystal.png", "textures/rocks/crystal.jpg", "assets/textures/rocks/crystal.png"));
        map.put(Slot.DIAMOND, first("textures/rocks/diamond.png", "textures/rocks/diamond.jpg", "assets/textures/rocks/diamond.png"));
        map.put(Slot.QUARTZ, first("textures/rocks/quartz.png", "textures/rocks/quartz.jpg", "assets/textures/rocks/quartz.png"));
        map.put(Slot.MAGMA, first("textures/magma/magma.jpg", "assets/textures/magma/magma.jpg", "textures/rocks/rock1.png"));
        map.put(Slot.MAGNECITE, first("textures/rocks/magnesite.png", "textures/rocks/magnesite.jpg", "assets/textures/rocks/magnesite.png"));
        map.put(Slot.CAST_IRON, first("textures/metals/castiron.png", "textures/metals/castiron.jpg", "assets/textures/metals/castiron.png"));
        map.put(Slot.IRON_BARS, first("textures/metals/ironbars.png", "textures/metals/ironbars.jpg", "assets/textures/metals/ironbars.png"));
        map.put(Slot.HOUSE_WALL, first("textures/house/housewall1.png", "assets/textures/house/housewall1.png"));
        map.put(Slot.ROOF, first("textures/house/rooff1.png", "textures/house/roof2.png", "assets/textures/house/rooff1.png"));
        map.put(Slot.LADDER, first("textures/house/ladder1.png", "assets/textures/house/ladder1.png"));
        map.put(Slot.BARK_BAMBOO, first("textures/trees/bamboobark.png", "textures/trees/bamboobark.jpg", "assets/textures/trees/bamboobark.png"));
        map.put(Slot.PLANKS_CEDAR, first("textures/wood/cedarplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/cedarplank.jpg"));
        map.put(Slot.PLANKS_CHERRY, first("textures/wood/cherryplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/cherryplank.jpg"));
        map.put(Slot.PLANKS_MAHOGANY, first("textures/wood/mahoganyplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/mahoganyplank.jpg"));
        map.put(Slot.PLANKS_TEAK, first("textures/wood/teakplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/teakplank.jpg"));
        map.put(Slot.PLANKS_WALNUT, first("textures/wood/walnutplank.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/walnutplank.jpg"));
        map.put(Slot.PLANKS_BAMBOO, first("textures/wood/bambooplanks.jpg", "textures/wood/oakplank.jpg", "assets/textures/wood/bambooplanks.jpg"));

        int ok = 0;
        for (Map.Entry<Slot, Texture> e : map.entrySet()) {
            Texture t = e.getValue();
            if (t != null && t.getWidth() > 1) ok++;
        }
        System.out.println("[AssetBank] slots=" + map.size() + " loaded-ish=" + ok);
    }

    private static Texture first(String... paths) {
        for (String p : paths) {
            Texture t = Texture.load(p);
            if (t != null && t.getWidth() > 1) return t;
        }
        return Texture.load(paths[0]);
    }

    /** Lookup by name (e.g. "PLANKS", "STONE") for structure parts. */
    public Texture forKey(String key) {
        if (key == null || key.isEmpty()) return null;
        try {
            return get(Slot.valueOf(key.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return get(Slot.PLANKS);
        }
    }

    public Texture get(Slot s) {
        Texture t = map.get(s);
        if (t != null) return t;
        if (fallback == null) {
            System.err.println("[AssetBank] missing slot " + s);
            fallback = Texture.load("missing");
        }
        return fallback;
    }

    /** Map piece id → texture for cube/fallback rendering. */
    public Texture forPiece(String pieceId) {
        if (pieceId == null) return get(Slot.STONE);
        String u = pieceId.toUpperCase();
        if (u.contains("PINE") && u.contains("LOG")) return get(Slot.BARK_PINE);
        if (u.contains("BIRCH") && u.contains("LOG")) return get(Slot.BARK_BIRCH);
        if (u.contains("CEDAR")) return get(Slot.BARK_CEDAR);
        if (u.contains("CHERRY")) return get(Slot.BARK_CHERRY);
        if (u.contains("REDWOOD")) return get(Slot.BARK_REDWOOD);
        if (u.contains("DARKOAK") || u.contains("DARK_OAK")) return get(Slot.BARK_DARKOAK);
        if (u.contains("BAMBOO") && u.contains("LOG")) return get(Slot.BARK_BAMBOO);
        if (u.contains("CEDAR") && (u.contains("PLANK") || u.contains("FLOOR") || u.contains("BLOCK"))) return get(Slot.PLANKS_CEDAR);
        if (u.contains("CHERRY") && (u.contains("PLANK") || u.contains("FLOOR") || u.contains("BLOCK"))) return get(Slot.PLANKS_CHERRY);
        if (u.contains("MAHOGANY") && (u.contains("PLANK") || u.contains("FLOOR") || u.contains("BLOCK"))) return get(Slot.PLANKS_MAHOGANY);
        if (u.contains("TEAK") && (u.contains("PLANK") || u.contains("FLOOR") || u.contains("BLOCK"))) return get(Slot.PLANKS_TEAK);
        if (u.contains("WALNUT") && (u.contains("PLANK") || u.contains("FLOOR") || u.contains("BLOCK"))) return get(Slot.PLANKS_WALNUT);
        if (u.contains("BAMBOO") && (u.contains("PLANK") || u.contains("BLOCK"))) return get(Slot.PLANKS_BAMBOO);
        if (u.contains("LOG") || u.contains("BAMBOO")) return get(Slot.BARK);
        if (u.contains("PINE") && (u.contains("PLANK") || u.contains("BLOCK") || u.contains("RAMP"))) return get(Slot.PLANKS_PINE);
        if (u.contains("BIRCH") && (u.contains("PLANK") || u.contains("BLOCK"))) return get(Slot.PLANKS_BIRCH);
        if (u.contains("PLANK") || u.contains("FLOOR") || u.contains("OAK_BLOCK") || u.contains("BLOCK_OAK") || u.contains("RAMP_OAK") || u.contains("DOOR") || u.contains("SIGN") || u.contains("PANEL") || u.contains("RAFT"))
            return get(Slot.PLANKS);
        if (u.contains("SAND")) return get(Slot.SAND);
        if (u.contains("DIRT")) return get(Slot.DIRT);
        if (u.contains("GRASS")) return get(Slot.GRASS);
        if (u.contains("SNOW")) return get(Slot.SNOW);
        if (u.contains("ICE")) return get(Slot.ICE);
        if (u.contains("GLASS")) return get(Slot.GLASS);
        if (u.contains("CASTIRON")) return get(Slot.CAST_IRON);
        if (u.contains("IRON_BAR") || u.contains("IRONBARS")) return get(Slot.IRON_BARS);
        if (u.contains("IRON") || u.contains("MAG_ROD") || u.contains("CAGE")) return get(Slot.IRON);
        if (u.contains("STEEL") || u.contains("MAG_BALL")) return get(Slot.STEEL);
        if (u.contains("COPPER")) return get(Slot.COPPER);
        if (u.contains("TITANIUM")) return get(Slot.TITANIUM);
        if (u.contains("CRYSTAL")) return get(Slot.CRYSTAL);
        if (u.contains("DIAMOND")) return get(Slot.DIAMOND);
        if (u.contains("QUARTZ")) return get(Slot.QUARTZ);
        if (u.contains("MAGMA")) return get(Slot.MAGMA);
        if (u.contains("MAGNECITE") || u.contains("MAGNETIX")) return get(Slot.MAGNECITE);
        if (u.contains("HOUSE") || u.contains("WALL")) return get(Slot.HOUSE_WALL);
        if (u.contains("ROOF")) return get(Slot.ROOF);
        if (u.contains("LADDER")) return get(Slot.LADDER);
        if (u.contains("CONCRETE")) return get(Slot.CONCRETE);
        if (u.contains("BLOCK_STONE") || u.equals("STONE") || u.contains("STONE_BLOCK")) return get(Slot.STONE);
        if (u.contains("STONE") || u.contains("BOULDER") || u.contains("ROCK") || u.contains("RAMP_STONE")) return get(Slot.ROCK);
        if (u.contains("BLOCK")) return get(Slot.STONE);
        return get(Slot.PLANKS);
    }

    public void bindTerrainSet() {
        get(Slot.GRASS).bind(0);
        get(Slot.SAND).bind(1);
        get(Slot.SNOW).bind(2);
        get(Slot.ROCK).bind(3);
        get(Slot.DIRT).bind(4);
        get(Slot.WATER).bind(5);
    }

    /** Re-apply the Options texture filter to every loaded texture. */
    public void applyFiltering(int mode, int aniso) {
        for (Texture t : map.values()) t.setFilter(mode, aniso);
    }

    public void cleanup() {
        for (Texture t : map.values()) t.cleanup();
        map.clear();
        if (fallback != null) {
            fallback.cleanup();
            fallback = null;
        }
    }
}
