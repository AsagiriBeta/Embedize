#!/usr/bin/env python3
"""Build one vanilla-loadable structure datapack per source pack.

Output layout:
  <out>/<slug>/pack.mcmeta
  <out>/<slug>/data/...
  <out>/00_embedize_bridge/   # convention tags + has_structure + missing block tags
  <out>/index.json            # load order (base → overhaul → bridge)
  <out>/catalog.json          # aggregated structure catalog for StructureCatalog

Biome tags are sanitized **per pack** (no cross-pack file merges) so jigsaw
template pools / NBT stay coherent within each upstream pack.
"""
from __future__ import annotations

import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
REF = REPO / ".ref" / "datapacks"
OUT = Path(sys.argv[1] if len(sys.argv) > 1 else REPO / "build" / "embedize-structure-packs")

BRIDGE_SLUG = "00_embedize_bridge"

# Known vanilla structure / climate tags Terralith overlays often wipe.
VANILLA_FALLBACK_TAGS: dict[str, list[str]] = {
    "minecraft:is_forest": [
        "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
        "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:grove",
    ],
    "minecraft:is_jungle": [
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
    ],
    "minecraft:is_taiga": [
        "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga",
        "minecraft:old_growth_spruce_taiga",
    ],
    "minecraft:is_badlands": [
        "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands",
    ],
    "minecraft:is_savanna": [
        "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
    ],
    "minecraft:is_mountain": [
        "minecraft:meadow", "minecraft:frozen_peaks", "minecraft:jagged_peaks",
        "minecraft:stony_peaks", "minecraft:snowy_slopes", "minecraft:grove",
    ],
    "minecraft:is_ocean": [
        "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
        "minecraft:warm_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
        "embedize:abyssal_deep",
    ],
    "minecraft:is_beach": ["minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore"],
    "minecraft:is_river": ["minecraft:river", "minecraft:frozen_river"],
    "minecraft:is_nether": [
        "minecraft:nether_wastes", "minecraft:soul_sand_valley", "minecraft:crimson_forest",
        "minecraft:warped_forest", "minecraft:basalt_deltas",
    ],
    "minecraft:is_end": [
        "minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands",
        "minecraft:end_barrens", "minecraft:small_end_islands",
    ],
    "minecraft:is_overworld": [
        "minecraft:plains", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
        "minecraft:snowy_taiga", "minecraft:swamp", "minecraft:mangrove_swamp",
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
        "minecraft:savanna", "minecraft:desert", "minecraft:badlands",
        "minecraft:meadow", "minecraft:cherry_grove", "minecraft:grove",
        "minecraft:windswept_hills", "minecraft:sunflower_plains",
        "minecraft:ocean", "minecraft:deep_ocean", "minecraft:beach",
        "minecraft:river", "minecraft:frozen_river", "embedize:abyssal_deep",
    ],
    "minecraft:is_hill": [
        "minecraft:windswept_hills", "minecraft:windswept_forest",
        "minecraft:windswept_gravelly_hills", "minecraft:meadow",
    ],
    "minecraft:has_structure/village_plains": [
        "minecraft:plains", "minecraft:meadow", "minecraft:sunflower_plains",
    ],
    "minecraft:has_structure/village_desert": ["minecraft:desert"],
    "minecraft:has_structure/village_savanna": [
        "minecraft:savanna", "minecraft:savanna_plateau",
    ],
    "minecraft:has_structure/village_snowy": [
        "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
    ],
    "minecraft:has_structure/village_taiga": [
        "minecraft:taiga", "minecraft:old_growth_pine_taiga",
        "minecraft:old_growth_spruce_taiga",
    ],
    "minecraft:has_structure/ancient_city": ["minecraft:deep_dark"],
    "minecraft:has_structure/end_city": [
        "minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands",
        "minecraft:end_barrens", "minecraft:small_end_islands",
    ],
    "minecraft:has_structure/stronghold": [
        "minecraft:plains", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
        "minecraft:snowy_taiga", "minecraft:swamp", "minecraft:mangrove_swamp",
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
        "minecraft:savanna", "minecraft:desert", "minecraft:badlands",
        "minecraft:meadow", "minecraft:cherry_grove", "minecraft:grove",
        "minecraft:windswept_hills", "minecraft:sunflower_plains",
    ],
    "minecraft:has_structure/trial_chambers": [
        "minecraft:plains", "minecraft:forest", "minecraft:taiga", "minecraft:desert",
        "minecraft:savanna", "minecraft:jungle", "minecraft:swamp", "minecraft:dark_forest",
        "minecraft:birch_forest", "minecraft:cherry_grove", "minecraft:meadow",
        "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark",
    ],
    "minecraft:has_structure/mineshaft": [
        "minecraft:plains", "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
        "minecraft:snowy_taiga", "minecraft:swamp", "minecraft:mangrove_swamp",
        "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
        "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:desert",
        "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands",
        "minecraft:windswept_hills", "minecraft:meadow", "minecraft:cherry_grove",
    ],
    "minecraft:has_structure/mineshaft_mesa": [
        "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands",
    ],
    "minecraft:has_structure/ocean_monument": [
        "minecraft:deep_ocean", "minecraft:deep_cold_ocean",
        "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean",
        "embedize:abyssal_deep",
    ],
    "minecraft:has_structure/shipwreck": [
        "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean", "minecraft:lukewarm_ocean",
        "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean",
        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
        "minecraft:beach", "minecraft:snowy_beach", "embedize:abyssal_deep",
    ],
}

KEYWORD_VANILLA: list[tuple[re.Pattern[str], list[str]]] = [
    (re.compile(r"forest|woodland|grove|floral|flower"), VANILLA_FALLBACK_TAGS["minecraft:is_forest"] + [
        "minecraft:flower_forest", "minecraft:meadow", "minecraft:cherry_grove",
    ]),
    (re.compile(r"birch"), [
        "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
    ]),
    (re.compile(r"jungle"), VANILLA_FALLBACK_TAGS["minecraft:is_jungle"]),
    (re.compile(r"taiga|old_growth"), VANILLA_FALLBACK_TAGS["minecraft:is_taiga"] + [
        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
    ]),
    (re.compile(r"badland|mesa"), VANILLA_FALLBACK_TAGS["minecraft:is_badlands"]),
    (re.compile(r"savanna"), VANILLA_FALLBACK_TAGS["minecraft:is_savanna"]),
    (re.compile(r"mountain|peak|hill|windswept"), VANILLA_FALLBACK_TAGS["minecraft:is_mountain"] + VANILLA_FALLBACK_TAGS["minecraft:is_hill"]),
    (re.compile(r"ocean|deep_ocean|shore|beach|shipwreck|monument|shallow_ocean"), VANILLA_FALLBACK_TAGS["minecraft:is_ocean"]),
    (re.compile(r"desert|dune"), ["minecraft:desert"]),
    (re.compile(r"swamp|mangrove|marsh"), ["minecraft:swamp", "minecraft:mangrove_swamp"]),
    (re.compile(r"nether|crimson|warped|soul|basalt"), VANILLA_FALLBACK_TAGS["minecraft:is_nether"]),
    # Match is_end / the_end / end_city / end_ship / outer_end — NOT bare "end" inside "legend".
    (re.compile(
        r"is_end|in_the_end|the_end|nullscape|chorus|end_island|end_city|end_ship|"
        r"end_castle|end_tower|end_lighthouse|outer_end|collections/end\b|/#end\b|/end/"
    ), VANILLA_FALLBACK_TAGS["minecraft:is_end"]),
    (re.compile(r"plains|meadow|village"), [
        "minecraft:plains", "minecraft:meadow", "minecraft:sunflower_plains",
    ]),
    (re.compile(r"snowy|icy|frozen|cold"), [
        "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
        "minecraft:frozen_peaks", "minecraft:snowy_slopes",
    ]),
    (re.compile(r"cave|dripstone|lush|deep_dark|mineshaft|stronghold|trial|underground"), [
        "minecraft:plains", "minecraft:forest", "minecraft:taiga", "minecraft:desert",
        "minecraft:savanna", "minecraft:jungle", "minecraft:swamp", "minecraft:dark_forest",
        "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark",
    ]),
]

# Terrain / dimension overhauls — skip (pull unbound features / biomes we do not ship).
SKIP_PACK_SUBSTR = (
    "tectonic",
    "geophilic",
    "lithosphere",
    "amplified_nether",
    "incendium",
    "nullscape",
    "stellarity",
    "terralith",
)

# External convention tags often referenced by structure packs; we emit bridges.
EXTERNAL_TAG_PREFIXES = (
    "#c:",
    "#forge:",
    "#byg:",
    "#clifftree:",
    "#terralith:",
    "#bop:",
    "#biomesoplenty:",
    "#oh_the_biomes_we've_gone:",
    "#regions_unexplored:",
)


def pack_slug(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", name).strip("_").lower()


def should_skip(name: str) -> bool:
    slug = pack_slug(name)
    return any(s in slug for s in SKIP_PACK_SUBSTR)


def is_overhaul_slug(slug: str) -> bool:
    low = slug.lower()
    return "overhaul" in low or "overhual" in low


def load_order_key(slug: str) -> tuple[int, str]:
    if slug == BRIDGE_SLUG:
        return (2, slug)
    if is_overhaul_slug(slug):
        return (1, slug)
    return (0, slug)


def iter_sources() -> list[Path]:
    if not REF.is_dir():
        return []
    grouped: dict[str, list[Path]] = {}
    for p in REF.iterdir():
        if p.name.startswith("."):
            continue
        if p.is_dir() or (p.is_file() and p.suffix.lower() == ".zip"):
            key = p.name if p.is_dir() else p.stem
            grouped.setdefault(key, []).append(p)
    out: list[Path] = []
    for key in sorted(grouped.keys(), key=lambda k: pack_slug(k)):
        matches = grouped[key]
        preferred = next((m for m in matches if m.is_dir()), matches[0])
        if should_skip(key):
            print(f"skip terrain-only: {preferred.name}")
            continue
        out.append(preferred)
    return out


def normalize_data_rel(rel: str) -> str | None:
    """Return path under data/ or None if not a structure-relevant asset."""
    rel = rel.replace("\\", "/")
    idx = rel.find("data/")
    if idx < 0:
        return None
    rel = rel[idx:]  # data/...
    lower = rel.lower()
    # Worldgen JSON first — paths contain "/structure/" and must not be treated as NBT.
    if "/worldgen/structure_set/" in lower:
        return rel if lower.endswith(".json") else None
    if "/worldgen/structure/" in lower:
        return rel if lower.endswith(".json") else None
    if "/worldgen/template_pool/" in lower or "/worldgen/processor_list/" in lower:
        return rel if lower.endswith(".json") else None
    # Structure packs often hang pillars / bases off placed features.
    if "/worldgen/placed_feature/" in lower or "/worldgen/configured_feature/" in lower:
        return rel if lower.endswith(".json") else None
    if "/tags/worldgen/biome/" in lower:
        return rel if lower.endswith(".json") else None
    # Block tags referenced by structure features (e.g. trek springs).
    if "/tags/block/" in lower or "/tags/blocks/" in lower:
        # Normalize legacy plural
        rel = re.sub(r"/tags/blocks/", "/tags/block/", rel, flags=re.IGNORECASE)
        return rel if lower.endswith(".json") else None
    # Template NBT: 1.21+ uses data/<ns>/structure/*.nbt (singular).
    # Legacy packs still ship data/<ns>/structures/ — normalize to singular so
    # StructureTemplateManager can find them (plural is ignored on 1.21+).
    if lower.endswith(".nbt") and ("/structures/" in lower or "/structure/" in lower):
        parts = rel.split("/")
        # data / <ns> / structures|structure / ...
        if len(parts) >= 4 and parts[0] == "data" and parts[2].lower() == "structures":
            parts[2] = "structure"
            rel = "/".join(parts)
        return rel
    return None


def tag_entry_id(raw) -> str | None:
    if isinstance(raw, str):
        return raw
    if isinstance(raw, dict):
        eid = raw.get("id")
        return str(eid) if eid else None
    return None


def load_json_bytes(raw: bytes):
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return None


def keyword_fallback(tag_path: str) -> list[str]:
    blob = tag_path.lower()
    hits: list[str] = []
    seen: set[str] = set()
    for pattern, biomes in KEYWORD_VANILLA:
        if pattern.search(blob):
            for b in biomes:
                if b not in seen:
                    seen.add(b)
                    hits.append(b)
    return hits


def is_end_tag_name(tag_id: str) -> bool:
    """True when a biome tag / structure id is end-dimension scoped."""
    low = tag_id.lower().replace("\\", "/")
    return bool(re.search(
        r"is_end|in_the_end|the_end|nullscape|end_island|end_city|end_ship|"
        r"end_castle|end_tower|end_lighthouse|end_shipwreck|outer_end|"
        r"collections/end(?:_|\b|/|\.|$)|/end/|#end\b|:end$",
        low,
    ))


def empty_tag_fallback(tag_id: str) -> list[str]:
    """Default biomes when a tag expands to nothing — never dump end tags into overworld."""
    if is_end_tag_name(tag_id):
        return list(VANILLA_FALLBACK_TAGS["minecraft:is_end"])
    if re.search(r"nether|crimson|warped|soul_sand|basalt_deltas", tag_id.lower()):
        return list(VANILLA_FALLBACK_TAGS["minecraft:is_nether"])
    return list(VANILLA_FALLBACK_TAGS["minecraft:is_overworld"])


def expand_tag_ref(eid: str) -> list[str]:
    """Turn external convention tags into concrete vanilla biomes."""
    low = eid.lower().strip()
    if not low.startswith("#"):
        if low.startswith("minecraft:") or low.startswith("embedize:"):
            return [eid]
        return []
    body = low[1:]
    if body.startswith("c:") or body.startswith("forge:") or body.startswith("byg:") or body.startswith("clifftree:"):
        hit = keyword_fallback(body)
        # Unknown #c:/#forge: refs used to fall back to is_overworld — that leaked
        # #forge:is_end / #c:in_the_end into plains/ocean and spawned end cities in resource.
        return hit if hit else empty_tag_fallback(body)
    if body.startswith("minecraft:"):
        fb = VANILLA_FALLBACK_TAGS.get(body)
        if fb:
            return list(fb)
        hit = keyword_fallback(body)
        return hit if hit else [eid]
    # Pack-local tags (#nova_structures:…): keep as tag refs (also sanitized).
    return [eid]


def keep_tag_entry(eid: str) -> bool:
    """Only keep vanilla/embedize biomes and pack-local tags we ship."""
    low = eid.lower().strip()
    if not low:
        return False
    if low.startswith("#"):
        if any(low.startswith(p) for p in ("#c:", "#forge:", "#byg:", "#clifftree:", "#terralith:", "#bop:")):
            return False  # expanded elsewhere
        return True
    return low.startswith("minecraft:") or low.startswith("embedize:")


def sanitize_tag(tag_rel: str, data: dict) -> dict:
    """Ensure vanilla biomes survive overlays; expand #c/#forge refs to concrete ids."""
    values = data.get("values")
    if not isinstance(values, list):
        values = []
    parts = tag_rel.replace("\\", "/").split("/")
    try:
        ns = parts[1]
        biome_idx = parts.index("biome")
        path = "/".join(parts[biome_idx + 1 :])
        if path.endswith(".json"):
            path = path[:-5]
        tag_id = f"{ns}:{path}"
    except (ValueError, IndexError):
        tag_id = tag_rel

    merged: list[str] = []
    seen: set[str] = set()

    def add_all(items: list[str]) -> None:
        for item in items:
            low = item.lower()
            if low not in seen:
                seen.add(low)
                merged.append(item)

    for v in values:
        eid = tag_entry_id(v)
        if not eid:
            continue
        add_all(expand_tag_ref(eid))

    fb = VANILLA_FALLBACK_TAGS.get(tag_id.lower())
    if fb:
        add_all(fb)
    has_concrete = any(not i.startswith("#") for i in merged)
    if not has_concrete:
        add_all(keyword_fallback(tag_id))
    # Overworld ocean helpers — never apply to end_* tags (end_shipwreck ≠ shipwreck).
    low_tag = tag_id.lower()
    if not is_end_tag_name(low_tag) and (
            "ocean" in low_tag or "shipwreck" in low_tag or "monument" in low_tag
    ):
        add_all(["embedize:abyssal_deep"])
    # Drop any leftover non-vanilla concrete biomes / external tags.
    merged = [x for x in merged if keep_tag_entry(x)]
    if not merged:
        merged = empty_tag_fallback(tag_id)
    # End-scoped tags must not keep accidental overworld biomes from bad #c/#forge expands.
    if is_end_tag_name(tag_id):
        end_set = set(VANILLA_FALLBACK_TAGS["minecraft:is_end"])
        only_end = [x for x in merged if x.startswith("#") or x in end_set]
        merged = only_end if only_end else list(VANILLA_FALLBACK_TAGS["minecraft:is_end"])

    out = dict(data)
    out["replace"] = False
    out["values"] = merged
    return out


def sanitize_structure_biomes(obj: dict, structure_path: str = "") -> dict:
    """Rewrite structure biomes filters to vanilla-safe tags/ids."""
    biomes = obj.get("biomes")
    if biomes is None:
        return obj
    hint = f"{structure_path} {obj.get('biomes', '')}"
    default_tag = (
        "#minecraft:is_end" if is_end_tag_name(hint) else "#minecraft:is_overworld"
    )
    if isinstance(biomes, str):
        expanded = expand_tag_ref(biomes)
        if len(expanded) == 1 and expanded[0].startswith("#"):
            obj = dict(obj)
            obj["biomes"] = expanded[0]
            return obj
        if expanded and all(not x.startswith("#") for x in expanded):
            obj = dict(obj)
            # End structures that somehow expanded to OW biomes → force is_end.
            if is_end_tag_name(hint):
                end_set = set(VANILLA_FALLBACK_TAGS["minecraft:is_end"])
                if not any(x in end_set for x in expanded):
                    obj["biomes"] = "#minecraft:is_end"
                    return obj
            obj["biomes"] = expanded
            return obj
        if not expanded:
            obj = dict(obj)
            obj["biomes"] = default_tag
        return obj
    if isinstance(biomes, list):
        kept: list[str] = []
        for x in biomes:
            kept.extend(expand_tag_ref(str(x)))
        kept = [x for x in kept if keep_tag_entry(x)]
        if not kept:
            kept = [default_tag]
        elif is_end_tag_name(hint):
            end_set = set(VANILLA_FALLBACK_TAGS["minecraft:is_end"])
            filtered = [x for x in kept if x.startswith("#") or x in end_set]
            kept = filtered if filtered else ["#minecraft:is_end"]
        obj = dict(obj)
        obj["biomes"] = kept
    return obj


def write_convention_bridges(out_root: Path) -> int:
    """Emit #c:* / #forge:* biome tags so packs that still reference them resolve."""
    bridges: dict[str, list[str]] = {
        "c:forest": VANILLA_FALLBACK_TAGS["minecraft:is_forest"],
        "c:floral": ["minecraft:flower_forest", "minecraft:meadow", "minecraft:cherry_grove"],
        "c:jungle": VANILLA_FALLBACK_TAGS["minecraft:is_jungle"],
        "c:in_jungle": VANILLA_FALLBACK_TAGS["minecraft:is_jungle"],
        "c:taiga": VANILLA_FALLBACK_TAGS["minecraft:is_taiga"],
        "c:savanna": VANILLA_FALLBACK_TAGS["minecraft:is_savanna"],
        "c:badlands": VANILLA_FALLBACK_TAGS["minecraft:is_badlands"],
        "c:in_mesa": VANILLA_FALLBACK_TAGS["minecraft:is_badlands"],
        "c:desert": ["minecraft:desert"],
        "c:swamp": ["minecraft:swamp", "minecraft:mangrove_swamp"],
        "c:beach": VANILLA_FALLBACK_TAGS["minecraft:is_beach"],
        "c:plains": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
        "c:snowy": [
            "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes",
            "minecraft:frozen_peaks", "minecraft:snowy_slopes",
        ],
        "c:snowy_plains": ["minecraft:snowy_plains", "minecraft:ice_spikes"],
        "c:mountain": VANILLA_FALLBACK_TAGS["minecraft:is_mountain"],
        "c:mountain_peak": ["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks"],
        "c:mountain_slope": ["minecraft:snowy_slopes", "minecraft:grove", "minecraft:meadow"],
        "c:extreme_hills": VANILLA_FALLBACK_TAGS["minecraft:is_hill"],
        "c:mushroom": ["minecraft:mushroom_fields"],
        "c:dead": ["minecraft:dark_forest"],
        "c:deep_ocean": [
            "minecraft:deep_ocean", "minecraft:deep_cold_ocean",
            "minecraft:deep_lukewarm_ocean", "minecraft:deep_frozen_ocean",
            "embedize:abyssal_deep",
        ],
        "c:is_ocean": VANILLA_FALLBACK_TAGS["minecraft:is_ocean"],
        "c:stony_shores": ["minecraft:stony_shore"],
        "c:is_dark_forest": ["minecraft:dark_forest"],
        "c:in_overworld": VANILLA_FALLBACK_TAGS["minecraft:is_overworld"],
        "c:in_the_end": VANILLA_FALLBACK_TAGS["minecraft:is_end"],
        "c:end_islands": VANILLA_FALLBACK_TAGS["minecraft:is_end"],
        "c:is_end": VANILLA_FALLBACK_TAGS["minecraft:is_end"],
        "c:climate_cold": [
            "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:frozen_river",
            "minecraft:ice_spikes", "minecraft:frozen_peaks",
        ],
        "c:tree_deciduous": VANILLA_FALLBACK_TAGS["minecraft:is_forest"],
        "c:ense": ["minecraft:plains", "minecraft:forest"],
        "c:underground": [
            "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark",
        ],
        "c:caves": [
            "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark",
        ],
        "forge:is_forest": VANILLA_FALLBACK_TAGS["minecraft:is_forest"],
        "forge:is_jungle": VANILLA_FALLBACK_TAGS["minecraft:is_jungle"],
        "forge:is_savanna": VANILLA_FALLBACK_TAGS["minecraft:is_savanna"],
        "forge:is_desert": ["minecraft:desert"],
        "forge:is_swamp": ["minecraft:swamp", "minecraft:mangrove_swamp"],
        "forge:is_beach": VANILLA_FALLBACK_TAGS["minecraft:is_beach"],
        "forge:is_badlands": VANILLA_FALLBACK_TAGS["minecraft:is_badlands"],
        "forge:is_mesa": VANILLA_FALLBACK_TAGS["minecraft:is_badlands"],
        "forge:is_peak": ["minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks"],
        "forge:is_hills": VANILLA_FALLBACK_TAGS["minecraft:is_hill"],
        "forge:is_mushroom": ["minecraft:mushroom_fields"],
        "forge:is_icy": ["minecraft:ice_spikes", "minecraft:frozen_peaks", "minecraft:snowy_plains"],
        "forge:is_spooky": ["minecraft:dark_forest"],
        "forge:is_dead": ["minecraft:dark_forest"],
        "forge:is_overworld": VANILLA_FALLBACK_TAGS["minecraft:is_overworld"],
        "forge:is_end": VANILLA_FALLBACK_TAGS["minecraft:is_end"],
        "forge:is_nether": VANILLA_FALLBACK_TAGS["minecraft:is_nether"],
        "forge:is_underground": [
            "minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark",
        ],
        "minecraft:is_plains": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow"],
    }
    n = 0
    for tag_id, biomes in bridges.items():
        ns, path = tag_id.split(":", 1)
        dest = out_root / "data" / ns / "tags" / "worldgen" / "biome" / f"{path}.json"
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(
            json.dumps({"replace": False, "values": biomes}, indent=2) + "\n",
            encoding="utf-8",
        )
        n += 1
    return n


def write_vanilla_structure_tags(out_root: Path) -> int:
    """Emit sanitized minecraft has_structure / climate tags (bridge last-wins)."""
    n = 0
    for tag_id, biomes in VANILLA_FALLBACK_TAGS.items():
        ns, path = tag_id.split(":", 1)
        dest = out_root / "data" / ns / "tags" / "worldgen" / "biome" / f"{path}.json"
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(
            json.dumps({"replace": False, "values": biomes}, indent=2) + "\n",
            encoding="utf-8",
        )
        n += 1
    return n


def ensure_missing_block_tags(out_root: Path) -> int:
    """Ship block tags that features reference but upstream packs forgot."""
    tags = {
        "trek:spring_valid_blocks": [
            "minecraft:stone", "minecraft:deepslate", "minecraft:granite",
            "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
            "minecraft:calcite", "minecraft:dripstone_block",
            "minecraft:smooth_basalt", "minecraft:basalt",
        ],
    }
    n = 0
    for tag_id, values in tags.items():
        ns, path = tag_id.split(":", 1)
        dest = out_root / "data" / ns / "tags" / "block" / f"{path}.json"
        if dest.is_file():
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(
            json.dumps({"replace": False, "values": values}, indent=2) + "\n",
            encoding="utf-8",
        )
        n += 1
    return n


def ensure_noop_feature(out_root: Path) -> None:
    cfg = out_root / "data" / "embedize" / "worldgen" / "configured_feature" / "no_op.json"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    cfg.write_text(
        json.dumps({"type": "minecraft:no_op", "config": {}}, indent=2) + "\n",
        encoding="utf-8",
    )


def stub_missing_placed_features(out_root: Path) -> int:
    """Create no-op placed_feature / configured_feature stubs for missing refs."""
    ensure_noop_feature(out_root)
    data = out_root / "data"
    if not data.is_dir():
        return 0
    existing: set[str] = set()
    for p in data.rglob("*.json"):
        rel = p.relative_to(data).as_posix().replace("\\", "/")
        if "/worldgen/placed_feature/" not in rel.lower():
            continue
        parts = rel.split("/")
        try:
            wi = parts.index("worldgen")
        except ValueError:
            continue
        ns = parts[0]
        path = "/".join(parts[wi + 2 :])
        if path.lower().endswith(".json"):
            path = path[:-5]
        existing.add(f"{ns}:{path}".lower())

    id_re = re.compile(r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"')
    mentioned: set[str] = set()
    for p in data.rglob("*.json"):
        try:
            text = p.read_text(encoding="utf-8")
        except OSError:
            continue
        low = text.lower()
        path_low = str(p).replace("\\", "/").lower()
        interesting = (
            "feature" in low
            or "processor" in low
            or "/worldgen/structure/" in path_low
            or "/processor_list/" in path_low
            or "/template_pool/" in path_low
            or "/placed_feature/" in path_low
        )
        if not interesting:
            continue
        for m in id_re.findall(text):
            mid = m.lower()
            if mid.startswith("minecraft:") and not any(
                k in mid for k in ("_base", "donjon", "mansion", "witch_hut", "fortress")
            ):
                continue
            if any(k in mid for k in (
                "_base", "pillar", "feature_pool", "mineshaft", "monster_room",
                "pale_moss", "chorus_plant", "frost", "shrine", "fungus", "vegetation",
                "gold_pile", "quartz", "spike", "hanging", "ruin", "donjon",
                "illager_mansion", "witch_hut", "nether_fortress", "camp_base",
                "spring/lava", "spring/water",
            )):
                mentioned.add(mid)

    stub = {"feature": "embedize:no_op", "placement": []}
    cfg_stub = {"type": "minecraft:no_op", "config": {}}
    n = 0
    for fid in sorted(mentioned):
        if ":" not in fid:
            continue
        ns, path = fid.split(":", 1)
        cfg_dest = data / ns / "worldgen" / "configured_feature" / f"{path}.json"
        if not cfg_dest.is_file() and any(
            k in fid for k in ("spring/", "pillar", "_base", "monster_room", "feature_pool")
        ):
            cfg_dest.parent.mkdir(parents=True, exist_ok=True)
            cfg_dest.write_text(json.dumps(cfg_stub, indent=2) + "\n", encoding="utf-8")
        if fid in existing:
            continue
        dest = data / ns / "worldgen" / "placed_feature" / f"{path}.json"
        if dest.is_file():
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(json.dumps(stub, indent=2) + "\n", encoding="utf-8")
        n += 1
        existing.add(fid)
    return n


def sanitize_all_structure_json(out_root: Path) -> int:
    n = 0
    data = out_root / "data"
    if not data.is_dir():
        return 0
    for p in data.rglob("*.json"):
        rel = p.as_posix().lower()
        if "/tags/" in rel or "/worldgen/structure/" not in rel or "/structure_set/" in rel:
            continue
        parts = Path(p).relative_to(data).as_posix().replace("\\", "/").split("/")
        try:
            wg = parts.index("worldgen")
        except ValueError:
            continue
        if wg < 1 or parts[wg - 1] == "tags":
            continue
        parsed = load_json_bytes(p.read_bytes())
        if not isinstance(parsed, dict):
            continue
        changed = False
        if "biomes" in parsed:
            before = json.dumps(parsed.get("biomes"), sort_keys=True)
            cleaned = sanitize_structure_biomes(parsed, rel)
            after = json.dumps(cleaned.get("biomes"), sort_keys=True)
            if before != after:
                parsed = cleaned
                changed = True
        # Keep start_jigsaw_name — packs are ingested whole (no cross-pack merge), so
        # anchors match their own pools (DnT stronghold needs minecraft:stronghold_anchor).
        if changed:
            p.write_text(json.dumps(parsed, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            n += 1
    return n


def collect_template_pool_ids(pack_root: Path) -> set[str]:
    """Return ids like minecraft:village/plains/town_centers present in this pack."""
    ids: set[str] = set()
    data = pack_root / "data"
    if not data.is_dir():
        return ids
    for p in data.rglob("*.json"):
        rel = p.relative_to(data).as_posix().replace("\\", "/")
        low = rel.lower()
        if "/worldgen/template_pool/" not in low:
            continue
        parts = rel.split("/")
        try:
            wg = parts.index("worldgen")
        except ValueError:
            continue
        if wg + 1 >= len(parts) or parts[wg + 1] != "template_pool":
            continue
        ns = parts[0]
        path = "/".join(parts[wg + 2 :])
        if path.lower().endswith(".json"):
            path = path[:-5]
        ids.add(f"{ns}:{path}".lower())
    return ids


def strip_broken_vanilla_structure_overrides(pack_root: Path, known_pools: set[str]) -> int:
    """
    Drop dangerous minecraft:* structure overrides.

    1) Always strip village_*.json overrides — packs like Lukis Grand Capitals rewrite
       minecraft:village_plains → revampedvillages:start. Even when that pool JSON exists,
       natural generation /locate ends up with ghost starts (1 piece, no blocks), while
       vanilla ``place jigsaw minecraft:village/plains/town_centers`` still works.
    2) Also drop any other minecraft structure whose start_pool namespace is missing.
    """
    data = pack_root / "data" / "minecraft" / "worldgen" / "structure"
    if not data.is_dir():
        return 0
    removed = 0
    village_re = re.compile(r"^village_(plains|desert|savanna|snowy|taiga)\.json$", re.I)
    for p in sorted(data.glob("*.json")):
        if village_re.match(p.name):
            print(f"  strip village override {p.name} (restore vanilla)")
            p.unlink(missing_ok=True)
            removed += 1
            continue
        parsed = load_json_bytes(p.read_bytes())
        if not isinstance(parsed, dict):
            continue
        pool = parsed.get("start_pool")
        if not isinstance(pool, str) or not pool:
            continue
        if pool.lower().startswith("minecraft:"):
            continue
        if pool.lower() in known_pools:
            continue
        print(f"  strip broken vanilla override {p.name} (missing start_pool {pool})")
        p.unlink(missing_ok=True)
        removed += 1
    return removed


def sanitize_pack_biome_tags(out_root: Path) -> int:
    n = 0
    data = out_root / "data"
    if not data.is_dir():
        return 0
    for p in data.rglob("*.json"):
        rel = p.relative_to(out_root).as_posix()
        if "/tags/worldgen/biome/" not in rel.replace("\\", "/").lower():
            continue
        parsed = load_json_bytes(p.read_bytes())
        if not isinstance(parsed, dict):
            continue
        clean = sanitize_tag(rel, parsed)
        p.write_text(json.dumps(clean, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        n += 1
    return n


def write_pack_mcmeta(out_root: Path, description: str) -> None:
    (out_root / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "description": description,
                    "min_format": 94,
                    "max_format": 94,
                }
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def classify_env(structure_id: str, biomes_field: str, path: str) -> str:
    blob = f"{structure_id} {biomes_field} {path}".lower()
    if is_end_tag_name(blob) or any(x in blob for x in (
        "nullscape", "the_end", "end_highlands", "end_midlands", "end_barrens",
        "minecraft:is_end", "#minecraft:is_end", "/worldgen/structure/end/",
        "end_city", "end_castle", "end_ship", "end_tower", "end_lighthouse",
        "end_shipwreck", "outer_end", "collections/end",
    )):
        # Exclude nether-only lookalikes that mention "end" indirectly.
        if "incendium" not in blob and "warped_ender" not in blob:
            return "END"
    if any(x in blob for x in (
        "incendium", "nether", "basalt_deltas", "crimson_forest", "warped_forest", "soul_sand",
    )):
        return "NETHER"
    return "OVERWORLD"


def collect_structures(pack_root: Path, source_name: str) -> tuple[list[dict], int, int]:
    structures: list[dict] = []
    data = pack_root / "data"
    if not data.is_dir():
        return [], 0, 0
    nbt_count = sum(1 for _ in pack_root.rglob("*.nbt"))
    json_count = sum(1 for _ in data.rglob("*.json"))
    for sp in sorted(data.rglob("*.json")):
        rel = sp.relative_to(data).as_posix()
        lower = rel.lower()
        if "/tags/" in lower:
            continue
        if "/worldgen/structure/" not in lower or "/worldgen/structure_set/" in lower:
            continue
        parts = rel.split("/")
        try:
            wg = parts.index("worldgen")
        except ValueError:
            continue
        if wg + 1 >= len(parts) or parts[wg + 1] != "structure":
            continue
        # Require …/worldgen/structure/<file>.json — not tags/worldgen/structure/*.json
        if wg < 1 or parts[wg - 1] == "tags":
            continue
        ns = parts[0]
        path = "/".join(parts[wg + 2 :])
        if not path.endswith(".json"):
            continue
        path = path[:-5]
        sid = f"{ns}:{path}"
        parsed = load_json_bytes(sp.read_bytes()) or {}
        biomes = parsed.get("biomes", "")
        if isinstance(biomes, list):
            biomes_field = ",".join(str(x) for x in biomes)
        else:
            biomes_field = str(biomes)
        structures.append({
            "id": sid,
            "type": str(parsed.get("type", "")),
            "biomes": biomes_field,
            "env": classify_env(sid, biomes_field, rel),
            "pack": source_name,
        })
    return structures, nbt_count, json_count


def ingest_source_into(pack_root: Path, source: Path) -> tuple[int, int]:
    """Copy structure-relevant assets from one source into pack_root. Returns (nbt, json)."""
    nbt_count = 0
    json_count = 0

    def ingest_file(rel: str, raw: bytes) -> None:
        nonlocal nbt_count, json_count
        norm = normalize_data_rel(rel)
        if norm is None:
            return
        dest = pack_root / norm
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(raw)
        if norm.lower().endswith(".nbt"):
            nbt_count += 1
        else:
            json_count += 1

    if source.is_dir():
        for fp in source.rglob("*"):
            if not fp.is_file():
                continue
            rel = fp.relative_to(source).as_posix()
            try:
                ingest_file(rel, fp.read_bytes())
            except OSError as ex:
                print(f"  skip {rel}: {ex}")
    else:
        with zipfile.ZipFile(source, "r") as zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                try:
                    ingest_file(info.filename, zf.read(info))
                except Exception as ex:
                    print(f"  skip {info.filename}: {ex}")
    return nbt_count, json_count


def build_bridge_pack(out_root: Path) -> Path:
    bridge = out_root / BRIDGE_SLUG
    if bridge.exists():
        shutil.rmtree(bridge)
    bridge.mkdir(parents=True)
    bridge_n = write_convention_bridges(bridge)
    tag_n = write_vanilla_structure_tags(bridge)
    block_tag_n = ensure_missing_block_tags(bridge)
    ensure_noop_feature(bridge)
    write_pack_mcmeta(bridge, "Embedize structure bridge (#c/#forge + has_structure tags)")
    print(f"bridge: convention={bridge_n} vanilla_tags={tag_n} block_tags={block_tag_n}")
    return bridge


def main() -> int:
    sources = iter_sources()
    if not sources:
        print(f"No packs under {REF}", file=sys.stderr)
        return 1

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    built: list[dict] = []
    all_structures: list[dict] = []
    total_nbt = 0
    total_json = 0
    total_tags = 0
    seen_slugs: set[str] = set()

    for source in sources:
        slug = pack_slug(source.name if source.is_dir() else source.stem)
        if slug in seen_slugs:
            slug = f"{slug}_{len(seen_slugs)}"
        seen_slugs.add(slug)

        pack_root = OUT / slug
        pack_root.mkdir(parents=True)
        print(f"pack {source.name} -> {slug}")
        nbt_n, json_n = ingest_source_into(pack_root, source)
        tag_n = sanitize_pack_biome_tags(pack_root)
        struct_n = sanitize_all_structure_json(pack_root)
        pools = collect_template_pool_ids(pack_root)
        stripped = strip_broken_vanilla_structure_overrides(pack_root, pools)
        stub_n = stub_missing_placed_features(pack_root)
        write_pack_mcmeta(pack_root, f"Embedize structure pack: {source.name}")

        structures, disk_nbt, disk_json = collect_structures(pack_root, source.name)
        all_structures.extend(structures)
        total_nbt += disk_nbt
        total_json += disk_json
        total_tags += tag_n
        built.append({
            "slug": slug,
            "source": source.name,
            "nbt": disk_nbt,
            "json": disk_json,
            "tags": tag_n,
            "structures": len(structures),
            "structureBiomesRewritten": struct_n,
            "brokenVanillaOverridesStripped": stripped,
            "placedFeatureStubs": stub_n,
            "overhaul": is_overhaul_slug(slug),
        })
        print(
            f"  nbt={nbt_n} json={json_n} tags={tag_n} "
            f"structure_biomes={struct_n} stripped_overrides={stripped} "
            f"stubs={stub_n} structures={len(structures)}"
        )

    build_bridge_pack(OUT)
    load_order = sorted((b["slug"] for b in built), key=load_order_key)
    load_order.append(BRIDGE_SLUG)

    index = {
        "loadOrder": load_order,
        "packs": built,
        "bridge": BRIDGE_SLUG,
    }
    (OUT / "index.json").write_text(
        json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    # Deduplicate structure ids keeping first (base before overhaul in catalog order).
    by_id: dict[str, dict] = {}
    for entry in sorted(all_structures, key=lambda e: load_order_key(pack_slug(e.get("pack", "")))):
        key = entry["id"].lower()
        if key not in by_id:
            by_id[key] = entry
    structures_out = list(by_id.values())

    catalog = {
        "packs": [b["source"] for b in built],
        "slugs": load_order,
        "nbtCount": total_nbt,
        "jsonCount": total_json,
        "tagCount": total_tags,
        "structures": structures_out,
    }
    (OUT / "catalog.json").write_text(
        json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    if total_nbt <= 0:
        print("No .nbt structures found", file=sys.stderr)
        return 1

    print(
        f"OK embedize-structure-packs: packs={len(built)}+bridge "
        f"nbt={total_nbt} json={total_json} tags={total_tags} "
        f"structures={len(structures_out)} -> {OUT}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
