# MiniHUDServuxTPS

[English](README.md)

`MiniHUDServuxTPS` 是一个面向 Purpur/Paper 的插件，用来实现 MiniHUD 显示真实服务器 `TPS/MSPT` 所需的最小 `Servux HUD` 协议子集。

## 项目功能

当前项目只实现了 `servux:hud_metadata` 通道中，MiniHUD 显示真实 `TPS/MSPT` 所必需的部分：

- 元数据握手
- 出生点元数据刷新
- `tps` 数据记录器订阅
- 周期性推送真实 `TPS/MSPT`

目前**不**打算一次性实现完整 Servux，暂不包含以下内容：

- mob caps
- 配方同步
- 天气同步
- 实体数据同步

## 目标版本

- Java `21+`
- Paper/Purpur `1.21.11`
- MiniHUD `1.21.11`

## 下载

大多数用户应该直接下载**已经编译好的插件 jar**，不需要自己构建。

- 最新发布页：<https://github.com/wjztla/paper-minihud-servux-tps/releases/latest>
- 所有版本：<https://github.com/wjztla/paper-minihud-servux-tps/releases>

## 安装方法

1. 前往[最新发布页](https://github.com/wjztla/paper-minihud-servux-tps/releases/latest)下载插件 jar
2. 将下载好的 jar 复制到服务端的 `plugins/` 目录
3. 启动或重启服务器
4. 使用安装了 MiniHUD 的客户端进入服务器

## 客户端必须开启的选项

MiniHUD 必须开启 **HUD Data Sync （HUD 数据同步）**。

如果 `HUD Data Sync（HUD 数据同步）` 没开，MiniHUD 即使看起来识别到了通道，也仍然只会显示**预估**的 `TPS/MSPT`，而不会显示服务端真正推送的值。

这是目前最重要，也最容易忽略的客户端前置条件。

## 从源码构建

如果你确实需要自己构建，再运行：

```bash
./gradlew build
```

构建后的插件产物位于：

```text
build/libs/
```

## 运行时配置

配置文件：

```text
src/main/resources/config.yml
```

可用选项：

- `update-interval-ticks`：TPS 数据推送间隔，默认 `15`
- `debug-logging`：是否输出协议调试日志，默认 `false`

## 排错说明

### MiniHUD 仍然显示预估 TPS/MSPT

请优先检查以下几项：

1. MiniHUD 的 **HUD Data Sync（HUD 数据同步）** 是否已开启
2. MiniHUD 的 `Server TPS` 信息行是否已启用
3. 插件是否已经被服务器正确加载
4. `plugins/` 目录中是否还残留旧版本插件 jar

## 许可证

本仓库当前附带的是 `GPL-3.0` 许可证文件。

MiniHUD 与 Servux 是各自独立的上游项目，并且拥有它们自己的许可证。
本仓库并不打包它们的源码，而是为了互操作性实现了一个兼容的 HUD 协议子集。

## 当前状态

这个项目的目标，是为运行 Paper/Purpur 的服务器提供一个小型、可维护、适合开源继续演进的桥接插件，让 MiniHUD 在不依赖 Fabric 服务端 + Carpet 的前提下，也能显示真实的服务器 `TPS/MSPT`。
