package io.github.a13e300.myinjector.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a13e300.myinjector.ui.settings.SettingsApp
import io.github.a13e300.myinjector.ui.settings.AppSettingsDestination
import io.github.a13e300.myinjector.scope.ScopeApplication
import io.github.a13e300.myinjector.scope.ScopeCatalog
import androidx.compose.ui.graphics.ImageBitmap

/** 在同一页面中模拟异步连接／断开，回归 Navigation 3 缓存入口的状态更新。 */
class SettingsStateTestActivity : ComponentActivity() {
    private var scope by mutableStateOf(io.github.a13e300.myinjector.scope.ScopeManager.State(ready = true))
    private var connected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        connected = savedInstanceState?.getBoolean("connected")
            ?: intent.getBooleanExtra("connected", false)
        val prefs = getSharedPreferences("navigation_smoke", MODE_PRIVATE)
        setContent {
            InjectorTheme {
                SettingsApp(
                    prefs = prefs,
                    serviceConnected = connected,
                    scopeState = scope,
                    onScopeToggle = { pkg, enabled ->
                        scope = scope.copy(enabled = if (enabled) scope.enabled + pkg else scope.enabled - pkg)
                    },
                    loadApps = {
                        val components = listOf(
                            ScopeApplication("system", "系统框架", true),
                            ScopeApplication("com.android.systemui", "PM 系统界面", true),
                            ScopeApplication("com.miui.home", "PM 桌面", true),
                        )
                        val applications = if (intent.getBooleanExtra("scope_test", false)) listOf(
                            ScopeApplication("com.termux", "Termux", false),
                            ScopeApplication("com.android.chrome", "Chrome", false),
                            ScopeApplication("com.android.settings", "A SysApp Settings", true),
                            ScopeApplication("org.telegram.messenger", "Z SysApp Telegram", true),
                        ) else emptyList()
                        val scrollApps = if (intent.getBooleanExtra("scroll_test", false)) List(24) {
                            ScopeApplication("test.app.$it", "Test App $it", it % 2 == 0)
                        } else emptyList()
                        (components + applications + scrollApps).sortedWith(ScopeCatalog.applicationOrder).map {
                            AppSettingsDestination(it, ImageBitmap(48, 48))
                        }
                    },
                    openApp = { scope = scope.copy(message = "已打开应用内设置") },
                    onToggle = { _, _ -> },
                    onEdit = {},
                    onAction = {},
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        connected = intent.getBooleanExtra("connected", false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("connected", connected)
        super.onSaveInstanceState(outState)
    }
}
