package io.github.a13e300.myinjector.bridge

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Member

class Unhook(private val inner: XposedInterface.HookHandle) {
    val hookedMethod: Member
        get() = inner.executable

    fun unhook() {
        inner.unhook()
    }
}
