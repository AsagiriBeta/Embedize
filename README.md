# Embedize

Paper / Folia 插件：用**白名单**隔离结构数据包的自然生成。  
只在 `allowed-worlds` 里的世界生成托管结构；其它世界一律取消。不影响玩家手动 `/place`。

可选对接 [Dungeons and Taverns](https://modrinth.com/datapack/dungeons-and-taverns)，兼容 [Multiverse-Core](https://mvplugins.org/core/) / [TerraformGenerator](https://github.com/Hex27/TerraformGenerator/wiki)。

支持：**1.21.x、26.1.x、26.2**。

## 配置（核心就这一项）

```yaml
allowed-worlds:
  - resource
structure-filter:
  mode: ALL_NON_MINECRAFT   # 托管所有非原版命名空间；也可改成 NAMESPACES
```

- 只要 `resource` → 默认 `world` 不会生成这些结构  
- 只要 `world` → `resource` 不会生成  
- 两个都写 → 两边都能生成  

## 快速开始

1. 放入 Paper/Folia；建议 Multiverse-Core，探索世界可用 TerraformGenerator。  
2. 编辑 `plugins/Embedize/config.yml` 的 `allowed-worlds`。  
3. `/mv create resource normal -g TerraformGenerator`  
4. 若启用 DnT 自动安装：首次后 `/minecraft:reload` 或重启。  
5. `/embedize worlds` 查看各世界 YES/NO。  

## 命令

| 命令 | 说明 |
|------|------|
| `/embedize status` | 白名单、过滤、计数 |
| `/embedize worlds` | 每世界是否会生成托管结构 |
| `/embedize reload` | 重载配置 |
| `/embedize install` | 重装可选数据包 |
| `/embedize allow\|deny <world>` | 增删白名单 |

权限：`embedize.admin`。别名：`/emb`、`/ez`。

## 构建

```bash
./gradlew jar
```

产物：`build/libs/Embedize-1.0.0.jar`
