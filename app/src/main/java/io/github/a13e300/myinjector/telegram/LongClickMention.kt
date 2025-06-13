package io.github.a13e300.myinjector.telegram

import android.text.SpannableString
import android.text.Spanned
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.logE
import java.lang.reflect.Proxy

// 在 at 列表中，长按以强制使用无用户名的 at 形式
class LongClickMention : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.longClickMention

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val longClickListenerClass =
            findClass("org.telegram.ui.Components.RecyclerListView\$OnItemLongClickListener")
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        val userObjectClass = findClass("org.telegram.messenger.UserObject")
        val classURLSpanUserMention = findClass("org.telegram.ui.Components.URLSpanUserMention")
        findClass("org.telegram.ui.ChatActivity").hookAllAfter(
            "createView",
            cond = ::isEnabled
        ) { param ->
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
                    runCatching {
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
    }
}
