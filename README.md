# android-bridge

[中文](./README.zh-CN.md)

**Run the desktop version of Mindustry on Android — several versions of games in one app, Mixin bytecode patching supported.**

## Table of Contents

* [Introduction](#introduction)
* [Getting](#getting)
* [Features](#features)
* [Supported versions](#supported-versions)
* [Building](#building)
  * [Quick build](#quick-build)
  * [Detailed build](#detailed-build)
* [Known issues](#known-issues)
* [FAQ](#faq)
* [Contributing](#contributing)
* [Support](#support)
* [License](#license)
* [Credits & Dependencies](#credits--dependencies)

## Introduction

* android-bridge is a JVM bridge that runs the **desktop version** of Mindustry on Android
* **Several games or versions in one app**, with as many isolated sets of data and mod configuration
* **Bytecode patchers such as Mixin keep working**, and loaders such as CopperLoader and Fabric run as they are
* It runs vanilla Mindustry: official releases 146.0 and up, bleeding-edge builds 24369 and up
* The game itself needs no changes and no repackaging — it runs the official release jar as it is
* This repository is the bridge alone; the host app is yours to write, or to pick an existing one

## Getting

1. **Releases**: download `bridge-<version>.jar` from this repository's Releases (pushing a tag makes CI build it and attach the artifact)
2. **Build from source**: see [Building](#building)

## Features

- **Several games in one app**
  - Install several games or several versions, play one at a time
  - Data and mod configuration can come in several isolated sets
- **Mods**
  - Loaders such as CopperLoader and Fabric run as they are
  - Bytecode patchers such as Mixin keep working, patching classes as they load
- **Look and feel**
  - Touch, soft keyboard, back key and gesture, rotation
  - File picker, clipboard, vibration, opening links
- **Display**
  - OpenGL ES 2 / 3 both work
  - ANGLE for devices that need it
- **Tuning and logs**
  - Add and change JVM arguments yourself
  - Logs in one file, optionally also in logcat

## Supported versions

| Item | Range |
|---|---|
| Game version | Official releases **146.0 and up** <br> Bleeding-edge builds **24369 and up** <br> (the two numbering schemes are independent and not comparable) |
| Android | **11 (API 30) and up** |
| ABI | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Game jar | Must be a **desktop release build with assets**; a classes-only one does not run |

## Building

### Quick build

```bash
git clone https://github.com/MDTCopper/android-bridge.git
cd android-bridge
./gradlew :pack:bridgeJar
```

On Windows use `gradlew.bat`.

The jar lands in `pack/build/libs/bridge-0.1.0.jar`. The first build checks out and compiles Mindustry and Arc at v159 by itself, which takes a few minutes and a network connection; after that it reuses what is in `.mindustry/` and `.arc/`.

### Detailed build

Prepare the following if you want control over the build.

#### JDK

JDK 25. Gradle runs its daemon on it, and downloads one if it is missing.

#### Android SDK

It needs `build-tools` (that is where `d8` comes from), plus an NDK and cmake:

```properties
ndkVersion=29.0.14206865
cmakeVersion=3.22.1
```

Both versions are pinned in `gradle.properties` — a different NDK produces a different binary. The SDK location goes in `sdk.dir` in `local.properties`, or in `ANDROID_HOME`.

#### Mindustry and Arc

A **full clone** of each, next to this project, and with tags (the build checks out by tag):

```properties
mindustryDir=../Mindustry
arcDir=../Arc
```

Those are the default paths; they live in `gradle.properties` and can be changed.

#### LWJGL Android natives

Nothing to prepare. The build downloads them and packs them into the jar alongside the `.so`.

#### JRE

The bridge does **not** need a JRE to build. It is a payload the host carries at runtime; the tested one is an Android build of OpenJDK 25.

#### Build

```bash
./gradlew :pack:bridgeJar
```

Just to see the task list:

```bash
./gradlew tasks -PskipEpochPrepare
```

## Known issues

* The game jar must be a **release build with assets**; a classes-only one does not run
* The bridge ships no app; the host is yours to write
* The bridge is not a mod loader; loading mods is up to the loader the host provides

## FAQ

* **Can I use Mixin?** Yes. Have the bridge start a custom loader (CopperLoader, Fabric, and so on), and that loader patches bytecode as the game loads.
* **Does it need root?** No.
* **Can it run other games?** Only vanilla Mindustry for now.

## Contributing

Any kind of contribution is welcome. For code changes, open a pull request that says what it does and how you verified it.

Run the full build after a change:

```bash
./gradlew :processor:compileJava :core:compileJava :bridge-v1:compileJava :native:assemble :pack:bridgeJar
```

After touching `bridge-vX` sources, **run the game on the oldest versions of the range with a real jar** —compiling is not running.

## Support

Questions and suggestions go to this repository's issue tracker; read [Known issues](#known-issues) and the [FAQ](#faq) first.

## License

Released under [GPL-3.0](LICENSE).

## Credits & Dependencies

* [Oxygen Launcher](https://github.com/EmmmM9O/oxygen-launcher)
* [Mindustry](https://github.com/Anuken/Mindustry) — [GPLv3](https://github.com/Anuken/Mindustry/blob/master/LICENSE)
* [Arc](https://github.com/Anuken/Arc) — [Apache-2.0](https://github.com/Anuken/Arc/blob/master/LICENSE)
* [LWJGL](https://github.com/LWJGL/lwjgl3) — [BSD-3-Clause](https://www.lwjgl.org/license)
* [ByteHook](https://github.com/bytedance/bhook) — [MIT](https://github.com/bytedance/bhook/blob/main/LICENSE)
