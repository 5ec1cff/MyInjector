package io.github.a13e300.myinjector.bridge;

import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;

public class HookParam {
    final XC_MethodHook.MethodHookParam methodHookParam;

    public HookParam(XC_MethodHook.MethodHookParam param) {
        methodHookParam = param;
    }

    public Member getMethod() {
        return methodHookParam.method;
    }

    public void setMethod(Member m) {
        methodHookParam.method = m;
    }

    public Object getThisObject() {
        return methodHookParam.thisObject;
    }

    public void setThisObject(Object t) {
        methodHookParam.thisObject = t;
    }

    public Object[] getArgs() {
        return methodHookParam.args;
    }

    public void setArgs(Object[] a) {
        methodHookParam.args = a;
    }

    public Object getResult() {
        return methodHookParam.getResult();
    }

    public void setResult(Object r) {
        methodHookParam.setResult(r);
    }

    public Throwable getThrowable() {
        return methodHookParam.getThrowable();
    }

    public void setThrowable(Throwable t) {
        methodHookParam.setThrowable(t);
    }
}
