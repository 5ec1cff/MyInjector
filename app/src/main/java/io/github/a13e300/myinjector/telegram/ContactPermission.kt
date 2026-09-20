package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.toObfsInfo

// 禁止询问联系人权限
class ContactPermission : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.contactPermission

    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {
        // onBecomeFullyVisible
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
                }.single().toObfsInfo()
            }

        val contactsActivityCheckPermissionField =
            creator.create("ContactsActivityCheckPermissionField") { bridge ->
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
