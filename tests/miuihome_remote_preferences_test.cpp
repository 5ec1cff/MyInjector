#include "remote_preferences.h"
#include <cassert>
#include <cstring>
#include <cstdio>

struct XposedRemotePrefs {};
struct XposedPrefsSubscription {};
struct XposedPrefsSnapshot { int values[3]; bool wrongType; };
namespace {
XposedRemotePrefs prefs;
XposedPrefsSubscription token;
XposedPrefsChangedFn callback;
void *callbackData;
int values[]{1, 0, 1};
bool wrongType = false, failSnapshot = false, failSubscribe = true;
unsigned subscribed = 0, unsubscribed = 0, fetched = 0, released = 0, groupsReleased = 0;
uint32_t published = 0;
unsigned retriesWoken = 0;
XposedStatus group(void *, const char *name, XposedRemotePrefs **out) {
    assert(std::strcmp(name, "miuihome") == 0);
    *out = &prefs;
    return XPOSED_STATUS_OK;
}
XposedStatus snapshot(XposedRemotePrefs *p, XposedPrefsSnapshot **out) {
    assert(p == &prefs);
    ++fetched;
    if (failSnapshot) return XPOSED_STATUS_IO_ERROR;
    *out = new XposedPrefsSnapshot{{values[0], values[1], values[2]}, wrongType};
    return XPOSED_STATUS_OK;
}
XposedStatus value(const XposedPrefsSnapshot *s, XposedStringView key, XposedPrefValue *out) {
    const char *keys[]{"miuiHomeDragKill", "miuiHomeOpenAospSettings", "miuiHomeDisablePreLaunch"};
    for (unsigned i = 0; i < 3; ++i) {
        if (key.size != std::strlen(keys[i]) || std::memcmp(key.data, keys[i], key.size)) continue;
        if (s->values[i] < 0) return XPOSED_STATUS_NOT_FOUND;
        out->type = s->wrongType ? XPOSED_PREF_STRING : XPOSED_PREF_BOOLEAN;
        out->value.boolean_value = s->values[i];
        return XPOSED_STATUS_OK;
    }
    return XPOSED_STATUS_NOT_FOUND;
}
XposedStatus subscribe(XposedRemotePrefs *p, XposedPrefsChangedFn cb, void *data, XposedPrefsSubscription **out) {
    assert(p == &prefs);
    if (failSubscribe) return XPOSED_STATUS_SERVICE_ERROR;
    ++subscribed;
    callback = cb;
    callbackData = data;
    cb(data, p, nullptr, 0); // 初始 FULL 可以早于 subscribe 返回。
    *out = &token;
    return XPOSED_STATUS_OK;
}
void notify() { callback(callbackData, &prefs, nullptr, 0); }
}
int main() {
    XposedRuntimeApi api{};
    api.get_api_version = [](void *) { return XPOSED_NATIVE_API_VERSION; };
    api.get_framework_properties = [](void *) { return XPOSED_PROP_CAP_REMOTE; };
    api.get_remote_preferences = group;
    api.get_preferences_snapshot = snapshot;
    api.get_preferences_snapshot_value = value;
    api.release_preferences_snapshot = [](XposedPrefsSnapshot *s) { ++released; delete s; };
    api.release_remote_preferences = [](XposedRemotePrefs *p) { assert(p == &prefs); ++groupsReleased; };
    api.subscribe_remote_preferences = subscribe;
    api.unsubscribe_remote_preferences = [](XposedPrefsSubscription *s) { assert(s == &token); ++unsubscribed; };
    assert(miuihome::InitializeRemotePreferences(&api, [](uint32_t mask) { published = mask; }, [] { ++retriesWoken; }));
    miuihome::RetryRemotePreferences();
    assert(published == 0 && subscribed == 0 && miuihome::RemotePreferencesRetryPending());
    failSubscribe = false;
    miuihome::RetryRemotePreferences();
    assert(published == 5 && subscribed == 1 && !miuihome::RemotePreferencesRetryPending());
    auto initialFetches = fetched;
    miuihome::RetryRemotePreferences();
    assert(fetched == initialFetches); // 稳定状态不轮询。
    values[0] = 0; values[1] = 1; values[2] = 0;
    notify();
    assert(published == 2); // 回调返回前已经发布，不只是唤醒 worker。
    failSnapshot = true;
    notify();
    assert(published == 2 && miuihome::RemotePreferencesRetryPending() && retriesWoken == 2);
    failSnapshot = false;
    values[0] = 1;
    miuihome::RetryRemotePreferences();
    assert(published == 3 && subscribed == 1); // 快照失败重试，不重订阅。
    wrongType = true;
    notify();
    assert(published == 3 && miuihome::RemotePreferencesRetryPending());
    wrongType = false;
    values[0] = values[1] = values[2] = -1;
    notify();
    assert(published == 0 && !miuihome::RemotePreferencesRetryPending()); // clear -> 默认全关。
    assert(released == fetched - 1); // 只有失败的读取没有产生快照。
    miuihome::StopRemotePreferences();
    assert(unsubscribed == 1 && groupsReleased == 1);
    puts("通过：native 初始 FULL、回调同步发布、类型检查、clear、读取失败重试和取消释放");
}
