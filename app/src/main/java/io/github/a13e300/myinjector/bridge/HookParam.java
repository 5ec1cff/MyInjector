package io.github.a13e300.myinjector.bridge;

import java.lang.reflect.Member;

public class HookParam {
    Object[] args;
    Object thisObject;
    Member member;
    Object result;
    Throwable throwable;
    boolean invoked = false;

    public HookParam() {
    }

    public Member getMethod() {
        return member;
    }

    public void setMethod(Member m) {
        member = m;
    }

    public Object getThisObject() {
        return thisObject;
    }

    public void setThisObject(Object t) {
        thisObject = t;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] a) {
        args = a;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object r) {
        result = r;
        throwable = null;
        invoked = true;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable t) {
        throwable = t;
        result = null;
        invoked = true;
    }
}
