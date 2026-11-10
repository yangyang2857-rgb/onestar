#!/usr/bin/env python3
"""校验 APK 内 .so 是否满足 API 34+ 的装载要求。

android:extractNativeLibs="false" 时安装器直接 mmap APK 里的 .so，要求：
  - 条目 STORED（不压缩）
  - 数据偏移按页对齐（16 KB 页设备要求 16384 倍数）
不满足会报 INSTALL_FAILED_INVALID_APK: Failed to extract native libraries。

用法: check_align.py <apk> [对齐字节数，默认 16384]
"""
import struct
import sys
import zipfile


def main():
    if len(sys.argv) < 2:
        print("用法: check_align.py <apk> [align]")
        return 2
    apk = sys.argv[1]
    align = int(sys.argv[2]) if len(sys.argv) > 2 else 16384
    z = zipfile.ZipFile(apk)
    bad = 0
    total = 0
    for info in z.infolist():
        if not info.filename.endswith(".so"):
            continue
        total += 1
        z.fp.seek(info.header_offset)
        header = z.fp.read(30)
        name_len, extra_len = struct.unpack("<HH", header[26:30])
        data_off = info.header_offset + 30 + name_len + extra_len
        ok = data_off % align == 0 and info.compress_type == 0
        print("  %-34s off=%-9d %s" % (info.filename, data_off, "OK" if ok else "BAD"))
        if not ok:
            bad += 1
    if total == 0:
        print("  （无 .so，跳过）")
        return 0
    print("  check: %d 个 .so，%d 个不合规" % (total, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
