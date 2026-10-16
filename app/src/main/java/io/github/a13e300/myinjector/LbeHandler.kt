package io.github.a13e300.myinjector

import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.hookBefore

/**
 * Runs inside the MIUI permission manager service (com.lbe.security.miui).
 *
 * HyperOS 3 (permission manager 1.9.2) removed the legacy permmgr "active" table
 * (suggestAccept) that the "保持自启动" switch in the security center used to read
 * and write. The auto-start revoke scheduler now only skips packages listed in
 * PermissionConfigSharePref's AUTO_START_WHITE_LIST, which is private storage of
 * this app and cannot be written from any other process. So the existing permmgr
 * provider is extended with two module-only call() methods that toggle the native
 * whitelist in-process (see MIUISecurityCenterHandler for the callers). No
 * revocation logic is hooked for this flag: once it is stored, the stock scheduler
 * honours it even while this module is disabled.
 */
class LbeHandler : IHook() {
    override fun onHook() {
        if (loadPackageParam.packageName != "com.lbe.security.miui") return
        findClass("com.miui.privacy.autostart.AutoRevokePermissionManager")
            .hookAllNop("lambda\$startScheduleASCheck\$1")
        // HyperOS 3+ flag storage; older versions keep using the legacy table and the
        // switch falls back automatically when these methods answer nothing.
        val flagManager = findClassOrNull("com.lbe.security.utility.PermissionConfigSharePref")
        if (flagManager == null) {
            logI("keep-autostart: PermissionConfigSharePref missing, provider extension NOT installed")
            return
        }
        val provider = findClassOrNull("com.lbe.security.service.provider.PermissionManagerProvider")
        if (provider == null) {
            logI("keep-autostart: PermissionManagerProvider missing, provider extension NOT installed")
            return
        }
        provider.hookBefore("call", String::class.java, String::class.java, Bundle::class.java) { param ->
            when (param.args[0] as? String) {
                METHOD_GET_KEEP_AUTOSTART -> {
                    val pkg = param.args[1] as? String
                    if (!pkg.isNullOrBlank()) {
                        param.result = Bundle().apply {
                            runCatching {
                                val keep = flagManager.callS("isAutoStartWhiteList", pkg) as Boolean
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
                                flagManager.callS(
                                    if (enable) "addAutoStartWhiteList" else "removeAutoStartWhiteList",
                                    pkg
                                )
                                logI("keep-autostart SET $pkg enable=$enable ok")
                                putBoolean("ok", true)
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
    }

    companion object {
        /** Module-only provider method names; PermissionManagerProvider.call parses
         *  every stock method as an integer, so these strings never collide with it. */
        const val METHOD_GET_KEEP_AUTOSTART = "myinjector_get_keep_autostart"
        const val METHOD_SET_KEEP_AUTOSTART = "myinjector_set_keep_autostart"
    }
}
