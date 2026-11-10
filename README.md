# 拾光 OneStar

![Android](https://img.shields.io/badge/Android-5.0%2B%20(API%2021)-3DDC84?logo=android&logoColor=white)
![targetSdk](https://img.shields.io/badge/targetSdk-35-3DDC84)
![Gradle](https://img.shields.io/badge/Gradle-不需要-1f6feb)
![Build](https://img.shields.io/badge/build-aapt2%20%2B%20javac%20%2B%20d8%20%2B%20apksigner-8957e5)
![Languages](https://img.shields.io/badge/i18n-15%20种语言-f2cc60)
![License](https://img.shields.io/badge/license-MIT-2ea44f)

一个**完全不用 Gradle、不用 Android Gradle Plugin** 的原生 Android 应用：桌面小组件 + 手绘星空 Canvas 界面 + 手工维护的 JNI 层，构建链路由 `aapt2 / javac / d8 / apksigner` 手工串起来，全部代码可直接在命令行编译出 APK。

> 这个仓库的重点不是"一个记日记的 App"，而是**一套可复现的、脱离 Gradle 的 Android 构建实验**。如果你也好奇 AGP 到底替你做了哪些事，这里有一份能跑通的答案。

![星图面板](docs/screenshots/star-map-panel.png)

| 星图总览 | 长按星语 | 应用图标 |
| --- | --- | --- |
| ![星图](docs/screenshots/star-map.png) | ![星语](docs/screenshots/long-press.png) | ![图标](docs/screenshots/icon-1024.png) |

---

## 目录

- [1. 这个项目是什么](#1-这个项目是什么)
- [2. 构建链](#2-构建链)
- [3. 快速开始](#3-快速开始)
- [4. 工程结构](#4-工程结构)
- [5. 深入：三个值得单独说的技术点](#5-深入三个值得单独说的技术点)
- [6. CI](#6-ci)
- [7. 版本与发版流程](#7-版本与发版流程)
- [8. 数据与隐私](#8-数据与隐私)
- [9. 常见问题](#9-常见问题)
- [10. 已知限制](#10-已知限制)
- [11. Roadmap](#11-roadmap)
- [12. 文档索引](#12-文档索引)
- [12.1 跨平台开发与发布规范](#121-跨平台开发与发布规范)
- [13. 参与贡献](#13-参与贡献)
- [14. 许可与致谢](#14-许可与致谢)

## 1. 这个项目是什么

拾光（OneStar）是一款轻量的"每日星"记录 App：每天记一笔心情，界面用 Canvas 逐帧绘制，记录会累积成一片星图；支持桌面小组件、心情曲线、年度长图海报、旋律提示音与 5 秒可撤销的删除。

技术上它刻意**回避了现代 Android 的标准做法**，用来验证一条更"手工"的路线是否仍然可行：

- 没有 Gradle 工程文件，没有 `build.gradle`，没有 `settings.gradle`
- 没有 AndroidX，没有 Material Components，没有第三方 SDK
- 全部界面自绘（`Canvas` + 手写坐标布局），不依赖 XML 布局膨胀
- 全部字符串/资源手写，`aapt2` 直接处理 `res/`
- 二进制层只是一层薄 JNI，编译靠 `zig cc`（不装 NDK）

### 1.1 功能一览

| 能力 | 说明 | 主要实现 |
| --- | --- | --- |
| 每日一星 | 一天记一笔心情，累积成星图；支持补记与修改 | `StarFieldView` / `DaysStore` |
| 手绘星空 | 全部界面由 Canvas 逐帧绘制，含星轨、闪烁、粒子 | `StarFieldView`（3000+ 行） |
| 长按星语 | 长按某颗星查看那天写下的字，可编辑、可删除 | `StarFieldView` / `TextDraw` |
| 撤销删除 | 删除后 5 秒内可撤销，快照编码跨版本兼容 | `DaysCodec` / `DayEdit` |
| 心情曲线 | 按时间采样心情值，画成一条曲线 | `MoodCurve` |
| 年度长图 | 把一年记录导出成一张可分享的长图海报 | `PosterRenderer` |
| 桌面小组件 | AppWidget 展示今日/累计星数，跟随系统语言刷新 | `StarWidget` / `WidgetState` |
| 旋律提示音 | 点亮星星时播放一小段旋律，音序与节拍可测 | `ChimePlayer` / `Melody` |
| 15 种语言 | 简繁中文、英、日、韩、法、德、西（西班牙/拉美）、阿、泰、葡（巴西/欧洲）、藏、维吾尔 | `I18n` / `TextDraw`（RTL 重排） |
| 无障碍基础 | 夜空整体可朗读/可点击，顶部按钮暴露 Button 角色与 48dp 可点区 | `StarFieldView`（0.12.2 起） |

### 1.2 为什么可以不用 Gradle

AGP 并不是 Android 运行时的必需品，它主要做四件事：把资源编译成二进制、生成 `R` 类、把 class 变成 dex、把一切塞进 APK 并签名对齐。这四件事分别对应 `aapt2`、`aapt2 link --java`、`d8`、`zipalign / apksigner`，都是 SDK 自带的命令行工具。

| 平常由 AGP 完成 | 本项目对应做法 |
| --- | --- |
| 资源编译与合并 | `aapt2 compile` → `aapt2 link`，`res/` 手写，无 `values-*` 变体 |
| 生成 `R.java` | `aapt2 link --java gen/`，随 `java/` 一起 `javac` |
| Java 编译与脱糖 | `javac -source 8 -target 8 --release 8`，不使用需要 desugar 的语法 |
| dex 转换 | `d8 --min-api 21 --release` |
| 打包与压缩 | 手工 `zip` 逐个条目写入，配合 `tools/zip16k.py` 做对齐 |
| 签名 | `apksigner sign`，支持固定 keystore（见 5.3） |
| 变体 / 依赖管理 | 不需要：无第三方依赖、无 flavor、ABI 由 `ABIS` 变量控制 |
| 增量构建 | 不做，全量构建约数十秒；换来的是构建脚本 200 行可读可改 |

代价同样明确：没有依赖解析、没有注解处理器、没有资源混淆、没有变体矩阵，也没有 IDE 的一键运行。这个仓库存在的意义，是把代价和收益都摆在明面上。

## 2. 构建链

```text
res/  ──aapt2 compile──►  res.zip
                             │
AndroidManifest.xml ──aapt2 link──►  未签名 APK（含 R.java + assets/）
                             │
java/ + gen/ ──javac(source 8)──►  classes/
                             │
classes/ ──d8 --min-api 21──►  classes.dex ──► 打入 APK
                             │
jni/src/*.c ──zig cc (aarch64-linux-musl)──►  lib/arm64-v8a/*.so ──► 打入 APK
                             │
tools/zip16k.py ──►  16 KB 页对齐重打包（.so 以 STORED 存放并对齐 16384）
                             │
apksigner sign ──►  onestar-<version>-signed.apk
```

对应脚本就是仓库根的 `build.sh`，一共 200 来行 Bash。构建前它还会做两道版本自检：`versionCode` 不得小于历史最大值，`assets/COPYRIGHT.txt` 里的版本号必须与 `AndroidManifest.xml` 一致（确需跳过时设 `SKIP_VERSION_CHECK=1`）。

## 3. 快速开始

### 3.1 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 只需要其中的 `javac` / `keytool` |
| Android SDK build-tools | 34+ | 提供 `aapt2` / `d8` / `apksigner` |
| Android SDK platform | android-35 | 提供 `android.jar` |
| Python | 3.8+ | 仅用于 `tools/zip16k.py` |
| zig | 0.11+ | 可选，用于编译 `jni/src/*.c`；不提供则跳过原生库 |

### 3.2 构建

```bash
# 最小用法：用默认 debug 签名出一个可安装的 APK
ANDROID_HOME=$HOME/Android/Sdk ./build.sh

# 编译 JNI 并用自己的签名
ANDROID_HOME=$HOME/Android/Sdk \
JAVA_HOME=/path/to/jdk-17 \
ZIG=$(command -v zig) \
ONESTAR_KS=release.keystore ONESTAR_KS_PASS=****** ONESTAR_KS_ALIAS=onestar \
./build.sh
```

产物在 `build/onestar-<versionName>-signed.apk`。

可用环境变量：`ANDROID_HOME` / `ANDROID_SDK_ROOT`、`BUILD_TOOLS`、`JAVA_HOME`、`MIN_SDK`（默认 21）、`TARGET_SDK`（默认 35）、`ABIS`（默认 `arm64-v8a`）、`ZIG`、`ONESTAR_KS` / `ONESTAR_KS_PASS` / `ONESTAR_KS_ALIAS`、`ONESTAR_KS_B64`（base64 内联 keystore，CI 用）、`SKIP_VERSION_CHECK`。

### 3.3 安装到设备

```bash
adb install -r build/onestar-0.12.2-signed.apk
```

`-r` 表示保留数据覆盖安装。覆盖安装的前提是**新旧包使用同一张证书**，且 `versionCode` 不小于已安装版本；这两条任意一条不满足，系统都会拒绝安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 或 `INSTALL_FAILED_VERSION_DOWNGRADE`）。细节见 [5.3](#53-签名与覆盖升级)。

### 3.4 跑单测（不需要模拟器、不需要 android.jar）

```bash
JAVA_HOME=/path/to/jdk-17 ./test.sh
```

`test.sh` 会自动挑出不 `import android.*` 的纯逻辑类，与 `test/` 下的断言程序一起用普通 `javac` 编译，然后逐个跑 `main`。目前 9 个测试类、共 3131 项断言（其中前 7 类合计 221 项，多语言两层合计 2910 项）：

| 测试类 | 覆盖对象 | 断言数 |
| --- | --- | --- |
| `CompatTableTest` | 系统版本 / 页大小 / 内存档位映射 / 页大小说明文案 | 38 |
| `V4LogicTest` | 星轨回放与曲线取样 | 59 |
| `ConstellationTest` | 星座与星名生成 | 31 |
| `V62LogicTest` | 补记标记、撤销快照的编解码兼容 | 26 |
| `NoteTextTest` | 备注文本折行与截断 | 24 |
| `DayEditTest` | 日记录编辑状态机 | 23 |
| `MelodyTest` | 旋律序列与节拍 | 20 |
| `I18nTableTest` | 多语言表完整性、语言判定、日期/数字本地化 | 2818 |
| `I18nTest`（`com.yangyang2857rgb.onestar` 包） | 外语模式下各逻辑类输出不得残留汉字 | 92 |

这类"把可纯化的逻辑抽出来，用 JVM 断言兜住"的做法，是这个工程能长期手工演进而不至于崩掉的主要原因。

## 4. 工程结构

```text
.
├── AndroidManifest.xml          # 单 Activity + 一个 AppWidget receiver，无任何权限声明
├── build.sh                     # 无 Gradle 构建脚本
├── test.sh                      # 纯 JVM 单测入口
├── assets/
│   └── COPYRIGHT.txt            # 署名信息（随 APK 打包，也用于版本内说明）
├── docs/
│   ├── 发版规范.md              # 版本号规则与发布纪律
│   ├── 拾光OneStar_版本时间轴.md  # 历代版本与旧流水号对照
│   ├── 签名与覆盖升级.md        # 证书固定、CI 注入与验签流程
│   └── screenshots/             # README 引用的截图
├── java/com/yangyang2857rgb/onestar/
│   ├── MainActivity.java        # 入口，手势/生命周期
│   ├── StarFieldView.java       # 核心自绘视图（3036 行）
│   ├── I18n.java                # 15 种语言文案表（3237 行，全仓最大）
│   ├── TextDraw.java            # Canvas 文字绘制与 RTL 视觉重排
│   ├── DaysStore.java           # SharedPreferences 存储读写
│   ├── DaysCodec.java           # 记录行编码/解析（纯逻辑，可单测）
│   ├── DayEdit.java             # 编辑状态机（纯逻辑）
│   ├── Constellation.java       # 星座/星名（纯逻辑）
│   ├── StarName.java            # 星名中英对照（纯逻辑）
│   ├── ChimePlayer.java         # 提示音播放
│   ├── MoodCurve.java           # 心情曲线采样
│   ├── Replay.java              # 星轨回放
│   ├── Melody.java              # 提示音旋律
│   ├── NoteText.java            # 备注折行
│   ├── PosterRenderer.java      # 年度长图海报
│   ├── StarWidget.java          # 桌面小组件
│   ├── WidgetState.java         # 小组件状态（纯逻辑）
│   ├── Compat.java / CompatTable.java  # 系统能力适配（后者纯逻辑）
│   └── NativeCore.java          # JNI 声明与纯 Java 回退
├── jni/
│   ├── include/                 # 交叉编译所需的极简头
│   └── src/{ons_page,ons_dsp,ons_curve}.c
├── res/                         # 手写 drawable / mipmap / xml（含 locales_config.xml）
├── tools/
│   ├── zip16k.py                # 16 KB 对齐重打包
│   └── verify_apk_signature.py  # 验签与证书指纹比对
└── test/                        # 纯 JVM 断言程序
```

## 5. 深入：三个值得单独说的技术点

### 5.1 16 KB 页大小对齐

Android 15 起部分设备使用 16 KB 内存页，未按 16 KB 对齐的 `.so` 会加载失败。build-tools 34 的 `zipalign` 还不支持 `-P 16`，因此这里自己写了 `tools/zip16k.py`：把 `lib/**/*.so` 以 STORED（不压缩）方式重打包，并用 ZIP extra field 把数据偏移顶到 16384 的倍数，其余条目按 4 字节对齐。脚本自带 `--check` 校验，构建末尾会打印每个 `.so` 的实际偏移与对齐结果。

### 5.2 不装 NDK 的 JNI

`jni/src` 只有三个很薄的 C 文件（页面索引、采样、曲线），全部 `-nostdlib` 编译，头文件只需 JDK 自带的 `jni.h` 加极简桩。用 `zig cc --target aarch64-linux-musl` 交叉编译，省掉数 GB 的 NDK。`NativeCore` 在库缺失时自动退回纯 Java 实现，因此不提供 `ZIG` 也能构建、也能跑（性能略降）。

### 5.3 签名与覆盖升级

Android 的覆盖安装由系统做三件事的校验：**包名相同**、**证书相同**、**`versionCode` 不降**。任何一项不满足，安装器直接拒绝。

这个仓库曾在 CI 里踩过坑：工作流每次都现场 `keytool -genkeypair` 生成一个"一次性 debug keystore"，于是**每次构建出来的 APK 证书都不一样**。用户装了上一版，再装下一版就会得到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，只能卸载重装，本地数据一并丢失。

现在的约定是：**证书固定、口令固定、流水号只增**。

| 项 | 值 |
| --- | --- |
| 包名 | `com.yangyang2857rgb.onestar` |
| 签名证书主题 | `CN=OneStar Debug, O=OneStar, C=CN` |
| 证书 SHA-256 | `B0:F1:E2:A1:36:84:16:90:0A:AC:9C:A2:F9:9F:19:1C:3F:E2:C4:13:43:50:3A:7F:38:A0:F7:98:79:D5:4C:D1` |
| 有效期 | 2026-11-07 → 2054-03-25 |
| 签名方案 | v1（JAR）+ v2（APK Signature Scheme v2） |

CI 通过仓库 Secret 注入这张证书，而不是现场生成：

1. 本地把 keystore 转成 base64：`base64 -w0 onestar-debug.keystore > ks.b64`
2. 在 **Settings → Secrets and variables → Actions** 新建 `ONESTAR_KS_B64`（证书本体）与 `ONESTAR_KS_PASS`（口令）
3. `build.sh` 检测到 `ONESTAR_KS_B64` 时先解码到 `build/work/` 再签名；没有该变量才退回"一次性 debug keystore"
4. 构建末尾用 `tools/verify_apk_signature.py` 校验证书指纹，指纹不符直接让流水线失败

本地自检：

```bash
# 打印 APK 的签名证书
apksigner verify --print-certs build/onestar-0.12.2-signed.apk

# 或只看本项目约定的指纹是否一致
python3 tools/verify_apk_signature.py build/onestar-0.12.2-signed.apk
```

需要强调边界：这张 debug 证书的私钥随仓库/Secret 流转，虽然不在源码里明文出现，但**它不具备任何防伪能力**。它只适合自用、体验与验证构建链，**不能上架应用商店，也不适合对外分发**。要正式分发必须换成自己的 release keystore（`ONESTAR_KS` / `ONESTAR_KS_PASS`），代价是老用户需要卸载重装。

## 6. CI

`.github/workflows/android.yml` 是一份开箱即用的工作流：在 Ubuntu 上装 build-tools 34 与 platform 35，先跑 `test.sh` 再跑 `build.sh`，把签名后的 APK 作为 workflow artifact 上传。整个流水线同样不含 Gradle。

触发方式：推送到 `main`、打 `v*` 标签，或在 **Actions → build → Run workflow** 手动执行。产物在该次运行的 Artifacts → `onestar-apk`。

签名相关：工作流会读取 `ONESTAR_KS_B64` / `ONESTAR_KS_PASS` 两个 Secret；未配置时按"一次性 debug keystore"构建，并在日志里给出显式警告——那种产物不具备覆盖升级能力。

## 7. 版本与发版流程

版本记录见 [CHANGELOG.md](CHANGELOG.md)，规则全文见 [docs/发版规范.md](docs/发版规范.md)，历代版本对照见 [docs/拾光OneStar_版本时间轴.md](docs/拾光OneStar_版本时间轴.md)。

三条硬规则：

- **`versionName` 走语义化版本**：`MAJOR` 破坏性变更（存储格式、包名、构建方式、签名）、`MINOR` 新增可感知功能、`PATCH` 只修缺陷/手感/文案/无障碍
- **`versionCode` 是独立流水号**：与 `versionName` 没有换算关系，只增不减、永不复用，`build.sh` 在构建前校验
- **只改文档不发版**：README、注释、CHANGELOG 的纯文档改动直接进 `main`，不占版本号

一次发版的完整动作：

1. 改 `AndroidManifest.xml` 的 `versionCode` / `versionName`，同步 `assets/COPYRIGHT.txt`
2. 更新 `CHANGELOG.md`（Keep a Changelog 风格，未发布内容放 `Unreleased`）
3. `./test.sh && ./build.sh` 全绿
4. 提交、打 tag（`v0.12.2`）、推送
5. 在 **Releases** 用该 tag 建 Release，附 APK 与 SHA-256，写清变更点与升级注意事项

> 历史包袱说明：`v6.3`–`v6.7` 用的是旧的流水号式版本名，`0.12.x` 起改回语义化版本号，两者通过 CHANGELOG 与版本时间轴对照，`versionCode` 始终连续。`0.12.2` 对应 `versionCode 23`。

## 8. 数据与隐私

- **没有任何权限声明**：`AndroidManifest.xml` 里没有 `INTERNET`、没有存储、没有定位、没有传感器权限，App 在权限层面就没有联网能力
- **没有第三方 SDK / 统计 / 广告 / 崩溃上报**
- 记录写在应用私有的 `SharedPreferences` 里，格式由 `DaysCodec` 编解码；升级不改变存储格式，卸载即删除
- `android:allowBackup="true"`，因此系统备份（含 adb backup / 换机迁移）会把数据一起带走；不希望如此可自行改为 `false`
- 年度长图海报由本地 Canvas 渲染，不上传任何服务器

## 9. 常见问题

**Q：覆盖安装报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`？**
新旧包证书不一致。用 `apksigner verify --print-certs` 对比两个 APK；若确实不同，只能卸载重装（数据会丢），或改用同一张 keystore 重新签名。

**Q：报 `INSTALL_FAILED_VERSION_DOWNGRADE`？**
新包的 `versionCode` 小于等于已装版本。`versionCode` 只增不减，改大后重新构建即可。

**Q：`build.sh` 说找不到 `aapt2` / `d8` / `apksigner`？**
`ANDROID_HOME` 没指对，或 build-tools 低于 34。可以先 `ls $ANDROID_HOME/build-tools` 确认，必要时用 `BUILD_TOOLS=34.0.0` 显式指定。

**Q：为什么没有 `res/values-zh/` 这类多语言目录？**
界面文案不交给系统资源系统，而是集中在 `I18n.java` 的文案表里，由 `TextDraw` 处理换行、断词与 RTL 重排。`res/` 里只有系统必须读取的图标名、小组件标题等少量条目，`res/xml/locales_config.xml` 仅用于接入 Android 13 的「应用语言」入口。

**Q：不装 zig 能构建吗？**
能。`jni/` 会被跳过，`NativeCore` 自动走纯 Java 实现，功能完整、性能略降。

**Q：为什么只出 `arm64-v8a`？**
绝大多数在用设备都是 64 位 ARM。要其他 ABI 就改 `ABIS`，并自行补对应的 `zig cc` target。

**Q：支持的最低版本？**
`minSdkVersion 21`（Android 5.0）。低于 Android 13 的系统会忽略 `locales_config.xml`，语言仍由 `I18n.detect()` 跟随系统语言。

**Q：为什么版本号一会儿 6.x 一会儿 0.12.x？**
见 [7. 版本与发版流程](#7-版本与发版流程) 的历史包袱说明：6.x 是早期流水号式命名，0.12.x 起改用语义化版本，`versionCode` 连续未断。

## 10. 已知限制

- 只构建 `arm64-v8a`；需要其他 ABI 时改 `ABIS` 并自行加交叉工具链目标
- 界面坐标按竖屏固定比例手算，极端屏幕比例下需要调参
- Release 页面与 CI 产物使用固定的 **debug 证书**签名，只适合自用、体验与验证构建链，**不能上架应用商店，也不适合对外分发**（见 5.3）
- 多语言覆盖 15 种语言（简繁中文、英、日、韩、法、德、西班牙（西班牙/拉美）、阿拉伯、泰、巴西/欧洲葡萄牙、藏、维吾尔），全部由 `I18n.java` 的文案表与 `TextDraw` 的 RTL 重排实现，没有引入第三方 i18n 框架
- 无障碍为部分适配：0.12.2 起顶部胶囊按钮向 TalkBack 暴露 Button 角色与 `contentDescription`，并把可点区放大到不小于 48dp；完整的无障碍树、焦点遍历顺序与朗读节奏仍未系统梳理
- 没有增量构建，全量重编；没有依赖管理，新增第三方库需要手工接入

## 11. Roadmap

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 0.12.2 | 胶囊按钮对比度/可点区、空状态引导、无障碍基础、签名固化 | 预览包 `v0.12.2-rc1`，待真机回归 |
| 1.0.0 | 版式与文案冻结；15 种语言真机回归；系统字号极值与横竖屏切换回归；软著登记材料 | 计划中 |
| 1.x | 完整无障碍树与焦点顺序；更多 ABI；记录导出/导入 | 待定 |

## 12. 文档索引

| 文档 | 内容 |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | 逐版本变更记录与 `versionCode` 对照 |
| [docs/发版规范.md](docs/发版规范.md) | SemVer 2.0.0 + Keep a Changelog + Conventional Commits + DORA 指标 + WCAG 2.2 的落地规则 |
| [docs/拾光OneStar_版本时间轴.md](docs/拾光OneStar_版本时间轴.md) | 历代版本、旧流水号与语义化版本号的对应关系 |
| [docs/签名与覆盖升级.md](docs/签名与覆盖升级.md) | 证书固定、CI Secret 注入、验签与排错 |
| [docs/全生态开发与发布总纲.md](docs/全生态开发与发布总纲.md) | 六生态统一口径：全局时间表、身份与签名锚点、统一发布闸门 12 道、风险矩阵 |
| [docs/README.md](docs/README.md) | docs 目录索引与阅读路径 |
| [docs/ecosystems/](docs/ecosystems/) | 六个平台各自的完整要点原文（含签名命令与逐项自检清单） |
| [LICENSE](LICENSE) | MIT |

## 12.1 跨平台开发与发布规范

拾光 OneStar 同时维护 Android、Web、HarmonyOS、Apple、Windows、Linux 六个生态。六个平台的商店政策、SDK 门槛、签名链条差异很大，且**大部分约束是不可逆的**——包名改一次等于换一款应用，Linux 构建基线升一次等于永久放弃旧系统用户，macOS 漏掉公证等于用户下载后直接打不开。

因此跨平台发布规则被整理成一套可执行的规范，放在 [`docs/`](docs/)，入口是 [全生态开发与发布总纲](docs/全生态开发与发布总纲.md)：

| 文档 | 内容 | 什么时候看 |
| --- | --- | --- |
| [全生态开发与发布总纲](docs/全生态开发与发布总纲.md) | 六个生态放在一起时的统一口径：全局时间表、身份与签名锚点、跨平台共性矩阵、统一发布闸门 12 道、风险矩阵 | 排期、定锚点、发版前 |
| [docs 目录索引](docs/README.md) | 文档结构与阅读路径 | 第一次进 docs |
| [docs/ecosystems/](docs/ecosystems/) | 六个平台各自的完整要点原文（含签名命令、逐项自检清单） | 需要具体命令或逐项核对 |

### 三个最值得先知道的事实

1. **时间表里"已生效"的那一栏才是重点**。总纲把"已生效"与"待生效"分开列：Android 已要求 target API 36（Wear OS / AAOS 35、TV / XR 34）与 16 KB 页面对齐；Apple 已要求 iOS 26 SDK 且 macOS 27 起仅支持 Apple Silicon；Windows 10 已全面 EOL 且 ESU 也已到期。不满足这些，包不是"体验差"，是**提交不进去**。
2. **EV 证书不再绕过 SmartScreen**。Windows 侧唯一零警告路径是 Microsoft Store；非 Store 分发必须接受数周、数百次安装的信誉积累期。买 EV 只为消警告，钱是白花的。
3. **六个平台的隐私审核本质是同一条链条**：权限声明 → SDK 实际行为 → 网络请求 → 商店隐私表单 → 隐私政策。五套表单应当从同一份数据流图派生；反过来做，必然出现"声明与实际不一致"的高频拒审。

### 维护约定

规范类文档会随平台政策老化。约定：每季度复跑总纲 §8 的四项时效检查（Android target API、Apple SDK 门槛、Windows App SDK 版本、HarmonyOS 上架规则）；平台政策变化时先改 `docs/ecosystems/` 原文，再同步总纲，避免两处漂移。

## 13. 参与贡献

- 修 bug / 调手感：直接提 PR，目标分支 `main`，提交信息遵循 Conventional Commits（`fix:` / `feat:` / `docs:` / `chore:`）
- 改行为：先开 Issue 说明动机与影响面，涉及存储格式或签名的改动必须写清升级路径
- 提 PR 前请至少跑通 `./test.sh`；改到 `build.sh` 或 `tools/` 的，请附一次完整构建日志
- 新增/修改文案：15 种语言的 `I18n` 表必须同时更新，`I18nTableTest` 会校验行数与占位符一致性

## 14. 许可与致谢

代码以 [MIT License](LICENSE) 发布。`assets/COPYRIGHT.txt` 中的署名信息请随分发保留。

感谢 Android SDK 命令行工具链（`aapt2` / `d8` / `apksigner`）与 [zig](https://ziglang.org/) 提供了不依赖 Gradle 与 NDK 的另一种可能。

---

作者：王孙阳（Wang Sunyang）· 独立开发
