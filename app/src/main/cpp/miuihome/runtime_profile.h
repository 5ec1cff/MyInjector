#pragma once
#include <elf.h>
#include <stddef.h>
#include <stdint.h>

namespace miuihome {
enum HookIndex {
    DragStart,
    DragEndVertical,
    DragEndHorizontal,
    Commit,
    MenuDown,
    MenuUp,
    MenuClick,
    Pointer,
    HookCount
};
struct HookTarget {
    uintptr_t rva = 0;
    uint8_t prefix[32]{};
};
struct DartLayout {
    uint32_t controllerTask = 0, modelInfo = 0, infoKey = 0, taskId = 0, userId = 0;
    uint32_t keyIntent = 0, intentComponent = 0, intentPackage = 0, componentPackage = 0;
    uint32_t infoTopActivity = 0, topPackage = 0, stateWidget = 0, widgetTask = 0;
    uint32_t menuWidget = 0, menuOnPressed = 0, closureContext = 0, contextState = 0;
    uint32_t stateController = 0, pointerDelegate = 0, pointerDelta = 0;
    uint32_t menuStateCid = 0;
    // Down / Up / Move / Cancel，分别为原始事件和 transformed 事件。
    uint32_t pointerCids[4][2]{};
};
struct RustLayout {
    uintptr_t runtimeSlot = 0, runtimeState = 0, stringVtable = 0;
    uintptr_t receiverVtable = 0, boxVtable = 0, mutexVtable = 0, arcVtable = 0;
    // 文件中的 RELATIVE 重定位结果；加载后逐项核对，避免使用错误映像/布局。
    uintptr_t receiverWords[6]{}, boxWords[4]{}, mutexWords[5]{}, arcWords[9]{}, stringWords[5]{};
};
struct RuntimeProfile {
    HookTarget targets[HookCount]{};
    DartLayout dart{};
    RustLayout rust{};
    struct Image {
        Elf64_Phdr loads[8]{};
        unsigned count = 0;
        char textDigest[65]{};
    } dartImage{}, rustImage{};
};
struct ResolverError {
    char message[256]{};
};
struct LoadedImage {
    uintptr_t base;
    const Elf64_Phdr *phdr;
    size_t phnum;
};
// 路径支持普通 ELF 和 APK!/lib/arm64-v8a/*.so。只读文件，不进入 Dart/Rust VM。
bool ResolveRuntimeProfile(const char *dartPath, const char *rustPath, RuntimeProfile &result,
                           ResolverError &error);
// 与进程中真实 PT_LOAD 的范围、权限、代码和已重定位虚表交叉校验。
bool ValidateLoadedProfile(const RuntimeProfile &profile, const LoadedImage &dart,
                           const LoadedImage &rust, ResolverError &error);
} // namespace miuihome
