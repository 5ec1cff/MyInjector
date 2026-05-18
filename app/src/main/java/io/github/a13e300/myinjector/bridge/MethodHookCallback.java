package io.github.a13e300.myinjector.bridge;

import androidx.annotation.NonNull;

import java.lang.reflect.Modifier;

import io.github.a13e300.myinjector.LogKt;
import io.github.libxposed.api.XposedInterface;

public abstract class MethodHookCallback implements XposedInterface.Hooker {
    @Override
    public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
        var param = new HookParam();
        param.args = chain.getArgs().toArray(new Object[0]);
        param.thisObject = chain.getThisObject();
        param.member = chain.getExecutable();
        try {
            beforeHook(param);
        } catch (Throwable t) {
            LogKt.logE("beforeHook: ", t);
        }
        if (!param.invoked) {
            try {
                if ((param.member.getModifiers() & Modifier.STATIC) != 0) {
                    param.result = chain.proceed(param.args);
                } else {
                    param.result = chain.proceedWith(param.thisObject, param.args);
                }
            } catch (Throwable t) {
                param.throwable = t;
            }
        }
        try {
            afterHook(param);
        } catch (Throwable t) {
            LogKt.logE("afterHook: ", t);
        }
        if (param.throwable != null) {
            throw param.throwable;
        }
        return param.result;
    }

    protected void beforeHook(@NonNull HookParam param) {

    }

    protected void afterHook(@NonNull HookParam param) {

    }
}
