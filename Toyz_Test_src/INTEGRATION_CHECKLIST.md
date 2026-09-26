# Integration checklist

1. Add these fields to `MapSystem.StructureSettings`:
```java
public float fortChance = 0.28f;
public float templeChance = 0.28f;
public float bridgeChance = 0.18f;
public float magnetixChance = 0.35f;
public boolean includeSpawners = true;
```

2. Replace the body of `MapSystem.generateStructures(...)` with:
```java
StructureGenerator.Result result = StructureGenerator.generate(terrain, cfg);
return result.pieces;
```

3. Add:
```java
public static List<Mob.Spawner> generateStructureSpawners(
        Terrain.ForestTerrain terrain, StructureSettings cfg) {
    if (cfg == null || !cfg.includeSpawners) return new ArrayList<>();
    return StructureGenerator.generate(terrain, cfg).spawners;
}
```

4. For Survival initialization, use the same terrain seed/settings and append the returned spawners to `Survival.spawners`.

5. Consider adding a map metadata section later so a saved map can persist generated spawner positions instead of regenerating them.

6. Grok should implement the mesh exporter as a separate utility. Do not make the exporter a dependency of runtime world generation.
