package io.github.a13e300.myinjector

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceScreen
import android.preference.SwitchPreference
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.DynHookManager
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.findClass
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.switchPreference
import io.github.a13e300.myinjector.arch.toObfsInfo
import java.io.InputStream
import java.io.OutputStream

class DisableDetectObscureHook : DynHook() {
    override fun isFeatureEnabled(): Boolean = ChromeHandler.settings.disableWindowObscureDetection

    override fun onHook() {
        MotionEvent::class.java.hookAllAfter("getFlags", cond = ::isEnabled) { param ->
            param.result = (param.result as Int).and(
                MotionEvent.FLAG_WINDOW_IS_OBSCURED.or(
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
                ).inv()
            )
        }
    }
}

class DisableSwipeRefresh : DynHook() {
    companion object {
        private const val KEY_swipeRefreshHander_Pull = "swipeRefreshHander_Pull"
        private const val KEY_swipeRefreshHander_Release = "swipeRefreshHander_Release"
    }

    override fun isFeatureEnabled(): Boolean = ChromeHandler.settings.disableSwipeRefresh

    override fun onHook() {
        val tbl = createObfsTable("chrome", 1, classLoader = classLoader) { bridge ->
            val pullMethod = bridge.findMethod {
                matcher {
                    usingStrings("SwipeRefreshHandler.pull")
                }
            }.single()
            val releaseMethod = pullMethod.declaredClass!!.findMethod {
                matcher {
                    usingStrings("SwipeRefreshHandler.release")
                }
            }.single()

            mutableMapOf(
                KEY_swipeRefreshHander_Pull to pullMethod.toObfsInfo(),
                KEY_swipeRefreshHander_Release to releaseMethod.toObfsInfo(),
            )
        }

        val pullMethod = tbl[KEY_swipeRefreshHander_Pull]!!
        val releaseMethod = tbl[KEY_swipeRefreshHander_Release]!!

        val clazz = findClass(pullMethod.className)
        clazz.hookAllNopIf(pullMethod.memberName, this::isEnabled)
        clazz.hookAllNopIf(releaseMethod.memberName, this::isEnabled)
    }
}

// https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/SwipeRefreshHandler.java;l=293;drc=1b4216b712e68f7dc2b4b13e9d8e0c69203278e6
object ChromeHandler : DynHookManager<ChromeSettings>() {
    override fun onHook() {
        if (loadPackageParam.processName.contains(":")) return
        super.onHook()
        val name = loadPackageParam.appInfo.className
        // ensure split apk (split-chrome) is loaded, see:
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitCompatAppComponentFactory.java;l=136?q=SplitCompatAppComponentFactory&ss=chromium
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitChromeApplication.java;l=33?q=SplitChromeApplication&ss=chromium
        // LoadedApk$SplitDependencyLoaderImpl https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:frameworks/base/core/java/android/app/LoadedApk.java;l=639;drc=c72510de9db33033259d7e32afe3cbaac2266649
        // Context.createContextForSplit
        val appClz = findClass(name)
        val m = runCatching { appClz.getDeclaredMethod("onCreate") }.getOrNull()
        if (m != null) {
            m.hookAfter { param ->
                // real classloader will be available after onCreate
                val loader = (param.thisObject as Context).classLoader
                classLoader = loader
                subHook(DisableSwipeRefresh())
                subHook(DisableDetectObscureHook())
                hookSettings(loader)
            }
        } else {
            subHook(DisableSwipeRefresh())
            subHook(DisableDetectObscureHook())
            hookSettings(classLoader)
        }
    }

    private fun hookSettings(loader: ClassLoader) = runCatching {
        val settingsActivityClass =
            loader.findClass("org.chromium.chrome.browser.settings.SettingsActivity")

        settingsActivityClass.hookAllAfter("onCreateOptionsMenu") { param ->
            val menu = param.args[0] as Menu
            val item = menu.add("MyInjector")
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        settingsActivityClass.hookAllAfter("onOptionsItemSelected") { param ->
            val item = param.args[0] as MenuItem
            val ctx = param.thisObject as Context
            if (item.itemId == Menu.NONE) {
                ChromeSettingsDialog(ctx).show()
                param.result = true
            }
        }
    }.onFailure {
        logE("failed to hookSettings", it)
    }

    override fun onReadSettings(input: InputStream): ChromeSettings =
        ChromeSettings.parseFrom(input)

    override fun onWriteSettings(output: OutputStream, setting: ChromeSettings) {
        setting.writeTo(output)
    }

    override fun defaultSettings(): ChromeSettings = ChromeSettings.getDefaultInstance()
}

@Suppress("deprecation")
class ChromeSettingsDialog(ctx: Context) : SettingDialog(ctx) {
    override fun onPrefChanged(
        preference: Preference,
        newValue: Any?
    ): Boolean {
        val settings = ChromeHandler.settings.toBuilder()
        when (preference.key) {
            "disableSwipeRefresh" -> {
                settings.disableSwipeRefresh = newValue as Boolean
            }

            "disableWindowObscureDetection" -> {
                settings.disableWindowObscureDetection = newValue as Boolean
            }

            else -> return false
        }
        ChromeHandler.updateSettings(settings.build())
        return true
    }

    override fun onPrefClicked(preference: Preference): Boolean {
        return false
    }

    override fun onRetrievePref(preference: Preference) {
        when (preference.key) {
            "disableSwipeRefresh" -> {
                (preference as SwitchPreference).isChecked =
                    ChromeHandler.settings.disableSwipeRefresh
            }

            "disableWindowObscureDetection" -> {
                (preference as SwitchPreference).isChecked =
                    ChromeHandler.settings.disableWindowObscureDetection
            }
        }
    }

    override fun onCreatePref(prefScreen: PreferenceScreen) {
        prefScreen.run {
            switchPreference("禁用下拉刷新", "disableSwipeRefresh")
            switchPreference("禁用窗口遮挡检测", "disableWindowObscureDetection")
        }
    }

}
