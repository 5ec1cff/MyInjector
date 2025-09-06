package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllConstantIf
import java.util.concurrent.atomic.AtomicBoolean

// 禁用音频 / 摄像头按钮，防止误触
class DisableVoiceOrCameraButton : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableVoiceOrCameraButton

    override fun onHook() {
        val subHookFound = AtomicBoolean(false)
        findClass("org.telegram.ui.Components.ChatActivityEnterView").hookAllCAfter { param ->
            if (!isEnabled()) return@hookAllCAfter
            if (subHookFound.get()) return@hookAllCAfter
            val audioVideoButtonContainer =
                param.thisObject.getObj("audioVideoButtonContainer") ?: return@hookAllCAfter
            audioVideoButtonContainer.javaClass.hookAllConstantIf("onTouchEvent", true) {
                isEnabled()
            }
            subHookFound.set(true)
        }
    }
}
