# Toyz → single LWJGL backend (keep ideas + assets + piece builder)

**Decision (2026-09-27):** Public runtime is **LWJGL** (`ToyzBuilderWorldGen` / libwgl).  
**Raylib/Jaylib Toyz_Test:** freeze as **reference branch/build** (behavioral target) — do not delete yet; no new Raylib features.

**Repo:** `bladesilburwolf-star/ToyzBuilderWorldGen`  
**HEAD at prep:** `9d1e82e` (~1856 LOC)

**Assets / ideas:** same tree (textures, GLB, Light World / Chroma). Piece builder is **kept** — rebuilt on proper Mesh/Model/Collision, not dropped.

---

## Architecture (ChatGPT + Grok aligned)

```
WORLD DATA (engine-independent)
        │
┌───────┴────────┐
│                │
Terrain       Locations
│           ┌────┼────┐
│        Town  Dungeon  Indoor
│
└───────┬────────┘
        │
   Scene Data
        │
        ▼
  LWJGL runtime
  (Window Input Renderer Mesh Texture Material Shader Model Camera Audio)
```

**Do not** invent `RaylibWrapper.drawMesh` / `loadTexture` / `beginDrawing`.  
World systems consume clean abstractions only.

**Do not** migrate procedural generators first. Preserve them as data producers; put a solid runtime underneath, then wire gens to emit Scene/Mesh/collision.

---

## Migration order (authoritative)

| # | Layer | Status in WorldGen | Next action |
|---|--------|----------------------|-------------|
| 1 | LWJGL window + game loop | **Done** (`Window`) | Keep thin; no gen inside loop |
| 2 | Input abstraction | **Partial** (GLFW in PlayerController) | `Input` class: keys/mouse/cursor modes |
| 3 | OpenGL renderer | **Done** (`Renderer`) | Expand draw queues / layers |
| 4 | Shader/material system | **Partial** (`ShaderProgram` only) | `Material` + named shader library |
| 5 | Texture loading | **Done** (`Texture`, `AssetBank`) | Atlas / bindless later |
| 6 | GLB/model loading | **Missing** | Minimal glTF or Assimp → `Model` |
| 7 | Mesh abstraction | **Done** (`Mesh`) | Index/vertex formats documented |
| 8 | Camera | **Done** (`FirstPersonCamera`) | + third-person already |
| 9 | Collision / debug render | **Missing** | Height sample + AABB + debug draw |
| 10 | Terrain renderer | **Done** (textured heightfield) | Streaming later (#15) |
| 11 | Player | **Partial** (`PlayerController`) | Gravity, step-up, swim via Collision |
| 12 | Pieces / building | **Partial** (`Editor` + entities) | Catalog + snap + rotate on Mesh/Model |
| 13 | Mobs / sprites | **Missing** | Billboard batch after Model |
| 14 | Town / Dungeon | **Missing** (port gens last) | Mesh rooms + thick floors |
| 15 | World streaming | **Missing** | Chunks after collision solid |
| 16 | Audio | **Missing** | LWJGL OpenAL when needed |

---

## Package target (files stay ~≤400 LOC)

```
com.toyzbuilder
  Main
  engine/
    Window, Input, Renderer, ShaderProgram, Material
    Mesh, Texture, Model, AssetBank
    Camera / FirstPersonCamera, PlayerController
    Collision, Hud, Editor
  world/                    # DATA + gens (no OpenGL calls)
    World, WorldGenerator, MapFile, Entity
    DungeonGenerator, TownGenerator   # port after runtime
  ui/
    PauseMenu, WorldGenPanel
```

Generators output **heights, biomes, entity lists, collider AABBs, mesh descriptors** — never call GL directly.

---

## Piece builder (not dropped)

Raylib creative mode idea stays:

- Place / rotate / stack pieces  
- Categories (blocks, logs, ramps, Magnetix, …)  
- Save/load map  

Implementation path: Editor + `Model`/`Mesh` instances + `Collision` AABBs, same assets under `assets/models/`.  
No need for the old cube-grid fights if snap is on a fine world grid (4–8 unit style already explored).

---

## What other AIs should do

- **PRs only against ToyzBuilderWorldGen (LWJGL)**  
- **No new Raylib gameplay features**  
- Raylib tree = reference for behavior and assets only  
- Prefer new files over growing `Window.java`  
- Procedural gens: extract/port as pure Java under `world/`, no Raylib imports  

---

## Resume checklist (next Grok session)

1. [ ] `Input` abstraction (cursor, keys, edge triggers)  
2. [ ] `Collision` (terrain height + entity AABBs + debug boxes)  
3. [ ] Player grounded movement on Collision  
4. [ ] `Material` + tidy shader bind  
5. [ ] GLB `Model` loader (sparse props + pieces)  
6. [ ] Only then: wire DungeonGenerator / TownGenerator as data → scene  

Public bar unchanged: smooth terrain, stable water, no fall-through, editor that saves, one backend story.
