#!/usr/bin/env bash
# 拾光 OneStar 模拟器回归（替代人工真机回归的可自动化部分）
# 用法：bash tools/ci/regression.sh          （在 android-emulator-runner 的 script 里调用）
# 环境变量：PKG / APK / BASE_APK / OUT
#
# 设计原则：
#   1. 只断言"机器能判"的事实（装得上、起得来、不崩、节点不越界）；
#   2. 判不了的（手感、真实播报、刘海遮挡）一律 ::warning:: 记录，不判红，
#      避免用假红掩盖真问题；
#   3. 证据全部落盘到 build/regression，供 artifact 下载后人工扫一眼。

set -uo pipefail

PKG="${PKG:-com.yangyang2857rgb.onestar}"
APK="${APK:-$(ls build/onestar-*-signed.apk 2>/dev/null | head -1)}"
BASE_APK="${BASE_APK:-build/regression/base.apk}"
OUT="${OUT:-build/regression}"
LANGS="${LANGS:-zh-Hans zh-Hant en ar ug bo ja ko}"
SCALES="${SCALES:-0.90 1.0 1.15}"

WARN_N=0
FAIL_N=0
mkdir -p "$OUT"

log()  { printf '\n\033[1m=== %s ===\033[0m\n' "$*"; }
ok()   { printf '  [ok]   %s\n' "$*"; }
note() { printf '  [note] %s\n' "$*"; }
warn() { printf '  [warn] %s\n' "$*"; echo "::warning::$*"; WARN_N=$((WARN_N + 1)); }
bad()  { printf '  [FAIL] %s\n' "$*"; echo "::error::$*"; FAIL_N=$((FAIL_N + 1)); }
# 取 APK 签名证书 SHA-256（优先 apksigner；不可用时回退仓库验签脚本）
sig_sha256() {
  local f="$1" bin out
  bin="$(command -v apksigner 2>/dev/null || ls "${ANDROID_HOME:-/usr/local/lib/android/sdk}"/build-tools/*/apksigner 2>/dev/null | tail -1)"
  if [ -n "$bin" ]; then
    out="$("$bin" verify --print-certs "$f" 2>/dev/null | awk -F': ' '/SHA-256 digest/ {print $2; exit}')"
    [ -n "$out" ] && { printf '%s' "$out"; return 0; }
  fi
  python3 tools/verify_apk_signature.py "$f" 2>/dev/null | grep -oE '([0-9A-F]{2}:){31}[0-9A-F]{2}' | head -1
}

[ -f "$APK" ] || { echo "::error::找不到待测 APK：$APK"; exit 1; }
echo "待测 APK: $APK ($(stat -c %s "$APK") bytes)"

# ---------- 基础工具 ----------

# 所有 adb_ 调用都必须带超时：模拟器偶发 device offline 时 adb_ shell/exec-out 会永久阻塞，
# 之前就因此把整个 regression job 拖到 45min 超时（真实故障点被掩盖）。
ADB_TO="${ADB_TO:-60}"
adb_() { timeout "$ADB_TO" adb "$@"; }
# 输出二进制（screencap）用：超时后仍可能留半个文件，调用方需校验大小
shot_ok() { [ -s "$1" ]; }

# ---------- 设备健康检查 ----------
# 2026-11-12 run 43247695146：模拟器进程在装包后静默死亡，adb 从此持续 offline，
# 后续 9 项检查全部误报为 App 缺陷。这里把"模拟器没了"单独识别成环境故障并立即中止，
# 避免用环境噪音冒充回归结论。
dev_state() { timeout 20 adb get-state 2>/dev/null | tr -d '\r'; }

ensure_dev() {
  [ "$(dev_state)" = "device" ] && return 0
  echo "  [warn] 设备连接异常（state=$(dev_state)），尝试恢复"
  timeout 30 adb reconnect offline >/dev/null 2>&1 || true
  timeout 30 adb reconnect >/dev/null 2>&1 || true
  local i=0
  while [ $i -lt 3 ]; do
    i=$((i + 1)); sleep 3
    [ "$(dev_state)" = "device" ] && { echo "  [ok]   设备连接已恢复"; return 0; }
  done
  timeout 60 adb kill-server >/dev/null 2>&1 || true
  timeout 60 adb start-server >/dev/null 2>&1 || true
  i=0
  while [ $i -lt 20 ]; do
    i=$((i + 1)); sleep 3
    [ "$(dev_state)" = "device" ] && { echo "  [ok]   adb server 重启后设备恢复"; return 0; }
  done
  return 1
}

# 环境故障：中止本轮回归，结论作废，退出码 3（与 App 缺陷的 exit 1 区分）
env_die() {
  {
    echo "$*"
    echo "adb devices -l:"
    timeout 20 adb devices -l 2>&1 || true
  } > "$OUT/env-failure.txt" 2>&1
  echo "  [ENV]  $*（见 env-failure.txt）"
  echo "::error::$*"
  echo
  echo "模拟器/环境故障，本轮回归结论作废：不计入 App 缺陷，请重跑 job。"
  echo "ENV_FAIL=1" > "$OUT/.env-fail"
  exit 3
}

# 每个阶段开始前调用：设备还在就继续，没了就直接中止
need_dev() { ensure_dev || env_die "模拟器失联（$1 阶段无法继续）"; }


wait_boot() {
  timeout 300 adb wait-for-device
  local i=0
  until [ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    i=$((i + 1)); [ $i -gt 120 ] && { echo "::error::模拟器 240s 未启动完成"; exit 1; }
    sleep 2
  done
  sleep 3
  adb_ shell input keyevent 82 >/dev/null 2>&1 || true   # 解锁
  adb_ shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
  adb_ shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
  adb_ shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
}

screen_size() {
  local i=0 out
  while [ $i -lt 3 ]; do
    out="$(adb_ shell wm size 2>/dev/null | tr -d '\r' | awk -F': *' '/Physical size/ {print $2; exit}')"
    [ -n "$out" ] && { printf '%s' "$out"; return 0; }
    i=$((i + 1)); sleep 1
  done
}

shot() {
  timeout 60 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null
  if ! shot_ok "$OUT/$1.png"; then
    rm -f "$OUT/$1.png"   # 0 字节截图不留在证据包里，避免被误当"有图可查"
    warn "截图失败：$1（后续视觉证据缺失）"
  fi
}

dump_ui() {
  adb_ shell rm -f /sdcard/window.xml >/dev/null 2>&1 || true
  adb_ shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb_ pull /sdcard/window.xml "$OUT/$1.xml" >/dev/null 2>&1 || true
  [ -s "$OUT/$1.xml" ] || return 1
  return 0
}

launch() {
  adb_ shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb_ shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  sleep 5
}

alive() { [ -n "$(adb_ shell pidof "$PKG" 2>/dev/null | tr -d '\r')" ]; }

foreground() {
  adb_ shell dumpsys activity activities 2>/dev/null | tr -d '\r' \
    | grep -E "mResumedActivity|topResumedActivity" | grep -q "$PKG"
}

crash_check() {
  local tag="$1" out
  if ! out="$(adb_ logcat -d -b crash 2>&1 | tr -d '\r')"; then
    warn "取不到 crash 日志缓冲（$tag），本项无法判定"
    return 0
  fi
  if printf '%s' "$out" | grep -q "device offline\|error:"; then
    warn "crash 日志读取异常（$tag）：$(printf '%s' "$out" | head -1)"
    return 0
  fi
  if printf '%s' "$out" | grep -qF "$PKG"; then
    printf '%s\n' "$out" | grep -F "$PKG" | tail -30 > "$OUT/crash-$tag.log"
    bad "crash 日志命中 $PKG（见 crash-$tag.log）"
  else
    ok "无 crash 日志（$tag）"
  fi
  adb_ logcat -c >/dev/null 2>&1 || true
}

# 节点越界检查：自绘 Canvas 的文字不在 view 树里，此项主要覆盖系统/Android 控件层
# （输入框、按钮、弹窗），Canvas 溢出仍需靠截图人工看，见脚注。
check_bounds() {
  local tag="$1" size w h
  size="$(screen_size)"
  w="${size%x*}"; h="${size#*x}"
  if [ -z "${w:-}" ] || [ -z "${h:-}" ]; then warn "取不到屏幕尺寸，跳过越界检查（$tag）"; return; fi
  if ! dump_ui "$tag"; then warn "uiautomator dump 失败，跳过越界检查（$tag）"; return; fi
  if python3 tools/ci/check_bounds.py "$OUT/$tag.xml" "$w" "$h"; then
    ok "控件层无越界（$tag, ${w}x${h}）"
  else
    bad "控件层有节点越界（$tag，详见上方清单）"
  fi
}

# ---------- 0. 设备信息 ----------

log "0/6 设备与包信息"
need_dev "0/6 设备与包信息"
adb_ shell getprop ro.build.version.sdk | tr -d '\r' | sed 's/^/  API: /'
echo "  屏幕: $(screen_size)"
echo "  基线包: $([ -s "$BASE_APK" ] && echo "有 ($(stat -c %s "$BASE_APK") bytes)" || echo "无")"
echo "  设备 ABI: $(adb_ shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r')"
echo "  包内 ABI: $(unzip -l "$APK" 2>/dev/null | awk '/lib\//{split($4,a,"/"); print a[2]}' | sort -u | tr '\n' ' ')"
echo "  基线内 ABI: $([ -s "$BASE_APK" ] && unzip -l "$BASE_APK" 2>/dev/null | awk '/lib\//{split($4,a,"/"); print a[2]}' | sort -u | tr '\n' ' ' || echo 无)"

# ---------- 1. 覆盖安装 ----------

log "1/6 覆盖安装（基线 → 新版）"
need_dev "1/6 覆盖安装"
adb_ uninstall "$PKG" >/dev/null 2>&1 || true
COVER_TESTED=0

if [ -s "$BASE_APK" ]; then
  BASE_SIG="$(sig_sha256 "$BASE_APK")"
  NEW_SIG="$(sig_sha256 "$APK")"
  { echo "基线包 SHA-256: ${BASE_SIG:-未知}"; echo "待测包 SHA-256: ${NEW_SIG:-未知}"; } > "$OUT/base-signature.txt"

  if [ -n "$BASE_SIG" ] && [ "$BASE_SIG" = "$NEW_SIG" ]; then
    ok "基线包与待测包同签名，可实测覆盖升级"
    BASE_OK=1
  elif python3 tools/verify_apk_signature.py "$BASE_APK" >> "$OUT/base-signature.txt" 2>&1; then
    ok "基线包证书与仓库固定证书一致，可以真测覆盖升级"
    BASE_OK=1
  else
    warn "基线包与待测包签名不一致，覆盖升级不适用（见 base-signature.txt）"
    BASE_OK=0
  fi

  if [ "$BASE_OK" = "1" ]; then
    if adb_ install -r -d "$BASE_APK" > "$OUT/install-base.log" 2>&1 && grep -q Success "$OUT/install-base.log"; then
      ok "基线包安装成功"
      BASE_VC="$(adb_ shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r' | awk -F= '/versionCode=/ {print $2; exit}' | awk '{print $1}')"
      echo "  基线 versionCode: ${BASE_VC:-未知}"
      launch
      alive && ok "基线包可启动" || bad "基线包装上了但起不来（环境问题，后续结论不可信）"
      shot "00-base-launched"
      if adb_ install -r "$APK" > "$OUT/install-overwrite.log" 2>&1 && grep -q Success "$OUT/install-overwrite.log"; then
        ok "覆盖安装成功（未卸载、未丢数据目录）"
        COVER_TESTED=1
      else
        bad "覆盖安装失败，检查证书/versionCode（见 install-overwrite.log）"
      fi
    else
      warn "基线包安装失败（ABI/系统镜像不符等，见 install-base.log），覆盖升级本次未验"
      sed 's/^/     /' "$OUT/install-base.log" | head -3
      BASE_OK=0
    fi
  fi
fi

if [ "$COVER_TESTED" = "0" ]; then
  warn "未完成覆盖安装验证：改用卸载后干净安装，只测新版本身"
  adb_ uninstall "$PKG" >/dev/null 2>&1 || true
  if adb_ install -r "$APK" > "$OUT/install-clean.log" 2>&1 && grep -q Success "$OUT/install-clean.log"; then
    ok "干净安装成功"
  else
    bad "干净安装失败（见 install-clean.log）"
    sed 's/^/     /' "$OUT/install-clean.log" | head -5
  fi
fi

NEW_VC="$(adb_ shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r' | awk -F= '/versionCode=/ {print $2; exit}' | awk '{print $1}')"
EXPECT_VC="$(grep -oE 'android:versionCode="[0-9]+"' AndroidManifest.xml 2>/dev/null | head -1 | grep -oE '[0-9]+')"
echo "  当前 versionCode: ${NEW_VC:-未知}（manifest: ${EXPECT_VC:-未知}）"
[ -n "$NEW_VC" ] && [ "$NEW_VC" = "$EXPECT_VC" ] && ok "versionCode = $NEW_VC，与 manifest 一致" \
  || warn "versionCode = ${NEW_VC:-未知}，与 manifest 的 ${EXPECT_VC:-未知} 不符"

# ---------- 2. 冷启动与首屏 ----------

log "2/6 冷启动与首屏"
need_dev "2/6 冷启动"
adb_ logcat -c >/dev/null 2>&1 || true
launch
alive && ok "进程存活" || bad "冷启动后进程不存在（闪退）"
foreground && ok "MainActivity 在前台" || warn "未取到前台 Activity（可能是 dumpsys 文案差异）"
shot "01-cold-launch"
check_bounds "01-cold-launch"
crash_check "cold"

# ---------- 3. 字号三档 ----------

log "3/6 系统字号三档"
need_dev "3/6 字号"
for s in $SCALES; do
  adb_ shell settings put system font_scale "$s" >/dev/null 2>&1 || true
  sleep 1
  launch
  if alive; then ok "font_scale=$s 启动正常"; else bad "font_scale=$s 启动失败"; fi
  local_tag="font-${s//./_}"
  shot "$local_tag"
  check_bounds "$local_tag"
done
adb_ shell settings put system font_scale 1.0 >/dev/null 2>&1 || true

# ---------- 4. 方向切换 ----------

log "4/6 方向切换"
need_dev "4/6 方向"
adb_ shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
for r in 1 0; do
  adb_ shell settings put system user_rotation "$r" >/dev/null 2>&1 || true
  sleep 3
  if alive; then ok "user_rotation=$r 时进程存活"; else bad "user_rotation=$r 时进程死亡"; fi
  shot "rotation-$r"
done
warn "manifest 仍锁 screenOrientation=portrait：横屏下 App 保持竖屏（letterbox），这是已知技术债，闸门 6 待后续拆锁"

# ---------- 5. 多语言抽检 ----------

log "5/6 多语言与 RTL（设备能切 locale 就抽检，否则由单测层承担）"
need_dev "5/6 多语言"
# 分工：文案表本身、语言判定、RTL 标志、漏汉字，全部由纯 JVM 单测覆盖
# （test/I18nTableTest：15 语言 × 全部行非空 / 占位符对齐 / 漏汉字 / RTL；
#   test/com/onestar/.../I18nTest：跨类调用点漏翻），这些不需要设备。
# 设备层只补单测抓不到的部分：切了语言之后能不能启动、布局会不会越界。
# cmd locale set-app-locales 是 API 33+ 才有的命令，API 30 镜像没有，
# 按能力探测处理，不让「命令不存在」冒充成一条回归警告。
LOCALE_CMD_OK=1
adb_ shell cmd locale help >/dev/null 2>&1 || LOCALE_CMD_OK=0
if [ "$LOCALE_CMD_OK" = "0" ]; then
  note "本镜像无 cmd locale set-app-locales（API 33+ 才提供），设备层不做 locale 切换"
  note "语言/RTL 覆盖由单测层保证：test/I18nTableTest（15 语言·全行非空·占位符对齐·漏汉字·RTL）+ I18nTest（跨类漏翻），已由 build job 的 Unit tests 执行"
  note "注：manifest supportsRtl=false 是有意设计——界面为 Canvas 自绘，RTL 走 I18n.visual() 手动 bidi，不依赖系统布局镜像"
else
  for lang in $LANGS; do
    adb_ shell cmd locale set-app-locales "$PKG" --locales "$lang" >/dev/null 2>&1 \
      || { warn "设置 $lang 失败，跳过"; continue; }
    sleep 1
    adb_ logcat -c >/dev/null 2>&1 || true
    launch
    if alive; then ok "$lang 启动正常"; else bad "$lang 启动闪退"; fi
    tag="lang-$lang"
    shot "$tag"
    check_bounds "$tag"
    crash_check "$tag"
  done
  adb_ shell cmd locale set-app-locales "$PKG" --locales "" >/dev/null 2>&1 || true
fi

# ---------- 6. 无障碍语义抽查 ----------

log "6/6 无障碍语义抽查"
need_dev "6/6 无障碍"
adb_ shell cmd locale set-app-locales "$PKG" --locales zh-Hans >/dev/null 2>&1 || true
launch
if dump_ui "a11y-zh"; then
  if grep -q "content-desc=\"[^\"]\{2,\}\"" "$OUT/a11y-zh.xml"; then
    ok "UI 树中存在非空 content-desc（语义已暴露到无障碍层）"
  else
    warn "UI 树无 content-desc：自绘视图的虚拟节点未被 dump 捕获，TalkBack 完整播报仍需真机确认"
  fi
else
  warn "dump 失败，无障碍抽查跳过"
fi

# ---------- 汇总 ----------

log "回归结果汇总"
printf '  失败 %d 项 / 警告 %d 项\n' "$FAIL_N" "$WARN_N"
cat > "$OUT/summary.txt" <<EOF
拾光 OneStar 模拟器回归
APK: $APK ($(stat -c %s "$APK") bytes)
versionCode: ${NEW_VC:-未知}
覆盖安装实测: $([ "$COVER_TESTED" = "1" ] && echo 是 || echo 否)
失败: $FAIL_N 项
警告: $WARN_N 项
注意: 本脚本不覆盖主观项（视觉手感、TalkBack 完整播报、刘海/挖孔遮挡、厂商字体渲染），
     这些东西在检查表里必须写"未验"，不得因为 CI 绿了就当作全绿。
EOF
cat "$OUT/summary.txt"

[ "$FAIL_N" -eq 0 ] || exit 1
exit 0
