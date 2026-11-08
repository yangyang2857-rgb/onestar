#!/usr/bin/env bash
# 拾光 OneStar — 纯 JVM 逻辑单测（不需要模拟器、不需要 android.jar）
#
# 做法：把不依赖 android.* 的纯逻辑类（CompatTable / DaysCodec / DayEdit / Constellation /
# NoteText / Melody / MoodCurve / NativeCore / Replay / StarName / WidgetState）与 test/ 下的
# 断言程序一起用 javac 编译到 build/test-classes，再逐个跑 main。
# 好处：存储编码、版本映射、心情曲线这类易回归的逻辑，改完立刻能验，不必等 APK。
#
# 用法：JAVA_HOME=/path/to/jdk17 ./test.sh

set -euo pipefail
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"
else
  JAVAC="$(command -v javac)"; JAVA="$(command -v java)"
fi

OUT=build/test-classes
rm -rf "$OUT"; mkdir -p "$OUT"

# 只要不 import android.* 的类，就能脱离 android.jar 编译
PURE=()
while IFS= read -r f; do
  grep -q '^import android\.' "$f" || PURE+=("$f")
done < <(find java -name '*.java' | sort)

echo "纯逻辑类 $((${#PURE[@]})) 个，测试类 $(find test -name '*.java' | wc -l) 个"
"$JAVAC" -encoding UTF-8 -nowarn -d "$OUT" \
  "${PURE[@]}" $(find test -name '*.java' | sort)

FAIL=0
for T in $(find test -name '*Test.java' | sort); do
  C="$(basename "$T" .java)"
  PKG="$(sed -n 's/^package \(.*\);/\1/p' "$T" | head -1)"
  [ -n "$PKG" ] && C="$PKG.$C"
  printf '\n---- %s ----\n' "$C"
  # 固定中文：既有断言写的是中文原文，不能被开发机/CI 的默认 locale 带偏。
  if "$JAVA" -Duser.language=zh -Duser.country=CN -Dfile.encoding=UTF-8 -cp "$OUT" "$C"; then
    :;
  else
    FAIL=$((FAIL+1))
  fi
done

echo
if [ "$FAIL" -eq 0 ]; then echo "全部通过"; else echo "失败 $FAIL 个"; exit 1; fi
