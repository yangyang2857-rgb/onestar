Web 全生态开发与发布要点

Web 与前面几个生态最大的不同在于：没有“提交审核”这个统一闸门，也就没有人为你兜底。一个错误的 Cache-Control、一条过宽的 CSP、一次未回滚的发布，会直接触达全部线上用户。以下标注 ⛔ 的条目，踩了会导致安装失败、安全防线形同虚设、性能不达标影响搜索排名，或发布后无法回滚。

0. 五条“沉默的杀手”

⛔ 杀手一：HTTPS 不是“最好有”，是安装和安全的绝对前提。
PWA 的安装性要求、Service Worker 注册、SameSite=None Cookie 的 Secure 标志，全部以 HTTPS 为硬前提。HTTP 下不仅 PWA 无法安装，现代浏览器还会将非 Secure 的跨站 Cookie 直接丢弃。本地开发用 localhost 豁免，但任何非 localhost 的 staging/production 域名必须全站 HTTPS，不允许“后台 HTTPS、静态资源 HTTP”的混合内容。

⛔ 杀手二：PWA 安装性的硬门槛在 manifest，不在 Service Worker。
Chromium 浏览器的可安装性标准要求 manifest 必须包含：name 或 short_name、192px 和 512px 图标、start_url、display（或 display_override）。从 Chrome 移动版 108 和桌面版 112 起，不再要求 Service Worker 实现 fetch() 方法才能从菜单安装。但注意：这不等于可以不要 Service Worker——离线能力和推送仍然依赖它，只是它不再是“能装”的硬性门槛。

⛔ 杀手三：Baseline “广泛可用”是 30 个月互通，不是“新功能就能直接用”。
Baseline 的“Widely available”定义为：在核心浏览器集（Chrome、Edge、Firefox、Safari 的桌面和移动版本）中互通性至少达到 30 个月。一个功能“Newly available”只代表刚进入核心浏览器集，不意味着可以安全使用而不考虑回退。必须用 Browserslist 的 Baseline 查询锁定目标，而不是凭感觉判断“这个 API 应该都支持了”。

⛔ 杀手四：allowlist 式 CSP 已被证明大面积可绕过，strict CSP 是唯一正确路径。
2016 年 Google 的研究显示，94.72% 的真实 CSP 策略可以被绕过，根源是 allowlist 中的主机暴露了 JSONP 或存在漏洞的 AngularJS 端点。2026 年的正确做法是 nonce-based 或 hash-based 的 strict CSP，配合 Trusted Types 从根源消除 XSS 注入类，而不是维护一份“信任域名清单”。

⛔ 杀手五：发布回滚不是“重新部署旧版本”，回滚语义必须原子且可验证。
Web 发布最常见的错误是“回滚了代码，但 CDN 缓存、Service Worker、数据库迁移没有一起回滚”。发布契约必须包含不可变构建产物（不可变 SHA）、版本号、签名/完整性校验、资产集合一致性；回滚时要么回滚到上一个已知良好的不可变身份，要么以更高 semver 重新发布 last-good 内容来覆盖错误的 latest，不能静默跳过校验。

1. 兼容性基线：用 Baseline 锁定目标，不要凭感觉

1.1 Baseline 三档语义

标签 含义 使用策略
Limited availability 核心浏览器集中尚未全部实现 需要 polyfill、渐进增强或特性检测回退
Newly available 刚进入核心浏览器集，互通时间不足 30 个月 可以使用，但必须有回退方案；适合渐进增强场景
Widely available 核心浏览器集互通 ≥30 个月 可以无回退直接使用，是生产项目的安全基线

执行口径： 在 browserslist 中使用 Baseline 查询，将生产构建的目标锁定为 Baseline Widely available 或你根据受众数据选定的 Baseline 年份。不要 transpile Baseline 已广泛可用的特性，那只会增加包体积和运行时开销。

2. 多端适配：容器查询优先，系统特性必须显式处理

2.1 布局：从“视口断点”转向“容器断点”

2026 年响应式设计已进入组件级适配阶段。容器查询（@container）已获得全现代浏览器支持，且处于 Baseline Widely available 状态（2025 年 8 月起） ，应作为组件布局的首选方案，而不是继续用媒体查询按视口宽度一刀切。

硬约束：

· 组件布局由父容器宽度驱动，而不是视口宽度。同一个组件在侧边栏和主内容区可以呈现不同形态，媒体查询做不到这一点。
· 保留少量全局视口断点用于页面级布局切换（如导航从底部 Tab 变为侧边栏），但组件内部不要写视口媒体查询。
· 折叠屏/异形屏使用 screen-spanning 等 CSS 特性处理跨折叠中线的沉浸式布局。

2.2 暗黑模式：必须显式处理，禁止颜色反转

⛔ 操作系统级暗黑模式覆盖率已超过 80%，不处理暗黑模式等于让大多数用户看到刺眼的界面。

· 使用 prefers-color-scheme 媒体查询和 CSS 变量实现明暗主题切换，禁止用 filter: invert() 或简单的颜色反转来“凑合”暗黑模式。
· 在 <html> 上同步设置 color-scheme，让浏览器原生控件（表单、滚动条）跟随主题。
· 暗黑模式下不允许出现硬编码的十六进制颜色，所有颜色必须走 CSS 变量。

2.3 安全区与触摸目标

· viewport 必须包含 viewport-fit=cover，并使用 env(safe-area-inset-*) 适配刘海屏和底部横条。
· 所有可点击元素的最小触摸目标为 44 × 44 CSS 像素，并提供 ARIA 标签。
· 移动端与桌面端的交互差异（tap vs click、hover 状态）必须分别处理，不能假设“桌面能点、移动也能点”。

2.4 国际化

· 使用 Intl API 处理日期、数字、货币、复数形式，不要手写格式化逻辑。
· 从 <html lang> 到 dir="rtl"，从布局方向到图标镜像，RTL 语言需要独立的布局测试，不能只翻译文案。
· 文本扩展（同一句话在德语中可能比英语长 30%）必须纳入组件布局的弹性计算，容器查询是天然适合这一场景的工具。

3. 性能：Core Web Vitals 是硬指标，INP 是新的及格线

3.1 2026 年阈值（Good / 需改进 / Poor）

指标 Good 需改进 Poor 说明
LCP（最大内容渲染） ≤ 2.5 s 2.5–4.0 s > 4.0 s 加载性能的核心指标
INP（交互到下一帧绘制） ≤ 200 ms 200–500 ms > 500 ms 取代 FID，衡量真实交互响应性；Google 已将 INP 正式纳入 Core Web Vitals 考核
CLS（累计布局偏移） ≤ 0.1 0.1–0.25 > 0.25 视觉稳定性

INP 的特殊地位： INP 是 2026 年最容易被忽视但影响最直接的指标。移动端网站 INP 超过 200ms 会直接影响搜索排名。不要把 INP 当作“优化项”，它是及格线。

3.2 硬性执行要求

· LCP 元素必须是静态可发现的：Hero 图片、首屏标题不能被 JS 延迟渲染或懒加载。字体使用 font-display: swap 并预加载关键字体。
· 为所有媒体元素预留空间：图片、字体、广告、嵌入内容必须设置明确的宽高或 aspect-ratio，CLS 超过 0.1 通常是因为没有预留空间。
· INP 优化的核心是减少主线程长任务：将非关键 JS 拆分为小 chunk、使用 requestIdleCallback 或 scheduler.postTask 分片、避免在事件处理器中同步执行重计算。Wasm 应承载密集计算（图像/视频/加密/仿真），而不是阻塞主线程。
· 在 CI 中用 Lighthouse CI 或等效工具设置性能预算，LCP/INP/CLS 任一指标低于 Good 阈值时阻断合并。

4. 安全：strict CSP + Trusted Types，纵深防御

4.1 必须设置的安全响应头

以下头部是每个响应都必须携带的基线，不是“可选加固”：

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()
Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
Cross-Origin-Resource-Policy: same-origin
```

4.2 CSP：nonce/hash-based strict CSP

⛔ 不要使用 allowlist 式的 script-src 域名清单。正确的模板方向：

· default-src 'self'
· script-src 使用 nonce 或 hash，不使用 'unsafe-inline' 作为长期方案。
· object-src 'none'、base-uri 'self'、frame-ancestors 'none'。
· 配合 Trusted Types：在支持 Trusted Types 的浏览器上，强制所有 DOM XSS 汇聚点（innerHTML、eval 等）只能接受可信类型对象，从根源消除注入类。

4.3 OWASP Top 10:2025 的关键变化

· Supply Chain Failures 上升至 #3：在 CI 中执行 npm audit、使用 Dependabot、锁定文件审计和 SRI（子资源完整性）。
· Security Misconfiguration 上升至 #2：大多数安全事件不是密码学被攻破，而是部署配置错误。安全头、CSP、CORS 配置必须纳入自动化检查。
· Exceptional Conditions 新增至 #10：所有异步边界和外部调用必须有 try/catch 和优雅降级，未处理异常本身就是安全缺陷。

4.4 Cookie 与同源

· 所有 Cookie 设置 SameSite=Lax 或 Strict；需要跨站时 SameSite=None 必须同时带 Secure。
· CORS 不要使用 Access-Control-Allow-Origin: * 搭配凭证；使用精确的 origin 白名单，并校验 Origin 头。

5. PWA：安装性是门槛，离线是能力

5.1 最低安装性清单（Chromium 浏览器强制）

☐ manifest.json 通过 <link rel="manifest"> 被每个页面引用。
☐ name 或 short_name 至少一个。
☐ icons 包含 192px 和 512px 两个尺寸。
☐ start_url 有效。
☐ display 为 standalone、fullscreen、minimal-ui 之一，或使用 display_override。
☐ 全站 HTTPS（localhost 除外）。

5.2 Service Worker 的正确使用（安装性已不强制，但离线/推送必需）

· 不要缓存不透明响应（opaque response），fetch 事件中对于跨域无 CORS 的请求应直接放行，而不是返回 cache.addAll 的结果。
· 导航请求使用 Network First 或 Stale-While-Revalidate，静态资源使用 Cache First。
· 每次发布更新 Service Worker 的版本标识，并确保 skipWaiting 和 clients.claim 的时机不会导致用户在一次会话中混用新旧资源。

6. 发布：不可变身份、原子回滚、供应链完整性

6.1 发布契约

每次生产发布必须满足：

要素 要求
构建产物 绑定到不可变的 commit SHA，构建可复现
版本号 唯一，语义化版本；回滚后的重新发布使用更高的 semver
签名/完整性 关键资产（JS、CSS、Wasm）使用 SRI 或构建签名，发布 manifest 由后端侧管理
资产集合 发布声明中包含完整的资产清单，回滚时按清单验证，而不是“大概覆盖了”

6.2 回滚语义

⛔ 回滚不是“redeploy 旧版本”这么简单。 回滚必须同时处理：应用代码、CDN 缓存、Service Worker、数据库迁移（如果有）。

· 优先回滚到上一个已知良好的不可变身份（上一个通过的 SHA + manifest）。
· 如果不可变身份已被覆盖，采用 roll-forward：以更高的 semver 重新发布 last-good 内容来取代错误的 latest，而不是原地修改版本号。
· 数据库迁移必须可逆或向后兼容：发布前评估迁移是否可以在旧版本代码下运行。不可逆迁移必须在发布计划中单独标注，并准备前滚修复方案。
· 回滚后验证的关键指标：错误率、核心 API 成功率、CWV 是否恢复到发布前水平。不能只凭“部署成功了”就认为回滚完成。

6.3 CI/CD 供应链安全

· 依赖安装使用 lockfile + npm ci，不允许在 CI 中执行不带锁的 npm install。
· 发布流水线中的签名私钥和部署凭据只暴露给实际消费它的 step，不注入整个 job；使用 OIDC 临时凭据而非长期密钥。
· 生产发布加 environment gate，不是任何 PR 合并都能直接触达生产部署。

7. 发布前自检清单（一页版）

兼容性与适配

☐ browserslist 已锁定 Baseline Widely available 或选定的 Baseline 年份，未凭感觉判断 API 支持。
☐ 组件布局使用 容器查询，未在组件内部写视口媒体查询。
☐ 暗黑模式通过 prefers-color-scheme + CSS 变量显式实现，无颜色反转、无硬编码十六进制颜色。
☐ viewport 包含 viewport-fit=cover，安全区已适配。
☐ 所有可点击元素触摸目标 ≥ 44 × 44 px，ARIA 标签齐全。
☐ 折叠屏 screen-spanning 已处理（如涉及）。

性能

☐ LCP ≤ 2.5s，LCP 元素静态可发现，字体已优化。
☐ INP ≤ 200ms，主线程长任务已拆分，重计算交给 Wasm 或 Worker。
☐ CLS ≤ 0.1，所有媒体元素预留了尺寸/aspect-ratio。
☐ CI 中设置了 CWV 性能预算，低于阈值阻断合并。

安全

☐ 全站 HTTPS，无混合内容。
☐ 安全响应头齐全：nosniff、X-Frame-Options: DENY、HSTS、Referrer-Policy、Permissions-Policy、COOP/COEP/CORP。
☐ CSP 为 nonce/hash-based strict CSP，未使用 allowlist 域名清单。
☐ Trusted Types 已启用（或至少有 require-trusted-types-for 'script' 的渐进方案）。
☐ Cookie 的 SameSite 和 Secure 已正确设置。
☐ CI 中有依赖审计（npm audit/Dependabot）和 SRI 校验。

PWA

☐ manifest 包含 192px + 512px 图标、name/short_name、start_url、display。
☐ Service Worker 不缓存不透明响应，版本标识随发布更新。
☐ 离线回退路径已验证（断网下核心页面可到达）。

发布与回滚

☐ 构建产物绑定不可变 SHA，可复现。
☐ 发布 manifest 包含完整资产清单，回滚时按清单验证。
☐ 回滚方案覆盖代码、CDN、Service Worker、数据库迁移四个层面。
☐ 数据库迁移可逆或向后兼容，不可逆迁移有前滚计划。
☐ CI 中签名/部署凭据仅暴露给消费它的 step，生产发布有 environment gate。
☐ 回滚后有指标验证（错误率、API 成功率、CWV），不是“部署成功即完成”。

8. 一句话执行口径

Baseline 锁定兼容目标，组件用容器查询而不是视口媒体查询；暗黑模式走 CSS 变量不搞颜色反转，触摸目标 ≥44px；LCP ≤2.5s、INP ≤200ms、CLS ≤0.1，INP 是 2026 年的及格线；HTTPS 全站强制，CSP 用 nonce/hash 而不是 allowlist，Trusted Types 堵住 XSS 根；PWA 安装看 manifest 的 192/512 图标和 start_url，Service Worker 管离线但不再是安装硬门槛；发布绑定不可变 SHA + 资产清单，回滚同时处理代码/CDN/SW/迁移，回滚后验证指标才算完成。