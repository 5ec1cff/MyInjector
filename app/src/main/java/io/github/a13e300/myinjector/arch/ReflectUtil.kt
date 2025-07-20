@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")

package io.github.a13e300.myinjector.arch

import io.github.a13e300.myinjector.bridge.Xposed
import java.lang.reflect.Member
import java.lang.reflect.Method

inline fun Any?.getObj(name: String): Any? = Xposed.getObjectField(this, name)

inline fun Any?.setObj(name: String, value: Any?) = Xposed.setObjectField(this, name, value)

inline fun Any?.call(name: String, vararg args: Any?): Any? = Xposed.callMethod(this, name, args)

inline fun <T> Any?.getObjAs(name: String): T = getObj(name) as T
inline fun <T> Any?.getObjAsN(name: String): T? = getObj(name) as? T

inline fun Class<*>.callS(name: String, vararg args: Any?): Any? =
    Xposed.callStaticMethod(this, name, args)

inline fun Class<*>.getObjS(name: String): Any? = Xposed.getStaticObjectField(this, name)
inline fun Class<*>.setObjS(name: String, value: Any?): Any? =
    Xposed.setStaticObjectField(this, name, value)

inline fun <T> Class<*>.getObjSAs(name: String): T = getObjS(name) as T
inline fun <T> Class<*>.getObjSAsN(name: String): T? = getObjS(name) as? T

inline fun Class<*>.newInst(vararg args: Any?) = Xposed.newInstance(this, args)
inline fun <T> Class<*>.newInstAs(vararg args: Any?): T = Xposed.newInstance(this, args) as T

inline fun ClassLoader.findClass(name: String): Class<*> = Class.forName(name, false, this)

inline fun ClassLoader.findClassN(name: String): Class<*>? = try {
    findClass(name)
} catch (_: ClassNotFoundException) {
    null
}

fun ClassLoader.findClassOfN(vararg names: String): Class<*>? {
    for (name in names) {
        findClassN(name)?.let { return it }
    }
    return null
}

inline fun ClassLoader.findClassOf(vararg names: String): Class<*> {
    for (name in names) {
        findClassN(name)?.let { return it }
    }
    error("none of class found: ${names.joinToString(",")}")
}

inline fun Method.deoptimize() {
    Xposed.deoptimizeMethod(this)
}

fun Class<*>.deoptimize(name: String) {
    declaredMethods.forEach {
        if (it.name == name) it.deoptimize()
    }
}

inline fun Member.callOrig(receiver: Any?, vararg args: Any?) =
    Xposed.invokeOriginalMethod(this, receiver, args)
