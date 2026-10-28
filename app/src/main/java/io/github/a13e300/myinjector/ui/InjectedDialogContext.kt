package io.github.a13e300.myinjector.ui

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.Context
import android.view.ContextThemeWrapper
import io.github.a13e300.myinjector.R

/**
 * 模块资源来自未创建 Application 的 LoadedApk；窗口与运行期回调必须使用宿主。
 * ContextWrapper 的注册方法直接转发到 base，因此只覆盖 getApplicationContext 不够。
 */
internal class InjectedDialogContext(
    resourceContext: Context,
    override val dialogHost: Activity,
) : ContextThemeWrapper(resourceContext, R.style.AppTheme), DialogHostContext {
    override fun getApplicationContext(): Context = dialogHost.application

    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
        dialogHost.registerComponentCallbacks(callback)
    }

    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) {
        dialogHost.unregisterComponentCallbacks(callback)
    }

    override fun getSystemService(name: String): Any? =
        if (name == WINDOW_SERVICE) dialogHost.getSystemService(name) else super.getSystemService(name)
}
