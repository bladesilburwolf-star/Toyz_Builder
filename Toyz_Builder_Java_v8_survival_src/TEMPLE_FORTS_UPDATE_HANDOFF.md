# TOYZ BUILDER — TEMPLES AND FORTS UPDATE
## AI handoff / session log — 2026-09-25

**Primary task:** Temples and Forts Update  
**Contributor:** ChatGPT  
**Baseline:** Java V8 (`Toyz_Builder_Java_v8_survival_src`)  
**Related systems:** Lincoln Logs, Magnetix, bridges, procedural structures, survival spawners

### Implemented in this handoff package
- Added `StructureGenerator.java`.
- Added deterministic procedural structure families:
  - Lincoln Log forts
  - Lincoln Log temples
  - Magnetix forts
  - Magnetix temples
  - Lincoln Log bridges
  - Magnetix bridges
- Structures return ordinary `Piece.PlacedPiece` instances so Creative can edit/destroy/rebuild them.
- Generated structure results also carry `Mob.Spawner` definitions for later Survival integration.
- Uses the existing seed and `StructureSettings`, so structures remain deterministic.
- Erector Set is deliberately untouched.

### Next integration step
Merge `StructureGenerator.generate(...)` into `MapSystem.generateStructures(...)`.
Extend `MapSystem.StructureSettings` with:
- `fortChance`
- `templeChance`
- `bridgeChance`
- `magnetixChance`
- `includeSpawners`

Add a helper such as:
`MapSystem.generateStructureSpawners(terrain, cfg)`

Then pass those generated spawners into `Survival.spawners` when a generated Survival world is initialized.

### GROK HANDOFF — MESH EXPORTER
Please build a mesh exporter for the generated structures/placed-piece world so the user can:
1. Generate a Temple, Fort, or Bridge.
2. Export the assembled geometry to an editable mesh.
3. Edit the exported result in Blender or another modeling package.
4. Re-import or use the edited mesh as a GLB asset later.

Preferred exporter targets:
- OBJ for simple editable geometry.
- GLB/glTF as the production asset format.
- Preserve world transforms and material/color information where practical.
- Export selected structure only, not necessarily the entire world.
- Include an option to export collision geometry separately if practical.

### CLAUDE HANDOFF
Keep Erector Set work isolated from this update. The new generator should remain compatible with the existing `Piece.PlacedPiece` representation so later Erector pieces can be added without rewriting the structure pipeline.

### Design rule
Do not turn generated temples/forts/bridges into immutable special objects. The intended flow is:

Generate -> Explore -> Edit -> Destroy -> Rebuild -> Save -> Reload

### Suggested next test seeds
Use at least three different world seeds and verify that:
- both Log and Magnetix structures appear,
- bridges are not placed in water unless the terrain system later provides a crossing-aware placement rule,
- generated pieces can be selected/moved/deleted normally,
- survival spawners remain tied to the generated landmark positions.
