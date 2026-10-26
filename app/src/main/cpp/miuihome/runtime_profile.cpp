#include "resolver_elf.h"
#include "sha256.h"
#include <algorithm>
#include <initializer_list>
#include <set>
#include <string.h>

namespace miuihome {
namespace {
using namespace resolver;
constexpr uint32_t LdurW = 0xb8400000, LdurX = 0xf8400000, SturW = 0xb8000000;
struct Method {
    const Elf &elf;
    Symbol symbol{};
    uint32_t operator[](size_t i) const {
        return i < symbol.size / 4 ? elf.instruction(symbol.rva + 4 * i) : 0;
    }
    size_t count() const { return symbol.size / 4; }
    bool valid() const {
        return symbol.size && symbol.size % 4 == 0 &&
               elf.at(symbol.rva, symbol.size, PF_R | PF_X).size;
    }
};
bool unique(const std::set<uintptr_t> &values, uintptr_t &out, ResolverError &e,
            const char *label) {
    if (values.size() != 1)
        return fail(e, "%s: %zu candidates", label, values.size());
    out = *values.begin();
    return true;
}
bool offset(const Method &m, uint32_t opcode, unsigned dst, unsigned base, uint32_t &out,
            ResolverError &e, const char *label, size_t maxInstructions = SIZE_MAX) {
    std::set<uintptr_t> values;
    for (size_t i = 0; i < std::min(m.count(), maxInstructions); ++i) {
        int value = 0;
        if (unscaled(m[i], opcode, dst, base, value)) {
            if (value < 0 || value > 4095 || value % 4 != 3)
                return fail(e, "%s: invalid tagged offset", label);
            values.insert(uintptr_t(value));
        }
    }
    uintptr_t value = 0;
    if (!unique(values, value, e, label))
        return false;
    out = uint32_t(value);
    return true;
}
bool method(const Elf &elf, const MiniDebug &debug, const char *name, Method &out, ResolverError &e,
            size_t minSize = 0, bool frame = false) {
    if (!debug.unique(name, out.symbol, e, minSize) || !out.valid())
        return fail(e, "invalid method: %s", name);
    if (frame && (out[0] != 0xa9bf79fd || out[1] != 0xaa0f03fd))
        return fail(e, "unsupported Dart frame: %s", name);
    (void)elf;
    return true;
}
uint32_t allocation(const Elf &elf, uintptr_t pc) {
    uint32_t a = elf.instruction(pc), b = elf.instruction(pc + 4), c = elf.instruction(pc + 8);
    if ((a & 0xffe0001f) != 0xd2800002 || (b & 0xffe0001f) != 0xf2a00002 ||
        (c & 0xfc000000) != 0x14000000)
        return 0;
    uint32_t tag = ((a >> 5) & 0xffff) | (((b >> 5) & 0xffff) << 16);
    return (tag >> 12) & 0xfffff;
}
bool resolveDart(const Elf &elf, RuntimeProfile &p, ResolverError &e) {
    // 该版本号属于 VM snapshot 格式，而非桌面 APK；仍需逐项核对业务布局和入口约定。
    std::vector<Symbol> snapshot;
    for (const auto &s : elf.symbols)
        if (s.name == "_kDartVmSnapshotData")
            snapshot.push_back(s);
    if (snapshot.size() != 1)
        return fail(e, "VM snapshot data not unique");
    Bytes vm = elf.at(snapshot[0].rva, 256);
    static constexpr char version[] = "5d85d85296400e9b3992193deed317c9";
    uint32_t magic = 0;
    vm.read(0, magic);
    if (!vm.size || magic != 0xdcdcf5f5 || memcmp(vm.data + 20, version, 32))
        return fail(e, "unsupported Dart VM snapshot ABI");
    const char *features = reinterpret_cast<const char *>(vm.data + 52);
    const auto *end = static_cast<const char *>(memchr(features, 0, vm.size - 52));
    if (!end || std::string_view(features, size_t(end - features))
                        .find("arm64 android compressed-pointers") == std::string_view::npos)
        return fail(e, "unsupported Dart snapshot features");
    MiniDebug debug;
    if (!debug.init(elf, e))
        return false;
    DartLayout &d = p.dart;
    Method start{elf}, vertical{elf}, horizontal{elf}, pointer{elf}, commit{elf}, create{elf};
    if (!method(elf, debug, "_TaskWidgetState._handleDragStart", start, e, 257, true) ||
        !method(elf, debug, "_TaskWidgetState._handleDragEndVertical", vertical, e, 257, true) ||
        !method(elf, debug, "_TaskWidgetState._handleDragEndHorizontal", horizontal, e, 257,
                true) ||
        !method(elf, debug, "GestureBinding._handlePointerEventImmediately", pointer, e, 257,
                true) ||
        !method(elf, debug, "RecentsViewController.killProcess", commit, e, 0, true) ||
        !method(elf, debug, "MenuItemButton.createState", create, e, 0, true))
        return false;
    int value = 0;
    if (!unscaled(vertical[3], 0xf8000000, 1, 29, value) || value != -8 ||
        !unscaled(horizontal[3], 0xf8000000, 1, 29, value) || value != -8 ||
        commit[3] != 0xaa0103e0 || commit[4] != 0xaa0203e1 || pointer[3] != 0xaa0203e0 ||
        pointer[4] != 0xaa0103e4 || pointer[5] != 0xaa0203e3)
        return fail(e, "Dart observer argument convention changed");
    p.targets[DragEndVertical].rva = vertical.symbol.rva;
    p.targets[DragEndHorizontal].rva = horizontal.symbol.rva;
    p.targets[Commit].rva = commit.symbol.rva;
    p.targets[Pointer].rva = pointer.symbol.rva;
    std::set<uintptr_t> accepted;
    for (size_t i = 0; i + 16 <= start.count(); ++i) {
        uint32_t a = 0, b = 0;
        uintptr_t target = 0;
        if (unscaled(start[i], LdurX, 1, 29, value) && value == -8 &&
            ldr64(start[i + 1], 0, 26, a) && a == 0x78 && ldr64(start[i + 2], 0, 0, b) &&
            ldr64(start[i + 3], 16, 27, a) && a == 0x40 && start[i + 4] == 0x6b10001f &&
            (start[i + 5] & 0xff00001f) == 0x54000001 &&
            branch(start[i + 7], start.symbol.rva + (i + 7) * 4, target) &&
            start[i + 10] == 0xf90001f0 &&
            branch(start[i + 12], start.symbol.rva + (i + 12) * 4, target) &&
            unscaled(start[i + 13], LdurX, 1, 29, value) && value == -8 &&
            branch(start[i + 15], start.symbol.rva + (i + 15) * 4, target))
            accepted.insert(start.symbol.rva + i * 4);
    }
    if (!unique(accepted, p.targets[DragStart].rva, e, "accepted drag start"))
        return false;
    // State.widget -> TaskWidget.task：依据相邻加载、空值检查及指针解压提取。
    std::set<uintptr_t> widgetOffsets, taskOffsets;
    for (size_t i = 0; i + 6 <= start.count(); ++i) {
        int a = 0, b = 0;
        if (unscaled(start[i], LdurW, 1, 2, a) && start[i + 1] == 0x8b1c8021 &&
            start[i + 2] == 0x6b16003f && (start[i + 3] & 0xff00001f) == 0x54000000 &&
            unscaled(start[i + 4], LdurW, 4, 1, b) && start[i + 5] == 0x8b1c8084 && a > 0 &&
            b > 0) {
            widgetOffsets.insert(uintptr_t(a));
            taskOffsets.insert(uintptr_t(b));
        }
    }
    uintptr_t temp = 0;
    if (!unique(widgetOffsets, temp, e, "drag State.widget"))
        return false;
    d.stateWidget = uint32_t(temp);
    if (!unique(taskOffsets, temp, e, "TaskWidget.task"))
        return false;
    d.widgetTask = uint32_t(temp);
    std::set<uintptr_t> stateCids, initialized;
    for (size_t i = 0; i < create.count(); ++i) {
        uintptr_t target = 0;
        if (branch(create[i], create.symbol.rva + 4 * i, target)) {
            uint32_t cid = allocation(elf, target);
            if (cid)
                stateCids.insert(cid);
        }
        if (unscaled(create[i], SturW, 1, 0, value) && value > 0)
            initialized.insert(uintptr_t(value));
    }
    if (!unique(stateCids, temp, e, "MenuItemButton State CID"))
        return false;
    d.menuStateCid = uint32_t(temp);
    const char *menuNames[] = {"RecentsGlassPressLightMixin.glassPressLightDown",
                               "RecentsGlassPressLightMixin.glassPressLightUp"};
    for (unsigned role = 0; role < 2; ++role) {
        std::set<uintptr_t> methods;
        for (const auto &s : debug.named(menuNames[role])) {
            Method m{elf, s};
            if (!m.valid() || m[0] != 0xa9bf79fd || m[1] != 0xaa0f03fd)
                continue;
            for (size_t i = 2; i < m.count(); ++i)
                if (unscaled(m[i], LdurW, 0, 1, value)) {
                    if (value > 0 && initialized.count(uintptr_t(value)))
                        methods.insert(s.rva);
                    break;
                }
        }
        if (!unique(methods, p.targets[MenuDown + role].rva, e, menuNames[role]))
            return false;
    }
    Method info{elf}, modelKey{elf}, keyHash{elf}, package{elf}, delta{elf}, menuBuild{elf};
    if (!method(elf, debug, "RecentsViewController.itemShowApplicationInfo", info, e, 0, true) ||
        !method(elf, debug, "TaskCardModel.key", modelKey, e) ||
        !method(elf, debug, "TaskKey.hashCode", keyHash, e) ||
        !method(elf, debug, "getPackageName", package, e) ||
        !method(elf, debug, "_TransformedPointerEvent.delta", delta, e) ||
        !method(elf, debug, "_MenuItemButtonState.build", menuBuild, e, 0, true))
        return false;
    if (!offset(menuBuild, LdurW, 3, 2, d.menuWidget, e, "menu State.widget"))
        return false;
    std::set<uintptr_t> onPressed;
    for (size_t i = 0; i + 5 <= menuBuild.count(); ++i) {
        if (unscaled(menuBuild[i], LdurW, 2, 1, value) && value > 0 &&
            menuBuild[i + 1] == 0x8b1c8042 && menuBuild[i + 2] == 0xaa0203e6 &&
            (menuBuild[i + 3] & 0xfc000000) == 0x14000000 && menuBuild[i + 4] == 0xaa1603e6)
            onPressed.insert(uintptr_t(value));
    }
    if (!unique(onPressed, temp, e, "MenuItemButton.onPressed"))
        return false;
    d.menuOnPressed = uint32_t(temp);
    std::set<uintptr_t> closures;
    for (const auto &segment : elf.loads) {
        if (!(segment.p_flags & PF_X))
            continue;
        for (size_t off = 48; off + 16 <= segment.p_filesz; off += 4) {
            uintptr_t pc = segment.p_vaddr + off, target = 0;
            if (!branch(elf.instruction(pc), pc, target) || target != info.symbol.rva)
                continue;
            Method m{elf, {{}, pc - 48, 64, STT_FUNC}};
            uint32_t stack = 0;
            int a = 0, b = 0, c = 0;
            if (m[0] == 0xa9bf79fd && m[1] == 0xaa0f03fd && ldr64(m[2], 0, 29, stack) &&
                stack == 16 && unscaled(m[3], LdurW, 1, 0, a) && m[4] == 0x8b1c8021 &&
                unscaled(m[5], LdurW, 0, 1, b) && m[6] == 0x8b1c8000 &&
                unscaled(m[7], LdurW, 1, 0, c) && m[8] == 0x8b1c8021 && m[15] == 0xd65f03c0 &&
                a > 0 && b > 0 && c > 0) {
                closures.insert(m.symbol.rva);
                d.closureContext = uint32_t(a);
                d.contextState = uint32_t(b);
                d.stateController = uint32_t(c);
            }
        }
    }
    if (!unique(closures, p.targets[MenuClick].rva, e, "application info void closure"))
        return false;
    std::set<uintptr_t> controller;
    for (size_t i = 0; i + 1 < info.count(); ++i)
        if ((info[i] & 0xffe0001f) == 0xd2800011 && info[i + 1] == 0xb8716820)
            controller.insert((info[i] >> 5) & 0xffff);
    if (!unique(controller, temp, e, "controller.task"))
        return false;
    d.controllerTask = uint32_t(temp);
    if (!offset(modelKey, LdurW, 3, 1, d.modelInfo, e, "model.info") ||
        !offset(modelKey, LdurW, 0, 1, d.infoKey, e, "info.key") ||
        !offset(keyHash, LdurX, 2, 0, d.taskId, e, "task.id", 8) ||
        !offset(keyHash, LdurX, 4, 0, d.userId, e, "task.user", 8) ||
        !offset(package, LdurW, 3, 2, d.keyIntent, e, "key.intent") ||
        !offset(package, LdurW, 4, 2, d.componentPackage, e, "component.package") ||
        !offset(package, LdurW, 1, 4, d.topPackage, e, "topActivity.package") ||
        !offset(package, LdurW, 4, 1, d.infoTopActivity, e, "info.topActivity") ||
        !offset(delta, LdurW, 1, 0, d.pointerDelta, e, "pointer.delta"))
        return false;
    std::vector<std::pair<size_t, int>> intentReads;
    for (size_t i = 0; i < package.count(); ++i)
        if (unscaled(package[i], LdurW, 2, 3, value))
            intentReads.emplace_back(i, value);
    if (intentReads.size() != 2 || package[intentReads[0].first + 2] != 0x6b16005f ||
        intentReads[1].first < 2 || package[intentReads[1].first - 2] != 0xaa1603e2)
        return fail(e, "Intent component/package fallback chain changed");
    d.intentComponent = uint32_t(intentReads[0].second);
    d.intentPackage = uint32_t(intentReads[1].second);
    const char *kinds[] = {"Down", "Up", "Move", "Cancel"};
    std::set<uintptr_t> delegates;
    for (unsigned i = 0; i < 4; ++i)
        for (unsigned transformed = 0; transformed < 2; ++transformed) {
            std::string name = transformed
                                   ? std::string("Pointer") + kinds[i] + "Event.transformed"
                                   : std::string("_CopyPointer") + kinds[i] + "Event.copyWith";
            std::set<uintptr_t> cids;
            for (const auto &s : debug.named(name)) {
                Method m{elf, s};
                if (!m.valid())
                    return fail(e, "invalid pointer factory");
                for (size_t j = 0; j < m.count(); ++j) {
                    uintptr_t target = 0;
                    if (!branch(m[j], s.rva + j * 4, target))
                        continue;
                    uint32_t cid = allocation(elf, target);
                    if (!cid)
                        continue;
                    cids.insert(cid);
                    if (transformed) {
                        int a = 0, b = 0;
                        if (j + 2 >= m.count() || !unscaled(m[j + 1], LdurX, 1, 29, a) || a != -8 ||
                            !unscaled(m[j + 2], SturW, 1, 0, b) || b <= 0)
                            return fail(e, "transformed event delegate layout changed");
                        delegates.insert(uintptr_t(b));
                    }
                }
            }
            if (!unique(cids, temp, e, name.c_str()))
                return false;
            d.pointerCids[i][transformed] = uint32_t(temp);
        }
    if (!unique(delegates, temp, e, "transformed event delegate"))
        return false;
    d.pointerDelegate = uint32_t(temp);
    const uint32_t fields[] = {
        d.controllerTask,   d.modelInfo,       d.infoKey,         d.taskId,
        d.userId,           d.keyIntent,       d.intentComponent, d.intentPackage,
        d.componentPackage, d.infoTopActivity, d.topPackage,      d.stateWidget,
        d.widgetTask,       d.menuWidget,      d.menuOnPressed,   d.closureContext,
        d.contextState,     d.stateController, d.pointerDelegate, d.pointerDelta};
    for (auto field : fields)
        if (field > 4095 || field % 4 != 3)
            return fail(e, "unsupported tagged object field offset");
    std::set<uintptr_t> eventCids;
    for (const auto &pair : d.pointerCids)
        for (auto cid : pair)
            eventCids.insert(cid);
    if (eventCids.size() != 8 || eventCids.count(d.menuStateCid))
        return fail(e, "ambiguous Dart event class IDs");
    for (auto &target : p.targets) {
        Bytes code = elf.at(target.rva, sizeof(target.prefix), PF_R | PF_X);
        if (!target.rva || target.rva % 4 || !code.size)
            return fail(e, "invalid resolved hook target");
        memcpy(target.prefix, code.data, sizeof(target.prefix));
    }
    // 同一地址不可同时承担两个 hook 角色。
    std::set<uintptr_t> targets;
    for (const auto &t : p.targets)
        targets.insert(t.rva);
    if (targets.size() != HookCount)
        return fail(e, "duplicate hook roles");
    return true;
}

bool hasParts(std::string_view name, std::initializer_list<std::string_view> parts) {
    for (auto part : parts)
        if (name.find(part) == std::string_view::npos)
            return false;
    return true;
}
bool function(const MiniDebug &debug, std::initializer_list<std::string_view> parts, uintptr_t &out,
              ResolverError &e) {
    std::set<uintptr_t> matches;
    for (const auto &s : debug.elf.symbols)
        if (s.type == STT_FUNC && hasParts(s.name, parts))
            matches.insert(s.rva);
    return unique(matches, out, e, parts.begin()->data());
}
std::set<uintptr_t> tables(const Elf &elf, const std::set<uintptr_t> &functions, unsigned index,
                           uint64_t flags) {
    std::set<uintptr_t> matches;
    for (const auto &[offset, target] : elf.relocs) {
        uint64_t header = 0;
        if (offset >= index * 8 && functions.count(target) &&
            elf.word(offset - index * 8, header) && header == flags)
            matches.insert(offset - index * 8);
    }
    return matches;
}
bool importCall(const Elf &elf, uintptr_t pc, std::string_view name) {
    uintptr_t target = 0;
    if (!branch(elf.instruction(pc), pc, target))
        return false;
    uint32_t a = elf.instruction(target), b = elf.instruction(target + 4);
    uint32_t c = elf.instruction(target + 8), d = elf.instruction(target + 12), displacement = 0;
    uintptr_t got = 0;
    if (!addressPair(a, c, target, 16, got) || !ldr64(b, 17, 16, displacement) ||
        displacement != ((c >> 10) & 0xfff) || d != 0xd61f0220)
        return false;
    auto i = elf.imports.find(got);
    return i != elf.imports.end() && i->second == name;
}
bool runtimeSlot(const Elf &elf, const MiniDebug &debug, RustLayout &r, ResolverError &e) {
    std::vector<Symbol> attach;
    constexpr std::string_view suffix = "Application9on_attach";
    for (const auto &s : debug.elf.symbols)
        if (s.type == STT_FUNC && hasParts(s.name, {"12app_launcher3app"}) &&
            s.name.size() >= suffix.size() &&
            s.name.substr(s.name.size() - suffix.size()) == suffix)
            attach.push_back(s);
    if (attach.size() != 1)
        return fail(e, "App::on_attach not unique");
    Method m{elf, attach[0]};
    std::set<uintptr_t> candidates;
    unsigned confirmations = 0;
    for (size_t i = 0; i + 8 <= m.count(); ++i) {
        uintptr_t state = 0, slot = 0, pc = m.symbol.rva + i * 4;
        if (!addressPair(m[i], m[i + 1], pc, 8, state) || m[i + 2] != 0x88dffd08 ||
            (m[i + 3] & 0xff00001f) != 0x35000008 ||
            !addressPair(m[i + 4], m[i + 5], pc + 16, 0, slot) || state != slot + 8)
            continue;
        size_t call = 6;
        if ((m[i + call] & 0xffe0ffe0) == 0xaa0003e0 && (m[i + call] & 31) != 0)
            ++call;
        if (!importCall(elf, pc + call * 4, "content_resolver_new"))
            continue;
        if (slot % 8 || !elf.contains(slot, 12, PF_R | PF_W, PF_X))
            return fail(e, "invalid Runtime slot range");
        candidates.insert(slot);
        ++confirmations;
    }
    if (confirmations < 2 || !unique(candidates, r.runtimeSlot, e, "Runtime slot"))
        return fail(e, "Runtime slot lacks independent confirmations");
    r.runtimeState = r.runtimeSlot + 8;
    return true;
}
template <size_t N>
bool vtable(const Elf &elf, uintptr_t address, uintptr_t (&words)[N], uint64_t flags,
            unsigned firstFunction, ResolverError &e) {
    if (address % 8 || !elf.contains(address, N * 8, PF_R, PF_X))
        return fail(e, "vtable outside data segments");
    for (unsigned i = 0; i < N; ++i) {
        if (!elf.relative(address + i * 8, words[i]))
            return fail(e, "vtable read failed");
        if (i >= firstFunction &&
            (!elf.relocs.count(address + i * 8) || !elf.at(words[i], 4, PF_R | PF_X).size))
            return fail(e, "vtable method not a relative executable pointer");
    }
    if (words[0] != flags || !elf.relocs.count(address + 8) ||
        !elf.contains(words[1], 8, PF_R, PF_X))
        return fail(e, "vtable metadata changed");
    return true;
}
bool receiverConstruction(const Elf &elf, const Symbol &owner, const RustLayout &r) {
    Method m{elf, owner};
    for (size_t i = 0; i + 16 <= m.count(); ++i) {
        uintptr_t receiver = 0, box = 0, mutex = 0, arc = 0, constructor = 0;
        uintptr_t pc = owner.rva + i * 4;
        if (!addressPair(m[i], m[i + 1], pc, 8, receiver) || receiver != r.receiverVtable ||
            (m[i + 2] & 0xffc07fff) != 0xa90023ff || // stp xzr, x8, [sp, #offset]
            !addressPair(m[i + 3], m[i + 4], pc + 12, 8, box) || box != r.boxVtable ||
            m[i + 5] != 0xf900101f || // Receiver 的最后一个字段位于 +32。
            (m[i + 6] & 0xffc07fff) != 0xa90023e0 ||
            !addressPair(m[i + 7], m[i + 8], pc + 28, 8, mutex) || mutex != r.mutexVtable ||
            (m[i + 9] & 0xffc003ff) != 0x910003e0 || (m[i + 10] & 0xffc003ff) != 0xf90003e8 ||
            !branch(m[i + 11], pc + 44, constructor) ||
            !addressPair(m[i + 12], m[i + 13], pc + 48, 8, arc) || arc != r.arcVtable)
            continue;
        unsigned stack = ((m[i + 2] >> 15) & 0x7f) * 8;
        if (((m[i + 6] >> 15) & 0x7f) * 8 != stack + 16 || ((m[i + 9] >> 10) & 0xfff) != stack ||
            ((m[i + 10] >> 10) & 0xfff) * 8 != stack + 32)
            continue;
        // Arc::new 分配 56 字节，16 字节计数头后完整复制 40 字节 MutexReceiver。
        if (elf.instruction(constructor + 16) != 0x52800700 ||
            !importCall(elf, constructor + 20, "malloc") ||
            elf.instruction(constructor + 28) != 0x52800029 ||
            elf.instruction(constructor + 32) != 0xad400660 ||
            elf.instruction(constructor + 36) != 0xa9002409 ||
            elf.instruction(constructor + 40) != 0xf9401269 ||
            elf.instruction(constructor + 48) != 0xad808400 ||
            elf.instruction(constructor + 52) != 0xf9001909)
            continue;
        return true;
    }
    return false;
}
bool resolveRust(const Elf &elf, RuntimeProfile &p, ResolverError &e) {
    MiniDebug debug;
    if (!debug.init(elf, e))
        return false;
    RustLayout &r = p.rust;
    uintptr_t receive = 0, set = 0, get = 0, drop = 0, lock = 0, stringDrop = 0;
    constexpr std::string_view receiver = "ContextImpl17register_receiver08Receiver";
    if (!function(debug, {receiver, "10on_receive"}, receive, e) ||
        !function(debug, {receiver, "18set_pending_result"}, set, e) ||
        !function(debug, {receiver, "19get_pennding_result"}, get, e) ||
        !function(debug, {receiver, "destroy_box"}, drop, e) ||
        !function(debug, {"parking_lot5mutex4lock"}, lock, e) ||
        !function(debug, {"14destructor_vechE"}, stringDrop, e))
        return false;
    std::set<uintptr_t> receivers;
    for (auto address : tables(elf, {receive}, 3, 0x1f)) {
        uintptr_t a = 0, b = 0;
        if (elf.relative(address + 32, a) && a == set && elf.relative(address + 40, b) && b == get)
            receivers.insert(address);
    }
    if (!unique(receivers, r.receiverVtable, e, "receiver vtable") ||
        !unique(tables(elf, {drop}, 3, 7), r.boxVtable, e, "RBox vtable") ||
        !unique(tables(elf, {lock}, 2, 0xf), r.mutexVtable, e, "Mutex vtable") ||
        !unique(tables(elf, {stringDrop}, 3, 0xf), r.stringVtable, e, "RString vtable"))
        return false;
    std::set<uintptr_t> clones;
    for (const auto &s : debug.elf.symbols)
        if (s.type == STT_FUNC &&
            hasParts(s.name, {"3arc10vtable_mod9clone_arc", "RMutex", "BroadcastReceiver"}))
            clones.insert(s.rva);
    auto candidates = tables(elf, clones, 4, 0x7f);
    std::set<uintptr_t> arcs;
    std::vector<Symbol> owners;
    for (const auto &s : debug.elf.symbols) {
        if (s.type != STT_FUNC || !hasParts(s.name, {"Context_register_receiver_impl"}))
            continue;
        auto refs = references(elf, s);
        auto has = [&refs](uintptr_t x) {
            return std::find(refs.begin(), refs.end(), x) != refs.end();
        };
        if (!has(r.receiverVtable) || !has(r.boxVtable) || !has(r.mutexVtable))
            continue;
        for (auto arc : candidates)
            if (has(arc)) {
                arcs.insert(arc);
                owners.push_back(s);
            }
    }
    if (!unique(arcs, r.arcVtable, e, "receiver construction Arc vtable") ||
        !vtable(elf, r.receiverVtable, r.receiverWords, 0x1f, 3, e) ||
        !vtable(elf, r.boxVtable, r.boxWords, 7, 2, e) ||
        !vtable(elf, r.mutexVtable, r.mutexWords, 0xf, 2, e) ||
        !vtable(elf, r.arcVtable, r.arcWords, 0x7f, 2, e) ||
        !vtable(elf, r.stringVtable, r.stringWords, 0xf, 2, e))
        return false;
    // Receiver 的第 2 项是数据元信息，其余表的第 2 项是 type_id 函数。
    if (!elf.relocs.count(r.receiverVtable + 16) ||
        !elf.contains(r.receiverWords[2], 8, PF_R, PF_X))
        return fail(e, "receiver metadata changed");
    if (elf.instruction(r.arcWords[4]) != 0xf9400000 ||
        elf.instruction(r.arcWords[4] + 4) != 0xd1004008 ||
        elf.instruction(r.arcWords[3]) != 0xd1004000)
        return fail(e, "Arc strong/weak header ABI changed");
    bool layout = false;
    for (const auto &owner : owners)
        if (receiverConstruction(elf, owner, r))
            layout = true;
    if (!layout)
        return fail(e, "receiver / Mutex / Arc construction ABI changed");
    return runtimeSlot(elf, debug, r, e);
}
bool identity(const Elf &elf, RuntimeProfile::Image &image, ResolverError &e) {
    if (elf.loads.empty() || elf.loads.size() > 8)
        return fail(e, "unsupported load segments");
    Sha256 hash;
    unsigned executable = 0;
    for (const auto &segment : elf.loads) {
        image.loads[image.count++] = segment;
        if (!(segment.p_flags & PF_X))
            continue;
        auto bytes = elf.at(segment.p_vaddr, segment.p_filesz, PF_R | PF_X);
        if (!bytes.size)
            return fail(e, "unreadable executable segment");
        hash.update(bytes.data, bytes.size);
        ++executable;
    }
    hash.finish(image.textDigest);
    return executable != 0 || fail(e, "missing executable segment");
}
bool range(const RuntimeProfile::Image &image, uintptr_t rva, size_t size, unsigned flags) {
    for (unsigned i = 0; i < image.count; ++i) {
        const auto &s = image.loads[i];
        if ((s.p_flags & flags) == flags && rva >= s.p_vaddr && rva - s.p_vaddr <= s.p_memsz &&
            size <= s.p_memsz - (rva - s.p_vaddr))
            return true;
    }
    return false;
}
bool loadedImage(const RuntimeProfile::Image &image, const LoadedImage &loaded, ResolverError &e) {
    if (!loaded.base || !loaded.phdr || !loaded.phnum || loaded.phnum > 128)
        return fail(e, "invalid mapped image");
    unsigned count = 0;
    for (size_t i = 0; i < loaded.phnum; ++i) {
        const auto &s = loaded.phdr[i];
        if (s.p_type != PT_LOAD)
            continue;
        if (count >= image.count || memcmp(&s, &image.loads[count], sizeof(s)))
            return fail(e, "mapped image segments differ from file");
        ++count;
    }
    if (count != image.count)
        return fail(e, "mapped image segment count differs from file");
    uintptr_t base = loaded.base;
    Sha256 hash;
    for (unsigned i = 0; i < image.count; ++i) {
        const auto &s = image.loads[i];
        if (s.p_vaddr > UINTPTR_MAX - base || s.p_memsz > UINTPTR_MAX - base - s.p_vaddr)
            return fail(e, "mapped image address overflow");
        if (!(s.p_flags & PF_X))
            continue;
        hash.update(reinterpret_cast<const void *>(base + s.p_vaddr), size_t(s.p_filesz));
    }
    char digest[65];
    hash.finish(digest);
    return !strcmp(digest, image.textDigest) ||
           fail(e, "mapped code differs from file (updated/patched image)");
}
template <size_t N>
bool loadedTable(const RuntimeProfile &p, uintptr_t base, uintptr_t address,
                 const uintptr_t (&words)[N], ResolverError &e) {
    if (!range(p.rustImage, address, N * 8, PF_R))
        return fail(e, "mapped vtable outside image");
    for (unsigned i = 0; i < N; ++i) {
        uintptr_t actual = 0;
        memcpy(&actual, reinterpret_cast<const void *>(base + address + i * 8), 8);
        if (actual != words[i] + (i ? base : 0))
            return fail(e, "mapped vtable relocation mismatch at %#lx",
                        (unsigned long)(address + i * 8));
    }
    return true;
}
} // namespace

bool ResolveRuntimeProfile(const char *dartPath, const char *rustPath, RuntimeProfile &result,
                           ResolverError &error) {
    error = {};
    result = {};
    RuntimeProfile candidate{};
    // 按顺序释放两份 debugdata，限制初始化时峰值内存。
    {
        resolver::File file;
        resolver::Elf elf;
        if (!file.open(dartPath, error) || !elf.init(file.bytes, error) ||
            !resolveDart(elf, candidate, error) || !identity(elf, candidate.dartImage, error))
            return false;
    }
    {
        resolver::File file;
        resolver::Elf elf;
        if (!file.open(rustPath, error) || !elf.init(file.bytes, error) ||
            !resolveRust(elf, candidate, error) || !identity(elf, candidate.rustImage, error))
            return false;
    }
    result = candidate;
    return true;
}
bool ValidateLoadedProfile(const RuntimeProfile &p, const LoadedImage &dart,
                           const LoadedImage &rust, ResolverError &e) {
    e = {};
    if (!loadedImage(p.dartImage, dart, e) || !loadedImage(p.rustImage, rust, e))
        return false;
    uintptr_t dartBase = dart.base, rustBase = rust.base;
    for (const auto &target : p.targets)
        if (!range(p.dartImage, target.rva, sizeof(target.prefix), PF_R | PF_X) ||
            memcmp(reinterpret_cast<const void *>(dartBase + target.rva), target.prefix,
                   sizeof(target.prefix)))
            return fail(e, "hook prefix differs from parsed code");
    const auto &r = p.rust;
    return loadedTable(p, rustBase, r.receiverVtable, r.receiverWords, e) &&
           loadedTable(p, rustBase, r.boxVtable, r.boxWords, e) &&
           loadedTable(p, rustBase, r.mutexVtable, r.mutexWords, e) &&
           loadedTable(p, rustBase, r.arcVtable, r.arcWords, e) &&
           loadedTable(p, rustBase, r.stringVtable, r.stringWords, e);
}
} // namespace miuihome
