# Embedize

Paper / Folia 插件：用白名单隔离**结构数据包**的自然生成。  
**原版 `minecraft:` 结构一律不干涉**——在 `world`、自定义 `resource` 等世界都会按原版正常生成。

只隔离非原版命名空间（如 DnT 的 `nova_structures`）：仅在 `allowed-worlds` 中生成。手动 `/place` 不受影响。

## 配置

```yaml
allowed-worlds:
  - resource
structure-filter:
  mode: ALL_NON_MINECRAFT
  include-vanilla: false   # 保持 false：绝不管理原版结构
```

| 结构类型 | world | resource（在白名单） |
|---------|-------|---------------------|
| 原版村庄/要塞等 | 正常生成 | 正常生成 |
| DnT 等数据包结构 | 取消 | 允许 |

## 命令

`/embedize status|worlds|reload|install|allow|deny`  
权限：`embedize.admin`

## 构建

```bash
./gradlew jar
```
