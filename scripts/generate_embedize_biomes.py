#!/usr/bin/env python3
"""Generate Embedize custom biome datapack — only genuinely distinct biomes.

Philosophy: vanilla biomes are the canvas. Do NOT 1:1-rename plains/forest/etc.
Only emit biomes that fill a role vanilla does not have (currently: abyssal_deep).
"""

from __future__ import annotations

import copy
import json
import zipfile
from pathlib import Path

ROOT = Path("src/main/resources/embedize-biomes")
BIOME_DIR = ROOT / "data" / "embedize" / "worldgen" / "biome"
TAG_DIR = ROOT / "data" / "minecraft" / "tags" / "worldgen" / "biome"


def scrub(obj):
    if isinstance(obj, list):
        out = []
        for x in obj:
            if isinstance(x, str):
                if x.startswith("minecraft:"):
                    out.append(x)
            else:
                out.append(scrub(x))
        return out
    if isinstance(obj, dict):
        return {k: scrub(v) for k, v in obj.items()}
    return obj


def load_json(z: zipfile.ZipFile, path: str):
    return scrub(json.loads(z.read(path)))


def write_tag(name: str, values: list[str]):
    path = TAG_DIR / f"{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps({"replace": False, "values": values}, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    BIOME_DIR.mkdir(parents=True, exist_ok=True)
    TAG_DIR.mkdir(parents=True, exist_ok=True)

    # Clear previous rename biomes
    for old in BIOME_DIR.glob("*.json"):
        if old.name != "abyssal_deep.json":
            old.unlink()

    zt = zipfile.ZipFile(".ref/datapacks/Terralith_1.21.11_v2.6.0.zip")
    ow_base = load_json(zt, "data/terralith/worldgen/biome/sakura_valley.json")
    d = copy.deepcopy(ow_base)
    d["temperature"] = 0.4
    d["downfall"] = 0.5
    d["has_precipitation"] = True
    attrs = d.setdefault("attributes", {})
    attrs["minecraft:visual/fog_color"] = 0x203050
    attrs["minecraft:visual/sky_color"] = 0x304878
    attrs["minecraft:visual/water_fog_color"] = 0x050533
    fx = d.setdefault("effects", {})
    fx["water_color"] = 0x050533
    fx["grass_color"] = 0x8EB971
    fx["foliage_color"] = 0x71A74D
    (BIOME_DIR / "abyssal_deep.json").write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")

    (ROOT / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "description": "Embedize distinct biomes only (abyssal_deep trench)",
                    "min_format": 94,
                    "max_format": 94,
                }
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    write_tag("is_overworld", ["embedize:abyssal_deep"])
    write_tag("is_ocean", ["embedize:abyssal_deep"])
    for empty in (
        "is_nether",
        "is_end",
        "is_forest",
        "is_jungle",
        "is_savanna",
        "is_badlands",
        "is_beach",
        "is_mountain",
        "has_structure/mineshaft",
        "has_structure/village_plains",
    ):
        write_tag(empty, [])

    catalog = [
        {
            "id": "embedize:abyssal_deep",
            "vanilla": "minecraft:deep_ocean",
            "dimension": "ow",
            "role": "far-ocean trench (unique); ordinary deep_ocean stays vanilla",
        }
    ]
    Path("src/main/resources/embedize-biome-catalog.json").write_text(
        json.dumps(catalog, indent=2) + "\n", encoding="utf-8"
    )
    print("Wrote 1 distinct biome (abyssal_deep); removed rename overlays")


if __name__ == "__main__":
    main()
