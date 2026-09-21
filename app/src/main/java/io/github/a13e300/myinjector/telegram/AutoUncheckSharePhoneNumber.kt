package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.toObfsInfo

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoUncheckSharePhoneNumber

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        val bridge = creator.bridge
        val contactAddActivity = creator.create("ContactAddActivity") {
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

        val checkShare = creator.create("ContactAddActivityCheckShare") {
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
        val contactAddActivityFillItems = creator.create("ContactAddActivityFillItems") {
            bridge.findMethod {
                matcher {
                    usingEqStrings("MobileVisibleInfo")
                }
            }.single().toObfsInfo()
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
            if (!sameClass) findClass(ContactAddActivityFillItems.className).declaredFields.single { it.type == Object::class.java }
                .also { it.isAccessible = true } else null
        findClass(ContactAddActivityFillItems.className).hookAllBefore(
            ContactAddActivityFillItems.memberName,
            cond = ::isEnabled
        ) { param ->
            if (sameClass) {
                param.thisObject.setObj(ContactAddActivityCheckShare.memberName, false)
            } else {
                val contactAddActivity = objField!!.get(param.thisObject)
                if (contactAddActivityClass.isInstance(contactAddActivity)) {
                    contactAddActivity.setObj(ContactAddActivityCheckShare.memberName, false)
                }
            }
        }
    }
}
