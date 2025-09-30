package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllReplace
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.toObfsInfo
import java.io.File

class XhsHandler : IHook() {
    override fun onHook() {
        val obfsTable = createObfsTable("", 1) { bridge ->
            val savePicMethod = bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "decode bitmap is null",
                        "save image failed without watermark"
                    )
                }
            }.single()
            mutableMapOf("savePicMethod" to savePicMethod.toObfsInfo())
        }
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
    }
}
