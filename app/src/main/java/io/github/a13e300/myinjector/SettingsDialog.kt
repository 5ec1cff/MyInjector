package io.github.a13e300.myinjector

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Bundle
import io.github.a13e300.myinjector.arch.*
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.InjectedDialogContext
import io.github.a13e300.myinjector.ui.modernInjectedMessageView
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import io.github.a13e300.myinjector.ui.showInjectorComposeDialog
import io.github.a13e300.myinjector.ui.settings.*

abstract class SettingDialog(val activityCtx: Context) {
    private val appCtx = (activityCtx.call(
        "createApplicationContext",
        ApplicationInfo(activityCtx.applicationInfo).apply {
            packageName = BuildConfig.APPLICATION_ID
            sourceDir = Entry.modulePath
            publicSourceDir = Entry.modulePath
            splitSourceDirs = null
            splitPublicSourceDirs = null
            splitNames = null
        }, 0
    ) as Context).createConfigurationContext(activityCtx.resources.configuration)
    val context: Context = InjectedDialogContext(appCtx, activityCtx.findBaseActivity())

    protected lateinit var settingGroup: SettingsGroup
    protected open val dialogTitle: String = "MyInjector 设置"
    abstract fun onSettingChanged(setting: SettingItem, newValue: Any?): Boolean
    abstract fun onSettingClicked(setting: SettingItem): Boolean
    abstract fun loadSetting(setting: SettingItem)
    abstract fun createSettings(settingGroup: SettingsGroup)
    protected open fun onConfigureActions(actions: MutableList<ModernInjectedDialogAction>) {}

    fun show() {
        try {
            val activity = activityCtx.findBaseActivity()
            settingGroup = SettingsGroup()
            createSettings(settingGroup)
            settingGroup.flattened().filterNot { it is SettingsGroup }.forEach(::loadSetting)
            val actions = mutableListOf(
                ModernInjectedDialogAction("返回"),
                ModernInjectedDialogAction("重启") { restartApplication(activity) },
            )
            onConfigureActions(actions)
            showInjectorComposeDialog(context, dialogTitle, actions) {
                InjectedSettingsPanel(settingGroup, ::onSettingChanged, ::onSettingClicked)
            }
        } catch (e: Resources.NotFoundException) {
            logE("res:", e)
            showModernInjectedDialog(
                activityCtx, "需要重启",
                modernInjectedMessageView(activityCtx, "由于加载资源失败，需要重启应用以显示设置界面。"),
                listOf(ModernInjectedDialogAction("重启") { restartApplication(activityCtx.findBaseActivity()) }),
            )
        }
    }
}

fun IHook.addSettingsIntentInterceptor(callback: (activity: Activity) -> Unit) = runCatching {
    Activity::class.java.hookAllAfter("performNewIntent") { param ->
        val activity = param.thisObject as Activity
        val intent = param.args[0] as Intent
        // logD("newIntent $intent")
        if (intent.action == "io.github.a13e300.myinjector.SHOW_SETTINGS" || intent.hasCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")) {
            callback(activity)
        }
    }

    Activity::class.java.hookAfter("performCreate", Bundle::class.java) { param ->
        val activity = param.thisObject as Activity
        val intent = activity.intent
        // logD("create Intent $intent")
        if (intent.action == "io.github.a13e300.myinjector.SHOW_SETTINGS" || intent.hasCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")) {
            callback(activity)
        }
    }

    Instrumentation::class.java.deoptimize("callActivityOnCreate")
    Instrumentation::class.java.deoptimize("callActivityOnNewIntent")
}.onFailure {
    logE("addSettingsInterceptor", it)
}
