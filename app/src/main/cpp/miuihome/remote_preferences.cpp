#include "remote_preferences.h"
#include <atomic>
#include <cstring>
#include <mutex>
#ifdef __ANDROID__
#include <android/log.h>
#else
#include <cstdio>
#endif

namespace miuihome {
namespace {
const XposedRuntimeApi *runtime = nullptr;
void (*publishConfig)(uint32_t) = nullptr;
void (*wakeWorker)() = nullptr;
XposedRemotePrefs *preferences = nullptr;
XposedPrefsSubscription *subscription = nullptr; // worker owns subscription lifetime
std::mutex refreshLock;
std::atomic<bool> retry{true};

void failed(const char *operation, XposedStatus status) {
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_ERROR, "MyInjectorMiuiHome", "remote prefs %s failed: %d", operation, status);
#else
    std::fprintf(stderr, "remote prefs %s failed: %d\n", operation, status);
#endif
    retry.store(true, std::memory_order_release);
    if (wakeWorker) wakeWorker();
}
void refresh(XposedRemotePrefs *prefs) {
    std::lock_guard<std::mutex> guard(refreshLock);
    retry.store(false, std::memory_order_release);
    XposedPrefsSnapshot *snapshot = nullptr;
    XposedStatus status = runtime->get_preferences_snapshot(prefs, &snapshot);
    if (status != XPOSED_STATUS_OK) { failed("snapshot", status); return; }
    constexpr const char *keys[]{"miuiHomeDragKill", "miuiHomeOpenAospSettings", "miuiHomeDisablePreLaunch"};
    uint32_t mask = 0;
    bool valid = true;
    for (unsigned i = 0; i < 3; ++i) {
        XposedPrefValue value{};
        status = runtime->get_preferences_snapshot_value(snapshot, {keys[i], std::strlen(keys[i])}, &value);
        if (status == XPOSED_STATUS_NOT_FOUND) continue; // 原默认值为 false，包括 clear。
        if (status != XPOSED_STATUS_OK || value.type != XPOSED_PREF_BOOLEAN) {
            failed(keys[i], status == XPOSED_STATUS_OK ? XPOSED_STATUS_INVALID_ARGUMENT : status);
            valid = false;
            break;
        }
        if (value.value.boolean_value) mask |= uint32_t{1} << i;
    }
    runtime->release_preferences_snapshot(snapshot);
    // 发布发生在回调返回之前，框架随后才 ACK；失败时保留最近成功值并请求重试。
    if (valid) publishConfig(mask);
}
void changed(void *, XposedRemotePrefs *prefs, const XposedStringView *, size_t) {
    refresh(prefs); // key_count=0 同样读取完整快照，不保存借用指针。
}
}

bool InitializeRemotePreferences(const XposedRuntimeApi *api, void (*publish)(uint32_t), void (*wakeRetry)()) {
    if (!api || !publish || api->get_api_version(api->context) != XPOSED_NATIVE_API_VERSION ||
        !(api->get_framework_properties(api->context) & XPOSED_PROP_CAP_REMOTE)) return false;
    runtime = api;
    publishConfig = publish;
    wakeWorker = wakeRetry;
    return true;
}
void RetryRemotePreferences() {
    if (!runtime || !retry.load(std::memory_order_acquire)) return;
    if (!preferences) {
        auto status = runtime->get_remote_preferences(runtime->context, "miuihome", &preferences);
        if (status != XPOSED_STATUS_OK) { failed("group", status); return; }
    }
    if (!subscription) {
        auto status = runtime->subscribe_remote_preferences(preferences, changed, nullptr, &subscription);
        if (status != XPOSED_STATUS_OK) { failed("subscribe", status); return; }
    }
    refresh(preferences); // 先订阅、后快照，允许初始 FULL 与这次读取重叠。
}
bool RemotePreferencesRetryPending() { return retry.load(std::memory_order_acquire); }
void StopRemotePreferences() {
    if (subscription) {
        runtime->unsubscribe_remote_preferences(subscription); // 不持 refreshLock，避免等待回调死锁。
        subscription = nullptr;
    }
    std::lock_guard<std::mutex> guard(refreshLock);
    if (preferences) runtime->release_remote_preferences(preferences);
    preferences = nullptr;
    retry.store(true, std::memory_order_release);
}
}
