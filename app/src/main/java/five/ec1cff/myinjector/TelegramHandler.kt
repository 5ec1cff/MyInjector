package five.ec1cff.myinjector

import android.app.AndroidAppHelper
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.res.XModuleResources
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.WeakHashMap

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
    }
    private lateinit var moduleRes: XModuleResources

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "org.telegram.messenger" || !lpparam.processName.startsWith("org.telegram.messenger")) return
        moduleRes = XModuleResources.createInstance(Entry.modulePath, null)
        hookOpenLinkDialog(lpparam)
        hookMutualContact(lpparam)
        hookContactPermission(lpparam)
        // hookUserProfileShowId(lpparam)
        hookAutoCheckDeleteMessagesOptionAlso(lpparam)
        hookAutoUncheckSharePhoneNum(lpparam)
    }

    private fun hookAutoUncheckSharePhoneNum(lpparam: LoadPackageParam) = runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("org.telegram.ui.ContactAddActivity", lpparam.classLoader),
            "createView",
            object : XC_MethodHook() {
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
                object : XC_MethodHook() {
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
                object : XC_MethodHook() {
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
                        imageView.isVisible = true
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
}