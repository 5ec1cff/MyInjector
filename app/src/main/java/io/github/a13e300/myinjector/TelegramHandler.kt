package io.github.a13e300.myinjector

import android.app.Activity
import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.XModuleResources
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.findView
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllConstant
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstant
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.setObj
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

class TelegramHandler : IHook() {
    companion object {
        private const val MENU_DUMP = 301
        private const val MENU_GET_PROFILE = 302
        // emoji name -> (emoji id, emoji)
    }
    private lateinit var moduleRes: XModuleResources

    data class EmotionMap(
        val map: Map<String, Pair<String, String>>,
        val regex: Regex
    )

    private var _emotionMap: EmotionMap? = null
    private fun loadEmotionMap(json: String): EmotionMap {
        val data = JSONObject(json)
        val mp = mutableMapOf<String, Pair<String, String>>()
        for (k in data.keys()) {
            val v = data.getJSONArray(k)
            val id = v.getString(0)
            val name = v.getJSONArray(1).getString(0)
            mp.put(k, Pair(id, name))
        }
        val regex = Regex(mp.keys.joinToString("|") { Regex.escape(it) })
        return EmotionMap(mp, regex)
    }
    private fun Context.getEmotionMapFile(): File {
        return File(getExternalFilesDir(""), "emotion_map.json")
    }
    private val emotionMap: EmotionMap
        get() {
            val m = _emotionMap
            if (m == null) {
                synchronized(this) {
                    val m2 = _emotionMap
                    if (m2 == null) {
                        val f = AndroidAppHelper.currentApplication().getEmotionMapFile()
                        val mp = try {
                            loadEmotionMap(f.readText())
                        } catch (t: Throwable) {
                            logE("load emotion map from $f failed  ", t)
                            EmotionMap(emptyMap(), Regex(""))
                        }
                        _emotionMap = mp
                        return mp
                    }
                    return m2
                }
            }
            return m
        }


    override fun onHook(param: LoadPackageParam) {
        moduleRes = XModuleResources.createInstance(Entry.modulePath, null)
        hookOpenLinkDialog()
        hookMutualContact()
        hookContactPermission()
        hookAutoCheckDeleteMessagesOptionAlso()
        hookAutoUncheckSharePhoneNum()
        hookDisableVoiceVideoButton()
        hookLongClickMention()
        hookFakeInstallPermission()
        hookDoNotInstallGoogleMaps()
        hookEmoji()
        hookEmojiManage()
        hookHasAppToOpen()
        hookDefaultSearchTab()
        hookSetPosition()
        hookAvatarPagerScrollToCurrent()
    }

    private fun hookLongClickMention() = runCatching {
        val longClickListenerClass =
            findClass("org.telegram.ui.Components.RecyclerListView\$OnItemLongClickListener")
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        val userObjectClass = findClass("org.telegram.messenger.UserObject")
        val classURLSpanUserMention = findClass("org.telegram.ui.Components.URLSpanUserMention")
        findClass("org.telegram.ui.ChatActivity").hookAllAfter("createView") { param ->
            val obj = Object()
            val thiz = param.thisObject
            val mentionContainer = XposedHelpers.getObjectField(thiz, "mentionContainer")
            val listView = XposedHelpers.callMethod(
                mentionContainer,
                "getListView"
            ) // RecyclerListView
            val proxy = Proxy.newProxyInstance(
                classLoader, arrayOf(longClickListenerClass)
            ) { _, method, args ->
                if (method.name == "onItemClick") {
                    kotlin.runCatching {
                        var position = args[1] as Int
                        if (position == 0) return@newProxyInstance false
                        position--
                        val adapter = mentionContainer.call("getAdapter")
                        val item = adapter.call("getItem", position)
                        if (!tlUser.isInstance(item)) return@newProxyInstance false
                        val start = adapter.call("getResultStartPosition")
                        val len = adapter.call("getResultLength")
                        val name = userObjectClass.callS(
                            "getFirstName",
                            item,
                            false
                        )
                        val spannable = SpannableString("$name ")
                        val span = classURLSpanUserMention.newInst(
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
                            thiz.getObj("chatActivityEnterView")
                        chatActivityEnterView.call(
                            "replaceWithText",
                            start,
                            len,
                            spannable,
                            false
                        )
                        return@newProxyInstance true
                    }.onFailure { logE("onItemLongClicked: error", it) }
                    return@newProxyInstance false
                }
                return@newProxyInstance method.invoke(obj, args)
            }
            listView.call("setOnItemLongClickListener", proxy)
        }
        logD("hookLongClickMention: Done")
    }.onFailure {
        logE("hookLongClickMention: failed", it)
    }

    private fun hookDisableVoiceVideoButton() = runCatching {
        val subHookFound = AtomicBoolean(false)
        findClass("org.telegram.ui.Components.ChatActivityEnterView").hookAllCAfter { param ->
            if (subHookFound.get()) return@hookAllCAfter
            val audioVideoButtonContainer =
                param.thisObject.getObj("audioVideoButtonContainer") ?: return@hookAllCAfter
            audioVideoButtonContainer.javaClass.hookAllConstant("onTouchEvent", true)
            subHookFound.set(true)
        }
    }.onFailure {
        logE("hookDisableVoiceVideoButton: failed", it)
    }

    private fun hookAutoUncheckSharePhoneNum() = runCatching {
        findClass("org.telegram.ui.ContactAddActivity").hookAllAfter("createView") { param ->
            val checkBox = param.thisObject.getObj("checkBoxCell") as? View ?: return@hookAllAfter
            if (checkBox.call("isChecked") == true) {
                checkBox.performClick()
            }
        }
    }.onFailure {
        logE("hookAutoUncheckSharePhoneNum: failed", it)
    }

    private fun hookAutoCheckDeleteMessagesOptionAlso() =
        kotlin.runCatching {
            val isCreating = ThreadLocal<Boolean>()
            val alertDialogClass = findClass("org.telegram.ui.ActionBar.AlertDialog")
            val checkBoxCellClass = findClass("org.telegram.ui.Cells.CheckBoxCell")
            findClass("org.telegram.ui.Components.AlertsCreator").hookAll(
                "createDeleteMessagesAlert",
                before = {
                    isCreating.set(true)
                },
                after = {
                    isCreating.set(false)
                }
            )
            findClass("org.telegram.ui.ActionBar.BaseFragment").hookBefore(
                "showDialog",
                Dialog::class.java
            ) { param ->
                if (isCreating.get() != true) return@hookBefore
                val dialog = param.args[0]
                if (!alertDialogClass.isInstance(dialog)) return@hookBefore
                val root = dialog.getObjAsN<ViewGroup>("customView") ?: return@hookBefore

                // TODO: find the checkbox correctly
                val v = root.findView {
                    checkBoxCellClass.isInstance(it) &&
                            it.call("isChecked") == false
                }
                // logD("beforeHookedMethod: found view: $v")
                v?.performClick()
            }
        }.onFailure {
            logE("hookAutoCheckDeleteMessagesOptionAlso: error", it)
        }

    private fun hookContactPermission() = kotlin.runCatching {
        findClass("org.telegram.ui.ContactsActivity").hookAllBefore("onResume") { param ->
            param.thisObject.setObj("checkPermission", false)
        }
    }.onFailure {
        logE("hookContactPermission", it)
    }

    private fun hookMutualContact() = kotlin.runCatching {
        val drawable = moduleRes.getDrawable(R.drawable.ic_mutual_contact)
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        findClass("org.telegram.ui.Cells.UserCell").hookAllAfter("update") { param ->
            // logD("afterHookedMethod: update")
            val d = param.thisObject.getObjAs<Int>("currentDrawable")
            if (d != 0) {
                // logD("afterHookedMethod: currentdrawable not 0: $d")
                return@hookAllAfter
            }
            val current = param.thisObject.getObj("currentObject")
            if (!tlUser.isInstance(current)) return@hookAllAfter
            val imageView = param.thisObject.getObjAs<ImageView>("imageView")
            val mutual = current.getObjAs<Boolean>("mutual_contact")
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
                // logD("afterHookedMethod: set mutual contact $current")
            }
        }
        logD("hookMutualContact: done")
    }.onFailure { logE("hookMutualContact", it) }

    data class FixLink(
        val pos: Int,
        val url: String,
        var openRunnable: Runnable?
    )

    private fun hookOpenLinkDialog() = kotlin.runCatching {
        logD("hookOpenLinkDialog")
        val classBaseFragment = findClass("org.telegram.ui.ActionBar.BaseFragment")

        val fixLink = ThreadLocal<FixLink>()
        val regexTelegraph = Regex("^https?://telegra\\.ph")
        val escapeChars = Regex("[^!#\$&'*+\\(\\),-./:;%=\\?@_~0-9A-Za-z]")
        val classBrowser = findClass("org.telegram.messenger.browser.Browser")
        val classChatActivity = findClass("org.telegram.ui.ChatActivity")

        logD("hookOpenLinkDialog: start hook")

        classChatActivity.hookAllBefore("processExternalUrl") { param ->
            val url = param.args[1] as String
            // logD("processExternalUrl: $url")
            if (regexTelegraph.find(url) != null) return@hookAllBefore
            val match = escapeChars.find(url) ?: return@hookAllBefore
            val pos = match.range.first
            val realUrl = url.substring(0, pos)
            fixLink.set(FixLink(pos, realUrl, null))
            param.args[4] = true
        }


        findClass("org.telegram.ui.Components.AlertsCreator").hookBefore(
            "showOpenUrlAlert",
            classBaseFragment, // 0 fragment
            String::class.java, // 1 url
            Boolean::class.java, // 2 punycode
            Boolean::class.java, // 3 tryTelegraph
            Boolean::class.java, // 4 ask
            Boolean::class.java, // 5
            // 6 progress
            findClass("org.telegram.messenger.browser.Browser\$Progress"),
            // 7
            findClass("org.telegram.ui.ActionBar.Theme\$ResourcesProvider")
        ) { param ->
            fixLink.get()?.let {
                // logD("showOpenUrlAlert")
                it.openRunnable = Runnable {
                    val frag = param.args[0]
                    val inlineReturn = if (classChatActivity.isInstance(frag))
                        frag.call("getInlineReturn")
                    else 0
                    logD("open ${it.url}")
                    classBrowser.callS(
                        "openUrl",
                        frag.call("getParentActivity"),
                        Uri.parse(it.url),
                        inlineReturn == 0,
                        param.args[3], // tryTelegraph
                        param.args[6] // progress
                    )
                }
            }
        }


        classBaseFragment.hookBefore(
            "showDialog",
            Dialog::class.java
        ) { param ->
            fixLink.get()?.let { fl ->
                // logD("showDialog")
                fixLink.set(null)
                val dialog = param.args[0]
                dialog.setObj("neutralButtonText", "fix")
                dialog.setObj(
                    "neutralButtonListener",
                    DialogInterface.OnClickListener { _, _ ->
                        fl.openRunnable?.run()
                    }
                )
                val message = dialog.getObjAs<CharSequence>("message")
                val newMessage = SpannableStringBuilder(message)
                    .append("(")
                    .append(
                        fl.url,
                        StyleSpan(Typeface.BOLD),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    .append(")")
                dialog.setObj("message", newMessage)
            }
        }
    }.onFailure {
        logE("hookOpenLinkDialog: ", it)
    }

    private fun hookFakeInstallPermission() = runCatching {
        findClass("android.app.ApplicationPackageManager").hookConstant(
            "canRequestPackageInstalls",
            constant = true
        )
    }.onFailure {
        logE("hookFakeInstallPermission: ", it)
    }

    private fun hookDoNotInstallGoogleMaps() = runCatching {
        findClass("org.telegram.messenger.AndroidUtilities")
            .hookAllConstant("isMapsInstalled", true)
    }

    private fun hookEmoji() = runCatching {
        val emojiPacksAlert = findClass("org.telegram.ui.Components.EmojiPacksAlert")
        val emojiPacksAlertEmojiPackHeader =
            findClass("org.telegram.ui.Components.EmojiPacksAlert\$EmojiPackHeader")
        val customEmojiClass =
            findClass("org.telegram.tgnet.TLRPC\$TL_documentAttributeCustomEmoji")

        emojiPacksAlertEmojiPackHeader.hookAllCAfter { param ->
            val optionsButton = param.thisObject.getObj("optionsButton") ?: return@hookAllCAfter
            optionsButton.call("addSubItem", MENU_DUMP, "Dump")
            optionsButton.call("addSubItem", MENU_GET_PROFILE, "Profile of admin")
        }

        val messagesController = findClass("org.telegram.messenger.MessagesController")

        emojiPacksAlert.hookAllBefore("onSubItemClick") { param ->
            // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/EmojiPacksAlert.java#L1541
            if (param.args[0] == MENU_GET_PROFILE) {
                val stickerSet = (param.thisObject.getObj("customEmojiPacks")
                    .getObj("stickerSets") as List<*>)[0]
                val id = stickerSet.getObj("set").getObj("id") as Long
                var userId = id.shr(32)
                if (id.shr(16).and(0xff) == 0x3fL) {
                    userId = userId.or(0x80000000L)
                }
                if (id.shr(24).and(0xff) != 0L) {
                    userId += 0x100000000L
                }
                val fragment = param.thisObject.getObj("fragment")
                if (fragment != null) {
                    val user = fragment.call("getMessagesController").call("getUser", userId)
                    val currentAccount = param.thisObject.getObj("currentAccount")
                    if (user != null) {
                        messagesController.callS("getInstance", currentAccount)
                            .call("openChatOrProfileWith", user, null, fragment, 0, false)
                        return@hookAllBefore
                    }
                }
                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", userId.toString()))
                Toast.makeText(
                    AndroidAppHelper.currentApplication(), "User: " + userId.toString(),
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (param.args[0] != MENU_DUMP) return@hookAllBefore
            logD("dump: clicked")
            val customEmojiPacks = param.thisObject.getObj("customEmojiPacks")
            val stickerSets = customEmojiPacks.getObj("stickerSets") as List<*>
            val str = StringBuilder()
            stickerSets.firstOrNull()?.let { tlMessagesStickerSet ->
                val set = tlMessagesStickerSet.getObj("set")
                val title = set.getObj("title")
                val id = set.getObj("id")
                val shortName = set.getObj("short_name")
                // logD("dump: $title $id $shortName")
                str.append("title=")
                    .append(title)
                    .append("\nid=")
                    .append(id)
                    .append("\nshortName=")
                    .append(shortName)

                val documents = tlMessagesStickerSet.getObjAs<List<*>>(
                    "documents"
                )
                documents.forEachIndexed { i, doc ->
                    val id = doc.getObj("id")
                    val alt = doc.getObjAs<List<*>>(
                        "attributes"
                    ).firstOrNull {
                        customEmojiClass.isInstance(it)
                    }?.getObjAsN<String>("alt")
                    // logD("dump: $i id=$id alt=$alt")
                    val altUnicode = alt?.firstUnicodeChar()
                    str.append("\n$i=$id:$altUnicode")
                }

                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", str.toString()))
            }
        }

        ClipboardManager::class.java.hookAllAfter("getPrimaryClip") { param ->
            Regex.escape("")
            Regex.escapeReplacement("")
            // logD("afterHookedMethod: getPrimaryClip")
            val result = param.result as? ClipData ?: return@hookAllAfter
            val item = result.getItemAt(0)
            val origText = item.text
            val newText = StringBuilder()
            var pos = 0
            // logD("afterHookedMethod: $origText")
            val mp = emotionMap
            while (true) {
                val r = mp.regex.find(origText, pos) ?: break
                val kw = r.value
                val replacement = mp.map[kw]?.let {
                    "<animated-emoji data-document-id=\"${it.first}\">&#${it.second.firstUnicodeChar()};</animated-emoji>"
                } ?: kw.toHtml()
                newText.append(origText.substring(pos until r.range.first).toHtml())
                newText.append(replacement)
                pos = r.range.last + 1
                // logD("afterHookedMethod: replaced=$newText")
            }
            if (pos != 0) {
                if (pos < origText.length) newText.append(origText.substring(pos).toHtml())
                // logD("replace: $newText")
                param.result = ClipData.newHtmlText(
                    "",
                    newText,
                    newText.toString()
                )
            }
        }

        val stickersAlert = findClass("org.telegram.ui.Components.StickersAlert")

        // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/StickersAlert.java#L1485
        stickersAlert.hookAllAfter("init") { param ->
            val optionsButton = param.thisObject.getObj("optionsButton") ?: return@hookAllAfter
            optionsButton.call("addSubItem", MENU_GET_PROFILE, "Profile of admin")
        }

        stickersAlert.hookAllBefore("onSubItemClick") { param ->
            if (param.args[0] == MENU_GET_PROFILE) {
                val stickerSet = param.thisObject.getObj("stickerSet")
                val id = stickerSet.getObj("set").getObj("id") as Long
                var userId = id.shr(32)
                if (id.shr(16).and(0xff) == 0x3fL) {
                    userId = userId.or(0x80000000L)
                }
                if (id.shr(24).and(0xff) != 0L) {
                    userId += 0x100000000L
                }
                val parentFragment = param.thisObject.getObj("parentFragment")
                if (parentFragment != null) {
                    val user = parentFragment.call("getMessagesController").call("getUser", userId)
                    val currentAccount = param.thisObject.getObj("currentAccount")
                    if (user != null) {
                        messagesController.callS("getInstance", currentAccount)
                            .call("openChatOrProfileWith", user, null, parentFragment, 0, false)
                        return@hookAllBefore
                    }
                }
                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", userId.toString()))
                Toast.makeText(
                    AndroidAppHelper.currentApplication(), "User: " + userId.toString(),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }.onFailure {
        logE("emojiHandler: ", it)
    }

    private fun hookEmojiManage() = runCatching {
        val chatActivityEnterView = findClass(
            "org.telegram.ui.Components.ChatActivityEnterView"
        )

        val cst = chatActivityEnterView.declaredConstructors.maxBy { it.parameterCount }!!
            .also { it.isAccessible = true }
        cst.hookAfter { param ->
            param.thisObject.getObjAs<View>("emojiButton")
                .setOnLongClickListener { v ->
                    Toast.makeText(v.context, "choose an emotion map json file", Toast.LENGTH_SHORT)
                        .show()
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

        Activity::class.java.hookAllAfter("dispatchActivityResult") { param ->
            if (param.args[1] == 114514) {
                // logD("dispatchActivityResult: " + param.args[3])
                val ctx = param.thisObject as Activity
                (param.args[3] as? Intent)?.data?.let { url ->
                    ctx.contentResolver.openInputStream(url)?.let {
                        val text = it.readBytes().toString(Charsets.UTF_8)
                        val mp = try {
                            loadEmotionMap(text)
                        } catch (t: Throwable) {
                            logE("loadEmotionMap: ", t)
                            Toast.makeText(ctx, "load failed: $t", Toast.LENGTH_LONG).show()
                            return@hookAllAfter
                        }
                        synchronized(this@TelegramHandler) {
                            _emotionMap = mp
                        }
                        ctx.getEmotionMapFile().writeText(text)
                        Toast.makeText(ctx, "load success", Toast.LENGTH_SHORT).show()
                        return@hookAllAfter
                    }
                }
                Toast.makeText(ctx, "no file provided", Toast.LENGTH_SHORT).show()
            }
        }
    }.onFailure {
        logE("hookEmojiManage: ", it)
    }

    // 这构式逻辑谁写的？
    // https://github.com/DrKLO/Telegram/blob/eee720ef5e48e1c434f4c5a83698dc4ada34aaa9/TMessagesProj/src/main/java/org/telegram/messenger/browser/Browser.java#L391
    private fun hookHasAppToOpen() = runCatching {
        // TODO: FIX
        findClass("org.telegram.messenger.browser.Browser")
            .hookAllConstant("hasAppToOpen", true)
    }.onFailure {
        logE("hookHasAppToOpen: ", it)
    }

    private fun hookDefaultSearchTab() = runCatching {
        val chatActivity = findClass("org.telegram.ui.ChatActivity")
        val viewPagerFixedTabsView =
            findClass("org.telegram.ui.Components.ViewPagerFixed\$TabsView")
        val inOpenHashTagSearch = ThreadLocal<Boolean>()
        chatActivity.hook(
            "openHashtagSearch", String::class.java, java.lang.Boolean.TYPE,
            before = { param ->
                inOpenHashTagSearch.set(true)
            },
            after = { param ->
                inOpenHashTagSearch.set(false)
                param.thisObject.setObj("defaultSearchPage", 0)
            }
        )
        viewPagerFixedTabsView.hookBefore(
            "scrollToTab",
            Integer.TYPE, Integer.TYPE
        ) { param ->
            if (inOpenHashTagSearch.get() == true) {
                if (param.thisObject.call("getCurrentPosition") != 0) {
                    param.args[0] = 0
                    param.args[1] = 0
                } else {
                    param.result = null
                }
            }
        }
    }

    private fun hookSetPosition() = runCatching {
        val caall = findClass("org.telegram.ui.Components.ChatAttachAlertLocationLayout")
        caall.hookAllCAfter { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    AlertDialog.Builder(ctx)
                        .setTitle("latitude,longitude")
                        .setView(et)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val l = et.text.toString().split(",", limit = 2)
                            if (l.size == 2) {
                                val la = l[0].trim().toDoubleOrNull()
                                val lo = l[1].trim().toDoubleOrNull()
                                if (la != null && lo != null) {
                                    self.call("resetMapPosition", la, lo)
                                    return@setPositiveButton
                                }
                            }
                            Toast.makeText(
                                ctx,
                                "wrong position",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        }

        val la = findClass("org.telegram.ui.LocationActivity")
        la.hookAfter("createView", Context::class.java) { param ->
            val self = param.thisObject
            param.thisObject.getObjAs<ImageView>("locationButton").run {
                setOnLongClickListener {
                    val ctx = it.context
                    val et = EditText(ctx)
                    AlertDialog.Builder(ctx)
                        .setTitle("latitude,longitude")
                        .setView(et)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val l = et.text.toString().split(",", limit = 2)
                            if (l.size == 2) {
                                val la = l[0].trim().toDoubleOrNull()
                                val lo = l[1].trim().toDoubleOrNull()
                                if (la != null && lo != null) {
                                    self.call(
                                        "positionMarker",
                                        Location(null).apply {
                                            latitude = la
                                            longitude = lo
                                        })
                                    return@setPositiveButton
                                }
                            }
                            Toast.makeText(
                                ctx,
                                "wrong position",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            }
        }
    }

    private fun hookAvatarPagerScrollToCurrent() = runCatching {
        val pgvClass = findClass("org.telegram.ui.Components.ProfileGalleryView")
        pgvClass.hookAllBefore("resetCurrentItem") { param ->
            if (!param.thisObject.javaClass.name.startsWith("org.telegram.ui.ProfileActivity")) return@hookAllBefore
            val pa = param.thisObject.getObj("this$0")
            val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return@hookAllBefore
            val id = currentPhoto.getObjAs<Long>("id")
            val photos = param.thisObject.getObjAs<List<*>>("photos")
            val idx = photos.indexOfFirst {
                it?.getObjAs<Long>("id") == id
            }
            if (idx == -1) return@hookAllBefore
            var exactIdx = 0
            // I know it's dumb, but ...
            // https://github.com/DrKLO/Telegram/blob/289c4625035feafbfac355eb01591b726894a623/TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileGalleryView.java#L1502
            while (param.thisObject.call("getRealPosition", exactIdx) != idx) {
                exactIdx++
                // I believe no one can set over 300 photos
                if (exactIdx > 300) return@hookAllBefore
            }
            logD("current photo idx $idx real $exactIdx")
            param.thisObject.call("setCurrentItem", exactIdx, false)
            param.result = null
        }
        // fix wrong transition image
        val inSetFgImg = ThreadLocal<Boolean>()
        val paClass = findClass("org.telegram.ui.ProfileActivity")
        paClass.hookAll(
            "setForegroundImage",
            before = { param ->
                if (param.args[0] == false) {
                    inSetFgImg.set(true)
                }
            },
            after = { param ->
                inSetFgImg.set(false)
            }
        )
        pgvClass.hookAllBefore("getImageLocation") { param ->
            if (inSetFgImg.get() == true) {
                val pa = param.thisObject.getObj("this$0")
                val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                    ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return@hookAllBefore
                val id = currentPhoto.getObjAs<Long>("id")
                val photos = param.thisObject.getObjAs<List<*>>("photos")
                val idx = photos.indexOfFirst {
                    it?.getObjAs<Long>("id") == id
                }
                if (idx == -1) return@hookAllBefore
                param.args[0] = idx
                logD("fix fg img idx")
            }
        }
        /*
        val currentExactIdx = ThreadLocal<Int?>()
        XposedBridge.hookAllMethods(
            pgvClass, "setAnimatedFileMaybe", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!param.thisObject.javaClass.name.startsWith("org.telegram.ui.ProfileActivity")) return
                    val pa = param.thisObject.getObj("this$0")
                    val currentPhoto = pa.getObj("userInfo")?.getObj("profile_photo")
                        ?: pa.getObj("chatInfo")?.getObj("chat_photo") ?: return
                    val id = currentPhoto.getObjAs<Long>("id")
                    val photos = param.thisObject.getObjAs<List<*>>("photos")
                    val idx = photos.indexOfFirst {
                        it?.getObjAs<Long>("id") == id
                    }
                    if (idx == -1) return
                    var exactIdx = 0
                    // I know it's dumb, but ...
                    // https://github.com/DrKLO/Telegram/blob/289c4625035feafbfac355eb01591b726894a623/TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileGalleryView.java#L1502
                    while (param.thisObject.call("getRealPosition", exactIdx) != idx) {
                        exactIdx++
                        // I believe no one can set over 300 photos
                        if (exactIdx > 300) return
                    }
                    logD("current photo for anim idx $idx real $exactIdx")
                    currentExactIdx.set(exactIdx)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    currentExactIdx.set(null)
                }
            }
        )
        val adapterClass = findClass("org.telegram.ui.Components.CircularViewPager\$Adapter")
        XposedBridge.hookAllMethods(adapterClass, "getRealPosition", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val exactIdx = currentExactIdx.get()
                logD("getRealPosition ${param.args[0]} $exactIdx")
                if (exactIdx == null) return

                if (param.args[0] != exactIdx) {
                    param.result = 114514
                } else {
                    logD("set fake position for anim")
                    param.result = 0
                }
            }
        })*/
    }.onFailure {
        logE("hookAvatarPagerScrollToCurrent", it)
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

// https://github.com/DrKLO/Telegram/blob/17067dfc6a1f69618a006b14e1741b75c64b276a/TMessagesProj/src/main/java/org/telegram/messenger/utils/CustomHtml.java#L248
private fun String.toHtml(): String = StringBuilder().apply {
    for (ch in this@toHtml) {
        when (ch) {
            ' ' -> append("&nbsp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '\n' -> append("<br>")
            else -> append(ch)
        }
    }
}.toString()
