#!/usr/bin/env python3
"""离线验证 MiuiHome 动态定位方案；读取 APK，不连接设备，也不生成可直接注入的 profile。

需要 Python 的 lzma 标准库和 capstone 模块。主要地址由 .gnu_debugdata、调用关系及
重定位取得，不依赖 7751 的 RVA 或 APK 摘要白名单。模块使用同规则的 C++ 实现；
本脚本保留用于离线分析对照。
"""
import argparse
import hashlib
import json
import lzma
import struct
import zipfile
from collections import defaultdict
from pathlib import Path

from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from capstone.arm64 import ARM64_OP_REG, ARM64_OP_IMM, ARM64_OP_MEM

MD = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
MD.detail = True


def require(condition, message):
    if not condition:
        raise ValueError(message)


def one(values, label):
    values = list(values)
    require(len(values) == 1, f"{label}：需要唯一候选，实际为 {len(values)}")
    return values[0]


class Elf:
    def __init__(self, data):
        self.data = data
        require(data[:6] == b"\x7fELF\x02\x01" and len(data) >= 64, "需要小端 ELF64")
        require(struct.unpack_from("<H", data, 18)[0] == 183, "需要 AArch64 ELF")
        po, so = struct.unpack_from("<QQ", data, 32)
        ps, pn, ss, sn, si = struct.unpack_from("<HHHHH", data, 54)
        require(ss == 64 and sn and si < sn and so + ss*sn <= len(data), "节表越界")
        self.sections = [struct.unpack_from("<IIQQQQIIQQ", data, so+i*ss) for i in range(sn)]
        names = self.section_data(self.sections[si])
        self.named_sections = {self.string(names, h[0]): h for h in self.sections}
        require(not pn or (ps == 56 and po+ps*pn <= len(data)), "程序头越界")
        self.loads = [p for i in range(pn)
                      if (p := struct.unpack_from("<IIQQQQQQ", data, po+i*ps))[0] == 1]

    @staticmethod
    def string(data, offset):
        end = data.find(b"\0", offset)
        require(0 <= offset <= end < len(data), "字符串越界或未终止")
        return data[offset:end].decode("utf-8")

    def section_data(self, section):
        offset, size = section[4:6]
        require(offset+size <= len(self.data), "节内容越界")
        return self.data[offset:offset+size]

    def at(self, address, size, executable=False):
        for p in self.loads:
            if (not executable or p[1] & 1) and p[3] <= address and address+size <= p[3]+p[5]:
                offset = p[2]+address-p[3]
                require(offset+size <= len(self.data), "段内容越界")
                return self.data[offset:offset+size]
        raise ValueError(f"地址 {address:#x} 不在预期文件段中")

    def symbols(self):
        out = []
        for h in self.sections:
            if h[1] not in (2, 11):
                continue
            require(h[9] == 24 and h[6] < len(self.sections), "符号表格式错误")
            names = self.section_data(self.sections[h[6]])
            for ni, info, _, sec, value, size in struct.iter_unpack("<IBBHQQ", self.section_data(h)):
                if ni and sec:
                    out.append(dict(name=self.string(names, ni), rva=value, size=size, info=info))
        return out

    def mini_symbols(self):
        require(".gnu_debugdata" in self.named_sections, "缺少 .gnu_debugdata；原型不猜测业务地址")
        compressed = self.section_data(self.named_sections[".gnu_debugdata"])
        decoder = lzma.LZMADecompressor(memlimit=128*1024*1024)
        raw = decoder.decompress(compressed, max_length=16*1024*1024+1)
        require(decoder.eof and len(raw) <= 16*1024*1024, "调试数据损坏或超过大小上限")
        syms = Elf(raw).symbols()
        for sym in syms:
            if sym["info"] & 15 == 2:
                self.at(sym["rva"], max(sym["size"], 4), True)
        return syms

    def relocations(self):
        out = {}
        for h in self.sections:
            if h[1] == 4:  # SHT_RELA
                require(h[9] == 24, "不支持的重定位条目大小")
                for offset, info, addend in struct.iter_unpack("<QQq", self.section_data(h)):
                    if info & 0xffffffff == 1027:  # R_AARCH64_RELATIVE
                        require(offset not in out, "重复的相对重定位")
                        out[offset] = addend
        return out

    def imports(self):
        out = {}
        for h in self.sections:
            if h[1] != 4:
                continue
            require(h[6] < len(self.sections), "重定位符号表越界")
            symbols = self.sections[h[6]]
            require(symbols[9] == 24 and symbols[6] < len(self.sections), "导入符号表格式错误")
            entries = self.section_data(symbols)
            names = self.section_data(self.sections[symbols[6]])
            for offset, info, _ in struct.iter_unpack("<QQq", self.section_data(h)):
                if info & 0xffffffff == 1026:  # R_AARCH64_JUMP_SLOT
                    index = (info >> 32)*24
                    require(index+24 <= len(entries), "导入符号索引越界")
                    name = struct.unpack_from("<I", entries, index)[0]
                    out[offset] = self.string(names, name)
        return out


class Code:
    def __init__(self, elf):
        self.elf = elf
        self.symbols = elf.mini_symbols()
        self.names = defaultdict(list)
        for sym in self.symbols:
            if sym["info"] & 15 == 2:
                self.names[sym["name"]].append(sym)

    def named(self, name):
        return one(self.names[name], name)

    def body(self, sym):
        return list(MD.disasm(self.elf.at(sym["rva"], sym["size"], True), sym["rva"]))

    def large(self, name):
        # 同名的闭包解包器与方法本体并存；不能直接采用第一个同名符号。
        return one((s for s in self.names[name] if s["size"] > 256), name+" 方法本体")

    def allocation(self, address):
        words = struct.unpack("<III", self.elf.at(address, 12, True))
        if (words[0] & 0xffe0001f == 0xd2800002 and
                words[1] & 0xffe0001f == 0xf2a00002 and
                words[2] & 0xfc000000 == 0x14000000):
            tag = ((words[0] >> 5) & 0xffff) | (((words[1] >> 5) & 0xffff) << 16)
            return tag >> 12
        return None


def memory_offset(ins, mnemonic, destination, base):
    def register_name(reg):
        name = ins.reg_name(reg)
        return {"fp": "x29", "lr": "x30"}.get(name, name)

    ops = ins.operands
    if (ins.mnemonic == mnemonic and len(ops) == 2 and ops[0].type == ARM64_OP_REG and
            register_name(ops[0].reg) == destination and ops[1].type == ARM64_OP_MEM and
            register_name(ops[1].mem.base) == base):
        return ops[1].mem.disp
    return None


def address_references(code, sym):
    # 解析 ADRP/ADR + ADD 取址，供虚表构造关系校验使用。
    registers, refs = {}, set()
    for ins in code.body(sym):
        ops = ins.operands
        dest, value = None, None
        if ins.mnemonic in ("adrp", "adr") and len(ops) == 2:
            dest, value = ops[0].reg, ops[1].imm
        elif (ins.mnemonic == "add" and len(ops) == 3 and ops[1].type == ARM64_OP_REG and
              ops[2].type == ARM64_OP_IMM and ops[1].reg in registers):
            dest = ops[0].reg
            value = registers[ops[1].reg] + (ops[2].imm << ops[2].shift.value)
        for reg in ins.regs_access()[1]:
            registers.pop(reg, None)
        if dest is not None:
            registers[dest] = value
            refs.add(value)
        if ins.mnemonic in ("bl", "blr"):
            registers.clear()
    return refs


def address_pair(first, second, register):
    if (first.mnemonic == "adrp" and first.operands[0].reg == register and
            second.mnemonic == "add" and len(second.operands) == 3 and
            second.operands[0].reg == register and second.operands[1].reg == register and
            second.operands[2].type == ARM64_OP_IMM):
        return first.operands[1].imm + (second.operands[2].imm << second.operands[2].shift.value)
    return None


def resolve_runtime(code):
    elf, imports = code.elf, code.elf.imports()
    attach = one((s for s in code.symbols if "12app_launcher3app" in s["name"] and
                  s["name"].endswith("Application9on_attach")), "App::on_attach")
    body = code.body(attach)

    def imported_call(ins):
        if ins.mnemonic != "bl":
            return None
        target = ins.operands[0].imm
        plt = list(MD.disasm(elf.at(target, 16, True), target))
        if (len(plt) == 4 and plt[0].mnemonic == "adrp" and
                plt[0].op_str.startswith("x16, ") and
                memory_offset(plt[1], "ldr", "x17", "x16") is not None and
                plt[2].mnemonic == "add" and plt[2].op_str.startswith("x16, x16, ") and
                plt[3].mnemonic == "br" and plt[3].op_str == "x17"):
            got = plt[0].operands[1].imm + plt[1].operands[1].mem.disp
            return imports.get(got)
        return None

    slots = []
    for i in range(len(body)-7):
        w = body[i:i+8]
        if (w[0].mnemonic != "adrp" or not w[0].op_str.startswith("x8, ") or
                w[2].mnemonic != "ldar" or w[2].op_str != "w8, [x8]" or
                w[3].mnemonic != "cbnz" or not w[3].op_str.startswith("w8, ") or
                w[4].mnemonic != "adrp" or not w[4].op_str.startswith("x0, ")):
            continue
        state = address_pair(w[0], w[1], w[0].operands[0].reg)
        slot = address_pair(w[4], w[5], w[4].operands[0].reg)
        # 三个样本中第三个调用在取址与 BL 之间多一次 MOV；其目的寄存器不得为 x0。
        call = w[6]
        if (call.mnemonic == "mov" and call.operands[0].type == ARM64_OP_REG and
                call.reg_name(call.operands[0].reg) != "x0"):
            call = w[7]
        if state is None or slot is None or state != slot+8 or imported_call(call) != "content_resolver_new":
            continue
        require(any(p[1] & 7 == 6 and p[3] <= slot and state+4 <= p[3]+p[6]
                    for p in elf.loads), "Runtime 全局变量不在 RW 段中")
        slots.append(slot)
    require(len(slots) >= 2, "Runtime 缺少独立调用点交叉确认")
    slot = one(set(slots), "Runtime 全局变量")
    return dict(runtime_slot=slot, runtime_state=slot+8, runtime_confirmations=len(slots))


def resolve_dart(elf):
    c = Code(elf)
    start = c.large("_TaskWidgetState._handleDragStart")
    ins = c.body(start)
    accepted = []
    for i in range(len(ins)-15):
        w = ins[i:i+16]
        if (memory_offset(w[0], "ldur", "x1", "x29") == -8 and
                memory_offset(w[1], "ldr", "x0", "x26") == 0x78 and
                memory_offset(w[2], "ldr", "x0", "x0") is not None and
                memory_offset(w[3], "ldr", "x16", "x27") == 0x40 and
                w[4].mnemonic == "cmp" and w[4].op_str == "w0, w16" and
                w[5].mnemonic == "b.ne" and w[7].mnemonic == "bl" and
                memory_offset(w[10], "str", "x16", "x15") == 0 and
                w[12].mnemonic == "bl" and
                memory_offset(w[13], "ldur", "x1", "x29") == -8 and
                w[15].mnemonic == "bl"):
            accepted.append(w[0].address)
    targets = {"drag_start": one(accepted, "有效拖动开始位置")}
    for key, name in (("drag_end_vertical", "_TaskWidgetState._handleDragEndVertical"),
                      ("drag_end_horizontal", "_TaskWidgetState._handleDragEndHorizontal"),
                      ("pointer", "GestureBinding._handlePointerEventImmediately")):
        targets[key] = c.large(name)["rva"]
    targets["commit"] = c.named("RecentsViewController.killProcess")["rva"]

    create = c.body(c.named("MenuItemButton.createState"))
    state_cids = [cid for ins in create if ins.mnemonic == "bl"
                  if (cid := c.allocation(ins.operands[0].imm)) is not None]
    state_fields = {offset for ins in create
                    if (offset := memory_offset(ins, "stur", "w1", "x0")) is not None}
    for key, name in (("menu_down", "RecentsGlassPressLightMixin.glassPressLightDown"),
                      ("menu_up", "RecentsGlassPressLightMixin.glassPressLightUp")):
        candidates = []
        for sym in c.names[name]:
            fields = [value for ins in c.body(sym)
                      if (value := memory_offset(ins, "ldur", "w0", "x1")) is not None]
            if fields and fields[0] in state_fields:
                candidates.append(sym["rva"])
        targets[key] = one(candidates, key)

    info = c.named("RecentsViewController.itemShowApplicationInfo")
    closures = []
    for p in elf.loads:
        if not p[1] & 1:
            continue
        words = memoryview(elf.data)[p[2]:p[2]+p[5]//4*4].cast("I")
        for i, word in enumerate(words):
            if word & 0xfc000000 != 0x94000000:
                continue
            delta = word & 0x3ffffff
            if delta & 0x2000000:
                delta -= 0x4000000
            call = p[3]+4*i
            if call+4*delta != info["rva"] or i < 12:
                continue
            entry = call-48
            body = list(MD.disasm(elf.at(entry, 64, True), entry))
            if (body[0].mnemonic == "stp" and body[1].op_str == "x29, x15" and
                    memory_offset(body[2], "ldr", "x0", "x29") == 16 and
                    memory_offset(body[3], "ldur", "w1", "x0") == 0x17 and
                    memory_offset(body[5], "ldur", "w0", "x1") == 0xf and
                    memory_offset(body[7], "ldur", "w1", "x0") == 0x17 and
                    body[15].mnemonic == "ret"):
                closures.append(entry)
    targets["menu_click"] = one(closures, "应用信息 void 闭包")

    # 这些字段从对应具名方法/构造代码提取，不根据版本号选择常量。
    info_body = c.body(info)
    controller_fields = []
    for i, ins in enumerate(info_body[:-1]):
        if (ins.mnemonic == "mov" and ins.op_str.startswith("x17, #") and
                info_body[i+1].op_str == "w0, [x1, x17]"):
            controller_fields.append(ins.operands[1].imm)
    key_body = c.body(c.named("TaskKey.hashCode"))
    model_key = c.body(c.named("TaskCardModel.key"))
    package = c.body(c.named("getPackageName"))
    intent_reads = [(i, v) for i, ins in enumerate(package)
                    if (v := memory_offset(ins, "ldur", "w2", "x3")) is not None]
    require(len(intent_reads) == 2, "getPackageName 的 component/package 回退链发生变化")
    component_read, fallback_read = intent_reads
    require(package[component_read[0]+2].op_str == "w2, w22" and
            package[fallback_read[0]-2].op_str == "x2, x22", "Intent 字段使用关系发生变化")
    fields = dict(
        controller_task=one(set(controller_fields), "controller.task"),
        model_info=one({v for ins in model_key
                        if (v := memory_offset(ins, "ldur", "w3", "x1")) is not None}, "model.info"),
        info_key=one({v for ins in model_key
                      if (v := memory_offset(ins, "ldur", "w0", "x1")) is not None}, "info.key"),
        task_id=one({v for ins in key_body[:8]
                     if (v := memory_offset(ins, "ldur", "x2", "x0")) is not None}, "task.id"),
        user_id=one({v for ins in key_body[:8]
                     if (v := memory_offset(ins, "ldur", "x4", "x0")) is not None}, "task.user"),
        key_intent=one({v for ins in package
                        if (v := memory_offset(ins, "ldur", "w3", "x2")) is not None}, "key.intent"),
        intent_component=component_read[1],
        intent_package=fallback_read[1],
        component_package=one({v for ins in package
                               if (v := memory_offset(ins, "ldur", "w4", "x2")) is not None}, "component.package"),
        info_top_activity=one({v for ins in package
                               if (v := memory_offset(ins, "ldur", "w4", "x1")) is not None}, "info.topActivity"),
        pointer_delta=one({v for ins in c.body(c.named("_TransformedPointerEvent.delta"))
                           if (v := memory_offset(ins, "ldur", "w1", "x0")) is not None}, "pointer.delta"),
    )
    cids = {"menu_state": one(state_cids, "MenuItemButton State CID")}
    for kind in ("Down", "Up", "Move", "Cancel"):
        for role, name in (("transformed", f"Pointer{kind}Event.transformed"),
                           ("raw", f"_CopyPointer{kind}Event.copyWith")):
            found = set()
            for sym in c.names[name]:
                for ins in c.body(sym):
                    if ins.mnemonic == "bl" and (cid := c.allocation(ins.operands[0].imm)) is not None:
                        found.add(cid)
            cids[kind.lower()+"_"+role] = one(found, name+" 分配类型")
    dynamic = elf.symbols()
    snapshot = one((s for s in dynamic if s["name"] == "_kDartVmSnapshotData"), "VM snapshot")
    snapshot_hash = elf.at(snapshot["rva"]+20, 32).decode("ascii")
    return dict(targets=targets, fields=fields, cids=cids, snapshot_hash=snapshot_hash,
                mini_symbol_count=len(c.symbols))


def resolve_rust(elf):
    c = Code(elf)
    relocs = elf.relocations()

    def raw(address):
        return struct.unpack("<Q", elf.at(address, 8))[0]

    def function(parts):
        return one({s["rva"] for s in c.symbols if all(p in s["name"] for p in parts)},
                   "/".join(parts))

    def tables(functions, index, flags):
        out = []
        for offset, value in relocs.items():
            if value in functions:
                base = offset-index*8
                if raw(base) == flags:
                    out.append(base)
        return out

    receiver_path = "ContextImpl17register_receiver08Receiver"
    receive = function((receiver_path, "10on_receive"))
    pending_set = function((receiver_path, "18set_pending_result"))
    pending_get = function((receiver_path, "19get_pennding_result"))
    drop = function((receiver_path, "destroy_box"))
    receiver = one((v for v in tables({receive}, 3, 0x1f)
                    if relocs.get(v+32) == pending_set and relocs.get(v+40) == pending_get), "receiver 虚表")
    box = one(tables({drop}, 3, 7), "receiver RBox 虚表")
    mutex = one(tables({function(("parking_lot5mutex4lock",))}, 2, 0xf), "Mutex 虚表")
    string = one(tables({function(("14destructor_vechE",))}, 3, 0xf), "RString 虚表")
    clones = {s["rva"] for s in c.symbols if all(p in s["name"] for p in
              ("3arc10vtable_mod9clone_arc", "RMutex", "BroadcastReceiver"))}
    arcs = set(tables(clones, 4, 0x7f))
    selected = set()
    owners = []
    for sym in c.symbols:
        if "Context_register_receiver_impl" not in sym["name"]:
            continue
        refs = address_references(c, sym)
        if receiver in refs and box in refs:
            selected.update(arcs & refs)
            owners.append(sym["rva"])
    arc = one(selected, "与实际 receiver 构造关联的 Arc 虚表")
    return dict(**resolve_runtime(c), receiver_vtable=receiver, box_vtable=box, mutex_vtable=mutex,
                arc_vtable=arc, arc_candidates=len(arcs), receiver_owners=owners,
                string_vtable=string, mini_symbol_count=len(c.symbols))


def hex_values(value):
    if isinstance(value, dict):
        return {k: (v if k.endswith("_count") or k.endswith("_candidates") or
                    k.endswith("_confirmations") else hex_values(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [hex_values(v) for v in value]
    if isinstance(value, int):
        return hex(value)
    return value


def analyze(path):
    result = {"apk": path.name, "apk_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
    with zipfile.ZipFile(path) as archive:
        for name, resolver in (("libapp.so", resolve_dart), ("libapp_launcher.so", resolve_rust)):
            data = archive.read("lib/arm64-v8a/"+name)
            result[name] = dict(sha256=hashlib.sha256(data).hexdigest(), size=len(data),
                                resolved=hex_values(resolver(Elf(data))))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, nargs="+")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    results = []
    for path in args.apk:
        results.append(analyze(path))
        print(f"通过：{path.name} 的回调、关键字段/CID、Runtime 与广播虚表离线定位")
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2)+"\n")


if __name__ == "__main__":
    main()
