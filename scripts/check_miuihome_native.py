#!/usr/bin/env python3
"""HYOS 离线检查：不连接设备，不安装 APK，也不启用 hook。"""
import argparse
import hashlib
import json
import lzma
import warnings
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


def sections(blob):
    offset = struct.unpack_from("<Q", blob, 40)[0]
    size, count, strings = struct.unpack_from("<HHH", blob, 58)
    result = [struct.unpack_from("<IIQQQQIIQQ", blob, offset+i*size) for i in range(count)]
    names = result[strings]
    names = blob[names[4]:names[4]+names[5]]
    named = {}
    for i, h in enumerate(result):
        name = names[h[0]:names.index(0, h[0])].decode()
        named[name] = (offset+i*size, h)
    return named, result


def file_offset(blob, address):
    phoff = struct.unpack_from("<Q", blob, 32)[0]
    phsize, phnum = struct.unpack_from("<HH", blob, 54)
    for i in range(phnum):
        p = struct.unpack_from("<IIQQQQQQ", blob, phoff+i*phsize)
        if p[0] == 1 and p[3] <= address < p[3]+p[5]:
            return p[2]+address-p[3]
    raise AssertionError("RVA 越界")


def profile(apk, binary):
    result = subprocess.run([str(binary), "--check-mapped", str(apk)], check=True,
                            text=True, capture_output=True)
    actual = json.loads(result.stdout)
    samples = json.loads((ROOT/"docs/miuihome-resolver-samples.json").read_text())
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    expected = next((s for s in samples if s["apk_sha256"] == digest), None)
    if expected:
        dart = expected["libapp.so"]["resolved"]
        order = ["drag_start", "drag_end_vertical", "drag_end_horizontal", "commit",
                 "menu_down", "menu_up", "menu_click", "pointer"]
        assert actual["targets"] == [dart["targets"][key] for key in order]
        assert actual["cids"] == dart["cids"]
        for key, value in dart["fields"].items():
            assert actual["fields"][key] == value, key
        for key, value in actual["rust"].items():
            assert expected["libapp_launcher.so"]["resolved"][key] == value, key
        print(f"通过：{apk.name} 的 C++ 动态解析与已复核样本一致；映像校验正反例通过")
    else:
        print(f"解析成功：{apk.name}；映像校验正反例通过，尚无独立样本基准")
    return actual


def negative_profile(apk, binary, directory, actual):
    with zipfile.ZipFile(apk) as z:
        dart = z.read("lib/arm64-v8a/libapp.so")
        rust = z.read("lib/arm64-v8a/libapp_launcher.so")
    dp, rp = Path(directory)/"dart.so", Path(directory)/"rust.so"
    dp.write_bytes(dart); rp.write_bytes(rust)
    standalone = subprocess.run([str(binary), str(dp), str(rp)], text=True,
                                capture_output=True, check=True)
    assert json.loads(standalone.stdout) == actual, "独立 ELF 与 APK 路径结果不一致"

    def rejected(label, expected, d=dart, r=rust, arguments=None):
        dp.write_bytes(d); rp.write_bytes(r)
        result = subprocess.run([str(binary), *(arguments or [str(dp), str(rp)])],
                                text=True, capture_output=True)
        assert result.returncode == 1, (label, result.returncode, result.stderr)
        assert expected in result.stderr, (label, result.stderr)

    named, _ = sections(dart)
    sh, debug = named[".gnu_debugdata"]
    missing = bytearray(dart); struct.pack_into("<I", missing, sh, 0)
    rejected("移除调试节", ".gnu_debugdata", d=missing)
    corrupt = bytearray(dart); corrupt[debug[4]] ^= 0x80
    rejected("损坏 XZ", "XZ", d=corrupt)
    rejected("截断 ELF", "ELF", d=dart[:64])
    abi = bytearray(dart)
    version = b"5d85d85296400e9b3992193deed317c9"
    abi[abi.index(version)] = ord("0")
    rejected("未知 VM ABI", "snapshot ABI", d=abi)
    entry = bytearray(dart)
    where = file_offset(entry, int(actual["targets"][3], 16)+12)
    struct.pack_into("<I", entry, where, 0xaa0003e0)  # 改变 Commit 的参数搬运约定。
    rejected("参数约定变化", "argument convention", d=entry)
    arc = bytearray(rust)
    address = int(actual["rust"]["arc_vtable"], 16)
    clone = relative_relocations(rust)[address+32]
    struct.pack_into("<I", arc, file_offset(arc, clone+4), 0xd1008008)
    rejected("Arc 计数头变化", "Arc strong/weak", r=arc)

    # 在精简符号表中制造另一个同名函数，保留合法的代码地址和完整 XZ 校验。
    mini = bytearray(lzma.decompress(dart[debug[4]:debug[4]+debug[5]]))
    mn, ms = sections(mini); _, symtab = mn[".symtab"]
    strings = ms[symtab[6]]; strings = mini[strings[4]:strings[4]+strings[5]]
    target_name, other = None, None
    for at in range(symtab[4], symtab[4]+symtab[5], 24):
        ni, info, _, section, _, size = struct.unpack_from("<IBBHQQ", mini, at)
        if info & 15 != 2 or not section or not size: continue
        name = strings[ni:strings.index(0, ni)]
        if name == b"getPackageName": target_name = ni
        elif other is None: other = at
    assert target_name is not None and other is not None
    struct.pack_into("<I", mini, other, target_name)
    packed = lzma.compress(mini)
    ambiguous = bytearray(dart)
    struct.pack_into("<QQ", ambiguous, sh+24, len(ambiguous), len(packed))
    ambiguous.extend(packed)
    rejected("同名候选歧义", "getPackageName: 2 candidates", d=ambiguous)

    testzip = Path(directory)/"unsupported.apk"
    with zipfile.ZipFile(testzip, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("lib/arm64-v8a/libapp.so", dart)
    rejected("压缩 native 条目", "STORED", arguments=[str(testzip)])
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(testzip, "w") as z:
            z.writestr("lib/arm64-v8a/libapp.so", dart)
            z.writestr("lib/arm64-v8a/libapp.so", dart)
    rejected("重名 native 条目", "not unique", arguments=[str(testzip)])
    print("通过：独立 ELF 路径、缺少/损坏调试数据、未知 ABI、调用约定/Arc 布局变化、符号歧义及 ZIP 错误拒绝")


def module(apk):
    with zipfile.ZipFile(apk) as archive:
        assert archive.read("META-INF/xposed/native_init.list").strip() == b"libmyinjector_miuihome.so"
        assert b"targetApiVersion=102" in archive.read("META-INF/xposed/module.prop")
        entry = archive.getinfo("lib/arm64-v8a/libmyinjector_miuihome.so")
        assert entry.compress_type == zipfile.ZIP_STORED
        with Path(apk).open("rb") as stream:
            stream.seek(entry.header_offset)
            header = stream.read(30)
        nlen, xlen = struct.unpack_from("<HH", header, 26)
        assert (entry.header_offset+30+nlen+xlen) % 16384 == 0, "native ZIP entry alignment"
        data = archive.read(entry)
        named, all_sections = sections(data)
        _, dynsym = named[".dynsym"]
        names = all_sections[dynsym[6]]
        names = data[names[4]:names[4]+names[5]]
        exports = set()
        for ni, info, visibility, section, _, _ in struct.iter_unpack(
                "<IBBHQQ", data[dynsym[4]:dynsym[4]+dynsym[5]]):
            if ni and section and info >> 4 in (1, 2) and visibility & 3 == 0:
                exports.add(names[ni:names.index(0, ni)].decode())
        assert exports == {"native_init"}, exports
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phnum = struct.unpack_from("<HH", data, 54)
        for i in range(phnum):
            p = struct.unpack_from("<IIQQQQQQ", data, phoff+i*phsize)
            if p[0] == 1:
                assert p[7] >= 16384, "native ELF segment alignment"
    print("通过：LSPosed native 入口、仅导出 native_init、ELF/APK 的 16KB 对齐")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path, action="append", default=[])
    parser.add_argument("--resolver", type=Path, help="复用已构建的 C++ 解析器（可用于 ASan/UBSan 构建）")
    parser.add_argument("--module", type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="myinjector-native-") as tmp:
        binary = str(Path(tmp)/"state-test")
        subprocess.run(["clang++", "-std=c++17", "-Wall", "-Wextra", "-Werror",
                        "-I", str(NATIVE), str(ROOT/"tests/miuihome_native_test.cpp"), "-o", binary], check=True)
        subprocess.run([binary], check=True)
        if args.launcher:
            resolver = args.resolver
            if resolver is None:
                build = Path(tmp)/"resolver"
                subprocess.run(["cmake", "-S", str(NATIVE), "-B", str(build),
                                "-DCMAKE_BUILD_TYPE=RelWithDebInfo", "-DCMAKE_C_COMPILER=clang",
                                "-DCMAKE_CXX_COMPILER=clang++"], check=True, stdout=subprocess.DEVNULL)
                subprocess.run(["cmake", "--build", str(build), "-j", "4"], check=True,
                               stdout=subprocess.DEVNULL)
                resolver = build/"miuihome_resolver_cli"
            for index, apk in enumerate(args.launcher):
                actual = profile(apk, resolver)
                if index == 0: negative_profile(apk, resolver, tmp, actual)
    if args.module:
        module(args.module)


if __name__ == "__main__":
    main()
