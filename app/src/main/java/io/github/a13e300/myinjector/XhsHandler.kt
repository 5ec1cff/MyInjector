package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.preference.Preference
import android.preference.PreferenceScreen
import android.preference.SwitchPreference
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.DynHookManager
import io.github.a13e300.myinjector.arch.FindObjectConfiguration
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.dp2px
import io.github.a13e300.myinjector.arch.extraField
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.arch.findPathToObject
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllConstantIf
import io.github.a13e300.myinjector.arch.hookCAfter
import io.github.a13e300.myinjector.arch.hookReplace
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.switchPreference
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

fun toHtmlText(title: String?, desc: String?, url: String): String {
    val sb = StringBuilder()
    var hasPre = false
    if (title?.isNotEmpty() == true) {
        sb.append("<b>$title</b>")
        hasPre = true
    }
    val id = Regex("https://www.xiaohongshu.com/discovery/item/([0-9a-z]*)")
        .find(url)?.groupValues?.getOrNull(1)
    val text = if (id != null) "xhs/$id" else url.removePrefix("https://").split("?").first()
    if (hasPre) sb.append("<br>")
    sb.append("<a href=\"$url\">$text</a>")
    if (desc?.isNotEmpty() == true) {
        sb.append("<br>")
        sb.append(desc.split("\n").joinToString("<br>") { "<pre>$it</pre>" })
    }
    return sb.toString()
}

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

class SaveOriginalImageAbort(var f: File) : Throwable()

class SaveOriginalImage : DynHook() {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.saveOriginalImage

    override fun onHook() {
        val savePicMethodInfo = XhsHandler.creator.create("savePicMethod") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "decode bitmap is null",
                        "save image failed without watermark"
                    )
                }
            }.single().toObfsInfo()
        }
        val savePicClass = findClass(savePicMethodInfo.className)
        val savePicMethod =
            savePicClass.declaredMethods.single { it.name == savePicMethodInfo.memberName }
        val picCacheClass = savePicMethod.parameters[1].type
        val picCacheFileField =
            picCacheClass.declaredFields.single { f -> f.type == File::class.java }
                .also { it.isAccessible = true }

        val kotlinPair = findClass("kotlin.Pair")

        savePicMethod.hookReplace(
            cond = ::isEnabled
        ) { param ->
            val f = param.args[1].let { picCacheFileField.get(it) as File }
            logD("save original $f")
            param.args.last().apply {
                call("onNext", kotlinPair.newInst("success", f))
                call("onComplete")
            }
        }

        // 保存多图时可能会见到这个方法

        val savePicWithUrlInfo = XhsHandler.creator.create("savePicWithUrl") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass(savePicMethodInfo.className)
                    usingEqStrings(
                        "save image failed without watermark"
                    )
                }
            }.single { it.methodName != savePicMethodInfo.memberName }.toObfsInfo()
        }
        val picCacheInterface = picCacheClass.interfaces.single()
        val getCacheMethod = XhsHandler.creator.create("getImageCache") { bridge ->
            bridge.findMethod {
                matcher {
                    addCaller {
                        declaredClass(savePicWithUrlInfo.className)
                        name(savePicWithUrlInfo.memberName)
                    }
                    returnType(picCacheInterface.name)
                }
            }.single().toObfsInfo()
        }
        val tls = ThreadLocal<Boolean>()
        findClass(getCacheMethod.className).hookAllAfter(
            getCacheMethod.memberName,
            cond = ::isEnabled
        ) { param ->
            val res = param.result
            if (tls.get() == true && picCacheClass.isInstance(res)) {
                val f = res.let { picCacheFileField.get(it) as File }
                param.throwable = SaveOriginalImageAbort(f)
            }
        }
        savePicClass.hookAll(
            savePicWithUrlInfo.memberName, cond = ::isEnabled,
            before = {
                tls.set(true)
            },
            after = { param ->
                runCatching {
                    (param.throwable as? SaveOriginalImageAbort)?.let {
                        param.throwable = null
                        param.result = null
                        val f = it.f
                        logD("save original with url $f")
                        param.args.last().apply {
                            call("onNext", kotlinPair.newInst("success", f))
                            call("onComplete")
                        }
                    }
                }

                tls.set(false)
            }
        )
    }
}

class OpenStickerAsImage : DynHook() {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.openStickerAsImage

    override fun onHook() {
        runCatching {
            val openStickerAsImageMagicSwitch =
                XhsHandler.creator.create("OpenStickerAsImageMagicSwitch") { bridge ->
                    val openImageMethod = bridge.findMethod {
                        matcher {
                            usingEqStrings("CommentConsumerList", "onCommentImageClick commentId: ")
                        }
                    }.single()

                    val magic = bridge.findMethod {
                        matcher {
                            addCaller(openImageMethod.descriptor)
                            paramCount(0)
                            returnType(java.lang.Boolean.TYPE)

                            declaredClass {
                                fieldCount(1)
                            }
                        }
                    }.single()

                    magic.toObfsInfo()
                }
            findClass(openStickerAsImageMagicSwitch.className)
                .hookAllConstantIf(
                    openStickerAsImageMagicSwitch.memberName,
                    false,
                    cond = ::isEnabled
                )
        }.onFailure {
            logE("hookOpenStickerAsImage", it)
        }
    }
}

class ForceSaveImage : DynHook() {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.forceSaveImage

    override fun onHook() {
        runCatching {
            // 单张图片保存
            val method = XhsHandler.creator.create("disableSave") { bridge ->
                val clazz = bridge.findClass {
                    matcher {
                        fields {
                            add {
                                name("disable")
                            }
                            add {
                                name("disableToast")
                            }
                            add {
                                name("checkLogin")
                            }
                        }
                    }
                }.single()
                val method = clazz.findMethod {
                    matcher {
                        usingFields {
                            add {
                                name("disable")
                            }
                        }
                        returnType(java.lang.Boolean::class.java)
                    }
                }.single()
                method.toObfsInfo()
            }
            findClass(method.className).hookAllConstantIf(
                method.memberName,
                false,
                cond = ::isEnabled
            )
            // 笔记分享的保存所有图片
            // ShareInfoDetail$Operate
            val method2 = XhsHandler.creator.create("disableSaveAll") { bridge ->
                val clazz = bridge.findClass {
                    matcher {
                        fields {
                            add {
                                name("disable")
                            }
                            add {
                                name("disableToast")
                            }
                            add {
                                name("labelImageUrl")
                            }
                        }
                    }
                }.single()
                val method = clazz.findMethod {
                    matcher {
                        usingFields {
                            add {
                                name("disable")
                            }
                        }
                        returnType(java.lang.Boolean.TYPE)
                    }
                }.single()
                method.toObfsInfo()
            }
            findClass(method2.className).hookAllConstantIf(
                method2.memberName,
                false,
                cond = ::isEnabled
            )
        }.onFailure {
            logE("hookDisableSave", it)
        }
    }
}

class BetterShare : DynHook() {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.betterShare

    override fun onHook() {
        runCatching {
            val shareMethod = XhsHandler.creator.create("shareMethod") { bridge ->
                val shareMethod = bridge.findMethod {
                    matcher {
                        usingEqStrings("TYPE_LINKED", "xhsdiscover://tagged_me")
                    }
                }.single()

                shareMethod.toObfsInfo()
            }

            val shareClass = findClass(shareMethod.className)

            val shareEntityField = shareClass.declaredFields.single {
                it.type.name.endsWith("ShareEntity")
            }.also { it.isAccessible = true }

            val noteItemBeanField = shareClass.declaredFields.single {
                it.type.name.endsWith("NoteItemBean")
            }.also { it.isAccessible = true }

            var longPressed = false

            shareClass.hookAllBefore(shareMethod.memberName, cond = ::isEnabled) { param ->
                if (param.args[0] == "TYPE_LINKED") {
                    val shareEntity = shareEntityField.get(param.thisObject)
                    val noteItem = noteItemBeanField.get(param.thisObject)
                    val longClicked = longPressed
                    longPressed = false
                    val pageUrl = shareEntity.getObjAs<String>("pageUrl")
                    var desc = noteItem.getObjAsN<String>("desc")
                    var title = noteItem.getObjAsN<String>("title")
                    if (title != null && desc?.startsWith(title) == true) title = null

                    if (!longClicked && desc != null) {
                        desc = desc.let {
                            if (it.length > 50) {
                                val last2 = it[47]
                                if (last2.code in 0xd800..<0xdc00) {
                                    it.substring(0, 47).trimEnd() + ".."
                                } else {
                                    it.substring(0, 48).trimEnd() + ".."
                                }
                            } else it.trimEnd()
                        }
                    }

                    val uri = Uri.parse(pageUrl)
                    val newUri = uri.buildUpon()
                        .also {
                            uri.getQueryParameters("xsec_token").firstOrNull()?.let { token ->
                                it.clearQuery()
                                it.appendQueryParameter("xsec_token", token)
                            }
                        }
                        .build()
                        .toString()
                    // logD("newUri=$newUri desc=$desc title=$title")
                    val html = toHtmlText(title, desc, newUri)
                    var text = title
                    if (text.isNullOrEmpty()) text = desc
                    else text += "\n" + desc
                    if (text.isNullOrEmpty()) text = newUri
                    else text += "\n" + newUri
                    // logD("html=$html")

                    AndroidAppHelper.currentApplication()
                        .getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newHtmlText("", text, html))

                    param.result = null
                }
            }

            val shareUserView = findClass("com.xingin.sharesdk.ui.view.ShareUserView")
            shareUserView.hookCAfter(
                Context::class.java,
                AttributeSet::class.java,
                Integer.TYPE, cond = ::isEnabled
            ) { param ->
                (param.thisObject as View).setOnLongClickListener {
                    longPressed = true
                    it.callOnClick()
                }
            }
        }.onFailure {
            logE("hookShare", it)
        }
    }
}

class ExtractVideoLink : DynHook() {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.extractVideoLink

    override fun onHook() {
        val noteFeedClass = findClass("com.xingin.entities.notedetail.NoteFeed")

        fun locateNoteFeed(context: Context): Any {
            val base = context.findBaseActivity()
            val presenter = base.getObj("linker")
                .getObj("controller")
                .getObj("presenter")!!
            val path = findPathToObject(
                presenter, FindObjectConfiguration(
                    targetClz = noteFeedClass, maxDepth = 4, maxTries = Int.MAX_VALUE, first = true
                )
            ).first()
            return path.obj
        }

        val detailFeedActivity = findClass("com.xingin.matrix.detail.activity.DetailFeedActivity")

        fun View.decorViewActivity(): Activity? {
            val view = this
            val contentView = (view.getObj("mContentRoot") as View)
            val activity = contentView.context.findBaseActivityOrNull() ?: return null
            if (activity.window !== view.getObj("mWindow")) return null
            return activity
        }

        @SuppressLint("ViewConstructor")
        class FloatingWindow(private val activity: Activity, private val context: Context) :
            FrameLayout(context) {
            val contentView: View
            val winWidth: Int = 40.dp2px(activity.resources).toInt()
            val winHeight: Int = winWidth
            val wm = activity.windowManager
            val lp = WindowManager.LayoutParams().apply {
                gravity = Gravity.LEFT or Gravity.TOP
                format = PixelFormat.RGBA_8888
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                x = context.resources.displayMetrics.widthPixels - winWidth
                y = context.resources.displayMetrics.heightPixels.times(0.9).toInt()
                alpha = 0.7f
                width = winWidth
                height = winHeight
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }

            init {
                val v = LayoutInflater.from(context).inflate(R.layout.floating_window, null, false)
                v.setOnClickListener {
                    runCatching {
                        val feed = locateNoteFeed(activity)
                        val video = feed.getObj("video")
                        val urlInfoList = video.getObjAs<List<*>>("urlInfoList")
                        val dialogCtx = object : ContextThemeWrapper(context, R.style.AppTheme) {
                            override fun getSystemService(name: String): Any? {
                                if (name == WINDOW_SERVICE) {
                                    return activity.getSystemService(name)
                                }
                                return super.getSystemService(name)
                            }
                        }
                        AlertDialog.Builder(dialogCtx)
                            .setTitle("视频链接")
                            .setView(ScrollView(dialogCtx).also { sv ->
                                sv.addView(LinearLayout(dialogCtx).also { lv ->
                                    lv.orientation = LinearLayout.VERTICAL
                                    urlInfoList.forEach { info ->
                                        runCatching {
                                            val url = info.getObjAs<String>("url")
                                            val desc = info.getObjAs<String>("desc")
                                            val btn = Button(dialogCtx)
                                            btn.text = desc
                                            btn.setOnClickListener {
                                                dialogCtx.getSystemService(ClipboardManager::class.java)
                                                    .setPrimaryClip(ClipData.newPlainText("", url))
                                            }
                                            lv.addView(btn)
                                        }.onFailure { t ->
                                            logE("add url btn $info", t)
                                        }
                                    }
                                })
                            }).show()
                    }.onFailure { t ->
                        logE("show url dialog", t)
                    }
                }
                addView(v)
                contentView = v
            }

            private var lastX: Int = 0
            private var lastY: Int = 0

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = ev.rawX.toInt()
                        lastY = ev.rawY.toInt()
                    }

                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val curX = ev.rawX.toInt()
                        val curY = ev.rawY.toInt()
                        val dx = curX - lastX
                        val dy = curY - lastY
                        lastX = curX
                        lastY = curY
                        val coords = IntArray(2)
                        getLocationOnScreen(coords)
                        lp.x = coords[0] + dx
                        lp.y = coords[1] + dy

                        wm.updateViewLayout(this, lp)
                    }
                }
                return super.dispatchTouchEvent(ev)
            }

            fun addToWindow() {
                wm.addView(this, lp)
            }

            fun removeFromWindow() {
                wm.removeViewImmediate(this)
            }
        }

        val decorViewClass = findClass("com.android.internal.policy.DecorView")

        decorViewClass.hookAllAfter("onAttachedToWindow", cond = ::isEnabled) { param ->
            val view = param.thisObject as View
            val activity = view.decorViewActivity() ?: return@hookAllAfter
            logD("attachedToWindow $activity")
            if (activity.javaClass !== detailFeedActivity) return@hookAllAfter
            var floatingWindow by extraField<FloatingWindow?>(activity, "floatingWindow", null)
            floatingWindow = FloatingWindow(
                activity, activity.call(
                "createApplicationContext",
                ApplicationInfo(activity.applicationInfo).apply {
                    packageName = BuildConfig.APPLICATION_ID
                    sourceDir = Entry.modulePath
                    publicSourceDir = Entry.modulePath
                    splitSourceDirs = null
                    splitPublicSourceDirs = null
                    splitNames = null
                }, 0
            ) as Context
            ).apply { addToWindow() }
        }

        decorViewClass.hookAllAfter("onDetachedFromWindow") { param ->
            val view = param.thisObject as View
            val activity = view.decorViewActivity() ?: return@hookAllAfter
            logD("onDetachedFromWindow $activity")
            if (activity.javaClass !== detailFeedActivity) return@hookAllAfter
            var floatingWindow by extraField<FloatingWindow?>(activity, "floatingWindow", null)
            floatingWindow?.removeFromWindow()
        }
    }
}

@Suppress("deprecation")
class XhsSettingsDialog(ctx: Context) : SettingDialog(ctx) {
    override fun onPrefChanged(
        preference: Preference,
        newValue: Any?
    ): Boolean {
        val settings = XhsHandler.settings
        val newSettings = when (preference.key) {
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

    override fun onPrefClicked(preference: Preference): Boolean {
        return false
    }

    override fun onRetrievePref(preference: Preference) {
        when (preference.key) {
            "saveOriginalImage" -> {
                (preference as SwitchPreference).isChecked =
                    XhsHandler.settings.saveOriginalImage
            }

            "forceSaveImage" -> {
                (preference as SwitchPreference).isChecked =
                    XhsHandler.settings.forceSaveImage
            }

            "openStickerAsImage" -> {
                (preference as SwitchPreference).isChecked =
                    XhsHandler.settings.openStickerAsImage
            }

            "betterShare" -> {
                (preference as SwitchPreference).isChecked =
                    XhsHandler.settings.betterShare
            }

            "extractVideoLink" -> {
                (preference as SwitchPreference).isChecked =
                    XhsHandler.settings.extractVideoLink
            }
        }
    }

    override fun onCreatePref(prefScreen: PreferenceScreen) {
        prefScreen.run {
            switchPreference("保存原图", "saveOriginalImage")
            switchPreference("移除保存图片限制", "forceSaveImage")
            switchPreference("用打开图片方式打开表情", "openStickerAsImage")
            switchPreference("增强分享", "betterShare")
            switchPreference(
                "提取视频链接",
                "extractVideoLink",
                summary = "在视频页面显示悬浮按钮，点击可提取视频链接"
            )
        }
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
        get() = _creator ?: ObfsTableCreator("", 5, appInfo = loadPackageParam.appInfo)
            .also { _creator = it }


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

    private val changeLock = Object()

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
}
