package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.setObj

class SendImageWithHighQualityByDefault : DynHook() {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.sendImageWithHighQualityByDefault

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val classMediaEditState =
            findClass("org.telegram.messenger.MediaController\$MediaEditState")
        classMediaEditState.hookAllCAfter(cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
        classMediaEditState.hookAllAfter("reset", cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
    }
}
