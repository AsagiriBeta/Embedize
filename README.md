# Embedize

Paper / Folia 成品插件：结构数据包分组隔离 + 隐形世界边界回弹。

## 功能

1. **结构隔离** — 监听 Paper [`AsyncStructureSpawnEvent`](https://jd.papermc.io/paper/1.21.4/org/bukkit/event/world/AsyncStructureSpawnEvent.html)，按 `groups.yml` 白名单放行；原版 `minecraft:` 永不干涉。
2. **隐形边界** — 参考 WorldBorder / E-WorldBorder：不可见、越界回弹；配置按**世界名**存于 `borders.yml`，同名世界删除重建仍生效。
3. **Multiverse-Core 5** — 正式 API（见下），别名解析、世界 create/load/regen 时确认边界仍生效；回弹点可走 MV `BlockSafety`。
4. **TerraformGenerator** — **无公开插件 API**（上游仅提供 world generator）；可选安装 biome-tag 桥接数据包（映射 TFG 自定义群系进 DnT tags）。
5. **数据包** — 不下载第三方结构包；服主自行放入世界 `datapacks/`。

## Multiverse-Core 5 集成（正式）

官方文档：

- [Developer API Starter](https://mvplugins.org/core/developers/developer-api-starter/)
- [API Usage](https://mvplugins.org/core/developers/api-usage/)
- [Javadoc](https://multiverse.github.io/Multiverse-Core/javadoc/latest/)

本插件用法（`compileOnly`，运行时 softdepend）：

| API | 用途 |
|---|---|
| `MultiverseCoreApi.whenLoaded` / `get` / ServicesManager | 获取 API 根实例 |
| `WorldManager.getWorldByNameOrAlias` | 组别名 / allowlist 匹配 |
| `MultiverseWorld.getName` / `getAlias` / `getAliasOrName` | 规范世界名 |
| `BlockSafety.findSafeSpawnLocation` | 边界回弹安全落点 |
| `AsyncSafetyTeleporter` | 可选安全传送 |
| `MVWorldLoaded/Created/RegeneratedEvent` | 世界生命周期时确认边界 |

实现类 `MultiverseAccessImpl` 仅在检测到 Multiverse-Core 时反射加载，无 MV 的服不会 `NoClassDefFoundError`。

## 依赖

| 库 | 构建 | 运行时 |
|---|---|---|
| Paper API 1.21.4+ | compileOnly | 必需 |
| Multiverse-Core 5.7+ | compileOnly（`repo.onarandombox.com`） | softdepend |
| LuckPerms API 5.5 | compileOnly | softdepend |
| TerraformGenerator | — | softdepend（仅检测 + 可选桥接包） |

## 命令

```
/embedize reload|status|worlds|help
/embedize group list|create <id>|delete <id>
/embedize group <id> info|add|remove <pack>
/embedize group <id> allowlist add|remove <world>
/embedize border list
/embedize border <world> info|clear|set <radius> [x z|spawn]
/embedize border <world> set <rx> <rz> <x> <z>
/embedize border <world> shape square|round
```

别名：`/emb`、`/ez`。边界 `<world>` 会经 Multiverse 别名解析成规范世界名再写入 `borders.yml`。

## 配置

- `plugins/Embedize/config.yml` — 总开关、ungrouped 策略、TFG 桥接、MV 别名
- `plugins/Embedize/groups.yml` — packs / namespaces / allowed-worlds
- `plugins/Embedize/borders.yml` — 按世界名的隐形边界

## 落地步骤

1. 构建：`./gradlew jar` → `build/libs/Embedize-1.1.0.jar`
2. 放入 Paper 1.21+ 的 `plugins/`
3. （可选）安装 Multiverse-Core 5.7+、LuckPerms、TerraformGenerator
4. 把结构数据包 zip 放进目标世界的 `datapacks/`（例如 `resource/datapacks/`）
5. 编辑 `groups.yml`：`namespaces` + `allowed-worlds`
6. `/embedize border resource set 5000 spawn` 等设置隐形边界
7. `/embedize status` / `/embedize worlds` 确认 MV 绑定与组别矩阵

## 权限

- `embedize.admin`（含子权限）
- `embedize.group.admin` / `embedize.border.admin`
- `embedize.border.bypass`
- `embedize.command.reload` / `embedize.command.status`
