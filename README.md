# Embedize

Paper / Folia：按分组隔离结构数据包自然生成。原版 `minecraft:` 永不干涉。兼容 LuckPerms。

结构数据包请自行放入世界的 `datapacks/`，本插件不下载第三方包。

## 命令

```
/embedize group list
/embedize group create <id> [display]
/embedize group delete <id>
/embedize group <id> info
/embedize group <id> add <pack|namespace>
/embedize group <id> remove <pack|namespace>
/embedize group <id> allowlist add <world>
/embedize group <id> allowlist remove <world>
/embedize reload|status|worlds
```

示例：

```
/embedize group create dungeons
/embedize group dungeons add nova_structures
/embedize group dungeons allowlist add resource
```

`add` 也可写常见别名（如 `dungeons-and-taverns` → `nova_structures`，`towns-and-towers` → `towns_and_towers`）。

## 配置

- `plugins/Embedize/config.yml` — 总开关、可选 TFG biome bridge
- `plugins/Embedize/groups.yml` — 分组 packs / namespaces / allowed-worlds

## LuckPerms

`embedize.admin`、`embedize.group.admin`、`embedize.group.<id>.manage|view`

## 构建

```bash
./gradlew jar
```
