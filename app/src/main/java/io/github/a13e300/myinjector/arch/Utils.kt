package io.github.a13e300.myinjector.arch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Parcelable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.annotation.LayoutRes
import kotlin.system.exitProcess


fun View.findView(predicate: (View) -> Boolean): View? {
    if (predicate(this)) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val v = child.findView(predicate)
            if (v != null) return v
        }
    }
    return null
}

fun Float.dp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

fun Float.sp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

fun Int.dp2px(resources: Resources) = toFloat().dp2px(resources)
fun Int.sp2px(resources: Resources) = toFloat().sp2px(resources)

fun Context.addModuleAssets(path: String) {
    resources.assets.call("addAssetPath", path)
}

@Suppress("deprecation")
fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, clz)
    } else {
        getParcelableExtra(key)
    }

fun Context.inflateLayout(
    @LayoutRes resource: Int,
    root: ViewGroup? = null,
    attachToRoot: Boolean = root != null
): View = LayoutInflater.from(this).inflate(resource, root, attachToRoot)

fun ListView.forceSetSelection(pos: Int, top: Int = 0) {
    // stop scrolling
    smoothScrollBy(0, 0)
    setSelectionFromTop(pos, top)
}

fun restartApplication(activity: Activity) {
    // https://stackoverflow.com/a/58530756
    val pm = activity.packageManager
    val intent = pm.getLaunchIntentForPackage(activity.packageName)
    activity.finishAffinity()
    activity.startActivity(intent)
    exitProcess(0)
}

@Suppress("DEPRECATION")
fun createPackageResources(appInfo: ApplicationInfo): Resources {
    val assetManager = AssetManager::class.java.newInstance()
    assetManager.call("addAssetPath", appInfo.sourceDir)
    return Resources(assetManager, null, null)
}

fun Context.findBaseActivityOrNull(): Activity? =
    this as? Activity
        ?: (this as? ContextWrapper)?.baseContext?.findBaseActivityOrNull()

fun Context.findBaseActivity(): Activity =
    this.findBaseActivityOrNull()
        ?: error("not activity: $this")

fun ViewGroup.containsClass(clazz: Class<*>): Boolean {
    for (i in 0 until childCount) {
        if (clazz.isInstance(getChildAt(i))) {
            return true
        }
    }
    return false
}
