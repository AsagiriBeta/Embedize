# Scripts

| File | Role |
|------|------|
| `build_structure_datapack.py` | 由 `buildStructureDatapack` 调用：为每个 `.ref/datapacks/` 来源输出完整数据包到 `build/embedize-structure-packs/<slug>/`（含 biome tag 清洗），另写 bridge + `index.json` + `catalog.json` |
| `generate_embedize_biomes.py` | 生成/维护 `embedize-biomes` 自定义群系 JSON |

需要本机 `python` 在 PATH 中。`jar` / `fullJar` 都会跑 `buildStructureDatapack` 并将 `/embedize-structure-packs/` 打入分发 jar（正式服用 `build/libs/Embedize-*-full.jar`）。

结构拼接由 **Minecraft 原版 jigsaw / StructureSet 引擎**完成，不再使用自研装配器。不跨包合并文件，避免 template pool 与 NBT 错配。
