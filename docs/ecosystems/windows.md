Windows 全生态开发与发布要点

目标：照着做不出岔子。标注 ⛔ 的条目，踩了会直接导致Store 审核驳回、安装包被 SmartScreen 拦截、ARM64 设备体验崩塌，或已发布应用无法正常更新。

0. 四条“沉默的杀手”

⛔ 杀手一：EV 证书不再绕过 SmartScreen，这是 2024 年以来最大的认知更新。
过去“买 EV 证书 → 首次下载无警告”的路径已经失效。Microsoft 官方明确：EV 证书不再影响 SmartScreen 行为，仅为规避警告而购买 EV 已无理由。SmartScreen 现在是声誉驱动的：已签名（OV/EV）文件在积累足够下载量和行为证据之前，首次下载仍会显示“无法识别的应用”，通常需要数周时间和数百次全新安装才能建立信誉。唯一能完全避免 SmartScreen 警告的路径是 Microsoft Store 分发，因为 Store 会用 Microsoft 证书重新签名。

⛔ 杀手二：WinUI 3 的高 DPI 和多显示器适配需要手动处理，不是“自动搞定”。
WinUI 3 对单显示器 DPI 变化有较好的自动缩放能力，但跨不同 DPI 显示器拖拽窗口时，默认不会自动响应 DPI 变化，可能导致界面模糊或布局错乱。AppWindow.MoveAndResize 使用的是物理像素，而 WinUI 布局使用设备无关像素，两者混用会在 125%、150% 等缩放下产生尺寸偏差。必须在窗口初始化时显式声明 Per-Monitor V2 DPI awareness，并对跨屏 DPI 变化做手动重算。

⛔ 杀手三：Windows on ARM 不是“能跑就行”，原生 ARM64 是默认期望。
x64 应用可以通过 Prism 模拟层在 ARM 设备上运行，但模拟层带来性能损耗和电池续航下降。Microsoft Store 支持在同一个提交中捆绑 x64 和 ARM64 包，Store 会自动向对应设备分发。仅提交 x64 包意味着 ARM 用户只能跑模拟版本，在 Surface Pro（Snapdragon）等设备上体验明显劣化。Arm 已推出 AppReady for Windows 计划，帮助排查依赖项、构建系统和架构相关代码。

⛔ 杀手四：Windows 10 已于 2025 年 10 月 14 日结束支持。
新项目不应再以 Windows 10 为目标平台。Windows 10 的公开安全更新已终止，仅 ESU（扩展安全更新）覆盖到 2026 年 10 月 13 日。WinUI 3 和 .NET MAUI 均不支持 Windows 7，最低要求是 Windows 10 版本 1809+，但 2026 年及以后的新项目应以 Windows 11 为设计和测试基线。

1. 技术栈选择：一张表定方向

场景 推荐栈 关键约束
Windows 原生，全量 WinUI 3 能力 WinUI 3 + Windows App SDK 官方主推的原生 UI 框架，Fluey Design，支持 ARM64；当前稳定版 Windows App SDK 2.5.1（2026 年 9 月）
跨平台（Windows + iOS + Android + macOS） .NET MAUI Windows 上输出即 WinUI 3；不适合“先 WinUI 3 再追加 MAUI”的反向路径
Web 技术栈团队，桌面原生体验 React Native for Windows 适合有 JavaScript/TypeScript/React 经验的团队，直接渲染原生元素
老旧系统兼容（Win7/10） WPF / Windows Forms WinUI 3 和 MAUI 都不支持 Win7；WPF/WinForms 是唯一选择
Xbox UWP（注意 Xbox 专属限制） 游戏用 Microsoft 游戏开发套件
Surface Hub UWP（Teams Rooms / Hub 环境） Surface Hub 3 配置为 Win11 Pro/Enterprise 时可运行桌面应用技术

执行口径： 新 Windows 原生项目默认 WinUI 3 + Windows App SDK 2.5.x；跨平台团队从 .NET MAUI 或 React Native for Windows 起步，不要先写 WinUI 3 再回头加跨平台。WinUI 3 组件可以直接嵌入经典 Win32 应用，混合场景可行。

2. 打包与分发：MSIX 优先，EXE/MSI 作为备选

2.1 Microsoft Store 提交

推荐格式：MSIX（.msix / .msixbundle / .msixupload / .appx / .appxbundle / .appxupload）。MSIX 的好处包括：免费的 Microsoft 代码签名与 CDN 托管、更简单的更新、更好的 Windows 功能集成、支持飞行和商务等高级功能。

EXE/MSI 也接受，但约束更严格：

· 必须在提交时提供安装程序的包 URL，安装程序托管在自己的基础设施或 CDN 上。
· 必须是离线安装程序，安装过程中不得下载。
· 安装程序不得在提交后变更或捆绑不相关软件。

执行口径： 能上 MSIX 就上 MSIX。需要 EXE/MSI 时，确保安装程序是离线、自托管、提交后不变。

2.2 Store 外分发

格式 适用 关键要求
MSIX 独立下载、企业侧载 用受信任 CA 的证书签名即可直接双击安装，无需 Store
EXE/MSI 传统安装程序 必须用有效证书签名；未签名或自签名不推荐公开分发
.dmg/pkg 不适用 Windows —

3. ARM64 适配：原生优先，捆绑提交

构建目标： 在 Visual Studio / CI 中同时生成 x64 和 ARM64 产物。如果依赖项全部有 ARM64 原生版本，纯 .NET 应用可以做到原生 ARM64 构建，无模拟开销。

Store 提交策略： 在同一个提交中上传 x64 和 ARM64 包（或使用 .msixbundle），Store 自动按设备架构分发。不要只提交 x64 而期望 ARM 用户自动获得可接受体验——模拟层能跑，但不是目标体验。

迁移支持： 使用 Arm AppReady for Windows 检查关键依赖项、构建系统、编译器设置和安装程序插件，识别与架构相关的代码和性能关键路径。AI 智能体也可以辅助将 x86 代码转换为 ARM64 原生。

4. 代码签名与 SmartScreen：认知必须更新

4.1 签名不是“为了绕过警告”，而是“为了有资格积累信誉”

未签名文件：SmartScreen 信誉每个新版本从零开始，无法从旧版本继承。用户看到“Windows 保护你的电脑”，必须手动选择“仍然运行”。

已签名文件（OV/EV） ：首次下载仍可能显示“无法识别的应用”，但会显示发布者名称，并随下载量积累逐步建立信誉。使用一致的签名标识至关重要：更改证书会切断发布者信任信号。

Store 分发：唯一能完全避免 SmartScreen 警告的路径。

4.2 签名工具选择

方式 适用 SmartScreen 行为
Microsoft Store 希望零警告 ✅ 无警告（Microsoft 证书签名）
Azure Artifact Signing（原 Trusted Signing） 非 Store 分发的推荐 CI/CD 方案 从 $9.99/月起，无需硬件令牌，与 GitHub Actions / Azure DevOps 集成，信誉随时间积累
OV 证书（受信任 CA） 传统签名 首次警告，发布者名称可见，信誉逐步积累
EV 证书 企业采购/内部规范要求 不再影响 SmartScreen 行为，仅为规避警告购买已无理由
自签名 仅限内部测试 与未签名相同，公开分发不推荐

CI 安全红线：

· 签名证书（.pfx）和密码绝不进 Git 仓库，放 GitHub Secrets / Azure Key Vault。
· 优先使用 Azure Artifact Signing 等无硬件令牌的签名服务，避免 CI 中管理物理 USB 令牌。
· 不要在签名后修改文件——会中断签名，导致信任失效。

5. Windows AI：分层选择，按硬件匹配

2026 年的 Windows AI 分为三层，不是“一个 API 打天下” ：

层级 适用硬件 适用场景 关键约束
Windows AI APIs Copilot+ PC（NPU ≥40 TOPS） 为大多数 API 的硬性前提 OCR、图片描述、文本摘要、Phi Silica 聊天，单次 API 调用，无模型管理 需要 NPU；部分 API（GPU 上的 Phi Silica、语音识别、视频超分辨率）也支持非 Copilot+ 的 Win11 硬件
Foundry Local 任何具有 DirectX 12 GPU 的 Windows 设备，也包括无 NPU 设备 20+ 开源 LLM / 语音模型，OpenAI 兼容 REST API，可直接复用现有 OpenAI SDK 代码 原生 SDK（C#/Python/JS/Rust）目前 alpha/预发布，生产应用应固定包版本并视 API 为可能变动
Windows ML CPU / GPU / NPU 广泛硬件 自带 ONNX 模型，完全控制推理管道，硬件加速 优先级：NPU → GPU → CPU 自动选择；NPU 需要 Windows 11 24H2+

Copilot+ PC 硬门槛： NPU 算力 ≥ 40 TOPS，16GB+ RAM。不要假设用户设备都是 Copilot+ PC——Foundry Local 和 Windows ML 是覆盖普通硬件的兜底路径。

离线能力： Foundry Local 模型下载并缓存后完全本地推理，无云依赖；启动时可能尝试刷新目录，但离线时回退到缓存目录正常推理。

术语提醒： “Copilot Runtime”“Windows Copilot Runtime”“Windows AI Foundry”都是旧名称，当前统一伞品牌为 Microsoft Foundry on Windows，覆盖 Windows AI APIs + Foundry Local + Windows ML。

6. Microsoft Store 提交与合规

6.1 提交前必须准备的元数据

字段 要求
应用名称 必须唯一，可在发布前最多 3 个月保留名称
隐私政策 URL 收集个人数据时必须提供；链接必须是隐私政策页面本身，不能是主页、PDF 或失效链接
截图 每个设备系列至少 1 张，建议 5–8 张高质量截图
商店徽标 必需，建议提供多分辨率版本
搜索词 最多 7 个，每个 30 字符，不要用品牌名或“免费”“最佳”等通用词

6.2 高频拒审原因

⛔ 隐私政策无效/不相关（Policy 10.5.1） ：Partner Center 隐私政策字段为空、链接指向主页而非政策页面、政策过于单薄或过期，是 Microsoft Store 最常见的合规失败原因之一。

⛔ 隐私申报与实际行为不一致：采集了未在申报中声明的数据，或申报的数据类型与实际数据流不符，会导致审核驳回或后续更新被阻止。

⛔ 同意弹窗与数据请求顺序错误：审计中最常见的发现之一是“同意弹窗尚未关闭，第一条广告/数据请求已经发出”。同意必须在任何数据收集之前获得，且拒绝必须和接受一样容易，不能使用预勾选复选框或“继续使用即代表同意”的墙。

⛔ 演示账号缺失或不可用：审核员需要长期有效的演示账号进入主流程，且必须写在提交备注的第一行。

6.3 提交前合规自查

☐ Partner Center 隐私政策 URL 有效，指向具体政策页面，非主页/PDF，与应用内展示一致。
☐ 应用收集的任何个人数据均已逐项在申报中体现，与数据流图对齐。
☐ 同意弹窗在任何数据请求之前展示；拒绝与接受同样容易。
☐ 如有账号体系，审核用长期有效演示账号已提供并写在备注首行。
☐ 截图数量（每个设备系列 ≥1，建议 5–8）和商店徽标已准备。
☐ 搜索词 ≤7 个，每个 ≤30 字符，无误导性或品牌侵权词。

7. 发布前自检清单（一页版）

技术栈与构建

☐ 新 Windows 原生项目使用 WinUI 3 + Windows App SDK 2.5.x。
☐ 跨平台项目从 .NET MAUI 或 React Native for Windows 起步，未采用“先 WinUI 3 再加跨平台”的反向路径。
☐ 目标系统为 Windows 11，未在 Windows 10 已 EOL 的平台上新增功能。

打包与架构

☐ Store 提交使用 MSIX；如用 EXE/MSI，安装程序离线、自托管、提交后不变。
☐ x64 和 ARM64 包在同一提交中捆绑，Store 可向 ARM 设备分发原生版本。
☐ 已使用 Arm AppReady 或等效手段排查 ARM64 依赖项和架构相关代码。

签名与 SmartScreen

☐ 不再以 EV 证书作为绕过 SmartScreen 的手段；理解 SmartScreen 为声誉驱动。
☐ 签名证书未进 Git；CI 中签名材料临时使用、用完即删。
☐ 签名后未修改文件；跨版本使用一致的签名标识。
☐ 如追求零警告，优先走 Microsoft Store；非 Store 分发考虑 Azure Artifact Signing。

高 DPI 与多显示器

☐ 应用已声明 Per-Monitor V2 DPI awareness。
☐ 跨不同 DPI 显示器拖拽窗口时，布局手动响应 DPI 变化，未依赖默认行为。
☐ 窗口尺寸 API（AppWindow.MoveAndResize）未与设备无关像素混用。

AI 集成（如涉及）

☐ 已确认目标用户硬件：Copilot+ PC（NPU ≥40 TOPS） 才走 Windows AI APIs；普通硬件用 Foundry Local 或 Windows ML。
☐ Foundry Local 原生 SDK 视为可能变动的接口，已固定包版本。
☐ 离线场景已验证模型缓存和回退行为。

Store 合规

☐ 隐私政策 URL 有效、指向政策页面、与应用内一致。
☐ 数据申报与实际行为逐项对齐。
☐ 同意弹窗在任何数据请求之前展示；拒绝与接受同等容易。
☐ 演示账号（如需）长期有效，已写入提交备注首行。

8. 一句话执行口径

原生 WinUI 3 + Windows App SDK 2.5，跨平台从 MAUI 或 React Native 起步；Store 首选 MSIX，EXE/MSI 必须离线自托管；x64 + ARM64 捆绑提交，ARM 原生优先；EV 不再绕过 SmartScreen，零警告只有 Store，非 Store 用 Artifact Signing 并接受声誉积累期；WinUI 3 高 DPI 多屏需手动处理 Per-Monitor V2 和 DPI 变化；AI 按硬件分层——Copilot+ 用 Windows AI APIs，普通硬件用 Foundry Local 或 Windows ML；隐私政策 URL 必须指向政策页面本身，同意在数据请求之前，拒绝与接受同等容易。