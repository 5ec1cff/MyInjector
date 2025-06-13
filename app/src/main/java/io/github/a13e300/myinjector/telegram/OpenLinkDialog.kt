package io.github.a13e300.myinjector.telegram

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.logD

// 自动修正一些包含了错误字符的链接，在打开时提供 fix 选项以打开修复后的链接
class OpenLinkDialog : DynHook() {
    data class FixLink(
        val pos: Int,
        val url: String,
        var openRunnable: Runnable?
    )

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.openLinkDialog

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        logD("hookOpenLinkDialog")
        val classBaseFragment = findClass("org.telegram.ui.ActionBar.BaseFragment")

        val fixLink = ThreadLocal<FixLink>()
        val regexTelegraph = Regex("^https?://telegra\\.ph")
        val escapeChars = Regex("[^!#\$&'*+\\(\\),-./:;%=\\?@_~0-9A-Za-z]")
        val classBrowser = findClass("org.telegram.messenger.browser.Browser")
        val classChatActivity = findClass("org.telegram.ui.ChatActivity")

        logD("hookOpenLinkDialog: start hook")

        classChatActivity.hookAllBefore("processExternalUrl", cond = ::isEnabled) { param ->
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
    }
}
