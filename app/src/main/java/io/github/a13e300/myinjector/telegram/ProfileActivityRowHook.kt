package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.util.SparseIntArray
import android.view.View
import android.widget.Toast
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.bridge.Unhook
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.WeakHashMap

internal data class ProfileRowContent(val value: String, val title: String)

// Implements TMoe's ProfileActivityRowHook contract with static row discovery.
// https://github.com/cinit/TMoe
internal object ProfileActivityRowHook {
    private const val EXTRA_VIEW_TYPE = 0x4d4900
    private const val EXTRA_ITEM_ID = 0x4d490000
    private data class Row(
        val id: Int,
        val enabled: () -> Boolean,
        val content: (Any) -> ProfileRowContent,
    )
    private data class InsertedRow(val position: Int, val row: Row)
    private val callbacks = mutableListOf<Row>()
    // Values must not hold their profile key, so closed pages can be collected.
    private val rows = WeakHashMap<Any, List<InsertedRow>>()
    private var fields: Map<String, Field>? = null

    fun register(id: Int, enabled: () -> Boolean, content: (Any) -> ProfileRowContent) {
        check(callbacks.none { it.id == id }) { "Duplicate profile row $id" }
        callbacks += Row(id, enabled, content)
        callbacks.sortBy { it.id }
    }

    @Synchronized
    fun initialize(loader: ClassLoader): Map<String, Field> {
        fields?.let { return it }
        val members = profileActivityRowsMembers(TelegramHandler.creator)
        fun field(key: String) = DexField(members.getValue(key).descriptor).getFieldInstance(loader).apply { isAccessible = true }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader).apply { isAccessible = true }
        val idFields = listOf("user_id", "chat_id", "topic_id").associateWith(::field)
        val rowFields = members.getValue("rowFields").descriptor.lines().map {
            DexField(it).getFieldInstance(loader).apply { isAccessible = true }
        }
        val rowCount = field("rowCount")
        val section = field("infoSectionRow")
        val adapterProfile = field("adapterProfile")
        val diffProfile = field("diffProfile")
        val holderView = field("holderView")
        val holderPosition = method("holderPosition")
        val setTextAndValue = method("setTextAndValue")
        val update = method("updateRows")
        val viewType = method("viewType")
        val createHolder = method("createHolder")
        val bind = method("bind")
        val enabled = method("isEnabled")
        val fill = method("fillPositions")
        val click = method("click")
        val profileClass = rowCount.declaringClass
        val staticClick = Modifier.isStatic(click.modifiers)
        check(!staticClick || click.parameterTypes.first() == profileClass)
        val clickPosition = click.parameterTypes.size - 3
        fun inserted(profile: Any, position: Int) = rows[profile]?.find { it.position == position }
        val installed = mutableListOf<Unhook>()
        try {
            installed += update.hookAfter { param ->
                if (param.throwable != null) return@hookAfter
                val profile = param.thisObject
                // updateRowsIds has restored the native indices. Rebuild the extra
                // rows from the current switches rather than shifting them twice.
                rows.remove(profile)
                val selected = callbacks.filter { it.enabled() }
                val layout = ProfileRowLayout(rowCount.getInt(profile), section.getInt(profile), selected.size)
                if (layout.extraCount == 0) return@hookAfter
                rowFields.forEach { f ->
                    val index = f.getInt(profile)
                    val shifted = layout.shift(index)
                    if (index != shifted) f.setInt(profile, shifted)
                }
                rowCount.setInt(profile, layout.count)
                rows[profile] = selected.mapIndexed { index, row -> InsertedRow(layout.position + index, row) }
            }
            installed += viewType.hookBefore { param ->
                val profile = adapterProfile.get(param.thisObject)!!
                if (inserted(profile, param.args[0] as Int) != null) param.result = EXTRA_VIEW_TYPE
            }
            installed += createHolder.hookBefore { param ->
                // Telegram creates its themed TextDetailCell with native type 2.
                // RecyclerView keeps EXTRA_VIEW_TYPE, avoiding reuse of phone cells.
                if (param.args[1] == EXTRA_VIEW_TYPE) param.args[1] = 2
            }
            installed += enabled.hookBefore { param ->
                val profile = adapterProfile.get(param.thisObject)!!
                val position = holderPosition.invoke(param.args[0]) as Int
                if (inserted(profile, position) != null) param.result = true
            }
            installed += bind.hookBefore { param ->
                val profile = adapterProfile.get(param.thisObject)!!
                val extra = inserted(profile, param.args[1] as Int) ?: return@hookBefore
                param.result = null
                val view = holderView.get(param.args[0]) as View
                val content = extra.row.content(profile)
                val divider = rows.getValue(profile).last() != extra
                setTextAndValue.invoke(view, content.value, content.title, divider)
                view.contentDescription = "${content.title}: ${content.value}"
            }
            installed += click.hookBefore { param ->
                val profile = if (staticClick) param.args[0] else param.thisObject
                val extra = inserted(profile, param.args[clickPosition] as Int) ?: return@hookBefore
                param.result = null
                val view = param.args[clickPosition - 1] as View
                val content = extra.row.content(profile)
                view.context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(content.title, content.value))
                Toast.makeText(view.context, "已复制", Toast.LENGTH_SHORT).show()
            }
            installed += fill.hookAfter { param ->
                if (param.throwable != null) return@hookAfter
                val profile = diffProfile.get(param.thisObject)!!
                val positions = param.args[0] as SparseIntArray
                // Positive stable IDs are required by areItemsTheSame's >= 0 check.
                rows[profile]?.forEach { positions.put(it.position, EXTRA_ITEM_ID + it.row.id) }
            }
        } catch (t: Throwable) {
            installed.asReversed().forEach { it.unhook() }
            throw t
        }
        fields = idFields
        return idFields
    }
}
