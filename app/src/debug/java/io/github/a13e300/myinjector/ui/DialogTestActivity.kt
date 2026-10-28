package io.github.a13e300.myinjector.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.LinearLayout
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.ui.settings.*

/** 普通 Activity + 模块资源 Context，用来回归宿主未使用 AndroidX 的弹窗场景。 */
class DialogTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = this
        val dialogContext = object : ContextThemeWrapper(
            applicationContext.createConfigurationContext(resources.configuration), R.style.AppTheme,
        ), DialogHostContext {
            override val dialogHost: Activity get() = host
            override fun getSystemService(name: String): Any? =
                if (name == Context.WINDOW_SERVICE) host.getSystemService(name) else super.getSystemService(name)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(title: String, click: () -> Unit) {
            content.addView(Button(this).apply { text = title; setOnClickListener { click() } })
        }
        button("打开注入设置") {
            val settings = SettingsGroup().apply {
                group("隐私") {
                    toggle("隐藏手机号", "hidePhone")
                    toggle("仅对自己隐藏", "selfOnly") { isEnabled = false }
                    toggle("拒绝修改", "reject", "模拟业务拒绝写入")
                }
                group("其他") {
                    repeat(25) { toggle("功能 $it", "feature$it", "用于滚动与搜索回归") }
                }
            }
            showInjectorComposeDialog(
                dialogContext, "注入设置测试",
                listOf(
                    ModernInjectedDialogAction("错误详情", dismissAfterClick = false) {
                        showModernInjectedDialog(
                            dialogContext, "错误详情",
                            modernInjectedMessageView(dialogContext, "测试错误：保留详情与关闭操作"),
                            listOf(ModernInjectedDialogAction("关闭")),
                        )
                    },
                    ModernInjectedDialogAction("返回"),
                ),
            ) {
                InjectedSettingsPanel(settings, { setting, value ->
                    if (setting.key == "reject") false else {
                        if (setting.key == "hidePhone") settings.find("selfOnly").isEnabled = value as Boolean
                        true
                    }
                }, { false })
            }
        }
        button("打开文本输入") {
            showModernInjectedTextInputDialog(dialogContext, "坐标", "1,2") {
                getSharedPreferences("ui_smoke", MODE_PRIVATE).edit().putString("text", it).apply()
            }
        }
        setContentView(content)
    }
}
