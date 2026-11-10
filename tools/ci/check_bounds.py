#!/usr/bin/env python3
"""检查 uiautomator dump 的节点是否越出屏幕边界。

用法：python3 tools/ci/check_bounds.py dump.xml <width> <height> [--tolerance 2]

退出码：0 = 全部在界内；1 = 存在越界节点或文件不可解析。

边界说明：Canvas 自绘的文字不在 view 树里，本脚本只能覆盖系统控件层
（按钮、输入框、弹窗、Toast 容器）。自绘层是否溢出仍需看截图。
"""
import sys
import xml.etree.ElementTree as ET

BOUNDS_RE = r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"


def parse_bounds(text):
    import re
    m = re.match(BOUNDS_RE, (text or "").strip())
    if not m:
        return None
    return tuple(int(v) for v in m.groups())


def main(argv):
    if len(argv) < 4:
        print("用法: check_bounds.py <dump.xml> <width> <height> [--tolerance N]")
        return 2

    path, w, h = argv[1], int(argv[2]), int(argv[3])
    tol = 2
    if "--tolerance" in argv:
        tol = int(argv[argv.index("--tolerance") + 1])

    try:
        tree = ET.parse(path)
    except Exception as exc:  # noqa: BLE001
        print(f"  [warn] 无法解析 {path}: {exc}")
        return 1

    offenders = []
    checked = 0
    for node in tree.iter("node"):
        b = parse_bounds(node.get("bounds"))
        if not b:
            continue
        x1, y1, x2, y2 = b
        if x2 - x1 <= 0 or y2 - y1 <= 0:
            continue
        checked += 1
        label = (node.get("text") or node.get("content-desc") or node.get("class") or "?").strip()
        if x1 < -tol or y1 < -tol or x2 > w + tol or y2 > h + tol:
            offenders.append((label[:40], b))

    if not offenders:
        print(f"  已检查 {checked} 个节点，未发现越界")
        return 0

    print(f"  已检查 {checked} 个节点，越界 {len(offenders)} 个（屏幕 {w}x{h}）:")
    for label, (x1, y1, x2, y2) in offenders[:15]:
        print(f"    - 「{label}」 bounds=[{x1},{y1}][{x2},{y2}]")
    if len(offenders) > 15:
        print(f"    ... 另有 {len(offenders) - 15} 个")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
