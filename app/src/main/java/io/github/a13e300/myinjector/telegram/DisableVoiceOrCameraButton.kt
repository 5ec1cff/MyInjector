package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookAllConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

// 禁用音频 / 摄像头按钮，防止误触
class DisableVoiceOrCameraButton : MyDynHook("disableVoiceOrCameraButton") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableVoiceOrCameraButton

    fun deobf(
        creator: ObfsTableCreator
    ): ObfsTable {
        // ChatActivityEnterView.<init> audioVideoButtonContainer = new FrameLayout(...)
        creator.create("chatActivityEnterViewAudioVideoButtonContainerClass") { bridge ->
            bridge.findMethod {
                matcher {
                    name("<init>")
                    addCaller {
                        // this is not obfuscated
                        declaredClass("org.telegram.ui.Components.ChatActivityEnterView")
                        name("<init>")
                    }
                    declaredClass {
                        addMethod {
                            name("onTouchEvent")
                            addInvoke {
                                descriptor("Landroid/view/MotionEvent;->getAction()I")
                            }
                        }
                    }
                }
            }.single().toObfsInfo()
        }

        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val info = table["chatActivityEnterViewAudioVideoButtonContainerClass"]!!
        logD("chatActivityEnterViewAudioVideoButtonContainerClass ${info.className}")
        findClass(info.className).hookAllConstantIf("onTouchEvent", true) {
            isEnabled()
        }
    }
}
