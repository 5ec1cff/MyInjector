@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import io.github.a13e300.myinjector.system_server.ResultReceiver
import java.util.Arrays
import java.util.stream.Collectors

@Suppress("deprecation")
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            fragmentManager.beginTransaction()
                .add(R.id.fragment_container, SettingsFragment()).commit()
        }
    }

    class SettingsFragment : PreferenceFragment(), OnSharedPreferenceChangeListener {
        @Deprecated("Deprecated in Java")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferencesName = "system_server"
            addPreferencesFromResource(R.xml.prefs)
        }

        @Deprecated("Deprecated in Java")
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            view.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            view.setOnApplyWindowInsetsListener { v: View?, windowInsets: WindowInsets? ->
                var insets: Insets? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets = windowInsets!!.getInsets(WindowInsets.Type.systemBars())
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    insets = windowInsets!!.systemWindowInsets
                }
                val mlp = v!!.layoutParams as MarginLayoutParams
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mlp.leftMargin = insets!!.left
                    mlp.bottomMargin = insets.bottom
                    mlp.rightMargin = insets.right
                    mlp.topMargin = insets.top
                } else {
                    mlp.leftMargin = windowInsets!!.systemWindowInsetLeft
                    mlp.bottomMargin = windowInsets.systemWindowInsetBottom
                    mlp.rightMargin = windowInsets.systemWindowInsetRight
                    mlp.topMargin = windowInsets.systemWindowInsetTop
                }
                v.layoutParams = mlp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return@setOnApplyWindowInsetsListener WindowInsets.CONSUMED
                } else return@setOnApplyWindowInsetsListener windowInsets!!.consumeSystemWindowInsets()
            }
            super.onViewCreated(view, savedInstanceState)
            findPreference("commit").setOnPreferenceClickListener { x: Preference? ->
                commitSystem()
                true
            }
            findPreference("commitSysUI").setOnPreferenceClickListener { x: Preference? ->
                commitSystemUI()
                true
            }
            findPreference("checkHotUpdate").setOnPreferenceClickListener {
                showHotUpdateDialog()
                true
            }
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            val p = findPreference(key)
            if (p == null) return
            val pr = p.parent
            if (pr == null) return
            if ("system" == pr.key) {
                commitSystem()
            } else if ("systemui" == pr.key) {
                commitSystemUI()
            }
        }

        private fun commitSystem() {
            val context = getContext()
            val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG")
            val pendingIntent =
                PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            val sp = preferenceManager.sharedPreferences
            val config = SystemServerConfig.newBuilder()
                .setNoWakePath(sp.getBoolean("noWakePath", false))
                .setNoMiuiIntent(sp.getBoolean("noMiuiIntent", false))
                .setClipboardWhitelist(sp.getBoolean("clipboardWhitelist", false))
                .setFixSync(sp.getBoolean("fixSync", false))
                .setXSpace(sp.getBoolean("xSpace", false))
                .addAllClipboardWhitelistPackages(
                    Arrays.stream<String?>(
                        sp.getString("clipboardWhitelistPackages", "")!!.trim { it <= ' ' }
                            .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    )
                        .collect(
                            Collectors.toList()
                        )
                )
                .setForceNewTask(sp.getBoolean("forceNewTask", false))
                .addAllForceNewTaskRules(
                    Arrays.stream<String?>(
                        sp.getString(
                            "forceNewTaskRules",
                            ""
                        )!!.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()).map<NewTaskRule?> { x: String? ->
                        val parts: Array<String?> =
                            x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        var ignoreResult = false
                        var useNewDoc = false
                        if (parts.size == 3) {
                            val options: Array<String?> =
                                parts[2]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            for (o in options) {
                                if ("ir" == o) {
                                    ignoreResult = true
                                } else if ("nd" == o) {
                                    useNewDoc = true
                                }
                            }
                        } else if (parts.size != 2) return@map null
                        var sourcePackage: String = parts[0]!!
                        var targetPackage: String = parts[1]!!
                        var sourceComponent = ""
                        var targetComponent = ""
                        if (sourcePackage.contains("/")) {
                            val l: Array<String?> =
                                sourcePackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            if (l.size == 2) {
                                sourcePackage = l[0]!!
                                sourceComponent = l[1]!!
                                if (sourceComponent.startsWith(".")) sourceComponent = l[0] + l[1]
                            }
                        }
                        if (targetPackage.contains("/")) {
                            val l: Array<String?> =
                                targetPackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                            if (l.size == 2) {
                                targetPackage = l[0]!!
                                targetComponent = l[1]!!
                                if (targetComponent.startsWith(".")) targetComponent = l[0] + l[1]
                            }
                        }
                        NewTaskRule.newBuilder()
                            .setSourcePackage(sourcePackage)
                            .setTargetPackage(targetPackage)
                            .setSourceComponent(sourceComponent)
                            .setTargetComponent(targetComponent)
                            .setUseNewDocument(useNewDoc)
                            .setIgnoreResult(ignoreResult)
                            .build()
                    }.filter { it != null }.collect(Collectors.toList())
                )
                .setOverrideStatusBar(sp.getBoolean("overrideStatusBar", false))
                .addAllOverrideStatusBarRules(
                    Arrays.stream<String?>(
                        sp.getString("overrideStatusBarRules", "")!!
                            .trim { it <= ' ' }
                            .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    ).map<OverrideStatusBarRule?> { x: String? ->
                        val parts: Array<String?> =
                            x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (parts.size != 2) return@map null
                        var pkg: String = parts[0]!!
                        var light = false
                        if ("light" == parts[1]) {
                            light = true
                        } else if ("dark" != parts[2]) {
                            return@map null
                        }
                        var component = ""
                        val pkgSplit: Array<String?> =
                            pkg.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (pkgSplit.size == 2) {
                            pkg = pkgSplit[0]!!
                            component = pkgSplit[1]!!
                            if (component.startsWith(".")) component = pkg + component
                        } else if (pkgSplit.size != 1) return@map null
                        OverrideStatusBarRule.newBuilder()
                            .setPackage(pkg)
                            .setComponent(component)
                            .setLight(light)
                            .build()
                    }.filter { it != null }.collect(Collectors.toList())
                )
                .setForceNewTaskDebug(sp.getBoolean("forceNewTaskDebug", false))
                .build()
            intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
            intent.putExtra("EXTRA_CONFIG", config.toByteArray())
            context.sendBroadcast(intent)
        }

        private fun commitSystemUI() {
            val context = getContext()
            val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG")
            val pendingIntent =
                PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            val sp = preferenceManager.sharedPreferences
            val config = SystemUIConfig.newBuilder()
                .setAlwaysExpandNotification(sp.getBoolean("alwaysExpandNotification", false))
                .setNoDndNotification(sp.getBoolean("noDndNotification", false))
                .setShowNotificationDetail(sp.getBoolean("showNotificationDetail", false))
                .build()
            intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
            intent.putExtra("EXTRA_CONFIG", config.toByteArray())
            context.sendBroadcast(intent)
        }

        @SuppressLint("SetTextI18n")
        private fun showHotUpdateDialog() {
            val context = getContext()

            val rootView =
                LayoutInflater.from(context).inflate(R.layout.hot_update_dialog, null, false)

            val tv = rootView.findViewById<TextView>(R.id.result_text)

            fun command(name: String, args: Bundle.() -> Unit = {}, cb: (Int, Bundle?) -> Unit) {
                val intent = Intent("io.github.a13e300.myinjector.SYSTEM_SERVER_ENTRY")
                intent.`package` = "android"
                val pendingIntent =
                    PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
                val receiver = object : ResultReceiver() {
                    override fun onReceive(code: Int, data: Bundle?) {
                        cb(code, data)
                    }
                }
                val b = Bundle().apply {
                    putParcelable("EXTRA_CREDENTIAL", pendingIntent)
                    putString("EXTRA_ACTION", name)
                    putBinder("EXTRA_RECEIVER", receiver)
                    args()
                }
                intent.putExtras(b)
                context.sendBroadcast(intent)
            }

            fun check() {
                tv.text = "检查中……"
                command("needUpdate") { code, _ ->
                    tv.post {
                        tv.text = if (code == 1) "可更新" else "无需更新"
                    }
                }
            }

            fun update(force: Boolean) {
                command("reload", { putBoolean("force", force) }) { code, _ ->
                    tv.post {
                        tv.text = if (code == 1) "重新加载成功" else "重新加载失败"
                    }
                }
            }

            rootView.findViewById<Button>(R.id.update_btn).setOnClickListener {
                update(false)
            }

            rootView.findViewById<Button>(R.id.update_force_btn).setOnClickListener {
                update(true)
            }

            rootView.findViewById<Button>(R.id.check_btn).setOnClickListener {
                check()
            }

            rootView.findViewById<Button>(R.id.gc_btn).setOnClickListener {
                command("gc") { code, _ ->
                    tv.post {
                        tv.text = if (code == 0) "GC 成功" else "GC 失败"
                    }
                }
            }

            rootView.findViewById<Button>(R.id.old_hook_btn).setOnClickListener {
                command("reportOldHook") { code, data ->
                    val d = data?.getString("hooks") ?: ""
                    tv.post {
                        tv.text = "旧模块：" + d.ifEmpty { "无" }
                    }
                }
            }

            check()

            AlertDialog.Builder(context)
                .setTitle("热更新")
                .setView(rootView)
                .show()

        }

        @Deprecated("Deprecated in Java")
        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences
                .registerOnSharedPreferenceChangeListener(this)
        }

        @Deprecated("Deprecated in Java")
        override fun onPause() {
            super.onPause()
            preferenceManager.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
        }
    }
}
