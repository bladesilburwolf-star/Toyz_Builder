# Lincoln Logs 3D (raylib / C++)

High-performance Windows 3D log-builder prototype: **raylib** for rendering,
Dear ImGui (via rlImGui) for UI, ImGuizmo for translate/rotate gizmos.

Uses **OpenGL** — works on older GPUs like Radeon HD 6450 (no DX12 required).

## What's here

- `src/terrain.h` / `src/terrain.cpp` — deterministic large procedural forest terrain with low-poly trees
- `assets/textures/` — terrain, log, UI, and environment textures
- `assets/skyboxes/` — skybox textures/cubemaps
- `assets/music/` — music tracks
- `assets/sfx/` — construction and environment sound effects

- `CMakeLists.txt` — pulls raylib, Dear ImGui, rlImGui, ImGuizmo via FetchContent
- `src/piece.h` / `piece.cpp` — Straight / Notched / Corner / Roof / Window with snap points
- `src/inventory.h` / `inventory.cpp` — E-key inventory grid
- `src/main.cpp` — orbital camera, placement, **piece selection**, **red snap glow**

## Build (Visual Studio / Build Tools)

```bat
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

## Build (Ninja + MinGW)

```bat
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build
```

First configure clones dependencies (needs network once).

## Controls

| Key / Action        | Effect                                      |
|---------------------|---------------------------------------------|
| Mouse drag          | Orbit camera                                |
| E                   | Open/close inventory                        |
| Click inventory item| Start placing that piece (ghost follows)    |
| R                   | Rotate ghost 90° while placing              |
| Left click          | Place ghost / select a placed log           |
| Esc                 | Cancel placing                              |
| Delete / Backspace  | Remove selected piece                       |
| G                   | Toggle gizmo visibility (wiring is a stub)  |

## Features

- **Piece selection** — click a placed log → yellow tint; click empty space → deselect
- **Red snap glow** — while placing, nearby pieces whose snap points are in range turn red
- **Grid + neighbor snap** — ghost snaps to grid or to the closest snap point
- **Window** piece type included

## Next steps

1. Wire ImGuizmo::Manipulate for the selected piece (translate / rotate)
2. Oriented bounding boxes for tighter selection
3. Replace procedural placeholder trees with imported assets from `assets/` when ready
4. Real .glb / .obj log meshes via LoadModel()
5. Save / load of placedPieces
6. Snap orientation checks (end-to-end only, not any angle)

## Builder Mascot / Player Pass

The forest prototype now includes a small procedural blue spherical builder mascot. It uses only raylib primitives, so no external character asset is required.

- `src/player.h` / `src/player.cpp` contain player movement and rendering.
- Left stick / WASD moves the mascot relative to camera heading.
- Right stick / arrow keys orbit the camera.
- A / Space jumps when grounded.
- The camera follows the mascot through the procedural forest.
- The mascot has a simple face and small boots to establish the builder identity without importing copyrighted external character assets.
- The existing `assets/` directories remain the designated location for future textures, skyboxes, music, and SFX.
