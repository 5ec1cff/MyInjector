package io.github.a13e300.myinjector.xhs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.TextView
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

internal fun locateXhsCommentShare(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val noteClass = "com.xingin.entities.NoteItemBean"
    val commentClass = "com.xingin.entities.CommentCommentInfo"
    val copy = bridge.findMethod {
        matcher {
            paramTypes("android.app.Activity", noteClass, commentClass)
            returnType = "void"
            usingEqStrings("CommentCopyLink", "CommentCopyLinkManager.copy")
        }
    }.single()
    val request = bridge.findMethod {
        matcher {
            declaredClass = copy.className
            paramTypes(noteClass, commentClass, "java.lang.String")
            usingEqStrings("NOTE_COMMENT", "anchorCommentId", "commentContent")
        }
    }.single()
    val baseUrl = request.invokes.single {
        it.paramTypeNames == listOf(noteClass) && it.returnTypeName == "java.lang.String"
    }
    val commentId = request.invokes.single {
        it.className == commentClass && it.returnTypeName == "java.lang.String" && it.paramCount == 0
    }
    val noteType = request.invokes.single {
        it.className == "com.xingin.entities.MultiItemBean" && it.returnTypeName == "java.lang.String" && it.paramCount == 0
    }
    val content = request.invokes.single {
        it.paramTypeNames == listOf(commentClass) && it.returnTypeName == "java.lang.String"
    }
    val author = request.invokes.single {
        it.className == commentClass && it.returnTypeName == "com.xingin.entities.CommentCommentUser"
    }
    val authorName = request.invokes.single {
        it.className == author.returnTypeName && it.returnTypeName == "java.lang.String" && it.paramCount == 0
    }

    val menuItem = bridge.findClass {
        matcher { usingEqStrings("OperateButtonInfo(buttonText=", ", componentName=", ", clickAction=") }
    }.single()
    val menuItemConstructor = menuItem.methods.single { it.isConstructor }
    val action = copy.callers.single { it.paramTypeNames.firstOrNull() == "android.content.Context" }
    val actionInterface = action.declaredClass!!.interfaces.single().methods.single()
    val binder = actionInterface.callers.mapNotNull { it.declaredClass }
        .distinctBy { it.name }.single { cls ->
            cls.methods.any { it.returnTypeName == "android.view.View" && it.paramCount == 2 }
        }
    val bind = binder.methods.single { it.returnTypeName == "android.view.View" && it.paramCount == 2 }
    return mapOf(
        "copy" to copy.toObfsInfo(),
        "baseUrl" to baseUrl.toObfsInfo(),
        "commentId" to commentId.toObfsInfo(),
        "noteType" to noteType.toObfsInfo(),
        "content" to content.toObfsInfo(),
        "author" to author.toObfsInfo(),
        "authorName" to authorName.toObfsInfo(),
        "menuItemConstructor" to menuItemConstructor.toObfsInfo(),
        "action" to action.toObfsInfo(),
        "bind" to bind.toObfsInfo(),
    )
}

class BetterCommentShare : MyDynHook("betterShare") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.betterShare

    override fun onHook() {
        val creator = XhsHandler.creator
        val targets by lazy { locateXhsCommentShare(creator.bridge) }
        fun info(key: String) = creator.create("commentShare.$key") { targets.getValue(key) }
        fun method(key: String) = DexMethod(info(key).descriptor).getMethodInstance(classLoader)
            .apply { isAccessible = true }

        val baseUrl = method("baseUrl")
        val commentId = method("commentId")
        val noteType = method("noteType")
        val content = method("content")
        val author = method("author")
        val authorName = method("authorName")
        val contentHelper = content.declaringClass.declaredFields.single {
            it.type == content.declaringClass && Modifier.isStatic(it.modifiers)
        }.apply { isAccessible = true }.get(null)
        val longPressed = ThreadLocal<Boolean>()

        method("copy").hookBefore(cond = ::isEnabled) { param ->
            val activity = param.args[0] as Context
            val note = param.args[1]
            val comment = param.args[2]
            val source = Uri.parse(baseUrl.invoke(param.thisObject, note) as String)
            val token = source.getQueryParameter("xsec_token")?.takeIf { it.isNotEmpty() }
                ?: note.getObjAsN<String>("xsecToken")
            val url = buildXhsCommentUrl(
                note.getObjAs<String>("id"), commentId.invoke(comment) as String,
                noteType.invoke(note) as String, token,
            )
            val user = author.invoke(comment)
            val text = buildXhsCommentText(
                url, user?.let { authorName.invoke(it) as? String },
                if (longPressed.get() == true) content.invoke(contentHelper, comment) as String else null,
            )
            activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newHtmlText("", text.text, text.html))
            // 在分享请求前返回，不请求短链，也不让异步回调覆盖剪贴板
            param.result = null
        }

        fun enableLongCopy(view: View) {
            view.setOnLongClickListener {
                if (!isEnabled()) return@setOnLongClickListener false
                longPressed.set(true)
                try {
                    // 沿用菜单点击流程关闭面板，并把长按状态同步传给复制入口
                    it.callOnClick()
                } finally {
                    longPressed.remove()
                }
            }
        }

        // 旧菜单先创建按钮数据，再填充行视图；记录宿主提供的本地化标题
        var copyLinkTitle: String? = null
        DexMethod(info("menuItemConstructor").descriptor).getConstructorInstance(classLoader)
            .hookAfter(cond = ::isEnabled) { param ->
                if (param.args[2] == "copy_comment_link") copyLinkTitle = param.args[0] as String
            }
        findClass("com.xingin.matrix.comment.databinding.CommentOperateItemLayoutBinding")
            .hookAllCAfter(cond = ::isEnabled) { param ->
                val row = param.args[0] as View
                val title = param.args.last() as TextView
                row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        view.removeOnAttachStateChangeListener(this)
                        if (copyLinkTitle != null && title.text.toString() == copyLinkTitle) enableLongCopy(view)
                    }
                    override fun onViewDetachedFromWindow(view: View) = Unit
                })
            }

        // 新菜单的绑定器直接持有操作对象，只给复制链接这一项添加长按
        val actionClass = findClass(info("action").className)
        val bind = method("bind")
        val actionField = bind.declaringClass.declaredFields.single {
            it.type.isAssignableFrom(actionClass)
        }.apply { isAccessible = true }
        bind.hookAfter(cond = ::isEnabled) { param ->
            if (actionClass.isInstance(actionField.get(param.thisObject))) {
                enableLongCopy(param.result as View)
            }
        }
    }
}
