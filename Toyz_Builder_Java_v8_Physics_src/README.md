# Toyz Builder Engine — Java v5 (Jaylib)

Separate line from the C/Raylib desktop build. Path toward **Android OpenJDK**.

## Requirements
- JDK 17+ (`javac` on PATH). On Windows, a full JDK — not JRE-only.
- `gradlew.bat` (Windows) or `./gradlew` (Linux/macOS)

## Build / run
```bat
compile_and_run.bat build
compile_and_run.bat
```
```
./gradlew compileJava
./gradlew run
```
Keep `assets/` next to the project (textures + skyboxes).

## Controls (Java v5)
- Title: New World / Continue / Settings / Exit
- ESC pause · E inventory · C camera · M weather cycle
- F11 / Alt+Enter fullscreen

## Jaylib 6 notes
- Texture not Texture2D; RenderTexture not RenderTexture2D
- Ray._position() not Ray.position()
- String.format instead of TextFormat varargs

## Status
`compileJava` is the intended verification command. Java v5 keeps the working v4 foundation and expands the open-world terrain generator.

## Java v5 World Overhaul
- 640m x 640m open-world terrain target with clipped tree/vegetation drawing.
- 20 biome definitions with climate + elevation variants.
- Expanded macro terrain: mountains, alpine/highlands, mesas, badlands terraces and gentler coastal lowlands.
- Natural land features: boulders, mesa pillars, sand pits, snow drifts and volcanic basalt columns.
- Local lakes and segmented rivers replace the old world-sized water plane.
- Cold-region water freezes visually; snow/ice hooks are exposed through biome and water state.
- Existing caves, magma pools, rocks, trees and vegetation remain part of the generator.
- Architecture/buildings are deliberately not part of this pass.



## V6 World Generation
NEW WORLD now opens a second world-creation menu with Flat, Regular and Amplified terrain, deterministic advanced seeding, and Structures/No Structures. Procedural structures are generated as ordinary placed pieces and use tunable MapSystem.StructureSettings.
