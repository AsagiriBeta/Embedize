# Embedize

Paper / Folia：按分组隔离结构数据包自然生成；可选隐形世界边界回弹。原版 `minecraft:` 永不干涉。

结构数据包请自行放入世界的 `datapacks/`，本插件不下载第三方包。

## 命令（分组）

```
/embedize group list|create <id>|delete <id>
/embedize group <id> info|add|remove|allowlist ...
```

## 命令（隐形边界）

不是原版蓝色边界，而是插件不可见回弹（参考 WorldBorder / E-WorldBorder）。
配置按**世界名**保存在 `plugins/Embedize/borders.yml`，同名世界删除重建后仍生效。

```
/embedize border list
/embedize border <world> info
/embedize border <world> set <radius> [x z|spawn]
/embedize border <world> set <rx> <rz> <x> <z>
/embedize border <world> shape square|round
/embedize border <world> clear
```

示例：`/embedize border resource set 5000 0 0`

## 配置

- `plugins/Embedize/config.yml` — 总开关、可选 TFG biome bridge
- `plugins/Embedize/groups.yml` — 结构分组白名单
- `plugins/Embedize/borders.yml` — 隐形边界（按世界名）

## LuckPerms

`embedize.admin`、`embedize.group.admin`、`embedize.border.admin`、`embedize.border.bypass`

## 构建

```bash
./gradlew jar
```
