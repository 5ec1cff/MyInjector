package io.github.a13e300.myinjector.telegram

import android.app.Dialog
import android.view.ViewGroup
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.findView
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookBefore

// 自动勾选为对方删除消息（原行为是默认不勾选）
class AutoCheckDeleteMessageOption : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoCheckDeleteMessageOption

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val isCreating = ThreadLocal<Boolean>()
        val alertDialogClass = findClass("org.telegram.ui.ActionBar.AlertDialog")
        val checkBoxCellClass = findClass("org.telegram.ui.Cells.CheckBoxCell")
        findClass("org.telegram.ui.Components.AlertsCreator").hookAll(
            "createDeleteMessagesAlert",
            cond = ::isEnabled,
            before = {
                isCreating.set(true)
            },
            after = {
                isCreating.set(false)
            }
        )
        findClass("org.telegram.ui.ActionBar.BaseFragment").hookBefore(
            "showDialog",
            Dialog::class.java,
            cond = ::isEnabled
        ) { param ->
            if (isCreating.get() != true) return@hookBefore
            val dialog = param.args[0]
            if (!alertDialogClass.isInstance(dialog)) return@hookBefore
            val root = dialog.getObjAsN<ViewGroup>("customView") ?: return@hookBefore

            // TODO: find the checkbox correctly
            val v = root.findView {
                checkBoxCellClass.isInstance(it) &&
                        it.call("isChecked") == false
            }
            // logD("beforeHookedMethod: found view: $v")
            v?.performClick()
        }
    }
}
