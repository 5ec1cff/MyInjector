#include "gesture_state.h"
#include "lsposed_hook_backend.h"
#include "profile_7751.h"
#include "sha256.h"
#include <android/log.h>
#include <atomic>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

extern "C" {
__attribute__((visibility("hidden"))) void *myinjector_dart_original[8]{};
void MyInjectorDragStart();
void MyInjectorDragEndVertical();
void MyInjectorDragEndHorizontal();
void MyInjectorCommit();
void MyInjectorMenuDown();
void MyInjectorMenuUp();
void MyInjectorMenuClick();
void MyInjectorPointer();
}

namespace {
using namespace miuihome;
constexpr char kTag[] = "MyInjectorMiuiHome";
constexpr char kPrefix[] = "io.github.a13e300.myinjector.miuihome.";
constexpr uint32_t kDrag = 1, kSettings = 2;
std::atomic<uint32_t> config{0};
std::atomic<uint64_t> configEpoch{1};
std::atomic<int> eventFd{-1};
std::atomic<pid_t> workerPid{0};
std::atomic<uintptr_t> ownerThread{0};
uintptr_t ownerHeap = 0; // only accessed by the one owning Dart thread
uintptr_t dartBase = 0, launcherBase = 0;
std::atomic<void *> launcherHandle{nullptr};
bool installed = false, apiFailed = false;

uint64_t nowNs() {
    timespec ts{};
    syscall(SYS_clock_gettime, CLOCK_MONOTONIC, &ts);
    return uint64_t(ts.tv_sec) * 1000000000 + uint64_t(ts.tv_nsec);
}
bool isHyos() {
    char path[256]{};
    ssize_t size = readlink("/proc/self/exe", path, sizeof(path) - 1);
    return size > 0 && strcmp(path, "/system_ext/bin/hyos_spawner") == 0;
}
bool isLauncher() {
    if (getuid() < 10000)
        return false;
    char name[128]{};
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return false;
    ssize_t size = read(fd, name, sizeof(name) - 1);
    close(fd);
    return size > 0 && strcmp(name, "com.miui.home") == 0;
}

struct Image {
    uintptr_t base = 0;
    const ElfW(Phdr) *phdr = nullptr;
    size_t phnum = 0;
    char path[768]{};
};
struct Images {
    Image launcher{}, dart{};
    unsigned launcherCount = 0, dartCount = 0;
};
int scanImage(dl_phdr_info *info, size_t, void *opaque) {
    auto &images = *static_cast<Images *>(opaque);
    const char *path = info->dlpi_name;
    if (!path)
        return 0;
    const char *name = strrchr(path, '/');
    name = name ? name + 1 : path;
    Image *target = nullptr;
    if (strcmp(name, "libapp_launcher.so") == 0) {
        target = &images.launcher;
        ++images.launcherCount;
    }
    if (strcmp(name, "libapp.so") == 0) {
        target = &images.dart;
        ++images.dartCount;
    }
    if (target && strlen(path) < sizeof(target->path)) {
        target->base = info->dlpi_addr;
        target->phdr = info->dlpi_phdr;
        target->phnum = info->dlpi_phnum;
        strcpy(target->path, path);
    }
    return 0;
}
bool executable(const Image &image, uintptr_t rva, size_t size) {
    for (size_t i = 0; i < image.phnum; ++i) {
        const auto &p = image.phdr[i];
        if (p.p_type == PT_LOAD && (p.p_flags & PF_X) && rva >= p.p_vaddr && rva + size >= rva &&
            rva + size <= p.p_vaddr + p.p_memsz)
            return true;
    }
    return false;
}

// maps contains APK offsets, while dlpi_addr is an ELF load bias. Recover the
// ZIP entry's offset from a PT_LOAD mapping rather than guessing the APK base.
bool fileIdentity(const Image &image, size_t size, const char *expected) {
    char path[sizeof(image.path)]{};
    strcpy(path, image.path);
    uint64_t offset = 0;
    if (char *archive = strstr(path, "!/")) {
        *archive = 0;
        FILE *maps = fopen("/proc/self/maps", "re");
        if (!maps)
            return false;
        char line[1536];
        bool found = false;
        while (fgets(line, sizeof(line), maps)) {
            unsigned long long begin, end, fileOffset;
            int pathStart = 0;
            if (sscanf(line, "%llx-%llx %*s %llx %*s %*s %n", &begin, &end, &fileOffset,
                       &pathStart) != 3)
                continue;
            if (!pathStart)
                continue;
            char *mapped = line + pathStart;
            mapped[strcspn(mapped, "\r\n")] = 0;
            if (strcmp(mapped, path) != 0)
                continue;
            for (size_t i = 0; i < image.phnum; ++i) {
                const auto &p = image.phdr[i];
                uintptr_t address = image.base + p.p_vaddr;
                if (p.p_type == PT_LOAD && p.p_filesz && address >= begin && address < end &&
                    fileOffset + address - begin >= p.p_offset) {
                    uint64_t candidate = fileOffset + address - begin - p.p_offset;
                    if (found && candidate != offset) {
                        fclose(maps);
                        return false;
                    }
                    offset = candidate;
                    found = true;
                }
            }
        }
        fclose(maps);
        if (!found)
            return false;
    }
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0)
        return false;
    Sha256 hash;
    uint8_t buffer[16384];
    size_t done = 0;
    while (done < size) {
        size_t count = size - done;
        if (count > sizeof(buffer))
            count = sizeof(buffer);
        ssize_t got = pread(fd, buffer, count, off_t(offset + done));
        if (got <= 0) {
            close(fd);
            return false;
        }
        hash.update(buffer, size_t(got));
        done += size_t(got);
    }
    close(fd);
    char digest[65];
    hash.finish(digest);
    return strcmp(digest, expected) == 0;
}

struct RString {
    char *data;
    size_t length, capacity;
    const void *vtable;
};
struct OptionString {
    uint64_t tag;
    RString value;
};
struct PrestartResult {
    uint8_t tag;
    uint8_t padding[7];
    RString error;
};
struct NativeResult {
    uint8_t bytes[48];
};
static_assert(sizeof(PrestartResult) == 40 && sizeof(OptionString) == 40);
struct Api {
    void *(*pmDefault)();
    void (*pmDrop)(void *);
    NativeResult (*feature)(void *, const char *, size_t, uint32_t);
    void *(*intentDefault)();
    void (*intentDrop)(void *);
    void (*setAction)(void *, OptionString *);
    void (*setExtras)(void *, void *);
    void *(*bundleDefault)();
    void (*bundleDrop)(void *);
    void (*insertI32)(void *, RString *, int32_t);
    void (*insertI64)(void *, RString *, int64_t);
    void (*insertString)(void *, RString *, OptionString *);
    PrestartResult (*prestart)(const char *, size_t, void *, int32_t);
    void *pm = nullptr;
} api{};

template <typename T> bool resolve(T &target, void *handle, const char *name) {
    target = reinterpret_cast<T>(dlsym(handle, name));
    if (!target)
        __android_log_print(ANDROID_LOG_ERROR, kTag, "missing native API: %s", name);
    return target != nullptr;
}
bool resolveApi(void *handle) {
    bool ok = resolve(api.pmDefault, handle, "PackageManager_default") &&
              resolve(api.pmDrop, handle, "PackageManager_drop") &&
              resolve(api.feature, handle, "PackageManager_has_system_feature") &&
              resolve(api.intentDefault, handle, "Intent_default") &&
              resolve(api.intentDrop, handle, "Intent_drop") &&
              resolve(api.setAction, handle, "Intent_set_action") &&
              resolve(api.setExtras, handle, "Intent_set_extras") &&
              resolve(api.bundleDefault, handle, "Bundle_default") &&
              resolve(api.bundleDrop, handle, "Bundle_drop") &&
              resolve(api.insertI32, handle, "Bundle_insert_i32") &&
              resolve(api.insertI64, handle, "Bundle_insert_i64") &&
              resolve(api.insertString, handle, "Bundle_insert_string") &&
              resolve(api.prestart, handle, "context_prestart_process");
    if (ok)
        api.pm = api.pmDefault();
    return ok && api.pm;
}
bool feature(const char *suffix) {
    char name[128];
    snprintf(name, sizeof(name), "%s%s", kPrefix, suffix);
    NativeResult result = api.feature(api.pm, name, strlen(name), 0);
    if (result.bytes[0] & 1) {
        // Stop on an ABI/service error instead of repeatedly leaking opaque
        // foreign errors or interpreting an error payload as a boolean.
        apiFailed = true;
        return false;
    }
    return result.bytes[1] & 1;
}
bool owned(const char *text, RString &output) {
    size_t size = strlen(text);
    char *data = static_cast<char *>(malloc(size ? size : 1));
    if (!data)
        return false;
    memcpy(data, text, size);
    output = {data, size, size, reinterpret_cast<void *>(launcherBase + kStringVtable)};
    return true;
}
bool addI32(void *bundle, const char *key, int32_t value) {
    RString name{};
    if (!owned(key, name))
        return false;
    api.insertI32(bundle, &name, value);
    return true;
}
bool addI64(void *bundle, const char *key, int64_t value) {
    RString name{};
    if (!owned(key, name))
        return false;
    api.insertI64(bundle, &name, value);
    return true;
}
bool addString(void *bundle, const char *key, const char *value) {
    RString name{};
    OptionString text{};
    if (!owned(key, name))
        return false;
    if (!owned(value, text.value)) {
        free(name.data);
        return false;
    }
    api.insertString(bundle, &name, &text);
    return true;
}

enum class Kind : uint32_t { Begin, End, Cancel, MenuBegin, MenuEnd, Command };
struct Event {
    Kind kind{};
    Task task{};
    uint64_t generation = 0, time = 0, duration = 0;
    int operation = 0;
    uint64_t epoch = 0;
};
constexpr unsigned kQueueSize = 64;
Event queue[kQueueSize]{};
std::atomic<unsigned> writeIndex{0}, readIndex{0};
std::atomic<bool> overflow{false};
uint64_t uiConfigEpoch = 0; // accessed only by the producer's owning Dart thread
void wakeWorker() {
    int fd = eventFd.load(std::memory_order_relaxed);
    if (fd >= 0) {
        uint64_t one = 1;
        syscall(SYS_write, fd, &one, sizeof(one));
    }
}
bool enqueue(const Event &event) {
    unsigned write = writeIndex.load(std::memory_order_relaxed);
    if (write - readIndex.load(std::memory_order_acquire) >= kQueueSize) {
        overflow.store(true);
        wakeWorker();
        return false;
    }
    queue[write % kQueueSize] = event;
    queue[write % kQueueSize].epoch = uiConfigEpoch;
    writeIndex.store(write + 1, std::memory_order_release);
    wakeWorker();
    return true;
}
bool dequeue(Event &event) {
    unsigned read = readIndex.load(std::memory_order_relaxed);
    if (read == writeIndex.load(std::memory_order_acquire))
        return false;
    event = queue[read % kQueueSize];
    readIndex.store(read + 1, std::memory_order_release);
    return true;
}
Drag uiDrag{};
LongPress uiMenu{};
uint64_t nextGeneration = 0, pointerUp = 0;
std::atomic<uint64_t> firedMenu{0};

// These unaligned reads only happen synchronously on the owning Dart thread.
template <typename T> T load(uintptr_t at) {
    T value{};
    memcpy(&value, reinterpret_cast<void *>(at), sizeof(value));
    return value;
}
struct Dart {
    uintptr_t heap, null;
    bool object(uintptr_t value) const {
        return value > 0x100000000ull && (value & 1) && uint32_t(value) != uint32_t(null);
    }
    uintptr_t field(uintptr_t objectValue, size_t offset) const {
        if (!object(objectValue))
            return null;
        return heap + load<uint32_t>(objectValue + offset);
    }
    uint32_t cid(uintptr_t objectValue) const {
        return object(objectValue) ? uint32_t((load<uint64_t>(objectValue - 1) >> 12) & 0xfffff)
                                   : 0;
    }
    bool string(uintptr_t value, char (&out)[256]) const {
        if (cid(value) != 94)
            return false;
        uint32_t size = load<uint32_t>(value + 7);
        if (size & 1)
            return false;
        size >>= 1;
        if (!size || size >= sizeof(out))
            return false;
        for (unsigned i = 0; i < size; ++i) {
            char c = load<char>(value + 15 + i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
                  c == '.' || c == '_'))
                return false;
            out[i] = c;
        }
        out[size] = 0;
        return true;
    }
    bool task(uintptr_t model, Task &out) const {
        uintptr_t info = field(model, 0xf), key = field(info, 7);
        if (!object(key))
            return false;
        int64_t id = load<int64_t>(key + 7), user = load<int64_t>(key + 0x23);
        if (id < 0 || id > INT32_MAX || user < 0 || user > 10000)
            return false;
        uintptr_t intent = field(key, 0x1f), component = field(intent, 0x1f);
        uintptr_t name = field(component, 7);
        if (!string(name, out.package)) {
            uintptr_t top = field(info, 0x7f);
            name = field(top, 7);
            if (!string(name, out.package) && !string(field(intent, 0x23), out.package))
                return false;
        }
        out.id = int32_t(id);
        out.user = int32_t(user);
        return true;
    }
    bool infoClosure(uintptr_t closure) const {
        return object(closure) && load<uintptr_t>(closure + 0x1f) == dartBase + kInfoCallback;
    }
    bool menuTask(uintptr_t closure, Task &out) const {
        if (!infoClosure(closure))
            return false;
        uintptr_t state = field(field(closure, 0x17), 0xf);
        uintptr_t controller = field(state, 0x17);
        return task(field(controller, 0x21b), out);
    }
};

void sendCommand(const Event &event) {
    uint32_t mask = config.load(std::memory_order_acquire);
    if (!(mask & (event.operation == 2 ? kSettings : kDrag)) || apiFailed)
        return;
    void *intent = api.intentDefault();
    void *bundle = api.bundleDefault();
    if (!intent || !bundle) {
        if (intent)
            api.intentDrop(intent);
        if (bundle)
            api.bundleDrop(bundle);
        return;
    }
    OptionString action{};
    if (!owned("io.github.a13e300.myinjector.miuihome.COMMAND", action.value)) {
        api.intentDrop(intent);
        api.bundleDrop(bundle);
        return;
    }
    api.setAction(intent, &action);
    bool ok = addI32(bundle, "operation", event.operation) &&
              addI64(bundle, "generation", int64_t(event.generation)) &&
              addI32(bundle, "taskId", event.task.id) &&
              addI32(bundle, "userId", event.task.user) &&
              addI64(bundle, "durationMs", int64_t(event.duration)) &&
              addString(bundle, "package", event.task.package);
    if (!ok) {
        api.bundleDrop(bundle);
        api.intentDrop(intent);
        return;
    }
    api.setExtras(intent, bundle); // consumes the Bundle into the Intent
    constexpr char marker[] = "io.github.a13e300.myinjector.miuihome.command";
    PrestartResult result = api.prestart(marker, sizeof(marker) - 1, intent, event.task.user);
    if (result.tag & 1) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "bridge command failed: op=%d",
                            event.operation);
        if (result.error.vtable) {
            auto drop = reinterpret_cast<void (*)(RString *)>(
                load<uintptr_t>(reinterpret_cast<uintptr_t>(result.error.vtable) + 24));
            if (drop)
                drop(&result.error);
        }
    }
    api.intentDrop(intent);
}

void cancelUi(uint64_t now) {
    uiDrag.cancel();
    uiMenu.cancel();
    enqueue({Kind::Cancel, {}, 0, now, 0, 0});
}

bool install(const Images &images) {
    if (!fileIdentity(images.launcher, 16220456,
                      "89302282d70e3b40126ccce05fef94560529c0543ff6e05107724dade0d104a7") ||
        !fileIdentity(images.dart, 29448568,
                      "451c79322ddcb1e3d3bd48657bda591a4370b1d8843967573f8601c7ee64695c")) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "unsupported launcher image; hooks disabled");
        return false;
    }
    for (const auto &target : kTargets) {
        if (!executable(images.dart, target.rva, sizeof(target.prefix)) ||
            memcmp(reinterpret_cast<void *>(images.dart.base + target.rva), target.prefix,
                   sizeof(target.prefix))) {
            __android_log_print(ANDROID_LOG_ERROR, kTag, "Dart hook fingerprint mismatch at %#lx",
                                static_cast<unsigned long>(target.rva));
            return false;
        }
    }
    launcherBase = images.launcher.base;
    dartBase = images.dart.base;
    void *handle = launcherHandle.load(std::memory_order_acquire);
    if (!handle)
        handle = dlopen(images.launcher.path, RTLD_NOW | RTLD_NOLOAD);
    if (!handle || !resolveApi(handle) || !EnsureLsposedMadviseGuard()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "native API or madvise guard unavailable; hooks disabled");
        if (api.pm)
            api.pmDrop(api.pm);
        api.pm = nullptr;
        return false;
    }
    void (*replacements[])() = {MyInjectorDragStart,         MyInjectorDragEndVertical,
                                MyInjectorDragEndHorizontal, MyInjectorCommit,
                                MyInjectorMenuDown,          MyInjectorMenuUp,
                                MyInjectorMenuClick,         MyInjectorPointer};
    unsigned count = 0;
    for (; count < 8; ++count) {
        if (InstallInlineHook(reinterpret_cast<void *>(dartBase + kTargets[count].rva),
                              reinterpret_cast<void *>(replacements[count]),
                              &myinjector_dart_original[count]) != kHookSuccess)
            break;
    }
    if (count != 8) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Dart hook installation failed at index %u",
                            count);
        while (count)
            RemoveInlineHook(reinterpret_cast<void *>(dartBase + kTargets[--count].rva));
        if (api.pm)
            api.pmDrop(api.pm);
        api.pm = nullptr;
        return false;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "7751 native hooks ready: dart=%p launcher=%p",
                        reinterpret_cast<void *>(dartBase), reinterpret_cast<void *>(launcherBase));
    return true;
}

void *worker(void *) {
    Images images{};
    for (unsigned attempt = 0; attempt < 600 && !installed; ++attempt) {
        if (!isLauncher())
            return nullptr;
        images = {};
        dl_iterate_phdr(scanImage, &images);
        if (images.launcherCount == 1 && images.dartCount == 1 && images.launcher.base &&
            images.dart.base) {
            installed = install(images);
            if (!installed)
                return nullptr;
        } else {
            timespec pause{0, 50000000};
            nanosleep(&pause, nullptr);
        }
    }
    if (!installed)
        return nullptr;
    nextGeneration = nowNs();
    int fd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    if (fd < 0)
        return nullptr;
    eventFd.store(fd);
    Drag drag{};
    Task menu{};
    uint64_t generation = 0, menuTime = 0, nextConfig = 0;
    bool menuHeld = false;
    while (workerPid.load() == getpid() && !apiFailed) {
        uint64_t now = nowNs();
        if (now >= nextConfig) {
            bool ready = feature("v1");
            uint32_t mask = 0;
            if (ready && feature("dragKill"))
                mask |= kDrag;
            if (ready && feature("openAospSettings"))
                mask |= kSettings;
            if (apiFailed)
                mask = 0;
            if (config.exchange(mask, std::memory_order_acq_rel) != mask) {
                configEpoch.fetch_add(1);
                drag.cancel();
                menuHeld = false;
                __android_log_print(ANDROID_LOG_INFO, kTag, "native config mask=%u", mask);
            }
            nextConfig = now + 1000000000;
        }
        if (overflow.exchange(false)) {
            drag.cancel();
            menuHeld = false;
            readIndex.store(writeIndex.load(std::memory_order_acquire), std::memory_order_release);
            configEpoch.fetch_add(1);
        }
        Event event{};
        while (dequeue(event)) {
            if (event.epoch != configEpoch.load(std::memory_order_acquire))
                continue;
            switch (event.kind) {
            case Kind::Begin:
                drag.begin(event.task, event.generation, event.time);
                break;
            case Kind::End:
                drag.finish(event.time);
                break;
            case Kind::Cancel:
                drag.cancel();
                menuHeld = false;
                break;
            case Kind::MenuBegin:
                menu = event.task;
                generation = event.generation;
                menuTime = event.time;
                menuHeld = true;
                break;
            case Kind::MenuEnd:
                menuHeld = false;
                break;
            case Kind::Command:
                sendCommand(event);
                if (event.operation == 1)
                    drag.cancel();
                break;
            }
        }
        now = nowNs();
        if ((config.load() & kDrag) && drag.hintDue(now)) {
            drag.hinted = true;
            sendCommand({Kind::Command, drag.task, drag.generation, now, 700, 3});
        }
        if ((config.load() & kSettings) && menuHeld && now >= menuTime &&
            now - menuTime >= 500 * kMillisecond) {
            sendCommand({Kind::Command, menu, generation, now, (now - menuTime) / kMillisecond, 2});
            firedMenu.store(generation, std::memory_order_release);
            menuHeld = false;
        }
        int timeout = (drag.active || menuHeld) ? 10 : 1000;
        pollfd pending{fd, POLLIN, 0};
        poll(&pending, 1, timeout);
        uint64_t notifications;
        while (read(fd, &notifications, sizeof(notifications)) > 0) {
        }
    }
    config.store(0, std::memory_order_release);
    eventFd.store(-1);
    close(fd);
    if (api.pm) {
        api.pmDrop(api.pm);
        api.pm = nullptr;
    }
    return nullptr;
}
void startWorker() {
    if (!isLauncher())
        return;
    pid_t expected = 0, pid = getpid();
    if (!workerPid.compare_exchange_strong(expected, pid))
        return;
    pthread_t thread{};
    if (pthread_create(&thread, nullptr, worker, nullptr) == 0)
        pthread_detach(thread);
    else
        workerPid.store(0);
}
void loaded(const char *name, void *handle) {
    if (!name)
        return;
    const char *base = strrchr(name, '/');
    base = base ? base + 1 : name;
    if (strcmp(base, "libapp_launcher.so") == 0)
        launcherHandle.store(handle, std::memory_order_release);
    startWorker();
}
void afterFork() {
    workerPid.store(0);
    config.store(0);
    ownerThread.store(0);
    int fd = eventFd.exchange(-1);
    if (fd >= 0)
        close(fd);
    writeIndex.store(0);
    readIndex.store(0);
    overflow.store(false);
}
} // namespace

// Called only by the register-preserving assembly observer. No Rust/VM calls,
// JNI, allocation, logging, blocking, or foreign object destruction here.
extern "C" __attribute__((visibility("hidden"))) int
MyInjectorObserveDart(const uint64_t *registers, unsigned kind) {
    uint32_t mask = config.load(std::memory_order_acquire);
    if (!mask)
        return 0;
    uintptr_t thread = registers[26], heap = registers[28] << 32;
    uintptr_t expected = 0;
    if (kind != 0 && kind != 4 && ownerThread.load() == 0)
        return 0;
    if (ownerThread.load() == 0) {
        Dart candidate{heap, registers[22]};
        Task target{};
        if (kind == 0) {
            uintptr_t state = load<uintptr_t>(registers[29] - 8);
            if (!candidate.task(candidate.field(candidate.field(state, 0xb), 0xb), target))
                return 0;
        } else if (kind == 4) {
            uintptr_t state = registers[1];
            if (candidate.cid(state) != 0x1b05 ||
                !candidate.menuTask(candidate.field(candidate.field(state, 0xb), 0xb), target))
                return 0;
        }
    }
    if (ownerThread.compare_exchange_strong(expected, thread))
        ownerHeap = heap;
    if (ownerThread.load() != thread || ownerHeap != heap)
        return 0;
    Dart dart{heap, registers[22]};
    uint64_t now = nowNs();
    Task task{};
    uint64_t epoch = configEpoch.load(std::memory_order_acquire);
    if (uiConfigEpoch != epoch) {
        uiConfigEpoch = epoch;
        cancelUi(now);
    }
    if (kind == 0 && (mask & kDrag)) {
        uintptr_t state = load<uintptr_t>(registers[29] - 8);
        if (dart.task(dart.field(dart.field(state, 0xb), 0xb), task)) {
            uiDrag.begin(task, ++nextGeneration, now);
            pointerUp = 0;
            enqueue({Kind::Begin, task, uiDrag.generation, now, 0, 0});
        }
    } else if ((kind == 1 || kind == 2) && uiDrag.active) {
        uint64_t end =
            pointerUp && now >= pointerUp && now - pointerUp < 100 * kMillisecond ? pointerUp : now;
        uiDrag.finish(end);
        enqueue({Kind::End, {}, uiDrag.generation, end, 0, 0});
    } else if (kind == 3 && (mask & kDrag) && dart.task(registers[2], task)) {
        uint64_t duration = uiDrag.duration(), generation = uiDrag.generation;
        if (uiDrag.commit(task, now))
            enqueue({Kind::Command, task, generation, now, duration, 1});
    } else if (kind == 4 && (mask & kSettings)) {
        uintptr_t state = registers[1];
        // CID 0x1b05 is _MenuItemButtonState in the pinned AOT image.
        uintptr_t closure = dart.field(dart.field(state, 0xb), 0xb);
        if (dart.cid(state) == 0x1b05 && dart.menuTask(closure, task)) {
            uiMenu.begin(task, ++nextGeneration, now);
            enqueue({Kind::MenuBegin, task, uiMenu.generation, now, 0, 0});
        }
    } else if (kind == 5 && uiMenu.tracking) {
        uintptr_t state = registers[1];
        if (dart.cid(state) == 0x1b05 &&
            dart.menuTask(dart.field(dart.field(state, 0xb), 0xb), task) && task == uiMenu.task) {
            uiMenu.finish(now);
            enqueue({Kind::MenuEnd, {}, uiMenu.generation, now, 0, 0});
        }
    } else if (kind == 6 && (mask & kSettings) && uiMenu.tracking) {
        uintptr_t closure = load<uintptr_t>(registers[15]);
        if (dart.menuTask(closure, task) && task == uiMenu.task &&
            (uiMenu.duration(now) >= 500 ||
             firedMenu.load(std::memory_order_acquire) == uiMenu.generation)) {
            if (!enqueue({Kind::Command, task, uiMenu.generation, now, uiMenu.duration(now), 2}))
                return 0;
            uiMenu.cancel();
            return 1;
        }
    } else if (kind == 7) {
        uint32_t cid = dart.cid(registers[2]);
        if (cid == 0x19ba || cid == 0x16cf)
            cancelUi(now); // PointerCancelEvent
        else if (cid == 0x19c6 || cid == 0x16e5) {
            cancelUi(now);
            pointerUp = 0;
        } // PointerDownEvent
        else if (cid == 0x19c2 || cid == 0x16e1) {
            pointerUp = now;
            if (uiMenu.tracking) {
                uiMenu.finish(now);
                enqueue({Kind::MenuEnd, {}, uiMenu.generation, now, 0, 0});
            }
        } else if ((cid == 0x19c4 || cid == 0x16e3) && uiMenu.tracking) {
            uintptr_t event = cid == 0x16e3 ? dart.field(registers[2], 0xf) : registers[2];
            uintptr_t delta = dart.field(event, 0x33);
            if (dart.object(delta))
                uiMenu.move(load<double>(delta + 7), load<double>(delta + 0xf));
            else
                uiMenu.cancel();
            if (!uiMenu.tracking)
                enqueue({Kind::MenuEnd, {}, uiMenu.generation, now, 0, 0});
        }
    }
    return 0;
}

extern "C" __attribute__((visibility("default"))) NativeOnModuleLoaded
native_init(const NativeAPIEntries *entries) {
    if (!isHyos() || !InitializeLsposedHookBackend(entries))
        return nullptr;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "LSPosed HYOS native_init pid=%d uid=%d launcher=%d", getpid(), getuid(),
                        isLauncher());
    pthread_atfork(nullptr, nullptr, afterFork);
    startWorker();
    return loaded;
}
