package io.github.a13e300.myinjector.xhs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.ui.settings.*
import io.github.a13e300.myinjector.addSettingsIntentInterceptor
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.DynHookManager
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.modernInjectedMessageView
import io.github.a13e300.myinjector.ui.modernInjectedScrollContent
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

fun JSONObject.boolOrDefault(k: String, def: Boolean): Boolean =
    runCatching {
        if (has(k))
            getBoolean(k)
        else
            def
    }.getOrDefault(def)

data class XhsHookConfig(
    val saveOriginalImage: Boolean = true,
    val forceSaveImage: Boolean = true,
    val openStickerAsImage: Boolean = true,
    val betterShare: Boolean = true,
    val extractVideoLink: Boolean = true,
) {
    companion object {
        fun readFromJson(obj: JSONObject): XhsHookConfig {
            return XhsHookConfig(
                saveOriginalImage = obj.boolOrDefault("saveOriginalImage", true),
                forceSaveImage = obj.boolOrDefault("forceSaveImage", true),
                openStickerAsImage = obj.boolOrDefault("openStickerAsImage", true),
                betterShare = obj.boolOrDefault("betterShare", true),
                extractVideoLink = obj.boolOrDefault("extractVideoLink", true),
            )
        }
    }

    fun writeToJson(obj: JSONObject) {
        obj.put("saveOriginalImage", saveOriginalImage)
        obj.put("forceSaveImage", forceSaveImage)
        obj.put("openStickerAsImage", openStickerAsImage)
        obj.put("betterShare", betterShare)
        obj.put("extractVideoLink", extractVideoLink)
    }
}

abstract class MyDynHook(private val name: String) : DynHook() {
    override fun onHookError(t: Throwable) {
        logE("hook failed: $name", t)
        XhsHandler.hookErrors[name] = Log.getStackTraceString(t)
    }
}

class XhsSettingsDialog(ctx: Context) : SettingDialog(ctx) {
    override val dialogTitle = "小红书设置"
    override fun onSettingChanged(
        setting: SettingItem,
        newValue: Any?
    ): Boolean {
        val settings = XhsHandler.settings
        val newSettings = when (setting.key) {
            "saveOriginalImage" -> {
                settings.copy(saveOriginalImage = newValue as Boolean)
            }

            "forceSaveImage" -> {
                settings.copy(forceSaveImage = newValue as Boolean)
            }

            "openStickerAsImage" -> {
                settings.copy(openStickerAsImage = newValue as Boolean)
            }

            "betterShare" -> {
                settings.copy(betterShare = newValue as Boolean)
            }

            "extractVideoLink" -> {
                settings.copy(extractVideoLink = newValue as Boolean)
            }

            else -> return false
        }
        XhsHandler.updateSettings(newSettings)
        return true
    }

    override fun onSettingClicked(setting: SettingItem): Boolean {
        if (setting.key == "checkHotPatch") {

            val activity = activityCtx.findBaseActivity()

            thread {
                val res = XhsHandler.checkHotPatch()
                activity.runOnUiThread {
                    showModernInjectedDialog(
                        context,
                        "热补丁检查",
                        modernInjectedScrollContent(
                            context,
                            modernInjectedMessageView(context, res),
                            0.45f,
                        ),
                        listOf(
                            ModernInjectedDialogAction("分享") {
                                thread {
                                    runCatching {
                                        val f =
                                            File(context.filesDir, "myinjector-checkhotpatch.txt")
                                        f.writeText(res.toString())
                                        logD("wrote file $f")
                                        val uri = XhsHandler.fileProviderClass.callS(
                                            "getUriForFile",
                                            context,
                                            "com.xingin.xhs.provider",
                                            f
                                        ) as Uri
                                        logD("uri $uri")
                                        context.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND)
                                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                                    .setType("text/plain"),
                                                ""
                                            )
                                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        )
                                    }.onFailure { t ->
                                        logE("share failed: ", t)
                                        runCatching {
                                            activityCtx.findBaseActivity().runOnUiThread {
                                                Toast.makeText(
                                                    context,
                                                    "分享失败，请尝试复制",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                            ModernInjectedDialogAction("复制") {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("", res))
                            },
                            ModernInjectedDialogAction("关闭"),
                        ),
                    )
                }
            }
            return true
        } else if (setting.key == "clearHotPatch") {
            runCatching {
                XhsHandler.clearHotPatch()
                Toast.makeText(activityCtx, "清理成功", Toast.LENGTH_SHORT).show()
            }.onFailure {
                logE("clear hot patch", it)
                Toast.makeText(activityCtx, "清理失败： ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun loadSetting(setting: SettingItem) {
        when (setting.key) {
            "saveOriginalImage" -> {
                (setting as ToggleSetting).isChecked =
                    XhsHandler.settings.saveOriginalImage
            }

            "forceSaveImage" -> {
                (setting as ToggleSetting).isChecked =
                    XhsHandler.settings.forceSaveImage
            }

            "openStickerAsImage" -> {
                (setting as ToggleSetting).isChecked =
                    XhsHandler.settings.openStickerAsImage
            }

            "betterShare" -> {
                (setting as ToggleSetting).isChecked =
                    XhsHandler.settings.betterShare
            }

            "extractVideoLink" -> {
                (setting as ToggleSetting).isChecked =
                    XhsHandler.settings.extractVideoLink
            }
        }

        if (setting.key in XhsHandler.hookErrors) {
            val newSummary = SpannableStringBuilder(setting.summary ?: "")
                .apply { if (isNotEmpty()) append("\n") }
                .append(
                    "存在问题",
                    ForegroundColorSpan(Color.RED),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            setting.summary = newSummary
        }
    }

    override fun createSettings(settingGroup: SettingsGroup) {
        settingGroup.run {
            toggle("保存原图", "saveOriginalImage")
            toggle("移除保存图片限制", "forceSaveImage")
            toggle("用打开图片方式打开表情", "openStickerAsImage")
            toggle("增强分享", "betterShare")
            toggle(
                "提取视频链接",
                "extractVideoLink",
                summary = "在视频页面显示悬浮按钮，点击可提取视频链接"
            )
            action(
                "检查热补丁",
                "checkHotPatch",
                summary = "检查应用内置热补丁是否与 hook 冲突"
            )
            action(
                "清理热补丁",
                "clearHotPatch",
                summary = "临时清理热补丁，重启恢复"
            )
        }
    }

    override fun onConfigureActions(actions: MutableList<ModernInjectedDialogAction>) {
        if (XhsHandler.hookErrors.isEmpty()) return
        val errors = XhsHandler.hookErrors.map {
            "${it.key}:\n${it.value}"
        }.joinToString("\n")
        actions.add(
            0,
            ModernInjectedDialogAction("查看错误", dismissAfterClick = false) {
                showModernInjectedDialog(
                    context,
                    "错误详情",
                    modernInjectedScrollContent(
                        context,
                        modernInjectedMessageView(context, errors),
                        0.45f,
                    ),
                    listOf(
                        ModernInjectedDialogAction("分享") {
                            thread {
                                runCatching {
                                    val f = File(context.filesDir, "myinjector-error.txt")
                                    f.writeText(errors)
                                    logD("wrote file $f")
                                    val uri = XhsHandler.fileProviderClass.callS(
                                        "getUriForFile",
                                        context,
                                        "com.xingin.xhs.provider",
                                        f
                                    ) as Uri
                                    logD("uri $uri")
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .putExtra(Intent.EXTRA_STREAM, uri)
                                                .setType("text/plain"),
                                            ""
                                        )
                                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }.onFailure { t ->
                                    logE("share failed: ", t)
                                    runCatching {
                                        activityCtx.findBaseActivity().runOnUiThread {
                                            Toast.makeText(
                                                context,
                                                "分享失败，请尝试复制",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        },
                        ModernInjectedDialogAction("复制") {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("", errors))
                        },
                        ModernInjectedDialogAction("关闭"),
                    ),
                )
            },
        )
    }


}

class SettingsHook : IHook() {
    override fun onHook() {
        addSettingsIntentInterceptor { activity ->
            XhsSettingsDialog(activity).show()
        }

        val creator = XhsHandler.creator
        val buttonClassInfo = creator.create("settingshook.buttonClass") { bridge ->
            bridge.findClass {
                matcher {
                    usingEqStrings("Gap/Decrease/XL", "IconSize/XL")
                    superClass("android.widget.LinearLayout")
                }
            }.single().toObfsInfo()
        }
        val createMenuMethod = creator.create("settingshook.createMenuMethod") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass("com.xingin.xhs.homepage.sidebar.HomeNavigationView")
                    usingEqStrings("prepareData source:", "creator_center")
                }
            }.single().toObfsInfo()
        }

        val buttonClass = findClass(buttonClassInfo.className) // "elb.d"
        val dataClass = buttonClass.constructors.single().parameters[2].type
        val textClass = dataClass.superclass.declaredFields.single { it.name == "text" }.type
        val iconFieldInButton = buttonClass.declaredFields.single { it.type == dataClass }
            .also { it.isAccessible = true }

        findClass("com.xingin.xhs.homepage.sidebar.HomeNavigationView")
            .hookAllAfter(createMenuMethod.memberName) { param ->
                val bottom = param.thisObject.call("getNavigationBottomBar") as ViewGroup
                val ctx = bottom.context
                val icon =
                    iconFieldInButton.get(bottom.getChildAt(bottom.childCount - 1)).getObj("icon")
                val data = dataClass.newInst().apply {
                    setObj("icon", icon)
                    setObj("text", textClass.newInst().apply {
                        setObj("zh", "MyInjector")
                        setObj("zhTW", "MyInjector")
                        setObj("en", "MyInjector")
                    })
                }
                val btn = buttonClass.newInstAs<View>(ctx, "myinjector", data)
                btn.setOnClickListener {
                    XhsSettingsDialog(ctx).show()
                }
                (btn.layoutParams as? LinearLayout.LayoutParams?)?.apply {
                    width = 0
                    weight = 1f
                }
                bottom.addView(btn)
            }
    }
}

object XhsHandler : DynHookManager<XhsHookConfig>() {
    private var _creator: ObfsTableCreator? = null
    val creator: ObfsTableCreator
        get() = _creator ?: ObfsTableCreator("", 8, appInfo = loadPackageParam.appInfo)
            .also { _creator = it }
    val hookErrors = mutableMapOf<String, String>()
    val fileProviderClass by lazy { findClass("androidx.core.content.FileProvider") }


    override fun onHook() {
        super.onHook()
        subHook(SettingsHook())
        subHook(SaveOriginalImage())
        subHook(OpenStickerAsImage())
        subHook(ForceSaveImage())
        subHook(BetterShare())
        subHook(ExtractVideoLink())

        _creator?.let {
            it.persist()
            it.close()
            _creator = null
        }
    }

    private val changeLock = Any()

    override fun onChanged() {
        thread {
            synchronized(changeLock) {
                super.onChanged()
                _creator?.let {
                    it.persist()
                    it.close()
                    _creator = null
                }
            }
        }
    }

    override fun onReadSettings(input: InputStream): XhsHookConfig =
        XhsHookConfig.readFromJson(JSONObject(input.readBytes().decodeToString()))

    override fun defaultSettings(): XhsHookConfig = XhsHookConfig()

    override fun onWriteSettings(
        output: OutputStream,
        setting: XhsHookConfig
    ) {
        val obj = JSONObject()
        setting.writeToJson(obj)
        output.write(obj.toString().toByteArray())
    }

    @Suppress("Unchecked_Cast")
    fun checkHotPatch(): CharSequence {
        val res = SpannableStringBuilder()
        runCatching {
            val patchProxyClass = findClass("com.xingin.robust.PatchProxy")
            val quickRedirects =
                patchProxyClass.getObjS("changeQuickRedirects") as Map<Class<*>, Any>
            if (quickRedirects.isEmpty()) {
                res.append("无热补丁\n")
                return@runCatching
            }
            quickRedirects.forEach { (clz, c) ->
                runCatching {
                    val associated = creator.obfsTable.filter { it.value.className == clz.name }
                    val loc = getDexLocation(c.javaClass)
                    if (associated.isEmpty()) {
                        res.append("$clz: $c [$loc] (无直接影响)\n")
                    } else {
                        res.append(
                            "$clz: $c [$loc] (影响 ${associated.keys.joinToString(",")})\n",
                            ForegroundColorSpan(Color.RED), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }.onFailure {
                    logE("check $clz: ", it)
                    res.append("$clz: 错误： ${it.message}")
                }
            }
        }.onFailure {
            logE("check patch proxy", it)
            res.append("错误： ${it.message}")
        }
        return res
    }

    @Suppress("Unchecked_Cast")
    fun clearHotPatch() {
        val patchProxyClass = findClass("com.xingin.robust.PatchProxy")
        val quickRedirects =
            patchProxyClass.getObjS("changeQuickRedirects") as MutableMap<Class<*>, Any>
        quickRedirects.clear()
    }
}

fun getDexLocation(clz: Class<*>): String =
    clz.getObj("dexCache").getObjAs("location")
