# Terrain V3 — Water + Depth + Mapgen Presets

**Title:** Temples, Forts & New Terrain System (V3)  
**Design:** ChatGPT  
**Implementation:** Grok  
**Date:** 2026-09-25

## What changed

### Continuous water (no cubes)
`WaterMeshBuilder` builds:
- **Ocean/lake** mesh — quads only where terrain is submerged
- **River ribbons** along `WaterGenerator` centerlines (variable width, UVs)
- **Frozen** mesh variant

`Terrain.drawForestTerrain` draws these models; cube fallback only if mesh build fails.

### Depth + caves
`DepthGenerator` — solid depth field + cave openness under surface  
`CaveGenerator` — hillside cave mouths from depth (not fake sinkholes)

### Mapgen presets (menu)
V2…V7, Flat, Amplified — tune heightScale, maxDepth, landform/water strength  
**Sky Islands ON/OFF** — continuous floating meshes via `SkyIslandGenerator`

### Architecture
```
TerrainGenerator
 ├── LandformGenerator / BiomeGenerator / WaterGenerator
 ├── DepthGenerator / CaveGenerator
 ├── TerrainMeshBuilder / WaterMeshBuilder
 ├── SkyIslandGenerator
 └── LandmarkPlacement
```
`Terrain.java` remains the V8 render/compatibility bridge.

## Menu
CREATE WORLD → World Type + Mapgen Preset (V2–V7) + Sky Islands + Structures + Seed
