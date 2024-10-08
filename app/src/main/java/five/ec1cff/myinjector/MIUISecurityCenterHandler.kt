package five.ec1cff.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Toast
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.invokeMethodAuto
import com.github.kyuubiran.ezxhelper.utils.loadClass
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.concurrent.thread

class MIUISecurityCenterHandler : IXposedHookLoadPackage {

    companion object {
        val URI = Uri.parse("content://com.lbe.security.miui.permmgr/active")
        const val PERM_AUTO_START = 16384
    }

    private fun Context.addModuleAssets() {
        resources.assets.invokeMethodAuto("addAssetPath", Entry.modulePath)
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
                return c.getInt(0).also { Log.d("get $packageName flags $it") }
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
            EzXHelperInit.initHandleLoadPackage(lpparam)
            findMethod("com.miui.permcenter.privacymanager.SpecialPermissionInterceptActivity", findSuper = true) {
                name == "onCreate"
            }.hookBefore {
                val inst = it.thisObject as Activity
                val permName = inst.intent.getStringExtra("permName")
                if (permName == "miui_open_debug") {
                    Settings.Global.putInt(inst.contentResolver, Settings.Global.ADB_ENABLED, 1)
                }
                inst.setResult(-1)
                inst.finish()
            }

            val classAppDetailsActivity =
                loadClass("com.miui.appmanager.ApplicationsDetailsActivity")
            val classAppDetailCheckBoxView =
                loadClass("com.miui.appmanager.widget.AppDetailCheckBoxView")
            classAppDetailsActivity.findMethod { name == "initView" }.hookAfter {
                val ctx = it.thisObject as Activity
                ctx.addModuleAssets()
                val pkgName = ctx.packageName
                val idAmDetailAs = ctx.resources.getIdentifier("am_detail_as", "id", pkgName)
                val drawableAmCardBgSelector =
                    ctx.resources.getIdentifier("am_card_bg_selector", "drawable", pkgName)
                val dimenAmDetailsItemHeight =
                    ctx.resources.getIdentifier("am_details_item_height", "dimen", pkgName)
                val dimenAmMainPageMarginSe =
                    ctx.resources.getIdentifier("am_main_page_margin_se", "dimen", pkgName)

                Log.d("id=$idAmDetailAs")
                val viewAmDetailAs = ctx.findViewById<View>(idAmDetailAs)
                val container = viewAmDetailAs.parent as? LinearLayout ?: return@hookAfter
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
                    invokeMethodAuto("setTitle", R.string.asex_title)
                    invokeMethodAuto("setSummary", R.string.asex_summary)
                    invokeMethodAuto(
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
                                invokeMethodAuto(
                                    "setSlideButtonChecked",
                                    checked
                                )
                            }
                        }.onFailure {
                            Log.e("failed to get autostart stable permission", it)
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
                            Log.e("failed to set autostart stable permission", it)
                            ctx.runOnUiThread {
                                Toast.makeText(
                                    ctx,
                                    "修改保持自启动失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    invokeMethodAuto(
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
        }
    }
}