package io.github.a13e300.myinjector.bridge;

import androidx.annotation.NonNull;

import de.robv.android.xposed.XC_MethodHook;

public abstract class MethodHookCallback extends XC_MethodHook {
    @Override
    protected final void beforeHookedMethod(MethodHookParam param) {
        beforeHook(new HookParam(param));
    }

    @Override
    protected final void afterHookedMethod(MethodHookParam param) {
        afterHook(new HookParam(param));
    }

    protected void beforeHook(@NonNull HookParam param) {

    }

    protected void afterHook(@NonNull HookParam param) {

    }
}
