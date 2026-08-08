# Embedize

**GPLv3** · Paper / Folia **1.21.11** · 自研三维地形 + `embedize:*` 自定义群系 + **原版结构 / jigsaw 引擎**

结构内容在构建期按来源包分别打入 jar 内 `/embedize-structure-packs/`，经 Bootstrap 按 `index.json` **发现**（`autoEnableOnServerStart=false`）。
`BundledDatapackSync` 在存在 / 预期 Embedize 生成器世界时 **全局启用** `Embedize/struct-*` 与 `Embedize/biomes`（Paper 无按世界 datapack）。
自然生成 / locate 另由 `StructureWorldGateListener` 门禁（catalog 精确 id + 自定义命名空间）限制在 `Embedize*` 世界；**例外**：进度关键的 `minecraft:stronghold` 在默认 `world` 也放行（末影之眼 / locate）。
Multiverse `--generator Embedize` 走 Java 地形 + 捆绑结构；未选 Embedize 的世界保持原版地形，且不自然生成捆绑结构。

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

正式服安装 **`build/libs/Embedize-<version>.jar`**（`jar` 任务已含结构数据包；`fullJar` 仅为别名，不再产出第二个文件）。

```bash
# 将 DnT / T&T / Trek 等 zip 放入 .ref/datapacks/ 后构建
./gradlew jar

# 仅跑测试（不强制产出安装用 jar）
./gradlew test

# 拉起 Paper 测试服（使用含结构包的 jar）
./gradlew runServer
```

| 产物 | 路径 |
|------|------|
| 正式安装 | `build/libs/Embedize-<version>.jar` |

复制该 jar 到服务器 `plugins/`。缺少 `.ref/datapacks/` 时 `buildStructureDatapack` 会失败——正式分发请在有参考包的环境构建。

## 资源世界 / Multiverse / resreset

创建世界：

```text
mv create resource normal --generator Embedize
mv create resource-nether nether --generator Embedize:nether
mv create resource-end the_end --generator Embedize:end
```

- 无世界白名单：只有选了 Embedize 生成器的世界走自定义地形 **与捆绑结构自然生成**；默认 `world` 靠懒启用 + 门禁隔离。
- `/resreset` / `/resresetstatus`：按 `config.yml` 的 `resource-reset` 月更/手动重建资源世界。
- 重置路径：Multiverse **不存盘卸载** + Bukkit `WorldCreator`（`keepSpawnLoaded=false`）再登记，避免主线程卡在选出生点导致玩家被踢。
- 需要 Multiverse-Core；可选 PlaceholderAPI：`%resource_reset_next%` / `%resource_reset_countdown%` 等。

## 结构行为（自然生成 vs place）

| 场景 | 行为 |
|------|------|
| **自然生成（Embedize 世界）** | 原版 StructureSet / jigsaw；装饰前放置；村庄道路依赖 `WORLD_SURFACE_WG` heightmap |
| **自然生成（非 Embedize 世界）** | 门禁取消 catalog id（含 DnT overhaul 的 `minecraft:*`）+ 自定义 ns；**`minecraft:stronghold` 例外放行**（眼睛/locate）；未列入 catalog 的纯原版结构仍可生成 |
| **`/embedize place`** | 原版 `/place structure`；可强制加载区块；古城可按配置做软椭球掏空（非整盒） |
| **surface-ignore-air** | 地表结构：模板 AIR 不覆盖已有方块；地下/空腔结构按 step / adaptation 判定 **keep-air** |
| **古城 density-adapt** | 自然生成默认按**件** BB 做列式密度让路（Beardifier 近似）；仅当前 chunk + WorldGenLevel |
| **古城 soft-beard** | 可选加重体素掏空；仅当 `density-adapt=false` 时生效；不做 StructureStart AABB 硬掏 |
| **箭矢 Owner（可选加固）** | 构建期可剥离 Owner/OwnerUUID、保留箭实体；**进默认 world 不靠这个**，靠上方世界门禁 |

NBT 模板路径为 1.21+ 的 `data/<ns>/structure/*.nbt`（单数）；构建期会把旧式 `structures/` 改写到该路径。多包分别输出；末地 biome tag 不会误回落 overworld。

## 已知限制（相对 TFG 的诚实对照）

| | TerraformGenerator | Embedize（本版） |
|--|--------------------|------------------|
| 结构内容 | Java `StructureRegistry` + 选择性原版 | 真实 DnT 等数据包 → 原版 jigsaw |
| 隔离机制 | 挂在 TFG 的 **NMS ChunkGenerator 实例**；非 TFG 世界根本没有定义 | 懒启用全局包 + spawn/locate 门禁 |
| 默认 `world` registry | 从不被 TFG 结构污染 | **无 Embedize 世界时**可保持原版；一旦为资源世界启用包，**共享 registry**（Paper 限制） |
| locate | 覆盖 `findNearestMapStructure` | 过滤 `StructuresLocateEvent` |

- Paper issue [#7347](https://github.com/PaperMC/Paper/issues/7347) / [#10147](https://github.com/PaperMC/Paper/issues/10147)：无真正 per-world datapack；Leaves 同样是全局 `Datapack#setEnabled`。
- 包启用后，catalog 中的多数 `minecraft:*` overhaul（如古城）在默认世界会被门禁取消自然生成——**不会自动回落到另一份原版定义**（同一 id 只有一份 registry 条目）。**`minecraft:stronghold` 例外**：两边都放行，使用共享的 DnT/原版定义，保证末影之眼可用。
- Embedize 三维群系在地下多为 `lush_caves` / `dripstone_caves` / `deep_dark`；bridge 会把这些写入 `has_structure/stronghold` 与 `stronghold_biased_to`，否则 concentric rings / StructureStart 会空。
- `structures.bundled-pack-mode`：`embedize-worlds-only`（默认）| `always` | `never`。
- **旧区块不自愈**：已生成区块不会因升级自动改地形/群系/结构空隙；需新区块或 `/resreset`。
- **density-adapt / soft-beard ≠ 原版 Beardifier 1:1**：无密度路由、无邻块 Beardifier 贡献；街道可能更紧或边缘更硬。不做与 Terralith 像素级一致的承诺。

## 正式服验证步骤

1. 安装 `build/libs/Embedize-2.0.0.jar`（不要用 `-full`；本项目已无该产物）。
2. 确认 `plugins/Embedize/config.yml`：`bundled-pack-mode: embedize-worlds-only`，`density-adapt: true`，`soft-beard: false`，`debug: false`。
3. 默认 `world` 保持原版生成器；用 Multiverse 创建 `resource` / `resource_nether` / `resource_end`（见上）。
4. `/datapack list`：在资源世界预期存在时，应看到 `Embedize/struct-*` / `Embedize/biomes` 为 enabled；`/embedize packs` 显示 desired=enabled。
5. 默认 `world`：新区块不应自然刷 DnT/自定义 ns（古城等仍 gated）；**`/locate structure minecraft:stronghold` 与末影之眼应可用**（stronghold 例外放行）。未列入 catalog 的纯原版结构（如普通村庄）仍可出现。
6. Embedize 资源世界：新区块应有 jigsaw 结构（无残留 jigsaw 方块）；`/embedize status` 报 `engine=vanilla`；古城街道/广场不应被实心石完全夹死；**`/locate structure minecraft:stronghold` 与末影之眼应可用**。
7. `/resresetstatus` / 可选 PAPI 占位符冒烟。
8. 探索时确认无 watchdog / “server has not responded”（features worker 不得再走 CraftBlock.getType syncLoad）。
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
