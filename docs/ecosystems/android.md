安卓全生态开发与发布要点

目标：照着做不出岔子。以下所有标注 ⛔ 的条目，都是踩了会直接导致 Google Play 拒审、上架失败，或已发布应用无法更新的红线。

0. 先记住五条“沉默的杀手”

⛔ 杀手一：Target API 不够，连提交入口都进不去。
2026 年 8 月 31 日起，新应用和应用更新必须 target Android 16（API 36）或更高才能提交 Google Play。例外：Wear OS 和 Android Automotive OS 应用 target API 35 即可，不要盲目对齐手机端。

⛔ 杀手二：16 KB 页面大小，只影响有原生库的应用，但一旦触发就是硬拦截。
如果应用包含任何原生代码（.so），其 64 位原生库必须按 16 KB 对齐。从 2025 年 11 月 1 日起，target API 35+ 且原生库未对齐 16 KB 的应用，Google Play 直接拒绝更新。即使你的代码改了，如果依赖的某个第三方 SDK 仍以 4 KB 对齐打包，Play Console 仍然会标记不通过——需要逐项排查依赖链中的原生库。

⛔ 杀手三：Android 17（API 37）对大屏设备的“方向/尺寸限制”选择退出了。
Android 16 曾提供临时 opt-out，允许应用在大屏上继续锁定方向或禁止 resize。target API 37 时这个 opt-out 被移除。如果你的应用在大屏（sw > 600dp）上仍依赖 screenOrientation 锁定或 resizeableActivity=false，在 API 37 下会直接失效，布局必须在不锁方向、可自由 resize 的前提下可用。

⛔ 杀手四：Wear OS 的 64 位要求有独立时间表。
2026 年 9 月 15 日起，包含原生代码的 Wear OS 应用在 Google Play 上除 32 位版本外还必须提供 64 位版本（仅 64 位不违规，32 位支持政策不变）。不要因为“手表包小”就跳过这条。

⛔ 杀手五：上传密钥 ≠ 应用签名密钥。
Google Play 使用两把密钥：开发者持有上传密钥签名 .aab，Google 持有应用签名密钥为最终用户签名 APK。弄混会导致“Play App Signing 已启用但无法上传”或“密钥丢失后无法恢复”。

1. 包格式与分发形态

格式 定位 执行口径
.aab Google Play 生产发布的标准格式 Play 发布一律用 AAB，不要用 APK 当主分发格式。AAB 让 Play 按设备生成优化 APK，体积更小，也是 Play App Signing 和动态交付的前提
.apk 侧载/第三方商店/本地测试 仅在 GitHub Releases、F-Droid、企业内部分发等不走 Play 的场景使用；Play 上已发布应用不要用 APK 做更新主渠道
.apks 本地从 AAB 生成的设备专用 APK 集合 仅用于本地测试，不用于上架

2. 通用布局原则：以窗口为唯一依据，不以设备名为依据

Android 17 将“自适应优先”确立为标准。用户全天在手机、折叠屏、平板、笔记本、车机屏幕和 XR 之间切换，布局必须响应运行时窗口的可用空间，而不是 deviceType 字符串。

核心手段（Jetpack Compose 优先）：

· WindowSizeClass（来自 WindowManager 库） ：将可用窗口宽度分为 compact / medium / expanded，这是判断“何时切双栏、何时加列”的标准依据，不是按物理设备尺寸。分屏、多任务下物理尺寸不变，窗口却可能骤降，按设备名判断必然出错。
· 折叠边/铰链作为自然分隔符：WindowSizeClass 与折叠姿态结合，让双栏分界落在折痕或铰链上，而不是视口对半切。Notability 的做法是典型参考：用 Material 3 Window Size Classes 做响应式双窗格，在折叠/展开之间平滑过渡。
· ViewModel 保住配置变更中的状态：折叠/展开、旋转、resize 都会触发 Activity 重建。滚动位置、表单输入、打开的对话框必须通过 ViewModel 或 rememberSaveable 保留，否则用户一展开屏幕，页面就“跳回顶部”。
· 大屏底线：sw > 600dp 时，不要锁定方向，不要禁止 resize。target API 37 下这两类限制不再有 opt-out。

3. 各形态执行要点

📱 手机 + 折叠屏 + 平板（同一套 AAB，同一套自适应布局）

不要为三种形态维护三套独立逻辑。正确做法是一套 Compose 自适应布局 + WindowSizeClass 驱动，在 compact 下是底部导航/单栏，在 medium/expanded 下切为 NavigationRail/双栏/多列 Grid。

折叠屏专项（最容易翻车的地方）：

· 外屏到内屏的切换不是简单“变大”：外屏 compact，展开后 inner display 通常 expanded。必须用 WindowSizeClass 监听窗口宽度变化，而不是在 onCreate 里读一次屏幕尺寸就固定布局。
· 折痕/铰链区域不能承载关键交互：双栏分界应对齐折叠边/铰链，不要把可点击控件压在折痕上。
· 后置/悬停姿态：部分折叠屏支持半折叠悬停（tabletop）。如果应用有视频播放、拍照、视频通话场景，考虑悬停布局（上半屏预览、下半屏控制），但不是必选项，优先级低于“展开后布局可用”。
· screenOrientation 在大屏上不要再依赖：见 §0 杀手三。

平板/Chromebook 最低要求：

· 在 sw > 600dp 的窗口下，所有主要页面均可到达，不出现“只能竖屏看”的页面。
· 横屏/分屏下不出现内容截断、按钮被挤出可视区域。
· 如果应用声明了 supports-screens 或 resizeableActivity，确保与自适应布局一致，不要人为把大屏挡在外面。

⌚ Wear OS（不要当成“小屏手机”）

Wear OS 是独立 target 体系，Google Play 对 Wear OS 单独审核、单独 API 要求、单独体积预期。

包与 target：

· Wear OS 应用的 target API 下限是 API 35，不是手机的 API 36。
· 包含原生代码时，2026 年 9 月 15 日起除 32 位外必须提供 64 位（仅 64 位不违规）。

体积与内存（直接决定“能不能装、愿不愿意下”）：

· 典型 Wear OS 设备 RAM 仅 1–2 GB，用户对包体极其敏感。超过 5 MB 的应用会显著影响下载意愿，这不是 Play 的硬性红线，但实质上是质量红线。
· Watch Face 格式有硬内存限制：环境模式 ≤10 MB，交互模式 ≤100 MB。
· 建议为手表单独建 module/变体，不要把手机端全部代码和资源带入手表包。手表包越小，安装成功率越高。

UI 与交互：

· 圆形屏为主，触控目标至少 48 × 48 dp；启动图标在黑色背景上以 48 × 48 dp 展示。
· 交互以扫视（glanceability） 为第一原则：一屏一件事，减少层级，避免把手机列表原样缩到手表上。
· 支持旋转表冠、物理按键、语音等输入方式，但不要把表冠做成唯一入口。

📺 Android TV

· 2026 年 8 月 1 日起，TV 应用必须支持 64 位架构和 16 KB 页面大小。有原生库的 TV 应用需要在此前完成对齐。
· TV 的 target API 与手机同一套 Play 规则（API 36），但 TV 有独立的设备目录和审核清单，上架前确认应用已声明 Leanback launcher 等 TV 必需组件。
· TV 交互以方向键焦点导航为核心，不能只依赖触控。每个可聚焦元素必须有清晰的焦点态。

🚗 Android Automotive OS（AAOS）

· AAOS 应用的 target API 下限同样是 API 35，与手机分开。
· AAOS 是独立于 Android Auto 的车载系统：AAOS 应用直接跑在车机上，不依赖手机；Android Auto 是把手机内容投射到车机屏，两者不是一回事。
· 设计必须遵循 “Design for Driving” 原则：驾驶中禁止复杂文本输入和长时间视频播放；可交互元素要足够大，减少驾驶分心。车机应用上架前需要满足 Play 的驾驶分心（Driver Distraction） 要求，不是普通手机 UI 直接搬过去就能过。

🥽 Android XR

· 现有 2D 移动应用默认可以在 Android XR 设备上运行，前提是不依赖 XR 不支持的硬件能力（如 telephony）。不需要为了“能装”而从零重写。
· 但“能运行”不等于“原生体验”。如果希望在 XR 设备上获得更好的展示，至少达到 Android XR 质量指南的 Tier 2 支持水平，按指南检查 2D 内容在头显/眼镜中的可读性和窗口行为。
· 沉浸式/空间体验需要 Jetpack XR SDK（SceneCore、Depth Map、眼动/手部追踪等），这是可选能力；本项目执行优先级是：先保证 2D 自适应布局在 XR 上可用，再考虑空间化。

4. 签名：Google Play 场景的唯一正确姿势

4.1 两把密钥，各司其职

密钥 谁持有 用途
上传密钥（upload key） 开发者 签名上传到 Play Console 的 .aab 或 .apk，用于验证身份
应用签名密钥（app signing key） Google（Play App Signing） Google 用这把密钥为最终用户签 APK，用户设备上安装的是这把密钥签的包

执行口径：

· Play 发布必须启用 Play App Signing。不要试图自己用应用签名密钥签 AAB 再上传——那套流程已经过时，且拿不到 Play 的动态交付能力。
· 上传密钥丢失或泄露，可以在 Play Console 请求重置，因为应用签名密钥在 Google 侧，不会导致应用无法更新。这是 Play App Signing 的核心价值之一。
· 但上传密钥仍要妥善保管：重置需要走验证流程，会耽误发版节奏。

4.2 签名命令（CI/本地手动场景）

APK（侧载/非 Play 渠道）：

```bash
# 先 zipalign，再 apksigner
zipalign -p -f 4 input.apk aligned.apk
apksigner sign --ks release.keystore --ks-pass env:STORE_PASS \
  --key-pass env:KEY_PASS --out signed.apk aligned.apk
```

AAB（上传到 Play，由 Play App Signing 接管最终签名）：

```bash
# AAB 用 jarsigner 做上传签名即可（Play 上传密钥认证只要求 v1/JAR signing）
jarsigner -keystore release.keystore -storepass:env STORE_PASS \
  -keypass:env KEY_PASS -signedjar signed.aab unsigned.aab upload
```

⛔ CI 安全红线：

· .keystore / .jks 绝不进 Git 仓库。放 GitHub Secrets / 企业级密钥管理（Vault、KMS）。
· CI 中解码到临时目录，用完即删，不打印到日志。
· 生产签名密钥加 environment gate，不要任何 PR 构建都能触碰到发布密钥。

5. 隐私合规：Google Play 的核查是一条“链条”，不是一份表单

Google Play 2026 的审核重点已经从“有没有恶意行为”转向声明与实际行为的一致性。很多拒审不是因为代码有 bug，而是因为以下链条中某一环对不上。

Google 实际交叉核查的链条：

```
Manifest 权限 → 第三方 SDK 实际行为 → 网络请求 → Data Safety 表单 → 隐私政策
```

⛔ 最高频拒审原因 TOP 3：

1. Data Safety 表单与实际行为不一致。
不是“填了就行”。如果 Manifest 声明了位置权限、某个第三方 SDK 在后台读取了设备标识符，但 Data Safety 里没有体现，Google 会判定为“数据安全声明不准确”。

2. 权限最小化不过关。
以下权限是审核重点：通讯录、精确位置、后台定位、READ_PHONE_STATE、QUERY_ALL_PACKAGES、通知权限、前台服务。逐项问四个问题：真的需要吗？有没有替代方案？能不能延迟申请？是不是用户主动触发？给不出合理解释的，删掉。

3. 隐私政策 URL 无效或不可访问。
必须是一个活跃的、公开可访问的、非地理限制的 URL，不能是 PDF，不能是“登录后才能看”。隐私政策链接必须同时存在于 Play Console 指定字段和应用内部。

发布前合规自查（逐项打勾）：

☐ Manifest 权限已逐项复核，敏感权限有明确使用场景。
☐ 第三方 SDK 已升级到最新合规版本，并核对其原生库是否 16 KB 对齐（见 §0 杀手二）。
☐ Data Safety 表单已按“Manifest + SDK 行为 + 网络请求”的实际数据流填写，不是凭记忆填。
☐ 隐私政策 URL 活跃、可公开访问、非 PDF、与应用内展示一致。
☐ Target SDK 满足 API 36（手机）/ API 35（Wear OS、AAOS）。
☐ 如涉及广告，确认广告 ID 使用符合 Play 政策；儿童/家庭类应用有额外限制。

6. 时间节点速查（发布排期用）

时间 要求 影响范围
2025-11-01 起 target API 35+ 的应用，64 位原生库必须 16 KB 对齐 所有含原生库的应用
2026-08 起 Android TV 应用必须支持 64 位（16 KB 对齐按 2025-11-01 通用条款，TV 无单独强制日） TV
2026-08-31 起 新应用/更新必须 target API 36（手机）；Wear OS / AAOS target API 35 全生态
2026-09-15 起 含原生代码的 Wear OS 应用，除 32 位外还必须提供 64 位（仅 64 位不违规） Wear OS
target API 37 生效后 大屏（sw > 600dp）的方向/尺寸限制 opt-out 被移除 手机/折叠屏/平板

7. 发布前自检清单（一页版）

构建与包

☐ 生产发布使用 AAB，不是 APK。
☐ 含原生库的应用已确认 16 KB 对齐，包括所有传递依赖的第三方 SDK。
☐ Wear OS 包已单独瘦身，未把手机全量代码/资源带入。
☐ TV 应用已满足 64 位 + 16 KB。

适配

☐ 布局由 WindowSizeClass 驱动，不依赖 deviceType 或物理设备名。
☐ sw > 600dp 下未锁定方向、未禁止 resize。
☐ 折叠屏展开/折叠、旋转、分屏 resize 后，状态通过 ViewModel 保留。
☐ 圆形 Wear OS 屏上触控目标 ≥ 48×48 dp；TV 有清晰的焦点态；AAOS 符合驾驶分心原则。

Target API

☐ 手机/通用应用 target API 36。
☐ Wear OS / AAOS target API 35，且未遗漏 64 位/16 KB 的独立时间表。

签名

☐ 已启用 Play App Signing。
☐ 上传密钥由开发者持有，未提交到 Git，CI 中临时使用后即删。
☐ 未把上传密钥和应用签名密钥的角色混淆。

隐私合规

☐ Data Safety 表单与实际数据流一致，不是凭记忆填。
☐ 敏感权限逐项复核，无法合理解释的已删除。
☐ 隐私政策 URL 活跃、可公开访问、非 PDF，且应用内可到达。

8. 一句话执行口径

手机/折叠屏/平板用一套 AAB + WindowSizeClass 驱动，大屏不锁方向；Wear OS 单独瘦身、盯 64 位和 16 KB；TV 和 AAOS 有独立时间表；XR 先保 2D 可用再谈空间化；签名用上传密钥签 AAB、Play App Signing 管应用密钥；Target API 手机 36、Wear/AAOS 35；隐私合规按“权限→SDK→网络→Data Safety→隐私政策”整条链对齐。