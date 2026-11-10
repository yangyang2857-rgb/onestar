#!/usr/bin/env bash
# 拾光 OneStar — 不含 Gradle / AGP 的纯命令行 Android 构建脚本
#
# 工具链只用到 Android SDK build-tools（aapt2 / d8 / apksigner）与 JDK 的 javac，
# 不用 Gradle、不用 Android Gradle Plugin，整个构建过程可以在 CI 上跑通。
#
# 用法：
#   ANDROID_HOME=/path/to/sdk ./build.sh
#
# 可选环境变量：
#   ANDROID_HOME / ANDROID_SDK_ROOT  Android SDK 根目录（必填，二选一）
#   JAVA_HOME                         JDK 17 及以上的根目录（可选，默认用 PATH 里的 javac）
#   BUILD_TOOLS                       指定 build-tools 版本目录名，默认取最高版本
#   ONESTAR_KS / ONESTAR_KS_PASS      签名 keystore 路径与口令；不提供则自动生成 debug 签名
#   ONESTAR_KS_ALIAS                  签名别名，默认 onestar-debug / 与 keystore 对应
#   ONESTAR_KS_DRIFT_OK               设为 1 时跳过固定证书指纹校验（仅供 CI 回归构建，产物不可发布）
#   ZIG                               zig 可执行文件路径；提供则编译 jni/ 下的 .so，否则跳过原生库
#   ABIS                              目标 ABI，默认 arm64-v8a
#   MIN_SDK / TARGET_SDK              默认 21 / 35
#
# 产物：build/onestar-<versionName>-signed.apk

set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"

MIN_SDK="${MIN_SDK:-21}"
TARGET_SDK="${TARGET_SDK:-35}"
ABI="${ABIS:-arm64-v8a}"

log() { printf '\033[36m== %s\033[0m\n' "$*"; }
die() { printf '\033[31m!! %s\033[0m\n' "$*" >&2; exit 1; }

# ---------- 0. 版本自检 ----------
# versionCode 是独立整数流水号，与 versionName 没有换算关系（规则见 CHANGELOG.md）。
# 这里拦两类低级错误：versionCode 不比历史最大值大（系统会当降级，老用户装不上），
# 以及 assets/COPYRIGHT.txt 里写的版本号没跟 AndroidManifest.xml 同步（APK 内署名信息会过期）。
# 确需跳过（例如只做本地实验搭建）时设 SKIP_VERSION_CHECK=1。
if [ "${SKIP_VERSION_CHECK:-0}" != "1" ]; then
  HISTORY_CODE=22   # 已发布 tag 里最大的 versionCode：v6.7
  VNAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
  VCODE="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
  [ -n "$VNAME" ] || die "AndroidManifest.xml 里读不到 android:versionName"
  [ -n "$VCODE" ] || die "AndroidManifest.xml 里读不到 android:versionCode"
  case "$VCODE" in
    ''|*[!0-9]*) die "versionCode 必须是整数，当前是「$VCODE」" ;;
  esac
  case "$VNAME" in
    [0-9]*.[0-9]*) : ;;
    *) die "versionName 必须形如 6.7 或 1.0.0，当前是「$VNAME」" ;;
  esac
  [ "$VCODE" -ge "$HISTORY_CODE" ] \
    || die "versionCode=$VCODE 小于历史最大值 $HISTORY_CODE；发版时手动 +1，别复用旧号"
  grep -q "本版本：v$VNAME（versionCode $VCODE）" assets/COPYRIGHT.txt \
    || die "assets/COPYRIGHT.txt 的「本版本」与清单不一致（应为 v$VNAME / versionCode $VCODE），先同步"
  log "版本自检通过：$VNAME（versionCode $VCODE）"
fi

# ---------- 1. 定位 SDK ----------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || die "未设置 ANDROID_HOME / ANDROID_SDK_ROOT"

BT_ROOT="$SDK/build-tools"
[ -d "$BT_ROOT" ] || die "找不到 $BT_ROOT，请装 Android SDK build-tools"
if [ -n "${BUILD_TOOLS:-}" ]; then
  BT="$BT_ROOT/$BUILD_TOOLS"
else
  BT="$BT_ROOT/$(ls -1 "$BT_ROOT" | sort -V | tail -1)"
fi
[ -d "$BT" ] || die "找不到 build-tools 目录：$BT"
for t in aapt2 d8 apksigner; do
  [ -x "$BT/$t" ] || die "build-tools 缺少 $t：$BT"
done

PLATFORMS="$SDK/platforms"
[ -d "$PLATFORMS" ] || die "找不到 $PLATFORMS"
# 取一个不低于 TARGET_SDK 的平台，没有就用最高的
AJ=""
for AV in $(ls -1 "$PLATFORMS" | sort -V -r); do
  if [ -f "$PLATFORMS/$AV/android.jar" ]; then
    AJ="$PLATFORMS/$AV/android.jar"
    case "$AV" in
      android-*) N="${AV#android-}"; if [ "${N%%[!0-9]*}" -ge "$TARGET_SDK" ]; then break; fi ;;
    esac
  fi
done
[ -n "$AJ" ] || die "platforms 下找不到 android.jar"
log "SDK build-tools: $BT"
log "android.jar: $AJ"

# ---------- 2. 定位 JDK ----------
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  export PATH="$JAVA_HOME/bin:$PATH"
else
  JAVAC="$(command -v javac || true)"
fi
[ -n "$JAVAC" ] || die "找不到 javac，请安装 JDK 17+ 或设置 JAVA_HOME"
log "javac: $($JAVAC -version 2>&1)"

# ---------- 3. 读版本号 ----------
VN="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
VN="${VN:-0.0}"
log "versionName: $VN"

OUT="$ROOT/build"
WORK="$OUT/work"
mkdir -p "$WORK"
rm -f "$OUT"/onestar-*.apk

# ---------- 4. 资源编译 + 资源链接 ----------
log "aapt2 compile / link"
mkdir -p "$WORK/gen"
"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link \
  -o "$WORK/unsigned.apk" \
  -I "$AJ" \
  --manifest AndroidManifest.xml \
  -R "$WORK/res.zip" \
  --java "$WORK/gen" \
  -A assets \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --auto-add-overlay

# ---------- 5. Java 编译 ----------
log "javac"
find java "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
mkdir -p "$WORK/classes"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$AJ" -d "$WORK/classes" @"$WORK/sources.txt"
log "javac OK: $(find "$WORK/classes" -name '*.class' | wc -l) 个 class"

# ---------- 6. dex ----------
log "d8"
mkdir -p "$WORK/dex"
find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api "$MIN_SDK" --lib "$AJ" \
  --output "$WORK/dex" @"$WORK/classes.txt"
(cd "$WORK/dex" && zip -q -X "$WORK/unsigned.apk" classes.dex)

# ---------- 7. 原生库（可选） ----------
if [ -n "${ZIG:-}" ] && [ -x "${ZIG:-}" ]; then
  log "zig cc: jni/src -> lib/$ABI（16 KB 段对齐）"
  mkdir -p "$WORK/lib/$ABI"
  # jni.h 来自 JDK；没有 JAVA_HOME 时退回 javac 所在目录向上找
  INC=""
  if [ -n "${JAVA_HOME:-}" ] && [ -f "$JAVA_HOME/include/jni.h" ]; then
    INC="-I$JAVA_HOME/include -I$JAVA_HOME/include/linux"
  else
    JH="$(dirname "$(dirname "$(readlink -f "$JAVAC")")")"
    [ -f "$JH/include/jni.h" ] && INC="-I$JH/include -I$JH/include/linux"
  fi
  for SRC in jni/src/*.c; do
    N="$(basename "$SRC" .c)"
    "$ZIG" cc -target aarch64-linux-musl -O2 -fPIC -shared -nostdlib \
      -fno-stack-protector \
      -Ijni/include $INC \
      -Wl,-z,max-page-size=16384 -Wl,-soname,"lib$N.so" -Wl,--strip-all \
      -o "$WORK/lib/$ABI/lib$N.so" "$SRC"
  done
  (cd "$WORK" && zip -q -X -0 "unsigned.apk" lib/$ABI/*.so)
else
  log "跳过原生库（未提供 ZIG）。Java 侧 NativeCore 会走纯 Java 回退路径。"
fi

# ---------- 8. 16 KB 页对齐重打包 ----------
log "zip16k 重打包"
python3 tools/zip16k.py "$WORK/unsigned.apk" "$WORK/aligned.apk"

# ---------- 9. 签名 ----------
# ONESTAR_KS_B64：把 keystore 以 base64 内联进环境变量，供 CI 从 Secret 注入。
# 这样每次构建用的都是同一张证书，已装用户才能覆盖升级（见 docs/签名与覆盖升级.md）。
if [ -n "${ONESTAR_KS_B64:-}" ] && [ -z "${ONESTAR_KS:-}" ]; then
  ONESTAR_KS="$WORK/ci-signing.keystore"
  printf '%s' "$ONESTAR_KS_B64" | base64 -d > "$ONESTAR_KS"
  ONESTAR_KS_PASS="${ONESTAR_KS_PASS:-android}"
  ONESTAR_KS_ALIAS="${ONESTAR_KS_ALIAS:-onestar-debug}"
  log "已从 ONESTAR_KS_B64 还原固定签名证书"
fi

KS="${ONESTAR_KS:-}"
SIGNING_FIXED=0
if [ -z "$KS" ]; then
  KS="$WORK/debug.keystore"
  KS_PASS="android"
  KS_ALIAS="${ONESTAR_KS_ALIAS:-onestar-debug}"
  if [ ! -f "$KS" ]; then
    log "生成一次性 debug keystore（仅用于本地/CI 验证，不能上架）"
    keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
      -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=OneStar Debug, O=OneStar, C=CN" >/dev/null 2>&1
  fi
else
  KS_PASS="${ONESTAR_KS_PASS:?设置了 ONESTAR_KS 就必须同时给 ONESTAR_KS_PASS}"
  KS_ALIAS="${ONESTAR_KS_ALIAS:?设置了 ONESTAR_KS 就必须同时给 ONESTAR_KS_ALIAS}"
  if [ "${ONESTAR_KS_DRIFT_OK:-0}" = "1" ]; then
    log "ONESTAR_KS_DRIFT_OK=1：本次为回归构建，允许证书与仓库固定值不一致（产物不可上架、不可覆盖已装正式版）"
    SIGNING_FIXED=0
  else
    SIGNING_FIXED=1
  fi
fi

APK="$OUT/onestar-$VN-signed.apk"
# 签名方案显式锁定，不依赖 build-tools 的默认值：
#   v1(JAR) + v2(APK Signature Scheme v2) 必须开 —— 与 README §5.3 的声明一致，
#   且 minSdk 21 的机器只能靠 v1 校验。
#   v3/v4 显式关闭 —— 升 build-tools / targetSdk 后默认值可能变化（v4 还会额外产出 .idsig），
#   证书与分发方式（GitHub Release + 侧载）没变之前不要引入。
# 签名顺序很关键：v1(JAR) 会重写 zip、打乱条目偏移；v2 只在文件尾追加签名块、不动偏移。
# 所以必须 v1 → 16KB 对齐 → v2。否则 extractNativeLibs=false 的包在 API 34+ 上会
# INSTALL_FAILED_INVALID_APK: Failed to extract native libraries, res=-2（装不上）。
SIGN_ARGS=(--ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS"
           --ks-key-alias "$KS_ALIAS" --min-sdk-version "$MIN_SDK")

"$BT/apksigner" sign "${SIGN_ARGS[@]}" \
  --v1-signing-enabled true --v2-signing-enabled false \
  --v3-signing-enabled false --v4-signing-enabled false \
  --out "$WORK/v1signed.apk" "$WORK/aligned.apk"

python3 tools/zip16k.py "$WORK/v1signed.apk" "$WORK/aligned2.apk"

"$BT/apksigner" sign "${SIGN_ARGS[@]}" \
  --v1-signing-enabled false --v2-signing-enabled true \
  --v3-signing-enabled false --v4-signing-enabled false \
  --out "$APK" "$WORK/aligned2.apk"

"$BT/apksigner" verify "$APK"
python3 - "$APK" <<'PYEOF' || { echo "::error::签名后 so 未按 16 KB 对齐（API 34+ 会装不上）"; exit 1; }
import sys, zipfile, struct
z = zipfile.ZipFile(sys.argv[1]); bad = 0
for i in z.infolist():
    if not i.filename.endswith('.so'):
        continue
    z.fp.seek(i.header_offset); h = z.fp.read(30)
    fl, el = struct.unpack('<HH', h[26:30])
    off = i.header_offset + 30 + fl + el
    ok = (off % 16384 == 0) and (i.compress_type == 0)
    print("  %-32s off=%-9d %s" % (i.filename, off, "OK" if ok else "BAD"))
    bad += 0 if ok else 1
sys.exit(1 if bad else 0)
PYEOF
"$BT/aapt" dump badging "$APK" | head -3 || true

# 固定证书时校验指纹，漂移即失败；一次性 debug 证书只打印指纹供参考
if [ -f tools/verify_apk_signature.py ]; then
  if [ "$SIGNING_FIXED" = "1" ]; then
    python3 tools/verify_apk_signature.py "$APK"
  else
    python3 tools/verify_apk_signature.py "$APK" --print-only || true
  fi
fi

log "完成：$APK"
ls -lh "$APK"
