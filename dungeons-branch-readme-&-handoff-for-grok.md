# ToyzBuilderWorldGen — `dungeons` Branch
## README + Handoff (for Grok's safety review)

**Repo:** `bladesilburwolf-star/ToyzBuilderWorldGen`
**Branch:** `dungeons` (created from `main` @ `a384597`, 2026-09-28)
**Main is untouched.** All work below lives only on `dungeons`.

---

## 1. What this branch does

Makes the procedural structures "official": they now have real collision, and warp pads teleport the player.

- **Structures are solid.** Every wall, floor, ceiling, pillar, and building now has an AABB in the collision system. The player collides with towns, forts, shrines, tombs, and dungeons instead of walking through them.
- **Two-sided collision.** AABBs block from either face (no one-way walls). Floors are standable; ceilings block upward movement (relevant once jump/gravity is active).
- **Doorways stay open.** Door and gate gaps have no collision; walls with gaps are built as two segments plus a lintel above the opening, so passages stay walkable.
- **Branching dungeons.** Dungeons are no longer a single box. Each dungeon is a 4–5 room branching tree on a 3×3 grid (16m cells), depth 2–3, connected by corridors, with an open entrance corridor on the south side.
- **Warps are active.** A glowing cyan pad at each dungeon entrance teleports to the deepest room (gold chest marker there); an orange return pad teleports back out. Town wells glow cyan and warp to the nearest dungeon. 2.5s cooldown prevents ping-pong. Console prints `[Warp] <label>`.

## 2. Files changed (4 commits on `dungeons`)

| Commit | File | Change |
|---|---|---|
| `68d8d31` | `world/Warp.java` | **NEW** — warp pad data (x/y/z, radius, destination, label, glow color) |
| `61e089f` | `engine/Collision.java` | Second solid list `structureSolids`; `rebuildFromStructures(parts)`; two-sided resolution across both lists; debug lines cover both lists |
| `61e089f` | `world/World.java` | `setStructures(parts, warps)` wiring; per-frame warp check with cooldown in `update()` |
| `7a1142f` | `world/StructureGenerator.java` | `Part.solid` flag (solid vs. decorative); branching dungeon rooms/corridors/entrance; warp pad + treasure placement; towns/forts/shrines/tombs solidified with door/gate gaps |

## 3. Known design decisions (why it is built this way)

- **No rotating collision.** Structure site yaw is limited to 90° steps, so a rotated part just swaps X/Z extents and stays axis-aligned. `rebuildFromStructures` does this swap when yaw ≈ 90°/270°.
- **Editor pieces don't wipe structure collision.** `rebuildFromEntities()` only clears the entity solid list; structure solids are separate. Both survive terrain regen (regen clears both, then `setStructures` is re-called).
- **Warp trigger:** XZ distance ≤ radius AND |ΔY| < 2.5m. Cooldown starts at 1.5s after wiring so the spawn-frame position doesn't instantly trigger a pad.
- **Performance (HD 6450 target):** solid-count is printed at generation (`[StructureGen] ... solid=N`). A handful of sites × ~30–60 parts each is a few hundred AABBs — the per-frame resolution loops are O(solids) which is fine at this scale, but if sites/parts grow, consider spatial hashing.
- **"Avoiding block":** interpreted as *keep passages un-blocked* (doorways/gates open). If the original Raylib "avoid block" was a moving-hazard block inside dungeons, that is **not** implemented yet — confirm with James.

## 4. ⚠️ REQUIRED — 2-line Window.java edit (NOT yet done)

`Window.java` on `dungeons` still needs the wiring call. It was **not** pushed remotely on purpose: the previous AI session's file-read-back tool corrupted `Window.java` twice by injecting newlines mid-token (this is what broke the build twice before), so Window.java was never fetched-and-repushed. Whoever does this edit must do it **locally with a real editor or git**, not via a fetch-repush round trip.

Add this line in **two places** in `src/main/java/com/toyzbuilder/engine/Window.java`:

```java
world.setStructures(structureGen.getParts(), structureGen.getWarps());
```

1. In `init()` — immediately after:
   ```java
   worldMap.setMarkers(structureGen.getSites(), terrain.size);
   ```
2. In `regenerateFromSettings()` — immediately after:
   ```java
   world.setTerrain(terrain);
   ```
   (in that method the `worldMap.setMarkers(...)` line also exists; either anchor works, but it must come **after** `world` exists and after structures are generated.)

Note: the plain `regenerate()` method (R key) does **not** regenerate structures on main — that's pre-existing behavior; if you want R to also rebuild structures + warps there, add the same call after its `structureGen.generate(terrain, cfg.seed);` (currently missing in that method).

## 5. Review checklist for Grok

1. Fetch `dungeons` branch, apply the §4 edit locally, run `gradlew build` — must compile clean.
2. `Part.solid` default: the legacy `Part(...)` constructor defaults to solid=true — verify no callers rely on the old all-visual behavior (only `StructureGenerator` constructs parts; `Editor`/piece entities use their own path).
3. `Collision.rebuildFromStructures`: verify the 90° yaw swap logic (`yaw` is 0/90/180/270 exactly — floats from `rng.nextInt(4) * 90f`, so the epsilon ranges are safe).
4. Dungeon geometry: rooms overlap the entrance corridor and corridors only through door gaps — check the player can't clip through a wall/corridor seam (walls are 0.6 thick, door width 4, player body 0.6 wide).
5. Warp loop: `World.updateWarps` — confirm cooldown arithmetic (dt subtraction, 2.5s reset) and that `setPlayerPos` + `resyncMouseIfNeeded` don't fight the menu/inventory states.
6. Confirm **main** still points at `a384597` and has none of these commits.
7. Do **not** merge to main until James tests locally (download `dungeons` zip → apply §4 → `gradlew run`).

## 6. Test steps for James (after the §4 edit)

- `gradlew run`, start a new world from the menu.
- **F3** — toggles collision-box debug wireframes; walk near a town/fort: every wall should show a wireframe box.
- Walk into a house wall in a town → you should be blocked; walk through the fort's south gate → should pass through the gap.
- Find a dungeon (map markers show types): enter through the south corridor, doors connect rooms, you cannot walk through walls or fall through floors; ceiling is solid when jumping.
- Step on the glowing **cyan pad** outside the dungeon → teleport to the deepest room; step on the **orange pad** there → teleport back to the entrance corridor.
- In a town, step into the glowing **well** → teleport to the nearest dungeon entrance pad.
- Console at startup prints e.g. `[StructureGen] sites=12 parts=411 solid=380 warps=9`.

## 7. Suggested next steps (not started)

- Moving-hazard "avoid block" in dungeon rooms (pending James's confirmation of what that meant in Raylib).
- Dungeon interiors: torches, mobs, loot chests that open.
- Town buildings with enterable interiors (currently sealed solid boxes).
- Boss room at dungeon depth with a locked door + key.

— End of handoff —