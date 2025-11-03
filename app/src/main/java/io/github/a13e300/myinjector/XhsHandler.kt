package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.MutableObfsTable
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllConstant
import io.github.a13e300.myinjector.arch.hookAllReplace
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class XhsHandler : IHook() {

    private fun locateSavePic(bridge: DexKitBridge, map: MutableObfsTable) = runCatching {
        val savePicMethod = bridge.findMethod {
            matcher {
                usingEqStrings(
                    "decode bitmap is null",
                    "save image failed without watermark"
                )
            }
        }.single()

        map["savePicMethod"] = savePicMethod.toObfsInfo()
    }.onFailure {
        logE("locateSavePic", it)
    }

    private fun hookSavePic(obfsTable: ObfsTable) = runCatching {
        val savePicMethod = obfsTable["savePicMethod"]!!
        logD("got savePicMethod: $savePicMethod")

        val kotlinPair = findClass("kotlin.Pair")

        findClass(savePicMethod.className).hookAllReplace(savePicMethod.memberName) { param ->
            val f = param.args[1].let {
                it.javaClass.declaredFields.single { f -> f.type == File::class.java }
                    .apply { isAccessible = true }
                    .get(it) as File
            }
            param.args.last().call("onNext", kotlinPair.newInst("success", f))
        }
    }.onFailure {
        logE("hookSavePic", it)
    }

    private fun locateOpenStickerAsImage(bridge: DexKitBridge, map: MutableObfsTable) =
        runCatching {
            val openImageMethod = bridge.findMethod {
                matcher {
                    usingEqStrings("CommentConsumerList", "onCommentImageClick commentId: ")
                }
            }.single()

            logD("openImageMethod $openImageMethod")
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

            map["OpenStickerAsImageMagicSwitch"] = magic.toObfsInfo()
        }.onFailure {
            logE("locateOpenStickerAsImage", it)
        }

    private fun hookOpenStickerAsImage(obfsTable: ObfsTable) = runCatching {
        val openStickerAsImageMagicSwitch = obfsTable["OpenStickerAsImageMagicSwitch"]!!
        findClass(openStickerAsImageMagicSwitch.className)
            .hookAllConstant(openStickerAsImageMagicSwitch.memberName, false)
    }.onFailure {
        logE("hookOpenStickerAsImage", it)
    }

    override fun onHook() {
        val obfsTable = createObfsTable("", 2) { bridge ->
            val map = mutableMapOf<String, ObfsInfo>()
            locateSavePic(bridge, map)
            locateOpenStickerAsImage(bridge, map)
            map
        }

        hookSavePic(obfsTable)
        hookOpenStickerAsImage(obfsTable)
    }
}
