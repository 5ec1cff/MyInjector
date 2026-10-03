package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : MyDynHook("autoUncheckSharePhoneNumber") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoUncheckSharePhoneNumber

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        val contactAddActivity = creator.create("ContactAddActivity") { bridge ->
            bridge.findClass {
                matcher {
                    usingEqStrings(
                        "first_name_card",
                        "last_name_card",
                        "addContact",
                        "dialog_bar_exception"
                    )
                }
            }.single().toObfsInfo()
        }

        val checkShare = creator.create("ContactAddActivityCheckShare") { bridge ->
            bridge.findField {
                matcher {
                    addWriteMethod {
                        declaredClass(contactAddActivity.className)
                        name("createView")
                    }
                    type("boolean")
                }
            }.single().toObfsInfo()
        }

        // inlined and merged with other Callback2
        val contactAddActivityFillItems = creator.create("ContactAddActivityFillItems") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings("MobileVisibleInfo")
                }
            }.single().toObfsInfo()
        }

        val shareItem by lazy {
            val bridge = creator.bridge
            val fill = bridge.getMethodData(contactAddActivityFillItems.descriptor)!!
            if (fill.usingFields.none { it.usingType == FieldUsingType.Write && it.field.descriptor == checkShare.descriptor }) {
                // Telegram only sets the default in createView. Some forks
                // overwrite it in fillItems, so also clear the generated row.
                mapOf("idField" to ObfsInfo("", ""), "checkedField" to ObfsInfo("", ""), "id" to ObfsInfo("", ""))
            } else {
                val calls = scanTelegramDex(bridge, fill).calls
                val checked = calls.single {
                    it.method.paramTypeNames == listOf("boolean") && it.args.single()?.field?.descriptor == checkShare.descriptor
                }
                val factory = calls.last {
                    it.offset < checked.offset && it.method.returnTypeName == checked.method.className &&
                        it.args.any { arg -> arg?.resource == "AddContactShareNumber" }
                }
                fun writtenField(method: MethodData, type: String) = method.usingFields
                    .filter { it.usingType == FieldUsingType.Write }.map { it.field }.distinctBy { it.descriptor }
                    .single { it.className == checked.method.className && it.typeName == type }.toObfsInfo()
                mapOf(
                    "idField" to writtenField(factory.method, "int"),
                    "checkedField" to writtenField(checked.method, "boolean"),
                    "id" to ObfsInfo("", telegramIntArgument(bridge, fill, factory.offset, 0).toString()),
                )
            }
        }
        for (key in listOf("idField", "checkedField", "id")) {
            creator.create("ContactAddActivityShareItem.$key") { shareItem.getValue(key) }
        }

        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ContactAddActivityCheckShare = table["ContactAddActivityCheckShare"]!!
        val ContactAddActivityFillItems = table["ContactAddActivityFillItems"]!!
        val contactAddActivityClass = findClass(ContactAddActivityCheckShare.className)
        val sameClass =
            ContactAddActivityFillItems.className == ContactAddActivityCheckShare.className
        val objField =
            if (!sameClass) findClass(ContactAddActivityFillItems.className).declaredFields.single { it.type == Any::class.java }
                .also { it.isAccessible = true } else null
        fun contactActivity(receiver: Any): Any? = if (sameClass) receiver else objField!!.get(receiver)
            .takeIf { contactAddActivityClass.isInstance(it) }
        fun itemField(key: String) = table.getValue("ContactAddActivityShareItem.$key").descriptor
            .takeIf { it.isNotEmpty() }?.let { DexField(it).getFieldInstance(classLoader).apply { isAccessible = true } }
        val itemId = itemField("idField")
        val itemChecked = itemField("checkedField")
        val shareId = table.getValue("ContactAddActivityShareItem.id").memberName.toIntOrNull()
        findClass(ContactAddActivityFillItems.className).hookAll(
            ContactAddActivityFillItems.memberName,
            cond = ::isEnabled,
            before = { param ->
                contactActivity(param.thisObject)?.setObj(ContactAddActivityCheckShare.memberName, false)
            },
            after = { param ->
                if (param.throwable == null && itemId != null && itemChecked != null) {
                    val activity = contactActivity(param.thisObject)
                    if (activity != null) {
                        activity.setObj(ContactAddActivityCheckShare.memberName, false)
                        (param.args.first() as List<*>).filterNotNull().forEach { item ->
                            if (itemId.declaringClass.isInstance(item) && itemId.getInt(item) == shareId) {
                                itemChecked.setBoolean(item, false)
                            }
                        }
                    }
                }
            }
        )
    }
}
