package io.github.a13e300.myinjector.telegram

import android.view.View
import android.view.ViewGroup
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

// Feature ported from https://github.com/cinit/TMoe (AddInfoContainer).
class AddInfoContainer : MyDynHook("addInfoContainer") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.addInfoContainer

    override fun onHook() {
        val profile = profileMenuMembers(TelegramHandler.creator)
        val members = infoContainerMembers(TelegramHandler.creator, profile.getValue("menu").descriptor)
        fun method(descriptor: String) = DexMethod(descriptor).getMethodInstance(classLoader).apply { isAccessible = true }
        fun field(descriptor: String) = DexField(descriptor).getFieldInstance(classLoader).apply { isAccessible = true }
        val methods = infoContainerMethodKeys.associateWith { method(members.getValue(it).descriptor) }
        val fields = (members.keys - infoContainerMethodKeys.toSet() - "rowFields")
            .associateWith { field(members.getValue(it).descriptor) }
        val rowFields = members.getValue("rowFields").descriptor.lines().map(::field)
        val hiddenRows = listOf("recentActionsRow", "addNewSectionRow", "participantsDivider2Row", "removedUsersRow").map { fields.getValue(it) }.toSet()
        val currentChat = field(profile.getValue("currentChat").descriptor)
        val chatObject = findClass("org.telegram.messenger.ChatObject")
        val drawables = findClass("org.telegram.messenger.R\$drawable")
        val infoIcon = drawables.getObjSAs<Int>("msg_info")
        val adminIcon = drawables.getObjSAs<Int>("msg_admins")
        val strings = findClass("org.telegram.messenger.R\$string")
        val locale = findClass("org.telegram.messenger.LocaleController")
        val adminTitle = locale.callS("getString", "ChannelAdministrators", strings.getObjSAs<Int>("ChannelAdministrators"))
        val setAdmin = methods.getValue("setAdminText")
        check(setAdmin.parameterTypes.count { CharSequence::class.java.isAssignableFrom(it) } == 2)
        val adminArgs = setAdmin.parameterTypes.mapIndexed { index, type ->
            when {
                CharSequence::class.java.isAssignableFrom(type) -> if (index == 0) adminTitle else "**"
                type == Integer.TYPE -> adminIcon
                type == java.lang.Boolean.TYPE -> index == setAdmin.parameterCount - 1
                else -> error("Unexpected TextCell parameter $type")
            }
        }.toTypedArray()
        fun hasAdmin(chat: Any?) = chat != null && chatObject.callS("hasAdminRights", chat) == true
        fun isChannel(chat: Any?) = chat != null && chatObject.callS("isChannel", chat) == true
        fun showInfo(activity: Any) {
            val chat = fields.getValue("editChat").get(activity) ?: return
            val admin = hasAdmin(chat)
            // Native cells are populated by updateFields before createView ends.
            // Some bot settings use this same class but have no currentChat.
            for (key in listOf("blockCell", "infoContainer", "settingsTopSection")) {
                (fields.getValue(key).get(activity) as View?)?.visibility = View.VISIBLE
            }
            if (!admin) {
                val log = fields.getValue("logCell")
                val view = log.get(activity) as View?
                (view?.parent as ViewGroup?)?.removeView(view)
                log.set(activity, null)
                fields.getValue("adminCell").get(activity)?.let { setAdmin.invoke(it, *adminArgs) }
            }
        }
        fun guarded(action: () -> Unit) {
            runCatching(action).onFailure { logE("AddInfoContainer failed", it); onHookError(it) }
        }
        fun hideManagementButtons(activity: Any, chat: Any?) {
            if (chat == null || hasAdmin(chat) || fields.getValue("usersType").getInt(activity) !in listOf(0, 3)) return
            for (key in listOf("searchItem", "doneItem")) {
                (fields.getValue(key).get(activity) as View?)?.visibility = View.GONE
            }
        }
        val installed = mutableListOf<Unhook>()
        try {
            installed += method(profile.getValue("menu").descriptor).hookAfter(cond = ::isEnabled) { param ->
                if (param.throwable != null) return@hookAfter
                guarded {
                    val activity = param.thisObject
                    val chat = currentChat.get(activity) ?: return@guarded
                    // Topic profiles use the same button to edit the topic.
                    if ((fields.getValue("profileTopic").get(activity) as Number).toLong() != 0L) return@guarded
                    if (!hasAdmin(chat) && chat.getObj("megagroup") != true) return@guarded
                    val visible = fields.getValue("editVisible")
                    if (visible.getBoolean(activity)) return@guarded
                    val item = fields.getValue("editItem").get(activity) as View? ?: return@guarded
                    visible.setBoolean(activity, true)
                    methods.getValue("setIcon").invoke(item, infoIcon)
                    item.visibility = View.VISIBLE
                    item.contentDescription = "查看群信息"
                }
            }
            for (key in listOf("editCreate", "updateFields")) {
                installed += methods.getValue(key).hookAfter(cond = ::isEnabled) { param ->
                    if (param.throwable == null) guarded { showInfo(param.thisObject) }
                }
            }
            installed += methods.getValue("usersCreate").hookAfter(cond = ::isEnabled) { param ->
                if (param.throwable != null) return@hookAfter
                guarded {
                    val activity = param.thisObject
                    hideManagementButtons(activity, fields.getValue("usersChat").get(activity))
                }
            }
            installed += methods.getValue("updateRows").hookAfter(cond = ::isEnabled) { param ->
                if (param.throwable != null) return@hookAfter
                guarded {
                    val activity = param.thisObject
                    val chat = fields.getValue("usersChat").get(activity) ?: return@guarded
                    // Async participant loads can show search again before
                    // rebuilding rows; keep the query-only controls consistent.
                    hideManagementButtons(activity, chat)
                    if (isChannel(chat) && hasAdmin(chat)) return@guarded
                    val count = fields.getValue("rowCount")
                    val removal = TelegramRowRemoval(count.getInt(activity), hiddenRows.map { it.getInt(activity) })
                    rowFields.forEach { field ->
                        field.setInt(activity, if (field in hiddenRows) -1 else removal.shift(field.getInt(activity)))
                    }
                    count.setInt(activity, removal.count)
                }
            }
        } catch (t: Throwable) {
            installed.asReversed().forEach { it.unhook() }
            throw t
        }
    }
}
