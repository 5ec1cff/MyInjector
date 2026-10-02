package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

// https://github.com/5ec1cff/TMoe/blob/1776e0ce2a23c318e3c506055ccda06d4b358dcf/app/src/main/java/cc/ioctl/tmoe/hook/func/HistoricalNewsOption.kt
class CopyPrivateChatLink : MyDynHook("copyPrivateChatLink") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.copyPrivateChatLink

    private data class MenuState(val chat: Any, var added: Int = 0)

    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy { findCopyPrivateChatLink(creator.bridge) }
        val keys = listOf("createMenu", "options", "selected", "user", "popupAdd", "layout", "provider", "subClass", "subText", "setText")
        val members = keys.associateWith { key -> creator.create("CopyPrivateChatLink.$key") {
            val descriptor = found.getValue(key)
            when {
                key == "subClass" -> ObfsInfo(descriptor, "")
                key in listOf("selected", "user", "layout") -> {
                    val f = DexField(descriptor); ObfsInfo(f.className, f.name, descriptor)
                }
                else -> { val m = DexMethod(descriptor); ObfsInfo(m.className, m.name, descriptor) }
            }
        } }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        val currentUser = field("user")
        val selectedObject = field("selected")
        val layoutField = field("layout")
        val provider = method("provider")
        val subText = method("subText")
        val setText = method("setText")
        val processOption = method("options")
        val subItemClass = findClass(members.getValue("subClass").className)
        // R8 reorders the ResourcesProvider parameter ahead of the two booleans.
        val constructor = subItemClass.declaredConstructors.single {
            it.parameterCount == 4 && it.parameterTypes.count { t -> t == java.lang.Boolean.TYPE } == 2 &&
                it.parameterTypes.contains(android.content.Context::class.java) &&
                it.parameterTypes.contains(provider.returnType)
        }.apply { isAccessible = true }
        val activeMenu = ThreadLocal<MenuState>()
        method("createMenu").hook(cond = ::isEnabled,
            before = { activeMenu.set(MenuState(it.thisObject)) },
            after = { activeMenu.remove() })
        val strings = findClass("org.telegram.messenger.R\$string")
        val copyId = strings.getObjSAs<Int>("Copy")
        val copyLinkId = strings.getObjSAs<Int>("CopyLink")
        val linkIcon = findClass("org.telegram.messenger.R\$drawable").getObjSAs<Int>("msg_link")
        val getString = findClass("org.telegram.messenger.LocaleController").getDeclaredMethod(
            "getString", String::class.java, Integer.TYPE)
        method("popupAdd").hookAfter(cond = ::isEnabled) { param ->
            val state = activeMenu.get() ?: return@hookAfter
            if (currentUser.get(state.chat) == null) {
                activeMenu.remove()
                return@hookAfter
            }
            if (!subItemClass.isInstance(param.args[0])) return@hookAfter
            if (++state.added != 2) return@hookAfter
            activeMenu.remove()
            val layout = layoutField.get(param.thisObject) as LinearLayout
            val copyText = getString.invoke(null, "Copy", copyId) as String
            var first = -1
            var copy = -1
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (!subItemClass.isInstance(child)) continue
                if (first == -1) first = i
                if ((subText.invoke(child) as TextView).text == copyText) {
                    copy = i
                    break
                }
            }
            val position = if (copy != -1) copy + 1 else if (first != -1) first + 1 else return@hookAfter
            val context = (param.thisObject as ViewGroup).context
            val resources = provider.invoke(state.chat)
            val args = constructor.parameterTypes.map {
                when (it) {
                    android.content.Context::class.java -> context
                    java.lang.Boolean.TYPE -> true
                    else -> resources
                }
            }.toTypedArray()
            val item = constructor.newInstance(*args) as View
            val label = getString.invoke(null, "CopyLink", copyLinkId)
            setText.invoke(item, *setText.parameterTypes.map {
                if (it == Integer.TYPE) linkIcon else label
            }.toTypedArray())
            layout.addView(item, position)
            item.setOnClickListener { view ->
                runCatching {
                    val message = selectedObject.get(state.chat).getObj("messageOwner")
                    val id = message.getObj("id")
                    val dialogId = message.getObj("dialog_id")
                    view.context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("", "tg://openmessage?user_id=$dialogId&message_id=$id"))
                    processOption.invoke(state.chat, 999)
                }.onFailure { logE("error onclick", it) }
            }
        }
    }
}

private fun findCopyPrivateChatLink(bridge: DexKitBridge): Map<String, String> {
    val createMenu = bridge.findMethod { matcher { usingStrings("open menu msg_id="); returnType("boolean") } }.single()
    val options = bridge.findMethod { matcher {
        declaredClass(createMenu.className); paramTypes("int"); returnType("void")
        usingEqStrings("tel:", "canSelectTopics", "messagesCount")
    } }.single()
    // processSelectedOption starts by returning if selectedObject == null.
    val selected = options.usingFields.first().field
    check(selected.className == createMenu.className && selected.typeName == "org.telegram.messenger.MessageObject")
    val user = bridge.findField { matcher { declaredClass(createMenu.className); type("org.telegram.tgnet.TLRPC\$User"); addReadMethod { descriptor(createMenu.descriptor) } } }.single()
    val popup = createMenu.invokes.distinctBy { it.descriptor }.single {
        it.name == "addView" && it.paramTypeNames == listOf("android.view.View") &&
            it.declaredClass?.superClass?.name == "android.widget.FrameLayout"
    }
    val subConstructor = createMenu.invokes.distinctBy { it.descriptor }.single {
        it.isConstructor && it.paramCount == 4 && it.paramTypeNames.count { t -> t == "boolean" } == 2
    }
    val sub = subConstructor.declaredClass!!
    val text = sub.methods.single { it.paramCount == 0 && (it.returnTypeName == "android.widget.TextView" || it.returnType?.superClass?.name == "android.widget.TextView") }
    val setText = sub.methods.single { it.paramTypeNames.toSet() == setOf("int", "java.lang.CharSequence") && it.paramCount == 2 }
    val provider = subConstructor.paramTypeNames.single { it !in listOf("android.content.Context", "boolean") }
    fun field(c: String, type: String) = bridge.findField { matcher { declaredClass(c); type(type) } }.single()
    return mapOf("createMenu" to createMenu.descriptor, "options" to options.descriptor,
        "selected" to selected.descriptor, "user" to user.descriptor, "popupAdd" to popup.descriptor,
        "layout" to popup.usingFields.single().field.descriptor,
        "provider" to bridge.findMethod { matcher { declaredClass(createMenu.className); paramTypes(); returnType(provider) } }.single().descriptor, "subClass" to sub.name,
        "subText" to text.descriptor, "setText" to setText.descriptor)
}
