package io.github.a13e300.myinjector

import android.content.Context
import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.hookBefore
import java.lang.reflect.Method
import kotlin.concurrent.thread

/**
 * Runs inside the MIUI permission manager service (com.lbe.security.miui).
 *
 * HyperOS 3 (permission manager 1.9.2, packages under com.lbe.security.*) removed the
 * legacy permmgr "active" table (suggestAccept) that the "保持自启动" switch used to
 * read and write. The auto-start revoke scheduler then only skipped packages listed in
 * PermissionConfigSharePref's AUTO_START_WHITE_LIST, private storage of this app, so
 * the permmgr provider is extended with two module-only call() methods that toggle the
 * native whitelist in-process (see MIUISecurityCenterHandler for the callers). While
 * the module is enabled, the scheduler check is additionally nop-ed as a fallback.
 *
 * Android 17 / HyperOS next (permission manager 4.0.0, security center 13.5.8) moved
 * the whole namespace to com.hyperos.security.* and turned autostart into the plain
 * framework permission PERM_ID_AUTOSTART (com.miui.permission.PermissionManager.
 * setApplicationPermission, action 3 = allow / 1 = deny). The AUTO_START_WHITE_LIST
 * helpers and the 30-day scheduler are gone from the service; the keep flag keeps the
 * same storage and is enforced here. Semantics: while keep is ON the autostart
 * permission is locked ON; turn keep OFF first to disable autostart for that package.
 *
 * Enforcement is deliberately surgical (hardened twice after real-device feedback):
 * - Storage must never force PermissionConfigSharePref class initialization: its
 *   static block runs Application.getApplication().getSharedPreferences(...), and
 *   triggering it from an early binder thread (before the Application is up) NPEs
 *   the static initializer and permanently poisons the class - the stock service
 *   then dies with NoClassDefFoundError on every restart: a ~10s crash loop that
 *   took down the whole permission manager. The SharedPreferences file is
 *   process-global, so on 4.x the keep list is opened through the process
 *   application context, which yields the exact same cached instance without
 *   loading any service class. The mSp static-field reflection is gone.
 * - PERM_ID_AUTOSTART is read at runtime as a Number and enforcement is installed
 *   only when the read succeeds (the device log confirmed the value 16384, the old
 *   suggestAccept bit); a wrong guessed id would have the watchdog write foreign
 *   permission ids into the store.
 * - Only the exact setApplicationPermission overloads the service's own autostart
 *   paths use are inspected (dealOnePush: (long,int,int,String) deny;
 *   AutoStartManagerProvider.switchAutoStart: (long,int,int,int,Bundle,String);
 *   provider case 6 / security center: (long,int,int,String[])). Positional arg
 *   guessing across every overload could veto unrelated permission changes.
 * - The veto returns a return-type-safe default instead of raw null, so primitive-
 *   returning overloads cannot crash the caller on unboxing.
 * - The watchdog re-grants only after querying the current action through the
 *   service's own PermissionUtil, so healthy entries are never re-written.
 */
class LbeHandler : IHook() {
    override fun onHook() {
        if (loadPackageParam.packageName != "com.lbe.security.miui") return

        // HyperOS 3 and older: neuter the periodic auto-revoke scheduler as a fallback.
        // Android 17's service no longer ships it.
        val legacyScheduler = findClassOrNull("com.miui.privacy.autostart.AutoRevokePermissionManager")
        if (legacyScheduler != null) {
            legacyScheduler.hookAllNop("lambda\$startScheduleASCheck\$1")
        } else {
            logI("keep-autostart: legacy AutoRevokePermissionManager absent (Android 17 perm service)")
        }

        // 4.0.0 renamed every class into com.hyperos.security.*; keep the old names for HyperOS 3.
        val flagManager = findClassOrNull("com.hyperos.security.utility.PermissionConfigSharePref")
            ?: findClassOrNull("com.lbe.security.utility.PermissionConfigSharePref")
        if (flagManager == null) {
            logI("keep-autostart: PermissionConfigSharePref missing, provider extension NOT installed")
            return
        }
        val provider = findClassOrNull("com.hyperos.security.service.provider.PermissionManagerProvider")
            ?: findClassOrNull("com.lbe.security.service.provider.PermissionManagerProvider")
        if (provider == null) {
            logI("keep-autostart: PermissionManagerProvider missing, provider extension NOT installed")
            return
        }
        val legacyHelpers = flagManager.declaredMethods.any { it.name == LEGACY_METHOD_IS_KEEP }
        logI("keep-autostart: flagManager=${flagManager.name} legacyHelpers=$legacyHelpers")

        // The keep list always lives in the service-private SharedPreferences
        // "permission_config_sharePref" (key AUTO_START_WHITE_LIST). On 4.x the file
        // is opened through the process application context: the exact same cached
        // SharedPreferences instance the service uses, but WITHOUT ever loading
        // PermissionConfigSharePref - its static block runs Application.getApplication()
        // and crashes (permanently poisoning the class) when triggered before the
        // Application is ready, which is what crash-looped the service. HyperOS 3
        // keeps using the stock helper methods.
        val activityThread = findClassOrNull("android.app.ActivityThread")
        var appCtx: Context? = null
        fun appContext(): Context? {
            appCtx?.let { return it }
            val ctx = runCatching {
                activityThread?.callS("currentApplication") as? Context
            }.getOrNull() ?: return null
            appCtx = ctx
            return ctx
        }

        fun keepSet(): Set<String> = runCatching {
            appContext()?.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                ?.getStringSet(KEY_KEEP_AUTO_START, emptySet()).orEmpty()
        }.getOrDefault(emptySet())

        fun readKeep(pkg: String): Boolean {
            if (legacyHelpers) {
                return runCatching { flagManager.callS(LEGACY_METHOD_IS_KEEP, pkg) as? Boolean }
                    .onFailure { logE("keep-autostart legacy GET $pkg failed", it) }
                    .getOrDefault(false) == true
            }
            return keepSet().contains(pkg)
        }

        fun writeKeep(pkg: String, enable: Boolean): Boolean {
            if (legacyHelpers) {
                flagManager.callS(
                    if (enable) LEGACY_METHOD_ADD_KEEP else LEGACY_METHOD_REMOVE_KEEP,
                    pkg
                )
                return true
            }
            val prefs = appContext()?.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE) ?: return false
            val set = (prefs.getStringSet(KEY_KEEP_AUTO_START, emptySet()) ?: emptySet()).toMutableSet()
            if (enable) set.add(pkg) else set.remove(pkg)
            prefs.edit().putStringSet(KEY_KEEP_AUTO_START, set).apply()
            return true
        }

        provider.hookBefore("call", String::class.java, String::class.java, Bundle::class.java) { param ->
            when (param.args[0] as? String) {
                METHOD_GET_KEEP_AUTOSTART -> {
                    val pkg = param.args[1] as? String
                    if (!pkg.isNullOrBlank()) {
                        param.result = Bundle().apply {
                            runCatching {
                                val keep = readKeep(pkg)
                                logI("keep-autostart GET $pkg -> $keep")
                                putBoolean("keep", keep)
                            }.onFailure {
                                logE("keep-autostart GET $pkg failed", it)
                                putBoolean("keep", false)
                            }
                        }
                    }
                }
                METHOD_SET_KEEP_AUTOSTART -> {
                    val extras = param.args[2] as? Bundle ?: return@hookBefore
                    val pkg = extras.getString("pkg")
                    if (!pkg.isNullOrBlank()) {
                        val enable = extras.getBoolean("enable")
                        param.result = Bundle().apply {
                            runCatching {
                                val ok = writeKeep(pkg, enable)
                                logI("keep-autostart SET $pkg enable=$enable ok=$ok")
                                putBoolean("ok", ok)
                            }.onFailure {
                                logE("keep-autostart SET $pkg failed", it)
                                putBoolean("ok", false)
                            }
                        }
                    }
                }
            }
        }
        logI("keep-autostart provider extension installed")

        // ------------------- Android 17 (service 4.x) enforcement -------------------
        // On 1.9.x the stock scheduler honours the whitelist itself; keep the proven
        // HyperOS 3 behaviour untouched there.
        if (legacyHelpers) return

        val permManager = findClassOrNull("com.miui.permission.PermissionManager") ?: run {
            logI("keep-autostart: framework PermissionManager missing, enforcement NOT installed")
            return
        }

        // Read the framework constant as a Number; if that fails, install nothing —
        // a wrong guessed id would poison the permission store through the watchdog.
        // (Confirmed 16384 = the old suggestAccept autostart bit on the device.)
        val permIdAutoStart = runCatching {
            permManager.getDeclaredField("PERM_ID_AUTOSTART").apply { isAccessible = true }
                .get(null) as? Number
        }.getOrNull()?.toLong() ?: run {
            logW("keep-autostart: PERM_ID_AUTOSTART unreadable, enforcement NOT installed")
            return
        }
        logI("keep-autostart: PERM_ID_AUTOSTART=$permIdAutoStart")

        // Only the overloads the service's own autostart paths use; every other
        // signature (whatever else the framework may declare) is skipped untouched.
        val knownSignatures = listOf(
            arrayOf<Class<*>>(java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE, String::class.java),
            arrayOf<Class<*>>(java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE, Array<String>::class.java),
            arrayOf(
                java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE,
                Bundle::class.java, String::class.java
            )
        )
        fun Method.isKnownSignature(): Boolean =
            knownSignatures.any { it.contentEquals(parameterTypes) }

        // A veto must not return null from a primitive-returning method (unboxing NPE).
        fun Method.defaultResult(): Any? = when (returnType) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null // void / reference types
        }

        permManager.hookAllBefore("setApplicationPermission") { param ->
            val method = param.method as? Method ?: return@hookAllBefore
            if (!method.isKnownSignature()) return@hookAllBefore
            if ((param.args.getOrNull(0) as? Number)?.toLong() != permIdAutoStart) return@hookAllBefore
            // action 1 is the only revoke action the system's autostart paths use
            if ((param.args.getOrNull(1) as? Number)?.toInt() != ACTION_DENY) return@hookAllBefore
            val targets = param.args.drop(2).flatMap { arg ->
                when (arg) {
                    is String -> if (arg.isNotBlank()) listOf(arg) else emptyList()
                    is Array<*> -> arg.filterIsInstance<String>().filter { it.isNotBlank() }
                    else -> emptyList()
                }
            }.filter { it in keepSet() }
            if (targets.isEmpty()) return@hookAllBefore
            logI("keep-autostart: veto PERM_ID_AUTOSTART deny for ${targets.joinToString()}")
            param.result = method.defaultResult()
        }
        logI("keep-autostart deny veto installed")

        // Safety net for revocations that never pass through this app (e.g. a direct
        // framework call from another process): every OnePushJobService run checks the
        // current action through the service's own PermissionUtil and re-grants only
        // what was actually denied. No blind writes.
        val onePush = findClassOrNull("com.hyperos.security.service.OnePushJobService")
        val permissionUtil = findClassOrNull("com.hyperos.security.utility.PermissionUtil")
        val packageUtil = findClassOrNull("com.hyperos.security.utility.PackageUtil")
        if (onePush == null || permissionUtil == null || packageUtil == null) {
            logI("keep-autostart: OnePushJobService/PermissionUtil/PackageUtil missing, watchdog NOT installed")
            return
        }
        onePush.hookAfter("onStartJob", findClass("android.app.job.JobParameters")) { param ->
            val keep = keepSet().filter { it.isNotBlank() }
            if (keep.isEmpty()) return@hookAfter
            val ctx = param.thisObject as? Context ?: return@hookAfter
            val userId = runCatching {
                Class.forName("android.os.UserHandle").getMethod("myUserId").invoke(null) as Int
            }.getOrDefault(0)
            thread {
                keep.forEach { pkg ->
                    runCatching {
                        val info = packageUtil.callS("getPackageInfoAsUser", pkg, 0, userId)
                            ?: return@runCatching
                        val current = permissionUtil.callS(
                            "getPermissionAction", ctx, info, permIdAutoStart
                        ) as? Number ?: return@runCatching
                        if (current.toInt() != ACTION_ALLOW) {
                            logI("keep-autostart: re-grant autostart for $pkg (current=${current.toInt()})")
                            permManager.callS("getInstance", ctx)
                                .call("setApplicationPermission", permIdAutoStart, ACTION_ALLOW, userId, pkg)
                        }
                    }.onFailure {
                        logE("keep-autostart: watchdog failed for $pkg", it)
                    }
                }
            }
        }
        logI("keep-autostart watchdog installed (query-first)")
    }

    companion object {
        /** Module-only provider method names; PermissionManagerProvider.call parses
         *  every stock method as an integer (both 1.9.2 and 4.0.0), so these strings
         *  never collide with it. */
        const val METHOD_GET_KEEP_AUTOSTART = "myinjector_get_keep_autostart"
        const val METHOD_SET_KEEP_AUTOSTART = "myinjector_set_keep_autostart"

        /** Same storage as the official 1.9.x whitelist: the service-private
         *  SharedPreferences "permission_config_sharePref" (StringSet key
         *  AUTO_START_WHITE_LIST), opened via the process app context on 4.x. */
        private const val SP_NAME = "permission_config_sharePref"

        private const val KEY_KEEP_AUTO_START = "AUTO_START_WHITE_LIST"

        private const val LEGACY_METHOD_IS_KEEP = "isAutoStartWhiteList"
        private const val LEGACY_METHOD_ADD_KEEP = "addAutoStartWhiteList"
        private const val LEGACY_METHOD_REMOVE_KEEP = "removeAutoStartWhiteList"

        /** Framework actions observed in the 4.0.0 service: 3 = allow, 1 = deny. */
        private const val ACTION_ALLOW = 3
        private const val ACTION_DENY = 1
    }
}
