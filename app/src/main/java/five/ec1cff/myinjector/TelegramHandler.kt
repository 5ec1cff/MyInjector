package five.ec1cff.myinjector

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class TelegramHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "TelegramHandler"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "org.telegram.messenger" || !lpparam.processName.startsWith("org.telegram.messenger")) return
        hookOpenLinkDialog(lpparam)
    }

    data class FixLink(
        val pos: Int,
        val url: String,
        var openRunnable: Runnable?
    )

    private fun hookOpenLinkDialog(lpparam: LoadPackageParam) {
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
    }
}