package io.github.a13e300.myinjector.bridge

import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member

class Unhook(private val inner: XC_MethodHook.Unhook) {
    val hookedMethod: Member
        get() = inner.hookedMethod

    fun unhook() {
        inner.unhook()
    }
}
