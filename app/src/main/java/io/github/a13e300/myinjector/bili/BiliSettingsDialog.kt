package io.github.a13e300.myinjector.bili

import android.content.ClipData
import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.a13e300.myinjector.BiliHandler
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.SettingDialog
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.modernInjectedMessageView
import io.github.a13e300.myinjector.ui.modernInjectedScrollContent
import io.github.a13e300.myinjector.ui.showModernInjectedDialog
import io.github.a13e300.myinjector.ui.showInjectorComposeDialog
import io.github.a13e300.myinjector.ui.settings.SettingItem
import io.github.a13e300.myinjector.ui.settings.SettingsGroup
import io.github.a13e300.myinjector.ui.settings.ToggleSetting

internal class BiliSettingsDialog(ctx: Context, private val handler: BiliHandler) : SettingDialog(ctx) {
    override val dialogTitle = "哔哩哔哩设置"

    override fun createSettings(settingGroup: SettingsGroup) {
        settingGroup.action("开关生效方式", "liveHint", "普通功能对新页面和后续操作生效，无需重启；界面版本修改后需要重启哔哩哔哩")
        BiliFeature.entries.groupBy { it.group }.forEach { (title, features) ->
            settingGroup.group(title) {
                features.forEach { toggle(it.title, it.key, it.summary) }
            }
        }
        settingGroup.group("界面版本") {
            BiliInterfaceComponent.entries.forEach { action(it.title, it.settingKey) }
        }
    }

    override fun loadSetting(setting: SettingItem) {
        if (setting.key == "liveHint") {
            setting.isEnabled = false
            return
        }
        BiliInterfaceComponent.entries.find { it.settingKey == setting.key }?.let { component ->
            val summary = handler.interfaceState.summary(component, handler.settings.interfaceMode(component))
            setting.summary = if (handler.interfaceState.hasError(component)) {
                SpannableStringBuilder(summary).apply {
                    setSpan(ForegroundColorSpan(Color.RED), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else summary
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

    override fun onSettingClicked(setting: SettingItem): Boolean {
        val component = BiliInterfaceComponent.entries.find { it.settingKey == setting.key } ?: return false
        lateinit var dialog: Dialog
        dialog = showInjectorComposeDialog(context, component.title, listOf(ModernInjectedDialogAction("取消"))) {
            Column {
                Text("保存后重启哔哩哔哩生效；不强制时使用宿主自己的灰度结果。")
                BiliInterfaceMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = handler.settings.interfaceMode(component) == mode,
                            role = Role.RadioButton,
                            onClick = {
                                runCatching {
                                    handler.updateSettings(handler.settings.withInterfaceMode(component, mode))
                                }.onSuccess {
                                    loadSetting(setting)
                                    dialog.dismiss()
                                }.onFailure {
                                    Toast.makeText(activityCtx, "保存失败：${it.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = handler.settings.interfaceMode(component) == mode, onClick = null)
                        Text(mode.title, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
        return true
    }

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
