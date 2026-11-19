package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Toast
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.addModuleAssets
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookReplace
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.newInstAs
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.concurrent.thread

class MIUISecurityCenterHandler : IHook() {
    companion object {
        private val URI = Uri.parse("content://com.lbe.security.miui.permmgr/active")
        private val PERM_MGR_FALLBACK_URIS = listOf(
            Uri.parse("content://com.lbe.security.miui.permmgr"),
            Uri.parse("content://com.hyperos.security.miui.permmgr")
        )
        private const val PERM_AUTO_START = 16384
    }

    // Android 17 device builds renamed the permmgr provider authority: the 4.0.0 APK
    // still ships com.lbe.security.miui.permmgr, but newer builds answer
    // "Unknown authority" for it. Resolve the real authority from the service's
    // provider list (matching the PermissionManagerProvider class name) instead of
    // hardcoding it; the known entries stay as fallbacks and are tried in order.
    private var permmgrUris: List<Uri>? = null

    private fun resolvePermmgrUris(ctx: Context): List<Uri> {
        permmgrUris?.let { return it }
        val resolved = runCatching {
            ctx.packageManager
                .getPackageInfo("com.lbe.security.miui", PackageManager.GET_PROVIDERS)
                .providers
                .orEmpty()
                .filter { it.name?.endsWith("PermissionManagerProvider") == true }
                .flatMap { it.authority.orEmpty().split(';') }
                .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                .map { Uri.parse("content://$it") }
                .distinct()
        }.onFailure {
            logW("keep-autostart: failed to resolve permmgr provider authority", it)
        }.getOrDefault(emptyList())
        val uris = resolved + PERM_MGR_FALLBACK_URIS.filter { it !in resolved }
        permmgrUris = uris
        logI("keep-autostart: permmgr uris = ${uris.joinToString()}")
        return uris
    }

    private fun callPermmgr(ctx: Context, method: String, arg: String?, extras: Bundle?): Bundle? {
        for (uri in resolvePermmgrUris(ctx)) {
            try {
                return ctx.contentResolver.call(uri, method, arg, extras)
            } catch (_: IllegalArgumentException) {
                // unknown authority - try the next candidate
                logI("keep-autostart: authority ${uri.authority} unknown for $method, trying next")
            }
        }
        return null
    }

    private fun getPermissionFlags(ctx: Context, packageName: String): Int {
        runCatching {
            ctx.contentResolver.query(
                URI,
                arrayOf("suggestAccept"),
                "pkgName=?",
                arrayOf(packageName),
                null
            )?.use { c ->
                if (c.moveToNext()) {
                    return c.getInt(0)
                }
            }
        }.onFailure {
            logW("keep-autostart: legacy active table query failed (expected on new versions)", it)
        }
        return 0
    }

    // HyperOS 3 (permission manager 1.9.2) removed the legacy "active" table below; the
    // keep-autostart flag moved into the service's private AUTO_START_WHITE_LIST storage.
    // LbeHandler exposes it through two module-only permmgr provider call() methods.
    // Older versions answer null here and fall back to the old table.
    private fun queryKeepAutostart(
        ctx: Context,
        packageName: String
    ): Boolean? {
        val result = runCatching {
            callPermmgr(ctx, LbeHandler.METHOD_GET_KEEP_AUTOSTART, packageName, null)
        }.onFailure {
            logW("keep-autostart extension GET $packageName failed", it)
        }.getOrNull()
        if (result == null) {
            logI("keep-autostart extension answered nothing for $packageName (old version or scope missing)")
            return null
        }
        val keep = result.getBoolean("keep")
        logI("keep-autostart extension GET $packageName -> $keep")
        return keep
    }

    private fun setKeepAutostart(
        ctx: Context,
        packageName: String,
        stable: Boolean
    ): Boolean? {
        val result = runCatching {
            callPermmgr(
                ctx,
                LbeHandler.METHOD_SET_KEEP_AUTOSTART,
                null,
                Bundle().apply {
                    putString("pkg", packageName)
                    putBoolean("enable", stable)
                }
            )
        }.onFailure {
            logW("keep-autostart extension SET $packageName failed", it)
        }.getOrNull()
        if (result == null) {
            logI("keep-autostart extension answered nothing for $packageName (old version or scope missing)")
            return null
        }
        if (!result.getBoolean("ok")) {
            logW("keep-autostart extension SET $packageName rejected")
            error("permission manager failed to update the whitelist")
        }
        logI("keep-autostart extension SET $packageName ok")
        return true
    }

    private fun isAutoStartStable(ctx: Context, packageName: String): Boolean {
        queryKeepAutostart(ctx, packageName)?.let { return it }
        return getPermissionFlags(ctx, packageName).and(PERM_AUTO_START) != 0
    }

    private fun setAutoStartStable(
        ctx: Context,
        packageName: String,
        stable: Boolean
    ) {
        if (setKeepAutostart(ctx, packageName, stable) != null) return
        // Legacy path (pre-HyperOS 3): the keep bit is a column of the permmgr "active" table.
        val oldPermissionFlags = getPermissionFlags(ctx, packageName)
        val newFlags = if (stable)
            oldPermissionFlags.or(PERM_AUTO_START)
        else
            oldPermissionFlags.and(PERM_AUTO_START.inv())
        val updated = ctx.contentResolver.update(URI, ContentValues().apply {
            put("suggestAccept", newFlags)
        }, "pkgName=?", arrayOf(packageName))
        if (updated <= 0) error("permission manager rejected the legacy update")
    }

    @SuppressLint("DiscouragedApi")
    override fun onHook() {
        if (loadPackageParam.packageName == "com.miui.securitycenter") {
            hookSkipWarning()
            hookAddKeepAutoStart()
        }
    }

    // NOTE: an earlier Android 17 build also hooked the framework PermissionManager
    // inside the security center process (to catch the osbot direct-call path). That
    // hook was removed: it ran on every permission toggle the UI makes, and the perm
    // service watchdog in LbeHandler already re-grants anything that slips past the
    // service-side veto, so the extra hook surface is not worth the risk.

    private fun hookSkipWarning() = runCatching {
        val activityClass = findClass(
            "com.miui.permcenter.privacymanager.SpecialPermissionInterceptActivity"
        )
        var method: Method? = null
        var clz: Class<*> = activityClass
        while (method == null) {
            try {
                method = clz.getDeclaredMethod("onCreate", Bundle::class.java)
            } catch (_: NoSuchMethodException) {
                clz = clz.superclass!!
            }
        }
        method.hookBefore { param ->
            val inst = param.thisObject as Activity
            val permName = inst.intent.getStringExtra("permName")
            // special case of adb
            if (permName == "miui_open_debug") {
                Settings.Global.putInt(inst.contentResolver, Settings.Global.ADB_ENABLED, 1)
            }
            inst.setResult(-1)
            inst.finish()
        }
        val adbInputActivity = findClass(
            "com.miui.permcenter.install.AdbInputApplyActivity"
        )
        adbInputActivity.hookReplace("onCreate", Bundle::class.java) { param ->
            SystemProperties.set("persist.security.adbinput", "1")
            (param.thisObject as Activity).run {
                setResult(-1, null)
                finish()
            }
            return@hookReplace null
        }
    }.onFailure { logE("hookSkipAdbWarning: ", it) }

    @SuppressLint("DiscouragedApi")
    private fun hookAddKeepAutoStart() = runCatching {
        // HyperOS 3 (安全服务 12.8.1): the activity only hosts the fragment and has no
        // initView; tolerate the class disappearing so the fragment path keeps working.
        val classAppDetailsActivity =
            findClassOrNull(
                "com.miui.appmanager.ApplicationsDetailsActivity"
            )
        val initView = classAppDetailsActivity?.declaredMethods?.find { it.name == "initView" }
        if (initView == null) {
            logI("hook new!!!")
            val classAppDetailsFragment =
                findClass("com.miui.appmanager.fragment.ApplicationsDetailsFragment")
            val miuixCheckBoxPref = findClass("miuix.preference.CheckBoxPreference")
            val prefChangeListenerClass = findClass("androidx.preference.Preference")
                .declaredMethods.find { it.name == "setOnPreferenceChangeListener" }!!
                .parameters[0].type

            classAppDetailsFragment.hookAllAfter("onCreatePreferences") { param ->
                logI("onCreatePreferences")
                // 12.8.1 finishes the activity inside onCreatePreferences when the
                // package extra is unusable (F3() failed), yet this after-callback
                // still runs on the dying page: skip injection instead of tripping on !!.
                val ctx = param.thisObject.call("getActivity") as? Activity ?: return@hookAllAfter
                val currentPkgName = ctx.intent.getStringExtra("package_name")
                    ?: return@hookAllAfter
                val ps = param.thisObject.call("getPreferenceScreen") ?: return@hookAllAfter
                val cat = ps.call("findPreference", "app_detail_perm_category")
                    ?: return@hookAllAfter
                val pref = miuixCheckBoxPref.newInst(ctx)
                pref.call("setKey", "keep_autostart")
                pref.call("setTitle", "保持自启动")
                cat.call("addPreference", pref)
                logI("keep-autostart switch injected for $currentPkgName")
                fun updateStatus() {
                    kotlin.runCatching {
                        val checked =
                            isAutoStartStable(ctx, currentPkgName)
                        ctx.runOnUiThread {
                            pref.call(
                                "setChecked",
                                checked
                            )
                        }
                    }.onFailure {
                        logE("failed to get autostart stable permission", it)
                        ctx.runOnUiThread {
                            Toast.makeText(
                                ctx,
                                "获取保持自启动失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                fun modifyStatus(enabled: Boolean) {
                    kotlin.runCatching {
                        setAutoStartStable(ctx, currentPkgName, enabled)
                    }.onSuccess {
                        updateStatus()
                    }.onFailure {
                        logE("failed to set autostart stable permission", it)
                        ctx.runOnUiThread {
                            Toast.makeText(
                                ctx,
                                "修改保持自启动失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                val listener = Proxy.newProxyInstance(
                    prefChangeListenerClass.classLoader,
                    arrayOf(prefChangeListenerClass)
                ) { _, m, args ->
                    if (m.name == "onPreferenceChange") {
                        val v = args[1] as Boolean
                        thread {
                            modifyStatus(v)
                        }
                        return@newProxyInstance true
                    }
                    return@newProxyInstance null
                }
                pref.call("setOnPreferenceChangeListener", listener)

                thread {
                    updateStatus()
                }
            }

            return@runCatching
        }
        val classAppDetailCheckBoxView =
            findClass(
                "com.miui.appmanager.widget.AppDetailCheckBoxView"
            )
        initView.hookAfter { param ->
            val ctx = param.thisObject as Activity
            ctx.addModuleAssets(Entry.modulePath)
            val pkgName = ctx.packageName
            val idAmDetailAs = ctx.resources.getIdentifier("am_detail_as", "id", pkgName)
            val drawableAmCardBgSelector =
                ctx.resources.getIdentifier("am_card_bg_selector", "drawable", pkgName)
            val dimenAmDetailsItemHeight =
                ctx.resources.getIdentifier("am_details_item_height", "dimen", pkgName)
            val dimenAmMainPageMarginSe =
                ctx.resources.getIdentifier("am_main_page_margin_se", "dimen", pkgName)

            val viewAmDetailAs = ctx.findViewById<View>(idAmDetailAs)
            val container = viewAmDetailAs.parent as? LinearLayout ?: return@hookAfter
            val idx = container.indexOfChild(viewAmDetailAs)
            val currentPkgName = ctx.intent.getStringExtra("package_name") ?: return@hookAfter
            classAppDetailCheckBoxView.newInstAs<LinearLayout>(ctx, null).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(drawableAmCardBgSelector)
                isClickable = true
                minimumHeight = ctx.resources.getDimensionPixelSize(
                    dimenAmDetailsItemHeight
                )
                val dimensionPixelSize =
                    ctx.resources.getDimensionPixelSize(dimenAmMainPageMarginSe)
                setPadding(dimensionPixelSize, 0, dimensionPixelSize, 0)
                call("setTitle", R.string.asex_title)
                call("setSummary", R.string.asex_summary)
                call("setSlideButtonChecked", false) // TODO
                fun updateStatus() {
                    kotlin.runCatching {
                        val checked =
                            isAutoStartStable(ctx, currentPkgName)
                        ctx.runOnUiThread {
                            call(
                                "setSlideButtonChecked",
                                checked
                            )
                        }
                    }.onFailure {
                        logE("failed to get autostart stable permission", it)
                        ctx.runOnUiThread {
                            Toast.makeText(
                                ctx,
                                "获取保持自启动失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                fun modifyStatus(enabled: Boolean) {
                    kotlin.runCatching {
                        setAutoStartStable(ctx, currentPkgName, enabled)
                    }.onSuccess {
                        updateStatus()
                    }.onFailure {
                        logE("failed to set autostart stable permission", it)
                        ctx.runOnUiThread {
                            Toast.makeText(
                                ctx,
                                "修改保持自启动失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                this.call(
                    "setSlideButtonOnCheckedListener",
                    CompoundButton.OnCheckedChangeListener { _, isChecked ->
                        thread {
                            modifyStatus(isChecked)
                        }
                    }
                )
                thread {
                    updateStatus()
                }
                container.addView(this, idx + 1)
            }
        }
    }.onFailure { logE("hookAddKeepAutoStart: ", it) }
}
