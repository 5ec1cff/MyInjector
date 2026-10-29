#pragma once
#include <stdint.h>

namespace miuihome {
// 由 runtime 的 receiver 锁串行访问；版本号来自 system_server 的单调时钟。
struct ConfigSnapshot {
    int64_t revision = 0;
    uint32_t mask = 0;
    bool accept(int32_t protocol, int32_t value, int64_t next) {
        if (protocol != 1 || value < 0 || (uint32_t(value) & ~7u) || next <= revision)
            return false;
        revision = next;
        mask = uint32_t(value);
        return true;
    }
};
} // namespace miuihome
