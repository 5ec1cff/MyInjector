package io.github.a13e300.myinjector.arch

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Method


typealias HookCallback = (MethodHookParam) -> Unit
typealias HookReplacement = (MethodHookParam) -> Any?

// Class.allMethods
inline fun Class<*>.hookAll(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (cond()) before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            if (cond()) after(param)
        }
    })


inline fun Class<*>.hookAllAfter(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookAll(name, cond = cond, after = fn)


inline fun Class<*>.hookAllBefore(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookAll(name, cond = cond, before = fn)

inline fun Class<*>.hookAllReplace(
    name: String,
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            if (cond()) {
                try {
                    param.result = replacement(param)
                } catch (t: Throwable) {
                    param.throwable = t
                }
            }
        }
    })

fun Class<*>.hookAllConstant(name: String, constant: Any?): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, XC_MethodReplacement.returnConstant(constant))

fun Class<*>.hookAllConstantIf(
    name: String,
    constant: Any?,
    cond: () -> Boolean
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            if (cond()) param.result = constant
        }
    })

fun Class<*>.hookAllNop(name: String): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, XC_MethodReplacement.DO_NOTHING)

inline fun Class<*>.hookAllNopIf(name: String, crossinline cond: () -> Boolean) =
    hookAllBefore(name) {
        if (cond()) it.result = null
    }
// Class.allMethods END

// Class.allConstructors
inline fun Class<*>.hookAllC(
    crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllConstructors(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (cond()) before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            if (cond()) after(param)
        }
    })


inline fun Class<*>.hookAllCAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookAllC(cond = cond, after = fn)

inline fun Class<*>.hookAllCBefore(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookAllC(cond = cond, before = fn)
// Class.allConstructors END

// Method.hook
inline fun Method.hook(
    crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (cond()) before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            if (cond()) after(param)
        }
    })

inline fun Method.hookAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hook(cond = cond, after = fn)

inline fun Method.hookBefore(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hook(cond = cond, before = fn)


inline fun Method.hookReplace(
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            if (cond()) {
                try {
                    param.result = replacement(param)
                } catch (t: Throwable) {
                    param.throwable = t
                }
            }
        }
    })

fun Method.hookConstant(constant: Any?): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, XC_MethodReplacement.returnConstant(constant))

fun Method.hookConstantIf(constant: Any?, cond: () -> Boolean): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            if (cond()) param.result = constant
        }
    })

fun Method.hookNop(): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, XC_MethodReplacement.DO_NOTHING)

inline fun Method.hookNopIf(crossinline cond: () -> Boolean) =
    hookBefore {
        if (cond()) it.result = null
    }
// Method.hook END

// Constructor.hook
inline fun Constructor<*>.hook(
    crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (cond()) before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            if (cond()) after(param)
        }
    })

inline fun Constructor<*>.hookAfter(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) =
    hook(cond = cond, after = fn)

inline fun Constructor<*>.hookBefore(
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) =
    hook(cond = cond, before = fn)
// Constructor.hook END

// Class.findAndHook
inline fun Class<*>.hook(
    name: String, vararg types: Class<*>, crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hook(cond, before, after)

inline fun Class<*>.hookAfter(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hook(name, *types, cond = cond, after = fn)

inline fun Class<*>.hookBefore(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hook(name, *types, cond = cond, before = fn)

inline fun Class<*>.hookReplace(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline replacement: HookReplacement
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookReplace(cond, replacement)

fun Class<*>.hookConstant(
    name: String,
    vararg types: Class<*>,
    constant: Any?
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookConstant(constant)

fun Class<*>.hookConstantIf(
    name: String,
    vararg types: Class<*>,
    constant: Any?, cond: () -> Boolean
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookConstantIf(constant, cond)

fun Class<*>.hookNop(name: String, vararg types: Class<*>): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookNop()

inline fun Class<*>.hookNopIf(
    name: String,
    vararg types: Class<*>,
    crossinline cond: () -> Boolean
) =
    hookBefore(name, *types) {
        if (cond()) it.result = null
    }
// Class.findAndHook END

// Class.findAndHookConstructor
inline fun Class<*>.hookC(
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XPBridge.findConstructorExact(this, types).hook(cond = cond, before = before, after = after)

inline fun Class<*>.hookCAfter(
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookC(*types, cond = cond, after = fn)

inline fun Class<*>.hookCBefore(
    vararg types: Class<*>,
    crossinline cond: () -> Boolean = { true },
    crossinline fn: HookCallback
) = hookC(*types, cond = cond, before = fn)
// Class.findAndHookConstructor END
