package io.github.a13e300.myinjector.telegram

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method

internal data class MessageMenuAction(
    val click: (View) -> Unit,
    val longClick: ((View) -> Unit)? = null,
)

// Shares the statically located popup members with CopyPrivateChatLink. Each
// feature owns its switch and captures the selected message in its menu action.
internal object MessageMenuHook {
    private data class Entry(
        val key: String,
        val title: String,
        val icon: String,
        val enabled: () -> Boolean,
        val error: (Throwable) -> Unit,
        val action: (Any, Any) -> MessageMenuAction?,
    )
    private data class MenuState(val chat: Any, var inserted: Boolean = false)
    private val entries = mutableListOf<Entry>()
    private var options: Method? = null

    fun dismiss(chat: Any) { options!!.invoke(chat, 999) }

    fun register(key: String, title: String, icon: String, enabled: () -> Boolean,
                 error: (Throwable) -> Unit, action: (Any, Any) -> MessageMenuAction?) {
        check(entries.none { it.key == key })
        entries += Entry(key, title, icon, enabled, error, action)
    }

    @Synchronized
    fun initialize(loader: ClassLoader): Class<*> {
        options?.let { return it.declaringClass }
        val members = messageMenuMembers(TelegramHandler.creator)
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(loader).apply { isAccessible = true }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(loader).apply { isAccessible = true }
        val selected = field("selected")
        val layout = field("layout")
        val provider = method("provider")
        val setText = method("setText")
        val processOption = method("options")
        val subClass = loader.loadClass(members.getValue("subClass").className)
        val constructor = subClass.declaredConstructors.single {
            it.parameterCount == 4 && it.parameterTypes.count { type -> type == java.lang.Boolean.TYPE } == 2 &&
                it.parameterTypes.contains(Context::class.java) && it.parameterTypes.contains(provider.returnType)
        }.apply { isAccessible = true }
        val drawable = loader.loadClass("org.telegram.messenger.R\$drawable")
        val activeMenu = ThreadLocal<ArrayDeque<MenuState>>()
        val installed = mutableListOf<Unhook>()
        try {
            installed += method("createMenu").hook(
                before = { param ->
                    val stack = activeMenu.get() ?: ArrayDeque<MenuState>().also { activeMenu.set(it) }
                    stack.addLast(MenuState(param.thisObject))
                },
                after = {
                    val stack = activeMenu.get()
                    stack?.removeLastOrNull()
                    if (stack.isNullOrEmpty()) activeMenu.remove()
                },
            )
            installed += method("popupAdd").hookAfter { param ->
                if (param.throwable != null || !subClass.isInstance(param.args[0])) return@hookAfter
                val state = activeMenu.get()?.lastOrNull() ?: return@hookAfter
                if (state.inserted) return@hookAfter
                state.inserted = true
                val message = selected.get(state.chat) ?: return@hookAfter
                val parent = param.thisObject as ViewGroup
                val container = layout.get(parent) as LinearLayout
                val theme = provider.invoke(state.chat)
                for (entry in entries.filter { it.enabled() }) {
                    runCatching {
                        val action = entry.action(state.chat, message) ?: return@runCatching
                        val args = constructor.parameterTypes.map {
                            when (it) {
                                Context::class.java -> parent.context
                                java.lang.Boolean.TYPE -> true
                                else -> theme
                            }
                        }.toTypedArray()
                        val item = constructor.newInstance(*args) as View
                        val icon = drawable.getObjSAs<Int>(entry.icon)
                        setText.invoke(item, *setText.parameterTypes.map {
                            val value: Any = if (it == Integer.TYPE) icon else entry.title
                            value
                        }.toTypedArray())
                        fun perform(view: View, callback: (View) -> Unit) {
                            if (!entry.enabled()) return
                            runCatching { callback(view) }.onFailure {
                                logE("${entry.key} menu action failed", it)
                                entry.error(it)
                                Toast.makeText(view.context, "操作失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        item.setOnClickListener { perform(it, action.click) }
                        action.longClick?.let { callback ->
                            item.setOnLongClickListener { perform(it, callback); true }
                        }
                        // Native listeners are attached directly to their views;
                        // adding ours leaves their option indices intact.
                        container.addView(item)
                    }.onFailure { logE("${entry.key} menu creation failed", it); entry.error(it) }
                }
            }
        } catch (t: Throwable) {
            installed.asReversed().forEach { it.unhook() }
            throw t
        }
        options = processOption
        return processOption.declaringClass
    }
}
