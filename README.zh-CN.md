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
* [已知问题](#已知问题)
* [常见问题](#常见问题)
* [参与贡献](#参与贡献)
* [支持](#支持)
* [许可](#许可)
* [致谢与依赖](#致谢与依赖)

## 简介

* android-bridge 是一个让 Mindustry **桌面版**跑在 Android 上的 JVM 桥
* **一个 App 装多个游戏或版本**，数据与模组配置也可以多套，互不干扰
* **Mixin 这类字节码修改器照常生效**，CopperLoader、Fabric 这类 loader 可以直接用
* 可以跑官方原版的 Mindustry：发行版 146.0 及以上、BE 构建号 24369 及以上
* 游戏本体不用改，也不用重新打包 —— 直接跑官方发行的 jar
* 本仓库只包含桥本身；宿主 App 需要自己写，或者用一个现成的

## 获取

1. **Releases**：从本仓库的 Releases 下载 `bridge-<version>.jar`（打 tag 会触发 CI 构建并附上产物）
2. **从源码构建**：见下面的[构建](#构建)

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
| 游戏版本 | 官方发行版 **146.0 及以上** <br> BE 构建号 **24369 及以上** <br>（两套编号各自独立，互不可比） |
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

产物在 `pack/build/libs/bridge-0.1.0.jar`。第一次构建会自己签出并编译 v159 的 Mindustry 与 Arc，需要几分钟和网络；之后就复用 `.mindustry/` 与 `.arc/` 里的结果。

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

两个版本都钉在 `gradle.properties` —— 换一个 NDK，编出来的产物就变了。SDK 位置写在 `local.properties` 的`sdk.dir`，或者设 `ANDROID_HOME`。

#### Mindustry 与 Arc

各需要一个**完整克隆**，放在本项目旁边，而且必须带 tag（构建要按 tag 签出）：

```properties
mindustryDir=../Mindustry
arcDir=../Arc
```

上面是默认路径，写在 `gradle.properties` 里，可以改。

#### LWJGL 的 Android 原生库

不用准备。构建时自动下载，跟 `.so` 一起打进 jar。

#### JRE

构建桥**不需要** JRE。它是宿主运行时要带的载荷，测试用的是 Android 版 OpenJDK 25。

#### 构建

```bash
./gradlew :pack:bridgeJar
```

只想看一眼有哪些任务：

```bash
./gradlew tasks -PskipEpochPrepare
```

## 已知问题

* 游戏的 jar 必须是**带资源的发行版**，只有类的那种跑不起来
* 桥不产出 App，宿主要自己写
* 桥不是 mod loader：模组的载入由宿主的 loader 负责

## 常见问题

* **能用 Mixin 改游戏吗？** 能。让桥启动一个自定义 loader（如 CopperLoader、Fabric），由它在加载期做字节码修改。
* **需要 root 吗？** 不需要。
* **能跑别的游戏吗？** 目前只针对 Mindustry 的官方原版。

## 参与贡献

欢迎任何形式的贡献。代码改动请提 Pull Request，说明改了什么、怎么验证的。

改动之后跑一遍完整构建：

```bash
./gradlew :processor:compileJava :core:compileJava :bridge-v1:compileJava :native:assemble :pack:bridgeJar
```

动过 `bridge-vX` 的源码之后需要**拿真 jar 在区间下界的版本上把游戏跑起来验证**，编译过不等于跑得起来。

## 支持

有问题或建议请提到本仓库的 issue 追踪，提之前先看[已知问题](#已知问题)与[常见问题](#常见问题)。

## 许可

本项目以 [GPL-3.0](LICENSE) 发布。

## 致谢与依赖

* [Oxygen Launcher](https://github.com/EmmmM9O/oxygen-launcher)
* [Mindustry](https://github.com/Anuken/Mindustry) — [GPLv3](https://github.com/Anuken/Mindustry/blob/master/LICENSE)
* [Arc](https://github.com/Anuken/Arc) — [Apache-2.0](https://github.com/Anuken/Arc/blob/master/LICENSE)
* [LWJGL](https://github.com/LWJGL/lwjgl3) — [BSD-3-Clause](https://www.lwjgl.org/license)
* [ByteHook](https://github.com/bytedance/bhook) — [MIT](https://github.com/bytedance/bhook/blob/main/LICENSE)
