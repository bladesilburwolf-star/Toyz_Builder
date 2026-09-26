# Temples, Forts & New Terrain System

**Title:** Temples, Forts & New Terrain System  
**Contributor (design):** ChatGPT  
**Implementation:** Grok  
**Date:** 2026-09-25

## Architecture

```text
toyz.builder.terrain
├── WorldConfig          seed, FLAT/NORMAL/AMPLIFIED, size, spacing
├── TerrainSample        height + climate + slope + structureScore
├── BiomeId              ~20 biomes
├── Noise                deterministic value/fractal/ridged
├── BiomeGenerator       temperature, moisture, continentalness → biome
├── LandformGenerator    continents, ranges, foothills, plateaus, mesas, valleys
├── WaterGenerator       oceans, meandering rivers that carve channels
├── TerrainMeshBuilder   continuous mesh (polygons, normals, UVs) — LOD-ready
├── TerrainDecorator     biome → tree/rock density + preferred wood GLB key
├── StructureSite        footprint + score + kind (fort/temple/village/bridge)
├── LandmarkPlacement    site search + bridge crossing candidates
└── TerrainGenerator     facade → WorldData
```

`Terrain.ForestTerrain.v2` holds `WorldData`.  
`Terrain.getTerrainHeight` / mesh `heightAt` sample **V2 continuous landforms** when V2 init succeeds.  
Legacy draw (trees, rocks, vegetation) remains; Erector/Survival/combat untouched.

## Seed behavior

All noise is seeded from `WorldConfig.seed`. Same seed ⇒ same landforms, rivers, biomes, sites.

## World types

| Type | Behavior |
|------|----------|
| FLAT | Low detail height, minimal water |
| NORMAL | Full landform + river stack |
| AMPLIFIED | Stronger ranges / heightScale |

## Biome system

Climate fields: temperature, moisture, continentalness, erosion, elevation, water proximity.  
Soft classification (no hard grid borders). Includes snow, taiga, forest, meadow, jungle, savanna, desert, swamp, volcanic, birch, alpine, badlands, mesa, mangrove, beach, highlands, flower meadow, dry forest, frozen lake, ocean.

## Water

- Ocean level from height scale  
- 2–4 meandering rivers; **channels carve** the height field  
- `WaterGenerator.RiverPath` retained for **bridge crossing** API  

## Mesh generation

`TerrainMeshBuilder` builds a continuous grid mesh with smooth normals and tiled UVs for 512×512 materials.  
Sample spacing ≠ fixed render resolution (capped for older GPUs). Future LOD can rebuild with larger `meshSpacing`.

## Structure suitability

Each sample computes slope, flatness, water distance, biome, `structureScore`.  
`LandmarkPlacement.findSites` picks spaced fort/temple/village candidates.  
`findBridgeCrossings` evaluates river banks (orient bridge perpendicular to flow in a later pass).

## Construction pads

Sites expose footprint width/depth and score. Structure systems should **prefer** these points and apply only subtle local leveling — not a giant flat square.

## Bridge integration points

```text
river → findBridgeCrossings → bank eval → StructureSite(kind=bridge) → StructureGenerator
```

## GLB decoration

`TerrainDecorator.forBiome` returns preferred wood key (`oak`, `pine`, `birch`, …) matching `assets/models/{wood}logh.glb` / `logv.glb`, plus rock/volcanic density hints. Placement still driven by existing tree lists; prefer authored GLBs over primitives.

## Future LOD / chunking

- Keep `WorldData.sample(x,z)` as the authority for height/biome  
- Rebuild meshes per chunk with coarser `meshSpacing` at distance  
- Do not couple combat/mobs to generation  

## Compatibility

- Survival, Creative, Piece/Erector, mobs unchanged  
- If V2 init fails, legacy `heightAt` noise remains  
