package io.github.a13e300.myinjector

import android.app.AndroidAppHelper
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.res.XModuleResources
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

fun View.findView(predicate: (View) -> Boolean): View? {
    if (predicate(this)) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val v = getChildAt(i).findView(predicate)
            if (v != null) return v
        }
    }
    return null
}

class TelegramHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "TelegramHandler"
        private val emotionMap = """6177238186645787094:128522:良辰共此曲动态表情包_傲娇
6177033303820866469:10067:良辰共此曲动态表情包_哈？
6177141996558226404:128075:良辰共此曲动态表情包_鼓掌
6174592156078969324:128105:良辰共此曲动态表情包_emo
6177004269841945229:128105:良辰共此曲动态表情包_观察
6174977380285682173:128105:良辰共此曲动态表情包_微笑
6176959378843767052:128105:良辰共此曲动态表情包_rua猫
6174981185626706785:128105:良辰共此曲动态表情包_探头
6177111738513625463:128105:良辰共此曲动态表情包_生气
6174978707430576848:128105:良辰共此曲动态表情包_瞪
6177158764110548966:128298:良辰共此曲动态表情包_剪切
6176700478215164551:128105:良辰共此曲动态表情包_比心
6174547917915820011:128105:良辰共此曲动态表情包_逃跑
6177098359690498494:128105:良辰共此曲动态表情包_泣
6177039660372464007:128105:良辰共此曲动态表情包_思考
6174437850788926135:128563:Mygo表情包_害羞
6177051956863831654:128548:Mygo表情包_生气
6176975888698053206:128105:Mygo表情包_发送消息
6177165941000900726:127861:Mygo表情包_抹茶芭菲
6174668318734029132:128308:Mygo表情包_请点单
6176874321311436983:128105:Mygo表情包_不要吵架
6174484176306182483:128105:Mygo表情包_Love
6177197174003078127:129303:Mygo表情包_让我看看
6177009075910348984:128105:Mygo表情包_溜了溜了
6177056788702040683:129303:Mygo表情包_那我呢？
6177073762412794635:128105:Mygo表情包_创作中
6174584652771105649:128100:Mygo表情包_探头
6176716163435730260:129300:Mygo表情包_为什么！
6176924800062066490:128105:Mygo表情包_刚睡醒
6177113383486100732:128516:Mygo表情包_哈？
6174728396736566571:128532:Mygo表情包_忧郁
6176734902378042250:10067:Mygo表情包_不会吧？
6174644503140374877:128105:Mygo表情包_大哭
6174667206337500263:128105:Mygo表情包_有趣的女人
6174571785049085622:128105:Mygo表情包_Block!
        """.trim().split("\n").map { it.split(":") }.associateBy { it[2] }
    }
    private lateinit var moduleRes: XModuleResources

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        moduleRes = XModuleResources.createInstance(Entry.modulePath, null)
        hookOpenLinkDialog(lpparam)
        hookMutualContact(lpparam)
        hookContactPermission(lpparam)
        // hookUserProfileShowId(lpparam)
        hookAutoCheckDeleteMessagesOptionAlso(lpparam)
        hookAutoUncheckSharePhoneNum(lpparam)
        hookDisableVoiceVideoButton(lpparam)
        hookLongClickMention(lpparam)
        hookFakeInstallPermission(lpparam)
        hookDoNotInstallGoogleMaps(lpparam)
        hookEmoji(lpparam)
        hookHasAppToOpen(lpparam)
    }

    private fun hookLongClickMention(lpparam: LoadPackageParam) = runCatching {
        val longClickListenerClass = XposedHelpers.findClass(
            "org.telegram.ui.Components.RecyclerListView\$OnItemLongClickListener",
            lpparam.classLoader
        )
        val tlUser =
            XposedHelpers.findClass("org.telegram.tgnet.TLRPC\$TL_user", lpparam.classLoader)
        val userObjectClass =
            XposedHelpers.findClass("org.telegram.messenger.UserObject", lpparam.classLoader)
        val classURLSpanUserMention = XposedHelpers.findClass(
            "org.telegram.ui.Components.URLSpanUserMention",
            lpparam.classLoader
        )
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.ui.ChatActivity", lpparam.classLoader),
            "createView",
            object : de.robv.android.xposed.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val obj = Object()
                    val thiz = param.thisObject
                    val mentionContainer = XposedHelpers.getObjectField(thiz, "mentionContainer")
                    val listView = XposedHelpers.callMethod(
                        mentionContainer,
                        "getListView"
                    ) // RecyclerListView
                    XposedHelpers.callMethod(
                        listView,
                        "setOnItemLongClickListener",
                        Proxy.newProxyInstance(
                            lpparam.classLoader, arrayOf(longClickListenerClass)
                        ) { _, method, args ->
                            if (method.name == "onItemClick") {
                                kotlin.runCatching {
                                    var position = args[1] as Int
                                    if (position == 0) return@newProxyInstance false
                                    position--
                                    val adapter =
                                        XposedHelpers.callMethod(mentionContainer, "getAdapter")
                                    val item =
                                        XposedHelpers.callMethod(adapter, "getItem", position)
                                    if (!tlUser.isInstance(item)) return@newProxyInstance false
                                    val start =
                                        XposedHelpers.callMethod(adapter, "getResultStartPosition")
                                    val len = XposedHelpers.callMethod(adapter, "getResultLength")
                                    val name = XposedHelpers.callStaticMethod(
                                        userObjectClass,
                                        "getFirstName",
                                        item,
                                        false
                                    )
                                    val spannable = SpannableString("$name ")
                                    val span = XposedHelpers.newInstance(
                                        classURLSpanUserMention,
                                        XposedHelpers.getObjectField(item, "id").toString(),
                                        3
                                    )
                                    spannable.setSpan(
                                        span,
                                        0,
                                        spannable.length,
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )
                                    val chatActivityEnterView =
                                        XposedHelpers.getObjectField(thiz, "chatActivityEnterView")
                                    XposedHelpers.callMethod(
                                        chatActivityEnterView,
                                        "replaceWithText",
                                        start,
                                        len,
                                        spannable,
                                        false
                                    )
                                    return@newProxyInstance true
                                }.onFailure { Log.e(TAG, "onItemLongClicked: error", it) }
                                return@newProxyInstance false
                            }
                            return@newProxyInstance method.invoke(obj, args)
                        }
                    )
                }
            }
        )
        Log.d(TAG, "hookLongClickMention: Done")
    }.onFailure {
        Log.e(TAG, "hookLongClickMention: failed", it)
    }

    private fun hookDisableVoiceVideoButton(lpparam: LoadPackageParam) = runCatching {
        val subHookFound = AtomicBoolean(false)
        XposedBridge.hookAllConstructors(
            XposedHelpers.findClass(
                "org.telegram.ui.Components.ChatActivityEnterView",
                lpparam.classLoader
            ),
            object : de.robv.android.xposed.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (subHookFound.get()) return
                    val audioVideoButtonContainer =
                        XposedHelpers.getObjectField(param.thisObject, "audioVideoButtonContainer")
                            ?: return
                    XposedBridge.hookAllMethods(
                        audioVideoButtonContainer.javaClass,
                        "onTouchEvent",
                        XC_MethodReplacement.returnConstant(true)
                    )
                    subHookFound.set(true)
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookDisableVoiceVideoButton: failed", it)
    }

    private fun hookAutoUncheckSharePhoneNum(lpparam: LoadPackageParam) = runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.ui.ContactAddActivity", lpparam.classLoader),
            "createView",
            object : de.robv.android.xposed.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val checkBox =
                        XposedHelpers.getObjectField(param.thisObject, "checkBoxCell") as? View
                            ?: return
                    if (XposedHelpers.callMethod(checkBox, "isChecked") == true) {
                        checkBox.performClick()
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookAutoUncheckSharePhoneNum: failed", it)
    }

    private fun hookAutoCheckDeleteMessagesOptionAlso(lpparam: LoadPackageParam) =
        kotlin.runCatching {
            val isCreating = ThreadLocal<Boolean>()
            val alertDialogClass = XposedHelpers.findClass(
                "org.telegram.ui.ActionBar.AlertDialog",
                lpparam.classLoader
            )
            val checkBoxCellClass =
                XposedHelpers.findClass("org.telegram.ui.Cells.CheckBoxCell", lpparam.classLoader)
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass(
                    "org.telegram.ui.Components.AlertsCreator",
                    lpparam.classLoader
                ),
                "createDeleteMessagesAlert",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        isCreating.set(true)
                    }

                    override fun afterHookedMethod(param: MethodHookParam?) {
                        isCreating.set(false)
                    }
                }
            )
            XposedHelpers.findAndHookMethod(
                XposedHelpers.findClass(
                    "org.telegram.ui.ActionBar.BaseFragment",
                    lpparam.classLoader
                ),
                "showDialog",
                Dialog::class.java,
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isCreating.get() != true) return
                        val dialog = param.args[0]
                        if (!alertDialogClass.isInstance(dialog)) return
                        val root = XposedHelpers.getObjectField(dialog, "customView") as? ViewGroup
                            ?: return

                        // TODO: find the checkbox correctly
                        val v = root.findView {
                            checkBoxCellClass.isInstance(it) &&
                                    XposedHelpers.callMethod(it, "isChecked") == false
                        }
                        // Log.d(TAG, "beforeHookedMethod: found view: $v")
                        v?.performClick()
                    }
                }
            )
        }.onFailure {
            Log.e(TAG, "hookAutoCheckDeleteMessagesOptionAlso: error", it)
        }

    private fun hookContactPermission(lpparam: LoadPackageParam) = kotlin.runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.ui.ContactsActivity", lpparam.classLoader),
            "onResume",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setObjectField(param.thisObject, "checkPermission", false)
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookContactPermission", it)
    }

    private fun hookMutualContact(lpparam: LoadPackageParam) = kotlin.runCatching {
        val drawable = moduleRes.getDrawable(R.drawable.ic_mutual_contact)
        val tlUser =
            XposedHelpers.findClass("org.telegram.tgnet.TLRPC\$TL_user", lpparam.classLoader)
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.ui.Cells.UserCell", lpparam.classLoader),
            "update",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // Log.d(TAG, "afterHookedMethod: update")
                    val d = XposedHelpers.getObjectField(param.thisObject, "currentDrawable") as Int
                    if (d != 0) {
                        // Log.d(TAG, "afterHookedMethod: currentdrawable not 0: $d")
                        return
                    }
                    val current = XposedHelpers.getObjectField(param.thisObject, "currentObject")
                    if (!tlUser.isInstance(current)) return
                    val imageView =
                        XposedHelpers.getObjectField(param.thisObject, "imageView") as ImageView
                    val mutual = XposedHelpers.getObjectField(current, "mutual_contact") as Boolean
                    if (mutual) {
                        imageView.setImageDrawable(drawable)
                        imageView.visibility = View.VISIBLE
                        (imageView.layoutParams as FrameLayout.LayoutParams).apply {
                            val resource = imageView.context.resources
                            gravity =
                                (gravity and Gravity.HORIZONTAL_GRAVITY_MASK.inv()) or Gravity.RIGHT
                            rightMargin =
                                TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    8f,
                                    resource.displayMetrics
                                ).toInt()
                            leftMargin
                        }
                        // Log.d(TAG, "afterHookedMethod: set mutual contact $current")
                    }
                }
            }
        )
        Log.d(TAG, "hookMutualContact: done")
    }.onFailure { Log.e(TAG, "hookMutualContact", it) }

    data class FixLink(
        val pos: Int,
        val url: String,
        var openRunnable: Runnable?
    )

    private fun hookOpenLinkDialog(lpparam: LoadPackageParam) = kotlin.runCatching {
        Log.d(TAG, "hookOpenLinkDialog")
        val classBaseFragment =
            XposedHelpers.findClass("org.telegram.ui.ActionBar.BaseFragment", lpparam.classLoader)

        val fixLink = ThreadLocal<FixLink>()
        val regexTelegraph = Regex("^https?://telegra\\.ph")
        val escapeChars = Regex("[^!#\$&'*+\\(\\),-./:;%=\\?@_~0-9A-Za-z]")
        val classBrowser =
            XposedHelpers.findClass("org.telegram.messenger.browser.Browser", lpparam.classLoader)
        val classChatActivity =
            XposedHelpers.findClass("org.telegram.ui.ChatActivity", lpparam.classLoader)

        Log.d(TAG, "hookOpenLinkDialog: start hook")

        XposedBridge.hookAllMethods(
            classChatActivity,
            "processExternalUrl",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val url = param.args[1] as String
                    // Log.d(TAG, "processExternalUrl: $url")
                    if (regexTelegraph.find(url) != null) return
                    val match = escapeChars.find(url) ?: return
                    val pos = match.range.first
                    val realUrl = url.substring(0, pos)
                    fixLink.set(FixLink(pos, realUrl, null))
                    param.args[4] = true
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            XposedHelpers.findClass(
                "org.telegram.ui.Components.AlertsCreator",
                lpparam.classLoader
            ),
            "showOpenUrlAlert",
            classBaseFragment, // 0 fragment
            String::class.java, // 1 url
            Boolean::class.java, // 2 punycode
            Boolean::class.java, // 3 tryTelegraph
            Boolean::class.java, // 4 ask
            Boolean::class.java, // 5
            // 6 progress
            XposedHelpers.findClass(
                "org.telegram.messenger.browser.Browser\$Progress",
                lpparam.classLoader
            ),
            // 7
            XposedHelpers.findClass(
                "org.telegram.ui.ActionBar.Theme\$ResourcesProvider",
                lpparam.classLoader
            ),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    fixLink.get()?.let {
                        // Log.d(TAG, "showOpenUrlAlert")
                        it.openRunnable = Runnable {
                            val frag = param.args[0]
                            val inlineReturn = if (classChatActivity.isInstance(frag))
                                XposedHelpers.callMethod(frag, "getInlineReturn")
                            else 0
                            Log.d(TAG, "open ${it.url}")
                            XposedHelpers.callStaticMethod(
                                classBrowser,
                                "openUrl",
                                XposedHelpers.callMethod(frag, "getParentActivity"),
                                Uri.parse(it.url),
                                inlineReturn == 0,
                                param.args[3], // tryTelegraph
                                param.args[6] // progress
                            )
                        }
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            classBaseFragment,
            "showDialog",
            Dialog::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    fixLink.get()?.let { fl ->
                        // Log.d(TAG, "showDialog")
                        fixLink.set(null)
                        val dialog = param.args[0]
                        XposedHelpers.setObjectField(dialog, "neutralButtonText", "fix")
                        XposedHelpers.setObjectField(
                            dialog,
                            "neutralButtonListener",
                            DialogInterface.OnClickListener { _, _ ->
                                fl.openRunnable?.run()
                            })
                        val message =
                            XposedHelpers.getObjectField(dialog, "message") as CharSequence
                        val newMessage = SpannableStringBuilder(message)
                            .append("(")
                            .append(
                                fl.url,
                                StyleSpan(Typeface.BOLD),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            .append(")")
                        XposedHelpers.setObjectField(dialog, "message", newMessage)
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookOpenLinkDialog: ", it)
    }

    data class ProfileActivityCtx(
        var insertedId: Int = -1,
        var userId: String = "",
        var title: String = ""
    )

    private fun hookUserProfileShowId(lpparam: LoadPackageParam) = kotlin.runCatching {
        // activity -> ctx
        val ctxs = WeakHashMap<Any, ProfileActivityCtx>()
        fun outerThis(obj: Any): Any {
            return XposedHelpers.getObjectField(obj, "this\$0")
        }

        fun getCtx(obj: Any): ProfileActivityCtx? {
            return ctxs.computeIfAbsent(obj) {
                ProfileActivityCtx(-1)
            }
        }

        fun getCtxForAdapter(obj: Any) = getCtx(outerThis(obj))
        val profileActivityClass =
            XposedHelpers.findClass("org.telegram.ui.ProfileActivity", lpparam.classLoader)
        val listAdapterClass = XposedHelpers.findClass(
            "org.telegram.ui.ProfileActivity\$ListAdapter",
            lpparam.classLoader
        )
        val VIEW_TYPE_TEXT_DETAIL =
            2 // XposedHelpers.getStaticIntField(listAdapterClass, "VIEW_TYPE_TEXT")
        val rowFields = profileActivityClass.declaredFields.filter {
            it.name.endsWith("Row") || it.name.endsWith("Row2")
        }
        rowFields.forEach {
            it.isAccessible = true
        }
        XposedBridge.hookAllMethods(
            profileActivityClass,
            "updateRowsIds",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = getCtx(param.thisObject) ?: return
                    var userId = XposedHelpers.getObjectField(param.thisObject, "userId")
                    var isChat = false
                    if (userId == 0L) {
                        val chat = XposedHelpers.getObjectField(param.thisObject, "currentChat")
                        if (chat != null) {
                            userId = XposedHelpers.getObjectField(chat, "id")
                            isChat = true
                        }
                    }
                    if (userId == 0L) {
                        ctx.insertedId = -1
                        return
                    }
                    ctx.userId = userId.toString()
                    ctx.title = if (isChat) "Chat Id" else "User Id"
                    val headerId =
                        XposedHelpers.getObjectField(param.thisObject, "infoHeaderRow") as Int
                    val insertedId = if (headerId == -1) 1 else headerId + 1
                    ctx.insertedId = insertedId
                    rowFields.forEach {
                        val v = it.get(param.thisObject) as Int
                        if (v >= insertedId) it.set(param.thisObject, v + 1)
                    }
                    XposedHelpers.setObjectField(
                        param.thisObject, "rowCount",
                        XposedHelpers.getObjectField(param.thisObject, "rowCount") as Int + 1
                    )
                    // Log.d(TAG, "updateRowsIds: inserted=$insertedId")
                }
            }
        )
        XposedBridge.hookAllMethods(
            listAdapterClass,
            "onBindViewHolder",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ctx = getCtxForAdapter(param.thisObject) ?: return
                    if (ctx.insertedId == -1) return
                    val pos = param.args[1] as Int
                    if (pos == ctx.insertedId) {
                        val detailCeil = XposedHelpers.getObjectField(param.args[0], "itemView")
                        XposedHelpers.callMethod(
                            detailCeil, "setTextAndValue", ctx.userId,
                            ctx.title, false
                        )
                        param.result = null
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            listAdapterClass,
            "getItemViewType",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ctx = getCtxForAdapter(param.thisObject) ?: return
                    if (ctx.insertedId == -1) return
                    val pos = param.args[0] as Int
                    if (pos == ctx.insertedId) {
                        param.result = VIEW_TYPE_TEXT_DETAIL
                    }
                }
            }
        )
        XposedBridge.hookAllMethods(
            profileActivityClass,
            "processOnClickOrPress",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "processOnClickOrPress: ${param.args[0]}")
                    val ctx = getCtx(param.thisObject) ?: return
                    if (ctx.insertedId == -1) return
                    if (param.args[0] == ctx.insertedId) {
                        val context = AndroidAppHelper.currentApplication()
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("", ctx.userId))
                        Toast.makeText(context, "copied user id!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookUserProfileShowId: error", it)
    }

    private fun hookFakeInstallPermission(lpparam: LoadPackageParam) = runCatching {
        XposedHelpers.findAndHookMethod(
            XposedHelpers.findClass("android.app.ApplicationPackageManager", lpparam.classLoader),
            "canRequestPackageInstalls",
            XC_MethodReplacement.returnConstant(true)
        )
    }.onFailure {
        Log.e(TAG, "hookFakeInstallPermission: ", it)
    }

    private fun hookDoNotInstallGoogleMaps(lpparam: LoadPackageParam) = runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.messenger.AndroidUtilities", lpparam.classLoader),
            "isMapsInstalled",
            XC_MethodReplacement.returnConstant(true)
        )
    }

    private fun hookEmoji(lpparam: LoadPackageParam) = runCatching {
        val emojiPacksAlert = XposedHelpers.findClass(
            "org.telegram.ui.Components.EmojiPacksAlert",
            lpparam.classLoader
        )
        val emojiPacksAlertEmojiPackHeader = XposedHelpers.findClass(
            "org.telegram.ui.Components.EmojiPacksAlert\$EmojiPackHeader",
            lpparam.classLoader
        )
        val customEmojiClass = XposedHelpers.findClass(
            "org.telegram.tgnet.TLRPC\$TL_documentAttributeCustomEmoji",
            lpparam.classLoader
        )

        XposedBridge.hookAllConstructors(
            emojiPacksAlertEmojiPackHeader,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val optionsButton =
                        XposedHelpers.getObjectField(param.thisObject, "optionsButton") ?: return
                    XposedHelpers.callMethod(optionsButton, "addSubItem", 3, "Dump")
                }
            }
        )

        XposedBridge.hookAllMethods(
            emojiPacksAlert,
            "onSubItemClick",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != 3) return
                    Log.d(TAG, "dump: clicked")
                    val customEmojiPacks =
                        XposedHelpers.getObjectField(param.thisObject, "customEmojiPacks")
                    val stickerSets =
                        XposedHelpers.getObjectField(customEmojiPacks, "stickerSets") as List<*>
                    val str = StringBuilder()
                    stickerSets.firstOrNull()?.let { tlMessagesStickerSet ->
                        val set = XposedHelpers.getObjectField(tlMessagesStickerSet, "set")
                        val title = XposedHelpers.getObjectField(set, "title")
                        val id = XposedHelpers.getObjectField(set, "id")
                        val shortName = XposedHelpers.getObjectField(set, "short_name")
                        // Log.d(TAG, "dump: $title $id $shortName")
                        str.append("title=")
                            .append(title)
                            .append("\nid=")
                            .append(id)
                            .append("\nshortName=")
                            .append(shortName)

                        val documents = XposedHelpers.getObjectField(
                            tlMessagesStickerSet,
                            "documents"
                        ) as List<*>
                        documents.forEachIndexed { i, doc ->
                            val id = XposedHelpers.getObjectField(doc, "id")
                            val alt = (XposedHelpers.getObjectField(
                                doc,
                                "attributes"
                            ) as List<*>).firstOrNull {
                                customEmojiClass.isInstance(it)
                            }?.let { attr ->
                                XposedHelpers.getObjectField(attr, "alt")
                            } as? String
                            // Log.d(TAG, "dump: $i id=$id alt=$alt")
                            val altUnicode = alt?.firstUnicodeChar()
                            str.append("\n$i=$id:$altUnicode")
                        }

                        AndroidAppHelper.currentApplication().getSystemService(
                            ClipboardManager::class.java
                        ).setPrimaryClip(ClipData.newPlainText("", str.toString()))
                    }
                }
            }
        )

        XposedBridge.hookAllMethods(
            ClipboardManager::class.java,
            "getPrimaryClip",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // Log.d(TAG, "afterHookedMethod: getPrimaryClip")
                    val result = param.result as? ClipData ?: return
                    val item = result.getItemAt(0)
                    val origText = item.text
                    var newText = origText
                    var pos = 0
                    // Log.d(TAG, "afterHookedMethod: $origText")
                    while (true) {
                        val firstIdx = newText.indexOf('[', pos)
                        if (firstIdx == -1) break
                        val lastIdx = newText.indexOf(']', firstIdx)
                        if (lastIdx == -1) break
                        // Log.d(TAG, "afterHookedMethod: $firstIdx $lastIdx")
                        pos = lastIdx
                        val kw = newText.substring(firstIdx + 1 until lastIdx)
                        val replacement = emotionMap[kw]?.let {
                            "<animated-emoji data-document-id=\"${it[0]}\">&#${it[1]};</animated-emoji>"
                        } ?: continue
                        newText = newText.replaceRange(firstIdx..lastIdx, replacement)
                        // Log.d(TAG, "afterHookedMethod: replaced=$newText")
                        pos = firstIdx + replacement.length
                    }
                    if (newText !== origText) {
                        // Log.d(TAG, "replace: $newText")
                        param.result = ClipData.newHtmlText(
                            "",
                            newText,
                            newText.toString().replace("\n", "<br>")
                        )
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "emojiHandler: ", it)
    }

    // 这构式逻辑谁写的？
    // https://github.com/DrKLO/Telegram/blob/eee720ef5e48e1c434f4c5a83698dc4ada34aaa9/TMessagesProj/src/main/java/org/telegram/messenger/browser/Browser.java#L391
    private fun hookHasAppToOpen(lpparam: LoadPackageParam) = runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                "org.telegram.messenger.browser.Browser", lpparam.classLoader
            ),
            "hasAppToOpen",
            XC_MethodReplacement.returnConstant(true)
        )
    }.onFailure {
        Log.e(TAG, "hookHasAppToOpen: ", it)
    }
}

private fun String.firstUnicodeChar(): Int {
    val c = get(0).code
    if (c >= 0xd800 && c <= 0xdfff) {
        if (c < 0xdc00 && length >= 2) {
            val d = get(1).code
            if (d >= 0xdc00 && d <= 0xdfff) {
                return 0x010000.or((c - 0xd800).shl(10)).or(d - 0xdc00)
            }
        }
    }
    return c
}
