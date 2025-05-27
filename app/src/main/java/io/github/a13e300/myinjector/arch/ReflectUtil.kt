@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package io.github.a13e300.myinjector.arch

import de.robv.android.xposed.XposedHelpers

inline fun Any?.getObj(name: String): Any? = XposedHelpers.getObjectField(this, name)

inline fun Any?.setObj(name: String, value: Any?) = XposedHelpers.setObjectField(this, name, value)

inline fun Any?.call(name: String, vararg args: Any?): Any? = XPBridge.callMethod(this, name, args)

inline fun <T> Any?.getObjAs(name: String): T = getObj(name) as T
inline fun <T> Any?.getObjAsN(name: String): T? = getObj(name) as? T

inline fun Class<*>.callS(name: String, vararg args: Any?): Any? =
    XPBridge.callStaticMethod(this, name, args)

inline fun Class<*>.getObjS(name: String): Any? = XposedHelpers.getStaticObjectField(this, name)

inline fun <T> Class<*>.getObjSAs(name: String): T = getObjS(name) as T
inline fun <T> Class<*>.getObjSAsN(name: String): T? = getObjS(name) as? T

inline fun Class<*>.newInst(vararg args: Any?) = XPBridge.newInstance(this, args)
inline fun <T> Class<*>.newInstAs(vararg args: Any?): T = XPBridge.newInstance(this, args) as T

inline fun ClassLoader.findClass(name: String): Class<*> = XposedHelpers.findClass(name, this)

inline fun ClassLoader.findClassN(name: String): Class<*>? =
    XposedHelpers.findClassIfExists(name, this)

fun ClassLoader.findClassOfN(vararg names: String): Class<*>? {
    for (name in names) {
        XposedHelpers.findClassIfExists(name, this)?.let { return it }
    }
    return null
}

inline fun ClassLoader.findClassOf(vararg names: String): Class<*> {
    for (name in names) {
        XposedHelpers.findClassIfExists(name, this)?.let { return it }
    }
    error("none of class found: ${names.joinToString(",")}")
}
