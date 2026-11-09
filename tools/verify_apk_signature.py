#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 APK 的签名证书，并比对仓库约定的固定指纹。

为什么需要它：Android 只允许「同包名 + 同证书 + versionCode 不降」的包覆盖安装。
CI 曾经每次构建都现场生成一张 debug 证书，导致每次产物的证书指纹都不同，
已装用户升级时必然 INSTALL_FAILED_UPDATE_INCOMPATIBLE。
把证书固定下来之后，用这个脚本在构建末尾兜一道，指纹一旦漂移就让流水线失败。

用法：
    python3 tools/verify_apk_signature.py build/onestar-0.12.2-signed.apk
    python3 tools/verify_apk_signature.py build/onestar-0.12.2-signed.apk --print-only

退出码：0 一致；1 指纹不符；2 无法读取签名信息。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

# 仓库约定：所有可覆盖升级的产物都必须由这张证书签名
EXPECTED_SHA256 = (
    "B0:F1:E2:A1:36:84:16:90:0A:AC:9C:A2:F9:9F:19:1C:"
    "3F:E2:C4:13:43:50:3A:7F:38:A0:F7:98:79:D5:4C:D1"
)
EXPECTED_SUBJECT = "CN=OneStar Debug"


def extract_pkcs7(apk_path):
    """APK 的 v1 签名把 PKCS#7 证书块放在 META-INF/ 下，取出第一块。"""
    with zipfile.ZipFile(apk_path) as zf:
        for name in zf.namelist():
            upper = name.upper()
            if upper.startswith("META-INF/") and upper.endswith((".RSA", ".DSA", ".EC")):
                return name, zf.read(name)
    return None, None


def via_openssl(der_bytes):
    """用 openssl 解析 PKCS#7，返回 (subject, sha256, sha1)。"""
    tmpdir = tempfile.mkdtemp(prefix="apksig-")
    try:
        p7 = os.path.join(tmpdir, "sig.p7")
        pem = os.path.join(tmpdir, "cert.pem")
        with open(p7, "wb") as f:
            f.write(der_bytes)
        subprocess.run(
            ["openssl", "pkcs7", "-inform", "DER", "-in", p7, "-print_certs", "-out", pem],
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        out = subprocess.run(
            ["openssl", "x509", "-in", pem, "-noout", "-subject", "-fingerprint", "-sha256"],
            check=True, capture_output=True, text=True,
        ).stdout
        subject = re.search(r"subject=\s*(.+)", out).group(1).strip()
        sha256 = re.search(r"SHA256 Fingerprint=([0-9A-F:]+)", out, re.I).group(1).upper()
        return subject, sha256
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


def via_apksigner(apk_path):
    """没有 openssl 时退回 apksigner --print-certs。"""
    exe = os.environ.get("APKSIGNER") or shutil.which("apksigner")
    if not exe:
        return None
    out = subprocess.run(
        [exe, "verify", "--print-certs", apk_path], capture_output=True, text=True
    )
    text = out.stdout + out.stderr
    m = re.search(r"SHA-256 digest:\s*([0-9a-f]+)", text, re.I)
    if not m:
        return None
    digest = m.group(1).upper()
    sha256 = ":".join(digest[i:i + 2] for i in range(0, len(digest), 2))
    s = re.search(r"Signer #1 certificate DN:\s*(.+)", text)
    return (s.group(1).strip() if s else "unknown"), sha256


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    print_only = "--print-only" in sys.argv
    if not args:
        print(__doc__)
        return 2
    apk = args[0]

    entry, der = extract_pkcs7(apk)
    if not der:
        print("!! %s 里没有 v1 签名块（META-INF/*.RSA），无法校验" % apk)
        return 2

    info = None
    if shutil.which("openssl"):
        try:
            info = via_openssl(der)
        except Exception:
            info = None
    if not info:
        info = via_apksigner(apk)
    if not info:
        print("!! 既没有 openssl 也没有 apksigner，无法解析 %s" % entry)
        return 2

    subject, sha256 = info
    print("签名块：%s" % entry)
    print("证书主题：%s" % subject)
    print("SHA-256：%s" % sha256)

    if print_only:
        return 0

    if sha256 != EXPECTED_SHA256:
        print("")
        print("!! 证书指纹与仓库约定不一致，产物无法覆盖升级已装版本")
        print("!! 期望：%s" % EXPECTED_SHA256)
        print("!! 实际：%s" % sha256)
        print("!! 检查 ONESTAR_KS / ONESTAR_KS_B64 是否指向同一张 keystore")
        return 1

    # openssl 输出形如 "C = CN, O = OneStar, CN = OneStar Debug"，去掉空格再比
    if re.sub(r"\s+", "", EXPECTED_SUBJECT) not in re.sub(r"\s+", "", subject):
        print("!! 指纹一致但证书主题异常：%s" % subject)
        return 1

    print("校验通过：与仓库固定证书一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
