# Embedize verification gates

## L1 — JVM unit tests

```powershell
.\gradlew.bat test
```

Pass when all tests green, including:

- `WorldIsolationContractTest` — ChunkGenerator path, no vanilla noise delegation; structures enabled
- `TerrainGeneratorFactoryTest` — `shouldGenerateNoise() == false`, `shouldGenerateStructures() == true`
- existing terrain / biome tests

## L2 — structure datapack health

```powershell
.\gradlew.bat buildStructureDatapack
```

Pass when `build/embedize-structure-packs/` contains:

- `index.json` + `catalog.json`
- `00_embedize_bridge/pack.mcmeta`
- per-source `<slug>/pack.mcmeta` + `data/**/structure/**/*.nbt` with a non-zero total count (1.21+ singular path)
- biome tags sanitized per pack; bridge supplies `#c:`/`#forge:` and `has_structure/*`

## L3 — world isolation (manual / integration)

On a Paper/Leaves 1.21.11 server with Multiverse-Core 5.7.x and a **complete** jar
(`Embedize-*-full.jar` or same-content `Embedize-*.jar` from `./gradlew fullJar` / `jar`):

1. Leave the default `world` on vanilla generation.
2. `mv create resource normal --generator Embedize`
3. `mv create resource_nether nether --generator Embedize:nether`
4. `mv create resource_end the_end --generator Embedize:end`

Pass when:

- `/embedize worlds` shows vanilla worlds as `gen=vanilla` and resource worlds as Embedize generators
- vanilla world F3 biome keys stay `minecraft:*` and are not forced onto Embedize terrain
- resource worlds show Embedize terrain; `/embedize status` reports structure catalog + `engine=vanilla`
- new chunks contain intact jigsaw structures (no leftover jigsaw blocks) for datapack overhauls
- surface structures respect surface-ignore-air; underground cavities keep air as classified
- `/resresetstatus` shows next monthly reset; `/resreset` recreates `resource` without long main-thread stall / mass kicks
- with PlaceholderAPI installed: `/papi parse me %resource_reset_next%` and `%resource_reset_countdown%` resolve

## L4 — performance smoke

Pregen ~512 chunks in an Embedize overworld world and a vanilla world on the same seed machine:

- Embedize TPS stays ≥ 18 under fill/pregen
- Structure generation should track closer to a datapack-only world (no plugin piece queue)
