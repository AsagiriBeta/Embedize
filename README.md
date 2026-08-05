# Embedize

Paper / Folia：按分组隔离结构数据包自然生成。原版 `minecraft:` 永不干涉。兼容 LuckPerms。

## 命令（分组）

```
/embedize group list
/embedize group create <id> [display]
/embedize group delete <id>
/embedize group <id> info
/embedize group <id> add <pack>
/embedize group <id> remove <pack>
/embedize group <id> allow <world>
/embedize group <id> deny <world>
```

示例：

```
/embedize group create dungeons
/embedize group dungeons add dungeons-and-taverns
/embedize group dungeons allow resource
```

`pack` 优先匹配 `config.yml` 里 `datapacks.sources` 的 id（并带上其 `namespaces`）；未知 id 会当作结构命名空间处理。

## 配置

- `plugins/Embedize/config.yml` — sources 等多数据包安装
- `plugins/Embedize/groups.yml` — 分组 packs / namespaces / allowed-worlds

## LuckPerms

`embedize.admin`、`embedize.group.admin`、`embedize.group.<id>.manage|view`

## 构建

```bash
./gradlew jar
```
