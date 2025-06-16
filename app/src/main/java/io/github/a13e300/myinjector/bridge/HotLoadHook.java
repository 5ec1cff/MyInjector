package io.github.a13e300.myinjector.bridge;

public abstract class HotLoadHook {
    public abstract void onLoad(LoadPackageParam param);

    public abstract void onUnload();
}
