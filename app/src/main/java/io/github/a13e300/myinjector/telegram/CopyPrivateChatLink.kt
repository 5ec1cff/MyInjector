package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE

// https://github.com/5ec1cff/TMoe/blob/1776e0ce2a23c318e3c506055ccda06d4b358dcf/app/src/main/java/cc/ioctl/tmoe/hook/func/HistoricalNewsOption.kt
class CopyPrivateChatLink : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.copyPrivateChatLink

    override fun onHook() {
        var chatActivity: Any? = null
        var isCreateMenu = false
        var addedCnt = 0

        findClass("org.telegram.ui.ChatActivity").hookAll(
            "createMenu",
            before = {
                chatActivity = it.thisObject
                isCreateMenu = true
                addedCnt = 0
            },
            after = { isCreateMenu = false }
        )

        val classRstring = findClass("org.telegram.messenger.R\$string")
        val menuCopyStr = classRstring.getObjSAs<Int>("Copy")
        val menuCopyLinkStr = classRstring.getObjSAs<Int>("CopyLink")
        val copyLinkDrawable =
            findClass("org.telegram.messenger.R\$drawable").getObjSAs<Int>("msg_link")
        val getString = findClass("org.telegram.messenger.LocaleController")
            .declaredMethods.single {
                it.name == "getString" && it.parameterTypes.size == 2
            }

        val subItemClass = findClass("org.telegram.ui.ActionBar.ActionBarMenuSubItem")

        findClass("org.telegram.ui.ActionBar.ActionBarPopupWindow\$ActionBarPopupWindowLayout")
            .declaredMethods.single {
                it.name == "addView" && it.parameterTypes.size == 1
            }
            .hookAfter { param ->
                if (!isEnabled()) return@hookAfter
                if (!isCreateMenu) return@hookAfter

                // only apply to chat with user (dialog)
                if (chatActivity.getObj("currentUser") == null) {
                    isCreateMenu = false
                    return@hookAfter
                }
                if (!subItemClass.isInstance(param.args[0])) return@hookAfter
                addedCnt += 1
                if (addedCnt != 2) return@hookAfter
                val thisObject = param.thisObject
                val linearLayout = thisObject.getObjAs<LinearLayout>("linearLayout")

                // append to copy button, or first button if copy button not exists
                var firstPos = -1
                var copyPos = -1
                val copyText = getString.invoke(null, "Copy", menuCopyStr) as String

                for (i in 0 until linearLayout.childCount) {
                    val child = linearLayout.getChildAt(i)
                    if (!subItemClass.isInstance(child)) continue
                    if (firstPos == -1) {
                        firstPos = i
                    } else {
                        val texts = child.getObjAs<TextView>("textView")
                        if (texts.text == copyText) {
                            copyPos = i
                            break
                        }
                    }
                }

                val pos = if (copyPos != -1) {
                    copyPos + 1
                } else if (firstPos != -1) {
                    firstPos + 1
                } else {
                    -1
                }

                if (pos == -1) {
                    logE("no pos found")
                    isCreateMenu = false
                }

                logD("add at $pos")

                isCreateMenu = false

                // ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), true, true, themeDelegate);
                val ctx = (thisObject as ViewGroup).context
                val themeDelegate = thisObject.getObj("resourcesProvider")

                val subItem = subItemClass.newInstAs<View>(ctx, true, true, themeDelegate)
                subItem.call(
                    "setTextAndIcon",
                    getString.invoke(
                        null,
                        "CopyLink",
                        menuCopyLinkStr
                    ),
                    copyLinkDrawable
                )
                linearLayout.addView(subItem, pos)
                subItem.setOnClickListener {
                    runCatching {
                        val selectedObject = chatActivity.getObj("selectedObject")
                        val messageOwner = selectedObject.getObj("messageOwner")
                        val id = messageOwner.getObj("id")
                        val dialogId = messageOwner.getObj("dialog_id")

                        it.context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(
                                ClipData.newPlainText(
                                    "",
                                    "tg://openmessage?user_id=$dialogId&message_id=$id"
                                )
                            )

                        // dismiss
                        chatActivity?.call("processSelectedOption", 999)
                        chatActivity = null
                    }.onFailure {
                        logE("error onclick", it)
                    }
                }
            }

    }
}