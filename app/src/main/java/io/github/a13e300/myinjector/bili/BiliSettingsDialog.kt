package io.github.a13e300.myinjector.bili

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import io.github.a13e300.myinjector.BiliHandler
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.modernInjectedMessageView
import io.github.a13e300.myinjector.ui.modernInjectedScrollContent
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import io.github.a13e300.myinjector.ui.settings.SettingItem
import io.github.a13e300.myinjector.ui.settings.SettingsGroup
import io.github.a13e300.myinjector.ui.settings.ToggleSetting

internal class BiliSettingsDialog(ctx: Context, private val handler: BiliHandler) : SettingDialog(ctx) {
    override val dialogTitle = "哔哩哔哩设置"

    override fun createSettings(settingGroup: SettingsGroup) {
        settingGroup.action("开关生效方式", "liveHint", "修改后对新页面和后续操作生效，无需重启；已打开的页面和菜单不主动刷新")
        BiliFeature.entries.groupBy { it.group }.forEach { (title, features) ->
            settingGroup.group(title) {
                features.forEach { toggle(it.title, it.key, it.summary) }
            }
        }
    }

    override fun loadSetting(setting: SettingItem) {
        if (setting.key == "liveHint") {
            setting.isEnabled = false
            return
        }
        val feature = BiliFeature.entries.single { it.key == setting.key }
        (setting as ToggleSetting).isChecked = handler.settings.isEnabled(feature)
        if (handler.hasError(feature)) {
            setting.summary = SpannableStringBuilder(setting.summary ?: "")
                .append("\n")
                .append("存在问题", ForegroundColorSpan(Color.RED), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun onSettingChanged(setting: SettingItem, newValue: Any?): Boolean {
        val feature = BiliFeature.entries.singleOrNull { it.key == setting.key } ?: return false
        return runCatching {
            handler.updateSettings(handler.settings.withFeature(feature, newValue as Boolean))
        }.onFailure {
            Toast.makeText(activityCtx, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
        }.isSuccess
    }

    override fun onSettingClicked(setting: SettingItem): Boolean = false

    override fun onConfigureActions(actions: MutableList<ModernInjectedDialogAction>) {
        val errors = handler.errorReport().takeIf { it.isNotEmpty() } ?: return
        val version = activityCtx.packageManager.getPackageInfo(activityCtx.packageName, 0).versionName
        val report = "MyInjector ${BuildConfig.VERSION_NAME}\n哔哩哔哩 $version\n进程：${handler.loadPackageParam.processName}\n本次启动的 Hook 错误：\n\n$errors"
        actions.add(0, ModernInjectedDialogAction("查看错误", dismissAfterClick = false) {
            showModernInjectedDialog(
                context, "Hook 错误详情",
                modernInjectedScrollContent(context, modernInjectedMessageView(context, report), 0.45f),
                listOf(
                    ModernInjectedDialogAction("分享") {
                        runCatching {
                            activityCtx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report),
                                "分享 Hook 错误",
                            ))
                        }.onFailure {
                            Toast.makeText(activityCtx, "分享失败，请尝试复制", Toast.LENGTH_LONG).show()
                        }
                    },
                    ModernInjectedDialogAction("复制") {
                        activityCtx.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("MyInjector Hook 错误", report))
                    },
                    ModernInjectedDialogAction("关闭"),
                ),
            )
        })
    }
}
