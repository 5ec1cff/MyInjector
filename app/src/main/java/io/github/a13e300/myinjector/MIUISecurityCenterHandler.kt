package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
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
        private const val PERM_AUTO_START = 16384
    }

    private fun getPermissionFlags(contentResolver: ContentResolver, packageName: String): Int {
        contentResolver.query(
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
        return 0
    }

    private fun setAutoStartStable(
        contentResolver: ContentResolver,
        packageName: String,
        stable: Boolean
    ) {
        val oldPermissionFlags = getPermissionFlags(contentResolver, packageName)
        val newFlags = if (stable)
            oldPermissionFlags.or(PERM_AUTO_START)
        else
            oldPermissionFlags.and(PERM_AUTO_START.inv())
        contentResolver.update(URI, ContentValues().apply {
            put("suggestAccept", newFlags)
        }, "pkgName=?", arrayOf(packageName))
    }

    @SuppressLint("DiscouragedApi")
    override fun onHook() {
        if (loadPackageParam.packageName == "com.miui.securitycenter") {
            hookSkipWarning()
            hookAddKeepAutoStart()
        }
    }

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
        val classAppDetailsActivity =
            findClass(
                "com.miui.appmanager.ApplicationsDetailsActivity"
            )
        val initView = classAppDetailsActivity.declaredMethods.find { it.name == "initView" }
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
                val ctx = param.thisObject.call("getActivity") as Activity
                val currentPkgName = ctx.intent.getStringExtra("package_name")!!
                val ps = param.thisObject.call("getPreferenceScreen")
                val cat = ps.call("findPreference", "app_detail_perm_category")
                val pref = miuixCheckBoxPref.newInst(ctx)
                pref.call("setKey", "keep_autostart")
                pref.call("setTitle", "保持自启动")
                cat.call("addPreference", pref)
                fun updateStatus() {
                    kotlin.runCatching {
                        val checked =
                            getPermissionFlags(ctx.contentResolver, currentPkgName).and(
                                PERM_AUTO_START
                            ) != 0
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
                        setAutoStartStable(ctx.contentResolver, currentPkgName, enabled)
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
            val currentPkgName = ctx.intent.getStringExtra("package_name")!!
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
                            getPermissionFlags(ctx.contentResolver, currentPkgName).and(
                                PERM_AUTO_START
                            ) != 0
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
                        setAutoStartStable(ctx.contentResolver, currentPkgName, enabled)
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
