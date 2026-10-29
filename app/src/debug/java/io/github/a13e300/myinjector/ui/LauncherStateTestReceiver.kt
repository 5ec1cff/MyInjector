package io.github.a13e300.myinjector.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.a13e300.myinjector.LauncherActivity

/** 只在 debug 中模拟外部修改桌面入口，触发真实系统广播且不改变 Activity 生命周期。 */
class LauncherStateTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, LauncherActivity::class.java),
            if (intent.getBooleanExtra("visible", true)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
