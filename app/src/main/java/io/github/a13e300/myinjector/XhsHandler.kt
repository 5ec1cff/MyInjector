package io.github.a13e300.myinjector

import android.app.AndroidAppHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.MutableObfsTable
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllConstant
import io.github.a13e300.myinjector.arch.hookAllReplace
import io.github.a13e300.myinjector.arch.hookCAfter
import io.github.a13e300.myinjector.arch.newInst
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import java.io.File

fun toHtmlText(title: String?, desc: String?, url: String): String {
    val sb = StringBuilder()
    var hasPre = false
    if (title?.isNotEmpty() == true) {
        sb.append("<b>$title</b>")
        hasPre = true
    }
    val id = Regex("https://www.xiaohongshu.com/discovery/item/([0-9a-z]*)")
        .find(url)?.groupValues?.getOrNull(1)
    val text = if (id != null) "xhs/$id" else url.removePrefix("https://").split("?").first()
    if (hasPre) sb.append("<br>")
    sb.append("<a href=\"$url\">$text</a>")
    if (desc?.isNotEmpty() == true) {
        sb.append("<br>")
        sb.append(desc.split("\n").joinToString("<br>") { "<pre>$it</pre>" })
    }
    return sb.toString()
}

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

    private fun locateDisableSave(bridge: DexKitBridge, map: MutableObfsTable) = runCatching {
        val clazz = bridge.findClass {
            matcher {
                fields {
                    add {
                        name("disable")
                    }
                    add {
                        name("disableToast")
                    }
                    add {
                        name("checkLogin")
                    }
                }
            }
        }.single()
        val method = clazz.findMethod {
            matcher {
                usingFields {
                    add {
                        name("disable")
                    }
                }
                returnType(java.lang.Boolean::class.java)
            }
        }.single()
        map.put("disableSave", method.toObfsInfo())
    }.onFailure {
        logE("locateDisableSave", it)
    }

    private fun hookOpenStickerAsImage(obfsTable: ObfsTable) = runCatching {
        val openStickerAsImageMagicSwitch = obfsTable["OpenStickerAsImageMagicSwitch"]!!
        findClass(openStickerAsImageMagicSwitch.className)
            .hookAllConstant(openStickerAsImageMagicSwitch.memberName, false)
    }.onFailure {
        logE("hookOpenStickerAsImage", it)
    }

    private fun locateShare(bridge: DexKitBridge, map: MutableObfsTable) = runCatching {
        val shareMethod = bridge.findMethod {
            matcher {
                usingEqStrings("TYPE_LINKED", "xhsdiscover://tagged_me")
            }
        }.single()

        map["shareMethod"] = shareMethod.toObfsInfo()
    }.onFailure {
        logE("locateShare", it)
    }

    private fun hookShare(obfsTable: ObfsTable) = runCatching {
        val shareMethod = obfsTable["shareMethod"]!!

        val shareClass = findClass(shareMethod.className)

        val shareEntityField = shareClass.declaredFields.single {
            it.type.name.endsWith("ShareEntity")
        }.also { it.isAccessible = true }

        val noteItemBeanField = shareClass.declaredFields.single {
            it.type.name.endsWith("NoteItemBean")
        }.also { it.isAccessible = true }

        var longPressed = false

        shareClass.hookAllBefore(shareMethod.memberName) { param ->
            if (param.args[0] == "TYPE_LINKED") {
                val shareEntity = shareEntityField.get(param.thisObject)
                val noteItem = noteItemBeanField.get(param.thisObject)
                val longClicked = longPressed
                longPressed = false
                val pageUrl = shareEntity.getObjAs<String>("pageUrl")
                var desc = noteItem.getObjAsN<String>("desc")
                var title = noteItem.getObjAsN<String>("title")
                if (title != null && desc?.startsWith(title) == true) title = null

                if (!longClicked && desc != null) {
                    desc = desc.let {
                        if (it.length > 50) {
                            val last2 = it[47]
                            if (last2.code in 0xd800..<0xdc00) {
                                it.substring(0, 47).trimEnd() + ".."
                            } else {
                                it.substring(0, 48).trimEnd() + ".."
                            }
                        } else it.trimEnd()
                    }
                }

                val uri = Uri.parse(pageUrl)
                val newUri = uri.buildUpon()
                    .also {
                        uri.getQueryParameters("xsec_token").firstOrNull()?.let { token ->
                            it.clearQuery()
                            it.appendQueryParameter("xsec_token", token)
                        }
                    }
                    .build()
                    .toString()
                // logD("newUri=$newUri desc=$desc title=$title")
                val html = toHtmlText(title, desc, newUri)
                var text = title
                if (text.isNullOrEmpty()) text = desc
                else text += "\n" + desc
                if (text.isNullOrEmpty()) text = newUri
                else text += "\n" + newUri
                // logD("html=$html")

                AndroidAppHelper.currentApplication().getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newHtmlText("", text, html))

                param.result = null
            }
        }

        val shareUserView = findClass("com.xingin.sharesdk.ui.view.ShareUserView")
        shareUserView.hookCAfter(
            Context::class.java,
            AttributeSet::class.java,
            Integer.TYPE
        ) { param ->
            (param.thisObject as View).setOnLongClickListener {
                logD("onlongclicked")
                longPressed = true
                it.callOnClick()
            }
        }
    }.onFailure {
        logE("hookShare", it)
    }

    private fun hookDisableSave(obfsTable: ObfsTable) = runCatching {
        val method = obfsTable["disableSave"]!!
        findClass(method.className).hookAllConstant(method.memberName, false)
    }.onFailure {
        logE("hookDisableSave", it)
    }

    override fun onHook() {
        val obfsTable = createObfsTable("", 4) { bridge ->
            val map = mutableMapOf<String, ObfsInfo>()
            locateSavePic(bridge, map)
            locateOpenStickerAsImage(bridge, map)
            locateShare(bridge, map)
            locateDisableSave(bridge, map)
            map
        }

        hookSavePic(obfsTable)
        hookOpenStickerAsImage(obfsTable)
        hookShare(obfsTable)
        hookDisableSave(obfsTable)
    }
}
