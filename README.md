# Embedize

Paper / Folia 插件：按世界隔离结构数据包自然生成，并提供按世界名持久化的隐形边界。

要求：Paper（或 Folia）**1.21+**，Java **21**。

## 功能

- **结构隔离**：拦截 `AsyncStructureSpawnEvent`，按分组白名单控制数据包结构生成；不修改原版 `minecraft:` 结构。
- **隐形边界**：插件侧回弹（非原版世界边界），配置按世界名保存；同名世界重建后仍生效。
- **Multiverse-Core 5**（可选）：正式 API 接入，支持世界别名解析与安全落点。
- **TerraformGenerator**（可选）：无公开插件 API；可自动安装 biome-tag 桥接数据包。
- 不提供第三方数据包下载；结构包由服主自行放入世界 `datapacks/`。

## 安装

1. 构建：`./gradlew jar`，得到 `build/libs/Embedize-1.1.0.jar`
2. 将 jar 放入服务器 `plugins/`
3. （可选）安装 Multiverse-Core 5.7+、LuckPerms、TerraformGenerator
4. 将结构数据包放入目标世界的 `datapacks/`
5. 编辑 `plugins/Embedize/groups.yml`，配置命名空间与允许世界
6. 按需设置边界，例如：`/embedize border resource set 5000 spawn`

## 命令

别名：`/emb`、`/ez`

```
/embedize reload | status | worlds | help

/embedize group list
/embedize group create <id> [display]
/embedize group delete <id>
/embedize group <id> info
/embedize group <id> add|remove <pack>
/embedize group <id> allowlist add|remove <world>

/embedize border list
/embedize border <world> info|clear
/embedize border <world> set <radius> [x z|spawn]
/embedize border <world> set <rx> <rz> <x> <z>
/embedize border <world> shape square|round
```

## 配置

| 文件 | 说明 |
|------|------|
| `config.yml` | 总开关、未分组策略、TFG 桥接、Multiverse 别名 |
| `groups.yml` | 结构分组：`packs` / `namespaces` / `allowed-worlds` |
| `borders.yml` | 按世界名的隐形边界 |

## 权限

| 权限 | 说明 |
|------|------|
| `embedize.admin` | 全部管理权限 |
| `embedize.group.admin` | 管理结构分组 |
| `embedize.border.admin` | 管理隐形边界 |
| `embedize.border.bypass` | 忽略隐形边界 |
| `embedize.command.reload` | 重载配置 |
| `embedize.command.status` | 查看状态 |

## 构建依赖

| 依赖 | 范围 |
|------|------|
| Paper API 1.21.4+ | 必需 |
| Multiverse-Core 5.7+ | softdepend |
| LuckPerms API | softdepend |
| TerraformGenerator | softdepend |

## 许可

见 [LICENSE](LICENSE)。
