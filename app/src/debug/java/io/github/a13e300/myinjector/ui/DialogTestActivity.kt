package io.github.a13e300.myinjector.ui

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.a13e300.myinjector.ui.settings.*

/** 普通 Activity + 模块资源 Context，用来回归宿主未使用 AndroidX 的弹窗场景。 */
class DialogTestActivity : Activity() {
    private val registeredCallbacks = mutableSetOf<ComponentCallbacks>()
    private var callbackStatus: TextView? = null

    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
        super.registerComponentCallbacks(callback)
        registeredCallbacks += callback
        callbackStatus?.text = "配置回调：${registeredCallbacks.size}"
    }

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
        super.unregisterComponentCallbacks(callback)
        registeredCallbacks -= callback
        callbackStatus?.text = "配置回调：${registeredCallbacks.size}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = this
        val resourcePackage = intent.getStringExtra("resource_context_package")
        val resourceContext = if (resourcePackage != null) {
            // 仅加载外部包资源，不创建它的 Application，与注入时的 LoadedApk 条件一致。
            val packageContext = createPackageContext(resourcePackage, 0)
                .createConfigurationContext(resources.configuration)
            check(packageContext.applicationContext == null) { "回归用资源 Context 必须没有 Application" }
            object : ContextWrapper(packageContext) {
                override fun getResources() = host.resources
                override fun getAssets() = host.assets
                override fun getTheme() = host.theme
            }
        } else applicationContext.createConfigurationContext(resources.configuration)
        val dialogContext = InjectedDialogContext(resourceContext, host)
        check(dialogContext.applicationContext === application)
        check(dialogContext.resources === resourceContext.resources)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = if (resourcePackage != null) "资源 Context：无 Application" else "资源 Context：正常"
        })
        callbackStatus = TextView(this).apply { text = "配置回调：0" }
        content.addView(callbackStatus)
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
