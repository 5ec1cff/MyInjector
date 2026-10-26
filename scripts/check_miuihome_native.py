#!/usr/bin/env python3
"""HYOS 离线检查：不连接设备，不安装 APK，也不启用 hook。"""
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


def mapped_bytes(blob, address, count, executable=False):
    assert blob[:6] == b"\x7fELF\x02\x01"
    phoff = struct.unpack_from("<Q", blob, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", blob, 54)
    for i in range(phnum):
        kind, flags, offset, va, _, filesz, _, _ = struct.unpack_from("<IIQQQQQQ", blob, phoff+i*phsize)
        if kind == 1 and (not executable or flags & 1) and va <= address and address+count <= va+filesz:
            return blob[offset+address-va:offset+address-va+count]
    raise AssertionError(f"RVA {address:#x} 不在预期 ELF 段中")


def relative_relocations(blob):
    # 虚表在 ELF 文件中的指针为零，加载器通过 RELATIVE 重定位填入 RVA。
    phoff = struct.unpack_from("<Q", blob, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", blob, 54)
    dynamic = {}
    for i in range(phnum):
        p = struct.unpack_from("<IIQQQQQQ", blob, phoff+i*phsize)
        if p[0] == 2:
            for pos in range(p[2], p[2]+p[5], 16):
                tag, value = struct.unpack_from("<QQ", blob, pos)
                if tag == 0:
                    break
                dynamic[tag] = value
    assert dynamic[9] == 24
    table = mapped_bytes(blob, dynamic[7], dynamic[8])
    return {offset: addend for offset, info, addend in struct.iter_unpack("<QQq", table)
            if info & 0xffffffff == 1027}  # R_AARCH64_RELATIVE


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
        launcher = archive.read("lib/arm64-v8a/libapp_launcher.so")
        source = (NATIVE/"profile_7751.h").read_text()
        entries = re.findall(r"\{(0x[0-9a-f]+), \{([^}]+)\}\}", source)
        assert len(entries) == 8
        for rva, raw in entries:
            prefix = bytes(int(x, 16) for x in re.findall(r"0x[0-9a-f]+", raw))
            assert mapped_bytes(dart, int(rva, 16), len(prefix), True) == prefix, rva+" 指纹不匹配"
        constants = {k: int(v, 16) for k, v in re.findall(r"constexpr uintptr_t (\w+) = (0x[0-9a-f]+);", source)}
        checks = {
            "kRuntimeSlot": {0: 0, 1: 3},
            "kReceiverVtable": {0: 0x1f, 3: 0x7300b8, 4: 0x730164, 5: 0x7301a8},
            "kReceiverBoxVtable": {0: 7, 3: 0x5b4b48},
            "kReceiverMutexVtable": {0: 0xf, 2: 0x9be250, 4: 0x9be2b0},
            "kReceiverArcVtable": {0: 0x7f, 3: 0xb1a5fc, 4: 0xb1a634},
        }
        relocated = relative_relocations(launcher)
        for name, words in checks.items():
            for index, expected in words.items():
                address = constants[name]+8*index
                actual = relocated.get(address)
                if actual is None:
                    actual = struct.unpack("<Q", mapped_bytes(launcher, address, 8))[0]
                assert actual == expected, name+" 广播 ABI 布局不匹配"
    print("通过：7751 ELF 摘要、8 处 hook 指纹、Runtime/receiver 虚表布局")


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
    print("通过：LSPosed native 入口、ELF/APK 的 16KB 对齐")


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
