package io.github.a13e300.myinjector.arch;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedHelpers;

// Wrap vararg methods by array argument methods to prevent from coping array
// when using spread operator (*) to call in kotlin.
// These methods will be inlined when r8 is enabled.
// See also:
// https://youtrack.jetbrains.com/issue/KT-17043/Do-not-create-new-arrays-for-pass-through-vararg-parameters
// https://youtrack.jetbrains.com/issue/KT-27538/Avoid-Arrays.copyOf-when-inlining-a-function-call-with-vararg
public class XPBridge {
    public static Object callMethod(Object thiz, String name, Object[] args) {
        return XposedHelpers.callMethod(thiz, name, args);
    }

    public static Object callStaticMethod(Class<?> clz, String name, Object[] args) {
        return XposedHelpers.callStaticMethod(clz, name, args);
    }

    public static Object newInstance(Class<?> clz, Object[] args) {
        return XposedHelpers.newInstance(clz, args);
    }

    public static Method findMethodExact(Class<?> clz, String name, Class<?>[] clzs) {
        return XposedHelpers.findMethodExact(clz, name, clzs);
    }

    public static Constructor<?> findConstructorExact(Class<?> clz, Class<?>[] clzs) {
        return XposedHelpers.findConstructorExact(clz, clzs);
    }
}
