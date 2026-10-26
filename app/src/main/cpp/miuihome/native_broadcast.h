#pragma once
#include "runtime_profile.h"
#include <stdint.h>

namespace miuihome {
// 仅在解析并校验已加载映像后由 native worker 调用。回调只发布配置快照、唤醒 worker。
enum class ReceiverStatus { WaitingForRuntime, Ready, Failed };
ReceiverStatus StartConfigReceiver(void *handle, uintptr_t launcherBase, const RustLayout &layout,
                                   void (*changed)(uint32_t));
void StopConfigReceiver();
} // namespace miuihome
