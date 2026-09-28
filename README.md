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
- Build errors check log
