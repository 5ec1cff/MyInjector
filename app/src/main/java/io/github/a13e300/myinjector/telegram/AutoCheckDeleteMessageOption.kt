package io.github.a13e300.myinjector.telegram

import android.app.Dialog
import android.view.ViewGroup
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.findView
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

// 自动勾选为对方删除消息（原行为是默认不勾选）
class AutoCheckDeleteMessageOption : MyDynHook("autoCheckDeleteMessageOption") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoCheckDeleteMessageOption

    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {
        val alertDialogClass = creator.create("AlertDialog") { bridge ->
            bridge.findClass {
                matcher {
                    superClass("android.app.Dialog")
                    usingStrings("alpha")
                }
            }.single().toObfsInfo()
        }

        val createDeleteMessagesAlert = creator.create("createDeleteMessagesAlert") { bridge ->
            bridge.findMethod {
                matcher {
                    usingStrings("MessageScheduledRepeatDeletePostponeMonths")
                }
            }.single().toObfsInfo()
        }

        val checkBoxCell = creator.create("CheckBoxCell") { bridge ->
            bridge.findMethod {
                matcher {
                    name("<init>")
                    addCaller {
                        descriptor(createDeleteMessagesAlert.descriptor)
                    }
                    declaredClass {
                        usingStrings("android.widget.CheckBox")
                        superClass("android.widget.FrameLayout")
                    }
                }
            }.single().toObfsInfo()
        }

        val alertDialogBuilderSetView = creator.create("AlertDialogBuilderSetView") { bridge ->
            bridge.findMethod {
                matcher {
                    addCaller {
                        descriptor(createDeleteMessagesAlert.descriptor)
                    }
                    paramTypes("android.view.View")
                    addUsingField {
                        declaredClass(alertDialogClass.className)
                        type("android.view.View")
                    }
                }
            }.single().toObfsInfo()
        }

        val alertDialogCustomView = creator.create("AlertDialogCustomView") { bridge ->
            bridge.findField {
                matcher {
                    addWriteMethod {
                        descriptor(alertDialogBuilderSetView.descriptor)
                    }
                    declaredClass(alertDialogClass.className)
                    type("android.view.View")
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val isCreating = ThreadLocal<Boolean>()
        val alertDialogInfo = table["AlertDialog"]!!
        val checkBoxCellInfo = table["CheckBoxCell"]!!
        val createDeleteMessagesAlert = table["createDeleteMessagesAlert"]!!
        val baseFragmentInfo = table["BaseFragment"]!!
        val AlertDialogCustomView = table["AlertDialogCustomView"]!!
        val alertDialogClass = findClass(alertDialogInfo.className)
        val checkBoxCellClass = findClass(checkBoxCellInfo.className)
        val alertDialogCustomViewField =
            alertDialogClass.declaredFields.single { it.name == AlertDialogCustomView.memberName }
                .also { it.isAccessible = true }
        findClass(createDeleteMessagesAlert.className).hookAll(
            createDeleteMessagesAlert.memberName,
            cond = ::isEnabled,
            before = {
                logD("creatingDeleteMessage")
                isCreating.set(true)
            },
            after = {
                isCreating.set(false)
            }
        )
        findClass(baseFragmentInfo.className).hookBefore(
            "showDialog",
            Dialog::class.java,
            cond = ::isEnabled
        ) { param ->
            if (isCreating.get() != true) {
                return@hookBefore
            }
            val dialog = param.args[0]
            if (!alertDialogClass.isInstance(dialog)) {
                return@hookBefore
            }
            val root = alertDialogCustomViewField.get(dialog) as? ViewGroup? ?: return@hookBefore

            // TODO: find the checkbox correctly
            val v = root.findView {
                checkBoxCellClass.isInstance(it)
                // && it.call("isChecked") == false
            }
            // logD("beforeHookedMethod: found view: $v")
            v?.performClick()
        }
    }
}
