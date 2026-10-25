package io.github.a13e300.myinjector.xhs

import io.github.a13e300.myinjector.arch.hookAllConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo

class OpenStickerAsImage : MyDynHook("openStickerAsImage") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.openStickerAsImage

    override fun onHook() {
        val openStickerAsImageMagicSwitch =
            XhsHandler.creator.create("OpenStickerAsImageMagicSwitch") { bridge ->
                val openImageMethod = bridge.findMethod {
                    matcher {
                        usingEqStrings("CommentConsumerList", "onCommentImageClick commentId: ")
                    }
                }.single()

                val magic = bridge.findMethod {
                    matcher {
                        addCaller(openImageMethod.descriptor)
                        paramCount(0)
                        returnType(java.lang.Boolean.TYPE)

                        declaredClass {
                            fieldCount(1)
                        }
                    }
                }.single()

                magic.toObfsInfo()
            }
        findClass(openStickerAsImageMagicSwitch.className)
            .hookAllConstantIf(
                openStickerAsImageMagicSwitch.memberName,
                false,
                cond = ::isEnabled
            )
    }
}
