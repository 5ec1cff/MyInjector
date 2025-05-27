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
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            after(param)
        }
    })


inline fun Class<*>.hookAllAfter(name: String, crossinline fn: HookCallback) =
    hookAll(name, after = fn)


inline fun Class<*>.hookAllBefore(name: String, crossinline fn: HookCallback) =
    hookAll(name, before = fn)

inline fun Class<*>.hookAllReplace(
    name: String,
    crossinline replacement: HookReplacement
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? =
            replacement(param)
    })

fun Class<*>.hookAllConstant(name: String, constant: Any?): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, XC_MethodReplacement.returnConstant(constant))


fun Class<*>.hookAllNop(name: String): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllMethods(this, name, XC_MethodReplacement.DO_NOTHING)

inline fun Class<*>.hookAllNopIf(name: String, crossinline cond: () -> Boolean) =
    hookAllBefore(name) {
        if (cond()) it.result = null
    }
// Class.allMethods END

// Class.allConstructors
inline fun Class<*>.hookAllC(
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): MutableSet<XC_MethodHook.Unhook> =
    XposedBridge.hookAllConstructors(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            after(param)
        }
    })


inline fun Class<*>.hookAllCAfter(crossinline fn: HookCallback) =
    hookAllC(after = fn)

inline fun Class<*>.hookAllCBefore(crossinline fn: HookCallback) =
    hookAllC(before = fn)

// Class.allConstructors END

// Method.hook
inline fun Method.hook(
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            after(param)
        }
    })

inline fun Method.hookAfter(crossinline fn: HookCallback) =
    hook(after = fn)

inline fun Method.hookBefore(crossinline fn: HookCallback) =
    hook(before = fn)


inline fun Method.hookReplace(crossinline replacement: HookReplacement): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodReplacement() {
        override fun replaceHookedMethod(param: XC_MethodHook.MethodHookParam): Any? =
            replacement(param)
    })

fun Method.hookConstant(constant: Any?): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, XC_MethodReplacement.returnConstant(constant))

fun Method.hookNop(): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, XC_MethodReplacement.DO_NOTHING)

inline fun Method.hookNopIf(crossinline cond: () -> Boolean) =
    hookBefore {
        if (cond()) it.result = null
    }
// Method.hook END

// Constructor.hook
inline fun Constructor<*>.hook(
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            before(param)
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            after(param)
        }
    })

inline fun Constructor<*>.hookAfter(crossinline fn: HookCallback) =
    hook(after = fn)

inline fun Constructor<*>.hookBefore(crossinline fn: HookCallback) =
    hook(before = fn)
// Constructor.hook END

// Class.findAndHook
inline fun Class<*>.hook(
    name: String, vararg types: Class<*>,
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hook(before, after)

inline fun Class<*>.hookAfter(name: String, vararg types: Class<*>, crossinline fn: HookCallback) =
    hook(name, *types, after = fn)

inline fun Class<*>.hookBefore(name: String, vararg types: Class<*>, crossinline fn: HookCallback) =
    hook(name, *types, before = fn)

inline fun Class<*>.hookReplace(
    name: String,
    vararg types: Class<*>,
    crossinline replacement: HookReplacement
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookReplace(replacement)

fun Class<*>.hookConstant(
    name: String,
    vararg types: Class<*>,
    constant: Any?
): XC_MethodHook.Unhook =
    XPBridge.findMethodExact(this, name, types).hookConstant(constant)

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
    crossinline before: HookCallback = {},
    crossinline after: HookCallback = {}
): XC_MethodHook.Unhook =
    XPBridge.findConstructorExact(this, types).hook(before, after)

inline fun Class<*>.hookCAfter(vararg types: Class<*>, crossinline fn: HookCallback) =
    hookC(*types, after = fn)

inline fun Class<*>.hookCBefore(vararg types: Class<*>, crossinline fn: HookCallback) =
    hookC(*types, before = fn)
// Class.findAndHookConstructor END
