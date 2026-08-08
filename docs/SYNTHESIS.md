# Embedize synthesis

License: **GPLv3** · Target: Paper/Folia 1.21.11 · Version: **2.0.0**

## Goal

Terralith / Tectonic / Incendium / Nullscape **feel** and DnT / T&T / Trek **structure coverage**,
via clean-room Java terrain generators + a **bundled structure datapack** loaded into the
vanilla registries — **not** by reimplementing jigsaw assembly in the plugin.

**Biome policy:** vanilla `minecraft:*` biomes are the canvas. Terrain/decoration differences
are applied in Java. Custom biomes are registered only when they fill a role vanilla does not
have — currently just `embedize:abyssal_deep` (far-ocean trench). No 1:1 rename overlays.

## Directory roles

| Path | Role |
|------|------|
| `src/main/java/com/embedize/terrain/` | OW / Nether / End noise + chunk generators |
| `src/main/java/com/embedize/structure/` | Structure catalog + `/embedize place` (vanilla engine) |
| `src/main/resources/embedize-biomes/` | Distinct-biome datapack (Bootstrap `DATAPACK_DISCOVERY`) |
| `.ref/datapacks/` | Upstream packs → one datapack each under structure-packs |
| `build/embedize-structure-packs/` | Per-pack datapacks + bridge + `index.json` + `catalog.json` |
| `scripts/build_structure_datapack.py` | Per-pack copy, sanitize biome tags, emit bridge |
| `com.embedize.reset` | Resource world monthly/manual reset (`/resreset`) + PAPI `%resource_reset_*%` |

## Biomes

Plugin-owned pack `embedize-biomes`:

- `embedize:abyssal_deep` — far-ocean trench surface (deepslate / gravel / prismarine). Ordinary
  `deep_ocean` stays vanilla. Structure biome tags include this id for ocean monuments / shipwrecks.
- Regenerate: `python scripts/generate_embedize_biomes.py`

Height and biome for a column must agree: emerged land is never painted `abyssal_deep`.

### Overworld vanilla coverage (`OverworldBiomeProvider`)

All natural overworld `minecraft:*` biomes are in `getBiomes()` and can be sampled by
`getBiome(x,y,z)` (so `/locate biome` works), including:

| Layer | Biomes | How Embedize places them |
|-------|--------|---------------------------|
| Surface | plains→peaks, oceans, rivers, mushroom_fields, old_growth_*, eroded/wooded badlands, windswept_savanna, … | 2D climate (temp/humidity/continentalness/weirdness) |
| Cave | `lush_caves`, `dripstone_caves` | Below surface (roughly Y≪surface and Y≲48); humid → lush, dry/continental → dripstone |
| Deep | `deep_dark` | Sparse large-scale pockets under continental land at Y&lt;0 (ancient_city filter) |

Surface chunk painting still uses the 2D `biomeAt` path; only the Bukkit `BiomeProvider`
is 3D. Nether / End providers already list the full vanilla dim sets (5 each).

**Existing worlds:** already-generated chunks keep old biome maps. After upgrading, new
chunks get cave biomes; to refresh `resource`, run `/resreset` (or explore far enough that
new deep_dark columns generate).

## Terrain

| Dim | Inspiration | Notes |
|-----|-------------|--------|
| Overworld | Tectonic + Terralith + TFG ideas | Coastal continuum, slope limits, abyssal floor as surface, inland caves only |
| Nether | Incendium | Climate shelves, cheese mid-caves, glowstone / vines / magma |
| End | Nullscape | Center island, mid ring, far isles, chorus |

Overworld floating stone over water is treated as **column integrity** (height/biome mismatch or carver undercut), not a one-block sea-level off-by-one.

## Structure pipeline (vanilla engine)

1. `./gradlew buildStructureDatapack` — one datapack per `.ref/datapacks/` source → `build/embedize-structure-packs/`
2. Biome tags are sanitized **per pack** (no cross-pack merges) so jigsaw pools/NBT stay coherent.
   NBT templates are normalized to 1.21+ `data/<ns>/structure/*.nbt` (singular) — legacy
   `structures/` folders are rewritten at build time so `StructureTemplateManager` can load them.
   Optional hardening: projectile **Owner / OwnerUUID** may be stripped while keeping arrow
   entities (mitigates Leaves AsyncCatcher if `setOwner` hits async `getEntities`). This is
   **not** what keeps default `world` safe — pack lazy-enable + the world gate are.
3. `jar` embeds `/embedize-structure-packs/` (`fullJar` is an alias only); Bootstrap discovers each slug from `index.json` with `autoEnableOnServerStart(false)` (overhauls late, bridge last)
4. `BundledDatapackSync` enables `Embedize/struct-*` + `Embedize/biomes` when an Embedize
   world is loaded or expected (`resource-reset` / Multiverse `worlds.yml`). Enable is
   **server-global** (Paper limitation).
5. Embedize generators return `shouldGenerateStructures() == true` (+ decorations already on)
6. Minecraft's own StructureSet / `JigsawPlacement` places pieces and runs `jigsaw_replacement`
7. **World gate (dual insurance):** `StructureWorldGateListener` cancels `AsyncStructureSpawnEvent`
   (and filters `StructuresLocateEvent`) for catalog exact ids + custom-namespace structures unless
   `world.getGenerator() instanceof EmbedizeGenerator`

No plugin-side jigsaw planner, piece queue, or `final_state` scrubber.

### TFG vs Embedize (same product rule, different mechanism)

| | TerraformGenerator | Embedize |
|--|--------------------|----------|
| Content | Java `StructureRegistry` populators (+ selective vanilla `tryGenerateStructure`) | Bundled real datapacks → vanilla jigsaw |
| Where it runs | Only on TFG's injected `NMSChunkGenerator` (`createStructures` / BlockPopulator) | Vanilla pipeline + `StructurePlacementBridge` on Embedize worlds |
| Non-plugin worlds | Never see TFG structures (generator instance absent) | Packs off until Embedize expected; while on, **global** registry + spawn/locate gate |
| Locate | Override `findNearestMapStructure` on TFG generator | Filter `StructuresLocateEvent` outside Embedize worlds |

Paper/Leaves have no per-world datapack API ([Paper#7347](https://github.com/PaperMC/Paper/issues/7347),
[#10147](https://github.com/PaperMC/Paper/issues/10147) unlikely). Full TFG parity
(“structures exist only on our NMS ChunkGenerator”) would mean abandoning bundled DnT
datapacks for hand-written populators. This release gets closer via **lazy global enable**
+ **stronger gate**, not true per-world registry isolation.

## Multiverse

```text
mv create resource normal --generator Embedize
mv create resource-nether nether --generator Embedize:nether
mv create resource-end the_end --generator Embedize:end
```

No world allowlist for **terrain**: only worlds that request an Embedize generator get custom
noise. Bundled **structure natural spawn** is gated the same way (generator check), not by
world name. Vanilla structure biome filters still apply per dimension inside Embedize worlds.

## Reference study (clean-room terrain)

Local sources under `.ref/` (not shipped): Terralith / Tectonic / Geophilic / Incendium /
Nullscape / DnT+ / TerraformGenerator. Terrain patterns ported into Java; **structures ship as
real datapack content** for the vanilla engine:

- OW: Tectonic continental breakpoints + ridge/cliff jaggedness + weirdness river band
- Nether: Incendium multi-shelf + cheese/tunnel/web carve
- End: Nullscape-style six concentric rings
- Decor: Geophilic patches (podzol/mud/moss/bushes) + TFG-like thinning
- Structures: upstream DnT / T&T / Trek / overhauls via bundled datapack

## Still not terrain-datapack-identical

- Full Terralith multi-noise biome parameter space / skylands / arches
- Overworld remains 2D heightmap + continuum, not full 3D density like Iris
- Packs that hard-require missing mod biomes may still under-spawn until tags cover them
- `beard_box` / Beardifier: Embedize never runs vanilla `fillFromNoise` density
  subtraction (`shouldGenerateNoise=false`). Approximation:
  - **`structures.density-adapt` (default true)** — column-wise density falloff per
    structure *piece* BB (+ soft kernel) on the current populate chunk via
    `WorldGenLevel` before `placeInChunk`. Closer in spirit to Beardifier; not a
    continuous density router; no cross-chunk neighbour contribution.
  - **`structures.soft-beard` (default false)** — heavier per-voxel carve; only when
    density-adapt is off. Same WorldGenLevel / current-chunk rules (never
    `CraftBlock.getType` / syncLoad on features workers).
  Old chunks keep prior terrain; explore new areas after upgrade.
- Surface `surface-ignore-air` and place-only soft carve do not claim seamless vanilla parity;
  rivers / sandboxes can still look wrong under manual `/embedize place`.
- While bundled packs are enabled, the structure registry is **shared**. Non-Embedize worlds
  get spawn/locate gating (catalog ids + custom ns), not a second vanilla definition for
  overhauled `minecraft:*` ids. With no Embedize world expected, packs stay disabled and the
  shared registry can remain vanilla.

### Rebuild

```bash
./gradlew clean jar
# Install: build/libs/Embedize-<version>.jar
./gradlew test
```
