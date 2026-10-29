package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** 无界面的独立桌面入口，转发到设置页后结束，启停时不影响设置页及其任务。 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).setClass(this, SettingsActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        finish()
    }
}
