// 主机调用模块的同一份 C++ 解析器，供真实样本和损坏输入回归使用。
#include "runtime_profile.h"
#include "resolver_elf.h"
#include <algorithm>
#include <string.h>
#include <stdio.h>
#include <string>
using namespace miuihome;
static bool checkMapped(const RuntimeProfile &p, const std::string &dart, const std::string &rust) {
    resolver::File df, rf;
    resolver::Elf de, re;
    ResolverError error{};
    if (!df.open(dart.c_str(), error) || !rf.open(rust.c_str(), error) ||
        !de.init(df.bytes, error) || !re.init(rf.bytes, error))
        return false;
    auto image = [](const resolver::Elf &elf) {
        size_t size = 0;
        for (const auto &s : elf.loads)
            size = std::max(size, size_t(s.p_vaddr + s.p_memsz));
        std::vector<uint8_t> out(size);
        for (const auto &s : elf.loads) {
            auto bytes = elf.at(s.p_vaddr, s.p_filesz);
            memcpy(out.data() + s.p_vaddr, bytes.data, bytes.size);
        }
        for (const auto &[rva, target] : elf.relocs) {
            uintptr_t relocated = reinterpret_cast<uintptr_t>(out.data()) + target;
            memcpy(out.data() + rva, &relocated, 8);
        }
        return out;
    };
    auto dm = image(de), rm = image(re);
    LoadedImage di{reinterpret_cast<uintptr_t>(dm.data()), de.loads.data(), de.loads.size()};
    LoadedImage ri{reinterpret_cast<uintptr_t>(rm.data()), re.loads.data(), re.loads.size()};
    if (!ValidateLoadedProfile(p, di, ri, error)) {
        fprintf(stderr, "%s\n", error.message);
        return false;
    }
    // 文件与已映射代码不一致、加载器虚表结果不一致、PT_LOAD 不一致均必须拒绝。
    dm[p.targets[Commit].rva] ^= 1;
    if (ValidateLoadedProfile(p, di, ri, error))
        return false;
    dm[p.targets[Commit].rva] ^= 1;
    rm[p.rust.arcVtable + 32] ^= 1;
    if (ValidateLoadedProfile(p, di, ri, error))
        return false;
    rm[p.rust.arcVtable + 32] ^= 1;
    ++re.loads[0].p_memsz;
    if (ValidateLoadedProfile(p, di, ri, error))
        return false;
    --re.loads[0].p_memsz;
    if (!ValidateLoadedProfile(p, di, ri, error))
        return false;
    fprintf(stderr, "通过：模拟 PT_LOAD/重定位、内存代码变化、虚表变化和段布局变化检查\n");
    return true;
}
int main(int argc, char **argv) {
    bool mapped = argc == 3 && !strcmp(argv[1], "--check-mapped");
    if (argc != 2 && argc != 3) {
        fprintf(stderr, "用法：%s launcher.apk 或 dart.so launcher.so\n", argv[0]);
        return 2;
    }
    const char *apk = mapped ? argv[2] : argv[1];
    std::string dart =
        (argc == 2 || mapped) ? std::string(apk) + "!/lib/arm64-v8a/libapp.so" : argv[1];
    std::string rust =
        (argc == 2 || mapped) ? std::string(apk) + "!/lib/arm64-v8a/libapp_launcher.so" : argv[2];
    RuntimeProfile p{};
    ResolverError e{};
    if (!ResolveRuntimeProfile(dart.c_str(), rust.c_str(), p, e)) {
        fprintf(stderr, "解析拒绝：%s\n", e.message);
        // 失败时不得返回任何部分 profile。
        for (const auto &target : p.targets)
            if (target.rva)
                return 3;
        if (p.dart.controllerTask || p.rust.runtimeSlot || p.dartImage.count || p.rustImage.count)
            return 3;
        return 1;
    }
    if (mapped && !checkMapped(p, dart, rust))
        return 4;
    printf("{\"targets\":[");
    for (unsigned i = 0; i < HookCount; ++i)
        printf("%s\"0x%lx\"", i ? "," : "", (unsigned long)p.targets[i].rva);
    printf("],\"fields\":{");
#define FIELD(name, field) printf("\"" name "\":\"0x%x\",", p.dart.field)
    FIELD("controller_task", controllerTask);
    FIELD("model_info", modelInfo);
    FIELD("info_key", infoKey);
    FIELD("task_id", taskId);
    FIELD("user_id", userId);
    FIELD("key_intent", keyIntent);
    FIELD("intent_component", intentComponent);
    FIELD("intent_package", intentPackage);
    FIELD("component_package", componentPackage);
    FIELD("info_top_activity", infoTopActivity);
    FIELD("pointer_delta", pointerDelta);
    FIELD("top_package", topPackage);
    FIELD("state_widget", stateWidget);
    FIELD("widget_task", widgetTask);
    FIELD("menu_widget", menuWidget);
    FIELD("menu_on_pressed", menuOnPressed);
    FIELD("closure_context", closureContext);
    FIELD("context_state", contextState);
    FIELD("state_controller", stateController);
    printf("\"pointer_delegate\":\"0x%x\"},\"cids\":{\"menu_state\":\"0x%x\"",
           p.dart.pointerDelegate, p.dart.menuStateCid);
    const char *names[] = {"down", "up", "move", "cancel"};
    for (unsigned i = 0; i < 4; ++i)
        for (unsigned j = 0; j < 2; ++j)
            printf(",\"%s_%s\":\"0x%x\"", names[i], j ? "transformed" : "raw",
                   p.dart.pointerCids[i][j]);
    printf("},\"rust\":{");
#define RUST(name, field) printf("\"" name "\":\"0x%lx\",", (unsigned long)p.rust.field)
    RUST("runtime_slot", runtimeSlot);
    RUST("runtime_state", runtimeState);
    RUST("string_vtable", stringVtable);
    RUST("receiver_vtable", receiverVtable);
    RUST("box_vtable", boxVtable);
    RUST("mutex_vtable", mutexVtable);
    printf("\"arc_vtable\":\"0x%lx\"}}\n", (unsigned long)p.rust.arcVtable);
    return 0;
}
