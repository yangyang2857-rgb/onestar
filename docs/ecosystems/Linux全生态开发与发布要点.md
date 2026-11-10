Linux 全生态开发与发布要点

目标：照着做不出岔子。Linux 与前面几个生态最大的不同在于“不存在一个统一的发布渠道”——同一个应用需要同时面对 Debian/Ubuntu、Fedora、Arch、Flatpak、Snap 至少五条分发路径，且每条路径的依赖模型、签名要求和沙箱规则各不相同。标注 ⛔ 的条目，踩了会直接导致包无法安装、启动即崩溃、上架被拒或安全审计不通过。

0. 五条“沉默的杀手”

⛔ 杀手一：glibc 基线是 Linux 原生分发的第一决定因素，不是编译器版本。
Linux 没有“最低系统版本”的抽象层，二进制链接的 glibc 符号版本决定了它能在多老的系统上运行。在 Ubuntu 24.04 或 Fedora 40 上构建的 .deb/.rpm/AppImage，无法在 Ubuntu 22.04 或 Debian 12 上启动，因为符号版本不满足。唯一安全的做法是：在你打算支持的最老目标系统上构建。对于大多数桌面应用，这意味着 Ubuntu 22.04 或 Debian 12，它们同时提供 WebKitGTK 4.1 和 glibc 2.35/2.36。用 Docker 或 GitHub Actions 的 ubuntu-22.04 runner 做可重复构建，不要在开发机上“碰运气”编译。

⛔ 杀手二：Wayland 下“自己定位窗口”在协议层面被禁止，不是 bug 是设计。
set_outer_position() 在 Wayland 上是文档明确的无操作（no-op） ，任何依赖“把窗口/弹窗放到屏幕某个坐标”的逻辑在 Wayland 下都会失效。托盘弹窗、右键菜单、工具提示尤其容易翻车。这不是某款 compositor 的兼容性问题，是 Wayland 安全模型的硬约束。替代方案：托盘走 StatusNotifierItem / AppIndicator over D-Bus；弹窗/菜单使用 wlr-layer-shell 协议（Wine 的 wayland 驱动已经这样处理 Electron 风格的上下文菜单）。不要在 Wayland 会话下强行用 X11 的“绝对定位”思维写代码。

⛔ 杀手三：XDG_SESSION_TYPE 不可靠，Electron/Chromium 按它选后端会直接段错误。
Ubuntu 26.04 的 GDM 在 X11 会话下，loginctl 和 systemctl --user 仍然可能报告 XDG_SESSION_TYPE=wayland，导致 Electron/Chromium 错误地尝试连接 Wayland display 并 segmentation fault。症状是“在 X11 上启动就崩溃”，但用户根本没意识到自己在 X11。如果你的应用基于 Electron/CEF/Chromium，不要信任 XDG_SESSION_TYPE；在启动脚本中显式兜底，或在已知存在此问题的发行版上强制 --ozone-platform=x11 作为可配置项，而不是自动探测。

⛔ 杀手四：Secure Boot 开启后，未签名或 MOK 未注册的内核模块会被内核直接拒绝加载。
很多发行版在 Secure Boot 开启时自动启用 kernel lockdown，此时 CONFIG_MODULE_SIG_FORCE 或等效机制生效，未签名模块的 insmod/modprobe 会失败。如果你的产品包含 out-of-tree 驱动（自研硬件、文件系统、网络模块），DKMS 签名 + MOK 注册是必选项，不是可选项。只提供源码让用户自己 make 是不够的。

⛔ 杀手五：Flatpak/Snap 的“沙箱”不是建议，是硬边界——默认权限比你想象的小得多。
Flatpak 应用默认不能访问宿主文件系统（除 ~/.var/app/$FLATPAK_ID 和 $XDG_RUNTIME_DIR/app/$FLATPAK_ID）、不能访问 X11、不能访问 system D-Bus、不能访问 PulseAudio（视 runtime 配置）。Snap 的 strict confinement 同理。如果你的应用直接 open("/etc/something") 或 connect 到 system bus，在沙箱内会静默失败或抛异常。正确路径是 Portals（文件选择、打印、通知、Secret Service、动态启动器等），而不是申请“全面文件系统访问”权限——后者在 Flathub 审核中会被标记，且用户侧会收到安全警告。

1. 技术栈与基础约束

1.1 glibc 基线策略（先定，再写代码）

目标用户 构建基线 原因
最大化兼容性（Ubuntu 22.04 / Debian 12 及以上） Ubuntu 22.04 / Debian 12 glibc 2.35/2.36，提供 WebKitGTK 4.1，覆盖绝大多数仍在维护的桌面发行版
仅面向最新发行版（Ubuntu 24.04+ / Fedora 40+） Ubuntu 24.04 可使用更新的运行时，但放弃旧系统用户
静态链接 / musl 可行场景 静态链接 适用于无 glibc 依赖的 CLI/服务端，桌面应用通常不可行（GUI 栈依赖 glibc 生态）

执行口径： 除非明确放弃 Ubuntu 22.04 / Debian 12 用户，否则统一在 Ubuntu 22.04 容器中构建所有原生分发包。AppImage、.deb、.rpm 共用同一构建产物，不要为每个格式单独编译。

1.2 Wayland vs X11：不是二选一，是必须同时可用的两条路径

2026 年的现实是：Wayland 已是 Ubuntu、Fedora、GNOME 的默认会话，但 X11 仍在大量用户和远程场景中使用。不能只支持 Wayland，也不能假设 X11 永远存在。

必备兼容性检查：

· 启动时区分 WAYLAND_DISPLAY 和 DISPLAY，但不要仅依赖 XDG_SESSION_TYPE（见 §0 杀手三）。
· Qt 应用：避免在全局环境（如 /etc/profile）中设置 QT_QPA_PLATFORM=xcb，这会在 Wayland 会话下强制 Qt 走 X11 后端，导致 Flatpak Qt 应用无法启动。让 Qt 按会话自然选择后端，仅在需要时通过 -platform 参数显式指定。
· GTK 应用：确保使用 GTK 4 或 GTK 3 的 Wayland 后端；GTK 4 在特定 compositor 的 dmabuf v6 下曾有崩溃问题，需跟踪上游修复。

1.3 桌面集成（托盘、全局菜单、通知、启动器）

集成点 Wayland 下的正确方式 常见错误
系统托盘 StatusNotifierItem / AppIndicator over D-Bus；GNOME 需要 AppIndicator 扩展 依赖 XEmbed 旧式托盘（Wayland 下不可用）
托盘弹出菜单 使用 wlr-layer-shell 协议定位；或通过 compositor 提供的菜单协议 调用 set_outer_position() 试图绝对定位（no-op）
通知 标准 org.freedesktop.Notifications D-Bus 接口 自绘通知窗口（在沙箱和 Wayland 下不可靠）
启动器 / .desktop 提供有效的 .desktop 文件，Exec 指向可执行文件；Icon 使用标准主题图标名 在 Flatpak 中直接写宿主 .desktop（应使用 DynamicLauncher portal）
全局菜单 通过 com.canonical.AppMenu.Registrar 或 KDE 的 org.kde.kappmenu（视桌面环境） 假设所有桌面都有全局菜单协议

⛔ App ID 一致性： Wayland 下窗口的 app_id 必须与 .desktop 文件名（去掉 .desktop）一致，否则任务栏关联、图标匹配、窗口分组都会失效。Flatpak 中 app_id 还必须与 Flatpak ID 一致。在第一个 beta 之前锁定 App ID，之后不要更改，更改会断裂用户的任务栏固定和通知历史。

2. 打包格式：没有“一个格式通吃”，按受众矩阵分发

Linux 桌面分发的现实是多格式并行。AppImage + deb + rpm 覆盖约 90% 的桌面 Linux 用户；Flatpak + Snap 通过各自商店各再覆盖约 5%。不要试图用一个格式覆盖所有人。

2.1 格式选择矩阵

受众 推荐格式 自动更新 关键约束
“给我一个下载，我直接跑” AppImage 手动（或内置 updater） 需要目标系统有 FUSE；不要套进 .tar.gz 再分发
Ubuntu / Debian / Mint 用户 .deb + 托管 apt 仓库 通过 apt upgrade 依赖必须准确声明；postinst 脚本需幂等
Fedora / RHEL / openSUSE .rpm + dnf/yum 仓库 通过仓库 需要 GPG 签名，否则 dnf 拒绝安装
跨发行版“商店”用户 Flatpak（Flathub） 内置 沙箱权限受限；Portals 是唯一正确路径
Ubuntu 商店优先用户 Snap 内置 strict confinement 下权限严格；classic confinement 需人工审核
Arch 用户 AUR PKGBUILD 社区维护 从 .deb 重打包是低成本路径

2.2 各格式的硬性要求

AppImage

· 构建于 Ubuntu 22.04 基线，链接的 glibc 不低于 2.35。
· 使用 GPG 签名（AppImage 支持内嵌签名），并在发布页提供公钥指纹。
· 不要将 AppImage 放入 .zip/.tar.gz 再次压缩，会破坏可执行位和 FUSE 挂载。
· 如需包含 GStreamer 等多媒体框架，评估体积和 ugly 插件的许可证问题。

.deb / .rpm

· .deb 的 Depends 和 .rpm 的 Requires 必须准确，多写会导致不必要的依赖拉取，少写会在安装后运行时崩溃。
· 使用 postinst/postrm 脚本时，必须幂等：升级、重装、卸载再安装都应正常执行。
· .rpm 必须用 GPG 签名，并在仓库元数据中提供公钥；rpm -Kv 应显示签名验证通过。
· 不要只在开发机上测试安装：用干净的 Docker 容器（debian:12、ubuntu:22.04、fedora:latest）验证 dpkg -i / rpm -i 后能否启动。

Flatpak

· 沙箱权限最小化：优先使用 Portals（文件选择器、打印、通知、Secret Service、动态启动器），而不是 --filesystem=host 或 --socket=system-bus。
· org.freedesktop.secrets 不应直接申请；使用 Secret portal，否则应用可以读取其他应用的密钥，Flathub 审核会拒绝。
· 提交 Flathub 前，确保 finish-args 中的每个权限都有可解释的理由；Flathub 维护者会对过度权限提出质疑。

Snap

· 默认使用 strict confinement，只声明必需的 plug。例如，读取传感器用 hardware-observe，GPIO 用 gpio，不要用 classic 绕过。
· classic confinement 会触发人工审核，需要发布者通过论坛提申请并说明技术理由，审核通过后才会上架；后续同一发布者的上传不再重复人工审核，但权限授予是一次性的。
· Stable 渠道的晋升通常需要独立安装、刷新、回滚验证后由 owner 批准。

3. 容器与沙箱：Portals 是跨格式的统一答案

Flatpak 和 Snap 的沙箱模型不同，但面向应用的 API 收敛在 Portals 上。如果你的应用在 Flatpak 中正确使用 Portals，迁移到 Snap strict 下的工作量会显著降低。

必须走 Portal 的能力清单：

能力 Portal 不要做的事
打开/保存文件 org.freedesktop.portal.FileChooser 直接 open() 宿主路径
打印 org.freedesktop.portal.Print 直接连接 CUPS
通知 org.freedesktop.portal.Notification 自绘通知窗口
密钥/密码 org.freedesktop.portal.Secret 直接读 org.freedesktop.secrets
动态启动器 org.freedesktop.portal.DynamicLauncher 在 Flatpak 中直接写宿主 .desktop
inhibiting（阻止休眠等） org.freedesktop.portal.Inhibit 直接调用 logind

GTK/Qt 的透明 Portal 支持： GTK 在检测到 Flatpak 沙箱时会透明地使用 Portals 处理部分功能；Qt 和 KDE 库也有对应支持。但不要假设所有 API 都会自动走 Portal——文件选择、打印等需要显式使用 Portal API 或框架的 Portal-aware 接口。

4. 服务器与服务端应用

4.1 systemd 单元：单二进制的标准交付

对于服务端应用，systemd 是事实标准。交付方式通常有两种：

方式 A：单二进制 + 手工 systemd unit（适合小型服务/VPS）

· 将编译好的单二进制放到 /usr/local/bin/ 或 /opt/yourapp/。
· 提供 .service 文件，使用 Restart=on-failure、User=yourapp（不要以 root 运行）、NoNewPrivileges=yes、ProtectSystem=strict、ProtectHome=yes 等硬化选项。
· 日志交给 journald（直接写 stdout/stderr），不要自己管理日志轮转文件。
· 如需重启权限，给部署用户一条最小 sudoers 规则，只允许 systemctl restart yourapp，不要给通用 sudo。

方式 B：.deb/.rpm 包内含 systemd unit（适合企业分发）

· 在包安装时把 .service 放到 /lib/systemd/system/，postinst 中执行 systemctl daemon-reload，并按需 enable。
· 升级时避免重启正在运行的服务，除非你明确需要；在 postinst 中判断 systemctl is-active 再决定。

4.2 容器化交付（可选）

如果服务依赖复杂（多进程、特定运行时、GPU 驱动），Podman/Docker 镜像可以作为替代或补充。但注意：容器内的内核模块、GPU 直通、systemd 集成需要额外配置，不要用容器替代 systemd unit 做单纯进程管理——那是过度工程。

5. 内核模块与 Secure Boot（含 out-of-tree 驱动时必读）

5.1 签名链条

如果应用包含 out-of-tree 内核模块（自研硬件驱动、文件系统、网络模块等），必须走完以下链条：

1. 生成 MOK（Machine Owner Key）密钥对：私钥是信任根，必须妥善保管。
2. 用 MOK 私钥签名 .ko 文件：签名是追加到模块末尾的，不是嵌入；签名后不能修改/剥离模块，否则签名失效。
3. 通过 mokutil --import 注册公钥：这只是排队，需要在下次启动时由用户在 MokManager（shim 的蓝色界面）中手动确认。不要在 CI 中自动化这一步——MokManager 的确认必须有人在物理控制台前操作，这是安全设计。
4. DKMS 集成：如果走 DKMS 路径，DKMS 会在每次内核更新后重新编译并用 MOK 私钥自动签名，但 MOK 的公钥仍需用户事先注册。Debian/Ubuntu 的 dkms 包会自动处理签名，默认私钥在 /var/lib/dkms/mok.key（Ubuntu 指向 /var/lib/shim-signed/mok/）。

⛔ 私钥安全： MOK 私钥等同于内核级信任。能读取私钥的人可以签名任何模块，内核都会加载。Ubuntu 文档明确指出，放在 root 可读文件系统上的 MOK 私钥“实际上消除了 root 和内核模式之间的安全边界”。在 CI 中，私钥应来自 Secrets，临时使用后即删，不要写入镜像或缓存。

5.2 发行版差异

· Debian/Ubuntu：dkms 包自动签名；mokutil 和 MokManager 流程成熟。
· RHEL/Fedora：out-of-tree 模块必须用受信任密钥签名；mokutil --import 排队，MokManager 确认，/usr/src/kernels/$(uname -r)/scripts/sign-file 追加签名。
· Secure Boot 关闭时：未签名模块可以加载（可能 taint 内核）；Secure Boot 开启 + lockdown 时，未签名模块被绝对拒绝，没有绕过路径。

6. 游戏与 Wine/Proton 兼容层（如涉及）

如果发布的是 Linux 原生游戏，确保使用标准图形栈（Vulkan/OpenGL），并在 Wayland 和 XWayland 下分别测试。如果希望 Windows 构建也能在 Linux 上运行：

· Proton 11.0-1 稳定版（2026 年 7 月）已大幅提升兼容性；Steam 客户端 Beta 中，非 Steam 游戏添加的 Windows 可执行文件默认通过 Proton 运行，不再需要手动选择兼容层。
· 不要依赖 Proton 作为唯一运行路径。如果游戏对延迟/图形有严格要求，提供原生 Linux 构建仍然是质量最高的选择；Proton 是兜底，不是替代。

7. 发布前自检清单（一页版）

glibc 与构建

☐ 原生分发包在 Ubuntu 22.04 / Debian 12 或更老基线上构建，未在开发机“碰运气”编译。
☐ 同一构建产物用于 AppImage / deb / rpm，未为每个格式单独编译。
☐ ARM64 如涉及：使用 ubuntu-22.04-arm runner 或交叉编译工具链，未尝试在 x86 上“直接出 ARM 包”。

Wayland / X11

☐ 应用在 Wayland 和 X11 会话下分别启动测试，未假设 XDG_SESSION_TYPE 可靠。
☐ 托盘使用 StatusNotifierItem / AppIndicator；弹出菜单使用 wlr-layer-shell 或 compositor 协议，未调用 set_outer_position()。
☐ App ID / .desktop 文件名 / 窗口 app_id 三者一致，Flatpak 中与 Flatpak ID 一致。
☐ Electron/CEF 应用有已知 XDG_SESSION_TYPE 误报的兜底策略（强制 x11 或可配置）。

打包与签名

☐ AppImage GPG 签名，未套进 .tar.gz/.zip。
☐ .deb / .rpm 依赖声明准确，postinst/postrm 幂等。
☐ .rpm GPG 签名，rpm -Kv 验证通过。
☐ Snap 默认 strict confinement；如用 classic，已准备论坛申请和人工审核流程。

沙箱与 Portals

☐ Flatpak 优先使用 Portals（文件、打印、通知、Secret、DynamicLauncher），未申请 --filesystem=host 或 --socket=system-bus 作为捷径。
☐ Secret 使用 Secret portal，未直接申请 org.freedesktop.secrets。
☐ GTK/Qt 的 Portal 透明支持已确认，但文件选择/打印等关键路径已显式验证在沙箱内可用。

内核模块（如涉及）

☐ MOK 密钥对已生成，私钥未进入 Git / CI 缓存。
☐ .ko 用 MOK 签名，签名后未修改/剥离模块。
☐ mokutil --import 后，MokManager 确认流程已文档化，未尝试在 CI 中自动确认。
☐ DKMS 路径已测试：内核更新后模块重新编译并自动签名。

服务器（如涉及）

☐ 提供 systemd unit，使用 User= 非 root、NoNewPrivileges=yes、ProtectSystem=strict 等硬化选项。
☐ 日志写 stdout/stderr 交给 journald，未自管日志轮转。
☐ 部署用户只有最小 sudoers 权限（仅 systemctl restart yourapp），未给通用 sudo。

8. 一句话执行口径

glibc 基线在 Ubuntu 22.04 上构建，一个产物喂所有格式；Wayland 下别想绝对定位，托盘走 SNI/AppIndicator，弹窗走 layer-shell，XDG_SESSION_TYPE 不可信；AppImage + deb + rpm 覆盖 90%，Flatpak/Snap 用 Portals 不要申请全盘权限；out-of-tree 驱动必须 MOK 签名 + MokManager 人工确认，私钥就是内核信任根；服务器用 systemd 硬化单元，日志交给 journald，sudo 只给 restart。