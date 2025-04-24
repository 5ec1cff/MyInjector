package io.github.a13e300.myinjector

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
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
import org.json.JSONObject
import java.io.File
import java.io.InputStream
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

fun Any?.getObj(name: String): Any? = XposedHelpers.getObjectField(this, name)

fun Any?.call(name: String, vararg args: Any?): Any? = XposedHelpers.callMethod(this, name, *args)

fun Class<*>.callS(name: String, vararg args: Any?): Any? = XposedHelpers.callStaticMethod(this, name, *args)

class TelegramHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "TelegramHandler"
        private const val MENU_DUMP = 301
        private const val MENU_GET_PROFILE = 302
        // emoji name -> (emoji id, emoji)
    }
    private lateinit var moduleRes: XModuleResources

    private var _emotionMap: Map<String, Pair<String, String>>? = null
    private fun loadEmotionMap(json: String): Map<String, Pair<String, String>> {
        val data = JSONObject(json)
        val result = mutableMapOf<String, Pair<String, String>>()
        for (k in data.keys()) {
            val v = data.getJSONArray(k)
            val id = v.getString(0)
            val name = v.getJSONArray(1).getString(0)
            result.put(k, Pair(id, name))
        }
        return result
    }
    private fun Context.getEmotionMapFile(): File {
        return File(getExternalFilesDir(""), "emotion_map.json")
    }
    private val emotionMap: Map<String, Pair<String, String>>
        get() {
            if (_emotionMap == null) {
                synchronized(this) {
                    if (_emotionMap == null) {
                        val f = AndroidAppHelper.currentApplication().getEmotionMapFile()
                        try {
                            _emotionMap = loadEmotionMap(f.readText())
                        } catch (t: Throwable) {
                            Log.e(TAG, "load emotion map from $f failed  ", t)
                            _emotionMap = emptyMap()
                        }
                    }
                }
            }
            return _emotionMap!!
        }

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
        hookEmojiManage(lpparam)
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
                    XposedHelpers.callMethod(optionsButton, "addSubItem", MENU_DUMP, "Dump")
                    XposedHelpers.callMethod(optionsButton, "addSubItem", MENU_GET_PROFILE, "Profile of admin")
                }
            }
        )

        val messagesController = XposedHelpers.findClass("org.telegram.messenger.MessagesController", lpparam.classLoader)

        XposedBridge.hookAllMethods(
            emojiPacksAlert,
            "onSubItemClick",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/EmojiPacksAlert.java#L1541
                    if (param.args[0] == MENU_GET_PROFILE) {
                        val stickerSet = (param.thisObject.getObj("customEmojiPacks").getObj("stickerSets") as List<*>)[0]
                        val id = stickerSet.getObj("set").getObj("id") as Long
                        var userId = id.shr(32)
                        if (id.shr(16).and(0xff) == 0x3fL) {
                            userId = userId.or(0x80000000L)
                        }
                        if (id.shr(24).and(0xff) != 0L) {
                            userId += 0x100000000L;
                        }
                        val fragment = param.thisObject.getObj("fragment")
                        if (fragment != null) {
                            val user = fragment.call("getMessagesController").call("getUser", userId)
                            val currentAccount = param.thisObject.getObj("currentAccount")
                            if (user != null) {
                                messagesController.callS("getInstance", currentAccount)
                                    .call("openChatOrProfileWith", user, null, fragment, 0, false)
                                return
                            }
                        }
                        AndroidAppHelper.currentApplication().getSystemService(
                            ClipboardManager::class.java
                        ).setPrimaryClip(ClipData.newPlainText("", userId.toString()))
                        Toast.makeText(AndroidAppHelper.currentApplication(), "User: " + userId.toString(),
                            Toast.LENGTH_SHORT).show()
                    }
                    if (param.args[0] != MENU_DUMP) return
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
                        val kw = newText.substring(firstIdx..lastIdx)
                        val replacement = emotionMap[kw]?.let {
                            "<animated-emoji data-document-id=\"${it.first}\">&#${it.second.firstUnicodeChar()};</animated-emoji>"
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

        val stickersAlert = XposedHelpers.findClass(
            "org.telegram.ui.Components.StickersAlert",
            lpparam.classLoader
        )

        // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/StickersAlert.java#L1485
        XposedBridge.hookAllMethods(
            stickersAlert, "init",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val optionsButton =
                        XposedHelpers.getObjectField(param.thisObject, "optionsButton") ?: return
                    XposedHelpers.callMethod(optionsButton, "addSubItem", MENU_GET_PROFILE, "Profile of admin")
                }
            }
        )
        XposedBridge.hookAllMethods(
            stickersAlert,
            "onSubItemClick",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == MENU_GET_PROFILE) {
                        val stickerSet = param.thisObject.getObj("stickerSet")
                        val id = stickerSet.getObj("set").getObj("id") as Long
                        var userId = id.shr(32)
                        if (id.shr(16).and(0xff) == 0x3fL) {
                            userId = userId.or(0x80000000L)
                        }
                        if (id.shr(24).and(0xff) != 0L) {
                            userId += 0x100000000L;
                        }
                        val parentFragment = param.thisObject.getObj("parentFragment")
                        if (parentFragment != null) {
                            val user = parentFragment.call("getMessagesController").call("getUser", userId)
                            val currentAccount = param.thisObject.getObj("currentAccount")
                            if (user != null) {
                                messagesController.callS("getInstance", currentAccount)
                                    .call("openChatOrProfileWith", user, null, parentFragment, 0, false)
                                return
                            }
                        }
                        AndroidAppHelper.currentApplication().getSystemService(
                            ClipboardManager::class.java
                        ).setPrimaryClip(ClipData.newPlainText("", userId.toString()))
                        Toast.makeText(AndroidAppHelper.currentApplication(), "User: " + userId.toString(),
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "emojiHandler: ", it)
    }

    private fun hookEmojiManage(lpparam: LoadPackageParam) = runCatching {
        val chatActivityEnterView = XposedHelpers.findClass(
            "org.telegram.ui.Components.ChatActivityEnterView",
            lpparam.classLoader
        )

        val cst = chatActivityEnterView.declaredConstructors.maxBy { it.parameterCount }!!
            .also { it.isAccessible = true }
        XposedBridge.hookMethod(cst, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                (XposedHelpers.getObjectField(param.thisObject, "emojiButton") as View)
                    .setOnLongClickListener { v ->
                        Toast.makeText(v.context, "choose an emotion map json file", Toast.LENGTH_SHORT).show()
                        val activity = v.context as Activity
                        activity.startActivityForResult(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                            },
                            114514
                        )
                        true
                    }
            }
        })

        XposedBridge.hookAllMethods(Activity::class.java, "dispatchActivityResult", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.args[1] == 114514) {
                    // Log.d(TAG, "dispatchActivityResult: " + param.args[3])
                    val ctx = param.thisObject as Activity
                    (param.args[3] as? Intent)?.data?.let {
                        url ->
                        ctx.contentResolver.openInputStream(url)?.let {
                            val text = it.readBytes().toString(Charsets.UTF_8)
                            val mp = try {
                                loadEmotionMap(text)
                            } catch (t: Throwable) {
                                Log.e(TAG, "loadEmotionMap: ", t)
                                Toast.makeText(ctx, "load failed: $t", Toast.LENGTH_LONG).show()
                                return
                            }
                            synchronized(this@TelegramHandler) {
                                _emotionMap = mp
                            }
                            ctx.getEmotionMapFile().writeText(text)
                            Toast.makeText(ctx, "load success", Toast.LENGTH_SHORT).show()
                            return
                        }
                    }
                    Toast.makeText(ctx, "no file provided", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }.onFailure {
        Log.e(TAG, "hookEmojiManage: ", it)
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
