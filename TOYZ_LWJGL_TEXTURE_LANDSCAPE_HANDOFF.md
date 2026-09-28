# Toyz Builder — LWJGL Texture / Inventory / Landscape Patch

## What changed

### 1. Textured GLB rendering
`Model` now imports UV coordinates from Assimp and stores GLB meshes as `NORMALS_UV`.
A planar UV fallback is generated for models without a UV channel.

`Renderer.renderTexturedModel(...)` applies an AssetBank texture to authored GLB meshes.

`Window.drawPiece(...)` now uses the textured GLB path instead of the flat-color GLB path.

This keeps the existing model scaling/placement logic intact.

### 2. Inventory presentation
The builder inventory now:
- uses a larger steel-panel layout
- shows more pieces at once
- uses material textures as item icons
- uses the same texture mapping as the world renderer
- shows item count in the footer
- uses textured hotbar icons

HUD gained a small independent image shader/VBO so inventory images do not interfere with the text/rect batch.

### 3. AssetBank cleanup
Corrected several paths to match the supplied asset tree:
- ice
- crystal
- diamond
- quartz
- magma
- magnesite
- cherry bark
- cast iron
- iron bars
- house wall
- roof
- ladder
- bamboo bark
- cedar/cherry/mahogany/teak/walnut/bamboo planks

Added piece-to-material mappings for these families.

### 4. Terrain-aware water
The old renderer used one giant water plane over the entire world.

`Window.buildWaterMesh(...)` now examines terrain cells and creates water only where cells reach the configured water level. This gives rivers/lakes/coastal water an actual boundary instead of flooding the whole map.

### 5. Surface landscape features
Added `LandscapeFeatures.java`.

Deterministically generates lightweight surface decorations:
- small/medium/large boulders
- volcanic rocks
- crystal outcrops
- quartz outcrops
- biome-aware placement

Placement is based on terrain height, biome and local slope.

These are rendered with existing GLB assets and the corresponding terrain/material textures.

## Architecture

Procedural world data remains independent of LWJGL.

Terrain -> trees/features -> renderer
Builder pieces -> PieceCatalog -> Model/Texture -> renderer

No Raylib dependency is introduced.

## Testing focus

1. Open editor and press E.
2. Confirm inventory icons now use material textures instead of flat color blocks.
3. Select log/block/stone pieces and place them.
4. Confirm authored GLBs show textured surfaces.
5. Test glass/metal/wood/stone families.
6. Regenerate several seeds.
7. Confirm water follows low terrain instead of covering the whole map.
8. Confirm boulders/mineral outcrops appear in appropriate biomes.
9. Watch console for missing texture/model warnings.

## Known limitation

The GLB renderer currently applies the AssetBank's piece-family texture to the whole model rather than extracting each GLB material's embedded texture. This is deliberate for this migration stage: it makes the authored GLBs visibly textured while keeping the backend simple. Per-material GLB textures can be added later without changing the world or PieceCatalog APIs.
