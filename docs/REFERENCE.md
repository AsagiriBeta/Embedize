# Upstream reference packs (local)

所有上游 zip **只放** `.ref/`，不再使用单独的 `content/` 目录。

| Directory | Git | Purpose |
|-----------|-----|---------|
| `.ref/datapacks/` | ignored | 结构参考包（DnT、T&T、Trek、Terralith tags…）— 构建扫描入口 |
| `.ref/plugins/` | ignored | 引擎研究（TerraformGenerator、BetterStructures 等） |

```bash
./gradlew buildStructureDatapack fullJar
# jar 与 fullJar 均含结构包；正式服安装 build/libs/Embedize-*-full.jar（或同内容的 Embedize-*.jar）
```

Build 从 `.ref/datapacks/` 为每个来源包输出完整数据包（不跨包合并文件），
NBT 落在 1.21+ 单数路径 `data/<ns>/structure/*.nbt`（旧 `structures/` 在构建期改写），
写入 `build/embedize-structure-packs/<slug>/`（+ `00_embedize_bridge/` + `index.json` + `catalog.json`），
再打进插件 jar 的 `/embedize-structure-packs/`，由 Bootstrap `DATAPACK_DISCOVERY` 按 load order 注册。
末地相关 biome tag 按维度清洗，不会误回落 overworld。

## Typical roles

| Kind | Examples |
|------|----------|
| Structures | DnT, T&T, Trek, ATi, Structory, Hopo, Explorify, Luki capitals, DnT overhauls |
| Terrain study | Tectonic, Terralith, Geophilic, Incendium, Amplified Nether, Nullscape（地形包默认跳过） |
| Engine study | TerraformGenerator (Apache-2.0), BetterStructures (GPL-3) |

## Runtime vs build-time

| Concern | Mechanism |
|---------|-----------|
| Custom biomes | Bootstrap `DATAPACK_DISCOVERY` → bundled `embedize-biomes` |
| Structures | Bootstrap → bundled `embedize-structure-packs/*` + `shouldGenerateStructures()` → **vanilla** jigsaw |
| Generators | `Embedize` / `Embedize:nether` / `Embedize:end` |

## Local toolchain (optional)

| Path | Notes |
|------|-------|
| `.jdk21/` | Portable JDK 21 for builds / `runServer` |
| `run/` | Paper test server from `xyz.jpenilla.run-paper` |
| `resource-reset` in `config.yml` | Monthly/manual resource world reset (`/resreset`, `/resresetstatus`) |
| Reset recreate path | MV unload(save=false) + Bukkit `WorldCreator` + MV register — avoids spawn-select stall / kicks |
| PlaceholderAPI soft-depend | `%resource_reset_next%` / `%resource_reset_countdown%` / `%resource_reset_days|hours|mins%` |
| Structure air / place | `structures.surface-ignore-air`, `place-hollow-carve`, `keep-air-ids` in `config.yml` |

## License note

Upstream packs remain copyright of their authors. This repository ships Embedize code under GPLv3
and expects operators to obtain reference zips separately into `.ref/datapacks/`.
