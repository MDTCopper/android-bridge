# 参与贡献

本文说明向本仓库提交改动时需要满足的要求，以及哪些改动不在接受范围内。

## 仓库边界

本仓库只产出 `bridge.jar` 与一套宿主约定，不产出 App。ART 一侧负责活动、Surface、输入与生命周期，
JVM 一侧负责游戏循环与 EGL/GL。宿主 App 由使用方自行提供或选择。

## 接受范围

- 修复某个游戏版本上的行为问题
- 新增版本分支，支持新的游戏版本
- 为宿主增加可扩展点
- 修复构建、打包与兼容性问题

## 不接受范围

- 超出桥的职责。桥只做一件事：把桌面版游戏跑起来；显示、账号、下载、模组加载与界面由宿主实现
- 依赖对游戏、arc 或启动器的源码修改。改动必须能在未修改的它们上面工作
- 针对单个设备或 ROM 的特判

## 扩展方式

扩展应通过既有约定完成：子类、选项、事件、声明。约定不够时可以加接口，但接口要能被任何宿主用上，
不能带某一个宿主的假设。首帧接口就是按这条加的：桥只在游戏交出首帧时回调，显示由宿主决定。

## 复用现有机制

仓库已有以下机制，先确认能否直接表达需求，再看是否需要扩展：

- 跨 VM 调用：`core` 里的注解声明，两侧代码由注解处理器生成
- 日志：`<游戏数据目录>/last_log.txt`
- epoch：`:prepareEpoch*` 任务
- 版本归属：`gradle.properties` 的区间声明
- 宿主选项：`--gl2`、`--gl3`、`--angle`、`--angle-path`、`--logcat` 等

现有机制表达不了时，应扩展现有声明，而不是另建一条通道。其余机制以代码为准。

## 版本与区间

一个分支服务一段版本区间，区间与引用在 `gradle.properties` 中声明。版本归属应使用区间与真实构建号
表达，不应加入“某个版本号归某一档”的特判。

分支以区间内最新的 epoch 作为编译基准，但只能引用区间下界也存在的符号。编译器不检查这一点，需要真机
运行确认。

## 代码与注释

- 使用 Java，不使用 Kotlin。标识符、注释与日志使用英文 ASCII。
- 注释说明原因、约束与对外保证，不重复代码本身的行为，也不与仓库外的文档互相引用。
- 桥的 ART 一侧只使用公开 API。
- 跨 VM 的每帧数据只应包含标量；高频且需要装箱的数据应新增 Batch 通道。

## 提交

提交前应在设备上用产出的 `bridge.jar` 启动一次。改过分支源码、epoch 或版本区间时，还应在支持范围最
老的版本上运行一次。编译通过不代表可用。

提交前运行完整构建：

```bash
./gradlew :processor:compileJava :core:compileJava :bridge-v1:compileJava :bridge-v2:compileJava \
          :native:assemble :pack:bridgeJar
```

没有 NDK 或 Android SDK 时，至少完成 Java 编译，并在 PR 中说明未执行的步骤。

提交信息使用一句话、动词开头，一次提交只做一件事：

```text
fix volume keys not adjusting volume
add safe insets support
v2: add the Mindustry 9 branch (arc merged into the game repository)
```

改动较大，或涉及宿主约定（选项、日志格式、退出行为、活动创建方式）时，应先提 issue 说明方案。

提交中不应包含 `.mindustry/`、`build/`、`*.so`、lwjgl 原生库、`native/src/gen/` 与任何 jar，
也不应包含仅做格式化或重排的改动。

动过 `@Native` 或 `@UsedByNative` 时，名字和描述符都由注解处理器从 Java 声明生成，JNI 注册表和
native 调 Java 的桩会跟着声明一起变，不用手写。`@Native` 指向的 C++ 定义是手写的：名字或参数改了
而定义没跟着改，`:native:assemble` 就是链接错误，不会拖到运行期。

## 问题反馈

反馈问题时请提供以下信息：

- 设备型号与 Android 版本、ABI
- `last_log.txt` 首行（桥版本）与其中的 `game version:` 行
- 使用的 `--angle`、`--angle-path`、`--gl2`、`--gl3` 选项
- 相关日志片段
