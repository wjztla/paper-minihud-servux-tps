# MiniHUDServuxTPS

[简体中文](README.zh-CN.md)

`MiniHUDServuxTPS` is a Purpur/Paper plugin that implements the minimum `Servux HUD`
subset needed for MiniHUD to display real server-side `TPS/MSPT` values.

## What It Does

This project currently supports only the `servux:hud_metadata` pieces required for:

- metadata handshake
- spawn metadata refresh
- `tps` data logger subscription
- periodic real `TPS/MSPT` updates

It intentionally does **not** implement the rest of Servux yet, such as:

- mob caps
- recipe manager sync
- weather sync
- entity data

## Target Versions

- Java `21+`
- Paper/Purpur `1.21.11`
- MiniHUD `1.21.11`

## Download

Most users should **download the prebuilt plugin jar** instead of building from source.

- Latest release: <https://github.com/wjztla/paper-minihud-servux-tps/releases/latest>
- All releases: <https://github.com/wjztla/paper-minihud-servux-tps/releases>

## Installation

1. Download the plugin jar from the [latest release](https://github.com/wjztla/paper-minihud-servux-tps/releases/latest)
2. Copy the jar into your server `plugins/` folder
3. Start or restart the server
4. Join the server with a client that has MiniHUD installed

## Required Client Setup

MiniHUD must have **HUD Data Sync** enabled.

If `HUD Data Sync` is disabled, MiniHUD can still appear to recognize the channel,
but it will continue to show **estimated** `TPS/MSPT` values instead of the real
server-provided values.

This is the single most important client-side requirement.

## Build from Source

If you want to build the plugin yourself, run:

```bash
./gradlew build
```

The built plugin jar will be generated under:

```text
build/libs/
```

## Runtime Config

Config file:

```text
src/main/resources/config.yml
```

Available options:

- `update-interval-ticks`: how often TPS data is pushed, default `15`
- `debug-logging`: whether to log protocol details, default `false`

## Troubleshooting

### MiniHUD still shows estimated TPS/MSPT

Check the following first:

1. MiniHUD's **HUD Data Sync** is enabled
2. The MiniHUD `Server TPS` info line is enabled
3. The plugin is loaded successfully on the server
4. No older copy of the plugin jar is still present in `plugins/`


## License

This repository currently ships with a `GPL-3.0` license file.

MiniHUD and Servux are separate upstream projects with their own licenses.
This repository does not bundle their source code; it implements a compatible
subset of the HUD protocol for interoperability.

## Current Status

This project is intended to be a small, maintainable, open-source bridge for users
who run Purpur/Paper servers but want MiniHUD to show real server-side `TPS/MSPT`
without requiring a Fabric server + Carpet setup.
