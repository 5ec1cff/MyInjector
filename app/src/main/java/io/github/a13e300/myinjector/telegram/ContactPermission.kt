package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

// 禁止询问联系人权限
class ContactPermission : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.contactPermission

    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {
        // onBecomeFullyVisible
        // some modified version may not have this
        // https://github.com/NextAlone/Nagram/blob/b8db62a65e1e4dee34d92bff412548ef628ddb06/TMessagesProj/src/main/java/org/telegram/ui/ContactAddActivity.java
        val contactsActivityCheckPermissionsMethod =
            creator.create("ContactsActivityCheckPermissionMethod") { bridge ->
                bridge.findMethod {
                    matcher {
                        usingStrings("android.permission.READ_CONTACTS")
                        declaredClass {
                            superClass = creator.obfsTable["BaseFragment"]!!.className
                        }
                        addInvoke {
                            descriptor("Landroid/content/Context;->checkSelfPermission(Ljava/lang/String;)I")
                        }
                        addInvoke {
                            descriptor("Landroid/app/Activity;->shouldShowRequestPermissionRationale(Ljava/lang/String;)Z")
                        }
                    }
                }.singleOrNull()?.toObfsInfo() ?: ObfsInfo("", "")
            }

        val contactsActivityCheckPermissionField =
            creator.create("ContactsActivityCheckPermissionField") { bridge ->
                if (contactsActivityCheckPermissionsMethod.className.isEmpty()) return@create ObfsInfo(
                    "",
                    ""
                )
                bridge.findField {
                    matcher {
                        addReadMethod {
                            descriptor(contactsActivityCheckPermissionsMethod.descriptor)
                        }
                        addWriteMethod {
                            descriptor(contactsActivityCheckPermissionsMethod.descriptor)
                        }
                        declaredClass(contactsActivityCheckPermissionsMethod.className)
                        type("boolean")
                    }
                }.single().toObfsInfo()
            }

        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ContactsActivityCheckPermissionMethod = table["ContactsActivityCheckPermissionMethod"]!!
        if (ContactsActivityCheckPermissionMethod.className.isEmpty()) {
            logD("skip ContactPermission hook since this version don't check contact permission")
            return
        }
        val ContactsActivityCheckPermissionField = table["ContactsActivityCheckPermissionField"]!!
        val theClass = findClass(ContactsActivityCheckPermissionMethod.className)
        val theField =
            theClass.declaredFields.single { it.name == ContactsActivityCheckPermissionField.memberName }
                .also { it.isAccessible = true }
        theClass.hookAllBefore(
            ContactsActivityCheckPermissionMethod.memberName,
            cond = ::isEnabled
        ) { param ->
            theField.setBoolean(param.thisObject, false)
        }
    }
}
