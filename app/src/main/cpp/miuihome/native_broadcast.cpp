#include "native_broadcast.h"
#include "config_snapshot.h"
#include <android/log.h>
#include <atomic>
#include <dlfcn.h>
#include <new>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

namespace miuihome {
namespace {
constexpr char kTag[] = "MyInjectorMiuiHome";
constexpr char kAction[] = "io.github.a13e300.myinjector.miuihome.CONFIG";
// registerReceiver 的 permission 约束发送者。配置仅由 system_server 下发。
constexpr char kSenderPermission[] = "android.permission.BROADCAST_PACKAGE_REMOVED";
struct RBytes {
    void *data;
    size_t length, capacity;
    const uintptr_t *vtable;
};
struct OptionStr {
    uint64_t tag;
    const char *data;
    size_t length;
};
struct OptionI64 {
    uint64_t tag;
    int64_t value;
};
struct Result {
    uint8_t tag;
    uint8_t padding[7];
    union {
        void *intent;
        struct {
            uint64_t code;
            RBytes message;
        } error;
    };
};
static_assert(sizeof(Result) == 48 && sizeof(OptionStr) == 24 && sizeof(OptionI64) == 16);
struct Api {
    void (*runtimeInc)(void *);
    void (*runtimeDec)(void *);
    void *(*filterDefault)();
    void (*filterAddAction)(void *, RBytes *);
    void (*filterDelete)(void *);
    Result (*registerReceiver)(void **, void *, const uintptr_t *, void *, OptionStr *, uint32_t);
    Result (*unregisterReceiver)(void **, void *, const uintptr_t *);
    void *(*aimIntent)(void *);
    void *(*intentExtras)(void *);
    void (*intentDrop)(void *);
    uint64_t (*getI32)(void *, const char *, size_t);
    OptionI64 (*getI64)(void *, const char *, size_t);
    void *(*pendingClone)(void *);
    void (*pendingDelete)(void *);
} api{};
std::atomic<void (*)(uint32_t)> notify{nullptr};
void *runtime = nullptr;

// 解析器通过实际 register_receiver 构造代码、Arc 计数头及虚表关系验证以下布局。
// 复用同一 ELF 的 Arc/Mutex 实现，复制 receiver/RBox
// 虚表后替换回调和析构。 不把本模块的 C++ 对象交给桌面原来的 Receiver/Tokio
// 析构函数。
struct Receiver {
    void *pending = nullptr;
    ConfigSnapshot config{};
};
struct MutexReceiver {
    uint64_t lock;
    const uintptr_t *receiverVtable;
    Receiver *receiver;
    const uintptr_t *boxVtable;
    const uintptr_t *mutexVtable;
};
struct ArcBox {
    uint64_t strong, weak;
    MutexReceiver value;
};
static_assert(offsetof(ArcBox, value) == 16 && sizeof(MutexReceiver) == 40);
ArcBox *owner = nullptr;
uintptr_t receiverVtable[6]{}, boxVtable[4]{};
const uintptr_t *arcVtable = nullptr;

template <typename T> bool resolve(T &target, void *handle, const char *name) {
    target = reinterpret_cast<T>(dlsym(handle, name));
    if (!target)
        __android_log_print(ANDROID_LOG_ERROR, kTag, "missing broadcast API: %s", name);
    return target != nullptr;
}
bool resolveApi(void *handle) {
    return resolve(api.runtimeInc, handle, "Runtime_inc_strong") &&
           resolve(api.runtimeDec, handle, "Runtime_dec_strong") &&
           resolve(api.filterDefault, handle, "intent_fliter_default") &&
           resolve(api.filterAddAction, handle, "intent_fliter_add_action") &&
           resolve(api.filterDelete, handle, "intent_fliter_delete") &&
           resolve(api.registerReceiver, handle, "Broadcast_register_receiver") &&
           resolve(api.unregisterReceiver, handle, "Broadcast_unregister_receiver") &&
           resolve(api.aimIntent, handle, "Aim_get_intent") &&
           resolve(api.intentExtras, handle, "Intent_get_extras") &&
           resolve(api.intentDrop, handle, "Intent_drop") &&
           resolve(api.getI32, handle, "Bundle_get_i32") &&
           resolve(api.getI64, handle, "Bundle_get_i64") &&
           resolve(api.pendingClone, handle, "pending_result_clone") &&
           resolve(api.pendingDelete, handle, "pending_result_delete");
}
void releaseResult(Result &result, bool hasIntent) {
    if (result.tag == 1) {
        auto &bytes = result.error.message;
        if (bytes.vtable)
            reinterpret_cast<void (*)(RBytes *)>(bytes.vtable[3])(&bytes);
    } else if (hasIntent && result.intent) {
        api.intentDrop(result.intent);
    }
}
bool getI32(void *bundle, const char *name, int32_t &value) {
    uint64_t option = api.getI32(bundle, name, strlen(name));
    if (uint32_t(option) != 0)
        return false;
    value = int32_t(option >> 32);
    return true;
}
void receive(Receiver *self, void *, void *aim) {
    auto changed = notify.load(std::memory_order_acquire);
    if (!changed || !aim)
        return;
    void *intent = api.aimIntent(aim);
    void *extras = intent ? api.intentExtras(intent) : nullptr;
    int32_t protocol = 0, mask = -1;
    if (!extras || !getI32(extras, "protocol", protocol) || !getI32(extras, "mask", mask))
        return;
    OptionI64 revision = api.getI64(extras, "revision", 8);
    if (revision.tag == 0 && self->config.accept(protocol, mask, revision.value))
        changed(self->config.mask);
}
void setPending(Receiver *self, void *pending) {
    void *copy = pending ? api.pendingClone(pending) : nullptr;
    if (self->pending)
        api.pendingDelete(self->pending);
    self->pending = copy;
}
void *getPending(Receiver *self) { return self->pending; }
void destroyReceiver(Receiver *self, uint32_t dropReferent, uint8_t deallocate) {
    if (dropReferent == 0 && self->pending)
        api.pendingDelete(self->pending);
    if (deallocate & 1)
        free(self);
}
void *cloneReceiver() {
    __atomic_fetch_add(&owner->strong, uint64_t{1}, __ATOMIC_RELAXED);
    return &owner->value;
}
template<size_t N> bool tableMatches(uintptr_t base, uintptr_t rva, const uintptr_t (&expected)[N]) {
    const auto *words = reinterpret_cast<const uintptr_t *>(base+rva);
    for (size_t i = 0; i < N; ++i)
        if (words[i] != expected[i]+(i ? base : 0)) return false;
    return true;
}
} // namespace

ReceiverStatus StartConfigReceiver(void *handle, uintptr_t base, const RustLayout &layout, void (*changed)(uint32_t)) {
    if (owner)
        return ReceiverStatus::Ready;
    auto *state = reinterpret_cast<const uint32_t *>(base + layout.runtimeState);
    if (__atomic_load_n(state, __ATOMIC_ACQUIRE) != 0)
        return ReceiverStatus::WaitingForRuntime;
    auto *slot = reinterpret_cast<void *const *>(base + layout.runtimeSlot);
    void *current = __atomic_load_n(slot, __ATOMIC_ACQUIRE);
    if (!current)
        return ReceiverStatus::WaitingForRuntime;
    if (!resolveApi(handle))
        return ReceiverStatus::Failed;
    auto *sourceReceiver = reinterpret_cast<const uintptr_t *>(base + layout.receiverVtable);
    auto *sourceBox = reinterpret_cast<const uintptr_t *>(base + layout.boxVtable);
    arcVtable = reinterpret_cast<const uintptr_t *>(base + layout.arcVtable);
    auto *mutexVtable = reinterpret_cast<const uintptr_t *>(base + layout.mutexVtable);
    if (!tableMatches(base, layout.receiverVtable, layout.receiverWords) ||
        !tableMatches(base, layout.boxVtable, layout.boxWords) ||
        !tableMatches(base, layout.arcVtable, layout.arcWords) ||
        !tableMatches(base, layout.mutexVtable, layout.mutexWords) ||
        !tableMatches(base, layout.stringVtable, layout.stringWords))
        return ReceiverStatus::Failed;
    memcpy(receiverVtable, sourceReceiver, sizeof(receiverVtable));
    memcpy(boxVtable, sourceBox, sizeof(boxVtable));
    receiverVtable[3] = reinterpret_cast<uintptr_t>(receive);
    receiverVtable[4] = reinterpret_cast<uintptr_t>(setPending);
    receiverVtable[5] = reinterpret_cast<uintptr_t>(getPending);
    boxVtable[3] = reinterpret_cast<uintptr_t>(destroyReceiver);
    static_assert(sizeof(Receiver) <= 40);
    // 保留原 RBox 元数据描述的 40 字节分配大小，使用本模块自己的析构。
    void *storage = malloc(40);
    auto *receiver = storage ? new (storage) Receiver{} : nullptr;
    auto *box = static_cast<ArcBox *>(calloc(1, sizeof(ArcBox)));
    void *filter = api.filterDefault();
    char *action = static_cast<char *>(malloc(sizeof(kAction) - 1));
    if (!receiver || !box || !filter || !action) {
        free(receiver);
        free(box);
        free(action);
        if (filter)
            api.filterDelete(filter);
        return ReceiverStatus::Failed;
    }
    memcpy(action, kAction, sizeof(kAction) - 1);
    RBytes text{action, sizeof(kAction) - 1, sizeof(kAction) - 1,
                reinterpret_cast<const uintptr_t *>(base + layout.stringVtable)};
    api.filterAddAction(filter, &text); // 消费拥有型 RString。
    box->strong = box->weak = 1;
    box->value = {0, receiverVtable, receiver, boxVtable, mutexVtable};
    owner = box;
    runtime = current;
    api.runtimeInc(runtime);
    notify.store(changed, std::memory_order_release);
    OptionStr permission{0, kSenderPermission, sizeof(kSenderPermission) - 1};
    // Arc 引用按值消费；owner 保留独立引用用于注销，filter 只被借用。
    Result result =
        api.registerReceiver(&runtime, cloneReceiver(), arcVtable, filter, &permission, 2);
    api.filterDelete(filter);
    bool ok = result.tag == 0;
    releaseResult(result, true);
    if (!ok) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "config receiver registration failed");
        StopConfigReceiver();
        return ReceiverStatus::Failed;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "config broadcast receiver ready");
    return ReceiverStatus::Ready;
}

void StopConfigReceiver() {
    notify.store(nullptr, std::memory_order_release);
    if (!owner)
        return;
    Result result = api.unregisterReceiver(&runtime, cloneReceiver(), arcVtable);
    if (result.tag != 0)
        __android_log_print(ANDROID_LOG_ERROR, kTag, "config receiver unregistration failed");
    releaseResult(result, false);
    reinterpret_cast<void (*)(void *, uint32_t)>(arcVtable[3])(&owner->value, 0);
    owner = nullptr;
    api.runtimeDec(runtime);
    runtime = nullptr;
}
} // namespace miuihome
