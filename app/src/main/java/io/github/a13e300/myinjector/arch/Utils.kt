package io.github.a13e300.myinjector.arch

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook


fun View.findView(predicate: (View) -> Boolean): View? {
    if (predicate(this)) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val v = getChildAt(i).findView(predicate)
            if (v != null) return v
        }
    }
    return null
}

fun Float.dp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

fun Int.dp2px(resources: Resources) = toFloat().dp2px(resources)

fun Context.addModuleAssets(path: String) {
    resources.assets.call("addAssetPath", path)
}

@SuppressWarnings("deprecation")
fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clz: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, clz)
    } else {
        getParcelableExtra(key)
    }

class SkipIf(private val cond: (p: MethodHookParam) -> Boolean) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        if (cond(param)) param.result = null
    }
}
