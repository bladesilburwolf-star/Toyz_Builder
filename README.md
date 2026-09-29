# Toyz_Test_New / LWJGL

Primary gameplay runtime (replacing Raylib for public builds).

**Baseline:** ported from `ToyzBuilderWorldGen` @ 9d1e82e  
**Migration order:** window loop → input → renderer → … (see repo root `LWJGL_MERGE_HANDOFF.md`)

## Run (Windows)
```bat
cd Toyz_Test_New\LWJGL
gradlew.bat run
```
Place `assets/` next to this folder (same layout as WorldGen / Raylib assets).

## Status
- Window + game loop
- Textured multi-biome terrain + water
- FPS / third-person player
- Pieces and builder working and smooth
- Trees on landscape
- Inventory working but glitched and does not look proper
- Minecraft style controls
- New landscape features
- Fixed Inventory UI
- Dungeons, Towns, Graveyards, and Forts All Added
- Options Menu Added
- run bat optimized for performance
- towns, dungeons, and graveyards all expanded
- collision and artifacts issues addressed
-swim physics and survival early
- dungeons variety, water systems, and early caves
