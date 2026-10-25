#!/usr/bin/env python3
"""Offline HYOS checks. No device connection, APK install, or hook activation."""
import argparse
import hashlib
import re
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / "app/src/main/cpp/miuihome"


def executable(blob, address, count):
    assert blob[:6] == b"\x7fELF\x02\x01"
    phoff = struct.unpack_from("<Q", blob, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", blob, 54)
    for i in range(phnum):
        kind, flags, offset, va, _, filesz, _, _ = struct.unpack_from("<IIQQQQQQ", blob, phoff+i*phsize)
        if kind == 1 and flags & 1 and va <= address and address+count <= va+filesz:
            return blob[offset+address-va:offset+address-va+count]
    raise AssertionError(f"RVA {address:#x} is not in an executable segment")


def profile(apk):
    with zipfile.ZipFile(apk) as archive:
        images = {
            "libapp_launcher.so": "89302282d70e3b40126ccce05fef94560529c0543ff6e05107724dade0d104a7",
            "libapp.so": "451c79322ddcb1e3d3bd48657bda591a4370b1d8843967573f8601c7ee64695c",
        }
        for name, expected in images.items():
            data = archive.read("lib/arm64-v8a/"+name)
            assert hashlib.sha256(data).hexdigest() == expected, name+" hash mismatch"
        dart = archive.read("lib/arm64-v8a/libapp.so")
        entries = re.findall(r"\{(0x[0-9a-f]+), \{([^}]+)\}\}", (NATIVE/"profile_7751.h").read_text())
        assert len(entries) == 8
        for rva, raw in entries:
            prefix = bytes(int(x, 16) for x in re.findall(r"0x[0-9a-f]+", raw))
            assert executable(dart, int(rva, 16), len(prefix)) == prefix, rva+" fingerprint mismatch"
    print("PASS: 7751 image hashes and all 8 hook-site fingerprints")


def module(apk):
    with zipfile.ZipFile(apk) as archive:
        assert archive.read("META-INF/xposed/native_init.list").strip() == b"libmyinjector_miuihome.so"
        entry = archive.getinfo("lib/arm64-v8a/libmyinjector_miuihome.so")
        assert entry.compress_type == zipfile.ZIP_STORED
        with Path(apk).open("rb") as stream:
            stream.seek(entry.header_offset)
            header = stream.read(30)
        nlen, xlen = struct.unpack_from("<HH", header, 26)
        assert (entry.header_offset+30+nlen+xlen) % 16384 == 0, "native ZIP entry alignment"
        data = archive.read(entry)
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phnum = struct.unpack_from("<HH", data, 54)
        for i in range(phnum):
            p = struct.unpack_from("<IIQQQQQQ", data, phoff+i*phsize)
            if p[0] == 1:
                assert p[7] >= 16384, "native ELF segment alignment"
    print("PASS: LSPosed native metadata and 16 KiB ELF/APK alignment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path)
    parser.add_argument("--module", type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="myinjector-native-") as tmp:
        binary = str(Path(tmp)/"state-test")
        subprocess.run(["clang++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                        "-I", str(NATIVE), str(ROOT/"tests/miuihome_native_test.cpp"), "-o", binary], check=True)
        subprocess.run([binary], check=True)
    if args.launcher:
        profile(args.launcher)
    if args.module:
        module(args.module)


if __name__ == "__main__":
    main()
