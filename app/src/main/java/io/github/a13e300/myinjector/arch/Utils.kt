@file:Suppress("UNCHECKED_CAST")

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
import de.robv.android.xposed.XposedHelpers


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

fun Any?.getObj(name: String): Any? = XposedHelpers.getObjectField(this, name)

fun Any?.setObj(name: String, value: Any?) = XposedHelpers.setObjectField(this, name, value)

fun Any?.call(name: String, vararg args: Any?): Any? = XposedHelpers.callMethod(this, name, *args)

fun <T> Any?.getObjAs(name: String): T = getObj(name) as T
fun <T> Any?.getObjAsN(name: String): T? = getObj(name) as? T

fun Class<*>.callS(name: String, vararg args: Any?): Any? =
    XposedHelpers.callStaticMethod(this, name, *args)

fun Class<*>.getObjS(name: String) = XposedHelpers.getStaticObjectField(this, name)

fun <T> Class<*>.getObjSAs(name: String): T = getObjS(name) as T
fun <T> Class<*>.getObjSAsN(name: String): T? = getObjS(name) as? T

fun Class<*>.newInst(vararg args: Any?) = XposedHelpers.newInstance(this, *args)

fun ClassLoader.findClass(name: String): Class<*> = XposedHelpers.findClass(name, this)

fun ClassLoader.findClassN(name: String): Class<*>? = XposedHelpers.findClassIfExists(name, this)

fun ClassLoader.findClassOfN(vararg names: String): Class<*>? {
    for (name in names) {
        XposedHelpers.findClassIfExists(name, this)?.let { return it }
    }
    return null
}

fun ClassLoader.findClassOf(vararg names: String): Class<*> =
    findClassOfN(*names)?.let { it } ?: error("none of class found: ${names.joinToString(",")}")

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
