鸿蒙全生态开发与发布要点

这份文档的目标是“照着做不出岔子”。它不只罗列知识点，而是把硬约束、默认陷阱、发布前必检项单独标出来。凡是标注 ⛔ 的，都是踩了会直接导致打包失败、上架驳回或用户装不上的红线。

0. 先记住三条最容易被忽略的“默认陷阱”

⛔ 陷阱一：不声明 tablet / 2in1，不等于不会被分发。
当 deviceTypes 包含 phone 时，即使包里没有声明平板，应用也会以兼容方式默认分发到 NEXT 平板；手机和平板应用测试后，也会默认发布到 PC/2in1。但“兼容分发”不等于原生体验——要进入原生分发并获得大屏能力，必须显式声明 tablet 和/或 2in1，并在 AGC 同步勾选对应设备支持范围。

⛔ 陷阱二：default 能编译，但不能上架。
deviceTypes: ["default"] 可以正常构建，但不支持发布上架。没有明确理由时，用 phone 替代。

⛔ 陷阱三：发布证书只有 3 年，调试证书更短。
实名开发者发布证书 3 年，调试证书 180 天；未实名开发者调试证书仅 14 天。证书到期不影响已上架应用和已安装用户，但更新版本时上传过期证书签名的包会失败。到期前 3 个月开发者中心会发邮件提醒。证书过期后不能延期，需用原有的 .p12 和 .csr 重新申请 .cer 和 .p7b，不要从头生成密钥。

1. 包格式与分发

格式 定位 GitHub 发布怎么用
.hap 鸿蒙安装/运行的基本单元 Releases 的主安装包，必须是已签名的 HAP
.app 应用市场/多 HAP 分发容器 不作为用户主安装包；多 HAP/HSP 场景用于统一签名分发
.apk 安卓包 HarmonyOS NEXT 起不再原生支持
.har / .hsp 开发者共享包 不是面向用户的安装包；HSP 用于共享代码/资源，但注意元服务的 2MB 红线（见 §5）

GitHub 发布结论： 给用户的下载入口直接放已签名的 .hap。不要用 .app 当主安装包，也不要给未签名 HAP。

2. 设备声明：先让包“能装”，再谈“好用”

在 module.json5 的 deviceTypes 中声明目标设备：

```json
"deviceTypes": ["phone", "tablet", "2in1", "wearable"]
```

设备 枚举值 关键说明
手机 phone 基础形态，也是大屏布局的降级基线
平板 tablet 不声明可能以兼容模式分发，体验不到原生大屏
PC/2in1 2in1 多窗口、多任务、键鼠为主的交互形态
智能手表 wearable 系统能力较丰富，具备电话功能的手表
轻量级手表 liteWearable 独立枚举，不能用 wearable 代替；技术栈完全不同（见 §6）
车机 car 独立枚举，不要忽略
默认设备 default ⛔ 可编译但不可上架，建议用 phone 替代

执行口径： 目标设备全部列入 deviceTypes；不用的设备不要随手加，避免分发到未适配形态。

3. 通用布局原则：断点驱动，不按设备型号写死

· 核心依据是实时窗口宽度，不是 deviceType 字符串。
· 关键断点：600vp。
  · <600vp：单栏、底部导航、基础体验。
  · ≥600vp：双栏/多栏、左侧边栏、增加 Grid 列数、提升信息密度。
· 推荐手段：GridRow/GridCol、媒体查询监听窗口宽高和横竖屏、百分比宽度、aspectRatio，避免写死像素和绝对定位。
· 窗口尺寸约束：minWindowWidth/Height、maxWindowWidth/Height、min/maxWindowRatio，运行时可用 setWindowLimits()。
· 大屏布局必须在窄窗口下能优雅回退，否则分屏/悬浮窗一触发就溢出。

4. 各形态适配要点（含硬约束）

📱 手机

· 单栏布局，保证窄窗口可用；注意安全区、状态栏避让、触控点击区域。
· 作为全形态的降级基线：平板/PC/宽屏的任何布局，在窄窗口下都要能回退到这一层。

📟 平板

· 必须声明 tablet，并在 AGC 勾选平板支持范围。
· 重点场景：横竖屏、自由窗口、智慧多窗分屏/悬浮窗。
· 平行视界/分栏常见条件：窗口宽 ≥600vp 且宽高比 >1.2；不满足时降级单栏。
· 悬浮窗比例兼容（如 3:4、3:2）。
· 输入扩展：手写笔压感/按压、鼠标悬停、键盘 Tab 焦点与回车提交。
· 包体积：手机/平板/PC 资源差异大时，用 build-profile.json5 的 targets 做多目标产物，避免全量资源打入同一个 HAP。

📱→💻 Pura X View / 阔直板 / 阔折叠

HarmonyOS 7 时代的新型手机设备（Pura X View 阔直板、阔折叠、双折叠）在屏幕比例和交互体验上区别于传统直板手机。不要按瘦长手机拉伸：

· 按实时窗口宽度断点处理，通常落在 sm/md。
· 大圆角安全区避让：顶部状态栏/底部导航不能被圆角裁切。
· 利用横向空间增加信息密度（多列网格、轮播多内容）。
· 影音场景匹配屏幕比例，减少黑边；支持 M-Pencil 创作场景。
· 如果已完成平板断点响应式，迁移成本较低，但仍需单独回归圆角安全区。

💻 鸿蒙电脑（2in1）

· 必须声明 2in1，并在 AGC 勾选 PC 支持范围；否则可能停留在兼容分发，拿不到原生 PC 能力。
· 真正做大屏响应式布局，不是简单拉伸。
· 配置窗口最小/最大尺寸，防止拖拽到不可用。
· 键鼠适配：悬停、右键菜单、快捷键、滚轮、Tab 焦点顺序。
· 侧载支持，但侧载同样仅支持 HAP 格式。

🔲 折叠屏（不是“另一种平板”）

折叠屏有平板没有的物理约束，必须单独处理：

悬停态（半折叠）

· 首选 FolderStack：通过 upperItems 指定上半屏组件，系统自动分割上下屏并避让折痕区。
· 需要动态避让时，调用 display.getCurrentFoldCreaseRegion() 获取折痕区域位置和大小，计算上下半屏尺寸。
· ⛔ 如果 getCurrentFoldCreaseRegion() 返回为空，不要放任内容压在中线；用合成中线铰链（约 16vp）作为 master/detail 分界兜底。
· 分栏场景可用 FoldSplitContainer；布局复杂或需要自定义触发条件时再走自定义实现。

展开态

· 布局分界同样要读取折痕区域，而不是简单对半切视口。
· 断点判断基于可用窗口宽度，不是 deviceType === tablet。

⌚ 鸿蒙手表：先分清两类，技术栈完全不同

类型 枚举 技术栈 包格式 核心限制
智能手表 wearable ArkTS + ArkUI（Stage 模型） .hap 能力较丰富，支持 ArkWeb
轻量级手表 liteWearable 仅 JS/TS + ArkUI.Lite .hmp / .bin ⛔ 不支持 ArkTS HAP

Lite Wearable 的硬限制（第一天就要确认）：

· ⛔ 不支持 ArkTS HAP：WATCH GT 5 这类设备用 ArkTS 打包的 .hap 安装会报“解压 entry-default-signed.hap 失败”，因为包结构与设备系统不兼容。
· ⛔ 不支持 C/C++ 和 NDK，也不支持动态链接库集成。
· ⛔ 不支持 Promise/async/await 等 ES6 语法，异步写法要改用回调。
· 工程中只能包含 JS 文件，不允许出现 TS 文件，否则构建或运行异常。
· 调试走华为运动健康 + 应用调测助手，不是普通 HAP 的调试方式。

UI 与交互（两类手表通用）：

· 小圆屏必须重构，严格安全边距：圆形屏建议左右 26vp、顶部 20vp、底部 6vp，长列表底部额外留 40vp；方形屏建议左右/顶部 12vp、底部 6vp，底部滑动留 36vp。
· 用 ArcList、ArcSwiper 贴合圆屏；表冠通过 onDigitalCrown 监听旋转。
· 一屏一件事，减少层级，扩大点击区。

体积硬约束：

· 智能手表 HAP ≤2GB。
· 轻量/运动手表 HAP ≤20MB，运行内存建议 <50MB，无后台常驻。

5. 元服务：独立红线，和普通 HAP 不是一套规则

如果你的应用包含元服务形态，以下限制是硬约束，不能靠“优化一下”绕过去：

约束 数值 后果
单个包（含其 dependency 依赖的所有 HSP） ≤2MB ⛔ DevEco Studio 直接打包失败
同一设备类型下所有包总和 ≤10MB ⛔ 上架应用市场失败

关键执行点：

· HSP 不能突破 2MB：元服务的每一个包（包括 HSP）都要单独满足 2MB 限制，不能靠“把东西塞进 HSP”规避。
· 正确拆分方式：将非首屏 Tab 的业务模块拆到 HSP，并从 entry 模块的 oh-package.json5 中移除对 HSP 的静态依赖，使该 HSP 体积不计入首包 2MB。
· 元服务包内所有 HAP 必须是免安装类型（Stage 模型 bundleType: "atomicService"）。
· 同一设备类型下只允许一个 entry 包，可以有 0–N 个 feature/HSP。

6. 工程化：控体积与构建

· 多目标产物是控体积的第一手段：build-profile.json5 的 targets，每个 target 只打入对应设备资源。
· 开启 compressNativeLibs: true 压缩 SO 库。
· 用 HSP 替代 HAR 共享公共代码/资源，避免多包重复打包；但元服务场景注意 §5 的 2MB 红线。
· 低频功能做动态按需加载（import()）。
· ⛔ 元服务不可包含 SO 文件：API Level ≥11 时，元服务只能使用“元服务 API 集”，不允许 C API，包内不能存在 SO 文件。

7. 签名方式：GitHub 发布专用

7.1 核心原则

· 调试签名（DevEco Studio 自动签名）绑定本机，不能用于公开分发。
· 发布签名是 GitHub Release 的唯一正确选择，搭配固定发布证书和发布 Profile。
· 签名材料绝不提交到 Git 仓库：.p12、.cer、.p7b 及密码放 GitHub Secrets。

7.2 手动签名（基础推荐）

使用 DevEco Studio 自带的 hap-sign-tool.jar：

```bash
java -jar hap-sign-tool.jar sign-app \
  -keyAlias "你的密钥别名" \
  -signAlg "SHA256withECDSA" \
  -mode "localSign" \
  -appCertFile "发布证书.cer" \
  -profileFile "发布Profile.p7b" \
  -inFile "未签名.hap" \
  -keystoreFile "密钥库.p12" \
  -outFile "签名后.hap" \
  -keyPwd "密钥密码" \
  -keystorePwd "密钥库密码"
```

7.3 GitHub Actions 自动化（进阶）

推荐使用社区流水线 ohosvscode/harmony-next-pipeline，自动完成 构建 → 签名 → 上传 Release。签名材料通过 Secrets 注入，常见 Secrets 命名：

· HARMONY_STORE_FILE_BASE64
· HARMONY_PROFILE_BASE64
· HARMONY_CERT_FILE_BASE64
· HARMONY_KEY_PASSWORD
· HARMONY_STORE_PASSWORD

7.4 CI 密钥安全要求

· .p12 等文件 base64 编码后存入 Secret，CI 中解码到临时目录，用完即删，绝不打印到日志。
· Secret 只暴露给真正消费它的 step，不要注入整个 job。
· 生产签名密钥应加 environment gate；GitHub Actions artifact 默认保留 90 天，不要让签名中间产物被误上传。

7.5 证书生命周期管理

· 发布证书 3 年，调试证书 180 天（实名）/14 天（未实名）。
· 到期前 3 个月有邮件提醒；更新版本时过期证书签名会失败，建议建立到期监控。
· 过期后用原有 .p12 + .csr 重新申请 .cer/.p7b，不要重新生成密钥，否则指纹变化会影响在架应用。

8. 隐私合规：上架前的独立关卡

GitHub 侧载发布不经过应用市场审核，但如果后续要上架，以下必须在发布前规划好。隐私合规是鸿蒙上架驳回的最高频原因，其中两条是 TOP 级：

⛔ TOP1：用户同意隐私政策前获取个人信息或权限。
所有个人信息获取行为必须在用户同意后触发；隐私弹窗之前不得弹权限框、不得收集信息。

⛔ TOP2：申请了权限但未提供必须且有效的使用场景。
遵循权限最小化；在 module.json5 的 requestPermissions 中声明使用场景，reason 必须写清楚“为什么用”（例如“用于查找附近网点，需要访问定位权限”），不能只写“需要定位”。

其他高频项：

· 隐私政策必须以明示同意方式获得，不得默认勾选、登录即同意、无拒绝选项。
· 隐私政策中的开发者名称、应用名称必须与 AGC 提交信息完全一致。
· 存在个人信息收集行为时，必须在 AGC 勾选并填写隐私标签，明确使用场景和目的。

9. 发布前自检清单（逐项打勾）

设备与分发

☐ deviceTypes 覆盖所有目标设备，未使用 default。
☐ 平板：声明 tablet，AGC 已勾选平板支持范围。
☐ PC：声明 2in1，AGC 已勾选 PC 支持范围。
☐ 折叠屏：FolderStack / FoldSplitContainer 已处理悬停态；折痕区域读取了 getCurrentFoldCreaseRegion()，空值有 16vp 兜底。
☐ 手表：若目标含 GT/Fit 系列，确认走 JS/TS + .hmp/.bin，没有误用 ArkTS HAP。

布局与交互

☐ 600vp 上下均有可用布局；分屏、悬浮窗、横竖屏均回归。
☐ 平板/PC 设置了合理的最小/最大窗口尺寸。
☐ 键鼠、手写笔、表冠按设备补齐。
☐ Pura X View 类宽屏单独验证了圆角安全区和信息密度。

包体积

☐ 普通 HAP：用多目标产物按设备拆资源，未把全量资源打进一个包。
☐ 元服务：单包（含 HSP 依赖）≤2MB，总和 ≤10MB；非首屏模块已从 entry 静态依赖中剥离；包内无 SO 文件。
☐ 轻量手表：HAP ≤20MB，无后台常驻；智能手表 HAP ≤2GB。

签名与发布

☐ 使用发布证书 + 发布 Profile签名，未混用调试证书。
☐ .p12/.cer/.p7b 和密码在 GitHub Secrets，未进 Git 仓库。
☐ CI 中签名材料解码到临时目录，用完即删，未打印日志。
☐ 已确认发布证书剩余有效期，并有到期监控。
☐ GitHub Release 提供的是已签名 HAP，不是 .app 或未签名包。

上架合规（如后续上架）

☐ 隐私政策在收集信息/弹权限前展示，用户主动勾选同意。
☐ requestPermissions 中每个权限都有明确的 reason 使用场景。
☐ AGC 隐私标签已填写；隐私政策中的开发者/应用名称与 AGC 一致。

10. 一句话执行口径

HAP 是安装单元，.app 是分发容器；deviceTypes 必须覆盖目标设备且不用 default；600vp 断点驱动布局，平板重分屏、PC 重窗口与键鼠、折叠屏重折痕避让、宽屏重圆角、手表先确认是 wearable 还是 liteWearable；元服务死守 2MB/10MB，轻量手表死守 JS 和 20MB；公开分发只给发布签名的 HAP，签名材料放 Secrets，证书到期用原 .p12/.csr 续，隐私合规在上架前单独过一遍。