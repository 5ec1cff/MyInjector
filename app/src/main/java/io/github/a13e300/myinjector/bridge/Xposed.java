package io.github.a13e300.myinjector.bridge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.a13e300.myinjector.Entry;
import io.github.a13e300.myinjector.LogKt;
import io.github.libxposed.api.XposedInterface;

public class Xposed {
    private static final ConcurrentHashMap<MemberCacheKey.Field, Optional<Field>> fieldCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MemberCacheKey.Method, Optional<Method>> methodCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MemberCacheKey.Constructor, Optional<Constructor<?>>> constructorCache = new ConcurrentHashMap<>();
    private static final WeakHashMap<Object, HashMap<String, Object>> additionalFields = new WeakHashMap<>();
    private static final HashMap<String, ThreadLocal<AtomicInteger>> sMethodDepth = new HashMap<>();

    private static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(e);
        }
    }

    public static Object callMethod(Object thiz, String name, Object[] args) {
        try {
            return findMethodBestMatch(thiz.getClass(), name, args).invoke(thiz, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object callStaticMethod(Class<?> clz, String name, Object[] args) {
        try {
            return findMethodBestMatch(clz, name, args).invoke(null, args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e);
        } catch (IllegalAccessException t) {
            throw new RuntimeException(t);
        }
    }

    public static Object newInstance(Class<?> clz, Object[] args) {
        try {
            return findConstructorBestMatch(clz, args).newInstance(args);
        } catch (InvocationTargetException e) {
            throw new InvocationTargetError(e.getCause());
        } catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Constructor<?> findConstructorExact(Class<?> clz, Class<?>[] clzs) {
        var key = new MemberCacheKey.Constructor(clz, clzs, true);

        return constructorCache.computeIfAbsent(key, k -> {
            try {
                Constructor<?> constructor = k.clazz.getDeclaredConstructor(k.parameters);
                constructor.setAccessible(true);
                return Optional.of(constructor);
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchMethodError(key.toString()));
    }

    public static Set<Unhook> hookAllMethods(Class<?> hookClass, String methodName, MethodHookCallback callback) {
        var methods = hookClass.getDeclaredMethods();
        var newSet = new HashSet<Unhook>();
        for (var m : methods) {
            if (methodName.equals(m.getName())) {
                newSet.add(hookMethod(m, callback));
            }
        }
        return newSet;
    }

    public static Set<Unhook> hookAllConstructors(Class<?> hookClass, MethodHookCallback callback) {
        var methods = hookClass.getDeclaredConstructors();
        var newSet = new HashSet<Unhook>();
        for (var m : methods) {
            newSet.add(hookMethod(m, callback));
        }
        return newSet;
    }

    public static Unhook hookMethod(Member method, MethodHookCallback callback) {
        var handle = Entry.instance.hook((Executable) method)
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(callback);
        return new Unhook(handle);
    }

    public static void deoptimizeMethod(Member method) {
        try {
            Entry.instance.deoptimize((Executable) method);
        } catch (Throwable t) {
            LogKt.logE("deoptimize " + method, t);
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            findField(clazz, fieldName).set(null, value);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // copied from https://github.com/libxposed/helper/blob/new/legacy/src/main/java/de/robv/android/xposed/XposedHelpers.java

    public static Object getAdditionalInstanceField(Object obj, String key) {
        if (obj == null)
            throw new NullPointerException("object must not be null");
        if (key == null)
            throw new NullPointerException("key must not be null");

        HashMap<String, Object> objectFields;
        synchronized (additionalFields) {
            objectFields = additionalFields.get(obj);
            if (objectFields == null)
                return null;
        }

        synchronized (objectFields) {
            return objectFields.get(key);
        }
    }

    public static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        if (obj == null)
            throw new NullPointerException("object must not be null");
        if (key == null)
            throw new NullPointerException("key must not be null");

        HashMap<String, Object> objectFields;
        synchronized (additionalFields) {
            objectFields = additionalFields.get(obj);
            if (objectFields == null) {
                objectFields = new HashMap<>();
                additionalFields.put(obj, objectFields);
            }
        }

        synchronized (objectFields) {
            return objectFields.put(key, value);
        }
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (method instanceof Method) {
            return Entry.instance.getInvoker((Method) method)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(thisObject, args);
        } else if (method instanceof Constructor<?>) {
            return Entry.instance.getInvoker((Constructor<?>) method)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(thisObject, args);
        } else {
            throw new IllegalArgumentException("");
        }
    }

    /**
     * Look up a field in a class and set it to accessible.
     *
     * @param clazz     The class which either declares or inherits the field.
     * @param fieldName The field name.
     * @return A reference to the field.
     * @throws NoSuchFieldError In case the field was not found.
     */
    public static Field findField(Class<?> clazz, String fieldName) {
        var key = new MemberCacheKey.Field(clazz, fieldName);

        return fieldCache.computeIfAbsent(key, k -> {
            try {
                Field newField = findFieldRecursiveImpl(k.clazz, k.name);
                newField.setAccessible(true);
                return Optional.of(newField);
            } catch (NoSuchFieldException e) {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchFieldError(key.toString()));
    }

    /**
     * Look up and return a field if it exists.
     * Like {@link #findField}, but doesn't throw an exception if the field doesn't exist.
     *
     * @param clazz     The class which either declares or inherits the field.
     * @param fieldName The field name.
     * @return A reference to the field, or {@code null} if it doesn't exist.
     */
    public static Field findFieldIfExists(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName);
        } catch (NoSuchFieldError e) {
            return null;
        }
    }

    private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null) // MyInjector-changed: remove `clazz.equals(Object.class)`
                    break;

                try {
                    return clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw e;
        }
    }

    /**
     * Returns the first field of the given type in a class.
     * Might be useful for Proguard'ed classes to identify fields with unique types.
     *
     * @param clazz The class which either declares or inherits the field.
     * @param type  The type of the field.
     * @return A reference to the first field of the given type.
     * @throws NoSuchFieldError In case no matching field was not found.
     */
    public static Field findFirstFieldByExactType(Class<?> clazz, Class<?> type) {
        Class<?> clz = clazz;
        do {
            for (Field field : clz.getDeclaredFields()) {
                if (field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
        } while ((clz = clz.getSuperclass()) != null);

        throw new NoSuchFieldError("Field of type " + type.getName() + " in class " + clazz.getName());
    }

    /**
     * Look up a method in a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     */
    public static Method findMethodExact(Class<?> clazz, String methodName, Object[] parameterTypes) {
        return findMethodExact(clazz, methodName, getParameterClasses(clazz.getClassLoader(), parameterTypes));
    }

    /**
     * Look up and return a method if it exists.
     * See {@link #findMethodExactIfExists(String, ClassLoader, String, Object...)} for details.
     */
    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object[] parameterTypes) {
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (ClassNotFoundError | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * The method must be declared or overridden in the given class.
     *
     * @param className      The name of the class which implements the method.
     * @param classLoader    The class loader for resolving the target and parameter classes.
     * @param methodName     The target method name.
     * @param parameterTypes The parameter types of the target method.
     * @return A reference to the method.
     * @throws NoSuchMethodError  In case the method was not found.
     * @throws ClassNotFoundError In case the target class or one of the parameter types couldn't be resolved.
     */
    public static Method findMethodExact(String className, ClassLoader classLoader, String methodName, Object[] parameterTypes) {
        return findMethodExact(findClass(className, classLoader), methodName, getParameterClasses(classLoader, parameterTypes));
    }

    /**
     * Look up and return a method if it exists.
     * Like {@link #findMethodExact(String, ClassLoader, String, Object...)}, but doesn't throw an
     * exception if the method doesn't exist.
     *
     * @param className      The name of the class which implements the method.
     * @param classLoader    The class loader for resolving the target and parameter classes.
     * @param methodName     The target method name.
     * @param parameterTypes The parameter types of the target method.
     * @return A reference to the method, or {@code null} if it doesn't exist.
     */
    public static Method findMethodExactIfExists(String className, ClassLoader classLoader, String methodName, Object[] parameterTypes) {
        try {
            return findMethodExact(className, classLoader, methodName, parameterTypes);
        } catch (ClassNotFoundError | NoSuchMethodError e) {
            return null;
        }
    }

    /**
     * Look up a method in a class and set it to accessible.
     * See {@link #findMethodExact(String, ClassLoader, String, Object...)} for details.
     *
     * <p>This variant requires that you already have reference to all the parameter types.
     */
    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        var key = new MemberCacheKey.Method(clazz, methodName, parameterTypes, true);

        return methodCache.computeIfAbsent(key, k -> {
            try {
                Method method = k.clazz.getDeclaredMethod(k.name, k.parameters);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchMethodError(key.toString()));
    }

    /**
     * Returns an array of all methods declared/overridden in a class with the specified parameter types.
     *
     * <p>The return type is optional, it will not be compared if it is {@code null}.
     * Use {@code void.class} if you want to search for methods returning nothing.
     *
     * @param clazz          The class to look in.
     * @param returnType     The return type, or {@code null} (see above).
     * @param parameterTypes The parameter types.
     * @return An array with matching methods, all set to accessible already.
     */
    public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>[] parameterTypes) {
        var result = new LinkedList<Method>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (returnType != null && returnType != method.getReturnType())
                continue;

            Class<?>[] methodParameterTypes = method.getParameterTypes();
            if (parameterTypes.length != methodParameterTypes.length)
                continue;

            boolean match = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i] != methodParameterTypes[i]) {
                    match = false;
                    break;
                }
            }

            if (!match)
                continue;

            method.setAccessible(true);
            result.add(method);
        }
        return result.toArray(new Method[0]);
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * <p>This does'nt only look for exact matches, but for the best match. All considered candidates
     * must be compatible with the given parameter types, i.e. the parameters must be assignable
     * to the method's formal parameters. Inherited methods are considered here.
     *
     * @param clazz          The class which declares, inherits or overrides the method.
     * @param methodName     The method name.
     * @param parameterTypes The types of the method's parameters.
     * @return A reference to the best-matching method.
     * @throws NoSuchMethodError In case no suitable method was found.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        // find the exact matching method first
        try {
            return findMethodExact(clazz, methodName, parameterTypes);
        } catch (NoSuchMethodError ignored) {
        }

        // then find the best match
        var key = new MemberCacheKey.Method(clazz, methodName, parameterTypes, false);

        return methodCache.computeIfAbsent(key, k -> {
            Method bestMatch = null;
            Class<?> clz = k.clazz;
            boolean considerPrivateMethods = true;
            do {
                for (Method method : clz.getDeclaredMethods()) {
                    // don't consider private methods of superclasses
                    if (!considerPrivateMethods && Modifier.isPrivate(method.getModifiers()))
                        continue;

                    // compare name and parameters
                    if (method.getName().equals(k.name) && ClassUtils.isAssignable(
                            k.parameters,
                            method.getParameterTypes(),
                            true)) {
                        // get accessible version of method
                        if (bestMatch == null || MemberUtils.compareMethodFit(
                                method,
                                bestMatch,
                                k.parameters) < 0) {
                            bestMatch = method;
                        }
                    }
                }
                considerPrivateMethods = false;
            } while ((clz = clz.getSuperclass()) != null);

            if (bestMatch != null) {
                bestMatch.setAccessible(true);
                return Optional.of(bestMatch);
            } else {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchMethodError(key.toString()));
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * <p>See {@link #findMethodBestMatch(Class, String, Class...)} for details. This variant
     * determines the parameter types from the classes of the given objects.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object[] args) {
        return findMethodBestMatch(clazz, methodName, getParameterTypes(args));
    }

    /**
     * Look up a constructor in a class and set it to accessible.
     *
     * <p>See {@link #findMethodBestMatch(Class, String, Class...)} for details.
     */
    public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>[] parameterTypes) {
        // find the exact matching constructor first
        try {
            return findConstructorExact(clazz, parameterTypes);
        } catch (NoSuchMethodError ignored) {
        }

        // then find the best match
        var key = new MemberCacheKey.Constructor(clazz, parameterTypes, false);

        return constructorCache.computeIfAbsent(key, k -> {
            Constructor<?> bestMatch = null;
            Constructor<?>[] constructors = k.clazz.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                // compare name and parameters
                if (ClassUtils.isAssignable(
                        k.parameters,
                        constructor.getParameterTypes(),
                        true)) {
                    // get accessible version of method
                    if (bestMatch == null || MemberUtils.compareConstructorFit(
                            constructor,
                            bestMatch,
                            k.parameters) < 0) {
                        bestMatch = constructor;
                    }
                }
            }

            if (bestMatch != null) {
                bestMatch.setAccessible(true);
                return Optional.of(bestMatch);
            } else {
                return Optional.empty();
            }
        }).orElseThrow(() -> new NoSuchMethodError(key.toString()));
    }

    public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Object[] args) {
        return findConstructorBestMatch(clazz, getParameterTypes(args));
    }

    /**
     * Look up a method in a class and set it to accessible.
     *
     * <p>See {@link #findMethodBestMatch(Class, String, Class...)} for details. This variant
     * determines the parameter types from the classes of the given objects. For any item that is
     * {@code null}, the type is taken from {@code parameterTypes} instead.
     */
    public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Class<?>[] argsClasses = null;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != null)
                continue;
            if (argsClasses == null)
                argsClasses = getParameterTypes(args);
            parameterTypes[i] = argsClasses[i];
        }
        return findMethodBestMatch(clazz, methodName, parameterTypes);
    }

    /**
     * Returns an array with the classes of the given objects.
     */
    public static Class<?>[] getParameterTypes(Object[] args) {
        Class<?>[] clazzes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            clazzes[i] = (args[i] != null) ? args[i].getClass() : null;
        }
        return clazzes;
    }

    /**
     * Retrieve classes from an array, where each element might either be a Class
     * already, or a String with the full class name.
     */
    private static Class<?>[] getParameterClasses(ClassLoader classLoader, Object[] parameterTypesAndCallback) {
        Class<?>[] parameterClasses = null;
        for (int i = parameterTypesAndCallback.length - 1; i >= 0; i--) {
            Object type = parameterTypesAndCallback[i];
            if (type == null)
                throw new ClassNotFoundError("parameter type must not be null", null);

            // ignore trailing callback
            if (type instanceof MethodHookCallback)
                continue;

            if (parameterClasses == null)
                parameterClasses = new Class<?>[i + 1];

            if (type instanceof Class)
                parameterClasses[i] = (Class<?>) type;
            else if (type instanceof String)
                parameterClasses[i] = findClass((String) type, classLoader);
            else
                throw new ClassNotFoundError("parameter type must either be specified as Class or String, got " + type, null);
        }

        // if there are no arguments for the method
        if (parameterClasses == null)
            parameterClasses = new Class<?>[0];

        return parameterClasses;
    }

    private static String getParametersString(Class<?>[] clazzes) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Class<?> clazz : clazzes) {
            if (first)
                first = false;
            else
                sb.append(",");

            if (clazz != null)
                sb.append(clazz.getCanonicalName());
            else
                sb.append("null");
        }
        sb.append(")");
        return sb.toString();
    }

    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) {
            super(cause);
        }

        public ClassNotFoundError(String detailMessage, Throwable cause) {
            super(detailMessage, cause);
        }
    }

    public static class InvocationTargetError extends Error {
        public InvocationTargetError(Throwable cause) {
            super(cause);
        }

        public InvocationTargetError(String detailMessage, Throwable cause) {
            super(detailMessage, cause);
        }
    }

    /**
     * Note that we use object key instead of string here, because string calculation will lose all
     * the benefits of 'HashMap', this is basically the solution of performance traps.
     * <p>
     * So in fact we only need to use the structural comparison results of the reflection object.
     *
     * @see <a href="https://github.com/RinOrz/LSPosed/blob/a44e1f1cdf0c5e5ebfaface828e5907f5425df1b/benchmark/src/result/ReflectionCacheBenchmark.json">benchmarks for ART</a>
     * @see <a href="https://github.com/meowool-catnip/cloak/blob/main/api/src/benchmark/kotlin/com/meowool/cloak/ReflectionObjectAccessTests.kt#L37-L65">benchmarks for JVM</a>
     */
    private abstract static class MemberCacheKey {
        private final int hash;

        protected MemberCacheKey(int hash) {
            this.hash = hash;
        }

        @Override
        public abstract boolean equals(@Nullable Object obj);

        @Override
        public final int hashCode() {
            return hash;
        }

        static final class Constructor extends MemberCacheKey {
            private final Class<?> clazz;
            private final Class<?>[] parameters;
            private final boolean isExact;

            public Constructor(Class<?> clazz, Class<?>[] parameters, boolean isExact) {
                super(31 * Objects.hash(clazz, isExact) + Arrays.hashCode(parameters));
                this.clazz = clazz;
                this.parameters = parameters;
                this.isExact = isExact;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Constructor that)) return false;
                return isExact == that.isExact && Objects.equals(clazz, that.clazz) && Arrays.equals(parameters, that.parameters);
            }

            @NonNull
            @Override
            public String toString() {
                var str = clazz.getName() + getParametersString(parameters);
                if (isExact) {
                    return str + "#exact";
                } else {
                    return str;
                }
            }
        }

        static final class Field extends MemberCacheKey {
            private final Class<?> clazz;
            private final String name;

            public Field(Class<?> clazz, String name) {
                super(Objects.hash(clazz, name));
                this.clazz = clazz;
                this.name = name;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Field field)) return false;
                return Objects.equals(clazz, field.clazz) && Objects.equals(name, field.name);
            }

            @NonNull
            @Override
            public String toString() {
                return clazz.getName() + "#" + name;
            }
        }

        static final class Method extends MemberCacheKey {
            private final Class<?> clazz;
            private final String name;
            private final Class<?>[] parameters;
            private final boolean isExact;

            public Method(Class<?> clazz, String name, Class<?>[] parameters, boolean isExact) {
                super(31 * Objects.hash(clazz, name, isExact) + Arrays.hashCode(parameters));
                this.clazz = clazz;
                this.name = name;
                this.parameters = parameters;
                this.isExact = isExact;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Method method)) return false;
                return isExact == method.isExact && Objects.equals(clazz, method.clazz) && Objects.equals(name, method.name) && Arrays.equals(parameters, method.parameters);
            }

            @NonNull
            @Override
            public String toString() {
                return clazz.getName() + '#' + name + getParametersString(parameters) + (isExact ? "#exact" : "#bestmatch");
            }
        }
    }

    // https://github.com/apache/commons-lang/blob/18979f30e963081e5e0f018448dd3aadc429f51e/src/main/java/org/apache/commons/lang3/ClassUtils.java#L1336-L1392
    static class ClassUtils {
        private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP = new HashMap<>();
        private static final Map<Class<?>, Class<?>> WRAPPER_PRIMITIVE_MAP = new HashMap<>();

        static {
            PRIMITIVE_WRAPPER_MAP.put(Boolean.TYPE, Boolean.class);
            PRIMITIVE_WRAPPER_MAP.put(Byte.TYPE, Byte.class);
            PRIMITIVE_WRAPPER_MAP.put(Character.TYPE, Character.class);
            PRIMITIVE_WRAPPER_MAP.put(Short.TYPE, Short.class);
            PRIMITIVE_WRAPPER_MAP.put(Integer.TYPE, Integer.class);
            PRIMITIVE_WRAPPER_MAP.put(Long.TYPE, Long.class);
            PRIMITIVE_WRAPPER_MAP.put(Double.TYPE, Double.class);
            PRIMITIVE_WRAPPER_MAP.put(Float.TYPE, Float.class);
            PRIMITIVE_WRAPPER_MAP.put(Void.TYPE, Void.TYPE);
        }

        static {
            PRIMITIVE_WRAPPER_MAP.forEach((primitiveClass, wrapperClass) -> {
                if (!primitiveClass.equals(wrapperClass)) {
                    WRAPPER_PRIMITIVE_MAP.put(wrapperClass, primitiveClass);
                }
            });
        }

        public static Class<?> primitiveToWrapper(final Class<?> cls) {
            return cls != null && cls.isPrimitive() ? PRIMITIVE_WRAPPER_MAP.get(cls) : cls;
        }

        private static Class<?> wrapperToPrimitive(final Class<?> cls) {
            return WRAPPER_PRIMITIVE_MAP.get(cls);
        }

        public static boolean isAssignable(Class<?> cls, final Class<?> toClass, final boolean autoboxing) {
            if (toClass == null) {
                return false;
            }
            // have to check for null, as isAssignableFrom doesn't
            if (cls == null) {
                return !toClass.isPrimitive();
            }
            // autoboxing:
            if (autoboxing) {
                if (cls.isPrimitive() && !toClass.isPrimitive()) {
                    cls = primitiveToWrapper(cls);
                    if (cls == null) {
                        return false;
                    }
                }
                if (toClass.isPrimitive() && !cls.isPrimitive()) {
                    cls = wrapperToPrimitive(cls);
                    if (cls == null) {
                        return false;
                    }
                }
            }
            if (cls.equals(toClass)) {
                return true;
            }
            if (cls.isPrimitive()) {
                if (!toClass.isPrimitive()) {
                    return false;
                }
                if (Integer.TYPE.equals(cls)) {
                    return Long.TYPE.equals(toClass) || Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
                }
                if (Long.TYPE.equals(cls)) {
                    return Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
                }
                if (Boolean.TYPE.equals(cls)) {
                    return false;
                }
                if (Double.TYPE.equals(cls)) {
                    return false;
                }
                if (Float.TYPE.equals(cls)) {
                    return Double.TYPE.equals(toClass);
                }
                if (Character.TYPE.equals(cls) || Short.TYPE.equals(cls)) {
                    return Integer.TYPE.equals(toClass) || Long.TYPE.equals(toClass) || Float.TYPE.equals(toClass) || Double.TYPE.equals(toClass);
                }
                if (Byte.TYPE.equals(cls)) {
                    return Short.TYPE.equals(toClass) || Integer.TYPE.equals(toClass) || Long.TYPE.equals(toClass) || Float.TYPE.equals(toClass)
                            || Double.TYPE.equals(toClass);
                }
                // should never get here
                return false;
            }
            return toClass.isAssignableFrom(cls);
        }

        public static boolean isAssignable(@NonNull Class<?>[] classArray, @NonNull Class<?>[] toClassArray, final boolean autoboxing) {
            if (classArray.length != toClassArray.length) return false;
            for (int i = 0; i < classArray.length; i++) {
                if (!isAssignable(classArray[i], toClassArray[i], autoboxing)) {
                    return false;
                }
            }
            return true;
        }
    }

    static class MemberUtils {
        private static final int ACCESS_TEST = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
        /**
         * Array of primitive number types ordered by "promotability" from narrow to wide.
         */
        private static final Class<?>[] WIDENING_PRIMITIVE_TYPES = {
                // @formatter:off
                Byte.TYPE,      // byte
                Short.TYPE,     // short
                Character.TYPE, // char
                Integer.TYPE,   // int
                Long.TYPE,      // long
                Float.TYPE,     // float
                Double.TYPE     // double
                // @formatter:on
        };

        /**
         * Compares the relative fitness of two Constructors in terms of how well they match a set of runtime parameter types, such that a list ordered by the
         * results of the comparison would return the best match first (least).
         *
         * @param left   the "left" Constructor.
         * @param right  the "right" Constructor.
         * @param actual the runtime parameter types to match against. {@code left}/{@code right}.
         * @return int consistent with {@code compare} semantics.
         */
        static int compareConstructorFit(final Constructor<?> left, final Constructor<?> right, final Class<?>[] actual) {
            return compareParameterTypes(Executable.of(left), Executable.of(right), actual);
        }

        /**
         * Compares the relative fitness of two Methods in terms of how well they match a set of runtime parameter types, such that a list ordered by the results of
         * the comparison would return the best match first (least).
         *
         * @param left   the "left" Method.
         * @param right  the "right" Method.
         * @param actual the runtime parameter types to match against. {@code left}/{@code right}.
         * @return int consistent with {@code compare} semantics.
         */
        static int compareMethodFit(final Method left, final Method right, final Class<?>[] actual) {
            return compareParameterTypes(Executable.of(left), Executable.of(right), actual);
        }

        /**
         * Compares the relative fitness of two Executables in terms of how well they match a set of runtime parameter types, such that a list ordered by the
         * results of the comparison would return the best match first (least).
         *
         * @param left   the "left" Executable.
         * @param right  the "right" Executable.
         * @param actual the runtime parameter types to match against. {@code left}/{@code right}.
         * @return int consistent with {@code compare} semantics.
         */
        private static int compareParameterTypes(final Executable left, final Executable right, final Class<?>[] actual) {
            final float leftCost = getTotalTransformationCost(actual, left);
            final float rightCost = getTotalTransformationCost(actual, right);
            return Float.compare(leftCost, rightCost);
        }

        /**
         * Gets the number of steps needed to turn the source class into the destination class. This represents the number of steps in the object hierarchy graph.
         *
         * @param srcClass  The source class.
         * @param destClass The destination class.
         * @return The cost of transforming an object.
         */
        private static float getObjectTransformationCost(Class<?> srcClass, final Class<?> destClass) {
            if (destClass.isPrimitive()) {
                return getPrimitivePromotionCost(srcClass, destClass);
            }
            float cost = 0.0f;
            while (srcClass != null && !destClass.equals(srcClass)) {
                if (destClass.isInterface() && ClassUtils.isAssignable(srcClass, destClass, true)) {
                    // slight penalty for interface match.
                    // we still want an exact match to override an interface match,
                    // but
                    // an interface match should override anything where we have to
                    // get a superclass.
                    cost += 0.25f;
                    break;
                }
                cost++;
                srcClass = srcClass.getSuperclass();
            }
            /*
             * If the destination class is null, we've traveled all the way up to an Object match. We'll penalize this by adding 1.5 to the cost.
             */
            if (srcClass == null) {
                cost += 1.5f;
            }
            return cost;
        }

        /**
         * Gets the number of steps required to promote a primitive to another type.
         *
         * @param srcClass  the (primitive) source class.
         * @param destClass the (primitive) destination class.
         * @return The cost of promoting the primitive.
         */
        private static float getPrimitivePromotionCost(final Class<?> srcClass, final Class<?> destClass) {
            if (srcClass == null) {
                return 1.5f;
            }
            float cost = 0.0f;
            Class<?> cls = srcClass;
            if (!cls.isPrimitive()) {
                // slight unwrapping penalty
                cost += 0.1f;
                cls = ClassUtils.wrapperToPrimitive(cls);
            }
            // Increase the cost as the loop widens the type.
            for (int i = 0; cls != destClass && i < WIDENING_PRIMITIVE_TYPES.length; i++) {
                if (cls == WIDENING_PRIMITIVE_TYPES[i]) {
                    cost += 0.1f;
                    if (i < WIDENING_PRIMITIVE_TYPES.length - 1) {
                        cls = WIDENING_PRIMITIVE_TYPES[i + 1];
                    }
                }
            }
            return cost;
        }

        /**
         * Gets the sum of the object transformation cost for each class in the source argument list.
         *
         * @param srcArgs    The source arguments.
         * @param executable The executable to calculate transformation costs for.
         * @return The total transformation cost.
         */
        private static float getTotalTransformationCost(final Class<?>[] srcArgs, final Executable executable) {
            final Class<?>[] destArgs = executable.getParameterTypes();
            final boolean isVarArgs = executable.isVarArgs();
            // "source" and "destination" are the actual and declared args respectively.
            float totalCost = 0.0f;
            final long normalArgsLen = isVarArgs ? destArgs.length - 1 : destArgs.length;
            if (srcArgs.length < normalArgsLen) {
                return Float.MAX_VALUE;
            }
            for (int i = 0; i < normalArgsLen; i++) {
                totalCost += getObjectTransformationCost(srcArgs[i], destArgs[i]);
            }
            if (isVarArgs) {
                // When isVarArgs is true, srcArgs and dstArgs may differ in length.
                // There are two special cases to consider:
                final boolean noVarArgsPassed = srcArgs.length < destArgs.length;
                final boolean explicitArrayForVarargs = srcArgs.length == destArgs.length && srcArgs[srcArgs.length - 1] != null
                        && srcArgs[srcArgs.length - 1].isArray();
                final float varArgsCost = 0.001f;
                final Class<?> destClass = destArgs[destArgs.length - 1].getComponentType();
                if (noVarArgsPassed) {
                    // When no varargs passed, the best match is the most generic matching type, not the most specific.
                    totalCost += getObjectTransformationCost(destClass, Object.class) + varArgsCost;
                } else if (explicitArrayForVarargs) {
                    final Class<?> sourceClass = srcArgs[srcArgs.length - 1].getComponentType();
                    totalCost += getObjectTransformationCost(sourceClass, destClass) + varArgsCost;
                } else {
                    // This is typical varargs case.
                    for (int i = destArgs.length - 1; i < srcArgs.length; i++) {
                        final Class<?> srcClass = srcArgs[i];
                        totalCost += getObjectTransformationCost(srcClass, destClass) + varArgsCost;
                    }
                }
            }
            return totalCost;
        }

        static boolean isMatchingConstructor(final Constructor<?> method, final Class<?>[] parameterTypes) {
            return isMatchingExecutable(Executable.of(method), parameterTypes);
        }

        private static boolean isMatchingExecutable(final Executable method, final Class<?>[] parameterTypes) {
            final Class<?>[] methodParameterTypes = method.getParameterTypes();
            if (ClassUtils.isAssignable(parameterTypes, methodParameterTypes, true)) {
                return true;
            }
            if (method.isVarArgs()) {
                int i;
                for (i = 0; i < methodParameterTypes.length - 1 && i < parameterTypes.length; i++) {
                    if (!ClassUtils.isAssignable(parameterTypes[i], methodParameterTypes[i], true)) {
                        return false;
                    }
                }
                final Class<?> varArgParameterType = methodParameterTypes[methodParameterTypes.length - 1].getComponentType();
                for (; i < parameterTypes.length; i++) {
                    if (!ClassUtils.isAssignable(parameterTypes[i], varArgParameterType, true)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        static boolean isMatchingMethod(final Method method, final Class<?>[] parameterTypes) {
            return isMatchingExecutable(Executable.of(method), parameterTypes);
        }

        private static final class Executable {

            private final Class<?>[] parameterTypes;
            private final boolean isVarArgs;

            private Executable(final Constructor<?> constructor) {
                parameterTypes = constructor.getParameterTypes();
                isVarArgs = constructor.isVarArgs();
            }
            private Executable(final Method method) {
                parameterTypes = method.getParameterTypes();
                isVarArgs = method.isVarArgs();
            }

            private static Executable of(final Constructor<?> constructor) {
                return new Executable(constructor);
            }

            private static Executable of(final Method method) {
                return new Executable(method);
            }

            public Class<?>[] getParameterTypes() {
                return parameterTypes;
            }

            public boolean isVarArgs() {
                return isVarArgs;
            }
        }
    }
}
