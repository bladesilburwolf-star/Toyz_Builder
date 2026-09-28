# HANDOFF — Options menu + HD 6450 render pass

Written by: Claude (session of 2026-09-27). Status: **written, not build-tested** (user builds and posts logs).
Stack: Java + LWJGL 3 (GLFW, OpenGL 3.3 core, STB, Assimp) + JOML. No new dependencies were added.

## Target hardware (design constraint for everything)

Radeon HD 6450 (Caicos): ~160 stream processors, 8 TMUs, **4 ROPs**, 64-bit DDR3.
It is **fill-rate and memory-bandwidth bound**, not vertex bound. So:

- Per-pixel cost is what matters: alpha blending, overdraw, texture taps, anisotropic filtering, full-screen quads, MSAA, shadow maps, reflections.
- CPU/driver overhead also matters (weak CPU-side budget is shared with Java): avoid per-draw `glGetUniformLocation`, per-glyph draw calls, redundant state changes.
- Terrain triangle count (131k tris at Medium) is *not* the problem.

**Rule for all AIs:** any feature that costs per-pixel work must be gated by a `GameSettings` field and default OFF.
Transparency, shadows and reflections are OFF by default and **no preset ever enables them**.

## What changed

| File | Status | Summary |
|---|---|---|
| `engine/GameSettings.java` | NEW | Singleton settings, saved to `options.txt` (working dir). Presets POTATO/LOW/BALANCED/HIGH/CUSTOM. `version()` bumps on every change. |
| `engine/OptionsMenu.java` | NEW | Paged options UI (Performance / Quality / Controls) with per-row hint text. Owned by `MainMenu`. |
| `engine/MainMenu.java` | EDIT | `Screen.OPTIONS` now delegates draw/keys/clicks to `OptionsMenu`. Old 4-line stub removed. |
| `engine/Renderer.java` | REWRITE | Settings-aware. Frustum + distance culling, fog, opaque water by default, fast terrain blend, cached uniforms, CPU normal matrix, state caching. **API changed** (see below). |
| `engine/ShaderProgram.java` | EDIT | Cached uniform locations + `set1i/1f/3f/4f/Mat3/Mat4`. |
| `engine/Hud.java` | EDIT | All rects/glyphs are now **batched into one draw call** (was one draw + buffer upload per lit glyph pixel). Glyph rows merge horizontal runs. `image()` flushes first to keep draw order. Vertex stride 16 to 12. |
| `engine/Texture.java` | EDIT | `setFilter(mode, aniso)` (nearest / bilinear / trilinear, optional anisotropic). |
| `engine/AssetBank.java` | EDIT | `applyFiltering(mode, aniso)` over every loaded texture. |
| `engine/Window.java` | EDIT | Loads settings, applies vsync / mouse sens / texture filter live, software FPS limiter, calls new Renderer API, HUD text gated by `hudInfo`, piece culling. Prints `[GL] <renderer> | GL <version>` at startup. |

## Settings reference (defaults = BALANCED preset)

| Option | Default | Notes |
|---|---|---|
| VSync | ON | Live via `glfwSwapInterval`. |
| Max FPS | 60 | Software limiter; skipped when vsync already paces at/below cap. Menus are capped at 60. |
| Render Distance | 160 | 48/80/112/160/224/320/480/MAX(900). Sets far plane, fog range, and cull distance for trees/rocks/pieces. Terrain is one mesh so it is only clipped by the far plane. |
| Fog | ON | Linear, colour = current sky clear colour. Cheap. Underwater it shortens to ~48. |
| Trees | FAST | OFF / FAST (canopy-only beyond 40% range, drawn to 80% of render distance) / FANCY (full). |
| Tree Density | FULL | Draws 1 of every 1/2/4 trees. |
| Boulders and Crystals | ON | Drawn to 60% of render distance. |
| Model Backface Culling | OFF | Applies to GLB + tree + piece meshes. Default OFF because winding of the GLBs is unverified; ON is cheaper. |
| Transparency | **OFF** | Master alpha-blend switch: water (opaque when off), underwater tint quad, placement-ghost alpha. Future glass/ice/leaves must respect it. |
| Shadows | **OFF, locked** | Not implemented. Loader forces false. |
| Reflections | **OFF, locked** | Not implemented. Loader forces false. |
| Terrain Blend | FAST | FAST skips terrain texture layers with weight <= 0.01 (`textureGrad`, so mips stay correct). FANCY = original 4 taps. |
| Texture Filter | BILINEAR | NEAREST / BILINEAR (mip nearest) / TRILINEAR (previous behaviour). |
| Anisotropic | OFF (1x) | 1/2/4/8x if `GL_EXT_texture_filter_anisotropic` is present. |
| FOV | 70 | 50 to 110, step 5. |
| HUD Info | FULL | OFF / FPS ONLY / FULL. |
| Mouse Sensitivity | 100% | 100% = old 0.08 deg/px. |

## Renderer API (changed — update any new callers)

```java
renderer.beginFrame(cam, aspect, GameSettings.get(), fogR, fogG, fogB, underwater); // once per frame, before any render*
renderer.renderTerrain(mesh, assets);
renderer.renderTrees(field, meshes, assets);
renderer.renderLandscapeFeatures(features, assets);
renderer.renderTextured(mesh, x,y,z, sx,sy,sz, yawDeg, tex, r,g,b,a);
renderer.renderTexturedModel(model, x,y,z, sx,sy,sz, yawDeg, tex, r,g,b,a);
renderer.renderMesh(mesh, x,y,z, sx,sy,sz, r,g,b,a);   // flat colour
renderer.renderWater(mesh, assets, time);               // draw last
renderer.visible(x,y,z, radius, maxDist);               // frustum + distance test, use before expensive draws
```
`camera` and `aspect` are no longer passed per call.

## How to add a setting

1. Field + default in `GameSettings` (and `resetDefaults`/presets if relevant).
2. Read/write it in `GameSettings.apply()` and `save()`.
3. Add a `Row` in the right list in `OptionsMenu` (call `touch(true)` if it should flip the preset to CUSTOM; hint lines are uppercase, about 48 chars max).
4. If it has a live side effect, apply it in `Window.applySettings()`. Otherwise just read it where it's used.

## Rules of the road

- Never call `glGetUniformLocation` directly; use `ShaderProgram.set*`.
- Never draw HUD text one pixel at a time; use `Hud.text/rect` (batched). If you bind another shader mid-HUD, call `flush()` first (see `Hud.image`).
- Renderer caches program/texture/cull state per frame. If you bind a program, texture or toggle cull outside `Renderer`, do it after the frame's 3D pass, or the cache goes stale.
- Fog colour must equal whatever the scene clears/sky-draws with. If a skybox is added, feed its horizon colour to `beginFrame`.
- Hud glyph font is uppercase-only 5x7 and has no accented characters.

## Known gaps / suggested next items (ranked by expected win on HD 6450)

1. **Render scale** (render the 3D scene into an FBO at 50 to 100% and blit up). Biggest single fill-rate lever. Needs FBO + framebuffer-size handling.
2. Window is hard-coded 1280x720 (Window, Hud coordinates, menu hit-boxes). Resolution / fullscreen option needs that decoupled first.
3. MSAA must be requested before window creation, so it would be a restart-required option. Default OFF.
4. Terrain is a single mesh; chunking would allow frustum culling of terrain. Low priority (vertex load is fine).
5. Trees are 2 draw calls each; instancing would cut driver overhead if tree counts grow.
6. No in-game pause menu: ESC currently returns to the title screen, so Options is only reachable from the title.
7. Shadows / reflections: rows exist but are locked. When implemented, unlock the rows, stop forcing false in `GameSettings.load()`, and keep default OFF.

## Not verified (please check on first build)

- I could only syntax-check with `javac` (no LWJGL/JOML jars in my sandbox). All remaining errors were missing library classes, so **library method signatures are unverified**. Likeliest spots if the build complains: `FrustumIntersection.set/testSphere`, `Matrix4f.normal(Matrix3f)`, `GL11.glGetFloat(int)`, `EXTTextureFilterAnisotropic`.
- Visual checks: terrain FAST vs FANCY blend should look identical; water should be solid; fog should hide the render-distance edge.
- Turn Model Backface Culling ON and look for vanished/inside-out pieces or trees. If any, leave the default OFF and note which model.
- Please post: the `[GL] ...` startup line, and FPS at the same spot for POTATO / BALANCED / HIGH.
