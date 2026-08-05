# Embedize

Paper / Folia 插件：对**任意结构数据包**做严格的按世界隔离。  
Minecraft 会把数据包结构注册成全局；Embedize 在自然生成时取消非允许世界中的放置，从而保证例如只允许在 `resource` 维度生成时，**绝不会串到默认 `world`**。

可选对接 [Dungeons and Taverns](https://modrinth.com/datapack/dungeons-and-taverns)，并与 [Multiverse-Core](https://mvplugins.org/core/) / [TerraformGenerator](https://github.com/Hex27/TerraformGenerator/wiki) 兼容。日后可扩展更多数据包安装源。

支持：**1.21.x、26.1.x、26.2**（需 `AsyncStructureSpawnEvent`）。

## 严格隔离如何保证

1. 默认 `structure-filter.mode: ALL_NON_MINECRAFT` —— 托管所有非原版命名空间结构（不限 DnT）。
2. 默认 `mode: ALLOWLIST` + `strict-isolation: true` —— **未显式列入 `allowed-worlds` 的世界一律拒绝**。
3. `sealed-worlds` + `auto-seal-default-level` —— 主世界 / nether / end **绝对禁止**，即使误写入 allowlist 也以 sealed 为准。
4. 监听 `AsyncStructureSpawnEvent`（`HIGHEST`）并 `setCancelled(true)`，取消后该次自然结构**不会放置方块**。

示例：只允许 `resource`：

```yaml
mode: ALLOWLIST
strict-isolation: true
allowed-worlds:
  - resource
sealed-worlds:
  - world
  - world_nether
  - world_the_end
auto-seal-default-level: true
structure-filter:
  mode: ALL_NON_MINECRAFT
```

则 `nova_structures:*`、以及其他数据包命名空间的结构只会在 `resource` 生成；在 `world` 会被取消。原版 `minecraft:*` 默认不受影响。

## 快速开始

1. 放入 Paper/Folia，建议安装 Multiverse-Core；探索世界可用 TerraformGenerator。
2. 编辑 `plugins/Embedize/config.yml`（见上）。
3. `/mv create resource normal -g TerraformGenerator`
4. 若启用 DnT 自动安装：首次启动后 `/minecraft:reload` 或重启。
5. `/embedize worlds` 确认各世界为 YES/NO。

## 命令

| 命令 | 说明 |
|------|------|
| `/embedize status` | 严格模式、过滤、sealed/allow、计数 |
| `/embedize worlds` | 每世界是否会生成托管结构 |
| `/embedize reload` | 重载配置 |
| `/embedize install` | 重装可选数据包（如 DnT / TFG 桥） |
| `/embedize allow\|deny <world>` | 调整 allow 列表 |

权限：`embedize.admin`。别名：`/emb`、`/ez`。

## 构建

```bash
./gradlew jar
```

产物：`build/libs/Embedize-1.0.0.jar`

## 说明

- 隔离针对**自然区块结构生成**；玩家 `/place structure` 等手动放置不在此闸门内。
- DnT 为可选第三方 ARR，运行时从 Modrinth 下载。
- 原版结构默认 PASS；若要连原版一起隔离，设 `structure-filter.mode: ALL`。
