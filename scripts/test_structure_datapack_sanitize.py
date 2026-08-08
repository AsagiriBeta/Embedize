#!/usr/bin/env python3
"""Regression checks for end-biome leak + stronghold start_jigsaw keep."""
from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "bsd", ROOT / "scripts" / "build_structure_datapack.py"
)
assert SPEC and SPEC.loader
bsd = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bsd)

END = set(bsd.VANILLA_FALLBACK_TAGS["minecraft:is_end"])
OW = set(bsd.VANILLA_FALLBACK_TAGS["minecraft:is_overworld"])


def main() -> int:
    failed = 0

    def check(name: str, cond: bool, detail: str = "") -> None:
        nonlocal failed
        if cond:
            print(f"OK  {name}")
        else:
            failed += 1
            print(f"FAIL {name} {detail}")

    # #forge:is_end / #c:in_the_end must not become plains
    for tag in ("#forge:is_end", "#c:in_the_end", "#c:end_islands", "#c:is_end"):
        expanded = bsd.expand_tag_ref(tag)
        check(
            f"expand {tag}",
            bool(expanded) and all(x in END for x in expanded),
            str(expanded[:5]),
        )

    # DnT collections/end.json sanitize
    src = ROOT / ".ref/datapacks/Dungeons and Taverns v5.1.0/data/nova_structures/tags/worldgen/biome/collections/end.json"
    if src.is_file():
        raw = bsd.load_json_bytes(src.read_bytes())
        clean = bsd.sanitize_tag(
            "data/nova_structures/tags/worldgen/biome/collections/end.json", raw
        )
        vals = set(clean["values"])
        check("collections/end only end biomes", vals <= END and bool(vals & END), str(vals))
        check("collections/end no plains", "minecraft:plains" not in vals)

    # explorify end_shipwreck must not gain oceans
    ship = ROOT / ".ref/datapacks/Explorify v1.6.5.dp/data/explorify/tags/worldgen/biome/has_structure/end_shipwreck.json"
    if ship.is_file():
        raw = bsd.load_json_bytes(ship.read_bytes())
        clean = bsd.sanitize_tag(
            "data/explorify/tags/worldgen/biome/has_structure/end_shipwreck.json", raw
        )
        vals = clean["values"]
        check(
            "end_shipwreck no ocean dump",
            "minecraft:ocean" not in vals and "embedize:abyssal_deep" not in vals,
            str(vals),
        )

    # stronghold keeps start_jigsaw_name through sanitize_all (simulate one file)
    sh = {
        "type": "minecraft:jigsaw",
        "biomes": "#minecraft:has_structure/stronghold",
        "start_pool": "minecraft:stronghold/stronghold_endportal_chamber",
        "start_jigsaw_name": "minecraft:stronghold_anchor",
        "terrain_adaptation": "encapsulate",
    }
    cleaned = bsd.sanitize_structure_biomes(dict(sh), "minecraft/worldgen/structure/stronghold.json")
    check("stronghold start_jigsaw preserved in biome sanitize", "start_jigsaw_name" in cleaned)

    check(
        "classify end_castle",
        bsd.classify_env("nova_structures:end_castle", "#nova_structures:collections/end", "end_castle.json")
        == "END",
    )

    # empty fallback for end names
    check(
        "empty fallback end",
        set(bsd.empty_tag_fallback("forge:is_end")) == END,
    )
    check(
        "empty fallback ow",
        set(bsd.empty_tag_fallback("c:unknown_biome_xyz")) == OW,
    )

    print("failed=", failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
