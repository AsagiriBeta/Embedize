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
3. `jar` / `fullJar` embed `/embedize-structure-packs/` (same bytes; `-full` classifier is the install-friendly name); Bootstrap discovers each slug from `index.json` (overhauls late, bridge last)
4. Embedize generators return `shouldGenerateStructures() == true` (+ decorations already on)
5. Minecraft's own StructureSet / `JigsawPlacement` places pieces and runs `jigsaw_replacement`

No plugin-side jigsaw planner, piece queue, or `final_state` scrubber.

## Multiverse

```text
mv create resource normal --generator Embedize
mv create resource-nether nether --generator Embedize:nether
mv create resource-end the_end --generator Embedize:end
```

No world allowlist. Vanilla structure biome filters apply per dimension.

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
- `beard_box` / Beardifier density adaptation is approximated post-noise via per-piece
  soft carve (`SoftBeardAdaptation`) — not identical to vanilla density falloff. Old
  chunks keep prior terrain; explore new areas after upgrade.
- Surface `surface-ignore-air` and place-only soft carve do not claim seamless vanilla parity;
  rivers / sandboxes can still look wrong under manual `/embedize place`.

### Rebuild

```bash
./gradlew clean fullJar
# Install: build/libs/Embedize-<version>-full.jar  (or Embedize-<version>.jar — same contents)
./gradlew test
```
