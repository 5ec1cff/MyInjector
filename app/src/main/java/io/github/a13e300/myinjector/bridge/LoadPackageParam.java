package io.github.a13e300.myinjector.bridge;

import android.content.pm.ApplicationInfo;

public class LoadPackageParam {
    public final String packageName;
    public final String processName;
    public final ClassLoader classLoader;
    public final ApplicationInfo appInfo;
    public final boolean isFirstApplication;

    public LoadPackageParam(String packageName, String processName, ClassLoader classLoader, ApplicationInfo info, boolean isFirst) {
        this.packageName = packageName;
        this.processName = processName;
        this.classLoader = classLoader;
        appInfo = info;
        isFirstApplication = isFirst;
    }
}
