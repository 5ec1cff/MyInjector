package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAfter

class LanDropHandler : IHook() {
    override fun onHook() {
        val fileTypeEnumClass = findClass("b4.a\$a")
        val fileTypeFile = fileTypeEnumClass.getObjS("h") // "FILE"
        val fileTypeText = fileTypeEnumClass.getObjS("d") // "TEXT"
        // guessType
        // "image", "video", "text", "url"
        findClass("b4.a").hookAfter("c", String::class.java) { param ->
            if (param.result == fileTypeText) {
                param.result = fileTypeFile
            }
        }
    }
}
