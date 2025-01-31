package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method
import kotlin.concurrent.thread

class MIUISecurityCenterHandler : IXposedHookLoadPackage {
    private lateinit var lpparam: XC_LoadPackage.LoadPackageParam

    companion object {
        private val URI = Uri.parse("content://com.lbe.security.miui.permmgr/active")
        private const val PERM_AUTO_START = 16384
        private const val TAG = "MyInjector-MIUISec"
    }

    private fun Context.addModuleAssets() {
        XposedHelpers.callMethod(resources.assets, "addAssetPath", Entry.modulePath)
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
                return c.getInt(0).also { Log.d(TAG, "get $packageName flags $it") }
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
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.miui.securitycenter") {
            this.lpparam = lpparam
            hookSkipWarning()
            hookAddKeepAutoStart()
        }
    }

    private fun hookSkipWarning() = runCatching {
        val activityClass = XposedHelpers.findClass(
            "com.miui.permcenter.privacymanager.SpecialPermissionInterceptActivity",
            lpparam.classLoader
        )
        var method: Method? = null
        var clz = activityClass
        while (method == null) {
            try {
                method = clz.getDeclaredMethod("onCreate", Bundle::class.java)
            } catch (_: NoSuchMethodException) {
                clz = clz.superclass
            }
        }
        XposedBridge.hookMethod(method, object : de.robv.android.xposed.XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val inst = param.thisObject as Activity
                val permName = inst.intent.getStringExtra("permName")
                // special case of adb
                if (permName == "miui_open_debug") {
                    Settings.Global.putInt(inst.contentResolver, Settings.Global.ADB_ENABLED, 1)
                }
                inst.setResult(-1)
                inst.finish()
            }
        })
    }.onFailure { Log.e(TAG, "hookSkipAdbWarning: ", it) }

    private fun hookAddKeepAutoStart() = runCatching {
        val classAppDetailsActivity =
            XposedHelpers.findClass(
                "com.miui.appmanager.ApplicationsDetailsActivity",
                lpparam.classLoader
            )
        val classAppDetailCheckBoxView =
            XposedHelpers.findClass(
                "com.miui.appmanager.widget.AppDetailCheckBoxView",
                lpparam.classLoader
            )
        XposedBridge.hookAllMethods(
            classAppDetailsActivity,
            "initView",
            object : de.robv.android.xposed.XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val ctx = param.thisObject as Activity
                ctx.addModuleAssets()
                val pkgName = ctx.packageName
                val idAmDetailAs = ctx.resources.getIdentifier("am_detail_as", "id", pkgName)
                val drawableAmCardBgSelector =
                    ctx.resources.getIdentifier("am_card_bg_selector", "drawable", pkgName)
                val dimenAmDetailsItemHeight =
                    ctx.resources.getIdentifier("am_details_item_height", "dimen", pkgName)
                val dimenAmMainPageMarginSe =
                    ctx.resources.getIdentifier("am_main_page_margin_se", "dimen", pkgName)

                Log.d(TAG, "id=$idAmDetailAs")
                val viewAmDetailAs = ctx.findViewById<View>(idAmDetailAs)
                val container = viewAmDetailAs.parent as? LinearLayout ?: return
                val idx = container.indexOfChild(viewAmDetailAs)
                val currentPkgName = ctx.intent.getStringExtra("package_name")!!
                (XposedHelpers.newInstance(
                    classAppDetailCheckBoxView,
                    ctx,
                    null
                ) as LinearLayout).apply {
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
                    XposedHelpers.callMethod(this, "setTitle", R.string.asex_title)
                    XposedHelpers.callMethod(this, "setSummary", R.string.asex_summary)
                    XposedHelpers.callMethod(
                        this,
                        "setSlideButtonChecked",
                        false // TODO
                    )
                    fun updateStatus() {
                        kotlin.runCatching {
                            val checked =
                                getPermissionFlags(ctx.contentResolver, currentPkgName).and(
                                    PERM_AUTO_START
                                ) != 0
                            ctx.runOnUiThread {
                                XposedHelpers.callMethod(
                                    this,
                                    "setSlideButtonChecked",
                                    checked
                                )
                            }
                        }.onFailure {
                            Log.e(TAG, "failed to get autostart stable permission", it)
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
                            Log.e(TAG, "failed to set autostart stable permission", it)
                            ctx.runOnUiThread {
                                Toast.makeText(
                                    ctx,
                                    "修改保持自启动失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    XposedHelpers.callMethod(
                        this,
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
        })
    }.onFailure { Log.e(TAG, "hookAddKeepAutoStart: ", it) }
}
