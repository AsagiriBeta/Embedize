# Embedize

**GPLv3** · Paper / Folia **1.21.11** · 自研三维地形 + `embedize:*` 自定义群系 + **原版结构 / jigsaw 引擎**

结构内容在构建期按来源包分别打入 jar 内 `/embedize-structure-packs/`，经 Bootstrap 按 `index.json` 注册进原版 Registry；
Embedize 生成器开启 `shouldGenerateStructures()`，由 Minecraft 自己的 StructureSet / `JigsawPlacement` 拼接。
Multiverse `--generator Embedize` 走 Java 地形 + 原版结构；未选 Embedize 的世界保持完全原版。

| 维度 | Generator ID | 灵感方向 |
|------|----------------|----------|
| 主世界 | `Embedize` | 大陆连续坡、海岸、深海地表群系、洞穴（含 deep_dark / lush / dripstone） |
| 下界 | `Embedize:nether` | Incendium 风气候层 + cheese 洞穴 |
| 末地 | `Embedize:end` | Nullscape 风格中环 / 外岛 |

## 目录结构

```text
Embedize/
├── src/                    # 插件源码与内嵌 biome 数据包
├── scripts/                # 构建结构数据包 / 生成群系
├── docs/                   # 设计与参考说明
├── .ref/                   # 本地参考包（datapacks/ + plugins/，gitignore）
├── build/                  # Gradle 输出（含 embedize-structure-packs）
├── run/                    # run-paper 测试服（gitignore）
└── .jdk21/                 # 可选便携 JDK（gitignore）
```

## 构建与安装

正式服必须使用**含结构数据包的完整 jar**（`jar` / `fullJar` 产物内容相同；`fullJar` 带 `-full` 分类名，便于识别）。

```bash
# 将 DnT / T&T / Trek 等 zip 放入 .ref/datapacks/ 后构建
./gradlew fullJar
# 等价：./gradlew jar   （同样会跑 buildStructureDatapack 并打入结构包）

# 仅跑测试（不强制产出安装用 jar）
./gradlew test

# 拉起 Paper 测试服（使用含结构包的 jar）
./gradlew runServer
```

| 产物 | 路径 |
|------|------|
| 推荐安装 | `build/libs/Embedize-<version>-full.jar` |
| 同等完整 | `build/libs/Embedize-<version>.jar` |

复制上述任一完整 jar 到服务器 `plugins/`。缺少 `.ref/datapacks/` 时 `buildStructureDatapack` 会失败——正式分发请在有参考包的环境构建。

## 资源世界 / Multiverse / resreset

创建世界：

```text
mv create resource normal --generator Embedize
mv create resource-nether nether --generator Embedize:nether
mv create resource-end the_end --generator Embedize:end
```

- 无世界白名单：只有选了 Embedize 生成器的世界走自定义地形；默认 `world` 等保持原版。
- `/resreset` / `/resresetstatus`：按 `config.yml` 的 `resource-reset` 月更/手动重建资源世界。
- 重置路径：Multiverse **不存盘卸载** + Bukkit `WorldCreator`（`keepSpawnLoaded=false`）再登记，避免主线程卡在选出生点导致玩家被踢。
- 需要 Multiverse-Core；可选 PlaceholderAPI：`%resource_reset_next%` / `%resource_reset_countdown%` 等。

## 结构行为（自然生成 vs place）

| 场景 | 行为 |
|------|------|
| **自然生成** | 原版 StructureSet / jigsaw；装饰前放置；村庄道路依赖 `WORLD_SURFACE_WG` heightmap |
| **`/embedize place`** | 原版 `/place structure`；可强制加载区块；古城可按配置做软椭球掏空（非整盒） |
| **surface-ignore-air** | 地表结构：模板 AIR 不覆盖已有方块；地下/空腔结构按 step / adaptation 判定 **keep-air** |
| **古城 soft beard** | 自然生成按**件** bounding box 软让路，不做 StructureStart AABB 硬掏 |

NBT 模板路径为 1.21+ 的 `data/<ns>/structure/*.nbt`（单数）；构建期会把旧式 `structures/` 改写到该路径。多包分别输出；末地 biome tag 不会误回落 overworld。

## 已知限制

- **旧区块不自愈**：已生成区块不会因升级自动改地形/群系/结构空隙；需新区块或 `/resreset`。
- **沙盘 / place 仍可填河**：手动 place 与部分地形交互不等于原版生成路径。
- **soft beard ≠ 原版密度 1:1**：街道/广场更通透，但不是 Beardifier 密度场的完美复刻。
- **不做「完美无缝」承诺**：与 Terralith 等数据包地形不是像素级一致；覆盖与密度会有偏差。

## 命令

`/embedize reload|status|worlds|packs|gens|border|place|help`

资源世界：`/resreset` · `/resresetstatus` · `/resresetunlock`

## 文档

| 文档 | 内容 |
|------|------|
| [docs/SYNTHESIS.md](docs/SYNTHESIS.md) | 架构、群系意图、地形与结构管线 |
| [docs/REFERENCE.md](docs/REFERENCE.md) | 参考包放置、构建提取、本地目录约定 |
| [docs/TESTING.md](docs/TESTING.md) | 单元测试 / 世界隔离验收门禁 |
| [scripts/README.md](scripts/README.md) | 构建 / 运维脚本说明 |

## License

GPLv3 — see [LICENSE](LICENSE).
