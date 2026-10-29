package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** alias 只指向无界面的转发入口；禁用它时不影响设置 Activity 及其任务。 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).setClass(this, SettingsActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        finish()
    }
}
