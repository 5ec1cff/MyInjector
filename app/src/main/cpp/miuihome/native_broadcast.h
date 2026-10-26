#pragma once
#include <stdint.h>

namespace miuihome {
// 仅在校验 7751 ELF 后由 native worker 调用。回调只发布配置快照、唤醒 worker。
enum class ReceiverStatus { WaitingForRuntime, Ready, Failed };
ReceiverStatus StartConfigReceiver(void *handle, uintptr_t launcherBase, void (*changed)(uint32_t));
void StopConfigReceiver();
} // namespace miuihome
