# Embedize

Paper / Folia：按**分组**隔离结构数据包的自然生成。  
每组有独立的命名空间列表与世界白名单。原版 `minecraft:` 永不干涉。

兼容 LuckPerms（标准 Bukkit 权限桥接 + LP API 探测）。

## 配置文件

| 文件 | 作用 |
|------|------|
| `plugins/Embedize/config.yml` | 总开关、多数据包 sources、LuckPerms/MV 相关 |
| `plugins/Embedize/groups.yml` | 结构分组（namespaces + allowed-worlds） |

### groups.yml 示例

```yaml
groups:
  dungeons:
    display-name: Dungeons and Taverns
    namespaces:
      - nova_structures
    allowed-worlds:
      - resource
  towns:
    namespaces:
      - my_towns_pack
    allowed-worlds:
      - world
      - resource
```

则 DnT 只在 `resource` 生成；`my_towns_pack` 可在 `world` 与 `resource` 生成；原版两处都正常。

## 命令

```
/embedize group create <id> [display]
/embedize group delete <id>
/embedize group list
/embedize group info <id>
/embedize group ns add|remove <group> <namespace>
/embedize group world allow|deny <group> <world>
/embedize status|worlds|reload|install
```

## LuckPerms 权限

| 节点 | 说明 |
|------|------|
| `embedize.admin` | 全部管理 |
| `embedize.group.admin` | 管理所有分组 |
| `embedize.group.<id>.manage` | 管理指定分组 |
| `embedize.group.<id>.view` | 查看指定分组 |
| `embedize.command.reload/status/install` | 对应命令 |

LP 示例：`/lp group builder permission set embedize.group.dungeons.manage true`

（LP 文档：[Developer API](https://luckperms.net/wiki/Developer-API) — 在线玩家用 Bukkit `hasPermission` 即可，LP 会注入。）

## 构建

```bash
./gradlew jar
```
