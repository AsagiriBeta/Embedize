# Embedize

Paper / Folia 插件：用白名单隔离**结构数据包**的自然生成。  
原版 `minecraft:` 结构永不干涉（`world` / `resource` 等都按原版生成）。

数据包结构仅在 `allowed-worlds` 中自然生成。手动 `/place` 不受影响。

## 配置

```yaml
allowed-worlds:
  - resource
structure-filter:
  mode: ALL_NON_MINECRAFT
```

## 命令

`/embedize status|worlds|reload|install|allow|deny`  
权限：`embedize.admin`

## 构建

```bash
./gradlew jar
```
