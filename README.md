# Embedize

Paper / Folia 插件：把数据包结构的**全局生成**隔离到你指定的 Multiverse 世界（维度）里。

内置对接 [Dungeons and Taverns](https://modrinth.com/datapack/dungeons-and-taverns)（`nova_structures`），并与 [Multiverse-Core](https://mvplugins.org/core/) / [TerraformGenerator](https://github.com/Hex27/TerraformGenerator/wiki) 兼容。

支持 Minecraft / Paper：**1.21.x、26.1.x、26.2** 等（以 Paper API `AsyncStructureSpawnEvent` 可用为前提）。

## 原理

1. 从 Modrinth **自动下载** DnT 数据包（ARR，不在仓库内二次分发），安装到主世界 `datapacks/`。
2. 安装本插件自带的 **TFG 生物群系桥接包**，把 `terraformgenerator:*` 生物群系挂进 DnT 的 biome collection tags。
3. 监听 `AsyncStructureSpawnEvent`：对 `nova_structures`（可配置）结构，仅在配置的世界中允许生成，其它世界一律取消。

这样主世界可以保持干净，DnT 只出现在例如 `/mv create dungeons_world normal -g TerraformGenerator` 创建的探索维度里。

## 快速开始

1. 安装 Paper / Folia，放入本插件，建议同时安装 Multiverse-Core；探索世界可用 TerraformGenerator。
2. 编辑 `plugins/Embedize/config.yml`：

```yaml
mode: ALLOWLIST
allowed-worlds:
  - dungeons_world
managed-namespaces:
  - nova_structures
```

3. 创建世界示例：

```text
/mv create dungeons_world normal -g TerraformGenerator
```

4. 启动后插件会自动下载并安装 DnT；控制台提示后执行 `/minecraft:reload` 或重启。
5. 用 `/embedize status`、`/embedize worlds` 检查隔离状态。

也可手动把 DnT zip 放到 `plugins/Embedize/datapacks/`，插件会优先使用本地文件。

## 命令

| 命令 | 说明 |
|------|------|
| `/embedize status` | 模式、命名空间、取消/放行计数、MV/TFG、数据包路径 |
| `/embedize worlds` | 各已加载世界是否会生成托管结构 |
| `/embedize reload` | 重载配置 |
| `/embedize install` | 强制重新下载/安装 DnT 与 TFG 桥接包 |
| `/embedize allow <world>` | 将世界加入“可生成”侧 |
| `/embedize deny <world>` | 将世界移出“可生成”侧 |

权限：`embedize.admin`（默认 OP）。别名：`/emb`、`/ez`。

## 构建

```bash
./gradlew jar
```

产物：`build/libs/Embedize-1.0.0.jar`

## 许可说明

- 本插件代码：见仓库许可。
- Dungeons and Taverns 为第三方 ARR 内容，运行时从 Modrinth 获取；请遵守其作者条款。
- TerraformGenerator / Multiverse-Core 为可选软依赖。
