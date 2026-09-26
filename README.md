Toyz Builder
Procedural Survival Builder — Main Project Direction

Toyz Builder is evolving from a terrain/building prototype into a large procedural open-world survival builder.

The project takes inspiration from the large-scale procedural philosophy of Daggerfall Unity, the flexible map-generation concepts of Minetest, and survival sandbox games, while remaining its own game and visual system.

The goal is not to turn Toyz Builder into a voxel or Minecraft clone.

The world uses continuous 3D terrain, GLB assets, sprites, procedural structures, generated interiors, caves, ravines, dimensions, and the existing physical building-piece systems.

Core Design
The World

The world is a large deterministic procedural environment generated from a world seed.

Current and planned features include:

V2–V7-inspired map-generation presets
Flat, Normal, and Amplified world types
Advanced world seeds
Multiple climate regions and biomes
Mountains, foothills, valleys, mesas, badlands, deserts, forests, jungles, swamps, snow regions, beaches, oceans, coral regions, volcanic regions, and specialized environments
Terrain depth
Underground layers
Caves
Large ravines
Rivers, lakes, oceans, streams, and wetlands
Snow and ice
Sky islands
Nether/dimensional terrain
Procedural structures
Settlements and villages
Forts and temples
Ruins and landmarks
Generated dungeons
Generated indoor areas
Warp zones and portals

The terrain is continuous polygonal geometry, not a cube-per-cell voxel renderer.

Daggerfall-Style World Philosophy

The survival game should increasingly emphasize:

Explore → Discover → Gather → Build → Survive → Establish → Expand

The world should feel much larger than the immediate playable area.

Future world streaming/chunking should allow large procedural regions to be generated and loaded as needed rather than requiring the entire world to exist in memory at once.

Regions may contain:

wilderness
roads
settlements
villages
forts
temples
ruins
caves
ravines
dungeons
special landmarks
portals
resource areas
biome-specific structures

The procedural world is the environment in which the Toyz Builder construction system operates.

Building System

The existing builder systems remain a major part of Toyz Builder.

The world should not replace the construction mechanics with voxel placement.

Lincoln Logs

Continue expanding:

horizontal logs
vertical logs
corners
notched pieces
planks
roofs
roof peaks
windows
doors
signs
flags/fabric
multiple wood variants
Magnetix

Continue supporting:

horizontal rods
vertical rods
balls
polygon pieces
rainbow/color variants
steel
iron
titanium
light balls
magnetic physics
rotation
friction
vortex/atom-style experimental mechanics
Erector Set

Erector Set work remains compatible with the builder but should stay modular.

Its primary role is:

redstone-like devices
switches
traps
mechanisms
pulleys
engines
lights
circuit devices
physics decorations
interactive machinery

Erector Set functionality should not require the terrain system to understand individual construction pieces.

Structures, Dungeons & Warps

Toyz Builder already has a warp/zone architecture.

This should become the foundation for procedural locations.

A generated structure can contain a portal or entrance leading to:

indoor maps
caves
dungeons
temples
forts
special regions
Nether
sky regions
forest regions
desert regions
coral regions
industrial regions

Locations can be generated deterministically from:

World Seed + Region Seed + Structure Seed + Location Salt

This allows the same world to reproduce the same locations after saving and reloading.

Structure Philosophy

Structures should use existing authored GLB pieces wherever possible.

Procedural generation determines:

location
footprint
scale
rotation
layout
materials
variants
decoration

GLB assets determine the actual visual geometry.

Underground World

The underground is a major development stage.

The existing terrain-depth and cave systems should be expanded into a genuine underground environment.

Target features:

deep caves
branching tunnels
large caverns
enormous ravines
underground lakes
underground rivers
waterfalls
lava areas
exposed rock
ore deposits
crystals
abandoned structures
underground ruins
dungeon entrances
rare underground landmarks

Underground generation should use the world seed and deterministic depth/cave fields.

It should remain compatible with the continuous terrain philosophy.

Resources & Materials

New world blocks/materials do not always require new textures.

Existing 512×512 textures and GLB assets can be reused with material tinting and shader parameters while dedicated assets are developed.

Potential material families include:

stone
deep stone
dirt
clay
sand
mud
grass
snow
ice
copper
iron
steel
titanium
cast iron
gold
diamond
crystal
quartz
redstone-like materials
obsidian
magma
volcanic rock
glass
water

Material identity should remain separate from visual texture selection so new resources can be added without redesigning the renderer.

Obsidian & Nether

Rare overworld obsidian structures can act as dimensional landmarks.

Possible variants include:

obsidian obelisks
ruined obelisks
multi-obelisk gates
underground obsidian shrines
large dimensional gateways

Touching or entering an active structure can activate its ZonePortal and transfer the player to the Nether.

The Nether should use:

World Seed + Nether Dimension Salt

so that it remains deterministic while being clearly different from the Overworld.

Nether generation can include:

Nether wastes
crimson regions
basalt regions
magma
lava
Nether structures
caves
dimensional landmarks
Sprites & Characters

The existing sprite and GLB collections should be used together.

2D sprites can represent:

creatures
monsters
NPCs
villagers
merchants
guards
travelers
dungeon inhabitants
wildlife
bosses
environmental entities

The physical world remains fully 3D.

This combination is intentional:

3D procedural world + 3D GLB objects + 2D sprite characters

Survival

Survival is now part of the core world rather than a separate demonstration mode.

The survival layer should eventually include:

resource gathering
crafting
tools
health
enemies
bosses
environmental hazards
food/resources
exploration
dungeons
structures
dimensional travel
player-built bases

Combat and survival systems should remain decoupled from terrain generation wherever possible.

Performance & Streaming

The long-term world should support:

procedural region generation
streamed terrain meshes
GLB asset caching
distance-based detail
terrain LOD
structure LOD
sprite distance handling
unloaded distant regions
deterministic regeneration

The existing streamMeshes and mesh-spacing concepts should be retained and expanded.

Technical Architecture

The preferred architecture is:

World Seed
    |
    v
WorldConfig
    |
    +-- Mapgen Preset
    +-- Climate
    +-- Biome
    +-- Landforms
    +-- Water
    +-- Terrain Depth
    +-- Caves
    +-- Ravines
    +-- Underground
    +-- Resources
    +-- Structures
    +-- Regions
    +-- Portals
    |
    v
Terrain / World Meshes
    |
    +-- GLB Assets
    +-- Materials
    +-- Sprites
    +-- Structures
    +-- Survival
    +-- Player Construction

Keep these systems modular.

Avoid putting every new feature directly into Terrain.java.

Design Rule
Procedural systems decide WHAT and WHERE.
GLB/assets decide HOW it LOOKS.
Physics decides HOW it BEHAVES.
Survival decides WHY the player cares.

This separation should make it possible to continue expanding the world without repeatedly rewriting the builder.

Current Priorities
1. Underground Expansion
Complete deep cave generation
Expand ravines
Add caverns
Add underground water
Add lava
Add ore distribution
Add underground landmarks
Add dungeon entrances
2. Procedural World Regions
Region streaming
Larger world sizes
Roads
Settlements
Villages
Regional landmarks
Region-specific structures
3. Dungeons & Interiors

Use the existing warp system to create generated:

dungeons
temples
forts
caves
indoor structures
special challenge areas
4. Dimensional Travel
Obsidian structures
Nether portals
Nether generation
Return portals
Dimension-specific resources
5. Survival Integration

Continue expanding:

crafting
tools
resources
enemies
bosses
environmental hazards
exploration rewards
6. Builder Expansion

Keep improving:

Lincoln Logs
Magnetix
Erector Set mechanisms
structures
physics
construction tools
inventory/search

The building system remains the defining Toyz Builder feature.

Compatibility Rules

Do not:

convert the project into a voxel renderer
replace the existing builder-piece system
tightly couple Erector Set mechanics to terrain generation
remove working survival systems
replace authored GLBs unnecessarily
discard deterministic seed behavior
make structures dependent on a single fixed map

Prefer:

continuous geometry
deterministic procedural generation
authored GLB assets
reusable materials
modular systems
streamed regions
generated interiors
reusable warp/portal infrastructure
Project Identity

Toyz Builder is a procedural survival world combined with a physical construction toybox.

It borrows useful ideas from large procedural RPG worlds, Minetest-style map generation, survival sandbox games, and classic construction toys, but the combination is intended to remain unique.

The defining combination is:

Huge procedural world + survival + exploration + sprites + GLB environments + physical building systems + generated dungeons + player-created structures.

Development Notes

Multiple AI contributors may work on the project.

When making substantial changes:

Add/update a handoff or development log.
Document major architectural changes.
Preserve deterministic generation.
Keep systems modular.
Record new assets and material mappings.
Verify compilation after major Java changes.
Do not silently remove existing functionality.
Current implementation contributors
Grok — Java implementation, terrain/world systems, assets, survival integration, builder systems
ChatGPT — world-generation architecture, terrain/structure concepts, procedural systems, documentation and handoffs
Claude — C++ track and Erector Set/mechanical-system development
Next Major Milestone
Daggerfall-Style Procedural Survival World

The immediate objective is to move from a large terrain demonstration into a persistent-feeling procedural world containing:

Wilderness → Regions → Settlements → Structures → Dungeons → Underground → Dimensions

while preserving the existing Toyz Builder construction mechanics.

The world gets bigger.

The builder stays Toyz Builder.
