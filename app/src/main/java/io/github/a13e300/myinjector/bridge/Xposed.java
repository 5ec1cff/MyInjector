package io.github.a13e300.myinjector.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.a13e300.myinjector.LogKt;

// This class was used to wrap vararg methods by array argument methods to prevent from coping array
// when using spread operator (*) to call in kotlin.
// These methods will be inlined when r8 is enabled.
// See also:
// https://youtrack.jetbrains.com/issue/KT-17043/Do-not-create-new-arrays-for-pass-through-vararg-parameters
// https://youtrack.jetbrains.com/issue/KT-27538/Avoid-Arrays.copyOf-when-inlining-a-function-call-with-vararg
// Now it's just a wrapper
public class Xposed {
    private static final Method deoptimizeMethod;

    static {
        Method m = null;
        try {
            m = XposedBridge.class.getDeclaredMethod("deoptimizeMethod", Member.class);
            m.setAccessible(true);
        } catch (Throwable t) {
            LogKt.logE("get deoptimizeMethod", t);
        }
        deoptimizeMethod = m;
    }
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

    public static Set<Unhook> hookAllMethods(Class<?> hookClass, String methodName, MethodHookCallback callback) {
        var unhooks = XposedBridge.hookAllMethods(hookClass, methodName, callback);
        var newSet = new HashSet<Unhook>();
        for (var h : unhooks) {
            newSet.add(new Unhook(h));
        }
        return newSet;
    }

    public static Set<Unhook> hookAllConstructors(Class<?> hookClass, MethodHookCallback callback) {
        var unhooks = XposedBridge.hookAllConstructors(hookClass, callback);
        var newSet = new HashSet<Unhook>();
        for (var h : unhooks) {
            newSet.add(new Unhook(h));
        }
        return newSet;
    }

    public static Unhook hookMethod(Member method, MethodHookCallback callback) {
        return new Unhook(XposedBridge.hookMethod(method, callback));
    }

    public static void deoptimizeMethod(Member method) {
        if (deoptimizeMethod != null) {
            try {
                deoptimizeMethod.invoke(null, method);
            } catch (Throwable t) {
                LogKt.logE("deoptimize " + method, t);
            }
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return XposedHelpers.getObjectField(obj, fieldName);
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        XposedHelpers.setObjectField(obj, fieldName, value);
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        return XposedHelpers.getStaticObjectField(clazz, fieldName);
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        XposedHelpers.setStaticObjectField(clazz, fieldName, value);
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        return XposedHelpers.getAdditionalInstanceField(obj, key);
    }

    public static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        return XposedHelpers.setAdditionalInstanceField(obj, key, value);
    }
}
