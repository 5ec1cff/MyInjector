#pragma once
#include "native_api.h"
#include <stdint.h>

namespace miuihome {
bool InitializeRemotePreferences(const XposedRuntimeApi *api, void (*publish)(uint32_t), void (*wakeRetry)());
// 仅首次接入失败或快照失败时由 worker 重试；稳定订阅不轮询、不重订阅。
void RetryRemotePreferences();
bool RemotePreferencesRetryPending();
void StopRemotePreferences();
}
