package io.github.a13e300.myinjector.arch;

import dalvik.system.PathClassLoader;

public class HotLoadClassLoader extends PathClassLoader {
    private final ClassLoader parent;

    public HotLoadClassLoader(String dexPath, ClassLoader parent) {
        this(dexPath, null, parent);
    }

    public HotLoadClassLoader(String dexPath, String librarySearchPath, ClassLoader parent) {
        super(dexPath, librarySearchPath, parent);
        this.parent = parent;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("io.github.a13e300.myinjector.bridge.")) {
            return Class.forName(name, resolve, parent);
        }
        try {
            return findClass(name);
        } catch (ClassNotFoundException ignored) {

        }
        return super.loadClass(name, resolve);
    }
}
