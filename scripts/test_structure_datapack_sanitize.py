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
        "stronghold keeps has_structure tag ref",
        cleaned.get("biomes") == "#minecraft:has_structure/stronghold",
        str(cleaned.get("biomes")),
    )
    check(
        "stronghold tag includes cave biomes for Embedize 3D",
        {"minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark"}
        <= set(bsd.VANILLA_FALLBACK_TAGS["minecraft:has_structure/stronghold"]),
    )
    check(
        "stronghold_biased_to includes cave biomes",
        {"minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark"}
        <= set(bsd.VANILLA_FALLBACK_TAGS["minecraft:stronghold_biased_to"]),
    )

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

    # lukis vault terminals: minecraft:south pool → minecraft:empty (same UTF-8 length)
    check(
        "south/empty rewrite same length",
        all(len(a) == len(b) for a, b in bsd._JIGSAW_POOL_SAME_LEN_REWRITES),
    )
    import gzip
    import tempfile
    from pathlib import Path as P
    with tempfile.TemporaryDirectory() as td:
        root = P(td)
        nbt_dir = root / "data" / "demo" / "structure"
        nbt_dir.mkdir(parents=True)
        # Minimal gzip NBT-ish blob containing the bad pool id as raw UTF-8.
        payload = b"xxxx" + b"minecraft:south" + b"yyyy"
        (nbt_dir / "vault.nbt").write_bytes(gzip.compress(payload))
        n = bsd.sanitize_jigsaw_nbt_pool_strings(root)
        out = gzip.decompress((nbt_dir / "vault.nbt").read_bytes())
        check("rewrite minecraft:south in nbt", n == 1 and b"minecraft:empty" in out and b"minecraft:south" not in out)

        # Alias + breeze stub pools (only when pack already has the template nbt)
        melee_src = (
            root / "data" / "crazy_chambers" / "worldgen" / "template_pool" / "spawner" / "melee_spawner.json"
        )
        melee_src.parent.mkdir(parents=True, exist_ok=True)
        melee_src.write_text(
            json.dumps({
                "name": "crazy_chambers:spawner/melee_spawner",
                "fallback": "minecraft:empty",
                "elements": [],
            }),
            encoding="utf-8",
        )
        breeze_nbt = root / "data" / "crazy_chambers" / "structure" / "spawner" / "breeze.nbt"
        breeze_nbt.parent.mkdir(parents=True, exist_ok=True)
        breeze_nbt.write_bytes(b"\x0a\x00\x00\x00")  # empty compound root
        stubs = bsd.ensure_missing_template_pools(root)
        alias = root / "data" / "crazy_chambers" / "worldgen" / "template_pool" / "melee_spawner.json"
        breeze = (
            root / "data" / "crazy_chambers" / "worldgen" / "template_pool" / "spawner" / "breeze_spawner.json"
        )
        check("stub melee alias + breeze pool", stubs == 2 and alias.is_file() and breeze.is_file())

        # Unrelated packs must not grow crazy_chambers stubs
        other = root / "otherpack"
        (other / "data" / "other" / "structure").mkdir(parents=True)
        (other / "data" / "other" / "structure" / "x.nbt").write_bytes(b"\x0a\x00\x00\x00")
        check("no cross-pack breeze stub", bsd.ensure_missing_template_pools(other) == 0)

        # Prefer strip Owner on projectiles; keep arrow entities for author visuals
        arrow_ent = {
            "pos": (bsd._TAG_LIST, (bsd._TAG_DOUBLE, [0.5, 1.0, 0.5])),
            "blockPos": (bsd._TAG_LIST, (bsd._TAG_INT, [0, 1, 0])),
            "nbt": (bsd._TAG_COMPOUND, {
                "id": (bsd._TAG_STRING, "minecraft:arrow"),
                "Owner": (bsd._TAG_INT_ARRAY, [1, 2, 3, 4]),
                "inGround": (bsd._TAG_BYTE, 1),
            }),
        }
        frame_ent = {
            "pos": (bsd._TAG_LIST, (bsd._TAG_DOUBLE, [1.5, 1.0, 0.5])),
            "blockPos": (bsd._TAG_LIST, (bsd._TAG_INT, [1, 1, 0])),
            "nbt": (bsd._TAG_COMPOUND, {
                "id": (bsd._TAG_STRING, "minecraft:item_frame"),
            }),
        }
        villager_ent = {
            "pos": (bsd._TAG_LIST, (bsd._TAG_DOUBLE, [2.5, 1.0, 0.5])),
            "blockPos": (bsd._TAG_LIST, (bsd._TAG_INT, [2, 1, 0])),
            "nbt": (bsd._TAG_COMPOUND, {
                "id": (bsd._TAG_STRING, "minecraft:villager"),
                "Attributes": (bsd._TAG_LIST, (bsd._TAG_COMPOUND, [
                    {
                        "Name": (bsd._TAG_STRING, "minecraft:generic.movement_speed"),
                        "Base": (bsd._TAG_DOUBLE, 0.5),
                    },
                    {
                        "Name": (bsd._TAG_STRING, "forge:entity_gravity"),
                        "Base": (bsd._TAG_DOUBLE, 0.08),
                    },
                ])),
            }),
        }
        spectral_ent = {
            "pos": (bsd._TAG_LIST, (bsd._TAG_DOUBLE, [3.5, 1.0, 0.5])),
            "blockPos": (bsd._TAG_LIST, (bsd._TAG_INT, [3, 1, 0])),
            "nbt": (bsd._TAG_COMPOUND, {
                "id": (bsd._TAG_STRING, "minecraft:spectral_arrow"),
                "OwnerUUID": (bsd._TAG_INT_ARRAY, [5, 6, 7, 8]),
            }),
        }
        jockey_ent = {
            "pos": (bsd._TAG_LIST, (bsd._TAG_DOUBLE, [4.5, 1.0, 0.5])),
            "blockPos": (bsd._TAG_LIST, (bsd._TAG_INT, [4, 1, 0])),
            "nbt": (bsd._TAG_COMPOUND, {
                "id": (bsd._TAG_STRING, "minecraft:armor_stand"),
                "equipment": (bsd._TAG_COMPOUND, {
                    "head": (bsd._TAG_COMPOUND, {
                        "id": (bsd._TAG_STRING, "minecraft:stick"),
                        "count": (bsd._TAG_INT, 1),
                        "components": (bsd._TAG_COMPOUND, {
                            "minecraft:enchantments": (bsd._TAG_COMPOUND, {
                                "nova_structures:jockey/spawn_skeleton_horseman": (
                                    bsd._TAG_INT, 1
                                ),
                                "nova_structures:jockey/missing_not_shipped": (
                                    bsd._TAG_INT, 1
                                ),
                                "minecraft:binding_curse": (bsd._TAG_INT, 1),
                            }),
                        }),
                    }),
                }),
            }),
        }
        root_nbt = {
            "size": (bsd._TAG_LIST, (bsd._TAG_INT, [5, 2, 1])),
            "entities": (bsd._TAG_LIST, (bsd._TAG_COMPOUND, [
                arrow_ent, frame_ent, villager_ent, spectral_ent, jockey_ent,
            ])),
        }
        payload = bsd._NbtWriter().named_root("", root_nbt)
        cleaned, stripped_n, attached, forge_n, ench_n = (
            bsd.sanitize_structure_nbt_entities_bytes(payload)
        )
        check("strip Owner+OwnerUUID keep arrows", stripped_n == 2 and attached == 1)
        check("strip forge:entity_gravity attr", forge_n == 1)
        check("strip all custom enchants", ench_n == 2)
        _, cleaned_root = bsd._NbtReader(cleaned).named_root()
        et, items = cleaned_root["entities"][1]
        ids = []
        owners_left = 0
        forge_left = 0
        for it in items:
            eid = bsd._structure_entity_id(it)
            if eid:
                ids.append(eid)
            nbt = it.get("nbt")
            if nbt and isinstance(nbt[1], dict):
                body = nbt[1]
                owners_left += sum(1 for k in body if k in bsd._PROJECTILE_OWNER_KEYS)
                attrs = body.get("Attributes") or body.get("attributes")
                if attrs and attrs[0] == bsd._TAG_LIST:
                    for attr in attrs[1][1]:
                        if isinstance(attr, dict):
                            aid = bsd._attr_id_string(attr)
                            if aid and aid.startswith("forge:"):
                                forge_left += 1
        check(
            "kept all five entities incl arrows",
            ids == [
                "minecraft:arrow",
                "minecraft:item_frame",
                "minecraft:villager",
                "minecraft:spectral_arrow",
                "minecraft:armor_stand",
            ],
            str(ids),
        )
        check("no Owner keys remain on entities", owners_left == 0)
        check("no forge attrs remain", forge_left == 0)
        # Vanilla-only server: strip ALL custom enchants; keep minecraft:*
        jockey_body = items[4]["nbt"][1]
        ench = (
            jockey_body["equipment"][1]["head"][1]["components"][1]["minecraft:enchantments"][1]
        )
        check(
            "kept only vanilla ench on jockey",
            list(ench.keys()) == ["minecraft:binding_curse"],
            str(list(ench.keys())),
        )
        # Round-trip via gzip file on disk
        ent_dir = root / "data" / "demo" / "structure"
        (ent_dir / "arrows.nbt").write_bytes(gzip.compress(payload))
        f, r, a, fg, em = bsd.sanitize_structure_nbt_entities(root)
        check(
            "disk sanitize strips owners+forge+custom ench",
            f >= 1 and r == 2 and a >= 1 and fg == 1 and em == 2,
            f"f={f} r={r} a={a} fg={fg} em={em}",
        )
        disk_out = gzip.decompress((ent_dir / "arrows.nbt").read_bytes())
        check("disk nbt keeps arrow ids", b"minecraft:arrow" in disk_out and b"spectral_arrow" in disk_out)
        check("disk nbt keeps villager", b"minecraft:villager" in disk_out)
        check("disk nbt no forge gravity", b"forge:entity_gravity" not in disk_out)
        check("disk nbt drops all nova jockey ench", b"nova_structures:jockey" not in disk_out)
        check("disk nbt keeps vanilla binding_curse", b"minecraft:binding_curse" in disk_out)
        # Owner tag name is gone (key bytes); IntArray payload alone is not enough to re-bind
        _, disk_root = bsd._NbtReader(disk_out).named_root()
        disk_owners = 0
        for it in disk_root["entities"][1][1]:
            body = it.get("nbt", (0, {}))[1]
            if isinstance(body, dict):
                disk_owners += sum(1 for k in body if k in bsd._PROJECTILE_OWNER_KEYS)
        check("disk nbt no Owner keys", disk_owners == 0)

        # normalize_data_rel: trial_spawner yes; custom enchantment defs no
        check(
            "ingest trial_spawner json",
            bsd.normalize_data_rel(
                "data/nova_structures/trial_spawner/shrine/zombie/normal.json"
            )
            == "data/nova_structures/trial_spawner/shrine/zombie/normal.json",
        )
        check(
            "skip custom enchantment json",
            bsd.normalize_data_rel(
                "data/nova_structures/enchantment/jockey/spawn_skeleton_horseman.json"
            )
            is None,
        )
        check(
            "skip enchantment tag json",
            bsd.normalize_data_rel(
                "data/nova_structures/tags/enchantment/exclusive_set/loot_table_bugs.json"
            )
            is None,
        )

    # Hollow pack guard: structure_set without structure defs must be pruned / refused
    with tempfile.TemporaryDirectory() as td:
        root = P(td)
        ss = root / "data" / "demo" / "worldgen" / "structure_set"
        ss.mkdir(parents=True)
        (ss / "ghost.json").write_text(
            json.dumps({
                "structures": [{"structure": "demo:ghost", "weight": 1}],
                "placement": {"type": "minecraft:random_spread", "spacing": 10, "separation": 5, "salt": 1},
            }),
            encoding="utf-8",
        )
        pruned = bsd.prune_orphan_structure_sets(root)
        check("prune orphan structure_set", pruned == 1 and not (ss / "ghost.json").exists())
        # recreate hollow and assert fail-loud
        (ss / "ghost.json").write_text(
            json.dumps({
                "structures": [{"structure": "demo:ghost", "weight": 1}],
                "placement": {"type": "minecraft:random_spread", "spacing": 10, "separation": 5, "salt": 1},
            }),
            encoding="utf-8",
        )
        try:
            bsd.assert_pack_not_hollow(root, "demo_hollow")
            check("assert hollow raises", False)
        except RuntimeError:
            check("assert hollow raises", True)

    print("failed=", failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
