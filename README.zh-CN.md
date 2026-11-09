# android-bridge

[English](./README.md)

**在 Android 上跑 Mindustry 桌面版 —— 一个 App 装多个游戏版本，Mixin 字节码修改照用。**

## 目录

* [简介](#简介)
* [获取](#获取)
* [提供什么功能](#提供什么功能)
* [支持范围](#支持范围)
* [构建](#构建)
  * [快速构建](#快速构建)
  * [详细构建](#详细构建)
* [常见问题](#常见问题)
* [参与贡献](#参与贡献)
* [支持](#支持)
* [许可](#许可)
* [致谢与依赖](#致谢与依赖)

## 简介

* android-bridge 是一个让 Mindustry **桌面版**跑在 Android 上的 JVM 桥
* **一个 App 装多个游戏或版本**，数据与模组配置也可以多套，互不干扰
* **Mixin 这类字节码修改器照常生效**，CopperLoader、Fabric 这类 loader 可以直接用
* 可以跑官方原版的 Mindustry：发行版 92.0 及以上、BE 构建号 4550 及以上
* 游戏本体不用改，也不用重新打包 —— 直接跑官方发行的 jar
* 本仓库只包含桥本身；宿主 App 需要自己写，或者用一个现成的

## 获取

1. **Releases**：从本仓库的 Releases 下载 `bridge-<version>.jar`（推一个数字开头的版本 tag 会触发 CI 构建并附上产物）
2. **快照**：`snapshot` 那个 release 在每次 push（`main` 或版本 tag）后重建并替换资产（Pre-release），产物是 `bridge-snapshot.jar`；不想自己构建又不想钉版本时用它
3. **从源码构建**：见下面的[构建](#构建)

## 提供什么功能

- **一个 App 管多个游戏**
  - 装多个游戏或多个版本，一次玩一个
  - 数据与模组配置也可以多套，互不干扰
- **模组**
  - CopperLoader、Fabric 这类 loader 可以直接用
  - Mixin 这类字节码修改器照常生效，加载期就把类改掉
- **手感**
  - 触摸、软键盘、返回键与手势、横竖屏旋转
  - 文件选择、剪贴板、振动、打开链接
- **显示**
  - OpenGL ES 2 / 3 都能用
  - 兼容性不好的设备可以切 ANGLE
- **调节与日志**
  - JVM 参数自己加、自己改
  - 日志汇成一份文件，也能同时写进 logcat

## 支持范围

| 项目 | 范围 |
|---|---|
| 游戏版本 | 官方发行版 **92.0 及以上** <br> BE 构建号 **4550 及以上** <br>（两套编号各自独立，互不可比） |
| Android | **11（API 30）及以上** |
| ABI | `arm64-v8a`、`armeabi-v7a`、`x86_64` |
| 游戏 jar | 必须是**带资源的桌面发行版**；只有类的那种跑不起来 |

## 构建

### 快速构建

```bash
git clone https://github.com/MDTCopper/android-bridge.git
cd android-bridge
./gradlew :pack:bridgeJar
```

Windows 上用 `gradlew.bat`。

产物在 `pack/build/libs/bridge-snapshot.jar`：

```bash
./gradlew :pack:bridgeJar
```

### 详细构建

想要自己控制构建过程时，需要准备下面这些。

#### JDK

JDK 25。Gradle 用它跑 daemon，缺了会自动下载。

#### Android SDK

要带 `build-tools`（提供 `d8`），另外还要 NDK 与 cmake：

```properties
ndkVersion=29.0.14206865
cmakeVersion=3.22.1
```

#### 构建

```bash
./gradlew :pack:bridgeJar
```

只想看一眼有哪些任务：

```bash
./gradlew tasks -PskipEpochPrepare
```

## 常见问题

* 游戏的 jar 必须是**带资源的发行版**，只有类的那种跑不起来
* 桥不产出 App，宿主要自己写
* 桥不是 mod loader：模组的载入由宿主的 loader 负责
* **能用 Mixin 改游戏吗？** 能。让桥启动一个自定义 loader（如 CopperLoader、Fabric），由它在加载期做字节码修改。
* **需要 root 吗？** 不需要。
* **能跑别的游戏吗？** 目前只针对 Mindustry 的官方原版。

## 参与贡献

欢迎任何形式的贡献。代码改动请提 Pull Request，说明改了什么、怎么验证的。

完整的要求见 [CONTRIBUTING.zh-CN.md](./CONTRIBUTING.zh-CN.md)。

改动之后跑一遍完整构建：

```bash
./gradlew :pack:bridgeJar
```

动过 `bridge-vX` 的源码之后需要**拿真 jar 在区间下界的版本上把游戏跑起来验证**，编译过不等于跑得起来。

## 支持

有问题或建议请提到本仓库的 issue 追踪，提之前先看[常见问题](#常见问题)。

## 许可

本项目以 [MIT](LICENSE) 许可发布。

## 致谢与依赖

* [Oxygen Launcher](https://github.com/EmmmM9O/oxygen-launcher)
* [Mindustry](https://github.com/Anuken/Mindustry) — [GPLv3](https://github.com/Anuken/Mindustry/blob/master/LICENSE)
* [Arc](https://github.com/Anuken/Arc) — [Apache-2.0](https://github.com/Anuken/Arc/blob/master/LICENSE)
* [LWJGL](https://github.com/LWJGL/lwjgl3) — [BSD-3-Clause](https://www.lwjgl.org/license)
* [ByteHook](https://github.com/bytedance/bhook) — [MIT](https://github.com/bytedance/bhook/blob/main/LICENSE)
