#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* This public header always describes the latest native API. */
#define XPOSED_NATIVE_API_VERSION 103u
#define XPOSED_NATIVE_ENTRY_SYMBOL "libxposed_native_entry"

#if defined(__GNUC__)
#define XPOSED_EXPORT __attribute__((visibility("default"), used))
#else
#define XPOSED_EXPORT
#endif

#ifdef __cplusplus
#define XPOSED_EXTERN_C extern "C"
#else
#define XPOSED_EXTERN_C
#endif

typedef int32_t XposedStatus;

#define XPOSED_STATUS_OK               ((XposedStatus)0)
#define XPOSED_STATUS_INVALID_ARGUMENT ((XposedStatus)1)
#define XPOSED_STATUS_NOT_FOUND        ((XposedStatus)2)
#define XPOSED_STATUS_UNSUPPORTED      ((XposedStatus)3)
#define XPOSED_STATUS_IO_ERROR         ((XposedStatus)4)
#define XPOSED_STATUS_SERVICE_ERROR    ((XposedStatus)5)


#define XPOSED_PROP_CAP_SYSTEM        (UINT64_C(1) << 0)
#define XPOSED_PROP_CAP_REMOTE        (UINT64_C(1) << 1)
#define XPOSED_PROP_RT_API_PROTECTION (UINT64_C(1) << 2)

typedef struct XposedStringView {
    /* Borrowed bytes; not necessarily NUL-terminated. */
    const char* data;
    size_t size;
} XposedStringView;

typedef struct XposedStringList XposedStringList;
typedef struct XposedRemotePrefs XposedRemotePrefs;
typedef struct XposedPrefsSnapshot XposedPrefsSnapshot;
typedef struct XposedPrefsSubscription XposedPrefsSubscription;
typedef struct XposedStringSet XposedStringSet;

typedef uint32_t XposedPrefType;

#define XPOSED_PREF_BOOLEAN    ((XposedPrefType)1u)
#define XPOSED_PREF_INT        ((XposedPrefType)2u)
#define XPOSED_PREF_LONG       ((XposedPrefType)3u)
#define XPOSED_PREF_FLOAT      ((XposedPrefType)4u)
#define XPOSED_PREF_STRING     ((XposedPrefType)5u)
#define XPOSED_PREF_STRING_SET ((XposedPrefType)6u)

typedef struct XposedPrefValue {
    XposedPrefType type;
    union {
        int32_t boolean_value;
        int32_t int_value;
        int64_t long_value;
        float float_value;
        XposedStringView string_value;
        const XposedStringSet* string_set_value;
    } value;
} XposedPrefValue;

/*
 * Invoked serially on the subscription's framework-owned thread. Notifications
 * coalesce; zero keys means full invalidation. preferences, changed_keys and their
 * bytes are borrowed until return. Complete configuration consumption before
 * returning: the framework sends its internal ACK only after this callback returns.
 * If dispatching to another worker, wait for that work to complete before return;
 * merely enqueueing it does not acknowledge consumption. Do not wait for a thread
 * that is itself waiting for unsubscribe (unsubscribe joins this callback).
 * callback_data is supplied by the subscriber and passed through unchanged.
 */
typedef void (*XposedPrefsChangedFn)(void* callback_data,
                                     XposedRemotePrefs* preferences,
                                     const XposedStringView* changed_keys,
                                     size_t changed_key_count);

typedef XposedStatus (*XposedHookFn)(void* context, void* function, void* replacement,
                                    void** backup);

typedef XposedStatus (*XposedUnhookFn)(void* context, void* function);

/*
 * API supplied to the native entry in an ART process. Both function pointers
 * are always non-null. No ART/Java hook API is exposed through this profile.
 */
typedef struct XposedArtApi {
    void* context;
    XposedHookFn hook;
    XposedUnhookFn unhook;
} XposedArtApi;

/*
 * API supplied to the native entry in a pure native runtime. Every function
 * pointer in this table is always non-null.
 */
typedef struct XposedRuntimeApi {
    /* The table and its context remain valid for this module generation. */
    void* context;

    XposedHookFn hook;
    XposedUnhookFn unhook;

    uint32_t (*get_api_version)(void* context);
    /* Returned framework strings remain valid for the API table's lifetime. */
    const char* (*get_framework_name)(void* context);
    const char* (*get_framework_version)(void* context);
    int64_t (*get_framework_version_code)(void* context);
    uint64_t (*get_framework_properties)(void* context);

    /* priority uses Android's ANDROID_LOG_* values. */
    void (*log)(void* context, int priority, const char* tag, const char* message);

    /* group is a non-null UTF-8 C string (at most 4096 bytes); the empty group is valid.
     * Creating this handle does not fetch values or start a subscription. */
    XposedStatus (*get_remote_preferences)(void* context, const char* group,
                                           XposedRemotePrefs** preferences);
    void (*release_remote_preferences)(XposedRemotePrefs* preferences);
    /* Each call fetches a new immutable remote snapshot, independently of subscriptions. */
    XposedStatus (*get_preferences_snapshot)(XposedRemotePrefs* preferences,
                                             XposedPrefsSnapshot** snapshot);
    void (*release_preferences_snapshot)(XposedPrefsSnapshot* snapshot);
    size_t (*get_preferences_snapshot_size)(const XposedPrefsSnapshot* snapshot);
    XposedStatus (*get_preferences_snapshot_key)(
        const XposedPrefsSnapshot* snapshot, size_t index, XposedStringView* key);
    XposedStatus (*get_preferences_snapshot_value)(
        const XposedPrefsSnapshot* snapshot, XposedStringView key,
        XposedPrefValue* value);
    /* String and string-set views remain valid until the snapshot is released. */
    size_t (*get_string_set_size)(const XposedStringSet* string_set);
    XposedStatus (*get_string_set_value)(const XposedStringSet* string_set,
                                         size_t index, XposedStringView* value);
    /* One invalidation is in flight until its callback completes. Slow/frozen
     * consumers do not lose their connection due to elapsed time or backpressure.
     * Changes coalesce; overflow and initial subscription cause full invalidation.
     * Snapshot + subscribe are not atomic. Unsubscribe cancels pending I/O and joins
     * the callback, or may be called from the callback itself. Transport failures
     * reconnect internally and trigger full invalidation; consumers never resubscribe.
     * Always unsubscribe to release the handle. No connection status is exposed. */
    XposedStatus (*subscribe_remote_preferences)(
        XposedRemotePrefs* preferences, XposedPrefsChangedFn callback,
        void* callback_data, XposedPrefsSubscription** subscription);
    void (*unsubscribe_remote_preferences)(XposedPrefsSubscription* subscription);

    XposedStatus (*list_remote_files)(void* context, XposedStringList** files);
    size_t (*get_string_list_size)(const XposedStringList* list);
    XposedStatus (*get_string_list_value)(const XposedStringList* list, size_t index,
                                          XposedStringView* value);
    /* String views remain valid until the list is released. */
    void (*release_string_list)(XposedStringList* list);
    /* On success, the caller owns a read-only, CLOEXEC fd and must close it.
     * name is a non-empty UTF-8 C string of at most 4096 bytes. */
    XposedStatus (*open_remote_file)(void* context, const char* name, int* fd);
} XposedRuntimeApi;

typedef struct XposedArtModule {
    void (*on_loaded)(const XposedArtApi* api, const char* module_apk_path);
    void (*on_library_loaded)(const char* name, void* handle);
} XposedArtModule;

typedef struct XposedRuntimeModule {
    void (*on_loaded)(const XposedRuntimeApi* api, const char* module_apk_path);
    void (*on_library_loaded)(const char* name, void* handle);

    /* Hot reload callbacks will be defined before this ABI is made public. */
} XposedRuntimeModule;

typedef struct XposedModule {
    /* A null profile means that the module does not participate in that runtime. */
    const XposedArtModule* art;
    const XposedRuntimeModule* native_runtime;
} XposedModule;

/*
 * This entry envelope is permanent. api_version describes the native header or
 * language binding that produced the module; it is independent of targetApi in
 * module metadata. The framework validates it against the module's declared
 * [minApi, targetApi] range and performs compatibility adaptation internally.
 */
typedef struct XposedEntry {
    uint32_t api_version;
    const void* module;
} XposedEntry;

typedef const XposedEntry* (*XposedEntryFn)(void);

XPOSED_EXTERN_C XPOSED_EXPORT const XposedEntry* libxposed_native_entry(void);

/*
 * Export a module using the native API version represented by this header.
 * Language bindings should provide an equivalent helper which embeds the
 * binding's API version; module authors must not set api_version themselves.
 */
#define XPOSED_EXPORT_MODULE(module_)                                               \
    XPOSED_EXTERN_C XPOSED_EXPORT const XposedEntry* libxposed_native_entry(void) { \
        static const XposedEntry xposed_native_entry_ = {XPOSED_NATIVE_API_VERSION, \
                                                         &(module_)};               \
        return &xposed_native_entry_;                                               \
    }

#ifdef __cplusplus
}
#endif
