#!/usr/bin/env python3
"""拾光 v5.2 · zip16k
build-tools 34 的 zipalign 不支持 -P 16，这里自行重打包：
  - lib/**/*.so 以 STORED（不压缩）存放，数据偏移对齐到 16384
  - 其他条目按 4 字节对齐，能压缩的压缩
用法: zip16k.py <src.apk> <dst.apk> [--check]
"""
import struct
import sys
import zipfile
import zlib

ALIGN_SO = 16384
ALIGN_DEF = 4


def needed(base, align):
    e = (-base) % align
    if e < 4:          # 扩展字段至少要 4 字节（id + len）
        e += align
    return e


def repack(src, dst):
    zin = zipfile.ZipFile(src)
    out = open(dst, 'wb')
    central = []
    for info in zin.infolist():
        if info.filename.endswith('/'):
            continue
        name = info.filename.encode('utf-8')
        raw = zin.read(info.filename)
        is_so = name.endswith(b'.so')
        if is_so or info.compress_type == 0:
            # .so 便于 mmap 直读；resources.arsc 在 Android 11+ 必须不压缩
            method, data = 0, raw
        else:
            co = zlib.compressobj(9, zlib.DEFLATED, -15)
            packed = co.compress(raw) + co.flush()
            if len(packed) < len(raw):
                method, data = 8, packed
            else:
                method, data = 0, raw
        crc = zlib.crc32(raw) & 0xFFFFFFFF
        y, mo, d, h, mi, s = info.date_time
        dosdate = ((y - 1980) << 9) | (mo << 5) | d
        dostime = (h << 11) | (mi << 5) | (s // 2)
        align = ALIGN_SO if is_so else ALIGN_DEF

        start = out.tell()
        extra_len = needed(start + 30 + len(name), align)
        extra = struct.pack('<HH', 0xD935, extra_len - 4) + b'\x00' * (extra_len - 4)
        header = struct.pack('<IHHHHHIIIHH', 0x04034B50, 20, 0, method, dostime, dosdate,
                             crc, len(data), len(raw), len(name), len(extra))
        out.write(header + name + extra)
        offset = out.tell()
        assert offset % align == 0, (info.filename, offset, align)
        out.write(data)
        central.append((name, method, dostime, dosdate, crc, len(data), len(raw),
                        start, len(extra)))

    cd_start = out.tell()
    for (name, method, dostime, dosdate, crc, csize, usize, offset, extra_len) in central:
        extra = struct.pack('<HH', 0xD935, extra_len - 4) + b'\x00' * (extra_len - 4)
        out.write(struct.pack('<IHHHHHHIIIHHHHHII', 0x02014B50, 20, 20, 0, method,
                              dostime, dosdate, crc, csize, usize, len(name), len(extra),
                              0, 0, 0, 0, offset) + name + extra)
    cd_size = out.tell() - cd_start
    n = len(central)
    out.write(struct.pack('<IHHHHIIH', 0x06054B50, 0, 0, n, n, cd_size, cd_start, 0))
    out.close()
    return n


def check(apk):
    zin = zipfile.ZipFile(apk)
    bad = 0
    for info in zin.infolist():
        if info.filename.endswith('/'):
            continue
        is_so = info.filename.endswith('.so')
        align = ALIGN_SO if is_so else ALIGN_DEF
        off = info.header_offset + 30 + len(info.filename.encode('utf-8')) + len(info.extra)
        ok = (off % align == 0) and (not is_so or info.compress_type == 0)
        if is_so or not ok:
            print("  %-32s off=%-9d align=%-6d %s" %
                  (info.filename, off, align, "OK" if ok else "BAD"))
        if not ok:
            bad += 1
    print("  check: %d entries, %d bad" % (len(zin.infolist()), bad))
    return bad


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[3:] == ['--check']:
        sys.exit(1 if check(sys.argv[1]) else 0)
    n = repack(sys.argv[1], sys.argv[2])
    print("  repacked %d entries -> %s" % (n, sys.argv[2]))
    check(sys.argv[2])
