苹果全生态开发与发布要点

目标：照着做不出岔子。以下标注 ⛔ 的条目，都是踩了会直接导致 App Store 拒审、上传失败，或已发布应用无法更新的红线。

---

0. 先记住四条“沉默的杀手”

⛔ 杀手一：SDK 最低版本有时间窗口，跨过就传不上去。
2026 年 4 月 28 日起，上传到 App Store Connect 的应用必须使用 iOS/iPadOS 26、watchOS 26、visionOS 26 SDK 或更高版本构建。2027 年 4 月起门槛进一步抬升到 iOS/iPadOS 27、watchOS 27、visionOS 27、tvOS 27 SDK。这意味着 Xcode 26 是 2026 年的最低底线，Xcode 27 是 2027 年的底线。不要在项目启动后才升级 SDK，版本滞后会直接卡住发版。

⛔ 杀手二：macOS 27 是 Apple Silicon 专属，Intel 支持在 macOS 26 终结。
macOS 26 是最后一个支持 Intel Mac 和 Rosetta 的版本，macOS 27 起仅支持 Apple Silicon。如果你的应用或依赖中包含 x86_64 架构，需要在 Xcode 中将构建架构切换为 arm64 only，否则在新系统上面临兼容性问题。

⛔ 杀手三：watchOS 必须 64 位，二进制体积有 75MB 上限。
2026 年 4 月起，watchOS 应用必须包含 arm64 架构并以 watchOS 26 SDK 构建。同时，watchOS 应用的二进制体积不能超过 75MB，超出将无法提交。手表包不能简单当作手机包的“裁剪版”，需要独立控制资源体积。

⛔ 杀手四：指纹识别（fingerprinting）在苹果生态“零容忍”。
Apple 审核指南 3.3.9 明确规定：不得通过算法转换设备和使用数据来唯一识别用户或设备。即使用户授权追踪、即使在隐私政策中披露，指纹识别仍然不被允许。第三方归因 SDK（如 AppsFlyer）如果包含 fingerprinting 功能，会连带导致整包被拒。上线前逐一审查所有第三方 SDK 的这一行为。

1. 包格式与分发渠道

渠道 格式 适用场景 关键约束
App Store .ipa（通过 Xcode Archive 上传，实际传输的是 .xcarchive 导出的包） 正式上架 必须使用 App Store 分发证书 + 对应 Provisioning Profile；走 App Store 审核
TestFlight 同上 内测/外测 内部测试最多 100 人，无需审核；外部测试最多 10,000 人，首个构建需要 Beta App Review；构建 90 天过期
官网直装（macOS） .dmg / .pkg Mac App Store 之外的独立分发 必须 Developer ID 签名 + 公证（notarization） + staple，否则 Gatekeeper 直接拒绝打开，不是“右键打开”能绕过的
企业分发 .ipa 仅限企业内部员工 需要 Apple Developer Enterprise Program；不得用于公众分发

执行口径： App Store 上架用 App Store 分发；macOS 官网直装必须签名+公证，缺一不可；TestFlight 是发版前最后一道真实设备验证，不要跳过。

2. 通用布局原则：Size Class 驱动，不按设备型号写死

苹果 HIG 的核心要求是 “适应实际窗口宽度”，而非按设备名判断。

核心手段（SwiftUI 优先）：

· NavigationSplitView + Size Class：三栏在 iPad 上展开，在 iPhone 的 compact 环境下自动折叠为单栏（stack），这是官方推荐的自动适配方式。不要手写 if device == iPad 的分支。
· 布局随窗口连续适应，而非按断点重新设计：iOS 26 HIG 明确要求设计跨窗口尺寸连续适应，而不是在不同断点之间“跳变”。用 GeometryReader 和自适应容器，让界面平滑响应 resize 和分屏。
· iPad 多任务必须优雅响应：Split View、Slide Over、Stage Manager 都要测试，每一种分割比例和 Size Class 过渡都要回归。不要只在全屏横屏下验证一次。
· 每个窗口独立保留导航状态：分屏/多窗口场景下，不能因为窗口切换而丢失导航栈或未保存的工作。用 @SceneStorage / @StateObject 按窗口隔离状态。

大屏底线： 不锁定方向、不禁止 resize；在 compact / medium / expanded 三种窗口尺寸下，主要功能均可到达，不出现内容截断或按钮被挤出可视区域。

3. 各平台适配要点

📱 iPhone（含 iPhone Duo 类折叠屏）

· iPhone Duo 类设备要求应用在开合之间无缝适应内外双屏，提供连续体验。不能用“外屏一套布局、内屏另一套”的硬切逻辑，而应基于可用窗口宽度连续响应。
· 触控目标、安全区、Dynamic Island / 刘海避让是基础项。

📟 iPad

· 侧边栏 + 多栏布局是 iPad 的原生模式，优先使用 NavigationSplitView / UISplitViewController，不要套用 iPhone 的底部 Tab Bar。
· 支持可调整大小的窗口和 iPadOS 多任务（Split View / Slide Over / Stage Manager）。
· 键盘快捷键、指针（trackpad/mouse）交互需要补齐，iPad 用户高频使用外接键盘。

💻 macOS

· macOS 27 起 Apple Silicon only，构建架构切到 arm64 only。
· 充分利用菜单栏、工具栏、键盘快捷键，窗口应可精确 resize。不要直接把 iPad 的触控 UI 搬到 Mac 上。
· 官网直装路径：Developer ID Application 证书签名 → ENABLE_HARDENED_RUNTIME = YES → xcrun notarytool 提交公证 → staple 票证到 DMG/pkg。未公证的应用在 macOS 15+ / 26+ 上会被 Gatekeeper 直接拦截，提示“已损坏”或拒绝打开。

⌚ watchOS

· 必须是 64 位 arm64 架构，使用 watchOS 26 SDK 构建（2026 年 4 月起）；2027 年 4 月起抬到 watchOS 27 SDK。
· 二进制体积 ≤75MB，超出无法提交。手表包应独立瘦身，不要把手机端全量资源带入。
· 交互以扫视（glanceability） 为第一原则：一屏一件事，减少层级；优先 SwiftUI 独立应用路径。

🥽 visionOS

· SDK 要求与 iOS 同步：2026 年 4 月起 visionOS 26 SDK，2027 年 4 月起 visionOS 27 SDK。
· 空间一致性：窗口、volume、ornament 在共享空间中位置可预测，不要遮挡内容；用 ornament 承载工具栏和控件。
· 优先使用系统空间组件，不要试图在 visionOS 上复刻 2D 悬浮窗口体验。

4. 签名与公证（苹果生态的“双轨制”）

4.1 App Store 分发（iOS / iPadOS / watchOS / tvOS / visionOS）

· 使用 App Store 分发证书 + 对应平台的 Provisioning Profile，通过 Xcode Archive 上传到 App Store Connect。
· TestFlight 同样走 App Store 分发体系，不需要额外公证，但构建过期时间为 90 天。
· 上传后通过 TestFlight 做真实设备验证，再提交 App Review，这是发版前的标准动作。

4.2 macOS 官网直装分发（独立于 App Store）

这是苹果生态中最容易出岔子的分发路径，必须完整走完以下链条：

1. Developer ID Application 证书签名：每个二进制都要用 Developer ID 签名，不能用 Ad-hoc。
2. 启用 Hardened Runtime：ENABLE_HARDENED_RUNTIME = YES，公证的硬性前提。
3. 公证提交：通过 xcrun notarytool 提交到 Apple Notary Service，自动扫描通常 5–15 分钟完成。
4. Staple 票证：公证通过后，将票证 staple 到 DMG/pkg 上，确保用户离线首次打开时 Gatekeeper 也能验证。

⛔ 顺序不能错：签名 → 公证 → staple → 分发。跳过公证或 staple，下载后的应用会被 Gatekeeper 直接拒绝，不是“右键打开”能绕过的。

5. 隐私合规：苹果审核的“最高频拒审区”

5.1 隐私清单（Privacy Manifest）是强制项

应用和第三方 SDK 都必须包含 PrivacyInfo.xcprivacy 文件，声明四类信息：是否追踪（NSPrivacyTracking）、追踪域名、收集的数据类型、使用的“需要理由的 API”（NSPrivacyAccessedAPITypes）。

执行口径：

· 不仅主应用要加，每个第三方 SDK 都必须自带隐私清单。从 2025 年 2 月 12 日起，新增或更新隐私影响 SDK 时缺少 manifest 会被拒绝。
· 隐私清单在上传时由 App Store Connect 严格解析，格式错误或与目标未关联（target 未引用）会导致上传失败。

5.2 隐私政策与权限描述

· 隐私政策 URL 必须是有效的、公开可访问的网页链接，不能是 PDF，应用内必须有入口。
· 权限描述必须完整、具体：不能只写“需要相机”，要写清楚用来做什么。权限描述不完整是高频拒审原因。

5.3 数据安全标签必须与实际行为一致

App Store Connect 中的 App Privacy 标签必须与 Manifest 权限、第三方 SDK 实际行为、隐私清单三者对齐。标签与实际行为矛盾会直接触发 Guideline 5.1 拒审。

5.4 苹果审核高频拒审原因速查

· 不完整信息 / 应用无法完整运行（Guideline 2.1）。
· 隐私政策缺失或无效链接（Guideline 5.1.1）。
· 隐私标签与实际不符（Guideline 5.1）。
· 指纹识别（Guideline 5.1.2(iii) / 3.3.9）——零容忍，第三方 SDK 中的也不允许。
· 隐藏功能 / 审核时伪装、过审后切换（Guideline 2.3.1）——禁止“挂羊头卖狗肉”。

6. 发布前自检清单（一页版）

SDK 与架构

☐ 构建使用 Xcode 26+ / iOS 26+ SDK（2026 年 4 月起强制）；2027 年 4 月前升级到 iOS 27 SDK。
☐ watchOS 使用 watchOS 26 SDK + arm64 架构（2026 年 4 月起强制），2027 年 4 月前升级到 watchOS 27 SDK。
☐ visionOS 使用 visionOS 26 SDK（2026 年 4 月起强制），2027 年 4 月前升级到 visionOS 27 SDK。
☐ macOS 构建架构为 arm64 only（macOS 27 起 Intel 被彻底移除）。

布局与交互

☐ 布局由 Size Class / 窗口宽度驱动，不按设备型号写死。
☐ iPad 已测试 Split View、Slide Over、Stage Manager 的全部比例。
☐ iPhone Duo 类折叠屏已验证开合连续性，状态不丢失。
☐ macOS 支持窗口精确 resize，菜单栏/快捷键齐全。
☐ watchOS 二进制体积 ≤75MB，触控目标足够大。
☐ visionOS 窗口/volume/ornament 空间位置可预测，无内容遮挡。

签名与分发

☐ App Store 分发使用 App Store 分发证书 + 对应 Provisioning Profile。
☐ macOS 官网直装已走完 Developer ID 签名 → Hardened Runtime → notarytool 公证 → staple 全链条，顺序正确。
☐ 已通过 TestFlight 做真实设备验证；外部测试首个构建已通过 Beta App Review。
☐ 了解 TestFlight 构建 90 天过期，发版节奏与之对齐。

隐私合规

☐ 主应用和所有第三方 SDK 均包含有效的 PrivacyInfo.xcprivacy，且被正确的 target 引用。
☐ 没有任何指纹识别（fingerprinting）行为，包括第三方归因 SDK。
☐ 隐私政策 URL 有效、公开可访问、非 PDF，应用内可到达。
☐ App Privacy 标签与 Manifest、SDK 行为、隐私清单三方一致。
☐ 所有权限描述完整、具体，说明了“用来做什么”。

7. 一句话执行口径

SDK 跟紧时间表（2026 年 4 月 iOS 26，2027 年 4 月 iOS 27；watchOS 64 位 + 75MB 上限）；布局由 Size Class 连续驱动，iPad 测全部分屏比例，macOS 切 arm64；App Store 走分发证书，macOS 官网直装必须签名→公证→staple 一条龙；隐私清单主应用和 SDK 一个不能少，指纹识别零容忍，隐私标签与行为三方对齐，TestFlight 是发版前最后一道真实设备关。