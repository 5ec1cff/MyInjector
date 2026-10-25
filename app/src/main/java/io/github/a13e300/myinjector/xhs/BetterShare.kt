package io.github.a13e300.myinjector.xhs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import io.github.a13e300.myinjector.arch.currentApplication
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookCAfter
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import java.lang.reflect.Field

private fun toHtmlText(title: String?, desc: String?, url: String): String {
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
        sb.append("<br><blockquote>")
        sb.append(desc.split("\n").joinToString("<br>"))
        sb.append("</blockquote>")
    }
    return sb.toString()
}


class BetterShare : MyDynHook("betterShare") {
    override fun isFeatureEnabled(): Boolean =
        io.github.a13e300.myinjector.XhsHandler.settings.betterShare

    override fun onHook() {
        val shareMethod =
            io.github.a13e300.myinjector.XhsHandler.creator.create("shareMethod") { bridge ->
            val shareMethod = bridge.findMethod {
                matcher {
                    usingEqStrings("TYPE_LINKED", "xhsdiscover://tagged_me")
                }
            }.single()

            shareMethod.toObfsInfo()
        }

        val shareClass = findClass(shareMethod.className)

        val shareEntityField = shareClass.declaredFields.single {
            it.type.name.endsWith("ShareEntity")
        }.also { it.isAccessible = true }

        val noteItemBeanField = shareClass.declaredFields.single {
            it.type.name.endsWith("NoteItemBean")
        }.also { it.isAccessible = true }

        var longPressed = false

        fun shareCommon(shareEntity: Any, noteItem: Any) {
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
            val html = io.github.a13e300.myinjector.toHtmlText(title, desc, newUri)
            var text = title
            if (text.isNullOrEmpty()) text = desc
            else text += "\n" + desc
            if (text.isNullOrEmpty()) text = newUri
            else text += "\n" + newUri
            // logD("html=$html")

            currentApplication()
                .getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newHtmlText("", text, html))
        }

        shareClass.hookAllBefore(shareMethod.memberName, cond = ::isEnabled) { param ->
            if (param.args[0] == "TYPE_LINKED") {
                val shareEntity = shareEntityField.get(param.thisObject)
                val noteItem = noteItemBeanField.get(param.thisObject)

                shareCommon(shareEntity, noteItem)

                param.result = null
            }
        }

        val shareUserView = findClass("com.xingin.sharesdk.ui.view.ShareUserView")
        shareUserView.hookCAfter(
            Context::class.java,
            AttributeSet::class.java,
            Integer.TYPE, cond = ::isEnabled
        ) { param ->
            (param.thisObject as View).setOnLongClickListener {
                longPressed = true
                it.callOnClick()
            }
        }

        // l2b.e0#invoke 9.34.4
        val shareMethod2 =
            io.github.a13e300.myinjector.XhsHandler.creator.create("shareMethod2") { bridge ->
            val shareMethod = bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "TYPE_COPY_ONLY",
                        "configData is null copyText:",
                        "ShareHelper"
                    )
                }
            }.single()

            shareMethod.toObfsInfo()
        }

        val skipClipboardTls = ThreadLocal<Boolean>()

        ClipboardManager::class.java.hookAllBefore("setPrimaryClip") { param ->
            if (skipClipboardTls.get() == true) {
                param.result = true
            }
        }

        val shareClass2 = findClass(shareMethod2.className)

        // .d
        val shareEntityPreField = shareClass2.declaredFields.single {
            it.type.declaredFields.any {
                it.type.name.endsWith("ShareEntity")
            }
        }
            .also { it.isAccessible = true }
        // .d.a
        val shareEntityField2 =
            shareEntityPreField.type.declaredFields.single { it.type.name.endsWith("ShareEntity") }
                .also { it.isAccessible = true }
        logD("shareEntityPreField $shareEntityPreField shareEntityField2=$shareEntityField2")

        var typeField: Field? = null
        var anotherFieldContainNoteItemInPreField: Field? = null
        var theFieldAnotherFieldContainNoteItemInPreField: Field? = null

        shareClass2.hookAll(shareMethod2.memberName, cond = ::isEnabled, before = { param ->
            val arg0 = param.args[0]
            logD("arg0 $arg0")
            if (typeField == null) {
                val field =
                    arg0.javaClass.declaredFields.singleOrNull { it.type == String::class.java }
                        ?.also { it.isAccessible = true }
                logD("field $field arg0.<field>=${field?.get(arg0)}")
                if (field != null && field.get(arg0) == "TYPE_LINKED") {
                    logD("found type field $field")
                    typeField = field
                }
            }
            if (typeField == null) return@hookAll
            if (typeField.get(arg0) == "TYPE_LINKED") {
                val preFieldV = shareEntityPreField.get(param.thisObject)
                val shareEntity = shareEntityField2.get(preFieldV)
                if (anotherFieldContainNoteItemInPreField == null) {
                    var foundField: Field? = null
                    var foundParentField: Field? = null
                    runCatching {
                        for (f1 in preFieldV.javaClass.declaredFields) {
                            f1.isAccessible = true
                            val obj = f1.get(preFieldV)
                            if (obj != null) {
                                for (f2 in obj.javaClass.declaredFields) {
                                    if (f2.type.name.endsWith("NoteItemBean")) {
                                        logD("found NoteItemBean field $f2")
                                        foundField = f2
                                        break
                                    }
                                }
                                if (foundField != null) {
                                    logD("found field contains NoteItemBean field: $f1")
                                    foundParentField = f1
                                    break
                                }
                            }
                        }
                    }.onFailure {
                        logE("find noteitembean: ", it)
                    }
                    if (foundField != null) {
                        // .d.s
                        anotherFieldContainNoteItemInPreField = foundParentField
                        // .d.s.d
                        theFieldAnotherFieldContainNoteItemInPreField = foundField
                    }
                }
                val noteItem = theFieldAnotherFieldContainNoteItemInPreField!!.get(
                    anotherFieldContainNoteItemInPreField!!.get(preFieldV)
                )

                shareCommon(shareEntity, noteItem)

                skipClipboardTls.set(java.lang.Boolean.TRUE)
            }
        }, after = {
            skipClipboardTls.remove()
        })
    }
}
