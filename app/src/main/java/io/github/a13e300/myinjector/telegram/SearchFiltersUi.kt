package io.github.a13e300.myinjector.telegram

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.time.LocalDate
import java.time.ZoneId
import java.util.WeakHashMap

// Keep the native calendar listener so disabling the feature restores its action.
internal class SearchFiltersUi(
    chatClass: Class<*>,
    private val enabled: () -> Boolean,
    private val options: (Any) -> MessageSearchFilters?,
    private val apply: (Any, MessageSearchFilters, LocalDate?) -> Unit,
    private val error: (Throwable) -> Unit,
) {
    private val buttons = WeakHashMap<View, WeakReference<Any>>()
    private val chats = WeakHashMap<Any, Unit>()
    private val loader = requireNotNull(chatClass.classLoader)
    private val androidUtilities = loader.loadClass("org.telegram.messenger.AndroidUtilities")
    private val creator = TelegramHandler.creator
    private val found by lazy { findSearchControls(creator.bridge, chatClass.name) }
    private val members = listOf("create", "calendar", "visible", "showList", "sender").associateWith { key ->
        creator.create("SearchControls.$key") {
            val value = found.getValue(key)
            if (key in listOf("calendar", "visible")) DexField(value).let { ObfsInfo(it.className, it.name, value) }
            else DexMethod(value).let { ObfsInfo(it.className, it.name, value) }
        }
    }
    private val calendar = DexField(members.getValue("calendar").descriptor).getFieldInstance(loader).apply { isAccessible = true }
    private val visible = DexField(members.getValue("visible").descriptor).getFieldInstance(loader).apply { isAccessible = true }
    private val showList = DexMethod(members.getValue("showList").descriptor).getMethodInstance(loader).apply { isAccessible = true }

    init {
        DexMethod(members.getValue("create").descriptor).getMethodInstance(loader).apply { isAccessible = true }.hookAfter { p ->
            (calendar.get(p.thisObject) as? View)?.let { button ->
                buttons[button] = WeakReference(p.thisObject)
                chats[p.thisObject] = Unit
            }
        }
        DexMethod(members.getValue("sender").descriptor).getMethodInstance(loader).apply { isAccessible = true }.hookAfter { p ->
            // Native sender selection hides the calendar until search closes.
            if (enabled() && isSearching(p.thisObject)) (calendar.get(p.thisObject) as? View)?.visibility = View.VISIBLE
        }
        View::class.java.getDeclaredMethod("performClick").hookBefore { p ->
            if (enabled()) {
                val button = p.thisObject as View
                val chat = buttons[button]?.get()
                if (chat != null && isSearching(chat) && options(chat) != null) {
                    p.result = true
                    runCatching { showMenu(button, chat) }.onFailure(error)
                }
            }
        }
    }

    fun chatFor(media: Any, guid: Int): Any? = chats.keys.firstOrNull {
        it.call("getClassGuid") == guid && it.call("getMediaDataController") === media
    }
    fun isSearching(chat: Any) = visible.getBoolean(chat)
    fun showResults(chat: Any) { showList.invoke(chat, true) }
    fun forget(chat: Any) { chats.remove(chat) }

    private fun dialogContext(button: View): ContextThemeWrapper {
        val context = ContextThemeWrapper(button.context, 0)
        // Telegram's theme can differ from Android's night mode.
        androidUtilities.callS("checkAndroidTheme", context, true)
        val dialogTheme = TypedValue()
        check(context.theme.resolveAttribute(android.R.attr.alertDialogTheme, dialogTheme, true))
        // Do not copy the host's light popup text style into the dark theme.
        val theme = button.resources.newTheme().apply { applyStyle(dialogTheme.resourceId, true) }
        return ContextThemeWrapper(button.context, theme)
    }

    private fun showMenu(button: View, chat: Any) {
        val current = options(chat) ?: return
        button.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(button.windowToken, 0)
        PopupMenu(dialogContext(button), button).apply {
            menu.add(0, 0, 0, "消息类型：${current.type.title}")
            menu.add(0, 1, 1, "时间范围：${current.dates.title}")
            menu.add(0, 2, 2, "跳到日期")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> chooseType(button, chat, current)
                    1 -> chooseRange(button, chat, current)
                    2 -> pickDate(button, "跳到日期", LocalDate.now(), current.dates.start, current.dates.end) { date -> update(chat, current, date) }
                }
                true
            }
            show()
        }
    }

    private fun chooseType(button: View, chat: Any, current: MessageSearchFilters) {
        val types = SearchMessageType.values()
        AlertDialog.Builder(dialogContext(button)).setTitle("消息类型")
            .setSingleChoiceItems(types.map { it.title }.toTypedArray(), current.type.ordinal) { dialog, index ->
                dialog.dismiss()
                if (types[index] != current.type) update(chat, current.copy(type = types[index]))
            }.setNegativeButton("取消", null).show()
    }

    private fun chooseRange(button: View, chat: Any, current: MessageSearchFilters) {
        AlertDialog.Builder(dialogContext(button)).setTitle("时间范围：${current.dates.title}")
            .setItems(arrayOf("设置起止日期", "仅设置开始日期", "仅设置结束日期", "清除时间范围")) { _, which ->
                when (which) {
                    0 -> pickDate(button, "开始日期", current.dates.start ?: LocalDate.now()) { start ->
                        pickDate(button, "结束日期（包含当天）", current.dates.end?.coerceAtLeast(start) ?: start, start) { end ->
                            update(chat, current.copy(dates = SearchDateRange(start, end)))
                        }
                    }
                    1 -> pickDate(button, "开始日期", current.dates.start ?: LocalDate.now()) { start ->
                        update(chat, current.copy(dates = SearchDateRange(start, null)))
                    }
                    2 -> pickDate(button, "结束日期（包含当天）", current.dates.end ?: LocalDate.now()) { end ->
                        update(chat, current.copy(dates = SearchDateRange(null, end)))
                    }
                    3 -> update(chat, current.copy(dates = SearchDateRange()))
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun pickDate(button: View, title: String, initial: LocalDate, min: LocalDate? = null,
        max: LocalDate? = null, done: (LocalDate) -> Unit) {
        val date = initial.coerceIn(min ?: LocalDate.of(1970, 1, 2), max ?: LocalDate.now())
        DatePickerDialog(dialogContext(button), { _, year, month, day -> done(LocalDate.of(year, month + 1, day)) },
            date.year, date.monthValue - 1, date.dayOfMonth).apply {
            setTitle(title)
            datePicker.minDate = (min ?: LocalDate.of(1970, 1, 2)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = max?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
            show()
        }
    }

    private fun update(chat: Any, filters: MessageSearchFilters, date: LocalDate? = null) {
        if (!enabled() || !isSearching(chat) || options(chat) == null) return
        runCatching { apply(chat, filters, date) }.onFailure {
            error(it)
            (calendar.get(chat) as? View)?.let { button -> Toast.makeText(button.context, "搜索条件更新失败", Toast.LENGTH_SHORT).show() }
        }
    }
}
